package com.minipaas.service;

import com.minipaas.dto.NodeInfo;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service quản lý và theo dõi Worker Nodes trong K3s cluster.
 *
 * Trong kiến trúc Hybrid Multi-Cloud:
 * - Các node được kết nối với nhau qua Tailscale VPN
 * - Mỗi node có địa chỉ IP Tailscale (100.x.x.x)
 * - K3s scheduler tự động chọn node phù hợp theo CPU/RAM available
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NodeService {

    private final KubernetesClient client;

    @Value("${app.kubernetes.mock:true}")
    private boolean mockMode;

    /**
     * Lấy danh sách tất cả nodes trong K3s cluster.
     * Bao gồm cả master và worker nodes.
     */
    public List<NodeInfo> listNodes() {
        if (mockMode) {
            return getMockNodes();
        }

        List<NodeInfo> result = new ArrayList<>();
        List<Node> nodes = client.nodes().list().getItems();

        for (Node node : nodes) {
            NodeInfo info = new NodeInfo();
            info.setName(node.getMetadata().getName());
            info.setStatus(isNodeReady(node) ? "Ready" : "NotReady");

            // CPU và RAM từ node capacity
            var capacity = node.getStatus().getCapacity();
            if (capacity != null) {
                if (capacity.containsKey("cpu"))
                    info.setCpuCores(capacity.get("cpu").getAmount());
                if (capacity.containsKey("memory"))
                    info.setMemoryMi(capacity.get("memory").getAmount());
            }

            // Xác định role (master hay worker)
            var labels = node.getMetadata().getLabels();
            boolean isMaster = labels != null &&
                    labels.containsKey("node-role.kubernetes.io/master");
            info.setRoles(isMaster ? "master" : "worker");

            // Tailscale IP — thường được gán qua label hoặc annotation
            if (labels != null && labels.containsKey("tailscale-ip")) {
                info.setTailscaleIp(labels.get("tailscale-ip"));
            }

            result.add(info);
        }
        return result;
    }

    private boolean isNodeReady(Node node) {
        if (node.getStatus() == null || node.getStatus().getConditions() == null)
            return false;
        return node.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .findFirst()
                .map(c -> "True".equals(c.getStatus()))
                .orElse(false);
    }

    /** Mock data khi chạy local không có K3s */
    private List<NodeInfo> getMockNodes() {
        return List.of(
            new NodeInfo("k3s-master", "Ready", "100.64.0.1", "2", "12Gi", "master"),
            new NodeInfo("worker-01", "Ready", "100.64.0.2", "1", "6Gi", "worker"),
            new NodeInfo("worker-02", "Ready", "100.64.0.3", "1", "6Gi", "worker")
        );
    }
}
