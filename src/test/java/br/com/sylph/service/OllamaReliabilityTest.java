package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class OllamaReliabilityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final class Stub implements AutoCloseable {
        final HttpServer server;
        final ExecutorService handlers = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        final AtomicReference<String> body = new AtomicReference<>("{\"embeddings\":[[1,0]]}");
        final AtomicReference<String> request = new AtomicReference<>("");
        final AtomicReference<String> path = new AtomicReference<>("");
        final AtomicInteger status = new AtomicInteger(200), calls = new AtomicInteger();
        final AtomicInteger transientFailures = new AtomicInteger();
        final AtomicLong delay = new AtomicLong();
        final CountDownLatch received = new CountDownLatch(1);
        Stub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(handlers);
            server.createContext("/", exchange -> {
                path.set(exchange.getRequestURI().getPath());
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                calls.incrementAndGet(); received.countDown();
                try {
                    if (delay.get() > 0) Thread.sleep(delay.get());
                    int code = transientFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0 ? 503 : status.get();
                    byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(code, bytes.length);
                    try (var out = exchange.getResponseBody()) { out.write(bytes); }
                } catch (InterruptedException ex) { Thread.currentThread().interrupt();
                } finally { exchange.close(); }
            });
            server.start();
        }
        ProjectConfig config() {
            ProjectConfig cfg = new ProjectConfig();
            cfg.ollama_url = "http://127.0.0.1:" + server.getAddress().getPort();
            cfg.ollama_tentativas = 1; return cfg;
        }
        @Override public void close() { server.stop(0); handlers.shutdownNow(); }
    }

    @Test void embeddingCacheDeduplicatesBatchAndReturnsDefensiveCopies() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            List<double[]> first = client.embed(cfg, List.of("texto", "texto"));
            assertEquals(2, first.size()); assertEquals(1, mapper.readTree(stub.request.get()).path("input").size());
            first.getFirst()[0] = 999;
            assertArrayEquals(new double[]{1, 0}, first.get(1));
            assertArrayEquals(new double[]{1, 0}, client.embed(cfg, List.of("texto")).getFirst());
            assertEquals(1, stub.calls.get()); assertEquals(1, client.cachedEmbeddingCount());
            assertFalse(mapper.readTree(stub.request.get()).path("truncate").asBoolean(true));
        }
    }

    @Test void scopedMetricsPreserveRealOllamaUnitsAndDoNotAlterThinkingConfiguration() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config();
            OllamaClient client = new OllamaClient(mapper);
            stub.body.set("""
                {"message":{"content":"Resposta.","thinking":"12345"},"total_duration":3000000000,
                 "load_duration":100000000,"prompt_eval_duration":500000000,"eval_duration":2000000000,
                 "prompt_eval_count":100,"prompt_eval_cached_count":25,"eval_count":40}
                """);
            try (var metrics = client.beginMetrics()) {
                assertEquals("Resposta.", client.chat(cfg, "pergunta", "contexto"));
                var call = metrics.calls().getFirst();
                assertEquals("/api/chat", call.endpoint()); assertEquals(cfg.modelo_llm, call.model());
                assertEquals("generation", call.stage()); assertTrue(call.success());
                assertEquals(1, call.attempts()); assertEquals(200, call.httpStatus());
                assertEquals(3_000_000_000L, call.totalDurationNanos());
                assertEquals(100_000_000L, call.loadDurationNanos());
                assertEquals(500_000_000L, call.promptEvalDurationNanos());
                assertEquals(2_000_000_000L, call.evalDurationNanos());
                assertEquals(100, call.promptEvalCount()); assertEquals(25, call.promptEvalCachedCount()); assertEquals(40, call.evalCount());
                assertEquals(150, call.promptTokensPerSecond()); assertEquals(20, call.outputTokensPerSecond());
                assertEquals(5, call.thinkingCharacters());
                assertEquals(cfg.llm_think, mapper.readTree(stub.request.get()).path("think").asBoolean());
                assertThrows(UnsupportedOperationException.class, () -> metrics.calls().clear());
            }
        }
    }

    @Test void metricScopesAreUnboundedNestedAndStopCapturingAfterClose() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); cfg.cache_embeddings_entradas = 0;
            OllamaClient client = new OllamaClient(mapper);
            var all = client.beginMetrics();
            try (var nested = client.beginMetrics()) {
                client.embed(cfg, List.of("primeiro"));
                assertEquals(1, nested.calls().size());
            }
            for (int i = 0; i < 34; i++) client.embed(cfg, List.of("texto " + i));
            all.close(); all.close();
            client.embed(cfg, List.of("fora"));
            stub.body.set("{\"message\":{\"content\":\"Resposta fora do escopo.\"}}");
            client.chat(cfg, "pergunta", "contexto");
            assertEquals(35, all.calls().size()); assertEquals(30, client.diagnostics().size());
            assertEquals(30, client.recentMetrics().size());
            assertEquals("generation", client.recentMetrics().getLast().stage(), "Diagnóstico normal inclui chamadas fora do escopo");
            assertThrows(UnsupportedOperationException.class, () -> client.recentMetrics().clear());
            assertTrue(all.calls().stream().allMatch(call -> call.stage().equals("embed")));
            assertEquals(-1, all.calls().getFirst().totalDurationNanos());
            assertEquals(-1, all.calls().getFirst().evalCount());
            assertEquals(0, all.calls().getFirst().outputTokensPerSecond());
        }
    }

    @Test void metricStagesDistinguishRerankingAuditingRepairAndFailures() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            var sources = List.of(new RagService.Source("Manual", 1, "O tutor acompanha o aluno."));
            stub.body.set("{\"message\":{\"content\":\"{}\"}}");
            try (var metrics = client.beginMetrics()) {
                client.structuredChat(cfg, "sistema", "usuario", mapper.createObjectNode(), Duration.ofSeconds(2));
                client.auditAnswer(cfg, "pergunta", sources, "O tutor acompanha o aluno. [Fonte: Manual | Página: 1]");
                client.repairAnswer(cfg, "pergunta", sources, "resposta", new AnswerEvidence.Audit(List.of(), List.of(), List.of(), false));
                stub.status.set(503); cfg.ollama_tentativas = 2;
                assertThrows(IOException.class, () -> client.embed(cfg, List.of("falha")));
                assertEquals(List.of("rerank", "audit", "repair", "embed"), metrics.calls().stream().map(OllamaClient.CallMetric::stage).toList());
                var failure = metrics.calls().getLast();
                assertFalse(failure.success()); assertEquals(2, failure.attempts());
                assertEquals(503, failure.httpStatus()); assertFalse(failure.error().isBlank());
                assertEquals(-1, failure.evalDurationNanos());
            }
        }
    }

    @Test void embeddingCacheSeparatesUrlModelTextAndBoundsEntries() throws Exception {
        try (Stub first = new Stub(); Stub second = new Stub()) {
            ProjectConfig cfg = first.config(); cfg.cache_embeddings_entradas = 1;
            OllamaClient client = new OllamaClient(mapper);
            client.embed(cfg, List.of("a")); client.embed(cfg, List.of("b")); client.embed(cfg, List.of("a"));
            assertEquals(3, first.calls.get()); assertEquals(1, client.cachedEmbeddingCount());
            cfg.modelo_embeddings = "other-model"; client.embed(cfg, List.of("a"));
            assertEquals(4, first.calls.get());
            cfg.ollama_url = second.config().ollama_url; client.embed(cfg, List.of("a"));
            assertEquals(1, second.calls.get()); assertEquals(1, client.cachedEmbeddingCount());
            cfg.cache_embeddings_entradas = 0; client.embed(cfg, List.of("a")); client.embed(cfg, List.of("a"));
            assertEquals(3, second.calls.get()); assertEquals(0, client.cachedEmbeddingCount());
        }
    }

    @Test void malformedEmbeddingResponsesAreRejectedWithoutPoisoningCache() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            for (String invalid : List.of("", "not json", "[]", "{}", "{\"embeddings\":[]}",
                "{\"embeddings\":[[]]}", "{\"embeddings\":[[0,0]]}", "{\"embeddings\":[[\"1\",0]]}",
                "{\"embeddings\":[[null,0]]}", "{\"embeddings\":[[1e999,0]]}",
                "{\"embeddings\":[[1,0]]} {}", "{\"embeddings\":[[1,0]],\"embeddings\":[[2,0]]}")) {
                stub.body.set(invalid);
                assertThrows(IOException.class, () -> client.embed(cfg, List.of("invalid")), invalid);
                assertEquals(0, client.cachedEmbeddingCount(), invalid);
            }
            stub.body.set("{\"embeddings\":[[1,0]]}");
            assertArrayEquals(new double[]{1, 0}, client.embed(cfg, List.of("invalid")).getFirst());
        }
    }

    @Test void incompatibleBatchAndChangedModelDimensionsFailExplicitly() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            stub.body.set("{\"embeddings\":[[1,0],[1,0,0]]}");
            assertThrows(IOException.class, () -> client.embed(cfg, List.of("first", "second")));
            assertEquals(0, client.cachedEmbeddingCount());
            stub.body.set("{\"embeddings\":[[1,0]]}"); client.embed(cfg, List.of("first"));
            stub.body.set("{\"embeddings\":[[1,0,0]]}");
            IOException error = assertThrows(IOException.class, () -> client.embed(cfg, List.of("second")));
            assertTrue(error.getMessage().contains("Dimensão")); assertEquals(0, client.cachedEmbeddingCount());
        }
    }

    @Test void healthReportsInstalledAndMissingModelsWithoutDownloading() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            stub.body.set("{\"models\":[{\"name\":\"nomic-embed-text:latest\",\"digest\":\"a\"}]}");
            OllamaClient.HealthStatus health = client.health(cfg);
            assertTrue(health.available()); assertEquals(List.of(cfg.modelo_llm), health.missingModels());
            assertEquals("/api/tags", stub.path.get());
            assertThrows(IOException.class, () -> client.verifyModels(cfg));
            stub.body.set("{\"models\":[{\"name\":\"nomic-embed-text:latest\",\"digest\":\"a\"},{\"name\":\"sylph-unopar:latest\",\"digest\":\"b\"}]}");
            client.verifyModels(cfg); assertEquals(3, stub.calls.get());
            stub.status.set(503); assertFalse(client.health(cfg).available());
        }
    }

    @Test void changedInstalledModelDigestInvalidatesEmbeddings() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            stub.body.set("{\"models\":[{\"name\":\"nomic-embed-text:latest\",\"digest\":\"before\"}]}");
            client.listModels(cfg);
            stub.body.set("{\"embeddings\":[[1,0]]}"); client.embed(cfg, List.of("a"));
            assertEquals(1, client.cachedEmbeddingCount());
            stub.body.set("{\"models\":[{\"name\":\"nomic-embed-text:latest\",\"digest\":\"after\"}]}");
            client.listModels(cfg); assertEquals(0, client.cachedEmbeddingCount());
        }
    }

    @Test void retriesOnlyTransientFailuresAndRecordsSuccessfulLatency() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); cfg.ollama_tentativas = 2;
            OllamaClient client = new OllamaClient(mapper); stub.transientFailures.set(1);
            assertArrayEquals(new double[]{1, 0}, client.embed(cfg, List.of("retry")).getFirst());
            assertEquals(2, stub.calls.get());
            OllamaClient.CallDiagnostic diagnostic = client.diagnostics().getLast();
            assertTrue(diagnostic.success()); assertEquals(2, diagnostic.attempts());
            assertEquals(200, diagnostic.httpStatus()); assertTrue(diagnostic.milliseconds() >= 0);
            stub.status.set(404);
            IOException error = assertThrows(IOException.class, () -> client.embed(cfg, List.of("missing")));
            assertTrue(error.getMessage().contains("nenhum download")); assertEquals(3, stub.calls.get());
            stub.status.set(200); stub.body.set("not json");
            assertThrows(IOException.class, () -> client.embed(cfg, List.of("bad-json")));
            assertEquals(4, stub.calls.get(), "Malformed JSON is not a transient failure");
        }
    }

    @Test void structuredChatSendsSchemaThinkFalseAndRejectsTrailingOrEmptyContent() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            var schema = mapper.readTree("{\"type\":\"object\"}");
            stub.body.set(mapper.writeValueAsString(Map.of("message", Map.of("content", "{\"scores\":[]}"))));
            assertTrue(client.structuredChat(cfg, "system", "user", schema, Duration.ofSeconds(1)).has("scores"));
            var request = mapper.readTree(stub.request.get());
            assertEquals(schema, request.path("format")); assertFalse(request.path("think").asBoolean(true));
            assertFalse(request.path("stream").asBoolean(true)); assertEquals(0, request.path("options").path("temperature").intValue());
            assertEquals(cfg.seed_llm, request.path("options").path("seed").intValue());
            for (String content : List.of("", "{} {}", "{\"a\":1,\"a\":2}")) {
                stub.body.set(mapper.writeValueAsString(Map.of("message", Map.of("content", content))));
                assertThrows(IOException.class, () -> client.structuredChat(cfg, "system", "user", schema, Duration.ofSeconds(1)));
            }
            stub.body.set("{\"message\":{\"content\":3}}");
            assertThrows(IOException.class, () -> client.structuredChat(cfg, "system", "user", schema, Duration.ofSeconds(1)));
        }
    }

    @Test void structuredCallHasSingleAttemptTimeoutForRerankerFallback() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); cfg.ollama_tentativas = 4;
            OllamaClient client = new OllamaClient(mapper); stub.delay.set(5000);
            long start = System.nanoTime();
            assertThrows(IOException.class, () -> client.structuredChat(cfg, "system", "user",
                mapper.createObjectNode().put("type", "object"), Duration.ofMillis(100)));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3000);
            assertEquals(1, stub.calls.get()); assertEquals(1, client.diagnostics().getLast().attempts());
        }
    }

    @Test void chatTimeoutDoesNotRepeatGenerationButTransientServerFailuresStillRetry() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); cfg.ollama_tentativas = 4; cfg.ollama_timeout_segundos = 1;
            OllamaClient client = new OllamaClient(mapper); stub.delay.set(5000);
            long start = System.nanoTime();
            assertThrows(IOException.class, () -> client.chat(cfg, "pergunta", "contexto"));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 3000);
            assertEquals(1, stub.calls.get()); assertEquals(1, client.diagnostics().getLast().attempts());
            stub.delay.set(0); stub.transientFailures.set(1);
            stub.body.set("{\"message\":{\"content\":\"Resposta válida.\"}}");
            assertEquals("Resposta válida.", client.chat(cfg, "pergunta", "contexto"));
            assertEquals(3, stub.calls.get()); assertEquals(2, client.diagnostics().getLast().attempts());
        }
    }

    @Test void keepAliveIsExplicitAndRespectsConfigurationForEmbeddingsAndChats() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); OllamaClient client = new OllamaClient(mapper);
            cfg.ollama_keep_alive_segundos = 0;
            client.embed(cfg, List.of("texto"));
            assertEquals("0s", mapper.readTree(stub.request.get()).path("keep_alive").asText());
            cfg.ollama_keep_alive_segundos = 900;
            stub.body.set("{\"message\":{\"content\":\"{}\"}}");
            client.chat(cfg, "pergunta", "contexto");
            assertEquals("900s", mapper.readTree(stub.request.get()).path("keep_alive").asText());
            cfg.ollama_keep_alive_segundos = 30;
            client.structuredChat(cfg, "sistema", "usuario", mapper.createObjectNode(), Duration.ofSeconds(2));
            assertEquals("30s", mapper.readTree(stub.request.get()).path("keep_alive").asText());
        }
    }

    @Test void interruptionCancelsInFlightHttpOperationAndPreservesInterruptFlag() throws Exception {
        try (Stub stub = new Stub()) {
            ProjectConfig cfg = stub.config(); cfg.ollama_tentativas = 4;
            OllamaClient client = new OllamaClient(mapper); stub.delay.set(5000);
            AtomicBoolean interrupted = new AtomicBoolean(); AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch finished = new CountDownLatch(1);
            Thread caller = new Thread(() -> {
                try { client.embed(cfg, List.of("cancel")); }
                catch (Throwable ex) { failure.set(ex); interrupted.set(Thread.currentThread().isInterrupted()); }
                finally { finished.countDown(); }
            });
            caller.setDaemon(true); caller.start();
            assertTrue(stub.received.await(2, TimeUnit.SECONDS)); caller.interrupt();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.get()); assertTrue(interrupted.get()); assertEquals(1, stub.calls.get());
        }
    }

    @Test void invalidAndRemoteUrlsFailBeforeAnyNetworkCall() {
        for (String url : List.of("file:///etc/passwd", "http://user:password@127.0.0.1:11434", "http://127.0.0.1:11434/api",
            "http://127.0.0.1:11434?x=1", "http://127.0.0.1:11434#fragment", "http://example.com:11434", "bad url"))
            assertThrows(IOException.class, () -> OllamaClient.endpointUri(url, "/api/embed"), url);
    }

    @Test void missingServerIsDiagnosedWithoutRequiringRealOllama() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        ProjectConfig cfg = new ProjectConfig(); cfg.ollama_url = "http://127.0.0.1:" + port; cfg.ollama_tentativas = 1;
        OllamaClient client = new OllamaClient(mapper);
        IOException failure = assertThrows(IOException.class, () -> client.embed(cfg, List.of("texto")));
        assertTrue(failure.getMessage().contains("indisponível")); assertFalse(client.health(cfg).available());
    }
}
