package com.project.gatekeeper.analytics;

import com.project.gatekeeper.analytics.support.FlatJson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyticsServiceApplicationTests {

    @Test
    void readsGatewayEventJson() {
        Map<String, String> values = FlatJson.read("{\"eventId\":\"e1\",\"status\":201,\"error\":null}");
        assertEquals("e1", values.get("eventId"));
        assertEquals("201", values.get("status"));
        assertNull(values.get("error"));
    }
}
