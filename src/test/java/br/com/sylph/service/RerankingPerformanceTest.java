package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.config.SettingsService;
import br.com.sylph.model.RankedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Retrieval-only probe: same client/store/retriever, no answer generation, audit or repair. */
@EnabledIfSystemProperty(named = "sylph.perf.rerankingOnly", matches = "true")
class RerankingPerformanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void measureRerankingWithoutGeneratingAnswers() throws Exception {
        byte[] originalConfig = Files.readAllBytes(Path.of("config.json"));
        ProjectConfig local = new SettingsService(JSON).load();
        OllamaClient ollama = new OllamaClient(JSON);
        CsvKnowledgeStore store = new CsvKnowledgeStore(JSON);
        HybridRetriever retriever = new HybridRetriever(ollama, store);
        String phase = System.getProperty("sylph.perf.phase", "candidate");
        String runName = System.getProperty("sylph.perf.runName", phase + "-reranking");
        assertTrue(runName.matches("[A-Za-z0-9._-]+"));
        Path directory = Path.of(System.getProperty("sylph.perf.output", "docs/desempenho"));
        Files.createDirectories(directory);
        Path output = directory.resolve(runName + ".json");
        assertFalse(Files.exists(output), "Use nome novo para preservar evidências: " + output);
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        report.put("phase", phase); report.put("status", "running"); report.put("startedAt", now());
        report.put("configLocalSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(originalConfig)));
        report.put("localConfig", local);
        report.put("methodology", "Somente retrieveDetailed: nenhuma geração de resposta, auditoria ou reparo. Uma instância de cliente/store/retriever; desligado antes de ligado. A primeira recuperação aquece índice lexical/CSV e embedding da pergunta; tempo exclusivo do reranker registrado separadamente. Sem descarga forçada dos modelos. Configuração e top_k preservados. Comparar fallback, ordem e scores; não atribuir acerto factual sem avaliação das fontes.");
        report.put("indexManifest", Files.isRegularFile(store.manifestFile()) ? JSON.readTree(store.manifestFile().toFile()) : null);
        report.put("results", rows);
        write(output, report);
        try {
            List<String> cases = Arrays.stream(System.getProperty("sylph.perf.cases", "prazo").split(",")).map(String::trim).toList();
            int repetitions = Integer.parseInt(System.getProperty("sylph.perf.rerankingRepetitions", "1"));
            assertTrue(repetitions >= 1 && repetitions <= 5);
            for (String id : cases) {
                assertTrue(Set.of("prazo", "supervisores").contains(id), "Caso inválido: " + id);
                String question = id.equals("prazo") ? "Qual é o prazo para análise do cadastro de uma empresa?" : "Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?";
                for (boolean enabled : new boolean[]{false, true}) for (int repetition = 1; repetition <= repetitions; repetition++) {
                    ProjectConfig cfg = JSON.readValue(JSON.writeValueAsBytes(local), ProjectConfig.class);
                    cfg.chunking_ativo = System.getProperty("sylph.perf.method", local.chunking_ativo);
                    cfg.reranking_ativo = enabled; cfg.debug_rag = false; cfg.validar();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("caseId", id); row.put("question", question); row.put("method", cfg.chunking_ativo);
                    row.put("reranking", enabled); row.put("repetition", repetition); row.put("effectiveConfig", cfg);
                    row.put("startedAt", now()); row.put("status", "running"); row.put("embeddingCacheBefore", ollama.cachedEmbeddingCount());
                    rows.add(row); write(output, report);
                    long start = System.nanoTime();
                    try (var metrics = ollama.beginMetrics()) {
                        try {
                            var result = retriever.retrieveDetailed(cfg, question);
                            row.put("retrievalExclusiveMillis", Math.max(0, result.retrievalMillis() - result.rerankingMillis()));
                            row.put("rerankingMillis", result.rerankingMillis()); row.put("retrievalTotalMillis", result.retrievalMillis());
                            row.put("rerankingStatus", result.rerankingStatus()); row.put("rerankingReason", result.rerankingReason());
                            row.put("candidates", result.candidates().stream().map(RerankingPerformanceTest::chunk).toList());
                            row.put("contextChunks", result.chunks().stream().map(RerankingPerformanceTest::chunk).toList());
                            row.put("scores", result.scores()); row.put("status", "completed");
                        } catch (Exception ex) {
                            row.put("status", "error"); row.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        } finally {
                            row.put("wallMillis", (System.nanoTime() - start) / 1_000_000);
                            row.put("ollamaCalls", metrics.calls()); row.put("embeddingCacheAfter", ollama.cachedEmbeddingCount());
                            row.put("finishedAt", now()); write(output, report);
                        }
                    }
                    System.out.println("[PERF-RERANK] " + phase + " " + id + " ativo=" + enabled + " repetição=" + repetition + " total=" + row.get("wallMillis") + "ms rerank=" + row.get("rerankingMillis") + "ms status=" + row.get("rerankingStatus"));
                }
            }
            report.put("status", "completed");
        } catch (Throwable ex) {
            report.put("status", "interrupted"); report.put("failure", ex.getClass().getSimpleName() + ": " + ex.getMessage()); throw ex;
        } finally {
            report.put("finishedAt", now()); report.put("configUnchanged", Arrays.equals(originalConfig, Files.readAllBytes(Path.of("config.json"))));
            write(output, report);
            assertArrayEquals(originalConfig, Files.readAllBytes(Path.of("config.json")));
        }
    }

    private static Map<String, Object> chunk(RankedChunk ranked) {
        var c = ranked.chunk();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.id()); row.put("docId", c.docId()); row.put("documento", c.documento());
        row.put("pagina", c.pagina()); row.put("spans", c.spans()); row.put("texto", c.texto());
        row.put("cosseno", ranked.cosseno()); row.put("bm25", ranked.bm25()); row.put("hybridScore", ranked.scoreFinal());
        return row;
    }
    private static String now() { return Instant.now().atZone(ZoneId.of("America/Fortaleza")).toString(); }
    private static void write(Path file, Map<String, Object> report) throws Exception {
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".rerank-", ".tmp");
        Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        IOException failure;
        while (true) {
            try {
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                return;
            } catch (IOException ex) {
                failure = ex;
                if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) break;
                try { Thread.sleep(Math.min(100, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        String name = file.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String stem = extension > 0 ? name.substring(0, extension) : name;
        String suffix = extension > 0 ? name.substring(extension) : ".json";
        Path recovery = file.resolveSibling(stem + ".recovery-" + UUID.randomUUID() + suffix);
        try { Files.move(temporary, recovery); }
        catch (IOException recoveryFailure) { recovery = temporary; }
        System.out.println("[PERF-RERANK] Destino bloqueado (" + failure.getClass().getSimpleName() + "); evidência completa preservada em " + recovery.toAbsolutePath());
    }
}
