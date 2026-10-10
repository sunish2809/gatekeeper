package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.aop.Audited;
import com.project.gatekeeper.management.api.CatalogDtos.RouteResponse;
import com.project.gatekeeper.management.api.CatalogDtos.UpsertRouteRequest;
import com.project.gatekeeper.management.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients/{clientId}/routes")
public class RouteController {

    private final CatalogService catalog;

    public RouteController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    @Audited(action = "route.create")
    public RouteResponse create(@PathVariable String clientId, @Valid @RequestBody UpsertRouteRequest request) {
        return catalog.createRoute(clientId, request);
    }

    @GetMapping
    public List<RouteResponse> list(@PathVariable String clientId) {
        return catalog.listRoutes(clientId);
    }

    @PutMapping("/{routeId}")
    @Audited(action = "route.update")
    public RouteResponse update(@PathVariable String clientId, @PathVariable String routeId,
                                @Valid @RequestBody UpsertRouteRequest request) {
        return catalog.updateRoute(clientId, routeId, request);
    }

    @DeleteMapping("/{routeId}")
    @Audited(action = "route.delete")
    public void delete(@PathVariable String clientId, @PathVariable String routeId) {
        catalog.deleteRoute(clientId, routeId);
    }
}
