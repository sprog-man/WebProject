package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.xiaoliu.webporduce.chat.ChatMessage;
import com.xiaoliu.webporduce.chat.ChatMessageRepository;
import com.xiaoliu.webporduce.chat.MessageResponse;
import com.xiaoliu.webporduce.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectRevisionRepository revisionRepository;
    private final ChatMessageRepository messageRepository;
    private final PageSpecProvider specProvider;
    private final PageSpecValidator specValidator;
    private final ObjectMapper objectMapper;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectRevisionRepository revisionRepository,
                          ChatMessageRepository messageRepository,
                          PageSpecProvider specProvider,
                          PageSpecValidator specValidator,
                          ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.revisionRepository = revisionRepository;
        this.messageRepository = messageRepository;
        this.specProvider = specProvider;
        this.specValidator = specValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(UUID ownerId) {
        return projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail get(UUID ownerId, UUID projectId) {
        Project project = requireOwned(ownerId, projectId);
        List<MessageResponse> messages = messageRepository
                .findAllByProjectIdOrderByCreatedAtAsc(projectId)
                .stream()
                .map(MessageResponse::from)
                .toList();
        Long latestRevision = revisionRepository
                .findFirstByProjectIdOrderByRevisionNumberDesc(projectId)
                .map(ProjectRevision::getRevisionNumber)
                .orElse(null);
        return new ProjectDetail(
                project.getId(),
                project.getName(),
                project.getVersion(),
                readSpec(project.getSpecJson()),
                messages,
                latestRevision,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    @Transactional
    public ProjectDetail create(UUID ownerId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "页面名称不能为空");
        }

        JsonNode spec = specProvider.blankSpec(trimmed);
        Project project = new Project(ownerId, trimmed, writeSpec(spec));
        projectRepository.saveAndFlush(project);

        messageRepository.save(new ChatMessage(
                project.getId(),
                ChatMessage.Role.ASSISTANT,
                "告诉我你想做一个什么样的页面。\n\n比如：为一家独立咖啡品牌做官网首屏，安静、有手作感；或者做一个 AI 产品的发布页，有未来感但不要冰冷。",
                null
        ));
        return get(ownerId, project.getId());
    }

    @Transactional
    public SaveDraftResponse saveDraft(UUID ownerId, UUID projectId, JsonNode spec, long expectedVersion) {
        Project project = requireOwned(ownerId, projectId);
        if (project.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "页面已在别处更新，请刷新后重试");
        }
        specValidator.validate(spec);
        project.updateSpec(writeSpec(spec));
        projectRepository.saveAndFlush(project);
        return new SaveDraftResponse(project.getVersion(), project.getUpdatedAt());
    }

    @Transactional
    public ProjectSummary rename(UUID ownerId, UUID projectId, String name) {
        Project project = requireOwned(ownerId, projectId);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "页面名称不能为空");
        }
        project.rename(trimmed);
        projectRepository.saveAndFlush(project);
        return toSummary(project);
    }

    @Transactional
    public void delete(UUID ownerId, UUID projectId) {
        Project project = requireOwned(ownerId, projectId);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public Project requireOwned(UUID ownerId, UUID projectId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "页面不存在"));
    }

    @Transactional(readOnly = true)
    public JsonNode readSpec(Project project) {
        return readSpec(project.getSpecJson());
    }

    public JsonNode readSpec(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "页面数据损坏");
        }
    }

    public String writeSpec(JsonNode spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无法保存页面数据");
        }
    }

    private ProjectSummary toSummary(Project project) {
        return new ProjectSummary(
                project.getId(),
                project.getName(),
                project.getVersion(),
                readSpec(project.getSpecJson()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
