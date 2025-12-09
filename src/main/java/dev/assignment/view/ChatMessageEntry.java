package dev.assignment.view;

import dev.assignment.model.ChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Custom component for displaying a chat message
 */
public final class ChatMessageEntry extends VBox {

    private final Label messageLabel;
    private Label sourcesLabel;
    private final HBox messageContainer;
    private final boolean isUserMessage;

    public ChatMessageEntry(ChatMessage message) {
        this.messageLabel = new Label(message.content());
        this.isUserMessage = message.isUser();

        this.messageContainer = new HBox();

        // Set alignment based on message type
        if (message.isUser()) {
            messageContainer.setAlignment(Pos.CENTER_RIGHT);
            messageLabel.getStyleClass().add("user-message");
        } else {
            messageContainer.setAlignment(Pos.CENTER_LEFT);
            messageLabel.getStyleClass().add("ai-message");
        }

        messageLabel.setPadding(new Insets(10, 10, 10, 10));
        messageLabel.setMaxWidth(500);
        messageLabel.setWrapText(true);
        messageLabel.setMinHeight(Region.USE_PREF_SIZE);

        messageContainer.getChildren().add(messageLabel);

        getChildren().add(messageContainer);

        // Add sources label if available (for AI messages)
        if (!message.isUser() && message.hasSources()) {
            setSources(message.sources());
        }

        setSpacing(0);
    }

    /**
     * Update the message text (for streaming updates)
     */
    public void updateText(String newText) {
        messageLabel.setText(newText);
    }

    /**
     * Append text to the message (for streaming updates)
     */
    public void appendText(String text) {
        messageLabel.setText(messageLabel.getText() + text);
    }

    /**
     * Set the sources for the message
     */
    public void setSources(String sources) {
        if (sourcesLabel != null) {
            sourcesLabel.setText(sources);
        } else if (!isUserMessage) {
            // Create the sources label if it doesn't exist yet (for streaming responses)
            sourcesLabel = new Label(sources);
            sourcesLabel.getStyleClass().add("sources-label");
            sourcesLabel.setMaxWidth(Double.MAX_VALUE);
            sourcesLabel.setAlignment(Pos.CENTER_LEFT);
            sourcesLabel.setPadding(new Insets(2, 0, 5, 0));
            sourcesLabel.setWrapText(true);
            sourcesLabel.setMaxWidth(500);
            sourcesLabel.setMinHeight(Region.USE_PREF_SIZE);

            getChildren().add(0, sourcesLabel);
        }
    }
}
