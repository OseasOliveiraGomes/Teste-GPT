package br.com.sylph.service;

import br.com.sylph.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Read-only validation of an available prepared index; no Ollama or preparation is invoked. */
class RealIndexProvenanceTest {
    @Test void everyVersionedSpanMatchesTheExactPhysicalSourcePageAndDocumentHash() throws Exception {
        CsvKnowledgeStore store = new CsvKnowledgeStore(new ObjectMapper());
        assumeTrue(Files.isRegularFile(store.manifestFile()), "Índice SP 2.2 não preparado neste ambiente.");
        IndexManifest manifest = store.manifest();
        assumeTrue(!manifest.documents().isEmpty(), "Índice disponível sem documentos.");
        Map<String, Map<Integer, String>> originals = new HashMap<>();
        PdfPages pdf = new PdfPages();
        for (IndexedDocument document : manifest.documents()) {
            Path source = Path.of(document.sourcePath());
            assertTrue(Files.isRegularFile(source), document.originalName());
            assertEquals(document.sha256(), CsvKnowledgeStore.sha256(source), "Hash da fonte " + document.originalName());
            List<PdfPages.Page> pages;
            if (document.originalName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                assertEquals(pdf.pageCount(source), document.pages());
                pages = pdf.extract(source);
            } else {
                assertEquals(1, document.pages(), "TXT/Markdown têm uma página lógica.");
                String text = Files.readString(source, StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
                pages = List.of(new PdfPages.Page(1, text));
            }
            Map<Integer, String> text = new HashMap<>(); pages.forEach(p -> text.put(p.number(), p.text()));
            originals.put(document.docId(), text);
        }
        for (String method : List.of("recursivo", "semantico")) {
            List<KnowledgeChunk> chunks = store.read(method);
            Set<String> ids = new HashSet<>();
            for (KnowledgeChunk chunk : chunks) {
                assertTrue(ids.add(chunk.id()), "Chunk duplicado " + chunk.id());
                assertEquals(method, chunk.metodo());
                assertEquals(manifest.embeddingModel(), chunk.modeloEmbeddings());
                assertEquals(manifest.dimension(), chunk.embedding().length);
                assertEquals(chunk.pagina(), chunk.spans().getFirst().pagina());
                for (SourceSpan span : chunk.spans()) {
                    String page = originals.get(chunk.docId()).get(span.pagina());
                    assertNotNull(page, "Página inexistente: " + chunk.id() + " página " + span.pagina());
                    assertTrue(span.inicio() >= 0 && span.fim() <= page.length(), chunk.id());
                    assertEquals(page.substring(span.inicio(), span.fim()), span.texto(), chunk.id());
                }
            }
            assertEquals(manifest.files().get(method).chunks(), chunks.size());
        }
    }
}
