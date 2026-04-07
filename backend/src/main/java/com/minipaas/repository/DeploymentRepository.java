package com.minipaas.repository;

import com.minipaas.model.Deployment;
import com.minipaas.model.DeployStatus;
import com.minipaas.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
    List<Deployment> findByUserOrderByCreatedAtDesc(User user);
    List<Deployment> findByStatus(DeployStatus status);
}
