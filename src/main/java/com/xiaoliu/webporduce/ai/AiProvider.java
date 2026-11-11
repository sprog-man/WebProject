package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;

public interface AiProvider {

    String name();

    boolean available();

    AiResult generate(String projectName, JsonNode currentSpec, String message);

    AiResult edit(JsonNode currentSpec, String message);
}
