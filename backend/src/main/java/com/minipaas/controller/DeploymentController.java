package com.minipaas.controller;

import com.minipaas.dto.DeployRequest;
import com.minipaas.dto.DeploymentResponse;
import com.minipaas.model.BuildLog;
import com.minipaas.model.Deployment;
import com.minipaas.model.DeployStatus;
import com.minipaas.repository.BuildLogRepository;
import com.minipaas.repository.DeploymentRepository;
import com.minipaas.service.DeploymentService;
import com.minipaas.service.DeploymentSseManager;
import com.minipaas.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller xử lý các yêu cầu deploy.
 *
 * Endpoints chính:
 *   POST   /api/v1/deployments           → Tạo deployment mới
 *   GET    /api/v1/deployments           → Danh sách deployments của user
 *   GET    /api/v1/deployments/{id}      → Chi tiết 1 deployment
 *   GET    /api/v1/deployments/{id}/logs → SSE stream build logs (real-time)
 *   DELETE /api/v1/deployments/{id}      → Dừng & xóa deployment
 */
@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
@Slf4j
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentSseManager sseManager;
    private final DeploymentRepository deploymentRepo;
    private final BuildLogRepository buildLogRepo;
    private final UserService userService;

    /**
     * Tạo deployment mới.
     * Trả về ngay deploymentId — không chờ build xong.
     * Frontend dùng deploymentId này để subscribe SSE logs.
     */
    @PostMapping
    public ResponseEntity<DeploymentResponse> createDeployment(
            @Valid @RequestBody DeployRequest req,
            Authentication auth) {

        var user = userService.findByEmail(auth.getName());

        // Tạo entity deployment
        Deployment dep = Deployment.builder()
                .user(user)
                .githubUrl(req.getGithubUrl())
                .branch(req.getBranch())
                .port(req.getPort())
                .build();

        // Lưu vào DB (status: PENDING)
        dep = deploymentService.createDeployment(dep);
        log.info("Deployment mới: {} - {}", dep.getId(), dep.getGithubUrl());

        // Kích hoạt pipeline bất đồng bộ (non-blocking)
        deploymentService.executeDeployment(dep.getId());

        return ResponseEntity.ok(DeploymentResponse.from(dep));
    }

    /**
     * SSE Endpoint: Stream build logs real-time về client.
     *
     * Client kết nối: new EventSource('/api/v1/deployments/{id}/logs')
     *   event "log"    → một dòng log
     *   event "status" → trạng thái cuối cùng (RUNNING:url hoặc FAILED)
     *
     * MediaType: text/event-stream (SSE standard)
     */
    @GetMapping(value = "/{id}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable UUID id, Authentication auth) {
        log.debug("Client kết nối SSE cho deployment: {}", id);

        SseEmitter emitter = sseManager.createEmitter(id);

        // Gửi ngay các log đã lưu trong DB (nếu reconnect)
        deploymentRepo.findById(id).ifPresent(dep -> {
            List<BuildLog> existingLogs = buildLogRepo.findByDeploymentOrderByCreatedAtAsc(dep);
            existingLogs.forEach(buildLog -> {
                try {
                    emitter.send(SseEmitter.event().name("log").data(buildLog.getLogLine()));
                } catch (Exception ignored) {}
            });

            // Nếu đã RUNNING/FAILED, gửi status và đóng
            if (dep.getStatus() == DeployStatus.RUNNING || dep.getStatus() == DeployStatus.FAILED) {
                try {
                    String statusData = dep.getStatus().name() +
                            (dep.getUrl() != null ? ":" + dep.getUrl() : "");
                    emitter.send(SseEmitter.event().name("status").data(statusData));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /** Danh sách tất cả deployments của user đang đăng nhập */
    @GetMapping
    public ResponseEntity<List<DeploymentResponse>> listDeployments(Authentication auth) {
        var user = userService.findByEmail(auth.getName());
        var list = deploymentRepo.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(DeploymentResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** Chi tiết 1 deployment theo ID */
    @GetMapping("/{id}")
    public ResponseEntity<DeploymentResponse> getDeployment(@PathVariable UUID id) {
        return deploymentRepo.findById(id)
                .map(DeploymentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Dừng và xóa deployment (xóa K8s namespace + resources) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeployment(@PathVariable UUID id) {
        deploymentService.stopDeployment(id);
        return ResponseEntity.noContent().build();
    }

    /** Health check */
    @GetMapping("/health")
    @org.springframework.web.bind.annotation.RequestMapping(value = "/health", method = org.springframework.web.bind.annotation.RequestMethod.GET)
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(java.util.Map.of("status", "ok", "service", "minipaas-control-plane"));
    }
}
