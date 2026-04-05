package com.reasoningtestgen.service;

import com.reasoningtestgen.model.PromptEntry;
import com.reasoningtestgen.model.PromptEntry.ReasoningStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PromptHistoryService
 */
class PromptHistoryServiceTest {

    private PromptHistoryService service;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new PromptHistoryService(tempDir.toString());
    }

    @Nested
    @DisplayName("Storage")
    class StorageTests {

        @Test
        @DisplayName("Should store and retrieve prompt entries")
        void shouldStoreAndRetrieveEntries() {
            PromptEntry entry = createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true);
            
            service.storePrompt(entry);
            List<PromptEntry> entries = service.getAllEntries();
            
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).id()).isEqualTo(entry.id());
            assertThat(entries.get(0).step()).isEqualTo(ReasoningStep.INTENT_ANALYSIS);
        }

        @Test
        @DisplayName("Should store multiple entries sorted by timestamp")
        void shouldStoreMultipleEntriesSortedByTimestamp() {
            PromptEntry entry1 = createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true);
            PromptEntry entry2 = createSampleEntry(ReasoningStep.SCENARIO_MAPPING, true);
            
            service.storePrompt(entry1);
            service.storePrompt(entry2);
            
            List<PromptEntry> entries = service.getAllEntries();
            
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).timestamp()).isAfterOrEqualTo(entries.get(1).timestamp());
        }

        @Test
        @DisplayName("Should filter entries by step")
        void shouldFilterEntriesByStep() {
            PromptEntry intentEntry = createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true);
            PromptEntry scenarioEntry = createSampleEntry(ReasoningStep.SCENARIO_MAPPING, true);
            
            service.storePrompt(intentEntry);
            service.storePrompt(scenarioEntry);
            
            List<PromptEntry> intentEntries = service.getEntriesByStep(ReasoningStep.INTENT_ANALYSIS);
            
            assertThat(intentEntries).hasSize(1);
            assertThat(intentEntries.get(0).step()).isEqualTo(ReasoningStep.INTENT_ANALYSIS);
        }

        @Test
        @DisplayName("Should get recent entries with limit")
        void shouldGetRecentEntriesWithLimit() {
            for (int i = 0; i < 5; i++) {
                service.storePrompt(createSampleEntry(ReasoningStep.CODE_GENERATION, true));
            }
            
            List<PromptEntry> recent = service.getRecentEntries(3);
            
            assertThat(recent).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Persistence")
    class PersistenceTests {

        @Test
        @DisplayName("Should persist entries to disk")
        void shouldPersistEntriesToDisk() throws Exception {
            PromptEntry entry = createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true);
            service.storePrompt(entry);
            
            List<PromptEntry> loaded = service.loadFromDisk();
            
            assertThat(loaded).hasSize(1);
            assertThat(loaded.get(0).id()).isEqualTo(entry.id());
        }

        @Test
        @DisplayName("Should clear all history")
        void shouldClearAllHistory() {
            service.storePrompt(createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true));
            service.storePrompt(createSampleEntry(ReasoningStep.SCENARIO_MAPPING, true));
            
            service.clearHistory();
            
            assertThat(service.getAllEntries()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {

        @Test
        @DisplayName("Should generate statistics")
        void shouldGenerateStatistics() {
            service.storePrompt(createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true));
            service.storePrompt(createSampleEntry(ReasoningStep.SCENARIO_MAPPING, true));
            service.storePrompt(createSampleEntry(ReasoningStep.CODE_GENERATION, false));
            
            PromptHistoryService.PromptHistoryStats stats = service.getStats();
            
            assertThat(stats.totalEntries()).isEqualTo(3);
            assertThat(stats.successfulResponses()).isEqualTo(2);
            assertThat(stats.failedResponses()).isEqualTo(1);
            assertThat(stats.stepsCount()).containsKeys(
                ReasoningStep.INTENT_ANALYSIS,
                ReasoningStep.SCENARIO_MAPPING,
                ReasoningStep.CODE_GENERATION
            );
        }
    }

    @Nested
    @DisplayName("Enable/Disable")
    class EnableDisableTests {

        @Test
        @DisplayName("Should enable and disable service")
        void shouldEnableAndDisableService() {
            assertThat(service.isEnabled()).isTrue();
            
            service.setEnabled(false);
            service.storePrompt(createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true));
            
            assertThat(service.getAllEntries()).isEmpty();
            
            service.setEnabled(true);
            service.storePrompt(createSampleEntry(ReasoningStep.INTENT_ANALYSIS, true));
            assertThat(service.getAllEntries()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Convenience Methods")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("Should store prompt with convenience method")
        void shouldStorePromptWithConvenienceMethod() {
            service.storePrompt(
                ReasoningStep.CODE_GENERATION,
                "System prompt",
                "User prompt",
                "LLM response",
                "qwen/qwen3.5-9b",
                1500,
                true
            );
            
            List<PromptEntry> entries = service.getAllEntries();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).systemPrompt()).isEqualTo("System prompt");
            assertThat(entries.get(0).userPrompt()).isEqualTo("User prompt");
            assertThat(entries.get(0).llmResponse()).isEqualTo("LLM response");
            assertThat(entries.get(0).model()).isEqualTo("qwen/qwen3.5-9b");
            assertThat(entries.get(0).responseTimeMs()).isEqualTo(1500);
            assertThat(entries.get(0).success()).isTrue();
        }
    }

    // ===== Helper Methods =====

    private PromptEntry createSampleEntry(ReasoningStep step, boolean success) {
        return new PromptEntry(
            UUID.randomUUID().toString().substring(0, 8),
            LocalDateTime.now(),
            step,
            "System prompt for " + step.name(),
            "User prompt for " + step.name(),
            "LLM response for " + step.name(),
            "qwen/qwen3.5-9b",
            (long) (Math.random() * 2000 + 500),
            success
        );
    }
}
