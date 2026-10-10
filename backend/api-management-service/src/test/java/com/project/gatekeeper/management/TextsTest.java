package com.project.gatekeeper.management;

import com.project.gatekeeper.management.support.Texts;
import com.project.gatekeeper.management.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextsTest {

    @Test
    void normalizesPublicPathAndHeaders() {
        assertEquals("/shop", Texts.publicPath("/shop/"));
        assertEquals("GET,POST", Texts.methods(List.of("get", "POST", "get")));
        assertEquals(Map.of("X-Source", "gatekeeper"), Texts.decodeHeaders(Texts.encodeHeaders(Map.of("X-Source", "gatekeeper"))));
    }

    @Test
    void rejectsBadTarget() {
        assertThrows(ApiException.class, () -> Texts.targetUrl("ftp://example.com"));
    }
}
