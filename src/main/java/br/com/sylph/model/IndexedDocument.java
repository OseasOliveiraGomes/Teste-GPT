package br.com.sylph.model;

/** Source files are referenced for update, and are never deleted by indexing. */
public record IndexedDocument(String docId, String originalName, String sha256,
                              int pages, String sourcePath, boolean principal) { }
