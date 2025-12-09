package dev.assignment.view;

import java.util.function.Consumer;

import dev.assignment.model.Session;
import dev.assignment.service.DatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Custom sidebar component for displaying and managing sessions
 */
public class SessionSidebar extends VBox {

    private final VBox sessionListContainer;
    private final ScrollPane scrollPane;
    private final Button newSessionButton;
    private ObservableList<Session> sessions = FXCollections.observableArrayList();
    private Session currentSession;
    private Consumer<Session> onSessionSelected;
    private Runnable onSessionChanged;

    public SessionSidebar() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(20, 0, 20, 20));
        setSpacing(0);

        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(-1.0);
        scrollPane.setPrefWidth(-1.0);
        scrollPane.getStyleClass().add("scroll-pane");
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        sessionListContainer = new VBox();
        sessionListContainer.setSpacing(6.0);
        scrollPane.setContent(sessionListContainer);

        VBox.setMargin(scrollPane, new Insets(0, 0, 20, 0));

        newSessionButton = new Button("New Session");
        newSessionButton.setMaxWidth(Double.MAX_VALUE);
        newSessionButton.setMnemonicParsing(false);
        newSessionButton.setOnAction(e -> handleNewSession());
        VBox.setMargin(newSessionButton, new Insets(0, 20, 0, 0));

        getChildren().addAll(scrollPane, newSessionButton);
    }

    /**
     * Set callback for when a session is selected
     * 
     * @param onSessionSelected - the callback to set
     */
    public void setOnSessionSelected(Consumer<Session> onSessionSelected) {
        this.onSessionSelected = onSessionSelected;
    }

    /**
     * Set callback for when sessions are changed (added, renamed, deleted)
     * 
     * @param onSessionChanged - the callback to set
     */
    public void setOnSessionChanged(Runnable onSessionChanged) {
        this.onSessionChanged = onSessionChanged;
    }

    /**
     * Load sessions from the database and display them in the sidebar
     */
    public void loadSessions() {
        DatabaseService databaseService = DatabaseService.getInstance();
        if (databaseService == null) {
            sessions = FXCollections.observableArrayList();
            sessionListContainer.getChildren().clear();

            Label errorLabel = new Label("Database unavailable");
            errorLabel.getStyleClass().add("error-label");
            errorLabel.setMaxWidth(Double.MAX_VALUE);
            errorLabel.setAlignment(Pos.CENTER);
            sessionListContainer.getChildren().add(errorLabel);
            return;
        }

        try {
            sessions = FXCollections.observableArrayList(
                    databaseService.getAllSessions());
        } catch (java.sql.SQLException e) {
            AlertHelper.showError(
                    "Database Error",
                    "Database Schema Incompatible",
                    "The database schema is incompatible with this version of the application.\\n\\n" +
                            "Please delete the 'rag_sessions.db' file and restart the application.\\n\\n" +
                            "Error: " + e.getMessage());

            javafx.application.Platform.exit();
            System.exit(1);
            return;
        }

        sessionListContainer.getChildren().clear();

        if (sessions.isEmpty()) {
            Label emptyLabel = new Label("No sessions yet.");
            emptyLabel.getStyleClass().add("muted-label");
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            emptyLabel.setAlignment(Pos.CENTER);
            sessionListContainer.getChildren().add(emptyLabel);
        } else {
            for (Session session : sessions) {
                boolean isSelected = currentSession != null && currentSession.getId().equals(session.getId());
                SidebarSessionEntry sessionBox = new SidebarSessionEntry(
                        session,
                        isSelected,
                        () -> selectSession(session),
                        this::handleSessionChanged);
                sessionListContainer.getChildren().add(sessionBox);
            }
        }
    }

    /**
     * Handle session changes (rename, delete, etc.) and notify parent controller
     */
    private void handleSessionChanged() {
        String currentSessionId = currentSession != null ? currentSession.getId() : null;

        loadSessions();

        if (currentSessionId != null) {
            boolean sessionStillExists = sessions.stream()
                    .anyMatch(s -> s.getId().equals(currentSessionId));
            if (!sessionStillExists) {
                currentSession = null;
            }
        }

        if (onSessionChanged != null) {
            onSessionChanged.run();
        }
    }

    /**
     * Select a session and notify listeners
     * 
     * @param session - the session to select
     */
    private void selectSession(Session session) {
        currentSession = session;
        if (onSessionSelected != null) {
            onSessionSelected.accept(session);
        }
        refreshSessionStyling();
    }

    /**
     * Refresh the styling of session boxes to reflect the currently selected
     * session
     */
    private void refreshSessionStyling() {
        for (int i = 0; i < sessionListContainer.getChildren().size(); i++) {
            if (sessionListContainer.getChildren().get(i) instanceof SidebarSessionEntry) {
                SidebarSessionEntry sessionBox = (SidebarSessionEntry) sessionListContainer.getChildren().get(i);
                Session session = sessions.get(i);

                boolean isSelected = currentSession != null && currentSession.getId().equals(session.getId());
                sessionBox.updateStyling(isSelected);
            }
        }
    }

    /**
     * Handle creating a new session
     */
    private void handleNewSession() {
        NewSessionDialog dialog = new NewSessionDialog();
        Session newSession = dialog.showAndWait();

        if (newSession != null) {
            loadSessions();
            selectSession(newSession);
            if (onSessionChanged != null) {
                onSessionChanged.run();
            }
        }
    }

    public Session getCurrentSession() {
        return currentSession;
    }

    public void clearCurrentSession() {
        currentSession = null;
        refreshSessionStyling();
    }

    public ObservableList<Session> getSessions() {
        return sessions;
    }
}
