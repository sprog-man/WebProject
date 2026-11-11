package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SpecPatchApplier {

    public JsonNode apply(JsonNode spec, JsonNode patches) {
        if (patches == null || !patches.isArray()) {
            throw new AiProviderException("模型没有返回有效的 patches");
        }
        JsonNode next = spec.deepCopy();
        for (JsonNode patch : patches) {
            String op = patch.path("op").asText("");
            String path = patch.path("path").asText("");
            if (!path.startsWith("/") || path.startsWith("/fieldMeta")) {
                throw new AiProviderException("模型返回了不允许的修改路径：" + path);
            }
            if (!(path.startsWith("/theme") || path.startsWith("/meta") || path.startsWith("/sections"))) {
                throw new AiProviderException("模型返回了不允许的修改路径：" + path);
            }
            if (!"replace".equals(op) && !"add".equals(op) && !"remove".equals(op)) {
                throw new AiProviderException("模型返回了不支持的 patch 操作：" + op);
            }
            applyOne(next, op, path, patch.get("value"));
        }
        return next;
    }

    private void applyOne(JsonNode root, String op, String path, JsonNode value) {
        int slash = path.lastIndexOf('/');
        String parentPath = slash <= 0 ? "" : path.substring(0, slash);
        String last = path.substring(slash + 1);
        JsonNode parent = parentPath.isEmpty() ? root : root.at(parentPath);
        if (parent.isMissingNode() || parent.isNull()) {
            throw new AiProviderException("模型修改了不存在的路径：" + path);
        }

        if ("remove".equals(op)) {
            if (parent instanceof ObjectNode objectNode) {
                objectNode.remove(last);
            } else if (parent instanceof ArrayNode arrayNode) {
                int index = parseIndex(last, arrayNode.size(), false);
                if (index >= 0 && index < arrayNode.size()) {
                    arrayNode.remove(index);
                }
            } else {
                throw new AiProviderException("无法删除路径：" + path);
            }
            return;
        }

        if (value == null) {
            throw new AiProviderException("patch 缺少 value：" + path);
        }

        if (parent instanceof ObjectNode objectNode) {
            objectNode.set(last, value);
        } else if (parent instanceof ArrayNode arrayNode) {
            if ("-".equals(last)) {
                arrayNode.add(value);
            } else {
                int index = parseIndex(last, arrayNode.size(), true);
                if ("replace".equals(op) && index >= arrayNode.size()) {
                    throw new AiProviderException("数组索引超出范围：" + path);
                }
                if (index >= 0 && index <= arrayNode.size()) {
                    if (index == arrayNode.size()) {
                        arrayNode.add(value);
                    } else {
                        arrayNode.set(index, value);
                    }
                } else {
                    throw new AiProviderException("数组索引超出范围：" + path);
                }
            }
        } else {
            throw new AiProviderException("无法修改路径：" + path);
        }
    }

    private int parseIndex(String segment, int size, boolean allowEnd) {
        try {
            int index = Integer.parseInt(segment);
            if (index < 0 || index > size || (!allowEnd && index >= size)) {
                throw new NumberFormatException();
            }
            return index;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PATCH_INVALID", "模型返回了无效的数组索引");
        }
    }
}
