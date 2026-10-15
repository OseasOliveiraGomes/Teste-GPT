package br.com.sylph.service;

import br.com.sylph.config.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DevTerminal {
    private final SettingsService settings;
    private final BasePreparation prep;
    private final RagService rag;
    private final AcademicEvaluation evaluation;
    private final OllamaClient ollama;
    public DevTerminal(SettingsService settings, BasePreparation prep, RagService rag) {
        this(settings,prep,rag,null,null);
    }
    @Autowired
    public DevTerminal(SettingsService settings, BasePreparation prep, RagService rag,
                       AcademicEvaluation evaluation, OllamaClient ollama) {
        this.settings=settings; this.prep=prep; this.rag=rag;
        this.evaluation=evaluation; this.ollama=ollama;
    }

    public static void validateArguments(String[] args) {
        if (args.length == 0) return;
        switch (args[0]) {
            case "dev", "preparar", "preparar-referencia", "recriar", "documentos", "estatisticas", "diagnostico", "modelos" -> {
                if (args.length != 1) throw new IllegalArgumentException("Uso: " + args[0]);
            }
            case "modo" -> {
                if (args.length != 2 || !(args[1].equals("recursivo") || args[1].equals("semantico")))
                    throw new IllegalArgumentException("Uso: modo recursivo|semantico");
            }
            case "perguntar" -> {
                if (args.length < 2 || questionArgument(args).isBlank())
                    throw new IllegalArgumentException("Uso: perguntar \"Sua dúvida\"");
            }
            case "adicionar", "remover" -> {
                if (args.length != 2 || args[1].isBlank()) throw new IllegalArgumentException("Uso: " + args[0] + " caminho|documentoId");
            }
            case "reranking" -> {
                if (args.length != 2 || !(args[1].equals("on") || args[1].equals("off"))) throw new IllegalArgumentException("Uso: reranking on|off");
            }
            case "avaliar", "comparar" -> {
                if (args.length > 3 || args.length==3 && !args[2].equals("alternativas")) throw new IllegalArgumentException("Uso: " + args[0] + " [perguntas.json] [alternativas]");
            }
            case "avaliar-sp21" -> {
                if (args.length > 2 || args.length == 2 && args[1].isBlank()) throw new IllegalArgumentException("Uso: avaliar-sp21 [perguntas.json]");
            }
            default -> throw new IllegalArgumentException(
                "Comando inválido: " + args[0] + ". Use dev, preparar, modo ou perguntar; "
                    + "sem argumentos, inicie a consulta interativa.");
        }
    }

    public void execute(String[] args) throws IOException {
        execute(args, System.in, System.out, System.err);
    }

    void execute(String[] args, InputStream input, PrintStream out, PrintStream error) throws IOException {
        validateArguments(args);
        if (args.length == 0) { interactive(false, input, out, error); return; }
        if (args[0].equals("preparar")) { prep.prepare(); return; }
        if (advancedCommand(args[0],Arrays.copyOfRange(args,1,args.length),out)) return;
        if (args[0].equals("modo")) {
            settings.changeMode(args[1]);
            out.println("Configuração salva: " + args[1]); return;
        }
        if (args[0].equals("perguntar")) {
            printAnswer(rag.answer(questionArgument(args)), out);
            return;
        }
        interactive(true, input, out, error);
    }

    private static String questionArgument(String[] args) {
        return String.join(" ", Arrays.copyOfRange(args, 1, args.length));
    }

    private static void printAnswer(RagService.Answer answer, PrintStream out) {
        out.println("\n" + answer.resposta() + "\n");
        for (var source : answer.fontes())
            out.println("Fonte recuperada: " + source.documento() + " pág. " + source.pagina());
    }

    private void interactive(boolean developer, InputStream input, PrintStream out, PrintStream error) {
        Scanner in = new Scanner(input, StandardCharsets.UTF_8);
        String method = null;
        if (developer) {
            out.println("\nSYLPH — AMBIENTE DESENVOLVEDOR\n"
                + "Comandos: status | diagnostico | modelos | modo recursivo|semantico | reranking on|off\n"
                + "preparar | recriar | documentos | adicionar <caminho> | remover <documentoId> | estatisticas\n"
                + "perguntar | testar | trechos | falhas | comparar [perguntas.json] | avaliar [perguntas.json] | avaliar-sp21 [perguntas.json] | exportar | sair");
        } else {
            out.println("========================================\n"
                + "              SYLPH UNOPAR\n"
                + "       Tutora Virtual de Estágio\n"
                + "========================================\n");
            method = selectMethod(in, out, error);
            if (method == null) return;
            out.println("\nSylph está pronta para ajudar!\n"
                + "\nDigite sua pergunta ou 'sair' para encerrar.\n"
                + "Digite 'metodo' para trocar o método de chunking.\n");
        }
        while (true) {
            out.print(developer ? "dev> " : "Você [" + methodLabel(method) + "]: ");
            out.flush();
            if (!in.hasNextLine()) return;
            String cmd = in.nextLine().trim();
            try {
                if (cmd.equals("sair")) break;
                if (cmd.isEmpty()) continue;
                if (!developer) {
                    if (cmd.equals("ajuda")) out.println("Digite sua pergunta. Comandos: metodo | ajuda | sair.");
                    else if (cmd.equals("metodo")) {
                        method = selectMethod(in, out, error);
                        if (method == null) return;
                    } else printAnswer(rag.answer(cmd, method), out);
                }
                else if (cmd.equals("status")) {
                    out.println("Método salvo: " + settings.load().chunking_ativo + "; base pronta: " + rag.ready());
                    if (ollama!=null) out.println("Reranking: " + settings.load().reranking_ativo + "; índice: " + prep.indexStatus());
                }
                else if (cmd.equals("modo recursivo") || cmd.equals("modo semantico")) {
                    settings.changeMode(cmd.substring(5)); out.println("Método salvo: " + settings.load().chunking_ativo);
                }
                else if (cmd.equals("preparar")) prep.prepare();
                else if (cmd.equals("perguntar") || cmd.equals("testar")) {
                    out.print("Pergunta livre: ");
                    out.flush();
                    if (!in.hasNextLine()) break;
                    String question = in.nextLine().trim();
                    var answer = rag.answer(question);
                    printAnswer(answer, out);
                    if (cmd.equals("testar")) {
                        recordTest(question, answer);
                        out.println("Teste registrado. Verifique manualmente acerto e citações.");
                    }
                } else {
                    List<String> parts=new ArrayList<>(); var matcher=java.util.regex.Pattern.compile("\"([^\"]*)\"|([^\\s]+)").matcher(cmd);
                    while(matcher.find())parts.add(matcher.group(1)==null?matcher.group(2):matcher.group(1));
                    if (!advancedCommand(parts.getFirst(),parts.subList(1,parts.size()).toArray(String[]::new),out)) out.println("Comando inválido.");
                }
            } catch (Exception ex) { error.println("Falha: " + ex.getMessage()); }
        }
    }

    private boolean advancedCommand(String command, String[] args, PrintStream out) throws IOException {
        switch (command) {
            case "preparar-referencia" -> { prep.prepareBaseline(); return true; }
            case "recriar" -> { prep.rebuild(true); return true; }
            case "documentos" -> { for (var document:prep.listDocuments()) out.println(document); return true; }
            case "adicionar" -> { requireOne(command,args); prep.addDocument(Path.of(args[0])); out.println("Documento indexado."); return true; }
            case "remover" -> { requireOne(command,args); prep.removeDocument(args[0]); out.println("Documento removido do índice; arquivo de origem preservado."); return true; }
            case "estatisticas" -> { out.println(prep.chunkStatistics()); return true; }
            case "diagnostico" -> { out.println(ollama.health(settings.load())); out.println("Índice: " + prep.indexStatus()); return true; }
            case "modelos" -> { var cfg=settings.load(); out.println("LLM: "+cfg.modelo_llm+"; embeddings: "+cfg.modelo_embeddings); out.println("Instalados: "+ollama.listModels(cfg)); return true; }
            case "reranking" -> { requireOne(command,args); if (!Set.of("on","off").contains(args[0])) throw new IllegalArgumentException("Use reranking on|off"); settings.changeReranking(args[0].equals("on")); out.println("Reranking salvo: "+args[0]); return true; }
            case "comparar", "avaliar" -> { Path file=args.length==0 ? Path.of("testes","perguntas_academicas.json") : Path.of(args[0]); boolean alternatives=args.length>1 && args[1].equals("alternativas"); out.println("Resultados: "+evaluation.run(file,command.equals("comparar"),alternatives,out::println)); return true; }
            case "avaliar-sp21" -> {
                if (args.length > 1 || args.length == 1 && args[0].isBlank()) throw new IllegalArgumentException("Uso: avaliar-sp21 [perguntas.json]");
                Path file=args.length==0 ? Path.of("testes","perguntas_academicas.json") : Path.of(args[0]);
                out.println("Resultados SP 2.1: "+evaluation.runReference(file,false,out::println)); return true;
            }
            case "exportar" -> { out.println(evaluation.lastExport()==null ? "Execute comparar ou avaliar para exportar CSV, JSON e Markdown." : "Resultados já exportados: "+evaluation.lastExport()); return true; }
            case "trechos" -> { var last=rag.lastExecution(); if (last==null) out.println("Nenhuma consulta técnica nesta sessão."); else { for(var c:last.recovered()) out.printf(Locale.ROOT,"%s | %s | páginas %s | cos=%.5f bm25=%.5f score=%.5f%n%s%n",c.chunk().id(),c.chunk().documento(),c.chunk().spans().stream().map(s->s.pagina()).distinct().toList(),c.cosseno(),c.bm25(),c.scoreFinal(),c.chunk().texto()); if (last.retrieval()!=null) out.println("Reranking: "+last.retrieval().rerankingStatus()); } return true; }
            case "falhas" -> {
                var last=rag.lastExecution();
                out.println(last==null ? "Nenhuma consulta nesta sessão." : "Diagnósticos: "+last.diagnostics()+"; auditoria="+last.firstAudit()+"; reparo="+last.repairAudit());
                if (last!=null) out.printf(Locale.ROOT,
                    "Tempos (ms): recuperação=%d reranking=%d geração=%d auditoria=%d reparo=%d auditoria_reparo=%d total=%d%n",
                    last.ragRetrievalMillis(),last.rerankingMillis(),last.initialGenerationMillis(),last.initialAuditMillis(),
                    last.repairMillis(),last.repairAuditMillis(),last.totalMillis());
                out.println(ollama.diagnostics());
                for (var call:ollama.recentMetrics()) out.printf(Locale.ROOT,
                    "Ollama %s: %d ms, entrada=%d cache=%d saída=%d tokens, carga=%.1f ms, inferência=%.2f tokens/s%n",
                    call.stage(),call.milliseconds(),call.promptEvalCount(),call.promptEvalCachedCount(),call.evalCount(),
                    call.loadDurationNanos()<0 ? -1 : call.loadDurationNanos()/1_000_000d,call.outputTokensPerSecond());
                return true;
            }
            default -> { return false; }
        }
    }

    private static void requireOne(String command,String[] args) {
        if (args.length!=1 || args[0].isBlank()) throw new IllegalArgumentException("Informe um argumento para "+command);
    }

    private String selectMethod(Scanner in, PrintStream out, PrintStream error) {
        out.println("Selecione o método de chunking:\n"
            + "\n[1] Chunking Recursivo\n[2] Chunking Semântico\n[0] Sair\n");
        while (true) {
            out.print("Escolha uma opção: ");
            out.flush();
            if (!in.hasNextLine()) return null;
            String option = in.nextLine().trim();
            if (option.equals("0")) return null;
            String selected = switch (option) {
                case "1" -> "recursivo";
                case "2" -> "semantico";
                default -> null;
            };
            if (selected == null) {
                out.println("Opção inválida. Digite 1, 2 ou 0.");
            } else if (!rag.ready(selected)) {
                error.println("Base CSV ausente: dados/base_" + selected
                    + ".csv. Selecione outro método ou digite 0 para sair.");
            } else {
                out.println("\nMétodo selecionado: " + methodLabel(selected));
                return selected;
            }
        }
    }

    private static String methodLabel(String method) {
        return "recursivo".equals(method) ? "RECURSIVO" : "SEMÂNTICO";
    }

    private void recordTest(String question, RagService.Answer answer) throws IOException {
        Path file = Path.of("testes", "resultados_testes.csv");
        Files.createDirectories(file.toAbsolutePath().getParent());
        boolean exists = Files.isRegularFile(file);
        String pages = answer.fontes().stream().map(s->Integer.toString(s.pagina())).distinct().reduce((a,b)->a+";"+b).orElse("");
        String row = String.join(",", csv(OffsetDateTime.now().toString()), csv(settings.load().chunking_ativo),
            csv(question),csv(answer.resposta()),csv(pages),csv("PENDENTE"),csv("PENDENTE")) + "\n";
        if (!exists) Files.writeString(file,"data_hora,metodo,pergunta,resposta,paginas_citadas,acerto_validado,citacao_validada\n",StandardCharsets.UTF_8);
        Files.writeString(file,row,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
    }
    private String csv(String s) { return "\"" + s.replace("\"","\"\"") + "\""; }
}
