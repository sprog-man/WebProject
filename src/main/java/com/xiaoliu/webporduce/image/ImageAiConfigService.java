package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.ai.AiConfigCryptoService;
import com.xiaoliu.webporduce.asset.Asset;
import com.xiaoliu.webporduce.asset.AssetService;
import com.xiaoliu.webporduce.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ImageAiConfigService {

    private static final Logger log = LoggerFactory.getLogger(ImageAiConfigService.class);

    private final ImageAiConfigRepository repository;
    private final AiConfigCryptoService cryptoService;
    private final ImageProviderFactory providerFactory;
    private final AssetService assetService;

    public ImageAiConfigService(ImageAiConfigRepository repository,
                                AiConfigCryptoService cryptoService,
                                ImageProviderFactory providerFactory,
                                AssetService assetService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.providerFactory = providerFactory;
        this.assetService = assetService;
    }

    @Transactional(readOnly = true)
    public List<ImageAiConfigResponse> list(UUID ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId)
                .stream()
                .map(ImageAiConfigResponse::from)
                .toList();
    }

    public List<ImageProviderPresetResponse> presets() {
        return Arrays.stream(ImageProviderType.values())
                .map(type -> new ImageProviderPresetResponse(
                        type.name(),
                        type.label(),
                        type.defaultBaseUrl(),
                        type.defaultModel(),
                        type.description()
                ))
                .toList();
    }

    @Transactional
    public ImageAiConfigResponse create(UUID ownerId, ImageAiConfigRequest request) {
        ImageProviderType type = ImageProviderType.parse(request.providerType());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        String size = ImageSizes.normalize(request.defaultSize());
        String quality = normalizeQuality(request.defaultQuality());
        boolean makeDefault = request.defaultConfig() || repository.countByOwnerId(ownerId) == 0;
        if (makeDefault) {
            repository.clearDefault(ownerId);
        }
        ImageAiConfig config = new ImageAiConfig(
                ownerId,
                request.name().trim(),
                type.name(),
                baseUrl,
                model,
                cryptoService.encrypt(apiKey),
                keyHint(apiKey),
                size,
                quality,
                makeDefault
        );
        repository.save(config);
        return ImageAiConfigResponse.from(config);
    }

    @Transactional
    public ImageAiConfigResponse update(UUID ownerId, UUID configId, ImageAiConfigRequest request) {
        ImageAiConfig config = requireOwned(ownerId, configId);
        ImageProviderType type = ImageProviderType.parse(request.providerType());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String size = ImageSizes.normalize(request.defaultSize());
        String quality = normalizeQuality(request.defaultQuality());
        boolean wasDefault = config.isDefaultConfig();
        String encrypted = config.getApiKeyEncrypted();
        String hint = config.getApiKeyHint();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String apiKey = request.apiKey().trim();
            encrypted = cryptoService.encrypt(apiKey);
            hint = keyHint(apiKey);
        }

        ImageAiConfig nextDefault = null;
        if (request.defaultConfig()) {
            repository.clearDefault(ownerId);
        } else if (wasDefault) {
            repository.clearDefault(ownerId);
            nextDefault = repository
                    .findFirstByOwnerIdAndIdNotOrderByCreatedAtAsc(ownerId, config.getId())
                    .orElse(null);
            if (nextDefault != null) {
                nextDefault.setDefaultConfig(true);
            }
        }

        config.update(
                request.name().trim(),
                type.name(),
                baseUrl,
                model,
                encrypted,
                hint,
                size,
                quality,
                request.defaultConfig()
        );
        repository.save(config);
        if (nextDefault != null) {
            repository.save(nextDefault);
        }
        return ImageAiConfigResponse.from(config);
    }

    @Transactional
    public void delete(UUID ownerId, UUID configId) {
        ImageAiConfig config = requireOwned(ownerId, configId);
        boolean wasDefault = config.isDefaultConfig();
        repository.delete(config);
        repository.flush();
        if (wasDefault) {
            repository.findFirstByOwnerIdOrderByCreatedAtAsc(ownerId)
                    .ifPresent(next -> {
                        next.setDefaultConfig(true);
                        repository.save(next);
                    });
        }
    }

    @Transactional
    public ImageAiConfigResponse setDefault(UUID ownerId, UUID configId) {
        ImageAiConfig config = requireOwned(ownerId, configId);
        repository.clearDefault(ownerId);
        config.setDefaultConfig(true);
        repository.save(config);
        return ImageAiConfigResponse.from(config);
    }

    public ImageAiConfigTestResponse test(UUID ownerId, UUID configId) {
        ImageAiConfig config = requireOwned(ownerId, configId);
        long started = System.currentTimeMillis();
        ImageAiRuntimeConfig runtime;
        try {
            runtime = toRuntime(config);
        } catch (Exception exception) {
            log.info("Image AI config {} could not be decrypted: {}", configId, exception.getMessage());
            return failure(configId, config, started, exception);
        }

        try {
            ImageGenerationResult generated = providerFactory.create(runtime).testConnection();
            if (generated == null || generated.bytes() == null || generated.bytes().length == 0) {
                return new ImageAiConfigTestResponse(
                        false,
                        "图像服务没有返回图片数据",
                        System.currentTimeMillis() - started,
                        runtime.model(),
                        runtime.size(),
                        null,
                        null,
                        null,
                        null
                );
            }

            String previewUrl = null;
            Integer width = null;
            Integer height = null;
            String message = "连接成功，测试图片已真实生成。可以在卡片上查看预览。";
            try {
                Asset asset = assetService.storeGenerated(
                        ownerId,
                        null,
                        generated.bytes(),
                        generated.mimeType(),
                        "image-ai-test." + extensionFor(generated.mimeType()),
                        "图像 AI 连接测试"
                );
                previewUrl = "/api/assets/" + asset.getId() + "/content";
                width = asset.getWidth();
                height = asset.getHeight();
            } catch (Exception exception) {
                log.warn("Image AI test for {} generated an image but preview could not be stored: {}",
                        configId, exception.getMessage());
                message = "连接成功，测试图片已生成，但预览保存失败：" + exception.getMessage();
            }

            return new ImageAiConfigTestResponse(
                    true,
                    message,
                    System.currentTimeMillis() - started,
                    runtime.model(),
                    runtime.size(),
                    previewUrl,
                    generated.mimeType(),
                    width,
                    height
            );
        } catch (Exception exception) {
            return failure(configId, config, started, exception);
        }
    }

    private ImageAiConfigTestResponse failure(UUID configId, ImageAiConfig config, long started, Exception exception) {
        log.info("Image AI config test failed for {}: {}", configId, exception.getMessage());
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "连接失败，请检查 Base URL、模型名、尺寸和 API Key";
        }
        return new ImageAiConfigTestResponse(
                false,
                message,
                System.currentTimeMillis() - started,
                config.getModel(),
                config.getDefaultSize(),
                null,
                null,
                null,
                null
        );
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    @Transactional(readOnly = true)
    public ImageAiRuntimeConfig resolveDefault(UUID ownerId) {
        ImageAiConfig config = findDefault(ownerId);
        return config == null ? null : toRuntime(config);
    }

    @Transactional(readOnly = true)
    public UUID resolveDefaultId(UUID ownerId) {
        ImageAiConfig config = findDefault(ownerId);
        return config == null ? null : config.getId();
    }

    private ImageAiConfig findDefault(UUID ownerId) {
        return repository.findFirstByOwnerIdAndDefaultConfigTrue(ownerId)
                .orElseGet(() -> repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId)
                        .stream()
                        .findFirst()
                        .orElse(null));
    }

    @Transactional(readOnly = true)
    public ImageAiRuntimeConfig resolveForJob(UUID ownerId, UUID configId) {
        ImageAiConfig config = repository.findByIdAndOwnerId(configId, ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "IMAGE_AI_CONFIG_NOT_FOUND",
                        "图像 AI 配置不存在或已被删除"
                ));
        return toRuntime(config);
    }

    private ImageAiRuntimeConfig toRuntime(ImageAiConfig config) {
        return new ImageAiRuntimeConfig(
                ImageProviderType.parse(config.getProviderType()),
                config.getName(),
                config.getBaseUrl(),
                cryptoService.decrypt(config.getApiKeyEncrypted()),
                config.getModel(),
                config.getDefaultSize(),
                config.getDefaultQuality()
        );
    }

    private ImageAiConfig requireOwned(UUID ownerId, UUID configId) {
        return repository.findByIdAndOwnerId(configId, ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "IMAGE_AI_CONFIG_NOT_FOUND",
                        "图像 AI 配置不存在"
                ));
    }

    private String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IMAGE_AI_BASE_URL_INVALID",
                    "Base URL 必须以 http:// 或 https:// 开头"
            );
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeQuality(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.isBlank() ? null : value;
    }

    private String keyHint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "••••";
        }
        return trimmed.substring(trimmed.length() - 4);
    }
}
