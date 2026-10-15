package br.com.sylph.service;

import br.com.sylph.config.*;
import br.com.sylph.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class BasePreparation {
    private static final String PRINCIPAL = "Manual Estagio Obrigatorio UNOPAR 2026-2.pdf";
    private static final List<String> METHODS = List.of("recursivo", "semantico");
    private final PdfPages pdf;
    private final TextChunker chunker;
    private final OllamaClient ollama;
    private final CsvKnowledgeStore store;
    private final SettingsService settings;
    public BasePreparation(PdfPages pdf, TextChunker chunker, OllamaClient ollama,
                           CsvKnowledgeStore store, SettingsService settings) {
        this.pdf = pdf; this.chunker = chunker; this.ollama = ollama; this.store = store; this.settings = settings;
    }

    /** First run discovers supported local files; updates process only registered documents. */
    public synchronized void prepare() throws IOException { rebuild(false); }

    /** Frozen page-based algorithms; original legacy CSVs always take precedence and are never overwritten. */
    public synchronized void prepareBaseline() throws IOException {
        Path principal=store.dataDirectory().resolve(PRINCIPAL);
        if(!Files.isRegularFile(principal))throw new IOException("Manual principal ausente para preparar a referência SP 2.1.");
        CsvKnowledgeStore referenceStore=new CsvKnowledgeStore(new com.fasterxml.jackson.databind.ObjectMapper(),store.dataDirectory().resolve("referencia"));
        BasePreparation reference=new BasePreparation(pdf,chunker,ollama,referenceStore,settings);
        reference.withLock(()->reference.build(settings.load(),List.of(principal),false,referenceStore.manifest(),true));
    }

    public synchronized void rebuild(boolean force) throws IOException {
        withLock(() -> {
            IndexManifest manifest;
            try { manifest = store.manifest(); }
            catch(IOException ex) {
                if(!force)throw ex;
                Path backup=store.dataDirectory().resolve("manifesto-corrompido-"+UUID.randomUUID()+".json.bak");
                Files.copy(store.manifestFile(),backup);
                manifest=null;
                System.out.println("Manifesto inválido preservado para diagnóstico. Recriando a partir dos documentos em dados; arquivos externos devem ser adicionados novamente.");
            }
            List<Path> paths = manifest == null ? discover() : manifest.documents().stream().map(d -> Path.of(d.sourcePath())).toList();
            if (paths.isEmpty()) throw new IOException("Nenhum documento registrado. Use adicionar com um PDF, TXT ou Markdown.");
            build(settings.load(), paths, force, manifest);
        });
    }

    /** Explicit selection authorizes reading a local path outside dados; no source is copied or deleted. */
    public synchronized void addDocument(Path selected) throws IOException {
        withLock(() -> {
            ProjectConfig cfg = settings.load();
            Path source = validateSource(selected, cfg);
            IndexManifest manifest = store.manifest();
            LinkedHashSet<Path> paths = new LinkedHashSet<>();
            if (manifest == null) paths.addAll(discover());
            else manifest.documents().forEach(d -> paths.add(Path.of(d.sourcePath())));
            paths.add(source);
            build(cfg, List.copyOf(paths), false, manifest);
        });
    }

    /** Removal commits new index references only, retaining source and every previous generation. */
    public synchronized void removeDocument(String docId) throws IOException {
        withLock(() -> {
            IndexManifest manifest = store.manifest();
            if (manifest == null) throw new IOException("Base legada sem manifesto. Execute preparar para migrar com segurança.");
            if (docId == null || manifest.documents().stream().noneMatch(d -> d.docId().equals(docId)))
                throw new IOException("Identificador de documento não encontrado no índice.");
            Map<String, List<KnowledgeChunk>> bases = new LinkedHashMap<>();
            for (String method : METHODS) bases.put(method, store.read(method).stream().filter(c -> !c.docId().equals(docId)).toList());
            store.commit(manifest.embeddingModel(), manifest.chunkingFingerprint(),
                manifest.documents().stream().filter(d -> !d.docId().equals(docId)).toList(), bases);
            System.out.println("Documento removido apenas do índice; arquivo original e gerações anteriores preservados.");
        });
    }

    public List<IndexedDocument> listDocuments() throws IOException { return store.listDocuments(); }

    public Map<String, Object> indexStatus() throws IOException {
        IndexManifest manifest = store.manifest();
        if (manifest == null) return Map.of("tipo", "legado", "documentos", "metadados pendentes de migração", "diretorio", store.dataDirectory().toString());
        return Map.of("tipo", "versionado", "geracao", manifest.generation(), "modelo_embeddings", manifest.embeddingModel(),
            "dimensao", manifest.dimension(), "documentos", manifest.documents().size(), "criado_em", manifest.createdAt());
    }

    public Map<String, Object> chunkStatistics() throws IOException {
        Map<String, Object> results = new LinkedHashMap<>();
        for (String method : METHODS) {
            List<KnowledgeChunk> chunks = store.read(method);
            IntSummaryStatistics sizes = chunks.stream().mapToInt(c -> c.texto().length()).summaryStatistics();
            long pages = chunks.stream().flatMap(c -> c.spans().stream().map(s -> c.docId() + ":" + s.pagina())).distinct().count();
            results.put(method, Map.of("quantidade", chunks.size(), "minimo", sizes.getMin(), "maximo", sizes.getMax(),
                "media", sizes.getAverage(), "paginas_cobertas", pages, "documentos", chunks.stream().map(KnowledgeChunk::docId).distinct().count()));
        }
        return results;
    }

    private void build(ProjectConfig cfg, List<Path> requested, boolean force, IndexManifest previous) throws IOException {
        build(cfg,requested,force,previous,false);
    }
    private void build(ProjectConfig cfg, List<Path> requested, boolean force, IndexManifest previous, boolean baseline) throws IOException {
        String fingerprint = baseline ? CsvKnowledgeStore.sha256("baseline-v1|"+cfg.modelo_embeddings+"|"+cfg.limiar_semantico) : fingerprint(cfg);
        Map<String, IndexedDocument> previousDocs = new HashMap<>();
        Map<String, List<KnowledgeChunk>> old = new LinkedHashMap<>();
        Map<String, double[]> embeddingCache = new HashMap<>();
        if (previous != null && !force) {
            previous.documents().forEach(d -> previousDocs.put(d.docId(), d));
            for (String method : METHODS) {
                List<KnowledgeChunk> chunks = previous.files().get(method).chunks() == 0 ? List.of() : store.read(method);
                old.put(method, chunks);
                if (previous.embeddingModel().equals(cfg.modelo_embeddings))
                    chunks.forEach(c -> embeddingCache.put(c.texto(), c.embedding()));
            }
        }
        Map<String, List<KnowledgeChunk>> bases = new LinkedHashMap<>();
        METHODS.forEach(m -> bases.put(m, new ArrayList<>()));
        List<IndexedDocument> documents = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> registered = new HashSet<>();
        boolean changed = force || previous == null || !fingerprint.equals(previous.chunkingFingerprint());
        for (Path requestedPath : requested) {
            Path source = validateSource(requestedPath, cfg);
            if (!names.add(source.getFileName().toString().toLowerCase(Locale.ROOT))) throw new IOException("Documentos com o mesmo nome são ambíguos nas citações. Renomeie uma cópia antes de adicionar.");
            String docId = "doc-" + CsvKnowledgeStore.sha256(source.toString()).substring(0, 24);
            if (!registered.add(docId)) continue;
            String hash = CsvKnowledgeStore.sha256(source);
            IndexedDocument existing = previousDocs.get(docId);
            boolean unchanged = !force && previous != null && fingerprint.equals(previous.chunkingFingerprint())
                && existing != null && existing.sha256().equals(hash);
            if (unchanged) {
                documents.add(existing);
                for (String method : METHODS) bases.get(method).addAll(old.get(method).stream().filter(c -> c.docId().equals(docId)).toList());
                System.out.println("Sem alterações: " + existing.originalName() + " (embeddings reaproveitados).");
                continue;
            }
            changed = true;
            DocumentText content = readDocument(source, cfg);
            IndexedDocument document = new IndexedDocument(docId, source.getFileName().toString(), hash, content.pages(), source.toString(),
                source.getFileName().toString().equals(PRINCIPAL));
            documents.add(document);
            long recursiveStarted = System.nanoTime();
            List<TextChunker.Segment> recursive = baseline ? chunker.recursiveBaseline(cfg,content.text()) : chunker.recursive(cfg, content.text());
            long recursiveMillis = (System.nanoTime() - recursiveStarted) / 1_000_000;
            long semanticStarted = System.nanoTime();
            List<TextChunker.Segment> semantic = baseline ? chunker.semanticBaseline(cfg,content.text(),ollama) : chunker.semantic(cfg, content.text(), ollama);
            long semanticMillis = (System.nanoTime() - semanticStarted) / 1_000_000;
            if (recursive.isEmpty() || semantic.isEmpty()) throw new IOException("Documento sem chunks: " + document.originalName());
            bases.get("recursivo").addAll(embed(cfg, document, "recursivo", recursive, embeddingCache));
            bases.get("semantico").addAll(embed(cfg, document, "semantico", semantic, embeddingCache));
            if (!hash.equals(CsvKnowledgeStore.sha256(source))) throw new IOException("Documento alterado durante o processamento; índice anterior preservado.");
            ChunkingReport.Comparison report = ChunkingReport.compare(content.text(), recursive, semantic, recursiveMillis, semanticMillis);
            ChunkingReport.export(store.dataDirectory().resolve("relatorios"), "chunking-" + docId + "-" + hash.substring(0, 12), report);
            System.out.println(document.originalName() + ": " + recursive.size() + " chunks recursivos, " + semantic.size() + " semânticos; "
                + content.text().size() + " páginas com texto.");
        }
        if (previous != null && previous.documents().size() != documents.size()) changed = true;
        if (!changed) { System.out.println("Índice já atualizado; nenhuma chamada de embeddings foi necessária."); return; }
        IndexManifest committed = store.commit(cfg.modelo_embeddings, fingerprint, documents, bases);
        System.out.println("Bases publicadas com segurança: " + committed.generation() + ". Recursivo: " + bases.get("recursivo").size()
            + " chunks; semântico: " + bases.get("semantico").size() + " chunks.");
    }

    private List<KnowledgeChunk> embed(ProjectConfig cfg, IndexedDocument doc, String method, List<TextChunker.Segment> segments,
                                        Map<String, double[]> cache) throws IOException {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        segments.stream().map(TextChunker.Segment::text).filter(t -> !cache.containsKey(t)).forEach(missing::add);
        List<String> texts = List.copyOf(missing);
        for (int from = 0; from < texts.size(); from += 12) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Preparação cancelada; base anterior preservada.");
            List<String> batch = texts.subList(from, Math.min(from + 12, texts.size()));
            List<double[]> vectors = ollama.embed(cfg, batch);
            if (vectors == null || vectors.size() != batch.size()) throw new IOException("Quantidade de embeddings inválida.");
            for (int i = 0; i < batch.size(); i++) {
                double[] vector = vectors.get(i);
                if (vector == null || vector.length == 0) throw new IOException("Embedding vazio.");
                for (double value : vector) if (!Double.isFinite(value)) throw new IOException("Embedding não finito.");
                cache.put(batch.get(i), vector.clone());
            }
        }
        List<KnowledgeChunk> results = new ArrayList<>();
        for (TextChunker.Segment segment : segments) {
            String provenance = segment.spans().stream().map(s -> s.pagina() + ":" + s.inicio() + ":" + s.fim()).reduce((a, b) -> a + ";" + b).orElse("");
            String id = (method.equals("recursivo") ? "R-" : "S-") + doc.docId() + "-"
                + CsvKnowledgeStore.sha256(method + "\n" + provenance + "\n" + segment.text()).substring(0, 24);
            results.add(new KnowledgeChunk(id, doc.originalName(), segment.spans().getFirst().pagina(), method, segment.text(), cache.get(segment.text()),
                segment.spans(), doc.docId(), doc.sha256(), cfg.modelo_embeddings, "2"));
        }
        return results;
    }

    private record DocumentText(List<PdfPages.Page> text, int pages) { }
    private DocumentText readDocument(Path source, ProjectConfig cfg) throws IOException {
        List<PdfPages.Page> pages;
        int totalPages;
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            totalPages = pdf.pageCount(source);
            pages = pdf.extract(source, cfg.max_documento_caracteres);
        } else {
            String text = Files.readString(source, StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
            pages = text.isBlank() ? List.of() : List.of(new PdfPages.Page(1, text));
            totalPages = 1; // TXT and Markdown have one logical page, explicitly reported.
        }
        long characters = pages.stream().mapToLong(p -> p.text().length()).sum();
        if (characters > cfg.max_documento_caracteres) throw new IOException("Texto extraído excede max_documento_caracteres.");
        if (pages.isEmpty()) throw new IOException("Não foi possível extrair conteúdo textual: " + source.getFileName());
        return new DocumentText(pages, totalPages);
    }

    private Path validateSource(Path selected, ProjectConfig cfg) throws IOException {
        if (selected == null) throw new IOException("Caminho do documento ausente.");
        Path source = selected.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IOException("Documento local não encontrado: " + source.getFileName());
        source = source.toRealPath();
        String name = source.getFileName().toString();
        if (name.length() > 255 || name.chars().anyMatch(c -> c<32 || "[]|".indexOf(c)>=0)) throw new IOException("Nome do documento contém delimitadores de citação inseguros.");
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")))
            throw new IOException("Formato não suportado: use PDF, TXT ou Markdown.");
        if (Files.size(source) > cfg.max_documento_bytes) throw new IOException("Documento excede max_documento_bytes; arquivo original preservado.");
        return source;
    }

    private List<Path> discover() throws IOException {
        if (!Files.isDirectory(store.dataDirectory())) throw new IOException("Diretório dados ausente; adicione um documento local.");
        try (var stream = Files.list(store.dataDirectory())) {
            return stream.filter(Files::isRegularFile).filter(p -> {
                String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
            }).sorted(Comparator.comparing(p -> p.getFileName().toString().equals(PRINCIPAL) ? "" : p.getFileName().toString())).toList();
        }
    }

    public static String fingerprint(ProjectConfig cfg) {
        return CsvKnowledgeStore.sha256(TextChunker.ALGORITHM_VERSION + "|" + cfg.chunk_max_chars + "|" + cfg.chunk_min_chars + "|"
            + cfg.chunk_overlap_chars + "|" + cfg.chunk_cross_pages + "|" + cfg.semantic_unit_chars + "|" + cfg.limiar_semantico + "|" + cfg.modelo_embeddings);
    }

    @FunctionalInterface private interface Action { void run() throws IOException; }
    private void withLock(Action action) throws IOException {
        Files.createDirectories(store.dataDirectory());
        Path lockPath = store.dataDirectory().resolve("indice.lock");
        if (Files.isSymbolicLink(lockPath)) throw new IOException("Caminho de lock inseguro.");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException ex) { throw new IOException("Outra preparação do índice está em andamento.", ex); }
            if (lock == null) throw new IOException("Outra preparação do índice está em andamento.");
            try (lock) { action.run(); }
        }
    }
}
