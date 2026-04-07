package com.minipaas.dto;

import com.minipaas.model.Deployment;
import com.minipaas.model.DeployStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO trả về cho API response về một deployment.
 * Không expose entity trực tiếp để tránh leak data nhạy cảm.
 */
@Data
public class DeploymentResponse {

    private UUID id;
    private String githubUrl;
    private String branch;
    private String appName;
    private int port;
    private DeployStatus status;
    private String url;
    private String imageTag;
    private String k8sNamespace;
    private LocalDateTime createdAt;

    /** Tạo DTO từ entity */
    public static DeploymentResponse from(Deployment d) {
        DeploymentResponse r = new DeploymentResponse();
        r.setId(d.getId());
        r.setGithubUrl(d.getGithubUrl());
        r.setBranch(d.getBranch());
        r.setAppName(d.getAppName());
        r.setPort(d.getPort());
        r.setStatus(d.getStatus());
        r.setUrl(d.getUrl());
        r.setImageTag(d.getImageTag());
        r.setK8sNamespace(d.getK8sNamespace());
        r.setCreatedAt(d.getCreatedAt());
        return r;
    }
}
