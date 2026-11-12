package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.project.Project;
import com.xiaoliu.webporduce.project.ProjectService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ImageJobService {

    private static final List<ImageJobStatus> ACTIVE_STATUSES =
            List.of(ImageJobStatus.PENDING, ImageJobStatus.RUNNING);

    private final ImageJobRepository jobRepository;
    private final ImageAiConfigService configService;
    private final ProjectService projectService;
    private final ImageJobWorker worker;
    private final TaskExecutor imageTaskExecutor;

    public ImageJobService(ImageJobRepository jobRepository,
                           ImageAiConfigService configService,
                           ProjectService projectService,
                           ImageJobWorker worker,
                           @Qualifier("imageTaskExecutor") TaskExecutor imageTaskExecutor) {
        this.jobRepository = jobRepository;
        this.configService = configService;
        this.projectService = projectService;
        this.worker = worker;
        this.imageTaskExecutor = imageTaskExecutor;
    }

    @Transactional(readOnly = true)
    public ImageJobListResponse list(UUID ownerId, UUID projectId) {
        Project project = projectService.requireOwned(ownerId, projectId);
        List<ImageJobResponse> jobs = jobRepository
                .findTop50ByOwnerIdAndProjectIdOrderByCreatedAtDesc(ownerId, projectId)
                .stream()
                .map(ImageJobResponse::from)
                .toList();
        return new ImageJobListResponse(jobs, project.getVersion());
    }

    @Transactional
    public ImageJobListResponse create(UUID ownerId, UUID projectId, List<ImageJobRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择要生成的图片");
        }
        if (items.size() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "一次最多生成 5 张图片");
        }

        Project project = projectService.requireOwned(ownerId, projectId);
        UUID configId = configService.resolveDefaultId(ownerId);
        ImageAiRuntimeConfig config = configService.resolveDefault(ownerId);
        if (configId == null || config == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IMAGE_AI_NOT_CONFIGURED",
                    "还没有配置图像 AI。请先到「AI 设置」添加一个图像模型，并设为默认。"
            );
        }

        JsonNode spec = projectService.readSpec(project);
        List<ImageJob> created = new ArrayList<>();
        for (ImageJobRequest item : items) {
            String sectionId = item.sectionId() == null ? "" : item.sectionId().trim();
            String path = item.path() == null ? "" : item.path().trim();
            if (sectionId.isBlank() || !PageSpecImages.isSupportedPath(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "图片位置不正确");
            }
            JsonNode image = PageSpecImages.findImage(spec, sectionId, path);
            if (image == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "IMAGE_TARGET_NOT_FOUND",
                        "找不到要生成的图片位置"
                );
            }
            String prompt = item.prompt() == null ? "" : item.prompt().trim();
            if (prompt.isBlank()) {
                prompt = PageSpecImages.promptOf(image);
            }
            if (prompt.isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "IMAGE_PROMPT_REQUIRED",
                        "这张图片还没有提示词，请先填写或让 AI 生成。"
                );
            }
            if (prompt.length() > 1500) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "IMAGE_PROMPT_TOO_LONG",
                        "图像提示词不能超过 1500 个字符"
                );
            }
            boolean alreadyRunning = jobRepository
                    .findFirstByProjectIdAndTargetPathAndStatusInOrderByCreatedAtDesc(
                            projectId, path, ACTIVE_STATUSES)
                    .isPresent();
            if (alreadyRunning) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "IMAGE_JOB_ALREADY_RUNNING",
                        "这张图片正在生成，请等当前任务完成后再试。"
                );
            }

            ImageJob job = new ImageJob(
                    ownerId,
                    projectId,
                    configId,
                    sectionId,
                    path,
                    prompt,
                    PageSpecImages.sha256(prompt),
                    config.size()
            );
            jobRepository.saveAndFlush(job);
            created.add(job);
        }

        for (ImageJob job : created) {
            enqueueAfterCommit(job.getId());
        }

        List<ImageJobResponse> responses = created.stream().map(ImageJobResponse::from).toList();
        return new ImageJobListResponse(responses, project.getVersion());
    }

    private void enqueueAfterCommit(UUID jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(jobId);
                }
            });
        } else {
            enqueue(jobId);
        }
    }

    private void enqueue(UUID jobId) {
        imageTaskExecutor.execute(() -> worker.run(jobId));
    }
}
