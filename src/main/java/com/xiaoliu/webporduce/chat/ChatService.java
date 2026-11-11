package com.xiaoliu.webporduce.chat;

import com.xiaoliu.webporduce.ai.AiResult;
import com.xiaoliu.webporduce.ai.AiService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatPersistenceService persistenceService;
    private final AiService aiService;

    public ChatService(ChatPersistenceService persistenceService, AiService aiService) {
        this.persistenceService = persistenceService;
        this.aiService = aiService;
    }

    public SendMessageResponse send(UUID ownerId, UUID projectId, String content) {
        ChatPreparation preparation = persistenceService.prepare(ownerId, projectId, content);
        boolean emptyPage = preparation.spec().path("sections").isEmpty();
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        boolean forceGenerate = normalized.contains("重新生成") || normalized.contains("换一版") || normalized.contains("重做");

        AiResult result;
        if (emptyPage || forceGenerate) {
            result = aiService.generate(preparation.projectName(), preparation.spec(), content);
        } else {
            result = aiService.edit(preparation.spec(), content);
        }
        return persistenceService.complete(
                ownerId,
                preparation,
                result.reply(),
                result.spec(),
                result.changed()
        );
    }
}
