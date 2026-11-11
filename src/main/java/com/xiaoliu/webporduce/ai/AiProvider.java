package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;

public interface AiProvider {

    String name();

    boolean available();

    AiResult generate(String projectName, JsonNode currentSpec, String message);

    AiResult edit(JsonNode currentSpec, String message);

    /**
     * 用一次最小请求确认地址、模型和密钥是否可用。
     */
    default void testConnection() {
    }
}
