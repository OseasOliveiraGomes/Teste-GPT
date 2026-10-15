package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.model.IndexManifest;
import br.com.sylph.model.IndexedDocument;
import br.com.sylph.model.KnowledgeChunk;
import br.com.sylph.model.SourceSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class CsvKnowledgeStore {
    private static final List<String> METHODS = List.of("recursivo", "semantico");
    private static final String LEGACY_HEADER = "id,documento,pagina,metodo,texto,embedding";
    private static final String HEADER = LEGACY_HEADER + ",spans,docId,hash,modeloEmbeddings,indexVersion";
    private static final long MAX_CSV_BYTES = 512L * 1024 * 1024;
    private final ObjectMapper mapper;
    private final Path data;
    private final Map<String, Cached> cache = new HashMap<>();
    private CsvKnowledgeStore referenceStore;
    private record Cached(String revision, List<KnowledgeChunk> chunks) { }

    @Autowired public CsvKnowledgeStore(ObjectMapper mapper) { this(mapper, Path.of("dados")); }
    public CsvKnowledgeStore(ObjectMapper mapper, Path dataDirectory) {
        this.mapper = mapper;
        this.data = dataDirectory.toAbsolutePath().normalize();
    }
    public Path dataDirectory() { return data; }
    public Path manifestFile() { return data.resolve("indice-manifesto.json"); }
    private Path legacyFile(String method) { validateMethod(method); return data.resolve("base_" + method + ".csv"); }

    /** Existing callers can continue checking readiness with file(method). */
    public Path file(String method) {
        validateMethod(method);
        try {
            IndexManifest manifest = manifest();
            return manifest == null ? legacyFile(method) : committedFile(manifest, method);
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    /** A legacy single-file write is retained; committed generations use commit(). */
    public synchronized void write(String method, List<KnowledgeChunk> chunks) throws IOException {
        if (manifest() != null) throw new IOException("Índice versionado ativo: grave ambos os métodos com commit ou recrie as bases.");
        validateChunks(method, chunks, null);
        Path destination = legacyFile(method);
        Files.createDirectories(data);
        Path temp = Files.createTempFile(data, "base-", ".tmp");
        writeCsv(temp, chunks);
        atomicReplace(temp, destination);
        cache.clear();
    }

    /** Both CSV files are validated before the only publication step: the pointer. */
    public synchronized IndexManifest commit(String model, String fingerprint, List<IndexedDocument> documents,
                                              Map<String, List<KnowledgeChunk>> bases) throws IOException {
        if (model == null || model.isBlank() || fingerprint == null || fingerprint.isBlank())
            throw new IOException("Modelo e configuração de chunking precisam estar identificados.");
        if (!bases.keySet().equals(new HashSet<>(METHODS))) throw new IOException("A geração precisa conter os dois métodos.");
        int dimension = 0;
        for (String method : METHODS) {
            validateChunks(method, bases.get(method), null);
            for (KnowledgeChunk chunk : bases.get(method)) {
                if (!model.equals(chunk.modeloEmbeddings())) throw new IOException("Modelo de embedding diferente nos chunks.");
                if (dimension == 0) dimension = chunk.embedding().length;
                if (chunk.embedding().length != dimension) throw new IOException("Dimensões de embeddings incompatíveis entre métodos.");
            }
        }
        String generation = "g-" + UUID.randomUUID();
        Path indexRoot = data.resolve("indices");
        Files.createDirectories(indexRoot);
        rejectSymlink(indexRoot);
        Path directory = indexRoot.resolve(generation);
        Files.createDirectory(directory);
        Map<String, IndexManifest.CsvFile> files = new LinkedHashMap<>();
        // Failures leave the previous pointer intact and retain the uncommitted directory for diagnosis.
        for (String method : METHODS) {
            Path destination = directory.resolve("base_" + method + ".csv");
            writeCsv(destination, bases.get(method));
            files.put(method, new IndexManifest.CsvFile(destination.getFileName().toString(), sha256(destination), bases.get(method).size()));
        }
        IndexManifest manifest = new IndexManifest(2, generation, model, dimension, fingerprint,
            Instant.now().toString(), documents, files);
        validateManifest(manifest);
        for (String method : METHODS) validateChunks(method, bases.get(method), manifest);
        Path generationManifest = directory.resolve("manifesto.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(generationManifest.toFile(), manifest);
        // Read-back catches serialization, vector, checksum and provenance defects before publishing.
        for (String method : METHODS) load(directory.resolve(files.get(method).name()), method, manifest);
        Path temporaryPointer = Files.createTempFile(data, "manifesto-", ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporaryPointer.toFile(), manifest);
        atomicReplace(temporaryPointer, manifestFile());
        cache.clear();
        return manifest;
    }

    public synchronized IndexManifest manifest() throws IOException {
        Path pointer = manifestFile();
        if (!Files.exists(pointer)) return null;
        rejectSymlink(pointer);
        if (!Files.isRegularFile(pointer) || Files.size(pointer) > 4L * 1024 * 1024)
            throw new IOException("Manifesto do índice inválido; recrie a base sem apagar os documentos.");
        try {
            IndexManifest result = mapper.readValue(pointer.toFile(), IndexManifest.class);
            validateManifest(result);
            return result;
        } catch (IllegalArgumentException ex) { throw new IOException("Metadados do índice inválidos", ex); }
    }

    public synchronized List<KnowledgeChunk> read(String method) throws IOException {
        validateMethod(method);
        IndexManifest manifest = manifest();
        if (manifest == null) return readBaseline(method);
        Path path = committedFile(manifest, method);
        String signature = verifiedRevision(path, manifest.files().get(method).sha256()) + ":" + sha256(manifestFile());
        return readCached(method, signature, path, manifest);
    }

    /** Original SP 2.1 data remains available even after an advanced generation is committed. */
    public synchronized List<KnowledgeChunk> readBaseline(String method) throws IOException {
        Path path = legacyFile(method);
        if(!Files.isRegularFile(path)&&Files.isRegularFile(data.resolve("referencia").resolve("indice-manifesto.json"))) return referenceStore().read(method);
        return readCached("baseline:" + method, verifiedRevision(path, null), path, null);
    }

    public synchronized String revision(String method) throws IOException {
        validateMethod(method);
        IndexManifest manifest = manifest();
        return manifest == null ? baselineRevision(method) : manifest.generation() + ":" + sha256(manifestFile()) + ":"
            + verifiedRevision(committedFile(manifest, method), manifest.files().get(method).sha256());
    }
    public synchronized String baselineRevision(String method) throws IOException {
        Path path = legacyFile(method);
        if (!Files.isRegularFile(path) && Files.isRegularFile(data.resolve("referencia").resolve("indice-manifesto.json")))
            return referenceStore().revision(method);
        return verifiedRevision(path, null);
    }

    public synchronized void assertCompatible(ProjectConfig cfg) throws IOException {
        IndexManifest manifest = manifest();
        if (manifest != null && !manifest.embeddingModel().equals(cfg.modelo_embeddings))
            throw new IOException("Modelo de embeddings mudou (índice: " + manifest.embeddingModel()
                + "; configuração: " + cfg.modelo_embeddings + "). Execute preparar/recriar antes de consultar.");
    }

    public synchronized List<IndexedDocument> listDocuments() throws IOException {
        IndexManifest manifest = manifest();
        return manifest == null ? List.of() : manifest.documents();
    }
    public synchronized void invalidate() {
        cache.clear();
        if (referenceStore != null) referenceStore.invalidate();
    }

    private CsvKnowledgeStore referenceStore() {
        // The reference still checks the full SHA-256 on every read. Reusing its
        // owner only avoids reparsing unchanged CSVs and discarding lexical caches.
        if (referenceStore == null) referenceStore = new CsvKnowledgeStore(mapper, data.resolve("referencia"));
        return referenceStore;
    }

    private List<KnowledgeChunk> readCached(String key, String signature, Path path, IndexManifest manifest) throws IOException {
        Cached existing = cache.get(key);
        if (existing != null && existing.revision().equals(signature)) return existing.chunks();
        List<KnowledgeChunk> loaded = List.copyOf(load(path, key.startsWith("baseline:") ? key.substring(9) : key, manifest));
        if (loaded.isEmpty()) throw new IOException("Base vazia; adicione documentos ou execute preparar.");
        cache.put(key, new Cached(signature, loaded));
        return loaded;
    }

    private List<KnowledgeChunk> load(Path path, String method, IndexManifest manifest) throws IOException {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<List<String>> rows = records(reader);
            if (rows.isEmpty()) throw new IOException("CSV sem cabeçalho: " + path);
            String header = String.join(",", rows.getFirst()).replaceFirst("^\uFEFF", "");
            boolean legacy = LEGACY_HEADER.equals(header);
            if (!legacy && !HEADER.equals(header)) throw new IOException("Cabeçalho CSV desconhecido: " + path);
            if (manifest != null && legacy) throw new IOException("CSV versionado perdeu os metadados de origem.");
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.size() != (legacy ? 6 : 11)) throw new IOException("CSV malformado, linha lógica " + (i + 1));
                double[] vector = mapper.readValue(row.get(5), double[].class);
                KnowledgeChunk chunk = legacy
                    ? new KnowledgeChunk(row.get(0), row.get(1), Integer.parseInt(row.get(2)), row.get(3), row.get(4), vector)
                    : new KnowledgeChunk(row.get(0), row.get(1), Integer.parseInt(row.get(2)), row.get(3), row.get(4), vector,
                        Arrays.asList(mapper.readValue(row.get(6), SourceSpan[].class)), row.get(7), row.get(8), row.get(9), row.get(10));
                chunks.add(chunk);
            }
        } catch (RuntimeException ex) { throw new IOException("CSV ou metadados inválidos: " + path.getFileName(), ex); }
        validateChunks(method, chunks, manifest);
        return chunks;
    }

    private void writeCsv(Path path, List<KnowledgeChunk> chunks) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(HEADER + "\n");
            for (KnowledgeChunk c : chunks) {
                out.write(String.join(",", esc(c.id()), esc(c.documento()), Integer.toString(c.pagina()), esc(c.metodo()),
                    esc(c.texto()), esc(mapper.writeValueAsString(c.embedding())), esc(mapper.writeValueAsString(c.spans())),
                    esc(c.docId()), esc(c.hash()), esc(c.modeloEmbeddings()), esc(c.indexVersion())) + "\n");
            }
        }
    }

    private void validateManifest(IndexManifest manifest) throws IOException {
        if (manifest == null || manifest.schemaVersion() != 2 || manifest.generation() == null
            || !manifest.generation().matches("g-[0-9a-f-]{36}") || manifest.dimension() < 0
            || manifest.embeddingModel() == null || manifest.embeddingModel().isBlank()
            || manifest.chunkingFingerprint() == null || !manifest.chunkingFingerprint().matches("[0-9a-f]{64}")
            || !manifest.files().keySet().equals(new HashSet<>(METHODS))) throw new IOException("Manifesto do índice incompatível ou corrompido.");
        Set<String> ids = new HashSet<>();
        for (IndexedDocument doc : manifest.documents()) {
            if (doc == null || doc.docId() == null || !doc.docId().matches("doc-[0-9a-f]{24}") || !ids.add(doc.docId())
                || !validName(doc.originalName()) || doc.pages() < 1 || doc.sha256() == null || !doc.sha256().matches("[0-9a-f]{64}")
                || doc.sourcePath() == null || !Path.of(doc.sourcePath()).isAbsolute()) throw new IOException("Documento inválido no manifesto.");
        }
        for (String method : METHODS) {
            IndexManifest.CsvFile file = manifest.files().get(method);
            if (file == null || !("base_" + method + ".csv").equals(file.name()) || file.chunks() < 0
                || file.sha256() == null || !file.sha256().matches("[0-9a-f]{64}")) throw new IOException("Arquivo inválido no manifesto.");
        }
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 255 && name.chars().noneMatch(c -> c<32 || "[]|/\\".indexOf(c)>=0);
    }

    private static void validateChunks(String method, List<KnowledgeChunk> chunks, IndexManifest manifest) throws IOException {
        validateMethod(method);
        if (chunks == null) throw new IOException("Chunks ausentes.");
        Set<String> ids = new HashSet<>();
        int dimension = 0;
        Map<String, IndexedDocument> docs = new HashMap<>();
        if (manifest != null) manifest.documents().forEach(d -> docs.put(d.docId(), d));
        for (KnowledgeChunk chunk : chunks) {
            if (chunk == null || chunk.id() == null || chunk.id().isBlank() || !ids.add(chunk.id())
                || !validName(chunk.documento()) || chunk.pagina() < 1 || !method.equals(chunk.metodo())
                || chunk.texto() == null || chunk.texto().isBlank()) throw new IOException("Chunk inválido, repetido ou com método diferente.");
            double[] vector = chunk.embedding();
            if (!VectorMath.valid(vector)) throw new IOException("Embedding vazio, nulo ou não finito no índice.");
            if (dimension == 0) dimension = vector.length;
            if (dimension != vector.length) throw new IOException("Dimensões de embedding inconsistentes no CSV.");
            for (double value : vector) if (!Double.isFinite(value)) throw new IOException("Embedding contém valor não finito.");
            if (chunk.spans().isEmpty()) throw new IOException("Chunk sem proveniência.");
            for (SourceSpan span : chunk.spans()) {
                if (span == null || span.pagina() < 1 || span.inicio() < 0 || span.fim() <= span.inicio()
                    || span.texto() == null || span.texto().length() != span.fim() - span.inicio()) throw new IOException("Intervalo de origem inválido.");
            }
            if (chunk.spans().getFirst().pagina() != chunk.pagina()) throw new IOException("Primeira página inconsistente.");
            String sourceText = String.join(" ", chunk.spans().stream().map(SourceSpan::texto).toList()).replaceAll("\\s+", " ").trim();
            if (!sourceText.equals(chunk.texto().replaceAll("\\s+", " ").trim())) throw new IOException("Texto não coincide com os intervalos de origem.");
            if (manifest != null) {
                IndexedDocument doc = docs.get(chunk.docId());
                if (doc == null || !doc.originalName().equals(chunk.documento()) || !doc.sha256().equals(chunk.hash())
                    || !manifest.embeddingModel().equals(chunk.modeloEmbeddings()) || manifest.dimension() != vector.length
                    || !"2".equals(chunk.indexVersion()) || chunk.spans().stream().anyMatch(s -> s.pagina() > doc.pages()))
                    throw new IOException("Metadados do chunk incompatíveis com o manifesto.");
            }
        }
        if (manifest != null && chunks.size() != manifest.files().get(method).chunks()) throw new IOException("Quantidade de chunks diferente do manifesto.");
    }

    private Path committedFile(IndexManifest manifest, String method) throws IOException {
        Path indexRoot = data.resolve("indices");
        Path directory = indexRoot.resolve(manifest.generation()).normalize();
        Path path = directory.resolve(manifest.files().get(method).name()).normalize();
        if (!path.startsWith(indexRoot) || !path.getParent().equals(directory)) throw new IOException("Caminho inseguro no manifesto.");
        rejectSymlink(indexRoot); rejectSymlink(directory); rejectSymlink(path);
        return path;
    }

    private String verifiedRevision(Path file, String expectedSha) throws IOException {
        rejectSymlink(file);
        if (!Files.isRegularFile(file)) throw new IOException("Base ausente. Execute: java -jar target/sylph-unopar-java-1.0.0.jar preparar");
        if (Files.size(file) > MAX_CSV_BYTES) throw new IOException("CSV excede o tamanho máximo seguro.");
        String hash = sha256(file);
        if (expectedSha != null && !expectedSha.equals(hash)) throw new IOException("CSV corrompido: hash diferente do manifesto. Recrie a base.");
        return file.toString() + ":" + hash;
    }

    static void atomicReplace(Path from, Path to) throws IOException {
        // Require an atomic commit: silently falling back would expose partial state after interruption.
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { throw new IOException("O sistema de arquivos não suporta publicação atômica do índice; a base anterior foi preservada.", ex); }
    }
    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Caminho de índice não pode ser link simbólico: " + path.getFileName());
    }
    private static void validateMethod(String method) {
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Método inválido: use recursivo ou semantico.");
    }
    private static String esc(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static String sha256(String value) { return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8))); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    static List<List<String>> records(Reader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        PushbackReader in = new PushbackReader(reader, 1);
        boolean quoted = false, afterQuote = false;
        int ch;
        while ((ch = in.read()) != -1) {
            char c = (char) ch;
            if (quoted) {
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; afterQuote = true; if (next != -1) in.unread(next); }
                } else field.append(c);
            } else if (c == ',') {
                record.add(field.toString()); field.setLength(0); afterQuote = false;
            } else if (c == '\n') {
                record.add(field.toString()); field.setLength(0); rows.add(record); record = new ArrayList<>(); afterQuote = false;
            } else if (c == '\r') {
                // Windows CRLF. CR inside quoted text is preserved above.
            } else if (afterQuote) throw new IOException("Caracteres após fechamento de aspas no CSV.");
            else if (c == '"') {
                if (!field.isEmpty()) throw new IOException("Aspas inválidas no CSV.");
                quoted = true;
            } else field.append(c);
        }
        if (quoted) throw new IOException("Campo CSV com aspas não fechadas.");
        if (!field.isEmpty() || !record.isEmpty() || afterQuote) { record.add(field.toString()); rows.add(record); }
        return rows;
    }
}
