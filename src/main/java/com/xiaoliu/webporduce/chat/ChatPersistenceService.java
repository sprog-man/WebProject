package com.xiaoliu.webporduce.chat;

import tools.jackson.databind.JsonNode;
import com.xiaoliu.webporduce.common.ApiException;
import com.xiaoliu.webporduce.project.PageSpecValidator;
import com.xiaoliu.webporduce.project.Project;
import com.xiaoliu.webporduce.project.ProjectRepository;
import com.xiaoliu.webporduce.project.ProjectRevision;
import com.xiaoliu.webporduce.project.ProjectRevisionRepository;
import com.xiaoliu.webporduce.project.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ChatPersistenceService {

    private final ProjectRepository projectRepository;
    private final ProjectRevisionRepository revisionRepository;
    private final ChatMessageRepository messageRepository;
    private final PageSpecValidator specValidator;
    private final ProjectService projectService;

    public ChatPersistenceService(ProjectRepository projectRepository,
                                  ProjectRevisionRepository revisionRepository,
                                  ChatMessageRepository messageRepository,
                                  PageSpecValidator specValidator,
                                  ProjectService projectService) {
        this.projectRepository = projectRepository;
        this.revisionRepository = revisionRepository;
        this.messageRepository = messageRepository;
        this.specValidator = specValidator;
        this.projectService = projectService;
    }

    @Transactional
    public ChatPreparation prepare(UUID ownerId, UUID projectId, String content) {
        Project project = projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "页面不存在"));
        ChatMessage userMessage = messageRepository.save(new ChatMessage(
                projectId, ChatMessage.Role.USER, content.trim(), null));
        return new ChatPreparation(
                project.getId(),
                project.getName(),
                projectService.readSpec(project),
                project.getVersion(),
                MessageResponse.from(userMessage),
                content.trim(),
                Instant.now()
        );
    }

    @Transactional
    public SendMessageResponse complete(UUID ownerId, ChatPreparation preparation,
                                        String reply, JsonNode nextSpec, boolean changed) {
        Project project = projectRepository.findByIdAndOwnerId(preparation.projectId(), ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "页面不存在"));

        Long revisionNumber = null;
        if (changed && nextSpec != null) {
            specValidator.validate(nextSpec);
            project.updateSpec(projectService.writeSpec(nextSpec));
            projectRepository.saveAndFlush(project);

            long nextRevision = revisionRepository
                    .findFirstByProjectIdOrderByRevisionNumberDesc(project.getId())
                    .map(ProjectRevision::getRevisionNumber)
                    .orElse(0L) + 1;
            ProjectRevision revision = new ProjectRevision(
                    project.getId(),
                    nextRevision,
                    project.getSpecJson(),
                    "AI",
                    summarize(preparation.content())
            );
            revisionRepository.save(revision);
            revisionNumber = nextRevision;
        }

        ChatMessage assistantMessage = messageRepository.save(new ChatMessage(
                project.getId(),
                ChatMessage.Role.ASSISTANT,
                reply == null || reply.isBlank() ? "我已经更新了画布。" : reply.trim(),
                revisionNumber
        ));

        return new SendMessageResponse(
                preparation.userMessage(),
                MessageResponse.from(assistantMessage),
                projectService.readSpec(project),
                project.getVersion(),
                revisionNumber,
                changed
        );
    }

    private String summarize(String content) {
        if (content == null) {
            return "AI 更新页面";
        }
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "…" : cleaned;
    }
}
