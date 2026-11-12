package com.xiaoliu.webporduce.image;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PageSpecImages {

    private PageSpecImages() {
    }

    public static boolean isSupportedPath(String path) {
        if (path == null) {
            return false;
        }
        return path.equals("props.image")
                || path.matches("props\\.items\\.\\d+\\.image");
    }

    public static JsonNode findImage(JsonNode spec, String sectionId, String path) {
        if (spec == null || sectionId == null || path == null) {
            return null;
        }
        for (JsonNode section : spec.path("sections")) {
            if (!sectionId.equals(section.path("id").asText(""))) {
                continue;
            }
            JsonNode current = section;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    return null;
                }
                if (current.isArray()) {
                    try {
                        current = current.get(Integer.parseInt(segment));
                    } catch (NumberFormatException exception) {
                        return null;
                    }
                } else {
                    current = current.get(segment);
                }
            }
            return current != null && current.isObject() ? current : null;
        }
        return null;
    }

    public static void setImageAsset(JsonNode image, String assetUrl, String assetId) {
        if (!(image instanceof ObjectNode object)) {
            return;
        }
        object.put("src", assetUrl);
        object.put("assetId", assetId);
    }

    public static String promptOf(JsonNode image) {
        if (image == null) {
            return "";
        }
        return image.path("prompt").asText("").trim();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
