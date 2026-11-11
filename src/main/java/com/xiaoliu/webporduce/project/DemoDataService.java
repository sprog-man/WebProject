package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.xiaoliu.webporduce.chat.ChatMessage;
import com.xiaoliu.webporduce.chat.ChatMessageRepository;
import com.xiaoliu.webporduce.user.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DemoDataService {

    private final PageSpecProvider specProvider;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final ProjectRevisionRepository revisionRepository;
    private final ChatMessageRepository messageRepository;

    public DemoDataService(PageSpecProvider specProvider,
                           ObjectMapper objectMapper,
                           ProjectRepository projectRepository,
                           ProjectRevisionRepository revisionRepository,
                           ChatMessageRepository messageRepository) {
        this.specProvider = specProvider;
        this.objectMapper = objectMapper;
        this.projectRepository = projectRepository;
        this.revisionRepository = revisionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public void seed(UserAccount user) {
        if (projectRepository.countByOwnerId(user.getId()) > 0) {
            return;
        }

        JsonNode spec = specProvider.defaultSpec();
        Project project = new Project(user.getId(), "Aurora 品牌官网", write(spec));
        projectRepository.save(project);

        ProjectRevision revision = new ProjectRevision(
                project.getId(), 1, write(spec), "DEMO", "初始示例页面");
        revisionRepository.save(revision);

        Instant now = Instant.now();
        messageRepository.save(new ChatMessage(
                project.getId(),
                ChatMessage.Role.USER,
                "帮我做一版 Aurora 的品牌官网首屏。想要画廊感，克制一点，但不要太冷。",
                null,
                now.minusSeconds(60)
        ));
        messageRepository.save(new ChatMessage(
                project.getId(),
                ChatMessage.Role.ASSISTANT,
                "先做了一版「午夜画廊」。用大字号宋体压住画面，留白多一点，再把唯一的暖色留给行动按钮——冷静，但仍然有温度。\n\n这一版可以直接改：点画布里的文字、图片或数字，右侧调整颜色、字号和布局。改动会自动保存，不需要重新生成。",
                1L,
                now.minusSeconds(58)
        ));
    }

    private String write(JsonNode spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建示例页面", exception);
        }
    }
}
