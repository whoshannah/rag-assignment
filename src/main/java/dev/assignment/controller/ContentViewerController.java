package dev.assignment.controller;

import java.io.IOException;

import dev.assignment.model.Resource;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the Content Viewer window (Read-only)
 */
public class ContentViewerController {

    @FXML
    private VBox root;
    @FXML
    private TextArea contentArea;

    private Resource resource;

    private boolean isDarkMode = false;

    /**
     * Set the resource to display
     */
    public void setResource(Resource resource) {
        this.resource = resource;
        loadContent();
    }

    private void loadContent() {
        try {
            if (resource != null && resource.exists()) {
                String content = resource.getContent();
                contentArea.setText(content);
                contentArea.setEditable(false);
            } else {
                contentArea.setText("File not found: " + (resource != null ? resource.getFileName() : "null"));
            }
        } catch (IOException e) {
            contentArea.setText("Error loading file: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) contentArea.getScene().getWindow();
        stage.close();
    }

    /**
     * Toggle light/dark mode
     */
    @FXML
    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        root.getStylesheets().clear();
        if (isDarkMode) {
            root.getStylesheets().add(getClass().getResource("/dark.css").toExternalForm());
        } else {
            root.getStylesheets().add(getClass().getResource("/light.css").toExternalForm());
        }
    }
}
