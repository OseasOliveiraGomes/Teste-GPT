package br.com.sylph.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.*;

@Service
public class PdfPages {
    public record Page(int number, String text) { }

    public int pageCount(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) { return doc.getNumberOfPages(); }
    }

    public List<Page> extract(Path file) throws IOException { return extract(file, 5_000_000); }

    /** Bound extraction while PDFBox writes, including exceptionally large single pages. */
    public List<Page> extract(Path file, int maximumCharacters) throws IOException {
        if (maximumCharacters < 1) throw new IllegalArgumentException("Limite de texto PDF deve ser positivo.");
        List<Page> pages = new ArrayList<>();
        int extracted = 0;
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Extração do PDF cancelada.");
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                LimitedTextWriter writer = new LimitedTextWriter(maximumCharacters - extracted);
                stripper.writeText(doc, writer);
                extracted += writer.text.length();
                String cleaned = clean(writer.text.toString());
                if (!cleaned.isBlank()) pages.add(new Page(p, cleaned));
            }
        }
        return pages;
    }

    private static final class LimitedTextWriter extends Writer {
        private final int maximum;
        private final StringBuilder text = new StringBuilder();
        private LimitedTextWriter(int maximum) { this.maximum = maximum; }
        @Override public void write(char[] chars, int offset, int length) throws IOException {
            if (length > maximum - text.length()) throw new IOException("Texto extraído do PDF excede max_documento_caracteres.");
            text.append(chars, offset, length);
        }
        @Override public void flush() { }
        @Override public void close() { }
    }

    public static String clean(String raw) {
        String text = raw.replace("\r", "")
            .replaceAll("(?m)^\\s*Público\\s*$", "")
            .replaceAll("(?m)^\\s*\\d{1,3}\\s*$", "")
            .replaceAll("([\\p{L}])[-‐]\\n([\\p{Ll}])", "$1$2")
            .replaceAll("[\\t ]+", " ")
            .replaceAll("(?m)^[ ]+|[ ]+$", "")
            .replaceAll("\n{3,}", "\n\n")
            .trim();
        return text;
    }
}
