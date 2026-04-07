package com.minipaas.model;

/**
 * Enum trạng thái của một lần deploy
 *
 * Luồng trạng thái:
 *   PENDING → BUILDING → RUNNING
 *                     ↓
 *                  FAILED
 *   RUNNING → STOPPED (khi user xóa)
 */
public enum DeployStatus {
    PENDING,    // Vừa nhận yêu cầu, chưa bắt đầu
    BUILDING,   // Đang build Docker image (Kaniko đang chạy)
    RUNNING,    // Deploy thành công, app đang chạy trên K3s
    FAILED,     // Build hoặc deploy thất bại
    STOPPED     // User đã dừng / xóa app
}
