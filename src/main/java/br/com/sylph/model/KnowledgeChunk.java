package br.com.sylph.model;

import java.util.List;

/** Evidence text plus provenance; page is only the first page for compatibility. */
public record KnowledgeChunk(String id, String documento, int pagina, String metodo,
                             String texto, double[] embedding, List<SourceSpan> spans,
                             String docId, String hash, String modeloEmbeddings, String indexVersion) {
    public KnowledgeChunk {
        embedding = embedding == null ? new double[0] : embedding.clone();
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    /** Legacy CSV offsets are unknown; only its page-contained text is guaranteed. */
    public KnowledgeChunk(String id, String documento, int pagina, String metodo,
                          String texto, double[] embedding) {
        this(id, documento, pagina, metodo, texto, embedding,
            texto == null ? List.of() : List.of(new SourceSpan(pagina, 0, texto.length(), texto)),
            documento, "", "", "legacy");
    }

    @Override public double[] embedding() { return embedding.clone(); }
}
