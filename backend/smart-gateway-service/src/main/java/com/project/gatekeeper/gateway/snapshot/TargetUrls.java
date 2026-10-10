package com.project.gatekeeper.gateway.snapshot;

import java.net.URI;

public final class TargetUrls {

    private TargetUrls() {
    }

    public static URI build(String publicPath, String targetUrl, boolean stripPrefix, String path, String query) {
        String suffix = path;
        if (stripPrefix) {
            suffix = path.substring(Math.min(publicPath.length(), path.length()));
            if (suffix.isEmpty()) {
                suffix = "/";
            }
        }
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        String base = targetUrl.endsWith("/") ? targetUrl.substring(0, targetUrl.length() - 1) : targetUrl;
        String url = base + suffix;
        if (query != null && !query.isEmpty()) {
            url = url + "?" + query;
        }
        return URI.create(url);
    }
}
