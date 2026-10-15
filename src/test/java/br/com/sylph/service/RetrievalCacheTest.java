package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.model.KnowledgeChunk;
import br.com.sylph.model.SourceSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetrievalCacheTest {
    private static KnowledgeChunk chunk(String id, int page, String text, double secondCoordinate) {
        return new KnowledgeChunk(id, "Manual.pdf", page, "recursivo", text, new double[]{1, secondCoordinate},
            List.of(new SourceSpan(page, 0, text.length(), text)), "doc", "hash", "nomic-embed-text", "2");
    }

    private static List<KnowledgeChunk> chunks() {
        return List.of(chunk("seven", 1, "UNOPAR prazo de 7 dias úteis.", .2),
            chunk("nine", 4, "UNOPAR prazo de 9 dias úteis.", .4),
            chunk("long", 8, "UNOPAR prazo de entrega do relatório e todas as condições exigidas.", .6));
    }

    private static ProjectConfig config(String mode) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.modo_recuperacao = mode;
        cfg.top_k = 3;
        cfg.expansao_paginas = 0;
        cfg.trechos_contexto = 3;
        return cfg;
    }

    @ParameterizedTest @ValueSource(strings = {"hybrid", "lexical", "vetorial"})
    void warmSnapshotPreservesEveryRankingScoreAndSelectedChunk(String mode) throws Exception {
        ProjectConfig cfg = config(mode);
        OllamaClient ollama = mock(OllamaClient.class);
        CsvKnowledgeStore store = mock(CsvKnowledgeStore.class);
        List<KnowledgeChunk> snapshot = chunks();
        when(store.read("recursivo")).thenReturn(snapshot);
        when(ollama.embed(eq(cfg), anyList())).thenReturn(List.of(new double[]{1, 0}));
        HybridRetriever retriever = new HybridRetriever(ollama, store);
        var cold = retriever.retrieveDetailed(cfg, "UNOPAR prazo 7");
        var warm = retriever.retrieveDetailed(cfg, "UNOPAR prazo 7");
        assertFalse(cold.chunks().isEmpty());
        assertEquals(cold.chunks(), warm.chunks());
        assertEquals(cold.candidates(), warm.candidates());
        assertEquals(cold.scores(), warm.scores());
        assertEquals("disabled", warm.rerankingStatus());
        verify(store, times(2)).read("recursivo");
        if (mode.equals("lexical")) verifyNoInteractions(ollama);
    }

    @Test void alternatingReferenceAndAdvancedProfilesKeepsTheirDifferentTokenization() throws Exception {
        ProjectConfig cfg = config("hybrid");
        OllamaClient ollama = mock(OllamaClient.class);
        CsvKnowledgeStore store = mock(CsvKnowledgeStore.class);
        List<KnowledgeChunk> snapshot = chunks();
        when(store.read("recursivo")).thenReturn(snapshot);
        when(store.readBaseline("recursivo")).thenReturn(snapshot);
        when(ollama.embed(eq(cfg), anyList())).thenReturn(List.of(new double[]{1, 0}));
        HybridRetriever retriever = new HybridRetriever(ollama, store);
        String query = "UNOPAR prazo 7";
        var advanced = retriever.retrieveDetailed(cfg, query);
        cfg.perfil_execucao = "sp21";
        var reference = retriever.retrieveDetailed(cfg, query);
        var referenceWarm = retriever.retrieveDetailed(cfg, query);
        double[] expectedLegacyBm25 = HybridRetriever.bm25(snapshot, query);
        Map<String, HybridRetriever.RetrievalScore> byId = reference.scores().stream()
            .collect(Collectors.toMap(HybridRetriever.RetrievalScore::chunkId, Function.identity()));
        for (int i = 0; i < snapshot.size(); i++)
            assertEquals(expectedLegacyBm25[i], byId.get(snapshot.get(i).id()).bm25(), 0);
        assertEquals(reference.scores(), referenceWarm.scores());
        assertNotEquals(advanced.scores().getFirst().bm25(), byId.get(advanced.scores().getFirst().chunkId()).bm25());
        cfg.perfil_execucao = "sp22";
        assertEquals(advanced.scores(), retriever.retrieveDetailed(cfg, query).scores());
    }

    @Test void cachedTokenStatisticsRecalculateScoresAfterBm25ConfigurationChanges() throws Exception {
        ProjectConfig cfg = config("lexical");
        OllamaClient ollama = mock(OllamaClient.class);
        CsvKnowledgeStore store = mock(CsvKnowledgeStore.class);
        when(store.read("recursivo")).thenReturn(chunks());
        HybridRetriever retriever = new HybridRetriever(ollama, store);
        var initial = retriever.retrieveDetailed(cfg, "prazo");
        double oldK1 = cfg.bm25_k1, oldB = cfg.bm25_b;
        cfg.bm25_k1 = 2.4;
        cfg.bm25_b = .3;
        assertNotEquals(initial.scores(), retriever.retrieveDetailed(cfg, "prazo").scores());
        cfg.bm25_k1 = oldK1;
        cfg.bm25_b = oldB;
        assertEquals(initial.scores(), retriever.retrieveDetailed(cfg, "prazo").scores());
        verifyNoInteractions(ollama);
    }

    @Test void warmedLexicalCacheNeverBypassesModelCompatibilityOrStoreValidation() throws Exception {
        ProjectConfig cfg = config("lexical");
        OllamaClient ollama = mock(OllamaClient.class);
        CsvKnowledgeStore store = mock(CsvKnowledgeStore.class);
        when(store.read("recursivo")).thenReturn(chunks());
        HybridRetriever retriever = new HybridRetriever(ollama, store);
        retriever.retrieveDetailed(cfg, "prazo");
        cfg.modelo_embeddings = "outro-modelo";
        assertThrows(IOException.class, () -> retriever.retrieveDetailed(cfg, "prazo"));
        cfg.modelo_embeddings = "nomic-embed-text";
        when(store.read("recursivo")).thenThrow(new IOException("CSV corrompido"));
        assertThrows(IOException.class, () -> retriever.retrieveDetailed(cfg, "prazo"));
        assertNull(HybridRetriever.lastResult());
        verifyNoInteractions(ollama);
    }
}
