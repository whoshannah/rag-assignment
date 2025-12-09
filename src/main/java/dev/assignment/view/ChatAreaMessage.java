package dev.assignment.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;

/**
 * Reusable component for displaying centered messages in the chat area
 */
public class ChatAreaMessage extends Label {

    public ChatAreaMessage(String message) {
        super(message);
        getStyleClass().add("chat-area-message");
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        setAlignment(Pos.CENTER);
    }
}
