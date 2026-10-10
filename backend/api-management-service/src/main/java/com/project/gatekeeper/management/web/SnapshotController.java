package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.api.CatalogDtos.AuditResponse;
import com.project.gatekeeper.management.api.CatalogDtos.SnapshotResponse;
import com.project.gatekeeper.management.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SnapshotController {

    private final CatalogService catalog;

    public SnapshotController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/internal/snapshot")
    public SnapshotResponse snapshot() {
        return catalog.snapshot();
    }

    @GetMapping("/api/audit")
    public List<AuditResponse> audit() {
        return catalog.recentAudits();
    }
}
