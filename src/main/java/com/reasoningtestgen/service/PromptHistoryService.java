package com.reasoningtestgen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reasoningtestgen.model.PromptEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for storing and retrieving prompt history
 * Saves prompts for analysis and debugging
 */
public class PromptHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(PromptHistoryService.class);
    private static final String HISTORY_DIR_NAME = "prompt-history";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final Path historyDir;
    private final ObjectMapper objectMapper;
    private final List<PromptEntry> memoryCache;
    private boolean enabled = true;

    public PromptHistoryService(@NotNull String basePath) {
        this.historyDir = Paths.get(basePath, HISTORY_DIR_NAME);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.memoryCache = Collections.synchronizedList(new ArrayList<>());
        
        // Create history directory
        try {
            Files.createDirectories(historyDir);
            LOG.info("Prompt history directory: {}", historyDir);
        } catch (IOException e) {
            LOG.error("Failed to create prompt history directory: {}", historyDir, e);
            this.enabled = false;
        }
    }

    /**
     * Store a prompt entry
     */
    public void storePrompt(@NotNull PromptEntry entry) {
        if (!enabled) {
            return;
        }
        
        try {
            // Add to memory cache
            memoryCache.add(entry);
            
            // Save to file
            String fileName = entry.timestamp().format(FILE_DATE_FORMAT) + "_" + 
                            entry.step().name() + ".json";
            Path filePath = historyDir.resolve(fileName);
            
            objectMapper.writeValue(filePath.toFile(), entry);
            LOG.debug("Stored prompt history: {}", fileName);
            
        } catch (IOException e) {
            LOG.error("Failed to store prompt entry", e);
        }
    }

    /**
     * Store prompt with auto-generated ID
     */
    public void storePrompt(@NotNull PromptEntry.ReasoningStep step,
                           @NotNull String systemPrompt,
                           @NotNull String userPrompt,
                           @NotNull String llmResponse,
                           @NotNull String model,
                           long responseTimeMs,
                           boolean success) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        PromptEntry entry = new PromptEntry(
            id,
            LocalDateTime.now(),
            step,
            systemPrompt,
            userPrompt,
            llmResponse,
            model,
            responseTimeMs,
            success
        );
        storePrompt(entry);
    }

    /**
     * Get all stored prompt entries
     */
    @NotNull
    public List<PromptEntry> getAllEntries() {
        List<PromptEntry> result = new ArrayList<>(memoryCache);
        result.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
        return result;
    }

    /**
     * Get entries for specific reasoning step
     */
    @NotNull
    public List<PromptEntry> getEntriesByStep(@NotNull PromptEntry.ReasoningStep step) {
        return memoryCache.stream()
            .filter(e -> e.step() == step)
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get recent entries
     */
    @NotNull
    public List<PromptEntry> getRecentEntries(int count) {
        return memoryCache.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(count)
            .collect(Collectors.toList());
    }

    /**
     * Load entries from disk
     */
    @NotNull
    public List<PromptEntry> loadFromDisk() {
        List<PromptEntry> entries = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.json")) {
            for (Path path : stream) {
                try {
                    PromptEntry entry = objectMapper.readValue(path.toFile(), PromptEntry.class);
                    entries.add(entry);
                } catch (Exception e) {
                    LOG.warn("Failed to load prompt entry: {}", path, e);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to load prompt entries from disk", e);
        }
        
        entries.sort((a, b) -> b.timestamp().compareTo(a.timestamp()));
        return entries;
    }

    /**
     * Clear all stored prompts (both memory and disk)
     */
    public void clearHistory() {
        memoryCache.clear();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir, "*.json")) {
            for (Path path : stream) {
                Files.delete(path);
            }
            LOG.info("Cleared prompt history");
        } catch (IOException e) {
            LOG.error("Failed to clear prompt history", e);
        }
    }

    /**
     * Get statistics about stored prompts
     */
    @NotNull
    public PromptHistoryStats getStats() {
        int totalEntries = memoryCache.size();
        int successfulResponses = (int) memoryCache.stream().filter(PromptEntry::success).count();
        int failedResponses = totalEntries - successfulResponses;
        
        Map<PromptEntry.ReasoningStep, Long> stepsCount = memoryCache.stream()
            .collect(Collectors.groupingBy(PromptEntry::step, Collectors.counting()));
        
        double avgResponseTime = memoryCache.stream()
            .mapToLong(PromptEntry::responseTimeMs)
            .average()
            .orElse(0.0);
        
        return new PromptHistoryStats(
            totalEntries,
            successfulResponses,
            failedResponses,
            stepsCount,
            avgResponseTime,
            historyDir.toString()
        );
    }

    /**
     * Enable or disable prompt storage
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if service is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get history directory path
     */
    @NotNull
    public Path getHistoryDir() {
        return historyDir;
    }

    /**
     * Prompt history statistics
     */
    public record PromptHistoryStats(
        int totalEntries,
        int successfulResponses,
        int failedResponses,
        Map<PromptEntry.ReasoningStep, Long> stepsCount,
        double avgResponseTimeMs,
        String storagePath
    ) {
        @Override
        public String toString() {
            return String.format(
                "PromptHistoryStats{total=%d, success=%d, failed=%d, avgTime=%.0fms, path='%s'}",
                totalEntries, successfulResponses, failedResponses, avgResponseTimeMs, storagePath
            );
        }
    }
}
