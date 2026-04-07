package com.minipaas.repository;

import com.minipaas.model.BuildLog;
import com.minipaas.model.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuildLogRepository extends JpaRepository<BuildLog, Long> {
    List<BuildLog> findByDeploymentOrderByCreatedAtAsc(Deployment deployment);
}
