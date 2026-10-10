package com.project.gatekeeper.gateway.snapshot;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.Match;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;

import java.util.List;
import java.util.Locale;

public final class RouteMatcher {

    private RouteMatcher() {
    }

    public static Match match(List<RouteRule> routes, String method, String path) {
        String verb = method.toUpperCase(Locale.ROOT);
        RouteRule longest = null;
        for (RouteRule route : routes) {
            if (!pathMatches(route.publicPath(), path)) {
                continue;
            }
            if (longest == null || route.publicPath().length() > longest.publicPath().length()) {
                longest = route;
            }
        }
        if (longest == null) {
            return Match.none();
        }
        if (!longest.methods().contains(verb)) {
            return Match.methodRejected();
        }
        return Match.found(longest);
    }

    static boolean pathMatches(String publicPath, String path) {
        if ("/".equals(publicPath)) {
            return true;
        }
        return path.equals(publicPath) || path.startsWith(publicPath + "/");
    }
}
