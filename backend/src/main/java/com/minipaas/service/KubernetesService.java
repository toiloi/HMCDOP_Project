package com.minipaas.service;

import com.minipaas.dto.NodeInfo;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service giao tiếp với K3s qua Fabric8 Kubernetes Client.
 *
 * Đây là layer trừu tượng hóa tất cả Kubernetes API calls.
 * Mỗi lần deploy sẽ tạo ra các tài nguyên K8s sau:
 *
 *   Namespace "deploy-{id}"
 *     ├── Secret "ghcr-credentials"    (để pull image từ GHCR)
 *     ├── Job "build-{id}"             (Kaniko build job)
 *     ├── Deployment "app-{shortId}"   (chạy image đã build)
 *     ├── Service "app-{shortId}-svc"  (ClusterIP)
 *     └── IngressRoute "app-route"     (Traefik → URL public)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesService {

    private final KubernetesClient client;

    @Value("${app.kubernetes.mock:true}")
    private boolean mockMode;

    @Value("${app.registry.ghcr-user}")
    private String ghcrUser;

    @Value("${app.registry.ghcr-token}")
    private String ghcrToken;

    @Value("${app.deployment.domain:}")
    private String deploymentDomain;

    @Value("${app.deployment.ingress-public-ip:127.0.0.1}")
    private String ingressPublicIp;

    @Value("${app.deployment.url-scheme:http}")
    private String urlScheme;

    // ─────────────────────────────────────────────────
    // NAMESPACE
    // ─────────────────────────────────────────────────

    /** Tạo Kubernetes Namespace để isolate tài nguyên của deployment */
    public void createNamespace(String namespaceName) {
        if (mockMode) { log.info("[MOCK] createNamespace: {}", namespaceName); return; }
        Namespace ns = new NamespaceBuilder()
                .withNewMetadata()
                    .withName(namespaceName)
                    .addToLabels("managed-by", "minipaas")
                .endMetadata()
                .build();
        client.namespaces().resource(ns).createOrReplace();
        log.info("✅ Namespace tạo thành công: {}", namespaceName);
    }

    /** Xóa Namespace (và tất cả tài nguyên bên trong) khi user stop app */
    public void deleteNamespace(String namespaceName) {
        if (mockMode) { log.info("[MOCK] deleteNamespace: {}", namespaceName); return; }
        client.namespaces().withName(namespaceName).delete();
        log.info("🗑️  Namespace đã xóa: {}", namespaceName);
    }

    // ─────────────────────────────────────────────────
    // SECRET (GHCR Credentials)
    // ─────────────────────────────────────────────────

    /**
     * Tạo K8s Secret chứa credentials để push image lên GHCR.
     * Secret này được mount vào Kaniko container tại /kaniko/.docker/config.json
     */
    public void createRegistrySecret(String namespace) {
        if (mockMode) { log.info("[MOCK] createRegistrySecret in: {}", namespace); return; }

        // Format chuẩn Docker registry auth
        String auth = Base64.getEncoder()
                .encodeToString((ghcrUser + ":" + ghcrToken).getBytes());
        String dockerConfigJson = String.format(
                "{\"auths\":{\"ghcr.io\":{\"username\":\"%s\",\"password\":\"%s\",\"auth\":\"%s\"}}}",
                ghcrUser, ghcrToken, auth
        );

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName("ghcr-credentials")
                    .withNamespace(namespace)
                .endMetadata()
                .withType("kubernetes.io/dockerconfigjson")
                .addToData(".dockerconfigjson",
                        Base64.getEncoder().encodeToString(dockerConfigJson.getBytes()))
                .build();

        client.secrets().inNamespace(namespace).resource(secret).createOrReplace();
        log.info("✅ Registry secret tạo trong namespace: {}", namespace);
    }

    // ─────────────────────────────────────────────────
    // DEPLOYMENT
    // ─────────────────────────────────────────────────

    /**
     * Tạo K8s Deployment để chạy Docker image của app người dùng.
     * K3s scheduler sẽ tự động chọn worker node phù hợp.
     */
    public void createAppDeployment(String namespace, String appName,
                                     String imageTag, int containerPort) {
        if (mockMode) {
            log.info("[MOCK] createDeployment: {} (image: {})", appName, imageTag);
            return;
        }

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                    .withName(appName)
                    .withNamespace(namespace)
                    .addToLabels("app", appName)
                    .addToLabels("managed-by", "minipaas")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                        .addToMatchLabels("app", appName)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", appName)
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName(appName)
                                .withImage(imageTag)
                                .addNewPort()
                                    .withContainerPort(containerPort)
                                .endPort()
                                // Resource limits (phù hợp với t2.micro / Oracle ARM free)
                                .withNewResources()
                                    .addToRequests("memory", new Quantity("128Mi"))
                                    .addToRequests("cpu", new Quantity("100m"))
                                    .addToLimits("memory", new Quantity("512Mi"))
                                    .addToLimits("cpu", new Quantity("500m"))
                                .endResources()
                            .endContainer()
                            // Pull image từ GHCR
                            .addNewImagePullSecret()
                                .withName("ghcr-credentials")
                            .endImagePullSecret()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        client.apps().deployments().inNamespace(namespace).resource(deployment).createOrReplace();
        log.info("✅ Deployment tạo: {} trong {}", appName, namespace);
    }

    // ─────────────────────────────────────────────────
    // SERVICE
    // ─────────────────────────────────────────────────

    /** Tạo K8s Service (ClusterIP) để Traefik có thể route traffic đến app */
    public void createAppService(String namespace, String appName, int targetPort) {
        if (mockMode) { log.info("[MOCK] createService: {}-svc", appName); return; }

        io.fabric8.kubernetes.api.model.Service svc = new ServiceBuilder()
                .withNewMetadata()
                    .withName(appName + "-svc")
                    .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .addToSelector("app", appName)
                    .addNewPort()
                        .withPort(80)
                        .withTargetPort(new IntOrString(targetPort))
                    .endPort()
                .endSpec()
                .build();

        client.services().inNamespace(namespace).resource(svc).createOrReplace();
        log.info("✅ Service tạo: {}-svc", appName);
    }

    // ─────────────────────────────────────────────────
    // INGRESS ROUTE (Traefik CRD)
    // ─────────────────────────────────────────────────

    /**
     * Tạo Traefik IngressRoute để expose app ra internet qua nip.io.
     *
     * nip.io là wildcard DNS service miễn phí:
     *   app-abc.1.2.3.4.nip.io → resolve về 1.2.3.4
     *
     * Traefik (có sẵn trong K3s) sẽ route traffic từ hostname này
     * đến đúng Service trong cluster.
     *
     * @return URL public của app (vd: http://app-abc.1.2.3.4.nip.io)
     */
    public String createIngressRoute(String namespace, String appName) {
        String host = buildPublicHost(appName);
        String publicUrl = normalizeScheme(urlScheme) + "://" + host;

        if (mockMode) {
            log.info("[MOCK] createIngressRoute: {} -> {}", host, publicUrl);
            return publicUrl;
        }

        // Traefik IngressRoute là CRD — dùng GenericKubernetesResource
        String ingressRouteYaml = """
                apiVersion: traefik.containo.us/v1alpha1
                kind: IngressRoute
                metadata:
                  name: %s-route
                  namespace: %s
                spec:
                  entryPoints:
                    - web
                  routes:
                    - match: Host(`%s`)
                      kind: Rule
                      services:
                        - name: %s-svc
                          port: 80
                """.formatted(appName, namespace, host, appName);

        var context = new io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext.Builder()
                .withGroup("traefik.containo.us")
                .withVersion("v1alpha1")
                .withKind("IngressRoute")
                .withNamespaced(true)
                .build();

        var resource = io.fabric8.kubernetes.client.utils.Serialization
                .unmarshal(ingressRouteYaml,
                        io.fabric8.kubernetes.api.model.GenericKubernetesResource.class);

        client.genericKubernetesResources(context)
                .inNamespace(namespace)
                .resource(resource)
                .createOrReplace();

        log.info("IngressRoute created: {}", publicUrl);
        return publicUrl;
    }

    private String buildPublicHost(String appName) {
        String segment = sanitizeHostnameSegment(appName);
        if (deploymentDomain != null && !deploymentDomain.isBlank()) {
            return segment + "." + deploymentDomain.trim().toLowerCase();
        }
        return segment + "." + ingressPublicIp + ".nip.io";
    }

    private static String sanitizeHostnameSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "app";
        }
        String s = raw.toLowerCase().replaceAll("[^a-z0-9.-]", "-").replaceAll("-{2,}", "-");
        s = s.replaceAll("^[.-]+|[.-]+$", "");
        return s.isBlank() ? "app" : s;
    }

    private static String normalizeScheme(String scheme) {
        if (scheme == null || scheme.isBlank()) {
            return "http";
        }
        String s = scheme.trim().toLowerCase();
        return "https".equals(s) ? "https" : "http";
    }
}
