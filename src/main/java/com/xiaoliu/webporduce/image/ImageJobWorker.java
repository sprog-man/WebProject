package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.asset.Asset;
import com.xiaoliu.webporduce.asset.AssetService;
import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.project.PageSpecValidator;
import com.xiaoliu.webporduce.project.Project;
import com.xiaoliu.webporduce.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class ImageJobWorker {

    private static final Logger log = LoggerFactory.getLogger(ImageJobWorker.class);
    private static final int APPLY_ATTEMPTS = 3;

    private final ImageJobRepository jobRepository;
    private final ImageAiConfigService configService;
    private final ImageProviderFactory providerFactory;
    private final AssetService assetService;
    private final ProjectRepository projectRepository;
    private final PageSpecValidator specValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ImageJobWorker(ImageJobRepository jobRepository,
                          ImageAiConfigService configService,
                          ImageProviderFactory providerFactory,
                          AssetService assetService,
                          ProjectRepository projectRepository,
                          PageSpecValidator specValidator,
                          ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.configService = configService;
        this.providerFactory = providerFactory;
        this.assetService = assetService;
        this.projectRepository = projectRepository;
        this.specValidator = specValidator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void run(UUID jobId) {
        ImageJob running = transactionTemplate.execute(status -> {
            ImageJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != ImageJobStatus.PENDING) {
                return null;
            }
            job.markRunning();
            return jobRepository.save(job);
        });
        if (running == null) {
            return;
        }

        try {
            ImageAiRuntimeConfig config = configService.resolveForJob(running.getOwnerId(), running.getConfigId());
            ImageGenerationResult generated = providerFactory.create(config).generate(
                    new ImageGenerationRequest(running.getPrompt(), running.getImageSize(), config.quality())
            );
            Asset asset = assetService.storeGenerated(
                    running.getOwnerId(),
                    running.getProjectId(),
                    generated.bytes(),
                    generated.mimeType(),
                    "ai-image." + extensionFor(generated.mimeType()),
                    running.getPrompt()
            );
            applyWithRetry(running.getId(), asset);
        } catch (Exception exception) {
            log.info("Image job {} failed: {}", jobId, exception.getMessage());
            markFailed(running.getId(), failureMessage(exception));
        }
    }

    private void applyWithRetry(UUID jobId, Asset asset) {
        for (int attempt = 1; attempt <= APPLY_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> apply(jobId, asset));
                return;
            } catch (OptimisticLockingFailureException exception) {
                if (attempt == APPLY_ATTEMPTS) {
                    throw exception;
                }
                log.info("Image job {} hit a project version conflict, retrying ({}/{})",
                        jobId, attempt, APPLY_ATTEMPTS);
            } catch (jakarta.persistence.OptimisticLockException exception) {
                if (attempt == APPLY_ATTEMPTS) {
                    throw exception;
                }
                log.info("Image job {} hit a JPA version conflict, retrying ({}/{})",
                        jobId, attempt, APPLY_ATTEMPTS);
            }
        }
    }

    private void apply(UUID jobId, Asset asset) {
        ImageJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != ImageJobStatus.RUNNING) {
            return;
        }
        Project project = projectRepository.findById(job.getProjectId()).orElse(null);
        if (project == null) {
            job.markFailed("页面已被删除，生成结果未保存");
            jobRepository.save(job);
            return;
        }

        JsonNode spec;
        try {
            spec = objectMapper.readTree(project.getSpecJson());
        } catch (Exception exception) {
            job.markFailed("页面数据无法解析，生成结果未保存");
            jobRepository.save(job);
            return;
        }

        JsonNode image = PageSpecImages.findImage(spec, job.getSectionId(), job.getTargetPath());
        if (image == null) {
            job.markSuperseded("图片位置已不存在，生成结果未覆盖页面");
            jobRepository.save(job);
            return;
        }
        String currentPrompt = PageSpecImages.promptOf(image);
        if (!PageSpecImages.sha256(currentPrompt).equals(job.getPromptHash())) {
            job.markSuperseded("提示词已变更，生成结果未覆盖当前图片");
            jobRepository.save(job);
            return;
        }
        String currentSrc = image.path("src").asText("");
        if (job.getBaseSrc() != null && !job.getBaseSrc().equals(currentSrc)) {
            job.markSuperseded("图片已被手动更换，生成结果未覆盖当前图片");
            jobRepository.save(job);
            return;
        }

        String assetUrl = "/api/assets/" + asset.getId() + "/content";
        PageSpecImages.setImageAsset(image, assetUrl, asset.getId().toString());
        specValidator.validate(spec);
        try {
            project.updateSpec(objectMapper.writeValueAsString(spec));
        } catch (Exception exception) {
            job.markFailed("生成图片无法写入页面");
            jobRepository.save(job);
            return;
        }
        projectRepository.saveAndFlush(project);

        job.markSucceeded(asset.getId());
        jobRepository.save(job);
    }

    private void markFailed(UUID jobId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            ImageJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || !job.isActive()) {
                return;
            }
            job.markFailed(message);
            jobRepository.save(job);
        });
    }

    private String failureMessage(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return truncate(apiException.getMessage());
        }
        if (exception instanceof ImageProviderException imageException) {
            return truncate(imageException.getMessage());
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "图片生成失败，请稍后重试";
        }
        return truncate(message);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "图片生成失败，请稍后重试";
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 900 ? cleaned.substring(0, 900) + "…" : cleaned;
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
}
