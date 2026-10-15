package br.com.sylph.model;

import java.util.Objects;

/** Exact half-open offsets into the cleaned text of one source page. */
public record SourceSpan(int pagina, int inicio, int fim, String texto) {
    public SourceSpan {
        Objects.requireNonNull(texto, "texto");
        if (pagina < 1 || inicio < 0 || fim <= inicio || fim - inicio != texto.length())
            throw new IllegalArgumentException("Intervalo de origem invalido");
    }
}
