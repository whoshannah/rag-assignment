package dev.assignment.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import dev.assignment.model.Resource;
import javafx.stage.FileChooser;

/**
 * Service class for managing resources in the knowledgebase
 */
public class ResourceService {

    private static final String STORAGE_DIR = "knowledgebase_storage";
    private final String sessionId;
    private Path storagePath;

    /**
     * Progress callback interface for import operations
     */
    public interface ImportProgressCallback {
        void onProgress(String message);
    }

    private static final FileChooser.ExtensionFilter[] VALID_EXTENSIONS = new FileChooser.ExtensionFilter[] {
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("Markdown Files", "*.md", "*.mdx"),
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
    };

    /**
     * Create ResourceService for a specific session
     */
    public ResourceService(String sessionId) {
        this.sessionId = sessionId;
        try {
            initialize();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize resource service", e);
        }
    }

    /**
     * Initialize the storage directory for this session
     */
    private void initialize() throws IOException {
        storagePath = Paths.get(STORAGE_DIR, sessionId);
        Files.createDirectories(storagePath);
    }

    /**
     * Get the storage directory path for this session
     */
    public Path getStoragePath() {
        return storagePath;
    }

    /**
     * Get all resources in the knowledgebase
     */
    public List<Resource> getAllResources() {
        List<Resource> resources = new ArrayList<>();
        File storageDir = getStoragePath().toFile();

        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        resources.add(new Resource(file.getName(), file));
                    }
                }
            }
        }
        return resources;
    }

    /**
     * Get a specific resource by filename
     */
    public Resource getResource(String fileName) {
        Path filePath = getStoragePath().resolve(fileName);
        File file = filePath.toFile();
        if (file.exists()) {
            return new Resource(fileName, file);
        }
        return null;
    }

    /**
     * Check if a resource exists
     */
    public boolean resourceExists(String fileName) {
        return getResource(fileName) != null;
    }

    /**
     * Get the count of resources in the knowledge base
     */
    public int getResourceCount() {
        File storageDir = getStoragePath().toFile();
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            return files != null ? files.length : 0;
        }
        return 0;
    }

    public boolean resourceExtensionValid(String extension) {

        for (String ext : getValidExtensionStrings()) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return false;
    }

    public FileChooser.ExtensionFilter[] getValidExtensions() {
        String[] extensions = Arrays.stream(getValidExtensionStrings())
                .map(ext -> "*" + ext)
                .toArray(String[]::new);

        FileChooser.ExtensionFilter allSupported = new FileChooser.ExtensionFilter("All Supported Files", extensions);
        FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter("All Files", "*.*");

        List<FileChooser.ExtensionFilter> filters = new ArrayList<>();
        filters.add(allSupported);

        Collections.addAll(filters, VALID_EXTENSIONS);

        filters.add(allFiles);

        return filters.toArray(FileChooser.ExtensionFilter[]::new);
    }

    public String[] getValidExtensionStrings() {
        List<String> extensions = new ArrayList<>();
        for (FileChooser.ExtensionFilter filter : VALID_EXTENSIONS) {
            extensions.addAll(filter.getExtensions().stream()
                    .map(ext -> ext.startsWith("*") ? ext.substring(1) : ext)
                    .collect(Collectors.toList()));
        }

        return extensions.toArray(String[]::new);
    }

    /**
     * Import a file into the knowledgebase
     * 
     * @param sourceFile The file to import
     * @return The imported Resource
     */
    public Resource importResource(File sourceFile) throws IOException {
        return importResource(sourceFile, null);
    }

    /**
     * Import a file into the knowledgebase with progress callback
     * 
     * @param sourceFile       The file to import
     * @param progressCallback Callback for progress updates
     * @return The imported Resource
     */
    public Resource importResource(File sourceFile, ImportProgressCallback progressCallback)
            throws IOException {
        String fileName = sourceFile.getName();
        String fileExtension = getFileExtension(fileName).toLowerCase();

        if (!resourceExtensionValid(fileExtension)) {
            throw new IOException("Unsupported file format: " + fileExtension);
        }

        Path destinationPath = getStoragePath().resolve(fileName);

        if (Files.exists(destinationPath)) {
            throw new IOException("File already exists: " + fileName);
        }

        Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);

        return new Resource(fileName, destinationPath.toFile());
    }

    /**
     * Delete a resource
     */
    public boolean deleteResource(Resource resource) throws IOException {
        return resource.delete();
    }

    /**
     * Delete a resource by filename
     */
    public boolean deleteResource(String fileName) throws IOException {
        Resource resource = getResource(fileName);
        if (resource != null) {
            return deleteResource(resource);
        }
        return false;
    }

    public static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex);
        }
        return "";
    }

    /**
     * Reads the file content of a file, handling PDF format files specifically
     */
    public static String readFileContent(File file) throws IOException {
        String fileExtension = getFileExtension(file.getName()).toLowerCase();
        if (fileExtension.equals(".pdf")) {
            try (PDDocument document = PDDocument.load(file)) {
                PDFTextStripper pdfStripper = new PDFTextStripper();
                return pdfStripper.getText(document);
            }
        } else {
            return Files.readString(file.toPath());
        }
    }
    
    /**
     * Extract text content from a PDF file
     */
    private static String readPdfContent(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        }
    }

}
