package br.com.sylph.service;

import br.com.sylph.model.IndexedDocument;
import br.com.sylph.model.KnowledgeChunk;
import br.com.sylph.model.SourceSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceStoreCacheTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper();

    private void commit(CsvKnowledgeStore store, String text) throws IOException {
        Path source = temporary.resolve("manual.txt").toAbsolutePath();
        Files.writeString(source, text);
        String hash = CsvKnowledgeStore.sha256(source);
        IndexedDocument document = new IndexedDocument("doc-" + "a".repeat(24), "manual.txt", hash, 1, source.toString(), false);
        store.commit("nomic-embed-text", CsvKnowledgeStore.sha256("cache-reference-test"), List.of(document), Map.of(
            "recursivo", List.of(chunk("recursivo", text, hash)),
            "semantico", List.of(chunk("semantico", text, hash))));
    }

    private KnowledgeChunk chunk(String method, String text, String hash) {
        return new KnowledgeChunk(method, "manual.txt", 1, method, text, new double[]{1},
            List.of(new SourceSpan(1, 0, text.length(), text)), "doc-" + "a".repeat(24), hash, "nomic-embed-text", "2");
    }

    @Test void fallbackReusesImmutableSnapshotsAndExplicitInvalidationClearsThem() throws Exception {
        CsvKnowledgeStore store = new CsvKnowledgeStore(mapper, temporary.resolve("dados"));
        CsvKnowledgeStore reference = new CsvKnowledgeStore(mapper, store.dataDirectory().resolve("referencia"));
        commit(reference, "Prazo oficial.");
        var first = store.readBaseline("recursivo");
        assertSame(first, store.readBaseline("recursivo"));
        double[] vector = first.getFirst().embedding();
        vector[0] = 999;
        assertEquals(1, store.readBaseline("recursivo").getFirst().embedding()[0]);
        store.invalidate();
        var reloaded = store.readBaseline("recursivo");
        assertNotSame(first, reloaded);
        assertEquals("Prazo oficial.", reloaded.getFirst().texto());
        assertSame(reloaded, store.readBaseline("recursivo"));
    }

    @Test void warmedReferenceCacheRejectsCorruptionEvenWithIdenticalSizeAndModificationTime() throws Exception {
        CsvKnowledgeStore store = new CsvKnowledgeStore(mapper, temporary.resolve("dados"));
        CsvKnowledgeStore reference = new CsvKnowledgeStore(mapper, store.dataDirectory().resolve("referencia"));
        commit(reference, "Prazo de 5 dias.");
        store.readBaseline("recursivo");
        Path csv = reference.file("recursivo");
        var timestamp = Files.getLastModifiedTime(csv);
        long size = Files.size(csv);
        String contents = Files.readString(csv);
        Files.writeString(csv, contents.replace("Prazo de 5 dias.", "Prazo de 9 dias."));
        Files.setLastModifiedTime(csv, timestamp);
        assertEquals(size, Files.size(csv));
        assertThrows(IOException.class, () -> store.readBaseline("recursivo"));
        assertThrows(IOException.class, () -> store.baselineRevision("recursivo"));
        assertEquals("Prazo de 5 dias.", store.readBaseline("semantico").getFirst().texto());
    }

    @Test void committingAnotherGenerationReplacesTheCachedSnapshotAndRevision() throws Exception {
        CsvKnowledgeStore store = new CsvKnowledgeStore(mapper, temporary.resolve("dados"));
        CsvKnowledgeStore reference = new CsvKnowledgeStore(mapper, store.dataDirectory().resolve("referencia"));
        commit(reference, "Regra anterior.");
        var first = store.readBaseline("recursivo");
        String revision = store.baselineRevision("recursivo");
        commit(reference, "Regra seguinte.");
        var updated = store.readBaseline("recursivo");
        assertNotSame(first, updated);
        assertNotEquals(revision, store.baselineRevision("recursivo"));
        assertEquals("Regra seguinte.", updated.getFirst().texto());
        assertSame(updated, store.readBaseline("recursivo"));
    }
}
