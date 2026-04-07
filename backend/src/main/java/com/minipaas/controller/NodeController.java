package com.minipaas.controller;

import com.minipaas.dto.NodeInfo;
import com.minipaas.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller trả về thông tin về K3s cluster nodes.
 * Hiển thị trên trang "Nodes" của Dashboard.
 */
@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping
    public ResponseEntity<List<NodeInfo>> listNodes() {
        return ResponseEntity.ok(nodeService.listNodes());
    }
}
