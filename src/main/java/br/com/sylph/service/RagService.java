package br.com.sylph.service;

import br.com.sylph.config.*;
import br.com.sylph.model.RankedChunk;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

@Service
public class RagService {
    public static final String UNKNOWN = "Não encontrei essa informação no material consultado. Recomendo confirmar com a coordenação do polo.";
    public record Source(String documento, int pagina, String trecho, List<String> chunkIds, String documentoId) {
        public Source(String documento, int pagina, String trecho) { this(documento, pagina, trecho, List.of(), ""); }
        public Source { chunkIds = List.copyOf(chunkIds); }
        // Preserve the historical public value identity while enriching provenance.
        @Override public boolean equals(Object other) {
            return other instanceof Source s && pagina==s.pagina && Objects.equals(documento,s.documento) && Objects.equals(trecho,s.trecho);
        }
        @Override public int hashCode() { return Objects.hash(documento,pagina,trecho); }
    }
    public record Answer(String resposta, List<Source> fontes, boolean encontrou) { }
    public record Execution(Answer answer, List<RankedChunk> recovered,
                            HybridRetriever.RetrievalResult retrieval,
                            AnswerEvidence.Audit firstAudit, AnswerEvidence.Audit repairAudit,
                            boolean repaired, long retrievalMillis, long generationMillis,
                            long auditMillis, long totalMillis, List<String> diagnostics,
                            long initialAuditMillis, long repairMillis, long repairAuditMillis,
                            int contextCharacters, String generatedText, String repairedText) {
        public Execution(Answer answer, List<RankedChunk> recovered, HybridRetriever.RetrievalResult retrieval,
                         AnswerEvidence.Audit firstAudit, AnswerEvidence.Audit repairAudit, boolean repaired,
                         long retrievalMillis, long generationMillis, long auditMillis, long totalMillis,
                         List<String> diagnostics) {
            this(answer,recovered,retrieval,firstAudit,repairAudit,repaired,retrievalMillis,generationMillis,
                auditMillis,totalMillis,diagnostics,auditMillis,0,0,0,"","");
        }
        /** Historical aggregate fields remain compatible; these stages do not overlap. */
        public long ragRetrievalMillis() { return Math.max(0,retrievalMillis-rerankingMillis()); }
        public long rerankingMillis() { return retrieval == null ? 0 : retrieval.rerankingMillis(); }
        public long initialGenerationMillis() { return Math.max(0,generationMillis-repairMillis); }
    }
    private static final class Trace {
        List<RankedChunk> recovered = List.of();
        HybridRetriever.RetrievalResult retrieval;
        AnswerEvidence.Audit first, second;
        boolean repaired;
        long retrievalMs, generationMs, auditMs, firstAuditMs, repairMs, secondAuditMs;
        int contextCharacters;
        String generatedText = "";
        String repairedText = "";
        final List<String> diagnostics = new ArrayList<>();
    }
    private final ThreadLocal<Trace> trace = new ThreadLocal<>();
    private final ThreadLocal<Execution> last = new ThreadLocal<>();

    private final SettingsService settings;
    private final HybridRetriever retriever;
    private final OllamaClient ollama;
    private final CsvKnowledgeStore store;
    public RagService(SettingsService settings, HybridRetriever retriever, OllamaClient ollama,
                      CsvKnowledgeStore store) {
        this.settings = settings; this.retriever = retriever; this.ollama = ollama; this.store = store;
    }

    public boolean ready() {
        try { return ready(settings.load().chunking_ativo); }
        catch (Exception e) { return false; }
    }

    public boolean ready(String method) {
        try { return java.nio.file.Files.isRegularFile(store.file(method)); }
        catch (Exception e) { return false; }
    }

    public Answer answer(String question) throws IOException {
        return answer(question, null);
    }

    public Answer answer(String question, String method) throws IOException {
        return execute(question, method, null);
    }

    public Execution answerDetailed(String question, ProjectConfig config) throws IOException {
        execute(question, config.chunking_ativo, config);
        return last.get();
    }

    public Execution lastExecution() { return last.get(); }

    private Answer execute(String question, String method, ProjectConfig supplied) throws IOException {
        long start = System.nanoTime();
        Trace current = new Trace();
        trace.set(current);
        last.remove();
        Answer result = null;
        try { result = answerInternal(question, method, supplied); return result; }
        catch (IOException | RuntimeException ex) { current.diagnostics.add(ex.getMessage()); throw ex; }
        finally {
            last.set(new Execution(result, current.recovered, current.retrieval, current.first, current.second,
                current.repaired, current.retrievalMs, current.generationMs, current.auditMs,
                (System.nanoTime()-start)/1_000_000, List.copyOf(current.diagnostics),
                current.firstAuditMs,current.repairMs,current.secondAuditMs,current.contextCharacters,current.generatedText,current.repairedText));
            trace.remove();
        }
    }

    private Answer answerInternal(String question, String method, ProjectConfig supplied) throws IOException {
        if (question == null || question.isBlank() || question.length() > 2000)
            throw new IllegalArgumentException("Digite uma pergunta de até 2000 caracteres.");
        if (method != null && !"recursivo".equals(method) && !"semantico".equals(method))
            throw new IllegalArgumentException("Use 'recursivo' ou 'semantico'.");
        var socialReply = SocialInteraction.replyTo(question);
        // Interação social não representa evidência encontrada no manual.
        if (socialReply.isPresent()) return new Answer(socialReply.get(), List.of(), false);
        ProjectConfig cfg = supplied == null ? settings.load() : supplied;
        // load() entrega uma configuração nova; a escolha da sessão não é salva em disco.
        if (method != null) cfg.chunking_ativo = method;
        Trace current = trace.get();
        long started = System.nanoTime();
        HybridRetriever.clearLastResult();
        List<RankedChunk> top;
        try { top = retriever.retrieve(cfg, question.trim()); }
        finally { current.retrievalMs = (System.nanoTime()-started)/1_000_000; }
        current.recovered = List.copyOf(top);
        current.retrieval = HybridRetriever.lastResult();
        if(current.retrieval!=null && !current.retrieval.rerankingReason().isBlank())current.diagnostics.add("Reranking: "+current.retrieval.rerankingReason());
        if (top.isEmpty()) { if (cfg.debug_rag) System.out.println("[RAG] final=UNKNOWN motivo=recuperacao_vazia"); return new Answer(FinalAnswerPresentation.unknown(question), List.of(), false); }
        var prepared = EvidenceContext.prepare(top);
        StringBuilder context = new StringBuilder(prepared.text());
        List<Source> sources = prepared.sources();
        if (cfg.debug_rag) for (RankedChunk r : top)
            System.out.println("[RAG] Qwen doc=" + r.chunk().documento() + " pagina=" + r.chunk().pagina() + " chunk=" + r.chunk().id());
        var conflicts=EvidenceConflicts.detect(sources);
        if(!conflicts.isEmpty()) {
            current.diagnostics.add("Conflitos quantitativos potenciais: "+conflicts);
            context.append("AVISO DA APLICAÇÃO: há regras com a mesma redação e quantidades diferentes: ").append(conflicts)
                .append(". Apresente a divergência, sem escolher um valor por suposição; oriente consulta à coordenação.\n");
        }
        started = System.nanoTime();
        current.contextCharacters = context.length();
        String generated;
        try { generated = ollama.chat(cfg, question.trim(), context.toString()); }
        finally { current.generationMs = (System.nanoTime()-started)/1_000_000; }
        generated = StableCitations.resolve(generated, sources);
        current.generatedText = generated;
        if (cfg.debug_rag) System.out.println("[RAG] geracao_bruta=" + generated);
        started = System.nanoTime();
        AnswerEvidence.Audit audit;
        try { audit = ollama.auditAnswer(cfg, question.trim(), sources, generated); }
        finally { current.firstAuditMs = (System.nanoTime()-started)/1_000_000; current.auditMs += current.firstAuditMs; }
        current.first = audit;
        if (cfg.debug_rag) System.out.println("[RAG] primeira_decisao=" + audit);
        String validated = String.join("\n\n", audit.approved());
        List<String> finalApproved = new ArrayList<>(audit.approved());
        String presentationNotice = "";
        if (!audit.accepted()) {
            current.repaired = true;
            if (cfg.debug_rag) System.out.println("[RAG] reparo=iniciado motivos=" + audit.issues() + " faltantes=" + audit.missing());
            try {
                started = System.nanoTime();
                String repaired;
                try { repaired = ollama.repairAnswer(cfg, question.trim(), sources, generated, audit); }
                finally { current.repairMs = (System.nanoTime()-started)/1_000_000; current.generationMs += current.repairMs; }
                repaired = StableCitations.resolve(repaired, sources);
                current.repairedText = repaired;
                if (cfg.debug_rag) System.out.println("[RAG] reparo_bruto=" + repaired);
                started = System.nanoTime();
                AnswerEvidence.Audit second;
                try { second = ollama.auditAnswer(cfg, question.trim(), sources, repaired); }
                finally { current.secondAuditMs = (System.nanoTime()-started)/1_000_000; current.auditMs += current.secondAuditMs; }
                current.second = second;
                if (cfg.debug_rag) System.out.println("[RAG] reparo_decisao=" + second);
                if (second.accepted()) {
                    validated = String.join("\n\n", second.approved());
                    finalApproved = new ArrayList<>(second.approved());
                }
                else {
                    // Keep approved original units; a later failed audit must not erase their evidence.
                    LinkedHashSet<String> preserved = new LinkedHashSet<>(audit.approved());
                    preserved.addAll(second.approved());
                    validated = String.join("\n\n", preserved);
                    finalApproved = new ArrayList<>(preserved);
                    presentationNotice = FinalAnswerPresentation.notice(question, second.missing(),second.issues(),false);
                }
                if (validated.isBlank()) {
                    // Distinguish absence reported by the model from technical/semantic validation failure.
                    boolean noInformation = generated.trim().equals(UNKNOWN) && repaired.trim().equals(UNKNOWN);
                    String failure = noInformation ? FinalAnswerPresentation.unknown(question) :
                        "Não foi possível validar uma resposta com segurança. " + presentationNotice;
                    if (cfg.debug_rag) System.out.println("[RAG] final_sem_resposta motivo=" + (noInformation ? "ausencia_reportada" : "falha_validacao") + " resposta=" + failure);
                    return new Answer(failure, List.of(), false);
                }
            } catch (IOException ex) {
                current.diagnostics.add("Falha de reparo: " + ex.getMessage());
                if (cfg.debug_rag) System.out.println("[RAG] reparo_falha_tecnica=" + ex.getMessage());
                if (validated.isBlank()) throw ex;
                presentationNotice = FinalAnswerPresentation.notice(question,audit.missing(),audit.issues(),true);
            }
        }
        // All model calls and evidence decisions used the original text above.
        // Only the student-facing rendering is cleaned/consolidated here.
        generated = FinalAnswerPresentation.render(finalApproved,sources);
        if (!presentationNotice.isBlank()) generated += "\n\n" + presentationNotice;
        if (cfg.debug_rag) System.out.println("[RAG] final_aprovado=" + generated);
        List<Source> cited = AnswerEvidence.parse(generated, sources).units().stream()
            .flatMap(u -> u.evidence().stream()).distinct().toList();
        return new Answer(generated, cited, true);
    }

}
