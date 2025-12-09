package dev.assignment.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.assignment.model.QueryResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

/**
 * Service for handling RAG (Retrieval Augmented Generation) operations
 */
public class RAGService {

    private static final Logger logger = LogManager.getLogger(RAGService.class);

    private final String modelName;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final OpenAiChatModel chatModel;
    private final List<ChatMessage> sessionHistory;
    private final Map<String, Long> indexedFiles;
    private final DocumentIndexingService indexingService;
    private final RerankingService rerankingService;

    public interface ProgressCallback {
        void onProgress(String message, int current, int total);
    }

    private static final int MAX_RESULTS = 5;
    private static final double MIN_SCORE = 0.5;
    private static final int MAX_RESULTS_BEFORE_RERANK = 15;

    private final String sessionId;

    public RAGService(String sessionId, String modelName) {
        this.sessionId = sessionId;
        this.modelName = modelName;
        this.sessionHistory = new ArrayList<>();
        this.indexedFiles = new HashMap<>();
        String apiKey = APIKeyService.getInstance().getApiKey();

        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(1.0)
                .build();

        this.indexingService = new DocumentIndexingService(sessionId, embeddingModel, embeddingStore, indexedFiles);
        this.rerankingService = new RerankingService();

        sessionHistory.add(SystemMessage.from(
                "You are a helpful AI assistant. Use the provided context to answer questions accurately. " +
                        "If the context doesn't contain relevant information, say so politely. " +
                        "In your response, do not use any markdown formatting. Simple plain text is preferred."));

        loadSessionHistory();

        EmbeddingCacheService.loadCache(sessionId, embeddingStore, indexedFiles);
    }

    /**
     * Load session history from database and restore it to the session
     */
    private void loadSessionHistory() {
        List<dev.assignment.model.ChatMessage> dbMessages = DatabaseService.getInstance().getChatHistory(sessionId);

        for (dev.assignment.model.ChatMessage dbMessage : dbMessages) {
            if (dbMessage.isUser()) {
                sessionHistory.add(UserMessage.from(dbMessage.content()));
            } else {
                sessionHistory.add(AiMessage.from(dbMessage.content()));
            }
        }

        logger.debug("Loaded {} messages from database into session history", dbMessages.size());
    }

    /**
     * Index all documents from the knowledgebase (incremental) with progress
     * callback
     */
    public void indexKnowledgebase(ResourceService resourceService, ProgressCallback progressCallback)
            throws IOException {
        indexingService.indexKnowledgebase(resourceService,
                progressCallback != null ? (msg, curr, total) -> progressCallback.onProgress(msg, curr, total) : null);
    }

    /**
     * Index a single file when added to knowledgebase
     */
    public void indexSingleFile(File file) throws IOException {
        indexingService.indexSingleFile(file);
    }

    /**
     * Remove a file from the index when deleted from knowledgebase
     */
    public void removeFileFromIndexByName(String fileName) {
        indexingService.removeFileFromIndexByName(fileName);
    }

    /**
     * Query the RAG system with a user message
     * Basically, our final prompt to the chat model is:
     * 
     * Relevant context:
     * [context from retrieved segments]
     * User question: [user message]
     * 
     * So, simply put, RAG is essentially just an automated way to build better
     * prompts for LLMs by retrieving relevant information from a knowledgebase.
     * 
     * For the context chaining part, we are utilizing recent session history
     * to facilitate follow-up questions that depend on prior context.
     * 
     * For example:
     * User: "How old is Melvin Chia?"
     * AI: "Melvin Chia is 19 years old."
     * User: "How about his brother?"
     * AI: "Melvin Chia's brother is 28 years old."
     */
    public dev.assignment.model.QueryResponse query(String userMessage) {
        String contextualizedQuery = buildContextualizedQuery(userMessage);

        Embedding queryEmbedding = embeddingModel.embed(contextualizedQuery).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(MAX_RESULTS_BEFORE_RERANK)
                .minScore(MIN_SCORE)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> relevantSegments = searchResult.matches();
        logger.debug("Retrieval found {} segments", relevantSegments.size());

        List<EmbeddingMatch<TextSegment>> rerankedSegments = rerankingService.rerank(contextualizedQuery,
                relevantSegments);

        if (rerankedSegments.size() > MAX_RESULTS) {
            rerankedSegments = rerankedSegments.subList(0, MAX_RESULTS);
        }

        Set<String> sourceFiles = new HashSet<>();
        for (EmbeddingMatch<TextSegment> match : rerankedSegments) {
            TextSegment segment = match.embedded();
            if (segment.metadata() != null && segment.metadata().containsKey("fileName")) {
                String fileName = segment.metadata().getString("fileName");
                sourceFiles.add(fileName);
            }
        }
        logger.debug("Query matched {} segments from documents: {}", rerankedSegments.size(), sourceFiles);

        StringBuilder context = new StringBuilder();
        if (!rerankedSegments.isEmpty()) {
            context.append("Relevant context:\n\n");
            for (EmbeddingMatch<TextSegment> match : rerankedSegments) {
                context.append(match.embedded().text()).append("\n\n");
            }
        }

        String messageWithContext;
        if (context.length() > 0) {
            messageWithContext = context + "\nUser question: " + userMessage;
        } else {
            messageWithContext = userMessage;
        }

        sessionHistory.add(UserMessage.from(userMessage));
        ;

        logger.debug("Sending message to chat model: {} with {} messages in history", modelName,
                sessionHistory.size());

        List<ChatMessage> messagesForRequest = new ArrayList<>(sessionHistory);
        messagesForRequest.set(messagesForRequest.size() - 1, UserMessage.from(messageWithContext));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messagesForRequest)
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        AiMessage aiMessage = chatResponse.aiMessage();
        String responseText = aiMessage.text();

        sessionHistory.add(aiMessage);

        return new QueryResponse(responseText, new java.util.ArrayList<>(sourceFiles));
    }

    /**
     * Clear session history (keeps system message)
     */
    public void clearHistory() {
        sessionHistory.clear();
        sessionHistory.add(SystemMessage.from(
                "You are a helpful AI assistant. Use the provided context to answer questions accurately. " +
                        "If the context doesn't contain relevant information, say so politely." +
                        " In your response, do not use any markdown formatting. Simple plain text is preferred."));
    }

    /**
     * Build a contextualized query by incorporating recent session history
     * This helps with follow-up questions like "How about that?" or "Tell me more"
     * 
     * @param userMessage The current user message
     * @return Contextualized query string for better embedding search
     */
    private String buildContextualizedQuery(String userMessage) {
        if (sessionHistory.size() <= 1) {
            return userMessage;
        }

        StringBuilder contextBuilder = new StringBuilder();
        int startIdx = Math.max(1, sessionHistory.size() - 4);

        for (int i = startIdx; i < sessionHistory.size(); i++) {
            ChatMessage msg = sessionHistory.get(i);
            if (msg instanceof UserMessage) {
                contextBuilder.append("User asked: ").append(((UserMessage) msg).singleText()).append(" ");
            } else if (msg instanceof AiMessage) {
                String aiText = ((AiMessage) msg).text();
                String snippet = aiText.length() > 100 ? aiText.substring(0, 100) + "..." : aiText;
                contextBuilder.append("Assistant answered: ").append(snippet).append(" ");
            }
        }

        contextBuilder.append("Current question: ").append(userMessage);

        String contextualizedQuery = contextBuilder.toString();
        logger.debug("Contextualized query: {}", contextualizedQuery);

        return contextualizedQuery;
    }

}
