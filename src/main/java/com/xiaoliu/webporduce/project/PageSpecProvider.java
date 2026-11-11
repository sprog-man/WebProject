package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PageSpecProvider {

    private final ObjectMapper objectMapper;
    private final JsonNode defaultSpec;

    public PageSpecProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try (InputStream stream = new ClassPathResource("ai/default-page.json").getInputStream()) {
            this.defaultSpec = objectMapper.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载默认页面模板", exception);
        }
    }

    public JsonNode defaultSpec() {
        return defaultSpec.deepCopy();
    }

    public JsonNode blankSpec(String title) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", 1);
        ObjectNode meta = root.putObject("meta");
        meta.put("title", title == null || title.isBlank() ? "未命名页面" : title);
        meta.put("description", "");
        ObjectNode theme = root.putObject("theme");
        theme.put("mode", "ink");
        theme.put("accent", "#ff6b3d");
        theme.put("titleScale", 100);
        theme.put("bodyScale", 100);
        theme.put("radius", 18);
        theme.put("font", "serif");
        theme.put("spacing", 100);
        root.putArray("sections");
        root.putObject("fieldMeta");
        return root;
    }
}
