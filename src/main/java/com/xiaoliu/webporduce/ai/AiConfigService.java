package com.xiaoliu.webporduce.ai;

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
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    private final AiConfigRepository repository;
    private final AiConfigCryptoService cryptoService;
    private final AiProviderFactory providerFactory;

    public AiConfigService(AiConfigRepository repository,
                           AiConfigCryptoService cryptoService,
                           AiProviderFactory providerFactory) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.providerFactory = providerFactory;
    }

    @Transactional(readOnly = true)
    public List<AiConfigResponse> list(UUID ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId)
                .stream()
                .map(AiConfigResponse::from)
                .toList();
    }

    public List<AiProviderPresetResponse> presets() {
        return Arrays.stream(AiProviderType.values())
                .map(type -> new AiProviderPresetResponse(
                        type.name(),
                        type.label(),
                        type.defaultBaseUrl(),
                        type.defaultModel(),
                        type.description()
                ))
                .toList();
    }

    @Transactional
    public AiConfigResponse create(UUID ownerId, AiConfigRequest request) {
        AiProviderType type = AiProviderType.parse(request.providerType());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = request.model().trim();
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();
        boolean makeDefault = request.defaultConfig() || repository.countByOwnerId(ownerId) == 0;
        if (makeDefault) {
            repository.clearDefault(ownerId);
        }
        AiConfig config = new AiConfig(
                ownerId,
                request.name().trim(),
                type.name(),
                baseUrl,
                model,
                cryptoService.encrypt(apiKey),
                keyHint(apiKey),
                makeDefault
        );
        repository.save(config);
        return AiConfigResponse.from(config);
    }

    @Transactional
    public AiConfigResponse update(UUID ownerId, UUID configId, AiConfigRequest request) {
        AiConfig config = requireOwned(ownerId, configId);
        AiProviderType type = AiProviderType.parse(request.providerType());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String model = request.model().trim();
        boolean wasDefault = config.isDefaultConfig();
        String encrypted = config.getApiKeyEncrypted();
        String hint = config.getApiKeyHint();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String apiKey = request.apiKey().trim();
            encrypted = cryptoService.encrypt(apiKey);
            hint = keyHint(apiKey);
        }

        AiConfig nextDefault = null;
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
                request.defaultConfig()
        );
        repository.save(config);
        if (nextDefault != null) {
            repository.save(nextDefault);
        }
        return AiConfigResponse.from(config);
    }

    @Transactional
    public void delete(UUID ownerId, UUID configId) {
        AiConfig config = requireOwned(ownerId, configId);
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
    public AiConfigResponse setDefault(UUID ownerId, UUID configId) {
        AiConfig config = requireOwned(ownerId, configId);
        repository.clearDefault(ownerId);
        config.setDefaultConfig(true);
        repository.save(config);
        return AiConfigResponse.from(config);
    }

    public AiConfigTestResponse test(UUID ownerId, UUID configId) {
        AiConfig config = requireOwned(ownerId, configId);
        long started = System.currentTimeMillis();
        try {
            AiRuntimeConfig runtime = toRuntime(config);
            providerFactory.create(runtime).testConnection();
            return new AiConfigTestResponse(
                    true,
                    "连接成功，模型已正常响应",
                    System.currentTimeMillis() - started,
                    runtime.model()
            );
        } catch (Exception exception) {
            log.info("AI config test failed for {}: {}", configId, exception.getMessage());
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = "连接失败，请检查 Base URL、模型名和 API Key";
            }
            return new AiConfigTestResponse(
                    false,
                    message,
                    System.currentTimeMillis() - started,
                    config.getModel()
            );
        }
    }

    /**
     * 返回当前用户默认 AI 配置；没有配置时返回 null，由 AiService 决定系统兜底或 Mock。
     */
    @Transactional(readOnly = true)
    public AiRuntimeConfig resolveDefault(UUID ownerId) {
        AiConfig config = repository.findFirstByOwnerIdAndDefaultConfigTrue(ownerId)
                .orElseGet(() -> repository.findAllByOwnerIdOrderByCreatedAtAsc(ownerId)
                        .stream()
                        .findFirst()
                        .orElse(null));
        return config == null ? null : toRuntime(config);
    }

    private AiRuntimeConfig toRuntime(AiConfig config) {
        return new AiRuntimeConfig(
                AiProviderType.parse(config.getProviderType()),
                config.getName(),
                config.getBaseUrl(),
                cryptoService.decrypt(config.getApiKeyEncrypted()),
                config.getModel()
        );
    }

    private AiConfig requireOwned(UUID ownerId, UUID configId) {
        return repository.findByIdAndOwnerId(configId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AI_CONFIG_NOT_FOUND", "AI 配置不存在"));
    }

    private String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_BASE_URL_INVALID", "Base URL 必须以 http:// 或 https:// 开头");
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
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
