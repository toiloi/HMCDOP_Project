package com.minipaas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity: Một dòng log của quá trình build/deploy.
 * Được lưu vào DB để xem lại history sau khi build xong.
 * Đồng thời cũng được stream real-time về frontend qua SSE.
 */
@Entity
@Table(name = "build_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(name = "log_line", columnDefinition = "TEXT", nullable = false)
    private String logLine;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
