package dev.assignment.model;

import java.util.List;

/**
 * Represents a response from the RAG system including sources
 */
public record QueryResponse(String response, List<String> sources) {
    public boolean hasSources() {
        return sources != null && !sources.isEmpty();
    }
}
