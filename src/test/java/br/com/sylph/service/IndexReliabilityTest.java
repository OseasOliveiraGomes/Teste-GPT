package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.config.SettingsService;
import br.com.sylph.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deterministic indexing reliability checks; never reads or writes the real dados directory. */
class IndexReliabilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEADER = "id,documento,pagina,metodo,texto,embedding\n";
    @TempDir Path temporary;

    @Test void readsLegacyCsvWithAccentsQuotesCommaAndEmbeddedNewlineWithoutChangingIt() throws Exception {
        CsvKnowledgeStore store = store();
        String original = HEADER + "\"R-1\",\"Manual.pdf\",1,\"recursivo\",\"Condição, prazo e "+"\"\"exceção\"\"\nsegunda linha\",\"[1.0,2.0]\"\n";
        Path file = legacy(store, original);
        List<KnowledgeChunk> first = store.read("recursivo");
        assertEquals("Condição, prazo e \"exceção\"\nsegunda linha", first.getFirst().texto());
        assertEquals(1, first.getFirst().spans().getFirst().pagina());
        assertEquals("legacy", first.getFirst().indexVersion());
        assertSame(first, store.read("recursivo"), "A second read should reuse parsed immutable chunks");
        double[] exposed = first.getFirst().embedding(); exposed[0] = 999;
        assertEquals(1.0, store.read("recursivo").getFirst().embedding()[0]);
        assertThrows(UnsupportedOperationException.class, () -> first.add(first.getFirst()));
        assertEquals(original, Files.readString(file));
    }

    static Stream<String> malformedCsv() {
        return Stream.of(
            "wrong,header\n",
            HEADER + "id,Manual.pdf,x,recursivo,texto,\"[1]\"\n",
            HEADER + "id,Manual.pdf,0,recursivo,texto,\"[1]\"\n",
            HEADER + "id,Manual.pdf,1,semantico,texto,\"[1]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,texto,\"[]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,texto,\"[0,0]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,texto,\"[1]\"\nother,Manual.pdf,2,recursivo,outro,\"[1,2]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,texto,\"[1]\"\nid,Manual.pdf,2,recursivo,outro,\"[1]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,\"aspas abertas,\"[1]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,\"texto\"lixo,\"[1]\"\n",
            HEADER + "id,Manual.pdf,1,recursivo,te\"xto,\"[1]\"\n");
    }
    @ParameterizedTest @MethodSource("malformedCsv")
    void rejectsMalformedCsvAndIncompatibleVectors(String contents) throws Exception {
        CsvKnowledgeStore store = store(); legacy(store, contents);
        assertThrows(IOException.class, () -> store.read("recursivo"));
    }

    @Test void csvRecordsRetainQuotedCarriageReturnsAndRejectTrailingCharacters() throws Exception {
        assertEquals(List.of(List.of("a\r\nb", "c\"d")), CsvKnowledgeStore.records(new StringReader("\"a\r\nb\",\"c\"\"d\"\r\n")));
        assertThrows(IOException.class, () -> CsvKnowledgeStore.records(new StringReader("\"a\"z,b")));
    }

    @Test void cacheDetectsReplacementsEvenWhenSizeAndModificationTimeMatch() throws Exception {
        CsvKnowledgeStore store = store();
        Path file = legacy(store, HEADER + "id,Manual.pdf,1,recursivo,antes,\"[1]\"\n");
        FileTime timestamp = Files.getLastModifiedTime(file);
        List<KnowledgeChunk> before = store.read("recursivo");
        String signature = store.revision("recursivo");
        long size = Files.size(file);
        Files.writeString(file, HEADER + "id,Manual.pdf,1,recursivo,depos,\"[1]\"\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, timestamp);
        assertEquals(size, Files.size(file));
        assertNotEquals(signature, store.revision("recursivo"));
        assertEquals("depos", store.read("recursivo").getFirst().texto());
        assertNotSame(before, store.read("recursivo"));
    }

    @Test void aCommittedGenerationPublishesBothMethodsAndRetainsOriginalBaseline() throws Exception {
        CsvKnowledgeStore store = store();
        Path original = legacy(store, HEADER + "legacy,Manual.pdf,1,recursivo,origem,\"[1]\"\n");
        String originalHash = CsvKnowledgeStore.sha256(original);
        IndexedDocument doc = document("documento.txt", "Cadastro confirmado.");
        IndexManifest manifest = commit(store, doc, "Cadastro confirmado.", new double[]{1, 2});
        assertEquals(manifest.generation(), store.manifest().generation());
        assertEquals(2, manifest.dimension());
        for (String method : List.of("recursivo", "semantico")) {
            KnowledgeChunk loaded = store.read(method).getFirst();
            assertEquals(doc.docId(), loaded.docId());
            assertEquals(doc.sha256(), loaded.hash());
            assertEquals("2", loaded.indexVersion());
            assertTrue(store.file(method).startsWith(store.dataDirectory().resolve("indices")));
        }
        assertEquals("origem", store.readBaseline("recursivo").getFirst().texto());
        assertEquals(originalHash, CsvKnowledgeStore.sha256(original));
        assertThrows(IOException.class, () -> store.write("recursivo", store.read("recursivo")), "Individual writes cannot split a committed pair");
    }

    @Test void invalidCommitCannotReplacePreviouslyCommittedGeneration() throws Exception {
        CsvKnowledgeStore store = store(); IndexedDocument doc = document("principal.txt", "Regra original.");
        commit(store, doc, "Regra original.", new double[]{1, 2});
        String oldPointer = Files.readString(store.manifestFile());
        List<KnowledgeChunk> oldRecursive = store.read("recursivo");
        Map<String, List<KnowledgeChunk>> invalid = Map.of(
            "recursivo", List.of(chunk(doc, "recursivo", "Novo trecho.", new double[]{1, 2})),
            "semantico", List.of(chunk(doc, "semantico", "Novo trecho.", new double[]{1, 2, 3})));
        assertThrows(IOException.class, () -> store.commit("nomic-embed-text", fingerprint(), List.of(doc), invalid));
        assertEquals(oldPointer, Files.readString(store.manifestFile()));
        assertEquals(oldRecursive, store.read("recursivo"));
        assertEquals("Regra original.", store.read("semantico").getFirst().texto());
    }

    @Test void changedGenerationInvalidatesBothCachesAndPreservesHistoricalFiles() throws Exception {
        CsvKnowledgeStore store = store(); IndexedDocument doc = document("regras.md", "Regra antiga.");
        IndexManifest first = commit(store, doc, "Regra antiga.", new double[]{1, 2});
        store.read("recursivo"); store.read("semantico");
        String revision = store.revision("recursivo");
        IndexManifest second = commit(store, doc, "Regra atualizada.", new double[]{2, 1});
        assertNotEquals(first.generation(), second.generation());
        assertNotEquals(revision, store.revision("recursivo"));
        assertEquals("Regra atualizada.", store.read("recursivo").getFirst().texto());
        assertEquals("Regra atualizada.", store.read("semantico").getFirst().texto());
        assertTrue(Files.isRegularFile(store.dataDirectory().resolve("indices").resolve(first.generation()).resolve("base_recursivo.csv")));
    }

    @Test void checksumDetectsCorruptionAfterCacheWasWarmed() throws Exception {
        CsvKnowledgeStore store = store(); IndexedDocument doc = document("regra.txt", "Texto oficial.");
        commit(store, doc, "Texto oficial.", new double[]{1}); store.read("recursivo");
        Files.writeString(store.file("recursivo"), "dados corrompidos", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> store.read("recursivo"));
        assertEquals("Texto oficial.", store.read("semantico").getFirst().texto());
    }

    @Test void metadataChangesCannotReuseAValidatedCacheEntry() throws Exception {
        CsvKnowledgeStore store = store(); IndexedDocument doc = document("fonte.txt", "Trecho exato.");
        commit(store, doc, "Trecho exato.", new double[]{1, 2}); store.read("recursivo");
        ObjectNode manifest = (ObjectNode) MAPPER.readTree(store.manifestFile().toFile());
        manifest.put("dimension", 3); MAPPER.writeValue(store.manifestFile().toFile(), manifest);
        assertThrows(IOException.class, () -> store.read("recursivo"));
    }

    @Test void rejectsManipulatedManifestPathsAndChangedEmbeddingModel() throws Exception {
        CsvKnowledgeStore store = store(); IndexedDocument doc = document("fonte.txt", "Prazo oficial.");
        commit(store, doc, "Prazo oficial.", new double[]{1});
        ProjectConfig cfg = new ProjectConfig(); cfg.modelo_embeddings = "outro-modelo";
        assertThrows(IOException.class, () -> store.assertCompatible(cfg));
        ObjectNode manifest = (ObjectNode) MAPPER.readTree(store.manifestFile().toFile());
        manifest.put("generation", "../../fora"); MAPPER.writeValue(store.manifestFile().toFile(), manifest);
        assertThrows(IOException.class, store::manifest);
        assertThrows(IOException.class, () -> store.read("recursivo"));
    }

    @Test void baselineCanLoadAndInvalidateFromTheSeparateReferenceWhenLegacyCsvIsAbsent() throws Exception {
        CsvKnowledgeStore store = store();
        CsvKnowledgeStore reference = new CsvKnowledgeStore(MAPPER, store.dataDirectory().resolve("referencia"));
        IndexedDocument doc = document("referencia.txt", "Referência preservada.");
        commit(reference, doc, "Referência preservada.", new double[]{1});
        assertEquals("Referência preservada.", store.readBaseline("recursivo").getFirst().texto());
        String first = store.baselineRevision("recursivo");
        commit(reference, doc, "Referência atualizada.", new double[]{1});
        assertNotEquals(first, store.baselineRevision("recursivo"));
        assertEquals("Referência atualizada.", store.readBaseline("recursivo").getFirst().texto());
    }

    @Test void frozenBaselinePreparationKeepsExistingLegacyCsvAndPhysicalPages() throws Exception {
        Fixture fixture = fixture();
        Path source = Files.writeString(fixture.store.dataDirectory().resolve("Manual Estagio Obrigatorio UNOPAR 2026-2.pdf"),
            "Arquivo de teste; extração PDF simulada.", StandardCharsets.UTF_8);
        String originalHash = CsvKnowledgeStore.sha256(source);
        Path legacy = legacy(fixture.store, HEADER + "legado,Manual.pdf,1,recursivo,Referência anterior,\"[1]\"\n");
        String legacyHash = CsvKnowledgeStore.sha256(legacy);
        PdfPages pdf = mock(PdfPages.class);
        when(pdf.pageCount(source.toRealPath())).thenReturn(3);
        when(pdf.extract(source.toRealPath(), fixture.config.max_documento_caracteres)).thenReturn(List.of(new PdfPages.Page(1, "Regra de matrícula."), new PdfPages.Page(3, "Procedimento do estágio.")));
        SettingsService settings = mock(SettingsService.class); when(settings.load()).thenReturn(fixture.config);
        BasePreparation preparation = new BasePreparation(pdf, fixture.chunker, fixture.ollama, fixture.store, settings);
        preparation.prepareBaseline();
        assertEquals(originalHash, CsvKnowledgeStore.sha256(source));
        assertEquals(legacyHash, CsvKnowledgeStore.sha256(legacy));
        assertEquals("Referência anterior", fixture.store.readBaseline("recursivo").getFirst().texto());
        CsvKnowledgeStore reference = new CsvKnowledgeStore(MAPPER, fixture.store.dataDirectory().resolve("referencia"));
        assertEquals(3, reference.listDocuments().getFirst().pages());
        for (String method : List.of("recursivo", "semantico")) {
            assertEquals(List.of(1, 3), reference.read(method).stream().map(KnowledgeChunk::pagina).toList());
            assertTrue(reference.read(method).stream().allMatch(c -> c.spans().size() == 1));
        }
    }

    @Test void multiDocumentUpdateSkipsUnchangedEmbeddingsAndRemovalKeepsSources() throws Exception {
        Fixture fixture = fixture();
        Path first = Files.writeString(fixture.store.dataDirectory().resolve("primeiro.txt"), "Cadastro da empresa leva cinco dias.", StandardCharsets.UTF_8);
        Path second = Files.writeString(fixture.store.dataDirectory().resolve("segundo.md"), "# Supervisão\nO supervisor de campo orienta o estágio.", StandardCharsets.UTF_8);
        fixture.preparation.prepare();
        assertEquals(2, fixture.preparation.listDocuments().size());
        List<String> stableIds = fixture.store.read("recursivo").stream().filter(c -> c.documento().equals("primeiro.txt")).map(KnowledgeChunk::id).toList();
        clearInvocations(fixture.ollama);
        String generation = fixture.store.manifest().generation();
        fixture.preparation.prepare();
        verifyNoInteractions(fixture.ollama);
        assertEquals(generation, fixture.store.manifest().generation());
        Files.writeString(second, "# Supervisão\nO orientador acadêmico avalia o relatório final.", StandardCharsets.UTF_8);
        fixture.preparation.prepare();
        verify(fixture.ollama, atLeastOnce()).embed(any(), anyList());
        assertEquals(stableIds, fixture.store.read("recursivo").stream().filter(c -> c.documento().equals("primeiro.txt")).map(KnowledgeChunk::id).toList());
        IndexedDocument changed = fixture.preparation.listDocuments().stream().filter(d -> d.originalName().equals("segundo.md")).findFirst().orElseThrow();
        assertEquals(CsvKnowledgeStore.sha256(second), changed.sha256());
        String firstHash = CsvKnowledgeStore.sha256(first), secondHash = CsvKnowledgeStore.sha256(second);
        fixture.preparation.removeDocument(changed.docId());
        assertEquals(1, fixture.preparation.listDocuments().size());
        assertTrue(fixture.store.read("semantico").stream().noneMatch(c -> c.docId().equals(changed.docId())));
        assertEquals(firstHash, CsvKnowledgeStore.sha256(first)); assertEquals(secondHash, CsvKnowledgeStore.sha256(second));
        clearInvocations(fixture.ollama); fixture.preparation.prepare();
        verifyNoInteractions(fixture.ollama);
        assertEquals(1, fixture.preparation.listDocuments().size(), "Preparing registered files must not silently re-add a removed source");
        try (var reports = Files.list(fixture.store.dataDirectory().resolve("relatorios"))) {
            assertTrue(reports.anyMatch(p -> p.toString().endsWith(".json")));
        }
    }

    @Test void addingAnExplicitExternalTextFileAndRemovingTheLastDocumentNeverDeletesIt() throws Exception {
        Fixture fixture = fixture();
        Path external = Files.writeString(temporary.resolve("externo.txt"), "Condições de matrícula e documentação.", StandardCharsets.UTF_8);
        fixture.preparation.addDocument(external);
        IndexedDocument indexed = fixture.preparation.listDocuments().getFirst();
        assertEquals(external.toRealPath().toString(), indexed.sourcePath());
        fixture.preparation.removeDocument(indexed.docId());
        assertTrue(Files.isRegularFile(external));
        assertTrue(fixture.preparation.listDocuments().isEmpty());
        assertEquals(0, fixture.store.manifest().files().get("recursivo").chunks());
        assertEquals(0, fixture.store.manifest().files().get("semantico").chunks());
        assertThrows(IOException.class, () -> fixture.store.read("recursivo"));
    }

    @Test void duplicateOriginalNamesAreRejectedAndPreviousGenerationRemainsReadable() throws Exception {
        Fixture fixture = fixture();
        Path first = Files.writeString(fixture.store.dataDirectory().resolve("manual.txt"), "Primeiro documento.", StandardCharsets.UTF_8);
        fixture.preparation.addDocument(first);
        Path directory = Files.createDirectories(temporary.resolve("externos"));
        Path duplicate = Files.writeString(directory.resolve("manual.txt"), "Segundo documento com outro conteúdo.", StandardCharsets.UTF_8);
        String generation = fixture.store.manifest().generation();
        assertThrows(IOException.class, () -> fixture.preparation.addDocument(duplicate));
        assertEquals(generation, fixture.store.manifest().generation());
        assertEquals("Primeiro documento.", fixture.store.read("recursivo").getFirst().texto());
        assertTrue(Files.isRegularFile(first)); assertTrue(Files.isRegularFile(duplicate));
    }

    @Test void aSemanticFailureDuringUpdatePreservesTheCommittedPairAndSource() throws Exception {
        Fixture fixture = fixture();
        Path source = Files.writeString(fixture.store.dataDirectory().resolve("manual.txt"), "Condição inicial da matrícula.", StandardCharsets.UTF_8);
        fixture.preparation.prepare();
        String pointer = Files.readString(fixture.store.manifestFile());
        Files.writeString(source, "Texto modificado durante atualização controlada.", StandardCharsets.UTF_8);
        doThrow(new IOException("falha semântica controlada")).when(fixture.chunker).semantic(any(), anyList(), any());
        assertThrows(IOException.class, fixture.preparation::prepare);
        assertEquals(pointer, Files.readString(fixture.store.manifestFile()));
        assertEquals("Condição inicial da matrícula.", fixture.store.read("recursivo").getFirst().texto());
        assertEquals("Condição inicial da matrícula.", fixture.store.read("semantico").getFirst().texto());
        assertEquals("Texto modificado durante atualização controlada.", Files.readString(source));
    }

    @Test void forcedRebuildRecoversCorruptCsvWithoutReadingOrDeletingPreviousGeneration() throws Exception {
        Fixture fixture = fixture();
        Path source = Files.writeString(fixture.store.dataDirectory().resolve("recuperacao.txt"), "Regra para recuperação segura do índice.", StandardCharsets.UTF_8);
        fixture.preparation.prepare();
        IndexManifest previous = fixture.store.manifest();
        Path previousRecursive = fixture.store.file("recursivo"), previousSemantic = fixture.store.file("semantico");
        String semanticHash = CsvKnowledgeStore.sha256(previousSemantic), sourceHash = CsvKnowledgeStore.sha256(source);
        Files.writeString(previousRecursive, "CSV corrompido preservado.", StandardCharsets.UTF_8);
        clearInvocations(fixture.ollama);
        assertThrows(IOException.class, fixture.preparation::prepare);
        verifyNoInteractions(fixture.ollama);
        fixture.preparation.rebuild(true);
        verify(fixture.ollama, atLeastOnce()).embed(any(), anyList());
        assertNotEquals(previous.generation(), fixture.store.manifest().generation());
        for (String method : List.of("recursivo", "semantico"))
            assertEquals("Regra para recuperação segura do índice.", fixture.store.read(method).getFirst().texto());
        assertEquals("CSV corrompido preservado.", Files.readString(previousRecursive));
        assertEquals(semanticHash, CsvKnowledgeStore.sha256(previousSemantic));
        assertEquals(sourceHash, CsvKnowledgeStore.sha256(source));
        assertTrue(Files.isRegularFile(previousRecursive.getParent().resolve("manifesto.json")));
    }

    @Test void malformedPointerRecoveryKeepsExactBackupAndDiscoversOnlyLocalDataSources() throws Exception {
        Fixture fixture = fixture();
        Path local = Files.writeString(fixture.store.dataDirectory().resolve("local.txt"), "Documento local disponível para recuperação.", StandardCharsets.UTF_8);
        Path external = Files.writeString(temporary.resolve("externo.md"), "Documento externo que exige seleção explícita.", StandardCharsets.UTF_8);
        fixture.preparation.addDocument(external);
        IndexManifest previous = fixture.store.manifest();
        Path previousCsv = fixture.store.file("recursivo");
        String previousHash = CsvKnowledgeStore.sha256(previousCsv);
        String localHash = CsvKnowledgeStore.sha256(local), externalHash = CsvKnowledgeStore.sha256(external);
        byte[] malformed = "{ manifesto truncado: não apagar nem normalizar\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(fixture.store.manifestFile(), malformed);
        assertThrows(IOException.class, fixture.preparation::prepare);
        try (var files = Files.list(fixture.store.dataDirectory())) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("manifesto-corrompido-")));
        }
        fixture.preparation.rebuild(true);
        List<Path> backups;
        try (var files = Files.list(fixture.store.dataDirectory())) {
            backups = files.filter(p -> p.getFileName().toString().startsWith("manifesto-corrompido-") && p.toString().endsWith(".json.bak")).toList();
        }
        assertEquals(1, backups.size());
        assertArrayEquals(malformed, Files.readAllBytes(backups.getFirst()));
        assertNotEquals(previous.generation(), fixture.store.manifest().generation());
        assertEquals(List.of("local.txt"), fixture.preparation.listDocuments().stream().map(IndexedDocument::originalName).toList());
        assertEquals("Documento local disponível para recuperação.", fixture.store.read("semantico").getFirst().texto());
        assertEquals(previousHash, CsvKnowledgeStore.sha256(previousCsv));
        assertEquals(localHash, CsvKnowledgeStore.sha256(local));
        assertEquals(externalHash, CsvKnowledgeStore.sha256(external));
    }

    @Test void failedForcedRecoveryRetainsCorruptPointerBackupAndAllSources() throws Exception {
        Fixture fixture = fixture();
        Path source = Files.writeString(fixture.store.dataDirectory().resolve("falha.txt"), "Fonte preservada mesmo se o Ollama falhar.", StandardCharsets.UTF_8);
        byte[] malformed = "manifesto inválido original".getBytes(StandardCharsets.UTF_8);
        Files.write(fixture.store.manifestFile(), malformed);
        when(fixture.ollama.embed(any(), anyList())).thenThrow(new IOException("Ollama indisponível no teste"));
        assertThrows(IOException.class, () -> fixture.preparation.rebuild(true));
        assertArrayEquals(malformed, Files.readAllBytes(fixture.store.manifestFile()));
        try (var files = Files.list(fixture.store.dataDirectory())) {
            List<Path> backups = files.filter(p -> p.getFileName().toString().startsWith("manifesto-corrompido-")).toList();
            assertEquals(1, backups.size()); assertArrayEquals(malformed, Files.readAllBytes(backups.getFirst()));
        }
        assertEquals("Fonte preservada mesmo se o Ollama falhar.", Files.readString(source));
    }

    @Test void excessiveDocumentsAndUnsafeCitationNamesAreRejectedBeforeEmbedding() throws Exception {
        Fixture fixture = fixture(); fixture.config.max_documento_bytes = 5;
        Path big = Files.writeString(temporary.resolve("grande.txt"), "documento com mais de cinco bytes", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> fixture.preparation.addDocument(big));
        fixture.config.max_documento_bytes = 50_000;
        Path badName = Files.writeString(temporary.resolve("fonte[forjada].txt"), "trecho", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> fixture.preparation.addDocument(badName));
        Path executable = Files.writeString(temporary.resolve("documento.exe"), "trecho", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> fixture.preparation.addDocument(executable));
        verifyNoInteractions(fixture.ollama);
        assertNull(fixture.store.manifest());
        assertTrue(Files.isRegularFile(big));
    }

    @Test void pdfExtractionStopsAtTheCharacterBudgetBeforeAccumulatingAnOversizedPage() throws Exception {
        Path source = temporary.resolve("limite.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream contents = new PDPageContentStream(document, page)) {
                contents.beginText(); contents.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                contents.newLineAtOffset(10, 700); contents.showText("Texto de teste para limite de extracao. ".repeat(50)); contents.endText();
            }
            document.save(source.toFile());
        }
        PdfPages pdf = new PdfPages();
        assertThrows(IOException.class, () -> pdf.extract(source, 100));
        assertTrue(pdf.extract(source, 10_000).getFirst().text().length() > 100);
        assertThrows(IllegalArgumentException.class, () -> pdf.extract(source, 0));
    }

    private CsvKnowledgeStore store() throws IOException {
        return new CsvKnowledgeStore(MAPPER, Files.createDirectories(temporary.resolve("dados")));
    }
    private Path legacy(CsvKnowledgeStore store, String content) throws IOException {
        return Files.writeString(store.dataDirectory().resolve("base_recursivo.csv"), content, StandardCharsets.UTF_8);
    }
    private IndexedDocument document(String name, String text) throws IOException {
        Path source = Files.writeString(temporary.resolve(name), text, StandardCharsets.UTF_8).toRealPath();
        return new IndexedDocument("doc-" + CsvKnowledgeStore.sha256(source.toString()).substring(0, 24), name,
            CsvKnowledgeStore.sha256(source), 1, source.toString(), false);
    }
    private static KnowledgeChunk chunk(IndexedDocument doc, String method, String text, double[] vector) {
        return new KnowledgeChunk(method + "-" + CsvKnowledgeStore.sha256(text).substring(0, 12), doc.originalName(), 1, method, text, vector,
            List.of(new SourceSpan(1, 0, text.length(), text)), doc.docId(), doc.sha256(), "nomic-embed-text", "2");
    }
    private static String fingerprint() { return CsvKnowledgeStore.sha256("fixed-test-configuration"); }
    private IndexManifest commit(CsvKnowledgeStore store, IndexedDocument doc, String text, double[] vector) throws IOException {
        return store.commit("nomic-embed-text", fingerprint(), List.of(doc), Map.of(
            "recursivo", List.of(chunk(doc, "recursivo", text, vector)),
            "semantico", List.of(chunk(doc, "semantico", text, vector))));
    }
    private record Fixture(ProjectConfig config, CsvKnowledgeStore store, OllamaClient ollama,
                           TextChunker chunker, BasePreparation preparation) { }
    private Fixture fixture() throws IOException {
        ProjectConfig config = new ProjectConfig();
        CsvKnowledgeStore store = store();
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.embed(any(), anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(1);
            return texts.stream().map(t -> new double[]{1, 2}).toList();
        });
        TextChunker chunker = spy(new TextChunker());
        SettingsService settings = mock(SettingsService.class); when(settings.load()).thenReturn(config);
        return new Fixture(config, store, ollama, chunker, new BasePreparation(new PdfPages(), chunker, ollama, store, settings));
    }
}
