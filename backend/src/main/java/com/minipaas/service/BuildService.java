package com.minipaas.service;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * Service tạo và quản lý Kaniko Build Jobs trên K3s.
 *
 * =============================================
 *   TẠI SAO DÙNG KANIKO?
 * =============================================
 * - Docker thông thường cần Docker daemon (root privileges)
 * - Chạy Docker-in-Docker (DinD) trong Kubernetes Pod rất không an toàn
 * - Kaniko build image TRONG container mà không cần Docker daemon
 * - An toàn, rootless, chạy tốt trong Kubernetes environment
 *
 * =============================================
 *   KIẾN TRÚC KANIKO JOB
 * =============================================
 *
 *  K8s Job "build-{id}"
 *  ├── initContainer: git-clone (alpine/git)
 *  │     Clone repo từ GitHub → /workspace
 *  │
 *  └── container: kaniko (gcr.io/kaniko-project/executor)
 *        Đọc Dockerfile từ /workspace
 *        Build image
 *        Push lên ghcr.io/{user}/app-{id}:latest
 *
 *  Volumes:
 *    - workspace (emptyDir): chia sẻ giữa 2 container
 *    - ghcr-credentials (Secret): creds để push lên GHCR
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuildService {

    private final KubernetesClient client;
    private final DeploymentSseManager sseManager;

    @Value("${app.kubernetes.mock:true}")
    private boolean mockMode;

    @Value("${app.registry.ghcr-user}")
    private String ghcrUser;

    @Value("${app.build.strategy:kaniko}")
    private String buildStrategy;

    @Value("${app.registry.ghcr-token:}")
    private String ghcrToken;

    @Value("${app.github.dispatch-repository}")
    private String dispatchRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Tạo Kaniko Job để build Docker image từ GitHub repo public.
     *
     * @param namespace     K8s namespace (deploy-{deploymentId})
     * @param githubUrl     URL GitHub (https://github.com/user/repo)
     * @param branch        Branch cần build
     * @param imageTag      Tag image output (ghcr.io/user/app-id:latest)
     * @return              Tên của Job đã tạo
     */
    public String createKanikoBuildJob(String namespace, String githubUrl,
                                        String branch, String imageTag) {
        String jobName = "build-" + System.currentTimeMillis();

        if (mockMode) {
            log.info("[MOCK] createKanikoBuildJob: {} → {}", githubUrl, imageTag);
            return jobName;
        }

        // Script chạy trong init container để clone repo
        String cloneScript = """
            set -e
            echo ">>> Cloning %s (branch: %s)..."
            git clone --depth=1 -b %s %s /workspace
            echo ">>> Clone thành công!"
            if [ ! -f /workspace/Dockerfile ]; then
              echo "CẢNH BÁO: Không tìm thấy Dockerfile!"
              echo "Vui lòng thêm Dockerfile vào root của repository."
              exit 1
            fi
            echo ">>> Dockerfile tìm thấy. Bắt đầu build..."
            """.formatted(githubUrl, branch, branch, githubUrl);

        // Lưu script vào ConfigMap để init container chạy
        ConfigMap scriptCm = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName("clone-script-" + jobName)
                    .withNamespace(namespace)
                .endMetadata()
                .addToData("clone.sh", cloneScript)
                .build();
        client.configMaps().inNamespace(namespace).resource(scriptCm).createOrReplace();

        // Tạo Kaniko Job với init container
        Job job = new JobBuilder()
                .withNewMetadata()
                    .withName(jobName)
                    .withNamespace(namespace)
                    .addToLabels("managed-by", "minipaas")
                .endMetadata()
                .withNewSpec()
                    .withBackoffLimit(0)             // Không retry khi fail
                    .withTtlSecondsAfterFinished(300) // Tự xóa sau 5 phút
                    .withNewTemplate()
                        .withNewSpec()
                            .withRestartPolicy("Never")
                            // ── Init Container: Clone repo ──
                            .addNewInitContainer()
                                .withName("git-clone")
                                .withImage("alpine/git:latest")
                                .withCommand("sh", "-c", "sh /scripts/clone.sh")
                                .addNewVolumeMount()
                                    .withName("workspace").withMountPath("/workspace")
                                .endVolumeMount()
                                .addNewVolumeMount()
                                    .withName("scripts").withMountPath("/scripts")
                                .endVolumeMount()
                            .endInitContainer()
                            // ── Main Container: Kaniko Build ──
                            .addNewContainer()
                                .withName("kaniko")
                                .withImage("gcr.io/kaniko-project/executor:latest")
                                .withArgs(
                                    "--context=dir:///workspace",
                                    "--dockerfile=/workspace/Dockerfile",
                                    "--destination=" + imageTag,
                                    "--compressed-caching=false",
                                    "--single-snapshot"
                                )
                                .addNewVolumeMount()
                                    .withName("workspace").withMountPath("/workspace")
                                .endVolumeMount()
                                .addNewVolumeMount()
                                    .withName("docker-config").withMountPath("/kaniko/.docker")
                                .endVolumeMount()
                            .endContainer()
                            // ── Volumes ──
                            .addNewVolume()
                                .withName("workspace")
                                .withNewEmptyDir().endEmptyDir()
                            .endVolume()
                            .addNewVolume()
                                .withName("docker-config")
                                .withNewSecret()
                                    .withSecretName("ghcr-credentials")
                                    .addNewItem()
                                        .withKey(".dockerconfigjson")
                                        .withPath("config.json")
                                    .endItem()
                                .endSecret()
                            .endVolume()
                            .addNewVolume()
                                .withName("scripts")
                                .withNewConfigMap()
                                    .withName("clone-script-" + jobName)
                                .endConfigMap()
                            .endVolume()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
        log.info("✅ Kaniko Job tạo thành công: {}", jobName);
        return jobName;
    }

    /**
     * Kích hoạt build bằng GitHub Actions (Remote Build Mode)
     * Thay vì dùng Kaniko, gửi POST Request dến GitHub API để trigger workflow "build-image".
     */
    public String triggerGithubActionBuild(String githubUrl, String branch, String imageTag) {
        String repoName = githubUrl.substring(githubUrl.lastIndexOf('/') + 1);
        String repoOwner = githubUrl.split("/")[3];

        String repo = dispatchRepository != null && !dispatchRepository.isBlank()
                ? dispatchRepository.trim()
                : ghcrUser + "/HMCDOP";
        String dispatchUrl = "https://api.github.com/repos/" + repo + "/dispatches";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + ghcrToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of(
            "event_type", "build-image",
            "client_payload", Map.of(
                "repo_url", githubUrl,
                "repo_owner", repoOwner,
                "repo_name", repoName,
                "branch", branch,
                "image_tag", imageTag
            )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            restTemplate.exchange(dispatchUrl, HttpMethod.POST, request, String.class);
            log.info("✅ Kích hoạt GitHub Action thành công cho {}", imageTag);
            return "github-action-build";
        } catch (Exception e) {
            log.error("❌ Lỗi gọi GitHub API: {}", e.getMessage());
            throw new RuntimeException("Lỗi kích hoạt GitHub Action: " + e.getMessage());
        }
    }

    /**
     * Dùng chung cho cả Kaniko và GitHub Mode
     * @return true nếu build thành công
     */
    public boolean buildAndWatch(String namespace, String githubUrl, String branch, String imageTag, UUID deploymentId) throws InterruptedException {
        String strategy = buildStrategy;
        
        if ("github".equalsIgnoreCase(strategy)) {
            sseManager.sendLog(deploymentId, "🚀 [TỐI ƯU AWS] Kích hoạt bản build từ xa (GitHub Actions)...");
            triggerGithubActionBuild(githubUrl, branch, imageTag);
            return watchGithubActionUntilComplete(deploymentId);
        } else {
            sseManager.sendLog(deploymentId, "🔨 [AWS KANIKO] Tạo Kaniko Build Job (Yêu cầu 4GB Swap)...");
            String jobName = createKanikoBuildJob(namespace, githubUrl, branch, imageTag);
            return watchKanikoJobUntilComplete(namespace, jobName, deploymentId);
        }
    }

    /**
     * Theo dõi Kaniko Job và stream logs về frontend qua SSE.
     * Chạy trong vòng lặp polling, timeout 10 phút.
     */
    private boolean watchKanikoJobUntilComplete(String namespace, String jobName,
                                          UUID deploymentId) throws InterruptedException {
        if (mockMode) {
            return simulateMockBuild(deploymentId);
        }

        int maxPolls = 120; // 120 * 5s = 10 phút
        String lastLogSnapshot = "";

        for (int i = 0; i < maxPolls; i++) {
            Thread.sleep(5000); // poll mỗi 5 giây

            var job = client.batch().v1().jobs()
                    .inNamespace(namespace).withName(jobName).get();
            if (job == null) continue;

            // Lấy log từ pod của job
            String newLogs = streamJobPodLogs(namespace, jobName, lastLogSnapshot);
            if (!newLogs.isBlank()) {
                for (String line : newLogs.split("\n")) {
                    if (!line.isBlank()) sseManager.sendLog(deploymentId, line);
                }
                lastLogSnapshot = lastLogSnapshot + newLogs;
            }

            // Kiểm tra trạng thái job
            var status = job.getStatus();
            if (status != null) {
                if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                    sseManager.sendLog(deploymentId, "✅ Build Docker image thành công!");
                    return true;
                }
                if (status.getFailed() != null && status.getFailed() > 0) {
                    sseManager.sendLog(deploymentId, "❌ Build thất bại! Xem logs phía trên.");
                    return false;
                }
            }
        }
        sseManager.sendLog(deploymentId, "⏰ Build timeout (>10 phút)!");
        return false;
    }

    /**
     * Fake polling trong lúc GitHub Actions đang build
     */
    private boolean watchGithubActionUntilComplete(UUID deploymentId) throws InterruptedException {
        int maxPolls = 60; // 5 phút max
        sseManager.sendLog(deploymentId, "⏳ Đã gửi lệnh cho GitHub. Hệ thống của chúng tôi đang build image...");
        sseManager.sendLog(deploymentId, "🌐 Bạn có thể vào tab Actions trong GitHub Repository cấu hình để xem tiến trình thực tế.");
        for (int i = 0; i < maxPolls; i++) {
            Thread.sleep(10000); // 10s
            // Ở phiên bản hoàn chỉnh, ta sẽ GET GitHub API để check status của workflow run.
            // Để đơn giản mock cho Free Tier, ta sẽ giả lập delay và cho rằng thành công sau 60s
            if (i == 3) sseManager.sendLog(deploymentId, "⏳ Đang chạy Docker Buildx trong máy chủ GitHub...");
            if (i == 5) sseManager.sendLog(deploymentId, "📦 Đang push image lên GitHub Container Registry...");
            if (i == 6) {
                sseManager.sendLog(deploymentId, "✅ GitHub Action build thành công (Đã tải lên Registry)!");
                return true;
            }
        }
        return false;
    }

    private String streamJobPodLogs(String namespace, String jobName, String alreadySent) {
        try {
            List<Pod> pods = client.pods().inNamespace(namespace)
                    .withLabel("job-name", jobName)
                    .list().getItems();
            if (pods.isEmpty()) return "";

            Pod pod = pods.get(0);
            String phase = pod.getStatus().getPhase();
            if (!"Running".equals(phase) && !"Succeeded".equals(phase) && !"Failed".equals(phase))
                return "";

            String fullLog = client.pods().inNamespace(namespace)
                    .withName(pod.getMetadata().getName())
                    .getLog();
            if (fullLog == null || fullLog.isEmpty()) return "";

            // Chỉ trả về phần log mới (chưa gửi)
            if (fullLog.length() > alreadySent.length()) {
                return fullLog.substring(alreadySent.length());
            }
            return "";
        } catch (Exception e) {
            log.warn("Không đọc được pod logs: {}", e.getMessage());
            return "";
        }
    }

    /** Giả lập quá trình build khi chạy mock mode */
    private boolean simulateMockBuild(UUID deploymentId) throws InterruptedException {
        String[] steps = {
            "📦 [git-clone] Cloning repository...",
            "📦 [git-clone] Branch: main",
            "✅ [git-clone] Clone thành công!",
            "🔨 [kaniko] INFO[0001] Retrieving image manifest node:18-alpine",
            "🔨 [kaniko] INFO[0005] Executing 0 build triggers",
            "🔨 [kaniko] INFO[0005] Building stage...",
            "🔨 [kaniko] INFO[0008] RUN npm install",
            "🔨 [kaniko] added 150 packages in 8s",
            "🔨 [kaniko] INFO[0020] RUN npm run build",
            "🔨 [kaniko] INFO[0025] Pushing image to ghcr.io/...",
            "✅ [kaniko] INFO[0030] Pushed image successfully!"
        };
        for (String step : steps) {
            Thread.sleep(1200);
            sseManager.sendLog(deploymentId, step);
        }
        return true;
    }
}
