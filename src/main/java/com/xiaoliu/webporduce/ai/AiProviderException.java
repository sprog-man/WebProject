package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;

public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
