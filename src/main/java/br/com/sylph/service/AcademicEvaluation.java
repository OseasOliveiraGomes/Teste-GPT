package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.config.SettingsService;
import br.com.sylph.model.RankedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Controlled comparisons; programmatic grounding is never a human correctness judgment. */
@Service
public class AcademicEvaluation {
    public record Question(String id, String pergunta, List<String> evidencias_relevantes) {
        public Question { evidencias_relevantes = evidencias_relevantes == null ? List.of() : List.copyOf(evidencias_relevantes); }
    }
    public record Variant(String perfil, String metodo, boolean reranking, String recuperacao) { }
    public record HumanReview(Boolean resposta_correta, Boolean resposta_parcialmente_correta,
                              Boolean resposta_incorreta, Boolean informacao_omitida,
                              Boolean alucinacao_identificada, Boolean citacao_correta,
                              String observacoes, String status) {
        static HumanReview pending() { return new HumanReview(null,null,null,null,null,null,"","PENDENTE"); }
    }
    public record Evidence(String chunkId, String docId, String documento, String sha256,
                           List<Integer> paginas, String texto, double cosseno, double bm25, double score) { }
    public record Metrics(Double precisao_evidencias, Double recall_at_k, Double mrr,
                          Double taxa_citacoes_validas, String status_gabarito) { }
    public record Result(String pergunta_id, String pergunta, Variant variante, ProjectConfig configuracao,
                         String resposta, List<Evidence> evidencias, List<RagService.Source> fontes_citadas,
                         List<HybridRetriever.RetrievalScore> pontuacoes, String reranking_status,
                         AnswerEvidence.Audit auditoria, AnswerEvidence.Audit auditoria_reparo, boolean houve_reparo,
                         long tempo_recuperacao_ms, long tempo_geracao_ms, long tempo_auditoria_ms,
                         long tempo_total_ms, List<String> erros_tecnicos, Metrics metricas,
                         HumanReview avaliacao_humana) { }
    public record Run(String id, String inicio, String java_version, boolean somente_recuperacao,
                      String metodologia, List<Result> resultados) { }
    private final ObjectMapper mapper;
    private final SettingsService settings;
    private final RagService rag;
    private final HybridRetriever retriever;
    private final OllamaClient ollama;
    private Path lastExport;
    private final Path outputRoot;

    @Autowired
    public AcademicEvaluation(ObjectMapper mapper, SettingsService settings, RagService rag,
                              HybridRetriever retriever, OllamaClient ollama) {
        this(mapper,settings,rag,retriever,ollama,Path.of("resultados"));
    }
    public AcademicEvaluation(ObjectMapper mapper, SettingsService settings, RagService rag,
                              HybridRetriever retriever, OllamaClient ollama,Path outputRoot) {
        this.mapper=mapper; this.settings=settings; this.rag=rag; this.retriever=retriever; this.ollama=ollama;
        this.outputRoot=outputRoot;
    }

    public static List<Variant> variants(boolean alternatives) {
        var result = new ArrayList<Variant>();
        for (String method : List.of("recursivo","semantico")) {
            result.add(new Variant("sp21",method,false,"hybrid"));
            result.add(new Variant("sp22",method,false,"hybrid"));
            result.add(new Variant("sp22",method,true,"hybrid"));
            if (alternatives) for (String mode : List.of("vetorial","lexical")) result.add(new Variant("sp22",method,false,mode));
        }
        return List.copyOf(result);
    }

    public List<Question> loadQuestions(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file)>1_000_000) throw new IOException("Arquivo de perguntas ausente ou muito grande");
        var node = mapper.readTree(file.toFile());
        if (!node.isArray() || node.isEmpty() || node.size()>1000) throw new IOException("Perguntas devem ser uma lista JSON com 1 a 1000 itens");
        List<Question> questions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var entry : node) {
            Question q = mapper.treeToValue(entry, Question.class);
            if (q.id()==null || q.id().isBlank() || !ids.add(q.id()) || q.pergunta()==null || q.pergunta().isBlank() || q.pergunta().length()>2000) throw new IOException("ID duplicado ou pergunta inválida");
            questions.add(q);
        }
        return List.copyOf(questions);
    }

    public Path run(Path questionsFile, boolean retrievalOnly, boolean alternatives, Consumer<String> progress) throws IOException {
        return run(questionsFile,retrievalOnly,variants(alternatives),
            "Mesmas perguntas e configuração base. SP2.1: CSV legado e recuperação de referência; SP2.2: índice avançado. Métodos e reranking variam explicitamente. Auditoria não mede correção factual. Gabaritos e avaliação humana ausentes ficam PENDENTE.",progress);
    }

    /** The historic reference uses both legacy CSVs and never enables reranking. */
    public Path runReference(Path questionsFile, boolean retrievalOnly, Consumer<String> progress) throws IOException {
        return run(questionsFile,retrievalOnly,variants(false).stream().filter(v->v.perfil().equals("sp21")).toList(),
            "Referência SP 2.1: mesmas perguntas, modelos e configuração base nos CSVs legados recursivo e semântico; recuperação híbrida histórica, sem reranking. A configuração original é preservada. Auditoria não mede correção factual. Gabaritos e avaliação humana ausentes ficam PENDENTE.",progress);
    }

    private Path run(Path questionsFile, boolean retrievalOnly, List<Variant> selectedVariants,
                     String methodology, Consumer<String> progress) throws IOException {
        List<Question> questions = loadQuestions(questionsFile);
        ProjectConfig original = settings.load(); original.validar();
        ZonedDateTime start = ZonedDateTime.now(ZoneId.of("America/Fortaleza"));
        String id = start.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + "_" + UUID.randomUUID().toString().substring(0,8);
        Path directory = outputRoot.resolve(id).toAbsolutePath(); Files.createDirectories(directory);
        atomic(directory.resolve("perguntas.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(questions));
        atomic(directory.resolve("config_original.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(original));
        // Server inventory is recorded separately; absence never fabricates experimental results.
        atomic(directory.resolve("ollama.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ollama.health(original)));
        Path manifest=Path.of("dados","indice-manifesto.json");
        if (Files.isRegularFile(manifest)) Files.copy(manifest,directory.resolve("indice-manifesto.json"));
        Map<String,String> baselineHashes=new LinkedHashMap<>();
        for(String method:List.of("recursivo","semantico")) { Path csv=Path.of("dados","base_"+method+".csv"); if(Files.isRegularFile(csv))baselineHashes.put(method,CsvKnowledgeStore.sha256(csv)); }
        atomic(directory.resolve("referencia-sha256.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(baselineHashes));
        List<Result> results = new ArrayList<>();
        for (Question question : questions) for (Variant variant : selectedVariants) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Avaliação cancelada; resultados parciais preservados em " + directory);
            ProjectConfig cfg = mapper.readValue(mapper.writeValueAsBytes(original),ProjectConfig.class);
            cfg.chunking_ativo=variant.metodo(); cfg.perfil_execucao=variant.perfil();
            cfg.reranking_ativo=variant.reranking(); cfg.modo_recuperacao=variant.recuperacao(); cfg.debug_rag=false;
            progress.accept(question.id()+" — "+variant.perfil()+" / "+variant.metodo()+" / "+variant.recuperacao()+" / reranking="+variant.reranking());
            results.add(evaluate(question,variant,cfg,retrievalOnly));
            Run run = new Run(id,start.toString(),System.getProperty("java.version"),retrievalOnly,
                methodology,List.copyOf(results));
            export(directory,run); // Checkpoint after each result, including failures.
        }
        lastExport=directory;
        return directory;
    }

    Result evaluate(Question q, Variant variant, ProjectConfig cfg, boolean retrievalOnly) {
        long start=System.nanoTime();
        List<RankedChunk> recovered=List.of();
        HybridRetriever.RetrievalResult retrieval=null;
        RagService.Execution execution=null;
        List<String> errors=new ArrayList<>();
        try {
            if (retrievalOnly) { retrieval=retriever.retrieveDetailed(cfg,q.pergunta()); recovered=retrieval.chunks(); }
            else { execution=rag.answerDetailed(q.pergunta(),cfg); recovered=execution.recovered(); retrieval=execution.retrieval(); errors.addAll(execution.diagnostics()); }
        } catch (IOException | RuntimeException ex) {
            errors.add(ex.getMessage()==null ? ex.getClass().getSimpleName() : ex.getMessage());
            if (!retrievalOnly) { execution=rag.lastExecution(); if (execution!=null) { recovered=execution.recovered(); retrieval=execution.retrieval(); } }
        }
        RagService.Answer answer=execution==null ? null : execution.answer();
        String text=answer==null ? "" : answer.resposta();
        List<RagService.Source> cited=answer==null ? List.of() : answer.fontes();
        List<Evidence> evidence=recovered.stream().map(c -> new Evidence(c.chunk().id(),c.chunk().docId(),c.chunk().documento(),c.chunk().hash(),c.chunk().spans().stream().map(s->s.pagina()).distinct().toList(),c.chunk().texto(),c.cosseno(),c.bm25(),c.scoreFinal())).toList();
        Double citationRate=null;
        if (!retrievalOnly && answer!=null && !AnswerEvidence.parse(text,cited).units().isEmpty()) {
            var parsed=AnswerEvidence.parse(text,cited);
            long valid=parsed.units().stream().filter(u->!u.evidence().isEmpty()).count();
            citationRate=valid/(double)parsed.units().size();
        }
        Metrics metrics=metrics(q.evidencias_relevantes(),evidence,cfg.top_k,citationRate);
        return new Result(q.id(),q.pergunta(),variant,cfg,text,evidence,cited,
            retrieval==null ? List.of() : retrieval.scores(), retrieval==null ? "não avaliado" : retrieval.rerankingStatus(),
            execution==null ? null : execution.firstAudit(),execution==null ? null : execution.repairAudit(),execution!=null && execution.repaired(),
            execution!=null ? execution.retrievalMillis() : retrieval==null ? 0 : retrieval.retrievalMillis(),execution==null ? 0 : execution.generationMillis(),execution==null ? 0 : execution.auditMillis(),
            (System.nanoTime()-start)/1_000_000,List.copyOf(errors),metrics,HumanReview.pending());
    }

    public static Metrics metrics(List<String> relevant, List<Evidence> recovered, int k, Double citationRate) {
        if (relevant==null || relevant.isEmpty()) return new Metrics(null,null,null,citationRate,"NÃO AVALIADO: sem gabarito de evidências");
        Set<String> gold=new HashSet<>(relevant), found=new HashSet<>();
        int hits=0,rank=0;
        for (int i=0;i<Math.min(k,recovered.size());i++) {
            Evidence e=recovered.get(i); Set<String> keys=new HashSet<>(); keys.add(e.chunkId());
            for (int p:e.paginas()) { keys.add(e.docId()+"#"+p); keys.add(e.documento()+"#"+p); }
            keys.retainAll(gold);
            if (!keys.isEmpty()) { hits++; if (rank==0) rank=i+1; found.addAll(keys); }
        }
        return new Metrics(recovered.isEmpty()?0d:hits/(double)Math.min(k,recovered.size()),found.size()/(double)gold.size(),rank==0?0d:1d/rank,citationRate,"AVALIADO COM GABARITO FORNECIDO");
    }

    public Path lastExport() { return lastExport; }

    public void export(Path dir, Run run) throws IOException {
        Files.createDirectories(dir);
        atomic(dir.resolve("resultados.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(run));
        StringBuilder csv=new StringBuilder("\uFEFFpergunta_id,pergunta,perfil,metodo,recuperacao,reranking,modelo_embeddings,modelo_llm,configuracao,resposta,evidencias,fontes_citadas,pontuacoes,reranking_status,auditoria,auditoria_reparo,houve_reparo,tempo_recuperacao_ms,tempo_geracao_ms,tempo_auditoria_ms,tempo_total_ms,erros_tecnicos,precisao_evidencias,recall_at_k,mrr,taxa_citacoes_validas,resposta_correta,resposta_parcialmente_correta,resposta_incorreta,informacao_omitida,alucinacao_identificada,citacao_correta,observacoes_avaliador,status_avaliacao\r\n");
        String title = !run.resultados().isEmpty() && run.resultados().stream().allMatch(r->r.variante().perfil().equals("sp21"))
            ? "Referência SP 2.1" : "Comparação SP 2.1 e SP 2.2";
        StringBuilder md=new StringBuilder("# "+title+"\n\n"+run.metodologia()+"\n\nInício: "+run.inicio()+". Java: "+run.java_version()+". Somente recuperação: "+run.somente_recuperacao()+".\n\n");
        for (Result r:run.resultados()) {
            List<Object> cells=Arrays.asList(r.pergunta_id(),r.pergunta(),r.variante().perfil(),r.variante().metodo(),r.variante().recuperacao(),r.variante().reranking(),r.configuracao().modelo_embeddings,r.configuracao().modelo_llm,mapper.writeValueAsString(r.configuracao()),r.resposta(),mapper.writeValueAsString(r.evidencias()),mapper.writeValueAsString(r.fontes_citadas()),mapper.writeValueAsString(r.pontuacoes()),r.reranking_status(),mapper.writeValueAsString(r.auditoria()),mapper.writeValueAsString(r.auditoria_reparo()),r.houve_reparo(),r.tempo_recuperacao_ms(),r.tempo_geracao_ms(),r.tempo_auditoria_ms(),r.tempo_total_ms(),mapper.writeValueAsString(r.erros_tecnicos()),r.metricas().precisao_evidencias(),r.metricas().recall_at_k(),r.metricas().mrr(),r.metricas().taxa_citacoes_validas(),r.avaliacao_humana().resposta_correta(),r.avaliacao_humana().resposta_parcialmente_correta(),r.avaliacao_humana().resposta_incorreta(),r.avaliacao_humana().informacao_omitida(),r.avaliacao_humana().alucinacao_identificada(),r.avaliacao_humana().citacao_correta(),r.avaliacao_humana().observacoes(),r.avaliacao_humana().status());
            csv.append(cells.stream().map(AcademicEvaluation::csv).reduce((a,b)->a+","+b).orElse("")).append("\r\n");
            md.append("## ").append(r.pergunta_id()).append(" — ").append(r.variante()).append("\n\n").append(r.pergunta()).append("\n\n").append(r.resposta().isBlank()?"Resposta não gerada nesta execução.":r.resposta()).append("\n\n");
            md.append("| Documento | Chunk | Páginas | Cosseno | BM25 | Pontuação |\n|---|---|---|---:|---:|---:|\n");
            for (Evidence e:r.evidencias()) md.append("| ").append(e.documento().replace("|","\\|")).append(" | ").append(e.chunkId()).append(" | ").append(e.paginas()).append(" | ").append(e.cosseno()).append(" | ").append(e.bm25()).append(" | ").append(e.score()).append(" |\n");
            md.append("\nReranking: ").append(r.reranking_status()).append(". Recuperação: ").append(r.tempo_recuperacao_ms()).append(" ms. Geração/reparo: ").append(r.tempo_geracao_ms()).append(" ms. Auditoria: ").append(r.tempo_auditoria_ms()).append(" ms. Total: ").append(r.tempo_total_ms()).append(" ms.\n\nMétricas: ").append(r.metricas()).append(". Avaliação humana: PENDENTE.\n\nErros técnicos: ").append(r.erros_tecnicos()).append("\n\n");
        }
        md.append("## Limitações e conclusões\n\nA comparação registra evidências e latência; não conclui superioridade factual sem gabaritos e revisão humana. Cosseno mede proximidade vetorial. Uma citação verificável e uma auditoria aprovada não demonstram que a resposta está correta ou completa. As chamadas são locais, mas a geração pode variar com hardware e versão do modelo mesmo com seed e temperatura controladas.\n");
        atomic(dir.resolve("resultados.csv"),csv.toString()); atomic(dir.resolve("relatorio.md"),md.toString());
    }

    private static String csv(Object value) {
        String s=value==null ? "" : value.toString();
        if (s.matches("(?s)^[=+@-].*")) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    static void atomic(Path path,String content) throws IOException {
        Path tmp=Files.createTempFile(path.toAbsolutePath().getParent(),"export-",".tmp");
        try {
            Files.writeString(tmp,content,StandardCharsets.UTF_8);
            try { Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
