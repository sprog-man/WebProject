package com.xiaoliu.webporduce.chat;

import com.xiaoliu.webporduce.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/messages")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public SendMessageResponse send(@PathVariable UUID projectId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return chatService.send(CurrentUser.require().getId(), projectId, request.content());
    }
}
