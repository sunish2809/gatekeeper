package com.project.gatekeeper.gateway;

import com.project.gatekeeper.gateway.snapshot.GatewayModels.Match;
import com.project.gatekeeper.gateway.snapshot.GatewayModels.RouteRule;
import com.project.gatekeeper.gateway.snapshot.RouteMatcher;
import com.project.gatekeeper.gateway.snapshot.TargetUrls;
import com.project.gatekeeper.gateway.support.FlatJson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRoutingTest {

    @Test
    void longestPathWinsAndMethodMustMatch() {
        RouteRule shop = rule("/shop", Set.of("GET"));
        RouteRule orders = rule("/shop/orders", Set.of("POST"));
        Match match = RouteMatcher.match(List.of(shop, orders), "POST", "/shop/orders/10");
        assertEquals("/shop/orders", match.route().publicPath());

        Match wrongMethod = RouteMatcher.match(List.of(shop), "POST", "/shop/orders");
        assertTrue(wrongMethod.methodNotAllowed());
        assertNull(wrongMethod.route());
    }

    @Test
    void stripsPublicPrefixWhenForwarding() {
        assertEquals(
                "http://localhost:9000/orders/10?x=1",
                TargetUrls.build("/shop", "http://localhost:9000", true, "/shop/orders/10", "x=1").toString());
        assertEquals(
                "http://localhost:9000/shop/orders",
                TargetUrls.build("/shop", "http://localhost:9000", false, "/shop/orders", null).toString());
    }

    @Test
    void flatJsonRoundTrip() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", 200);
        payload.put("error", null);
        payload.put("path", "a\"b");
        String json = FlatJson.write(payload);
        Map<String, String> values = FlatJson.read(json);
        assertEquals("200", values.get("status"));
        assertNull(values.get("error"));
        assertEquals("a\"b", values.get("path"));
    }

    private RouteRule rule(String path, Set<String> methods) {
        return new RouteRule("r", "c", "key", "admin", path, "http://localhost:9000",
                methods, 1000, true, Set.of(), 10, 60, false, true, Map.of(), false);
    }
}
