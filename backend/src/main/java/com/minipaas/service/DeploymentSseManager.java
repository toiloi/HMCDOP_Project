package com.minipaas.service;

import com.minipaas.model.Deployment;
import com.minipaas.model.DeployStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý các SSE (Server-Sent Events) connections cho build logs.
 *
 * Kiến trúc SSE trong hệ thống này:
 * ┌──────────────────────────────────────────────────────┐
 * │  Frontend                                            │
 * │  const es = new EventSource('/api/v1/deployments/{id}/logs')│
 * │  es.addEventListener('log', e => appendLog(e.data)) │
 * └──────────────────┬───────────────────────────────────┘
 *                    │ HTTP long connection (SSE)
 * ┌──────────────────▼───────────────────────────────────┐
 * │  DeploymentController.streamLogs()                   │
 * │  → tạo SseEmitter, đăng ký vào SseManager           │
 * └──────────────────────────────────────────────────────┘
 *                    ▲
 *                    │ emitter.send(event)
 * ┌──────────────────┴───────────────────────────────────┐
 * │  DeploymentService (@Async thread)                   │
 * │  → Kaniko logs → sseManager.sendLog(deploymentId, …) │
 * └──────────────────────────────────────────────────────┘
 */
@Component
@Slf4j
public class DeploymentSseManager {

    // Map từ deploymentId → danh sách SSE emitters
    // (Có thể có nhiều tab mở cùng 1 deployment)
    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Tạo SseEmitter mới cho client kết nối.
     * Timeout 10 phút (Kaniko build có thể mất lâu)
     */
    public SseEmitter createEmitter(UUID deploymentId) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 phút

        emitters.computeIfAbsent(deploymentId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(deploymentId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout cho deployment: {}", deploymentId);
            removeEmitter(deploymentId, emitter);
        });
        emitter.onError(e -> removeEmitter(deploymentId, emitter));

        return emitter;
    }

    /** Gửi một dòng log đến tất cả clients đang xem deployment này */
    public void sendLog(UUID deploymentId, String message) {
        List<SseEmitter> list = emitters.get(deploymentId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(message));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        });
        list.removeAll(deadEmitters);
    }

    /** Gửi event báo deploy hoàn thành (thành công hoặc thất bại) */
    public void sendStatus(UUID deploymentId, DeployStatus status, String url) {
        List<SseEmitter> list = emitters.get(deploymentId);
        if (list == null) return;

        String data = status.name() + (url != null ? ":" + url : "");
        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("status").data(data));
            } catch (IOException ignored) {}
        });
    }

    /** Đóng tất cả connections khi deploy xong */
    public void complete(UUID deploymentId) {
        List<SseEmitter> list = emitters.remove(deploymentId);
        if (list != null) {
            list.forEach(emitter -> {
                try { emitter.complete(); }
                catch (Exception ignored) {}
            });
        }
    }

    private void removeEmitter(UUID deploymentId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(deploymentId);
        if (list != null) list.remove(emitter);
    }
}
