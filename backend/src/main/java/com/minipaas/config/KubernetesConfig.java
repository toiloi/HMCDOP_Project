package com.minipaas.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Cấu hình Fabric8 Kubernetes Client
 *
 * Fabric8 là thư viện Java để giao tiếp với Kubernetes API Server.
 * Trong hệ thống này, nó được dùng để:
 *   - Tạo Namespace cho mỗi deployment
 *   - Tạo Kaniko Job (build Docker image từ GitHub)
 *   - Tạo K8s Deployment, Service, IngressRoute
 *   - Theo dõi logs của build job
 *   - List nodes trong cluster
 *
 * Kết nối:
 *   - Trên Oracle Cloud VM (production): đọc /etc/rancher/k3s/k3s.yaml
 *   - Trên máy local dev: đọc ~/.kube/config (hoặc mock mode)
 */
@Configuration
@Slf4j
public class KubernetesConfig {

    @Value("${app.kubernetes.kubeconfig:/etc/rancher/k3s/k3s.yaml}")
    private String kubeconfigPath;

    @Value("${app.kubernetes.mock:true}")
    private boolean mockMode;

    @Bean
    public KubernetesClient kubernetesClient() {
        if (mockMode) {
            log.warn("==============================================");
            log.warn("  K8s MOCK MODE đang bật (K8S_MOCK=true)");
            log.warn("  Các lệnh K8s sẽ được giả lập (không thật)");
            log.warn("  Để dùng K3s thật: đặt K8S_MOCK=false");
            log.warn("==============================================");
            // Trả về client với config trống (sẽ không dùng thật)
            return new KubernetesClientBuilder().build();
        }

        File kubeconfigFile = new File(kubeconfigPath);
        if (!kubeconfigFile.exists()) {
            log.error("Không tìm thấy kubeconfig tại: {}", kubeconfigPath);
            log.error("Đặt biến môi trường KUBECONFIG hoặc bật K8S_MOCK=true");
            throw new RuntimeException("Kubeconfig không tìm thấy: " + kubeconfigPath);
        }

        Config config = new ConfigBuilder()
                .withFile(kubeconfigFile)
                .build();

        log.info("Kết nối K3s tại: {}", config.getMasterUrl());
        return new KubernetesClientBuilder()
                .withConfig(config)
                .build();
    }
}
