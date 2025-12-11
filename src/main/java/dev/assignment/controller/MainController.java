package dev.assignment.controller;

import dev.assignment.service.APIKeyService;
import dev.assignment.service.DatabaseService;
import dev.assignment.view.AlertHelper;
import dev.assignment.view.SessionSidebar;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ProgressIndicator;

public class MainController {

    @FXML
    private StackPane root;

    @FXML
    private SessionSidebar sessionSidebar;

    @FXML
    private Label sessionNameLabel;

    @FXML
    private Label sessionCreatedLabel;

    @FXML
    private VBox chatContainer;

    @FXML
    private ScrollPane chatScrollPane;

    @FXML
    private TextArea messageInput;

    @FXML
    private Button sendButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label modelLabel;

    @FXML
    private Button manageKnowledgebaseButton;

    @FXML
    private Button clearSessionButton;

    @FXML
    private Button toggleThemeButton;

    private ChatSessionController chatSessionController;
    private boolean isDarkMode = false;

    @FXML
    private Pane loadingOverlay;
    @FXML
    private ProgressIndicator loadingSpinner;

    @FXML
    private void initialize() {

        if (loadingOverlay != null)
            loadingOverlay.setVisible(false);
        if (loadingSpinner != null)
            loadingSpinner.setVisible(false);

        DatabaseService databaseService = DatabaseService.getInstance();
        if (databaseService == null) {
            AlertHelper.showError(
                    "Database Error",
                    "Failed to Initialize Database",
                    "The application database could not be initialized. Please check file permissions and disk space.\n\nThe application will continue with limited functionality.");

            if (sessionSidebar != null) {
                sessionSidebar.setDisable(true);
            }
            sendButton.setDisable(true);
            messageInput.setDisable(true);
            manageKnowledgebaseButton.setDisable(true);
            clearSessionButton.setDisable(true);
            statusLabel.setText("Database unavailable");
            return;
        }

        APIKeyService apiKeyService = APIKeyService.getInstance();
        boolean hasApiKey = apiKeyService.loadApiKey();

        if (hasApiKey) {
            statusLabel.setText("Validating API Key...");

            new Thread(() -> {
                boolean isValid = apiKeyService.validateApiKey();

                javafx.application.Platform.runLater(() -> {
                    if (isValid) {
                        statusLabel.setText("API Key validated successfully");
                    } else {
                        statusLabel.setText("Invalid API Key - Chat disabled");
                        sendButton.setDisable(true);
                        messageInput.setDisable(true);

                        AlertHelper.showError(
                                "Invalid API Key",
                                "API Key Validation Failed",
                                "The provided OpenAI API key is invalid. Please check your .env file or provide a valid key.");
                    }
                });
            }).start();

        } else {
            statusLabel.setText("No API Key - Chat disabled");
            sendButton.setDisable(true);
            messageInput.setDisable(true);
        }

        chatSessionController = new ChatSessionController(
                sessionNameLabel,
                sessionCreatedLabel,
                chatContainer,
                messageInput,
                sendButton,
                statusLabel,
                modelLabel,
                manageKnowledgebaseButton,
                clearSessionButton,
                sessionSidebar);

        sessionSidebar.setOnSessionSelected(chatSessionController::handleSessionSelected);
        sessionSidebar.setOnSessionChanged(chatSessionController::handleSessionChanged);

        sessionSidebar.loadSessions();

        chatContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        if (toggleThemeButton != null) {
            toggleThemeButton.setOnAction(e -> handleToggleTheme());
        }

        javafx.application.Platform.runLater(() -> {
            Scene scene = root.getScene();
            if (scene != null) {
                String lightCss = getClass().getResource("/dev/assignment/css/light.css").toExternalForm();
                scene.getStylesheets().add(lightCss);
            }
        });
    }

    private void showLoading() {
        javafx.application.Platform.runLater(() -> {
            loadingOverlay.setVisible(true);
            loadingSpinner.setVisible(true);
        });
    }

    private void hideLoading() {
        javafx.application.Platform.runLater(() -> {
            loadingOverlay.setVisible(false);
            loadingSpinner.setVisible(false);
        });
    }

    @FXML
    private void handleManageKnowledgebase() {
        chatSessionController.handleManageKnowledgebase();
    }

    @FXML
    private void handleClearSession() {
        chatSessionController.handleClearSession();
    }

    @FXML
    private void handleSendMessage() {
        chatSessionController.handleSendMessage();
    }

    @FXML
    private void handleToggleTheme() {
        var scene = toggleThemeButton.getScene();
        if (scene == null)
            return;

        var lightURL = getClass().getResource("/dev/assignment/css/light.css");
        var darkURL = getClass().getResource("/dev/assignment/css/dark.css");

        if (lightURL == null || darkURL == null) {
            return;
        }

        String lightCss = lightURL.toExternalForm();
        String darkCss = darkURL.toExternalForm();

        scene.getStylesheets().removeAll(lightCss, darkCss);

        if (isDarkMode) {
            scene.getStylesheets().add(lightCss);
        } else {
            scene.getStylesheets().add(darkCss);
        }

        isDarkMode = !isDarkMode;
    }

    @FXML
    private ToggleButton themeSwitch;

    @FXML
    private void handleThemeSwitch() {
        boolean dark = themeSwitch.isSelected();
        Scene scene = themeSwitch.getScene();
        if (scene == null)
            return;

        String darkCss = getClass().getResource("/dev/assignment/css/dark.css").toExternalForm();
        String lightCss = getClass().getResource("/dev/assignment/css/light.css").toExternalForm();

        scene.getStylesheets().removeAll(darkCss, lightCss);
        scene.getStylesheets().add(dark ? darkCss : lightCss);
    }
}
