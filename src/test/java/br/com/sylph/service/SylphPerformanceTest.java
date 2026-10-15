package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.config.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in measurements against the existing local index/models. Never writes configuration or indexes. */
@EnabledIfSystemProperty(named = "sylph.perf", matches = "true")
class SylphPerformanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId USER_ZONE = ZoneId.of("America/Fortaleza");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(USER_ZONE);
    private static final List<CaseSpec> CASES = List.of(
        new CaseSpec("prazo", "simples", "Qual é o prazo para análise do cadastro de uma empresa?", List.of(
            new Criterion("prazo_empresa", "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
                List.of(4), List.of(List.of("4", "quatro"), List.of("uteis"), List.of("empresa"))))),
        new CaseSpec("supervisores", "complexa", "Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?", List.of(
            new Criterion("academico_ies", "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.", List.of(15), List.of(List.of("academico"), List.of("ies", "instituicao de ensino superior"))),
            new Criterion("campo_empresa", "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.", List.of(16), List.of(List.of("campo"), List.of("empresa", "concedente"))))),
        new CaseSpec("condicoes", "complexa", "Quais condições e exceções se aplicam ao cadastro e vínculo dos supervisores acadêmico e de campo nos cursos de Saúde, Serviço Social e demais cursos?", List.of(
            new Criterion("condicoes_cadastro", "Cursos listados exigem cadastro antes da associação; preservar condições e diferenças de vínculo nas tabelas das páginas 15 e 16.", List.of(15, 16), List.of(List.of("cadastr"), List.of("antes", "previamente", "primeiramente"), List.of("vincul"))),
            new Criterion("demais_cursos", "Para demais cursos, o acadêmico não consta no termo nem precisa ser informado na associação; não generalizar a exigência dos cursos listados.", List.of(15), List.of(List.of("demais cursos"), List.of("nao", "dispens"))))),
        new CaseSpec("ausente", "ausente", "Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório?", List.of(
            new Criterion("ausencia_explicita", "O manual consultado não estabelece um valor universal de auxílio-transporte. Reconhecer a ausência e não inventar quantia.", List.of(), List.of(List.of("nao encontrei", "nao consta", "nao foi possivel", "nao informa", "sem informacao", "nao ha informacao")))))
    );

    private record Criterion(String id, String expected, List<Integer> pages, List<List<String>> anyOfGroups) { }
    private record CaseSpec(String id, String kind, String question, List<Criterion> criteria) { }
    private record Outcome(RagService.Execution execution, List<OllamaClient.CallMetric> calls, String error) { }

    @Test void measureExistingLocalPipeline() throws Exception {
        SettingsService settings = new SettingsService(JSON);
        ProjectConfig local = settings.load();
        byte[] originalConfig = Files.readAllBytes(Path.of("config.json"));
        CsvKnowledgeStore store = new CsvKnowledgeStore(JSON);
        OllamaClient ollama = new OllamaClient(JSON);
        RagService rag = new RagService(settings, new HybridRetriever(ollama, store), ollama, store);
        String phase = System.getProperty("sylph.perf.phase", "baseline");
        String runName = System.getProperty("sylph.perf.runName", phase + "-" + STAMP.format(Instant.now()));
        assertTrue(runName.matches("[A-Za-z0-9._-]+"), "runName deve ser um nome simples");
        Path directory = Path.of(System.getProperty("sylph.perf.output", "docs/desempenho"));
        Files.createDirectories(directory);
        Path resultFile = directory.resolve(runName + ".json");
        assertFalse(Files.exists(resultFile), "Use runName novo para preservar a evidência anterior: " + resultFile);
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        report.put("phase", phase);
        report.put("startedAt", Instant.now().atZone(USER_ZONE).toString());
        report.put("status", "running");
        report.put("configLocalSha256", hash(originalConfig));
        report.put("localConfig", local);
        report.put("indexManifest", Files.isRegularFile(store.manifestFile()) ? JSON.readTree(store.manifestFile().toFile()) : null);
        report.put("environment", Map.of("os", System.getProperty("os.name"), "java", System.getProperty("java.version"), "processors", Runtime.getRuntime().availableProcessors()));
        report.put("methodology", "Uma instância de cliente Ollama, retriever, store e RAG por processo; sem aquecimento oculto, sem reconstrução do índice; ordem e caches registrados. Health consulta metadados fora do relógio e não carrega modelos. Não é feito descarregamento forçado: residência inicial do Ollama depende do uso anterior da máquina. Durações de estágios são exclusivas; métricas Ollama originais em nanossegundos. Avaliação factual humana permanece pendente.");
        report.put("results", results);
        write(report, resultFile);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "sylph-performance"); t.setDaemon(true); return t; });
        try {
            var health = ollama.health(local);
            report.put("health", health);
            report.put("modelInventory", ollama.modelInventory());
            assertTrue(health.available() && health.missingModels().isEmpty(), health.message());
            int repetitions = Integer.parseInt(System.getProperty("sylph.perf.repetitions", "1"));
            assertTrue(repetitions > 0 && repetitions <= 20, "repetitions entre 1 e 20");
            long caseTimeout = Long.parseLong(System.getProperty("sylph.perf.caseTimeoutSeconds", "1200"));
            for (String method : values("sylph.perf.methods", local.chunking_ativo)) {
                assertTrue(rag.ready(method), "Índice indisponível: " + method);
                for (String thinking : values("sylph.perf.thinking", "local")) {
                    for (String reranking : values("sylph.perf.reranking", "local")) {
                        for (int repetition = 1; repetition <= repetitions; repetition++) {
                            for (CaseSpec spec : CASES) {
                                if (!values("sylph.perf.cases", "prazo,supervisores").contains(spec.id())) continue;
                                ProjectConfig cfg = JSON.readValue(JSON.writeValueAsBytes(local), ProjectConfig.class);
                                cfg.chunking_ativo = method;
                                cfg.llm_think = boolOverride(thinking, cfg.llm_think);
                                cfg.reranking_ativo = boolOverride(reranking, cfg.reranking_ativo);
                                cfg.debug_rag = false;
                                cfg.validar();
                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("caseId", spec.id()); result.put("kind", spec.kind()); result.put("question", spec.question());
                                result.put("method", method); result.put("thinking", cfg.llm_think); result.put("reranking", cfg.reranking_ativo);
                                result.put("repetition", repetition); result.put("order", results.size() + 1); result.put("effectiveConfig", cfg);
                                result.put("startedAt", Instant.now().atZone(USER_ZONE).toString()); result.put("status", "running");
                                result.put("embeddingCacheBefore", ollama.cachedEmbeddingCount());
                                result.put("goldenCriteria", spec.criteria());
                                results.add(result); write(report, resultFile); // Preserve even a process interrupted mid-case.
                                System.out.println("[PERF] " + phase + " " + spec.id() + " " + method + " think=" + cfg.llm_think + " rerank=" + cfg.reranking_ativo + " repetição=" + repetition);
                                long start = System.nanoTime();
                                AtomicReference<Outcome> completed = new AtomicReference<>();
                                AtomicReference<OllamaClient.MetricsScope> activeMetrics = new AtomicReference<>();
                                CountDownLatch ended = new CountDownLatch(1);
                                Future<Outcome> future = worker.submit(() -> {
                                    try (var metrics = ollama.beginMetrics()) {
                                        activeMetrics.set(metrics);
                                        String error = "";
                                        try { rag.answerDetailed(spec.question(), cfg); }
                                        catch (Exception ex) { error = ex.getClass().getSimpleName() + ": " + ex.getMessage(); }
                                        Outcome outcome = new Outcome(rag.lastExecution(), metrics.calls(), error);
                                        completed.set(outcome); return outcome;
                                    } finally { ended.countDown(); }
                                });
                                Outcome outcome;
                                boolean timedOut = false;
                                try { outcome = future.get(caseTimeout, TimeUnit.SECONDS); }
                                catch (TimeoutException ex) {
                                    timedOut = true; future.cancel(true);
                                    ended.await(5, TimeUnit.SECONDS);
                                    outcome = completed.get();
                                    if (outcome == null) outcome = new Outcome(null, activeMetrics.get() == null ? List.of() : activeMetrics.get().calls(), "Timeout total do caso após " + caseTimeout + " segundos; execução parcial.");
                                }
                                result.put("wallMillis", (System.nanoTime() - start) / 1_000_000);
                                result.put("status", timedOut ? "timeout" : outcome.error().isBlank() ? "completed" : "error");
                                result.put("error", outcome.error()); result.put("execution", outcome.execution());
                                result.put("ollamaCalls", outcome.calls()); result.put("ollamaTotals", callTotals(outcome.calls()));
                                result.put("ollamaCallRates", outcome.calls().stream().map(call -> Map.of("stage", call.stage(), "endpoint", call.endpoint(), "model", call.model(),
                                    "promptTokensPerSecond", call.promptTokensPerSecond(), "outputTokensPerSecond", call.outputTokensPerSecond())).toList());
                                result.put("embeddingCacheAfter", ollama.cachedEmbeddingCount());
                                result.put("stageMillis", stages(outcome.execution()));
                                List<RagService.Source> corpus = corpusSources(store, method);
                                result.put("goldenSourceSpans", corpus.stream().filter(s -> spec.criteria().stream().anyMatch(c -> c.pages().contains(s.pagina()))).toList());
                                result.put("factualChecks", factualChecks(spec, outcome.execution(), corpus));
                                result.put("humanReview", "PENDENTE: comparar cada afirmação, atribuição, condição e exceção com os spans do gabarito; auditoria interna e sinais lexicais não atribuem acerto factual.");
                                write(report, resultFile);
                                System.out.println("[PERF] " + result.get("status") + " " + result.get("wallMillis") + " ms; evidência=" + resultFile);
                                if (timedOut && ended.getCount() != 0) throw new IllegalStateException("Cancelamento não encerrou a chamada: interrompendo para evitar sobreposição de carga no Ollama.");
                            }
                        }
                    }
                }
            }
            report.put("status", "completed");
            report.put("summary", summarize(results));
            String baseline = System.getProperty("sylph.perf.baseline", "");
            if (!baseline.isBlank()) report.put("comparison", compare(JSON.readTree(Path.of(baseline).toFile()), JSON.valueToTree(report)));
        } catch (Throwable ex) {
            report.put("status", "interrupted"); report.put("failure", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        } finally {
            worker.shutdownNow();
            report.put("finishedAt", Instant.now().atZone(USER_ZONE).toString());
            report.put("summary", summarize(results));
            report.put("configUnchanged", Arrays.equals(originalConfig, Files.readAllBytes(Path.of("config.json"))));
            write(report, resultFile);
            assertArrayEquals(originalConfig, Files.readAllBytes(Path.of("config.json")), "Benchmark alterou config.json");
        }
    }

    private static List<String> values(String property, String fallback) {
        return Arrays.stream(System.getProperty(property, fallback).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    private static boolean boolOverride(String value, boolean local) {
        if ("local".equals(value)) return local;
        if (!Set.of("true", "false").contains(value)) throw new IllegalArgumentException("Controle deve ser local,true,false: " + value);
        return Boolean.parseBoolean(value);
    }
    private static List<RagService.Source> corpusSources(CsvKnowledgeStore store, String method) throws Exception {
        Map<String, RagService.Source> sources = new LinkedHashMap<>();
        for (var chunk : store.read(method)) for (var span : chunk.spans()) {
            String key = chunk.documento() + ":" + span.pagina();
            RagService.Source previous = sources.get(key);
            String text = previous == null ? span.texto() : previous.trecho().contains(span.texto()) ? previous.trecho() : previous.trecho() + "\n" + span.texto();
            sources.put(key, new RagService.Source(chunk.documento(), span.pagina(), text));
        }
        return List.copyOf(sources.values());
    }
    private static Map<String, Object> factualChecks(CaseSpec spec, RagService.Execution execution, List<RagService.Source> corpus) {
        Map<String, Object> checks = new LinkedHashMap<>();
        String response = execution == null || execution.answer() == null ? "" : execution.answer().resposta();
        String factualResponse = fold(AnswerEvidence.CITATION.matcher(response).replaceAll(" "));
        var parsed = AnswerEvidence.parse(response, corpus);
        checks.put("citationParseIssues", parsed.issues());
        checks.put("citationUnits", parsed.units().size());
        List<String> grounding = new ArrayList<>(), responsibility = new ArrayList<>();
        for (var unit : parsed.units()) {
            String claim = AnswerEvidence.CITATION.matcher(unit.text()).replaceAll("");
            grounding.addAll(ClaimGrounding.unsupported(claim, unit.evidence()));
            responsibility.addAll(AttributionEvidence.unsupported(claim, unit.evidence().stream().map(RagService.Source::trecho).toList()));
        }
        checks.put("groundingIssues", grounding); checks.put("responsibilityIssues", responsibility);
        checks.put("criteriaSignals", spec.criteria().stream().map(c -> Map.of("id", c.id(), "expected", c.expected(),
            "lexicalCoverageSignal", c.anyOfGroups().stream().allMatch(group -> group.stream().anyMatch(term -> signalContains(factualResponse, term))),
            "expectedPages", c.pages(), "citedExpectedPages", execution == null || execution.answer() == null ? List.of() : execution.answer().fontes().stream().map(RagService.Source::pagina).filter(c.pages()::contains).distinct().toList())).toList());
        checks.put("missingGoldenPages", spec.criteria().stream().flatMap(c -> c.pages().stream()).distinct().filter(page -> corpus.stream().noneMatch(s -> s.pagina() == page)).toList());
        checks.put("classification", "PENDENTE_REVISAO_HUMANA");
        return checks;
    }
    private static Map<String, Object> stages(RagService.Execution execution) {
        if (execution == null) return Map.of("partial", true);
        JsonNode e = JSON.valueToTree(execution);
        long rerank = execution.retrieval() == null ? 0 : execution.retrieval().rerankingMillis();
        long repair = e.path("repairMillis").asLong(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retrievalExclusive", Math.max(0, execution.retrievalMillis() - rerank));
        result.put("reranking", rerank);
        result.put("generation", Math.max(0, execution.generationMillis() - repair));
        result.put("auditInitial", e.has("initialAuditMillis") ? e.path("initialAuditMillis").asLong() : execution.auditMillis());
        result.put("repair", e.has("repairMillis") ? repair : "indisponível nesta versão");
        result.put("auditRepair", e.path("repairAuditMillis").asLong(0));
        result.put("auditTotal", execution.auditMillis()); result.put("total", execution.totalMillis());
        result.put("contextCharacters", e.has("contextCharacters") ? e.path("contextCharacters").asLong() : "indisponível nesta versão");
        return result;
    }
    private static Map<String, Object> callTotals(List<OllamaClient.CallMetric> calls) {
        Map<String, Object> totals = new LinkedHashMap<>();
        JsonNode rows = JSON.valueToTree(calls);
        for (String name : List.of("promptEvalCount", "promptEvalCachedCount", "evalCount", "thinkingCharacters", "loadDurationNanos", "promptEvalDurationNanos", "evalDurationNanos", "totalDurationNanos")) {
            long total = 0; int observed = 0;
            for (JsonNode row : rows) if (row.path(name).asLong(-1) >= 0) { total += row.path(name).asLong(); observed++; }
            totals.put(name, observed == 0 ? null : total);
            totals.put(name + "ObservedCalls", observed);
        }
        totals.put("calls", calls.size());
        long outputTokens = 0, outputNanos = 0, inputTokens = 0, inputNanos = 0;
        for (var call : calls) {
            if (call.evalCount() >= 0 && call.evalDurationNanos() > 0) { outputTokens += call.evalCount(); outputNanos += call.evalDurationNanos(); }
            if (call.promptEvalCount() >= 0 && call.promptEvalDurationNanos() > 0) {
                inputTokens += Math.max(0, call.promptEvalCount() - Math.max(0, call.promptEvalCachedCount())); inputNanos += call.promptEvalDurationNanos();
            }
        }
        totals.put("weightedOutputTokensPerSecond", outputNanos == 0 ? null : outputTokens * 1_000_000_000d / outputNanos);
        totals.put("weightedPromptTokensPerSecond", inputNanos == 0 ? null : inputTokens * 1_000_000_000d / inputNanos);
        return totals;
    }
    private static Map<String, Object> summarize(List<Map<String, Object>> results) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String kind : List.of("all", "simples", "complexa", "ausente")) {
            var selected = results.stream().filter(r -> kind.equals("all") || kind.equals(r.get("kind"))).toList();
            var measured = selected.stream().filter(r -> r.get("wallMillis") instanceof Number).toList();
            var completed = measured.stream().filter(r -> "completed".equals(r.get("status"))).toList();
            var timings = completed.stream().mapToLong(r -> ((Number) r.get("wallMillis")).longValue()).summaryStatistics();
            var attempts = measured.stream().mapToLong(r -> ((Number) r.get("wallMillis")).longValue()).summaryStatistics();
            summary.put(kind, Map.of("attempted", selected.size(), "completed", completed.size(), "failedOrTimeout", selected.size() - completed.size(),
                "meanMillis", timings.getCount() == 0 ? 0 : timings.getAverage(), "maxMillis", timings.getCount() == 0 ? 0 : timings.getMax(),
                "meanAttemptMillis", attempts.getCount() == 0 ? 0 : attempts.getAverage(), "maxAttemptMillis", attempts.getCount() == 0 ? 0 : attempts.getMax(),
                "completedUnder60Seconds", completed.stream().filter(r -> ((Number) r.get("wallMillis")).longValue() < 60_000).count()));
        }
        summary.put("factualClassification", "PENDENTE_REVISAO_HUMANA");
        return summary;
    }
    private static Map<String, Object> compare(JsonNode baseline, JsonNode current) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (JsonNode now : current.path("results")) {
            JsonNode before = null;
            List<JsonNode> matches = new ArrayList<>();
            for (JsonNode old : baseline.path("results")) if (key(old).equals(key(now))) matches.add(old);
            for (JsonNode old : matches) if (old.path("thinking").equals(now.path("thinking"))) { before = old; break; }
            if (before == null && matches.size() == 1) before = matches.getFirst();
            if (before == null) continue;
            long oldMs = before.path("wallMillis").asLong(), newMs = now.path("wallMillis").asLong();
            List<String> review = new ArrayList<>();
            for (String control : List.of("top_k", "modelo_llm", "modelo_embeddings", "seed_llm", "temperatura_llm", "modo_recuperacao", "expansao_paginas", "candidatos_recuperacao", "candidatos_reranking"))
                if (!before.path("effectiveConfig").path(control).equals(now.path("effectiveConfig").path(control))) review.add("Controle alterado: " + control + ". Não atribuir a diferença apenas à otimização.");
            if (!baseline.path("indexManifest").equals(current.path("indexManifest"))) review.add("Índice mudou entre as fases; comparação exige controle da base de evidências.");
            if (!before.path("status").asText().equals("completed") || !now.path("status").asText().equals("completed")) review.add("Caso incompleto: tempo não representa resposta validada.");
            if (!before.path("execution").path("answer").path("encontrou").equals(now.path("execution").path("answer").path("encontrou"))) review.add("Mudança no indicador encontrou; revisar cobertura/ausência.");
            if (!before.path("execution").path("answer").path("fontes").equals(now.path("execution").path("answer").path("fontes"))) review.add("Fontes finais mudaram; comparar suporte e páginas.");
            if (!before.path("execution").path("answer").path("resposta").equals(now.path("execution").path("answer").path("resposta"))) review.add("Texto mudou; confrontar cada afirmação com gabarito.");
            for (JsonNode oldSignal : before.path("factualChecks").path("criteriaSignals")) for (JsonNode newSignal : now.path("factualChecks").path("criteriaSignals"))
                if (oldSignal.path("id").equals(newSignal.path("id")) && oldSignal.path("lexicalCoverageSignal").asBoolean() && !newSignal.path("lexicalCoverageSignal").asBoolean()) review.add("Possível perda de cobertura: " + oldSignal.path("id").asText() + ". Exige revisão, sinal lexical não comprova erro.");
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("case", key(now)); pair.put("beforeMillis", oldMs); pair.put("afterMillis", newMs);
            pair.put("beforeThinking", before.path("thinking")); pair.put("afterThinking", now.path("thinking"));
            pair.put("latencyReductionPercent", oldMs == 0 ? 0 : 100d * (oldMs - newMs) / oldMs);
            pair.put("beforeOllamaTotals", before.path("ollamaTotals")); pair.put("afterOllamaTotals", now.path("ollamaTotals"));
            pair.put("beforeRepaired", before.path("execution").path("repaired")); pair.put("afterRepaired", now.path("execution").path("repaired"));
            pair.put("beforeStageMillis", before.path("stageMillis")); pair.put("afterStageMillis", now.path("stageMillis"));
            pair.put("reviewRequired", review); pair.put("factualClassification", "PENDENTE_REVISAO_HUMANA"); pairs.add(pair);
        }
        return Map.of("baselinePhase", baseline.path("phase").asText(), "pairs", pairs, "baselineSummary", baseline.path("summary"), "currentSummary", current.path("summary"),
            "acceptance", "NÃO APROVADO AUTOMATICAMENTE: rejeitar otimizações com regressão factual confirmada; sinais de cobertura exigem revisão das respostas completas e fontes.");
    }
    private static String key(JsonNode row) { return row.path("caseId").asText() + "/" + row.path("method").asText() + "/rerank=" + row.path("reranking").asText() + "/r=" + row.path("repetition").asText(); }
    private static boolean signalContains(String text, String term) {
        if (term.matches("\\d+") || "ies".equals(term)) return Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(term) + "(?![\\p{L}\\d])").matcher(text).find();
        return text.contains(term);
    }
    private static String fold(String text) { return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replaceAll("\\s+", " "); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void write(Map<String, Object> report, Path json) throws Exception {
        Path writtenJson = atomic(json, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        JsonNode tree = JSON.valueToTree(report);
        StringBuilder md = new StringBuilder("# Desempenho Sylph — " + tree.path("phase").asText() + "\n\nStatus: " + tree.path("status").asText() + ". Avaliação factual humana: PENDENTE.\n\n");
        md.append("Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.\n\n");
        md.append("| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |\n|---|---|---|---|---|---|---:|---:|---:|---|\n");
        for (JsonNode row : tree.path("results")) md.append("| ").append(row.path("caseId").asText()).append(" | ").append(row.path("method").asText()).append(" | ").append(row.path("thinking").asText()).append(" | ").append(row.path("reranking").asText()).append(" | ").append(row.path("repetition").asText()).append(" | ").append(row.path("status").asText()).append(" | ").append(String.format(Locale.ROOT, "%.3f", row.path("wallMillis").asLong() / 1000d)).append(" | ").append(row.path("ollamaTotals").path("promptEvalCount")).append(" | ").append(row.path("ollamaTotals").path("evalCount")).append(" | ").append(row.path("execution").path("repaired")).append(" |\n");
        md.append("\nResultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](").append(writtenJson.getFileName()).append(").\n");
        for (JsonNode row : tree.path("results")) {
            md.append("\n## ").append(row.path("caseId").asText()).append(" / ").append(row.path("method").asText()).append(" / repetição ").append(row.path("repetition").asText()).append("\n\n").append(row.path("question").asText()).append("\n\n").append(row.path("execution").path("answer").path("resposta").asText("Sem resposta concluída.")).append("\n\n");
            md.append("Estágios (ms): `").append(row.path("stageMillis")).append("`.\n\n");
            md.append("Triagem factual (não comprova acerto):\n\n```json\n").append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(row.path("factualChecks"))).append("\n```\n");
        }
        if (tree.has("summary")) md.append("\n## Resumo\n\n```json\n").append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(tree.path("summary"))).append("\n```\n");
        if (tree.has("comparison")) md.append("\n## Comparação\n\n```json\n").append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(tree.path("comparison"))).append("\n```\n");
        atomic(json.resolveSibling(json.getFileName().toString().replaceFirst("\\.json$", ".md")), md.toString());
    }
    private static Path atomic(Path path, String contents) throws Exception {
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".perf-", ".tmp");
        Files.writeString(temp, contents, StandardCharsets.UTF_8);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        IOException failure;
        while (true) {
            try {
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
                return path;
            } catch (IOException ex) {
                failure = ex;
                if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) break;
                try { Thread.sleep(Math.min(100, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())))); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        // A reader/sync lock must not discard completed measurements or turn them into a RAG failure.
        String name = path.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String stem = extension > 0 ? name.substring(0, extension) : name;
        String suffix = extension > 0 ? name.substring(extension) : ".txt";
        Path recovery = path.resolveSibling(stem + ".recovery-" + UUID.randomUUID() + suffix);
        try { Files.move(temp, recovery); }
        catch (IOException recoveryFailure) { recovery = temp; }
        System.out.println("[PERF] Destino bloqueado (" + failure.getClass().getSimpleName() + "); evidência completa preservada em " + recovery.toAbsolutePath());
        return recovery;
    }
}
