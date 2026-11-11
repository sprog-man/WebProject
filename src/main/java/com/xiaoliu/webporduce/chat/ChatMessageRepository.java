package com.xiaoliu.webporduce.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);
    void deleteAllByProjectId(UUID projectId);
}
