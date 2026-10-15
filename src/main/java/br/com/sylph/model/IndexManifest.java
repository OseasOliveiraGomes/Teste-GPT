package br.com.sylph.model;

import java.util.List;
import java.util.Map;

/** One committed generation contains both methods, so a partial write is invisible. */
public record IndexManifest(int schemaVersion, String generation, String embeddingModel,
                            int dimension, String chunkingFingerprint, String createdAt,
                            List<IndexedDocument> documents, Map<String, CsvFile> files) {
    public IndexManifest {
        documents = documents == null ? List.of() : List.copyOf(documents);
        files = files == null ? Map.of() : Map.copyOf(files);
    }
    public record CsvFile(String name, String sha256, int chunks) { }
}
