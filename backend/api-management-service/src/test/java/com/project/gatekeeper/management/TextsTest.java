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
    void prefixesOrgSlugOnPublicPath() {
        assertEquals("foodfast", Texts.slug("FoodFast"));
        assertEquals("/foodfast/order", Texts.orgPublicPath("foodfast", "/order"));
        assertEquals("/foodfast/order", Texts.orgPublicPath("foodfast", "/foodfast/order"));
        assertEquals("/order", Texts.pathWithoutSlug("foodfast", "/foodfast/order"));
        assertThrows(ApiException.class, () -> Texts.slug("api"));
    }

    @Test
    void rejectsBadTarget() {
        assertThrows(ApiException.class, () -> Texts.targetUrl("ftp://example.com"));
    }

    @Test
    void normalizesOriginsAndIssuer() {
        assertEquals("https://xyz.com,http://localhost:3000",
                Texts.origins(List.of("https://XYZ.com/", "http://localhost:3000")));
        assertEquals("https://accounts.google.com", Texts.issuer("https://accounts.google.com"));
        assertThrows(ApiException.class, () -> Texts.origins(List.of("https://xyz.com/app")));
        assertThrows(ApiException.class, () -> Texts.issuer("http://evil.example"));
    }
}
