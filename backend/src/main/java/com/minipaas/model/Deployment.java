package com.minipaas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: Một lần deploy của người dùng.
 *
 * Mỗi deployment tương ứng với:
 * - 1 Kaniko Build Job trên K3s (để build Docker image)
 * - 1 K8s Namespace (tên: "deploy-{id}")
 * - 1 K8s Deployment + Service + Traefik IngressRoute
 */
@Entity
@Table(name = "deployments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Liên kết với user tạo deployment này */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** URL GitHub của project (public) */
    @Column(name = "github_url", nullable = false)
    private String githubUrl;

    /** Branch cần deploy, mặc định "main" */
    @Column(nullable = false)
    @Builder.Default
    private String branch = "main";

    /** Tên app (lấy từ repo name), dùng làm K8s resource name */
    @Column(name = "app_name")
    private String appName;

    /** Port mà ứng dụng trong container expose */
    @Column(nullable = false)
    @Builder.Default
    private int port = 8080;

    /** Trạng thái hiện tại của deployment */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeployStatus status = DeployStatus.PENDING;

    /** URL public để truy cập app (VD: http://app-abc.1.2.3.4.nip.io) */
    @Column
    private String url;

    /** Tag image đã build (VD: ghcr.io/user/app-id:latest) */
    @Column(name = "image_tag")
    private String imageTag;

    /** Tên K8s Namespace chứa tài nguyên của deployment này */
    @Column(name = "k8s_namespace")
    private String k8sNamespace;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
