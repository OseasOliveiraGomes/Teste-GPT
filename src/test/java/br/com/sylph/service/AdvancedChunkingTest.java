package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.model.SourceSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deterministic boundary/provenance tests. No running Ollama or official document is required. */
class AdvancedChunkingTest {
    @TempDir Path temporary;

    @Test void recursivePrefersSectionBoundaryBeforeSmallerTextBoundaries() {
        ProjectConfig cfg = config(280, 80, 0);
        String first = "# MATRÍCULA\n" + "A matrícula exige documento atualizado e vínculo acadêmico.\n".repeat(3);
        String second = "# RESPONSABILIDADES\n" + "A coordenação acompanha o estudante durante a atividade obrigatória.\n".repeat(6);
        var pages = List.of(new PdfPages.Page(3, first + "\n" + second));
        var chunks = new TextChunker().recursive(cfg, pages);
        assertEquals(first.strip(), chunks.getFirst().text());
        assertTrue(chunks.get(1).text().startsWith("# RESPONSABILIDADES"));
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void recursiveRetainsWholeListItemsIncludingConditionsAndNegations() {
        ProjectConfig cfg = config(230, 50, 0);
        String introduction = "Considere as condições descritas no regulamento para solicitar a atividade.\n";
        List<String> rules = List.of(
            "1. O estudante somente poderá iniciar as atividades quando o termo estiver assinado e aprovado. Não se admite início antecipado.",
            "2. A empresa deve indicar supervisor habilitado. Se houver alteração, o documento deverá ser atualizado antes da continuidade.",
            "Art. 4º A coordenação analisará o requerimento após o recebimento completo. O prazo não começa com documentação incompleta.");
        var pages = List.of(new PdfPages.Page(1, introduction + String.join("\n", rules)));
        var chunks = new TextChunker().recursive(cfg, pages);
        for (String rule : rules)
            assertTrue(chunks.stream().anyMatch(chunk -> chunk.text().contains(rule)), "Regra fragmentada: " + rule);
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void recursiveConfiguredOverlapRepeatsOnlyRealOffsetsAndNeverExceedsMaximum() {
        ProjectConfig cfg = config(240, 70, 45);
        String text = numberedWords(400);
        var pages = List.of(new PdfPages.Page(7, text));
        var chunks = new TextChunker().recursive(cfg, pages);
        assertTrue(chunks.size() > 3);
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
        boolean overlapFound = false;
        for (int i = 1; i < chunks.size(); i++) {
            SourceSpan before = chunks.get(i - 1).spans().getLast(), after = chunks.get(i).spans().getFirst();
            assertTrue(after.inicio() > before.inicio(), "A divisão deve avançar");
            int overlap = before.fim() - after.inicio();
            assertTrue(overlap <= cfg.chunk_overlap_chars, "Overlap configurado ultrapassado");
            overlapFound |= overlap > 0;
        }
        assertTrue(overlapFound);
        assertEquals(chunks.size(), chunks.stream().map(chunk -> chunk.spans().toString()).distinct().count());
    }

    @Test void recursiveLongWordIsSplitWithoutDroppingCharactersOrExceedingMaximum() {
        ProjectConfig cfg = config(180, 50, 30);
        var pages = List.of(new PdfPages.Page(2, "á".repeat(1013)));
        assertMaxAndExactCoverage(pages, new TextChunker().recursive(cfg, pages), cfg.chunk_max_chars);
    }

    @Test void recursiveCanSpanPagesButRetainsEveryExactPageInterval() {
        ProjectConfig cfg = config(600, 80, 0);
        var pages = List.of(new PdfPages.Page(3, "Primeira página: matrícula regular e acompanhamento obrigatório."),
            new PdfPages.Page(8, "Segunda página: o termo de compromisso exige assinatura."));
        var chunks = new TextChunker().recursive(cfg, pages);
        assertEquals(1, chunks.size());
        assertEquals(List.of(3, 8), chunks.getFirst().spans().stream().map(SourceSpan::pagina).toList());
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void disablingCrossPagesKeepsBothMethodsPageContained() throws Exception {
        ProjectConfig cfg = config(600, 80, 0); cfg.chunk_cross_pages = false;
        var pages = List.of(new PdfPages.Page(2, "O estudante solicita matrícula regular."),
            new PdfPages.Page(5, "A coordenação analisa o pedido recebido."));
        TextChunker chunker = new TextChunker();
        for (var chunks : List.of(chunker.recursive(cfg, pages), chunker.semantic(cfg, pages, coherentOllama()))) {
            assertEquals(2, chunks.size());
            assertTrue(chunks.stream().allMatch(chunk -> chunk.spans().size() == 1));
            assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
        }
    }

    @Test void semanticUsesRealAdjacentEmbeddingTransitionEvenWhenDocumentHasUnder700Characters() throws Exception {
        ProjectConfig cfg = config(600, 100, 0);
        String internship = "Estágio curricular exige matrícula ativa e supervisão em uma unidade concedente. "
            + "Estágio deve respeitar o termo assinado antes do início das atividades. ";
        String library = "Biblioteca oferece acervo digital para consultas e estudo individual. "
            + "Biblioteca permite acesso remoto mediante identificação acadêmica.";
        String text = internship + library;
        assertTrue(text.length() < 700);
        var pages = List.of(new PdfPages.Page(1, text));
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.embed(any(), anyList())).thenAnswer(call -> {
            List<String> units = call.getArgument(1);
            return units.stream().map(unit -> unit.startsWith("Biblioteca") ? new double[]{0, 1} : new double[]{1, 0}).toList();
        });
        TextChunker chunker = new TextChunker();
        var semantic = chunker.semantic(cfg, pages, ollama);
        assertEquals(2, semantic.size());
        assertEquals(internship.strip(), semantic.getFirst().text());
        assertEquals(library, semantic.getLast().text());
        assertEquals(1, chunker.recursive(cfg, pages).size());
        verify(ollama, atLeastOnce()).embed(eq(cfg), anyList());
        assertMaxAndExactCoverage(pages, semantic, cfg.chunk_max_chars);
    }

    @Test void semanticCoherentUnitsCanContinueAcrossPagesWithoutFalsePageAttribution() throws Exception {
        ProjectConfig cfg = config(500, 80, 0);
        var pages = List.of(new PdfPages.Page(4, "O termo precisa identificar a unidade concedente e o estudante."),
            new PdfPages.Page(5, "O termo registra a supervisão e a jornada da atividade curricular."));
        var chunks = new TextChunker().semantic(cfg, pages, coherentOllama());
        assertEquals(1, chunks.size());
        assertEquals(List.of(4, 5), chunks.getFirst().spans().stream().map(SourceSpan::pagina).toList());
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void semanticMinimumPreventsFragmentingEveryShortTopicTransition() throws Exception {
        ProjectConfig cfg = config(600, 100, 0);
        String text = "Estágio exige supervisão. Biblioteca oferece consultas. ".repeat(7);
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.embed(any(), anyList())).thenAnswer(call -> {
            List<String> units = call.getArgument(1);
            return units.stream().map(unit -> unit.startsWith("Estágio") ? new double[]{1, 0} : new double[]{0, 1}).toList();
        });
        var pages = List.of(new PdfPages.Page(1, text));
        var chunks = new TextChunker().semantic(cfg, pages, ollama);
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.size() < 7, "Não deve haver um chunk para cada frase curta");
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() >= cfg.chunk_min_chars));
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void semanticHardMaximumSplitsLongSentencesAtWordsAndKeepsAllSourceContent() throws Exception {
        ProjectConfig cfg = config(210, 70, 0); cfg.semantic_unit_chars = 65;
        var pages = List.of(new PdfPages.Page(6, numberedWords(190)));
        var chunks = new TextChunker().semantic(cfg, pages, coherentOllama());
        assertTrue(chunks.size() > 4);
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void semanticLongUnbrokenUnitStillHonorsMaximumAndFullCoverage() throws Exception {
        ProjectConfig cfg = config(150, 50, 0); cfg.semantic_unit_chars = 60;
        var pages = List.of(new PdfPages.Page(1, "ç".repeat(751)));
        assertMaxAndExactCoverage(pages, new TextChunker().semantic(cfg, pages, coherentOllama()), cfg.chunk_max_chars);
    }

    @Test void semanticPreservesListRulesAsEmbeddingUnitsWhenTheyFitMaximum() throws Exception {
        ProjectConfig cfg = config(500, 80, 0); cfg.semantic_unit_chars = 50;
        String rule = "1. O estágio depende da matrícula ativa. Não poderá começar antes da aprovação. Se houver pendência, deverá aguardar a regularização.";
        String next = "2. A coordenação examina o termo recebido. A aprovação será registrada no sistema.";
        OllamaClient ollama = coherentOllama();
        var pages = List.of(new PdfPages.Page(1, rule + "\n" + next));
        var chunks = new TextChunker().semantic(cfg, pages, ollama);
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.text().contains(rule)));
        verify(ollama).embed(eq(cfg), argThat(units -> units.contains(rule) && units.contains(next)));
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void semanticCachesUnitsAcrossRunsButNeverReusesDifferentModelOrServerVectors() throws Exception {
        ProjectConfig cfg = config(500, 50, 0);
        var pages = List.of(new PdfPages.Page(1, "O estudante solicita matrícula. A coordenação confirma a matrícula."));
        OllamaClient ollama = coherentOllama(); TextChunker chunker = new TextChunker();
        assertEquals(chunker.semantic(cfg, pages, ollama), chunker.semantic(cfg, pages, ollama));
        verify(ollama, times(1)).embed(eq(cfg), anyList());
        cfg.modelo_embeddings = "outro-modelo-local";
        chunker.semantic(cfg, pages, ollama);
        verify(ollama, times(2)).embed(eq(cfg), anyList());
        cfg.ollama_url = "http://localhost:11434";
        chunker.semantic(cfg, pages, ollama);
        verify(ollama, times(3)).embed(eq(cfg), anyList());
    }

    @Test void repeatedSourceUnitsReuseEmbeddingsWithoutDeletingRepeatedSourceOccurrences() throws Exception {
        ProjectConfig cfg = config(500, 50, 0);
        String sentence = "O estudante precisa manter a matrícula regular.";
        var pages = List.of(new PdfPages.Page(1, sentence + " " + sentence + " " + sentence));
        OllamaClient ollama = coherentOllama();
        var chunks = new TextChunker().semantic(cfg, pages, ollama);
        verify(ollama).embed(eq(cfg), eq(List.of(sentence)));
        assertEquals(1, chunks.size());
        assertEquals(pages.getFirst().text(), chunks.getFirst().text());
        assertMaxAndExactCoverage(pages, chunks, cfg.chunk_max_chars);
    }

    @Test void zeroCacheCapacityDoesNotRetainEmbeddingsBetweenRuns() throws Exception {
        ProjectConfig cfg = config(500, 50, 0); cfg.semantic_embedding_cache_size = 0;
        var pages = List.of(new PdfPages.Page(1, "O estudante solicita matrícula. A coordenação confirma o vínculo."));
        OllamaClient ollama = coherentOllama(); TextChunker chunker = new TextChunker();
        chunker.semantic(cfg, pages, ollama); chunker.semantic(cfg, pages, ollama);
        verify(ollama, times(2)).embed(eq(cfg), anyList());
    }

    @Test void semanticRejectsEmbeddingDimensionMismatchInsteadOfInventingTransitions() throws Exception {
        ProjectConfig cfg = config(500, 50, 0); OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.embed(any(), anyList())).thenReturn(List.of(new double[]{1, 0}, new double[]{1, 0, 0}));
        var pages = List.of(new PdfPages.Page(1, "O estudante solicita matrícula. A coordenação confirma o vínculo."));
        assertThrows(IOException.class, () -> new TextChunker().semantic(cfg, pages, ollama));
    }

    @Test void semanticRejectsZeroNonFiniteAndEmptyVectors() throws Exception {
        ProjectConfig cfg = config(500, 50, 0);
        var pages = List.of(new PdfPages.Page(1, "O estudante solicita matrícula. A coordenação confirma o vínculo."));
        for (double[] bad : List.of(new double[0], new double[]{0, 0}, new double[]{Double.NaN, 1}, new double[]{Double.POSITIVE_INFINITY})) {
            OllamaClient ollama = mock(OllamaClient.class);
            when(ollama.embed(any(), anyList())).thenReturn(List.of(bad, new double[]{1, 0}));
            assertThrows(IOException.class, () -> new TextChunker().semantic(cfg, pages, ollama));
        }
    }

    @Test void semanticRejectsWrongEmbeddingBatchCountAndPropagatesConnectionFailures() throws Exception {
        ProjectConfig cfg = config(500, 50, 0);
        var pages = List.of(new PdfPages.Page(1, "O estudante solicita matrícula. A coordenação confirma o vínculo."));
        OllamaClient missing = mock(OllamaClient.class);
        when(missing.embed(any(), anyList())).thenReturn(List.of(new double[]{1, 0}));
        assertThrows(IOException.class, () -> new TextChunker().semantic(cfg, pages, missing));
        OllamaClient failed = mock(OllamaClient.class);
        when(failed.embed(any(), anyList())).thenThrow(new IOException("Ollama indisponível"));
        IOException error = assertThrows(IOException.class, () -> new TextChunker().semantic(cfg, pages, failed));
        assertEquals("Ollama indisponível", error.getMessage());
    }

    @Test void emptyPagesAndEmptyTextProduceNoFabricatedChunks() throws Exception {
        TextChunker chunker = new TextChunker(); ProjectConfig cfg = config(500, 50, 0);
        OllamaClient ollama = mock(OllamaClient.class);
        assertTrue(chunker.recursive((String) null).isEmpty());
        assertTrue(chunker.semantic(cfg, " ", ollama).isEmpty());
        assertTrue(chunker.recursive(cfg, List.of()).isEmpty());
        assertTrue(chunker.semantic(cfg, List.of(new PdfPages.Page(1, "\n\t ")), ollama).isEmpty());
        verifyNoInteractions(ollama);
    }

    @Test void sourceSpanEnforcesNonemptyHalfOpenOffsetsAndImmutableSegmentOrigin() {
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(0, 0, 3, "abc"));
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(1, -1, 2, "abc"));
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(1, 0, 4, "abc"));
        assertThrows(IllegalArgumentException.class, () -> new SourceSpan(1, 0, 0, ""));
        List<SourceSpan> spans = new ArrayList<>(List.of(new SourceSpan(1, 0, 3, "abc")));
        var chunk = new TextChunker.Segment("abc", spans); spans.clear();
        assertEquals(1, chunk.spans().size());
        assertThrows(UnsupportedOperationException.class, () -> chunk.spans().clear());
    }

    @Test void baselineRecursiveKeepsOriginalContractAndExactOffsetsEvenWithRepeatedText() {
        ProjectConfig cfg = config(200, 50, 0);
        var pages = List.of(new PdfPages.Page(1, "valor ".repeat(1000)));
        TextChunker chunker = new TextChunker();
        var chunks = chunker.recursiveBaseline(cfg, pages);
        assertEquals(new BaselineTextChunker().recursive(pages.getFirst().text()), chunks.stream().map(TextChunker.Segment::text).toList());
        assertMaxAndExactCoverage(pages, chunks, TextChunker.RECURSIVE_MAX);
        for (int i = 1; i < chunks.size(); i++)
            assertTrue(chunks.get(i).spans().getFirst().inicio() < chunks.get(i - 1).spans().getLast().fim(), "Baseline deve preservar overlap original");
    }

    @Test void baselineShortSemanticPageKeepsOriginalNoEmbeddingBehavior() throws Exception {
        ProjectConfig cfg = config(500, 50, 0); OllamaClient ollama = mock(OllamaClient.class);
        var pages = List.of(new PdfPages.Page(3, "O estudante solicita matrícula. A coordenação confirma o vínculo."));
        var chunks = new TextChunker().semanticBaseline(cfg, pages, ollama);
        assertEquals(1, chunks.size()); assertEquals(pages.getFirst().text(), chunks.getFirst().text());
        assertMaxAndExactCoverage(pages, chunks, TextChunker.RECURSIVE_MAX);
        verifyNoInteractions(ollama);
    }

    @Test void reportMeasuresCoverageOverlapSizeDistributionAndActualProcessingTimes() {
        var pages = List.of(new PdfPages.Page(1, "abc def ghi"));
        var recursive = List.of(segment(1, 0, "abc def"), segment(1, 4, "def ghi"));
        var semantic = List.of(segment(1, 0, "abc def ghi"));
        var report = ChunkingReport.compare(pages, recursive, semantic, 17, 29);
        assertEquals(2, report.recursive().chunks()); assertEquals(7, report.recursive().minimum());
        assertEquals(7, report.recursive().maximum()); assertEquals(7d, report.recursive().average());
        assertEquals(2, report.recursive().distribution().get("1-279"));
        assertEquals(100d, report.recursive().originalCoveragePercent());
        assertEquals(100d, report.recursive().rawCoveragePercent());
        assertEquals(100d * 3 / 14, report.recursive().repeatedSourcePercent(), 1e-8);
        assertFalse(report.recursive().potentialDuplicates().isEmpty());
        assertEquals(0, report.identicalSegmentsPercent());
        assertEquals(100d * 2 / 3, report.textualOverlapPercent(), 1e-8);
        assertEquals(17, report.recursive().processingMillis()); assertEquals(29, report.semantic().processingMillis());
    }

    @Test void reportDistinguishesTextCoverageFromBoundaryWhitespaceAndCountsCoveredSections() {
        String text = "# MATRÍCULA\nabc\n\n# ESTÁGIO\ndef";
        var page = new PdfPages.Page(2, text);
        var chunk = segment(2, 0, "# MATRÍCULA\nabc");
        var stats = ChunkingReport.stats(List.of(page), List.of(chunk), 0);
        assertEquals(1, stats.sections()); assertEquals(1, stats.pages());
        assertTrue(stats.originalCoveragePercent() < 100);
        var chunks = new TextChunker().recursive(config(200, 50, 0), List.of(page));
        assertEquals(100, ChunkingReport.stats(List.of(page), chunks, 0).originalCoveragePercent());
    }

    @Test void reportIdenticalAndDuplicateMetricsReflectActualInputsAndEmptyRunsRemainFinite() {
        var pages = List.of(new PdfPages.Page(1, "abc abc"));
        var chunks = List.of(segment(1, 0, "abc"), segment(1, 4, "abc"));
        var same = ChunkingReport.compare(pages, chunks, chunks, 0, 0);
        assertEquals(100, same.identicalSegmentsPercent()); assertEquals(100, same.textualOverlapPercent());
        assertFalse(same.recursive().potentialDuplicates().isEmpty());
        var empty = ChunkingReport.compare(List.of(), List.of(), List.of(), 0, 0);
        assertEquals(0, empty.recursive().minimum()); assertEquals(0, empty.recursive().maximum());
        assertTrue(Double.isFinite(empty.identicalSegmentsPercent()));
        assertTrue(Double.isFinite(empty.recursive().originalCoveragePercent()));
    }

    @Test void reportRejectsNonexistentPagesOutOfBoundsAndFabricatedSourceText() {
        var pages = List.of(new PdfPages.Page(1, "abc def"));
        assertThrows(IllegalArgumentException.class, () -> ChunkingReport.stats(pages, List.of(segment(3, 0, "abc")), 0));
        assertThrows(IllegalArgumentException.class, () -> ChunkingReport.stats(pages, List.of(segment(1, 6, "abc")), 0));
        assertThrows(IllegalArgumentException.class, () -> ChunkingReport.stats(pages, List.of(segment(1, 0, "xyz")), 0));
    }

    @Test void reportExportsRealMeasurementsToUtf8JsonCsvAndMarkdown() throws Exception {
        var pages = List.of(new PdfPages.Page(1, "# MATRÍCULA\nA matrícula exige vínculo acadêmico."));
        var chunks = new TextChunker().recursive(config(500, 50, 0), pages);
        var report = ChunkingReport.compare(pages, chunks, chunks, 11, 13);
        ChunkingReport.export(temporary, "comparacao", report);
        var json = new ObjectMapper().readTree(Files.readString(temporary.resolve("comparacao.json"), StandardCharsets.UTF_8));
        assertEquals(1, json.path("recursive").path("chunks").asInt());
        assertEquals(100, json.path("identicalSegmentsPercent").asDouble());
        String csv = Files.readString(temporary.resolve("comparacao.csv"), StandardCharsets.UTF_8);
        String markdown = Files.readString(temporary.resolve("comparacao.md"), StandardCharsets.UTF_8);
        assertTrue(csv.contains("recursivo,1,")); assertTrue(csv.contains("semantico,1,"));
        assertTrue(markdown.contains("Validação do chunking")); assertTrue(markdown.contains("Segmentos idênticos"));
        assertTrue(markdown.contains("11")); assertTrue(markdown.contains("13"));
    }

    private static ProjectConfig config(int maximum, int minimum, int overlap) {
        ProjectConfig cfg = new ProjectConfig(); cfg.chunk_max_chars = maximum;
        cfg.chunk_min_chars = minimum; cfg.chunk_overlap_chars = overlap;
        cfg.semantic_unit_chars = Math.min(220, maximum); return cfg;
    }
    private static String numberedWords(int count) {
        StringJoiner words = new StringJoiner(" ");
        for (int i = 0; i < count; i++) words.add("termo" + String.format(Locale.ROOT, "%04d", i));
        return words.toString();
    }
    private static OllamaClient coherentOllama() throws IOException {
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.embed(any(), anyList())).thenAnswer(call -> {
            List<String> units = call.getArgument(1);
            return units.stream().map(unit -> new double[]{1, 0}).toList();
        });
        return ollama;
    }
    private static TextChunker.Segment segment(int page, int start, String text) {
        return new TextChunker.Segment(text, List.of(new SourceSpan(page, start, start + text.length(), text)));
    }
    private static void assertMaxAndExactCoverage(List<PdfPages.Page> pages, List<TextChunker.Segment> chunks, int maximum) {
        Map<Integer, PdfPages.Page> source = new HashMap<>(); Map<Integer, boolean[]> covered = new HashMap<>();
        for (var page : pages) { source.put(page.number(), page); covered.put(page.number(), new boolean[page.text().length()]); }
        for (var chunk : chunks) {
            assertTrue(chunk.text().length() <= maximum, "Chunk excede máximo: " + chunk.text().length());
            assertFalse(chunk.text().isBlank()); StringJoiner reconstructed = new StringJoiner("\n\n");
            for (var span : chunk.spans()) {
                assertTrue(source.containsKey(span.pagina()));
                assertEquals(source.get(span.pagina()).text().substring(span.inicio(), span.fim()), span.texto());
                reconstructed.add(span.texto()); Arrays.fill(covered.get(span.pagina()), span.inicio(), span.fim(), true);
            }
            assertEquals(chunk.text(), reconstructed.toString(), "Texto e metadados de origem divergentes");
        }
        for (var page : pages) for (int i = 0; i < page.text().length(); i++)
            if (!Character.isWhitespace(page.text().charAt(i)))
                assertTrue(covered.get(page.number())[i], "Conteúdo perdido: página " + page.number() + ", posição " + i);
    }
}
