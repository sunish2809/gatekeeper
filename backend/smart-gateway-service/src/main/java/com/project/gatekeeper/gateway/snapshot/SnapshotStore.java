package com.project.gatekeeper.gateway.snapshot;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.Match;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SnapshotStore {

    private volatile List<RouteRule> routes = List.of();

    public void replace(List<RouteRule> routes) {
        this.routes = List.copyOf(routes);
    }

    public int size() {
        return routes.size();
    }

    public Match match(String method, String path) {
        return RouteMatcher.match(routes, method, path);
    }
}
