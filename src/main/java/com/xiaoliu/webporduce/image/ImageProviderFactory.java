package com.xiaoliu.webporduce.image;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class ImageProviderFactory {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ImageProviderFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ImageProvider create(ImageAiRuntimeConfig runtime) {
        return switch (runtime.type()) {
            case OPENAI_IMAGES -> new OpenAiImageProvider(runtime, objectMapper, httpClient);
        };
    }
}
