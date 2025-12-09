package dev.assignment.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dev.assignment.handler.ResourceDeletionHandler;
import dev.assignment.handler.ResourceImportHandler;
import dev.assignment.handler.ResourceDeletionHandler.DeletionResult;
import dev.assignment.handler.ResourceImportHandler.ImportResult;
import dev.assignment.model.Resource;
import dev.assignment.service.RAGService;
import dev.assignment.service.ResourceService;
import dev.assignment.util.ResourceValidator;
import dev.assignment.util.ResourceValidator.ValidationResult;
import dev.assignment.view.AlertHelper;
import dev.assignment.view.ResourceListCell;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller for the Resource Management window
 * Coordinates between UI and handler classes
 */
public class ResourceManagementController {

    @FXML
    private ListView<Resource> resourceListView;

    private ResourceService resourceService;
    private RAGService ragService;
    private Runnable onResourcesChangedCallback;

    private ResourceValidator validator;
    private ResourceImportHandler importHandler;
    private ResourceDeletionHandler deletionHandler;

    @FXML
    private void initialize() {
        resourceListView.setCellFactory(listView -> new ResourceListCell());
        resourceListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
        this.validator = new ResourceValidator(resourceService);
        this.importHandler = new ResourceImportHandler(resourceService, ragService, this::onImportComplete);
        this.deletionHandler = new ResourceDeletionHandler(resourceService, ragService);
        loadResources();
    }

    public void setRagService(RAGService ragService) {
        this.ragService = ragService;
        if (resourceService != null) {
            this.importHandler = new ResourceImportHandler(resourceService, ragService, this::onImportComplete);
            this.deletionHandler = new ResourceDeletionHandler(resourceService, ragService);
        }
    }

    public void setOnResourcesChangedCallback(Runnable callback) {
        this.onResourcesChangedCallback = callback;
    }

    private void onImportComplete() {
        loadResources();
        notifyResourcesChanged();
    }

    private void loadResources() {
        if (resourceService == null)
            return;

        resourceListView.getItems().clear();
        resourceListView.getItems().addAll(resourceService.getAllResources());
    }

    private void notifyResourcesChanged() {
        if (onResourcesChangedCallback != null) {
            onResourcesChangedCallback.run();
        }
    }

    private List<Resource> getSelectedResources() {
        return new ArrayList<>(resourceListView.getSelectionModel().getSelectedItems());
    }

    private Stage getOwnerStage() {
        return (Stage) resourceListView.getScene().getWindow();
    }

    @FXML
    private void handleAddResource() {
        if (validator.isDocumentLimitReached()) {
            AlertHelper.showWarning("Document Limit Reached", "Cannot add more documents",
                    "This knowledge base has reached the maximum limit. Please remove some documents before adding new ones.");
            return;
        }

        FileChooser fileChooser = createFileChooser();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(getOwnerStage());

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            if (selectedFiles.size() > 1) {
                handleMultipleFileImport(selectedFiles);
            } else {
                handleSingleFileImport(selectedFiles.get(0));
            }
        }
    }

    @FXML
    private void handleRemoveResource() {
        List<Resource> selected = getSelectedResources();
        if (selected.isEmpty()) {
            AlertHelper.showWarning("No Selection", "Please select one or more resources to remove.");
            return;
        }

        if (!confirmDeletion(selected))
            return;

        DeletionResult result = deletionHandler.deleteMultipleResources(selected);

        loadResources();
        notifyResourcesChanged();

        if (!result.hasFailures()) {
            String message = result.successCount == 1
                    ? "File has been removed."
                    : result.successCount + " files have been removed.";
            AlertHelper.showInfo("Success", message);
        } else {
            String message = String.format(
                    "Removed %d file(s) successfully.\n\nFailed to remove %d file(s):\n%s",
                    result.successCount, result.failCount, result.getFailedFilesMessage());
            AlertHelper.showWarning("Partial Success", message);
        }
    }

    @FXML
    private void handleClose() {
        getOwnerStage().close();
    }

    private void handleSingleFileImport(File file) {
        ValidationResult validation = validator.validateSingleFile(file);
        if (!validation.isValid()) {
            AlertHelper.showWarning("File Too Large", "Document exceeds size limit", validation.getErrorMessage());
            return;
        }

        String targetFileName = getTargetFileName(file);

        if (resourceService.resourceExists(targetFileName)) {
            AlertHelper.showError("File Exists", "Cannot import file",
                    "The file '" + targetFileName
                            + "' already exists in the knowledgebase. Please rename the file or remove the existing one first.");
            return;
        }

        importHandler.importSingleFile(file, targetFileName, getOwnerStage(),
                () -> {
                    String message = ragService != null
                            ? "File imported and indexed successfully as '" + targetFileName + "'."
                            : "File imported successfully as '" + targetFileName + "'.";
                    AlertHelper.showInfo("Success", message);
                },
                error -> AlertHelper.showError("Error", "Failed to import or index file: " + error));
    }

    private void handleMultipleFileImport(List<File> files) {
        ValidationResult validation = validator.validateMultipleFiles(files);
        if (!validation.isValid()) {
            AlertHelper.showWarning("Validation Error", "Cannot import files", validation.getErrorMessage());
            return;
        }

        List<String> conflicts = validator.findConflicts(files);

        if (!conflicts.isEmpty()) {
            String fileList = String.join("\n", conflicts.subList(0, Math.min(5, conflicts.size())));
            if (conflicts.size() > 5) {
                fileList += "\n... and " + (conflicts.size() - 5) + " more";
            }

            AlertHelper.showError("Files Already Exist", "Cannot import files",
                    "The following files already exist in the knowledgebase:\n" + fileList +
                            "\n\nPlease rename these files or remove the existing ones first.\n" +
                            "Other files will not be imported.");
            return;
        }

        importHandler.importMultipleFiles(files, getOwnerStage(), this::showImportResults);
    }

    private void showImportResults(ImportResult result) {
        if (result.cancelled) {
            AlertHelper.showInfo("Import Cancelled",
                    String.format("Import was cancelled.\n\nCompleted: %d\nFailed: %d\nSkipped: %d",
                            result.success, result.failed, result.skipped));
        } else {
            String message = String.format("Import complete!\n\nSuccessfully imported: %d\nFailed: %d" +
                    (result.skipped > 0 ? "\nSkipped: %d" : ""),
                    result.success, result.failed, result.skipped);

            if (result.failed > 0) {
                AlertHelper.showWarning("Import Complete with Errors", message);
            } else {
                AlertHelper.showInfo("Import Complete", message);
            }
        }
    }

    private boolean confirmDeletion(List<Resource> resources) {
        String header;
        String content;

        if (resources.size() == 1) {
            header = "Remove Resource";
            content = "Are you sure you want to remove '" + resources.get(0).getFileName() +
                    "' from the knowledgebase?\nThis will delete the file from storage.";
        } else {
            String fileList = resources.stream()
                    .limit(5)
                    .map(Resource::getFileName)
                    .collect(Collectors.joining("\n"));
            if (resources.size() > 5) {
                fileList += "\n... and " + (resources.size() - 5) + " more";
            }
            header = "Remove " + resources.size() + " Resources";
            content = "Are you sure you want to remove these files from the knowledgebase?\n\n" +
                    fileList + "\n\nThis will delete the files from storage.";
        }

        return AlertHelper.showConfirm("Confirm Deletion", header, content);
    }

    private FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Resource File");
        if (resourceService != null) {
            fileChooser.getExtensionFilters().addAll(resourceService.getValidExtensions());
        }
        return fileChooser;
    }

    private String getTargetFileName(File file) {
        return file.getName();
    }
}