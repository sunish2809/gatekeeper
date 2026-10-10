package com.project.gatekeeper.gateway.snapshot;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.Caller;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.Match;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SnapshotStore {

    private record State(List<RouteRule> routes, Map<String, Caller> callers) {
    }

    private volatile State state = new State(List.of(), Map.of());

    public void replace(List<RouteRule> routes, Map<String, Caller> callers) {
        this.state = new State(List.copyOf(routes), Map.copyOf(callers));
    }

    public int size() {
        return state.routes().size();
    }

    public Match match(String method, String path) {
        return RouteMatcher.match(state.routes(), method, path);
    }

    public Caller caller(String apiKey) {
        return apiKey == null ? null : state.callers().get(apiKey);
    }
}
