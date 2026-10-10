package com.project.gatekeeper.management.support;

import com.project.gatekeeper.management.web.ApiException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class Texts {

    private Texts() {
    }

    public static String publicPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_path", "publicPath is required");
        }
        String path = raw.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.contains(" ") || path.contains("?")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_path", "publicPath cannot contain spaces or a query");
        }
        return path;
    }

    public static String targetUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_target", "targetUrl is required");
        }
        String value = raw.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_target", "targetUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_target", "targetUrl must be an http or https URL");
        }
        return value;
    }

    public static String methods(List<String> methods) {
        if (methods == null || methods.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_methods", "At least one HTTP method is required");
        }
        String joined = methods.stream()
                .map(method -> method == null ? "" : method.trim().toUpperCase(Locale.ROOT))
                .filter(method -> !method.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_methods", "At least one HTTP method is required");
        }
        return joined;
    }

    public static String csv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public static String encodeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .map(entry -> entry.getKey().trim() + "=" + (entry.getValue() == null ? "" : entry.getValue()))
                .collect(Collectors.joining("\n"));
    }

    public static Map<String, String> decodeHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return headers;
        }
        for (String line : raw.split("\n")) {
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            headers.put(line.substring(0, split).trim(), line.substring(split + 1));
        }
        return headers;
    }

    public static int or(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    public static boolean or(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
