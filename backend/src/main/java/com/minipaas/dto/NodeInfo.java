package com.minipaas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin về một K3s worker node.
 * Trả về từ /api/v1/nodes endpoint.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInfo {
    private String name;        // Tên node trong K3s
    private String status;      // "Ready" hoặc "NotReady"
    private String tailscaleIp; // IP trong Tailscale mesh
    private String cpuCores;    // Số CPU cores
    private String memoryMi;    // Tổng RAM (Mi)
    private String roles;       // "master" hoặc "worker"
}
