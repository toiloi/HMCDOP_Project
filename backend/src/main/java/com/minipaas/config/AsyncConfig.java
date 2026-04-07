package com.minipaas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình Async Executor cho Deploy Pipeline
 *
 * Mỗi lần deploy sẽ chạy trong một thread riêng biệt (@Async).
 * Điều này đảm bảo HTTP request trả về ngay lập tức (deploymentId),
 * trong khi quá trình Kaniko build có thể mất 5-15 phút.
 *
 * ThreadPool config:
 *   - corePoolSize=5: Tối đa 5 deploy đồng thời
 *   - maxPoolSize=10: Tăng lên 10 khi cần thiết
 *   - queueCapacity=25: Hàng đợi chờ khi pool đầy
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "deployTaskExecutor")
    public Executor deployTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("deploy-");
        executor.initialize();
        return executor;
    }
}
