package com.project.gatekeeper.gateway.support;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FlatJson {

    private FlatJson() {
    }

    public static String write(Map<String, ?> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return json.append('}').toString();
    }

    public static Map<String, String> read(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        Cursor cursor = new Cursor();
        skip(json, cursor);
        expect(json, cursor, '{');
        skip(json, cursor);
        if (peek(json, cursor) == '}') {
            return values;
        }
        while (cursor.i < json.length()) {
            expect(json, cursor, '"');
            String key = readString(json, cursor);
            skip(json, cursor);
            expect(json, cursor, ':');
            skip(json, cursor);
            values.put(key, readValue(json, cursor));
            skip(json, cursor);
            char next = peek(json, cursor);
            if (next == ',') {
                cursor.i++;
                skip(json, cursor);
                continue;
            }
            if (next == '}') {
                return values;
            }
            throw new IllegalArgumentException("Expected comma or end of object");
        }
        throw new IllegalArgumentException("Unclosed object");
    }

    private static String readValue(String json, Cursor cursor) {
        char current = peek(json, cursor);
        if (current == '"') {
            cursor.i++;
            return readString(json, cursor);
        }
        if (json.startsWith("null", cursor.i)) {
            cursor.i += 4;
            return null;
        }
        if (json.startsWith("true", cursor.i)) {
            cursor.i += 4;
            return "true";
        }
        if (json.startsWith("false", cursor.i)) {
            cursor.i += 5;
            return "false";
        }
        int start = cursor.i;
        while (cursor.i < json.length()) {
            char c = json.charAt(cursor.i);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                break;
            }
            cursor.i++;
        }
        if (start == cursor.i) {
            throw new IllegalArgumentException("Missing value");
        }
        return json.substring(start, cursor.i);
    }

    private static String readString(String json, Cursor cursor) {
        StringBuilder value = new StringBuilder();
        while (cursor.i < json.length()) {
            char current = json.charAt(cursor.i++);
            if (current == '"') {
                return value.toString();
            }
            if (current == '\\') {
                char escaped = json.charAt(cursor.i++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> value.append(escaped);
                }
            } else {
                value.append(current);
            }
        }
        throw new IllegalArgumentException("Unclosed string");
    }

    private static void skip(String json, Cursor cursor) {
        while (cursor.i < json.length() && Character.isWhitespace(json.charAt(cursor.i))) {
            cursor.i++;
        }
    }

    private static char peek(String json, Cursor cursor) {
        if (cursor.i >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of json");
        }
        return json.charAt(cursor.i);
    }

    private static void expect(String json, Cursor cursor, char expected) {
        if (peek(json, cursor) != expected) {
            throw new IllegalArgumentException("Expected " + expected);
        }
        cursor.i++;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static final class Cursor {
        private int i;
    }
}
