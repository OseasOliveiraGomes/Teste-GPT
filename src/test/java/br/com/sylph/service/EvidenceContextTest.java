package br.com.sylph.service;

import br.com.sylph.model.KnowledgeChunk;
import br.com.sylph.model.RankedChunk;
import br.com.sylph.model.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceContextTest {
    private static RankedChunk ranked(String id, String docId, String version, SourceSpan... spans) {
        String text = String.join(" ", java.util.Arrays.stream(spans).map(SourceSpan::texto).toList());
        return new RankedChunk(new KnowledgeChunk(id, "Manual.pdf", spans[0].pagina(), "recursivo", text,
            new double[]{1}, List.of(spans), docId, "hash", "nomic-embed-text", version), 1, 1, 1);
    }

    private static SourceSpan span(int page, int start, String text) {
        return new SourceSpan(page, start, start + text.length(), text);
    }

    private static void assertEverySpanPreserved(List<RankedChunk> chunks, EvidenceContext.Prepared prepared) {
        for (RankedChunk ranked : chunks) for (SourceSpan span : ranked.chunk().spans()) {
            RagService.Source source = prepared.sources().stream()
                .filter(s -> s.documentoId().equals(ranked.chunk().docId()) && s.pagina() == span.pagina())
                .findFirst().orElseThrow();
            assertTrue(source.trecho().contains(span.texto()), "A complete source interval must survive compaction");
            assertTrue(source.chunkIds().contains(ranked.chunk().id()));
            assertTrue(prepared.text().contains(span.texto()));
        }
    }

    @Test void mergesOnlyExactOverlappingCharactersAndRetainsRetrievalOrderAndIds() {
        List<RankedChunk> chunks = List.of(
            ranked("late", "doc", "2", span(7, 3, "defghi")),
            ranked("early", "doc", "2", span(7, 0, "abcdef")),
            ranked("inside", "doc", "2", span(7, 2, "cde")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals("abcdefghi", result.sources().getFirst().trecho());
        assertEquals(List.of("late", "early", "inside"), result.sources().getFirst().chunkIds());
        assertEquals(15, result.originalCharacters());
        assertTrue(result.text().contains("late (1), early (2), inside (3)"));
        assertEverySpanPreserved(chunks, result);
        assertThrows(UnsupportedOperationException.class, () -> result.sources().clear());
    }

    @Test void joinsAdjacentIntervalsButKeepsUnknownGapsVisiblySeparate() {
        List<RankedChunk> chunks = List.of(
            ranked("a", "doc", "2", span(2, 0, "abc")),
            ranked("b", "doc", "2", span(2, 3, "def")),
            ranked("gap", "doc", "2", span(2, 8, "xyz")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals("abcdef\n\n[...]\n\nxyz", result.sources().getFirst().trecho());
        assertFalse(result.sources().getFirst().trecho().contains("abcdefxyz"));
        assertEverySpanPreserved(chunks, result);
    }

    @Test void inconsistentOffsetsPreserveBothRulesAndConflictDetection() {
        List<RankedChunk> chunks = List.of(
            ranked("five", "doc", "2", span(4, 0, "O prazo é de 5 dias.")),
            ranked("nine", "doc", "2", span(4, 0, "O prazo é de 9 dias.")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals("O prazo é de 5 dias.\n\n[...]\n\nO prazo é de 9 dias.", result.sources().getFirst().trecho());
        assertEquals(1, EvidenceConflicts.detect(result.sources()).size());
        assertEverySpanPreserved(chunks, result);
    }

    @Test void anInconsistentIntervalDoesNotPreventOtherCompatibleIntervalsFromJoining() {
        List<RankedChunk> chunks = List.of(
            ranked("a", "doc", "2", span(1, 0, "abcdef")),
            ranked("conflict", "doc", "2", span(1, 2, "XXghi")),
            ranked("c", "doc", "2", span(1, 3, "defgh")));
        var result = EvidenceContext.prepare(chunks);
        assertTrue(result.sources().getFirst().trecho().contains("abcdefgh"));
        assertEverySpanPreserved(chunks, result);
    }

    @Test void legacyOffsetsAreNeverUsedToInferOverlapAndOnlyIdenticalTextsAreDeduplicated() {
        List<RankedChunk> chunks = List.of(
            ranked("a", "doc", "legacy", span(3, 0, "Prazo de cinco")),
            ranked("b", "doc", "legacy", span(3, 0, "cinco dias úteis.")),
            ranked("same", "doc", "legacy", span(3, 0, "Prazo de cinco")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals("Prazo de cinco\n\n[...]\n\ncinco dias úteis.", result.sources().getFirst().trecho());
        assertEquals(List.of("a", "b", "same"), result.sources().getFirst().chunkIds());
        assertEverySpanPreserved(chunks, result);
    }

    @Test void sourceIdsStayOnTheirOwnPagesIncludingCrossPageChunks() {
        List<RankedChunk> chunks = List.of(
            ranked("cross", "doc", "2", span(1, 0, "primeira"), span(2, 0, "segunda")),
            ranked("only-two", "doc", "2", span(2, 7, " página")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals(2, result.sources().size());
        assertEquals(List.of("cross"), result.sources().get(0).chunkIds());
        assertEquals(List.of("cross", "only-two"), result.sources().get(1).chunkIds());
        assertEquals("segunda página", result.sources().get(1).trecho());
        assertEquals("[Fonte: Manual.pdf | Página: 2]",
            StableCitations.resolve("[Trecho: only-two | Página: 2]", result.sources()));
        assertEquals("[Trecho: only-two | Página: 1]",
            StableCitations.resolve("[Trecho: only-two | Página: 1]", result.sources()));
        assertEverySpanPreserved(chunks, result);
    }

    @Test void sameDocumentNamesAndPageNumbersCannotMergeDifferentDocumentIds() {
        List<RankedChunk> chunks = List.of(
            ranked("a", "doc-a", "2", span(1, 0, "primeiro documento")),
            ranked("b", "doc-b", "2", span(1, 0, "outro documento")));
        var result = EvidenceContext.prepare(chunks);
        assertEquals(2, result.sources().size());
        assertEquals(List.of("a"), result.sources().get(0).chunkIds());
        assertEquals(List.of("b"), result.sources().get(1).chunkIds());
        assertEverySpanPreserved(chunks, result);
    }

    @Test void overlappingIntervalsRetainConditionsAroundAnAttributionQuotation() {
        String complete = "O supervisor deve assinar o relatório se estiver cadastrado.";
        int condition = complete.indexOf(" se ");
        int overlap = complete.indexOf("assinar");
        List<RankedChunk> chunks = List.of(
            ranked("action", "doc", "2", span(5, 0, complete.substring(0, condition))),
            ranked("condition", "doc", "2", span(5, overlap, complete.substring(overlap))));
        var result = EvidenceContext.prepare(chunks);
        var witnesses = AttributionEvidence.contextualWitnesses(result.sources().getFirst(),
            "O supervisor deve assinar o relatório");
        assertEquals(complete, result.sources().getFirst().trecho());
        assertFalse(AttributionEvidence.unsupported("O supervisor deve assinar o relatório.", witnesses).isEmpty());
        assertTrue(AttributionEvidence.unsupported(complete, witnesses).isEmpty());
        assertEverySpanPreserved(chunks, result);
    }

    @Test void overlappingIntervalsCannotCropNegationOrChangeTheResponsibleActor() {
        String complete = "O aluno não deve assinar pelo supervisor.";
        int overlap = complete.indexOf("deve");
        List<RankedChunk> chunks = List.of(
            ranked("negative", "doc", "2", span(6, 0, "O aluno não deve assinar")),
            ranked("tail", "doc", "2", span(6, overlap, complete.substring(overlap))));
        var result = EvidenceContext.prepare(chunks);
        var witnesses = AttributionEvidence.contextualWitnesses(result.sources().getFirst(),
            "deve assinar pelo supervisor");
        assertEquals(complete, result.sources().getFirst().trecho());
        assertFalse(AttributionEvidence.unsupported("O aluno deve assinar pelo supervisor.", witnesses).isEmpty());
        assertFalse(AttributionEvidence.unsupported("O tutor não deve assinar pelo supervisor.", witnesses).isEmpty());
        assertTrue(AttributionEvidence.unsupported(complete, witnesses).isEmpty());
        assertEverySpanPreserved(chunks, result);
    }

    @Test void disconnectedIntervalsCannotInventAnActorActionLinkAcrossMissingText() {
        List<RankedChunk> chunks = List.of(
            ranked("actor", "doc", "2", span(6, 0, "O aluno")),
            ranked("action", "doc", "2", span(6, 100, "deve assinar o relatório.")));
        var result = EvidenceContext.prepare(chunks);
        String source = result.sources().getFirst().trecho();
        assertEquals("O aluno\n\n[...]\n\ndeve assinar o relatório.", source);
        assertFalse(AnswerEvidence.normalize(source).contains("O aluno deve assinar o relatório"));
        var witnesses = AttributionEvidence.contextualWitnesses(result.sources().getFirst(), "deve assinar o relatório");
        assertFalse(AttributionEvidence.unsupported("O aluno deve assinar o relatório.", witnesses).isEmpty());
        assertTrue(result.text().contains("não presuma continuidade"));
        assertEverySpanPreserved(chunks, result);
    }

}
