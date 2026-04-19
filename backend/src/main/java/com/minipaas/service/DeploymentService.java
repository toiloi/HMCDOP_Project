package com.minipaas.service;

import com.minipaas.model.BuildLog;
import com.minipaas.model.Deployment;
import com.minipaas.model.DeployStatus;
import com.minipaas.repository.BuildLogRepository;
import com.minipaas.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.UUID;

/**
 * ================================================================
 * DeploymentService — Điều phối toàn bộ Deploy Pipeline
 * ================================================================
 *
 * Đây là service cốt lõi của hệ thống Mini-PaaS.
 * Khi người dùng gửi yêu cầu deploy, luồng xử lý như sau:
 *
 * [HTTP Request] → startDeployment() → lưu DB (PENDING)
 * → kích hoạt executeDeployment() @Async
 * [Trả response ngay] ← deploymentId
 *
 * [Thread riêng - @Async]:
 * 1. Tạo K8s Namespace
 * 2. Tạo GHCR Registry Secret
 * 3. Tạo Kaniko Job (clone repo + build image)
 * 4. Stream logs về frontend (SSE)
 * 5. Khi build xong: tạo K8s Deployment + Service
 * 6. Tạo Traefik IngressRoute → cấp URL
 * 7. Cập nhật DB (RUNNING + URL)
 *
 * Sơ đồ:
 * ┌───────────────┐ ┌────────────────┐ ┌─────────────────┐
 * │ Spring Boot │────▶│ K3s API Server │────▶│ Kaniko Pod │
 * │ (Fabric8) │ │ (Tailscale IP) │ │ (Worker Node) │
 * └───────────────┘ └────────────────┘ └─────────────────┘
 * │ │
 * │ SSE stream logs │ Build image
 * ▼ ▼
 * ┌───────────────┐ ┌──────────────────┐
 * │ Frontend │ │ GHCR Registry │
 * │ (LogViewer) │ │ ghcr.io/user/app │
 * └───────────────┘ └──────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentService {

    private final DeploymentRepository deploymentRepo;
    private final BuildLogRepository buildLogRepo;
    private final KubernetesService k8sService;
    private final BuildService buildService;
    private final DeploymentSseManager sseManager;
    private final CloudflareDnsService cloudflareDnsService;

    @Value("${app.registry.ghcr-user}")
    private String ghcrUser;

    @Value("${app.deployment.ingress-public-ip:127.0.0.1}")
    private String ingressPublicIp;

    /**
     * Đăng ký deployment mới vào DB.
     * Trả về ngay (không chờ build xong).
     */
    @Transactional
    public Deployment createDeployment(Deployment deployment) {
        deployment.setStatus(DeployStatus.PENDING);
        // Lưu lần 1 để hệ thống sinh ra một UUID độc nhất
        deployment = deploymentRepo.save(deployment);
        
        // Lấy repo name từ GitHub URL để làm app name
        String repoName = extractRepoName(deployment.getGithubUrl());
        String shortId = deployment.getId().toString().substring(0, 8);
        
        deployment.setAppName("app-" + repoName.toLowerCase() + "-" + shortId);
        deployment.setK8sNamespace("deploy-" + deployment.getId().toString());

        // Lưu lần 2 để cập nhật appName và k8sNamespace vào db
        return deploymentRepo.save(deployment);
    }

    /**
     * Pipeline deploy bất đồng bộ.
     * Chạy trong thread riêng (ThreadPoolTaskExecutor "deployTaskExecutor")
     * để không block HTTP thread.
     */
    @Async("deployTaskExecutor")
    public void executeDeployment(UUID deploymentId) {
        Deployment dep = deploymentRepo.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment không tồn tại: " + deploymentId));

        String namespace = "deploy-" + deploymentId;
        String appName = dep.getAppName();
        String imageTag = ("ghcr.io/" + ghcrUser + "/app-" + deploymentId.toString().substring(0, 8) + ":latest")
                .toLowerCase();

        try {
            // ── BƯỚC 1: Tạo Namespace ──
            sendLog(dep, "🚀 Bắt đầu deploy: " + dep.getGithubUrl());
            sendLog(dep, "📁 Tạo Kubernetes Namespace: " + namespace);
            k8sService.createNamespace(namespace);

            // Cập nhật namespace trong DB
            dep.setK8sNamespace(namespace);
            dep.setStatus(DeployStatus.BUILDING);
            dep.setImageTag(imageTag);
            deploymentRepo.save(dep);

            // ── BƯỚC 2: Tạo Registry Secret ──
            sendLog(dep, "🔐 Tạo GHCR credentials secret...");
            k8sService.createRegistrySecret(namespace);

            // ── BƯỚC 3 & 4: Build Image & Theo dõi (Kaniko hoặc GitHub Actions) ──
            sendLog(dep, "⏳ Đang khởi tạo luồng Build Image...");
            boolean buildSuccess = buildService.buildAndWatch(namespace, dep.getGithubUrl(), dep.getBranch(), imageTag,
                    deploymentId);

            if (!buildSuccess) {
                throw new RuntimeException("Build Image thất bại!");
            }

            // ── BƯỚC 5: Deploy app lên K3s ──
            sendLog(dep, "🚢 Đang deploy Docker image lên K3s cluster...");
            k8sService.createAppDeployment(namespace, appName, imageTag, dep.getPort());
            sendLog(dep, "✅ K8s Deployment tạo: " + appName);

            // ── BƯỚC 6: Tạo Service ──
            k8sService.createAppService(namespace, appName, dep.getPort());
            sendLog(dep, "✅ K8s Service tạo: " + appName + "-svc");

            // ── BƯỚC 7: Tạo Traefik IngressRoute → Cấp URL ──
            sendLog(dep, "🌐 Cấu hình Traefik Ingress...");
            String publicUrl = k8sService.createIngressRoute(namespace, appName);
            if (cloudflareDnsService.isEnabled()) {
                try {
                    URI uri = URI.create(publicUrl);
                    String fqdn = uri.getHost();
                    if (fqdn != null && !fqdn.isBlank()) {
                        cloudflareDnsService.upsertARecord(fqdn, ingressPublicIp);
                        sendLog(dep, "Cloudflare DNS: " + fqdn + " -> " + ingressPublicIp);
                    }
                } catch (Exception e) {
                    log.warn("Cloudflare DNS failed: {}", e.getMessage());
                    sendLog(dep, "Cloudflare DNS warning: " + e.getMessage());
                }
            }

            // ── BƯỚC 8: Cập nhật DB → RUNNING ──
            dep.setStatus(DeployStatus.RUNNING);
            dep.setUrl(publicUrl);
            deploymentRepo.save(dep);

            sendLog(dep, "");
            sendLog(dep, "🎉 ========================================");
            sendLog(dep, "🎉 DEPLOY THÀNH CÔNG!");
            sendLog(dep, "🌐 URL: " + publicUrl);
            sendLog(dep, "🎉 ========================================");

            sseManager.sendStatus(deploymentId, DeployStatus.RUNNING, publicUrl);

        } catch (Exception e) {
            log.error("Deploy thất bại cho {}: {}", deploymentId, e.getMessage(), e);
            dep.setStatus(DeployStatus.FAILED);
            deploymentRepo.save(dep);
            sendLog(dep, "❌ LỖI: " + e.getMessage());
            sseManager.sendStatus(deploymentId, DeployStatus.FAILED, null);
        } finally {
            sseManager.complete(deploymentId);
        }
    }

    /** Dừng và xóa deployment (xóa toàn bộ K8s namespace) */
    @Transactional
    public void stopDeployment(UUID deploymentId) {
        Deployment dep = deploymentRepo.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy deployment: " + deploymentId));
        if (dep.getK8sNamespace() != null) {
            k8sService.deleteNamespace(dep.getK8sNamespace());
        }
        dep.setStatus(DeployStatus.STOPPED);
        deploymentRepo.save(dep);
    }

    // Helper: Gửi log đến SSE và lưu vào DB
    private void sendLog(Deployment dep, String message) {
        sseManager.sendLog(dep.getId(), message);
        buildLogRepo.save(BuildLog.builder()
                .deployment(dep)
                .logLine(message)
                .build());
    }

    // Helper: Lấy repo name từ GitHub URL
    // "https://github.com/user/my-repo" → "my-repo"
    private String extractRepoName(String githubUrl) {
        String[] parts = githubUrl.split("/");
        String name = parts[parts.length - 1];
        return name.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
    }
}
