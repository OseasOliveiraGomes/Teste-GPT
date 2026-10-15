package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.model.SourceSpan;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

@Service
public class TextChunker {
    public static final int RECURSIVE_MAX = 1350;
    public static final int OVERLAP = 200;
    public static final String ALGORITHM_VERSION = "hierarchical-semantic-v2";
    private static final Pattern LINES = Pattern.compile("(?m)^.*$");
    private static final Pattern LIST = Pattern.compile("(?iu)^\\s*(?:[-*•✓]|\\d+[.)]|[a-z][.)]|art\\.\\s*\\d+|§)\\s*.*");
    private static final Pattern SENTENCE = Pattern.compile("[.!?;]\\s+(?=[\\p{L}\\d•✓])");
    private final Map<EmbeddingKey, double[]> embeddingCache = new LinkedHashMap<>(256, .75f, true);

    public record Segment(String text, List<SourceSpan> spans) {
        public Segment {
            Objects.requireNonNull(text, "text");
            spans = List.copyOf(spans);
            if (text.isBlank() || spans.isEmpty()) throw new IllegalArgumentException("Segmento sem origem");
        }
        public String texto() { return text; }
    }
    private record Range(int start, int end) { int size() { return end - start; } }
    private record PageAt(PdfPages.Page page, int start, int end) { }
    private record EmbeddingKey(String server, String model, String text) { }

    public List<String> recursive(String text) {
        if (text == null || text.isBlank()) return List.of();
        return recursive(new ProjectConfig(), List.of(new PdfPages.Page(1, text))).stream().map(Segment::text).toList();
    }
    public List<String> semantic(ProjectConfig cfg, String text, OllamaClient ollama) throws IOException {
        if (text == null || text.isBlank()) return List.of();
        return semantic(cfg, List.of(new PdfPages.Page(1, text)), ollama).stream().map(Segment::text).toList();
    }
    public List<Segment> recursive(ProjectConfig cfg, List<PdfPages.Page> pages) {
        checkLimits(cfg);
        if (!cfg.chunk_cross_pages) {
            List<Segment> result = new ArrayList<>();
            for (PdfPages.Page page : pages) result.addAll(recursiveDocument(cfg, new DocumentText(List.of(page))));
            return List.copyOf(result);
        }
        return recursiveDocument(cfg, new DocumentText(pages));
    }
    private List<Segment> recursiveDocument(ProjectConfig cfg, DocumentText doc) {
        if (doc.text.isBlank()) return List.of();
        Boundaries boundaries = new Boundaries(doc.text);
        List<Range> ranges = new ArrayList<>();
        int start = 0;
        while (start < doc.text.length()) {
            int limit = Math.min(doc.text.length(), start + cfg.chunk_max_chars);
            int end = limit;
            if (limit < doc.text.length()) {
                int minimum = Math.min(limit, start + cfg.chunk_min_chars);
                end = boundaries.cut(start, minimum, limit, cfg.chunk_max_chars);
                int next = overlapStart(doc.text, start, end, cfg.chunk_overlap_chars);
                if (doc.text.length() - next < cfg.chunk_min_chars) {
                    int balanced = doc.text.length() - cfg.chunk_min_chars + cfg.chunk_overlap_chars;
                    if (balanced >= minimum && balanced < end)
                        end = boundaries.cut(start, minimum, balanced, cfg.chunk_max_chars);
                }
            }
            Range range = trimmed(doc.text, start, end);
            if (range.size() > 0) ranges.add(range);
            if (end >= doc.text.length()) break;
            start = overlapStart(doc.text, start, end, cfg.chunk_overlap_chars);
        }
        return doc.segments(ranges);
    }
    private static int overlapStart(String text, int start, int end, int overlap) {
        if (overlap == 0) return end;
        int next = Math.max(start + 1, end - overlap);
        while (next < end && next > 0 && !Character.isWhitespace(text.charAt(next - 1))) next++;
        while (next < end && Character.isWhitespace(text.charAt(next))) next++;
        return next;
    }
    public List<Segment> semantic(ProjectConfig cfg, List<PdfPages.Page> pages, OllamaClient ollama) throws IOException {
        checkLimits(cfg);
        if (!cfg.chunk_cross_pages) {
            List<Segment> result = new ArrayList<>();
            for (PdfPages.Page page : pages) result.addAll(semanticDocument(cfg, new DocumentText(List.of(page)), ollama));
            return List.copyOf(result);
        }
        return semanticDocument(cfg, new DocumentText(pages), ollama);
    }
    private List<Segment> semanticDocument(ProjectConfig cfg, DocumentText doc, OllamaClient ollama) throws IOException {
        if (doc.text.isBlank()) return List.of();
        List<Range> units = semanticUnits(doc.text, cfg.semantic_unit_chars, cfg.chunk_max_chars);
        if (units.size() == 1) return doc.segments(units);
        List<double[]> vectors = unitEmbeddings(cfg, doc, units, ollama);
        List<Range> chunks = new ArrayList<>();
        int first = 0;
        for (int i = 1; i < units.size(); i++) {
            Range current = units.get(i);
            int currentSize = units.get(i - 1).end - units.get(first).start;
            boolean overflow = current.end - units.get(first).start > cfg.chunk_max_chars;
            boolean topicChange = VectorMath.cosine(vectors.get(i - 1), vectors.get(i)) < cfg.limiar_semantico
                && currentSize >= cfg.chunk_min_chars && doc.text.length() - current.start >= cfg.chunk_min_chars;
            if (overflow || topicChange) {
                chunks.add(trimmed(doc.text, units.get(first).start, units.get(i - 1).end));
                first = i;
            }
        }
        Range tail = trimmed(doc.text, units.get(first).start, units.getLast().end);
        if (!chunks.isEmpty() && tail.size() < cfg.chunk_min_chars && tail.end - chunks.getLast().start <= cfg.chunk_max_chars) {
            Range last = chunks.removeLast(); chunks.add(new Range(last.start, tail.end));
        } else chunks.add(tail);
        return doc.segments(chunks);
    }
    private synchronized List<double[]> unitEmbeddings(ProjectConfig cfg, DocumentText doc, List<Range> units,
                                                       OllamaClient ollama) throws IOException {
        Map<EmbeddingKey, double[]> local = new LinkedHashMap<>();
        List<EmbeddingKey> keys = new ArrayList<>();
        for (Range unit : units) {
            EmbeddingKey key = new EmbeddingKey(cfg.ollama_url, cfg.modelo_embeddings, doc.text.substring(unit.start, unit.end));
            keys.add(key);
            if (!local.containsKey(key)) local.put(key, embeddingCache.get(key));
        }
        List<EmbeddingKey> missing = local.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList();
        for (int i = 0; i < missing.size(); i += 12) {
            List<EmbeddingKey> batch = missing.subList(i, Math.min(i + 12, missing.size()));
            List<double[]> result = ollama.embed(cfg, batch.stream().map(EmbeddingKey::text).toList());
            if (result.size() != batch.size()) throw new IOException("Quantidade de embeddings semanticos invalida");
            for (int j = 0; j < batch.size(); j++) {
                validateVector(result.get(j)); local.put(batch.get(j), result.get(j).clone());
            }
        }
        int dimensions = local.values().iterator().next().length;
        for (var entry : local.entrySet()) {
            validateVector(entry.getValue());
            if (entry.getValue().length != dimensions)
                throw new IOException("Dimensoes de embeddings semanticos incompatíveis; reindexe a base");
            embeddingCache.put(entry.getKey(), entry.getValue());
        }
        while (embeddingCache.size() > cfg.semantic_embedding_cache_size)
            embeddingCache.remove(embeddingCache.keySet().iterator().next());
        return keys.stream().map(local::get).toList();
    }
    private static void validateVector(double[] vector) throws IOException {
        if (vector == null || vector.length == 0) throw new IOException("Embedding semantico vazio");
        double norm = 0;
        for (double value : vector) {
            if (!Double.isFinite(value)) throw new IOException("Embedding semantico nao finito");
            norm += value * value;
        }
        if (!Double.isFinite(norm) || norm == 0) throw new IOException("Embedding semantico sem norma valida");
    }
    private static List<Range> semanticUnits(String text, int target, int maximum) {
        Boundaries boundaries = new Boundaries(text);
        TreeSet<Integer> cuts = new TreeSet<>();
        cuts.add(0); cuts.add(text.length());
        cuts.addAll(boundaries.paragraphs); cuts.addAll(boundaries.lines); cuts.addAll(boundaries.sentences);
        cuts.removeIf(cut -> cut != 0 && cut != text.length() && boundaries.protectedAt(cut, maximum));
        List<Range> units = new ArrayList<>();
        int previous = 0;
        for (int cut : cuts) {
            Range range = trimmed(text, previous, cut);
            while (range.size() > maximum || range.size() > target * 2 && !boundaries.isProtected(range)) {
                int limit = Math.min(range.end, range.start + Math.min(maximum, target * 2));
                int end = boundaries.cut(range.start, range.start + 1, limit, Math.min(maximum, target * 2));
                units.add(trimmed(text, range.start, end)); range = trimmed(text, end, range.end);
            }
            if (range.size() > 0) units.add(range);
            previous = cut;
        }
        List<Range> joined = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            Range unit = units.get(i);
            if (i + 1 < units.size() && isHeading(text.substring(unit.start, unit.end)) && units.get(i + 1).end - unit.start <= maximum)
                unit = new Range(unit.start, units.get(++i).end);
            joined.add(unit);
        }
        return joined;
    }
    static boolean isHeading(String line) {
        String value = line.strip();
        if (value.isEmpty() || value.length() > 110 || value.contains("\n")) return false;
        if (value.matches("#{1,6}\\s+.+") || value.matches("(?iu)(?:cap[ií]tulo|se[cç][aã]o|anexo)\\s+.+")) return true;
        if (value.matches("\\d+(?:\\.\\d+)+[.)]?\\s+\\p{Lu}.*")) return true;
        return value.matches(".*\\p{L}.*") && value.equals(value.toUpperCase(Locale.ROOT))
            && !value.matches(".*[.!?;:]$") && !LIST.matcher(value).matches();
    }
    private static void checkLimits(ProjectConfig cfg) {
        Objects.requireNonNull(cfg, "config");
        if (cfg.chunk_max_chars < 1 || cfg.chunk_min_chars < 1 || cfg.chunk_min_chars > cfg.chunk_max_chars
            || cfg.chunk_overlap_chars < 0 || cfg.chunk_overlap_chars >= cfg.chunk_max_chars
            || cfg.semantic_unit_chars < 1 || cfg.semantic_embedding_cache_size < 0)
            throw new IllegalArgumentException("Limites de chunking invalidos");
    }
    private static Range trimmed(String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        return new Range(start, end);
    }
    private static final class DocumentText {
        final String text;
        final List<PageAt> pages = new ArrayList<>();
        DocumentText(List<PdfPages.Page> source) {
            StringBuilder combined = new StringBuilder(); Set<Integer> numbers = new HashSet<>();
            for (PdfPages.Page page : source) {
                if (page.number() < 1 || !numbers.add(page.number())) throw new IllegalArgumentException("Paginas de origem invalidas");
                if (page.text() == null || page.text().isBlank()) continue;
                if (!combined.isEmpty()) combined.append("\n\n");
                int start = combined.length(); combined.append(page.text()); pages.add(new PageAt(page, start, combined.length()));
            }
            text = combined.toString();
        }
        List<Segment> segments(List<Range> ranges) {
            List<Segment> result = new ArrayList<>();
            for (Range range : ranges) {
                if (range.size() <= 0) continue;
                List<SourceSpan> spans = new ArrayList<>();
                for (PageAt at : pages) {
                    int start = Math.max(range.start, at.start), end = Math.min(range.end, at.end);
                    if (start < end) spans.add(new SourceSpan(at.page.number(), start - at.start, end - at.start, text.substring(start, end)));
                }
                if (!spans.isEmpty()) result.add(new Segment(text.substring(range.start, range.end), spans));
            }
            return List.copyOf(result);
        }
    }
    /** Structural hierarchy selects recursive cuts; embeddings decide semantic transitions. */
    private static final class Boundaries {
        final String text;
        final NavigableSet<Integer> sections = new TreeSet<>(), paragraphs = new TreeSet<>(),
            lines = new TreeSet<>(), sentences = new TreeSet<>(), words = new TreeSet<>();
        final List<Range> protectedRanges = new ArrayList<>();
        Boundaries(String text) {
            this.text = text;
            Matcher matcher = LINES.matcher(text); List<Range> lineRanges = new ArrayList<>();
            while (matcher.find()) {
                Range line = new Range(matcher.start(), matcher.end()); lineRanges.add(line);
                if (isHeading(matcher.group())) sections.add(line.start);
                if (matcher.end() < text.length() && !isHeading(matcher.group())) lines.add(matcher.end() + 1);
            }
            for (int i = 0; i < lineRanges.size(); i++) {
                Range line = lineRanges.get(i);
                if (!LIST.matcher(text.substring(line.start, line.end)).matches()) continue;
                int end = line.end;
                for (int j = i + 1; j < lineRanges.size(); j++) {
                    Range continuation = lineRanges.get(j); String value = text.substring(continuation.start, continuation.end);
                    if (value.isBlank() || LIST.matcher(value).matches() || isHeading(value)) break;
                    end = continuation.end;
                }
                protectedRanges.add(new Range(line.start, end));
            }
            matcher = Pattern.compile("\\n\\s*\\n").matcher(text);
            while (matcher.find()) paragraphs.add(matcher.end());
            matcher = SENTENCE.matcher(text);
            while (matcher.find()) {
                String before = text.substring(Math.max(0, matcher.start() - 12), matcher.start() + 1);
                if (!before.matches("(?iu).*(?:art|prof|dra?|sr|sra|etc|n[oº])\\.$")) sentences.add(matcher.end());
            }
            for (int i = 1; i < text.length(); i++)
                if (Character.isWhitespace(text.charAt(i - 1)) && !Character.isWhitespace(text.charAt(i))) words.add(i);
        }
        boolean protectedAt(int cut, int max) {
            for (Range range : protectedRanges) if (range.size() <= max && cut > range.start && cut < range.end) return true;
            return false;
        }
        boolean isProtected(Range unit) {
            return protectedRanges.stream().anyMatch(range -> range.start <= unit.start && range.end >= unit.end);
        }
        int cut(int start, int min, int max, int protectedMax) {
            for (NavigableSet<Integer> level : List.of(sections, paragraphs, lines, sentences, words)) {
                Integer candidate = level.floor(max);
                while (candidate != null && candidate >= min && candidate > start) {
                    if (!protectedAt(candidate, protectedMax)) return candidate;
                    candidate = level.lower(candidate);
                }
            }
            for (Range range : protectedRanges)
                if (range.start > start && range.start < max && range.end > max && range.size() <= protectedMax) return range.start;
            Integer word = words.floor(max); return word != null && word > start ? word : max;
        }
    }
    /** Frozen original algorithms, page-contained, for reproducible SP 2.1 runs. */
    public List<Segment> recursiveBaseline(ProjectConfig cfg, List<PdfPages.Page> pages) {
        List<Segment> result = new ArrayList<>();
        for(PdfPages.Page page:pages) {
            String text=page.text(); if(text==null||text.isBlank())continue; int pos=0;
            while(pos<text.length()) {
                int end=Math.min(text.length(),pos+RECURSIVE_MAX);
                if(end<text.length()) {
                    int minCut=pos+550,cut=text.lastIndexOf("\n\n",end);
                    if(cut<minCut)cut=text.lastIndexOf("\n",end);
                    if(cut<minCut)cut=text.lastIndexOf(". ",end);
                    if(cut<minCut)cut=text.lastIndexOf(" ",end);
                    if(cut>=minCut)end=cut+1;
                }
                int begin=pos,finish=end;
                while(begin<finish&&text.charAt(begin)<=' ')begin++;
                while(finish>begin&&text.charAt(finish-1)<=' ')finish--;
                if(finish>begin)result.add(new Segment(text.substring(begin,finish),List.of(new SourceSpan(page.number(),begin,finish,text.substring(begin,finish)))));
                if(end>=text.length())break;
                pos=Math.max(pos+1,end-OVERLAP);
            }
        }
        return List.copyOf(result);
    }
    public List<Segment> semanticBaseline(ProjectConfig cfg, List<PdfPages.Page> pages, OllamaClient ollama) throws IOException {
        BaselineTextChunker baseline = new BaselineTextChunker(); List<Segment> result = new ArrayList<>();
        for (PdfPages.Page page : pages) {
            if(page.text()!=null&&page.text().length()>=700&&sentencesAsUnits(page.text(),330).size()<3) {
                result.addAll(recursiveBaseline(cfg,List.of(page))); continue;
            }
            List<String> chunks = baseline.semantic(cfg, page.text(), ollama);
            boolean overlaps = page.text() != null && page.text().length() >= 700 && sentencesAsUnits(page.text(), 330).size() < 3;
            result.addAll(mapBaseline(page, chunks, overlaps));
        }
        return List.copyOf(result);
    }
    private static List<Segment> mapBaseline(PdfPages.Page page, List<String> chunks, boolean overlaps) {
        if (chunks.isEmpty()) return List.of();
        String text = page.text(); StringBuilder normalized = new StringBuilder(); List<Integer> offsets = new ArrayList<>();
        boolean whitespace = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                if (!whitespace) { normalized.append(' '); offsets.add(i); }
                whitespace = true;
            } else { normalized.append(text.charAt(i)); offsets.add(i); whitespace = false; }
        }
        List<Segment> result = new ArrayList<>(); int searchStart = 0;
        for (String chunk : chunks) {
            String value = chunk.replaceAll("\\s+", " ").strip(); int match = normalized.indexOf(value, searchStart);
            if (match < 0 && !overlaps) match = normalized.indexOf(value, Math.max(0, searchStart - OVERLAP - 1));
            if (match < 0) throw new IllegalStateException("Nao foi possivel rastrear o segmento SP2.1");
            int start = offsets.get(match), end = offsets.get(match + value.length() - 1) + 1;
            result.add(new Segment(chunk, List.of(new SourceSpan(page.number(), start, end, text.substring(start, end)))));
            searchStart = overlaps ? match + 1 : match + value.length();
        }
        return List.copyOf(result);
    }
    static List<String> sentencesAsUnits(String text, int targetChars) {
        return BaselineTextChunker.sentencesAsUnits(text, targetChars);
    }
}
