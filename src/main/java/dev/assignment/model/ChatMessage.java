package dev.assignment.model;

import java.time.LocalDateTime;

/**
 * Represents a chat message in the session
 */
public record ChatMessage(
        String id,
        String content,
        boolean isUser,
        LocalDateTime timestamp,
        String sources // Optional sources for AI messages
) {
    public ChatMessage(String content, boolean isUser) {
        this(java.util.UUID.randomUUID().toString(), content, isUser, LocalDateTime.now(), null);
    }

    public ChatMessage(String content, boolean isUser, String sources) {
        this(java.util.UUID.randomUUID().toString(), content, isUser, LocalDateTime.now(), sources);
    }

    public boolean hasSources() {
        return sources != null && !sources.trim().isEmpty();
    }
}
