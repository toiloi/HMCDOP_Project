package com.minipaas;

import com.minipaas.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ====================================================
 * MiniPaaS — Control Plane chính
 * ====================================================
 * Hệ thống này đóng vai trò "Control Plane" trong kiến trúc Hybrid Multi-Cloud.
 * 
 * Luồng xử lý khi có yêu cầu deploy:
 *   1. Nhận GitHub URL từ người dùng (qua REST API)
 *   2. Gửi lệnh cho K3s tạo Kaniko Build Job (build Docker image)
 *   3. Stream build logs về frontend qua SSE (Server-Sent Events)
 *   4. Khi build xong: tạo K8s Deployment + Service + IngressRoute (Traefik)
 *   5. Trả về URL public (custom domain hoặc nip.io) cho người dùng
 *
 * @EnableAsync: Cho phép pipeline deploy chạy bất đồng bộ
 *              (không block HTTP request)
 */
@SpringBootApplication
@EnableAsync
public class MiniPaasApplication {

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(MiniPaasApplication.class, args);
    }
}
