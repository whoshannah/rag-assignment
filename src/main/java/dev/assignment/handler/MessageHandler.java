package dev.assignment.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.assignment.model.ChatMessage;
import dev.assignment.model.Session;
import dev.assignment.service.DatabaseService;
import dev.assignment.service.RAGService;
import dev.assignment.util.Constants;
import dev.assignment.view.AlertHelper;
import dev.assignment.view.ChatAreaMessage;
import dev.assignment.view.ChatMessageEntry;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * Handles message sending and response streaming.
 */
public class MessageHandler {

    private static final Logger logger = LogManager.getLogger(MessageHandler.class);

    private final VBox chatContainer;
    private final TextArea messageInput;
    private final Label statusLabel;
    private final SessionStateHandler sessionStateHandler;
    private final Runnable toggleAllControlsCallback;

    public MessageHandler(
            VBox chatContainer,
            TextArea messageInput,
            Label statusLabel,
            SessionStateHandler sessionStateHandler,
            Runnable toggleAllControlsCallback) {
        this.chatContainer = chatContainer;
        this.messageInput = messageInput;
        this.statusLabel = statusLabel;
        this.sessionStateHandler = sessionStateHandler;
        this.toggleAllControlsCallback = toggleAllControlsCallback;
    }

    /**
     * Handle sending a message.
     */
    public void handleSendMessage() {
        String userMessage = messageInput.getText().trim();

        if (userMessage.isEmpty()) {
            return;
        }

        if (userMessage.length() > Constants.MAX_QUERY_LENGTH) {
            AlertHelper.showWarning("Query Too Long", "Your query exceeds the maximum length",
                    String.format("Please limit your query to %d characters. Current length: %d characters.",
                            Constants.MAX_QUERY_LENGTH, userMessage.length()));
            return;
        }

        RAGService ragService = sessionStateHandler.getRagService();
        if (ragService == null) {
            logger.warn("Cannot send message: RAG service not initialized (API key may be missing)");
            return;
        }

        Session currentSession = sessionStateHandler.getCurrentSession();
        if (currentSession == null) {
            logger.error("Cannot send message: No session selected");
            return;
        }

        logger.info("========== Sending Message ==========");
        logger.info("Session: id={}, name='{}'", currentSession.getId(), currentSession.getName());
        logger.info("Message length: {} characters", userMessage.length());
        logger.debug("Message content: {}", userMessage);

        messageInput.clear();

        chatContainer.getChildren().removeIf(node -> node instanceof ChatAreaMessage);

        ChatMessage userChatMessage = new ChatMessage(userMessage, true);
        ChatMessageEntry userMessageBox = new ChatMessageEntry(userChatMessage);
        chatContainer.getChildren().add(userMessageBox);

        DatabaseService databaseService = DatabaseService.getInstance();
        if (databaseService != null) {
            databaseService.saveChatMessage(currentSession.getId(), userChatMessage);
        }

        ChatMessage aiChatMessage = new ChatMessage("...", false);
        ChatMessageEntry aiMessageBox = new ChatMessageEntry(aiChatMessage);
        chatContainer.getChildren().add(aiMessageBox);

        toggleAllControlsCallback.run();
        statusLabel.setText("Generating response...");
        String finalUserMessage = userMessage;
        new Thread(() -> {
            try {
                logger.info("Querying RAG service...");
                dev.assignment.model.QueryResponse queryResponse = ragService.query(finalUserMessage);
                String responseText = queryResponse.response();
                java.util.List<String> sources = queryResponse.sources();

                Platform.runLater(() -> {
                    aiMessageBox.updateText(responseText);

                    String sourcesText = null;
                    if (sources != null && !sources.isEmpty()) {
                        sourcesText = String.join(", ", sources);
                        aiMessageBox.setSources(sourcesText);
                    }

                    ChatMessage finalAiMessage = new ChatMessage(responseText, false, sourcesText);
                    DatabaseService db = DatabaseService.getInstance();
                    if (db != null) {
                        db.saveChatMessage(currentSession.getId(), finalAiMessage);
                    }

                    toggleAllControlsCallback.run();
                    statusLabel.setText("Ready");
                    messageInput.requestFocus();

                    logger.info("Response complete and displayed");
                });
            } catch (Exception e) {
                logger.error("========== Error Getting Response ==========");
                logger.error("Session: {}", currentSession.getName());
                logger.error("Error type: {}", e.getClass().getSimpleName());
                logger.error("Error message: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    chatContainer.getChildren().remove(aiMessageBox);

                    AlertHelper.showError("Error", "Failed to get response", e.getMessage());

                    toggleAllControlsCallback.run();
                    statusLabel.setText("Error occurred");
                    messageInput.requestFocus();
                });
            }
        }).start();
    }
}
