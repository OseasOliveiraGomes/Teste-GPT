package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.config.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Sp21ReferenceExecutionTest {
    @TempDir Path directory;

    @Test void referenceRunGeneratesOnlyTwoHistoricVariantsWithoutChangingSavedConfiguration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SettingsService settings = mock(SettingsService.class); RagService rag = mock(RagService.class);
        HybridRetriever retriever = mock(HybridRetriever.class); OllamaClient ollama = mock(OllamaClient.class);
        ProjectConfig original = new ProjectConfig();
        original.chunking_ativo = "semantico"; original.reranking_ativo = true;
        when(settings.load()).thenReturn(original);
        when(ollama.health(any())).thenReturn(new OllamaClient.HealthStatus(true,List.of(),List.of(),0,"test"));
        List<ProjectConfig> executed = new ArrayList<>();
        when(rag.answerDetailed(eq("Qual é o prazo?"),any())).thenAnswer(call -> {
            executed.add(call.getArgument(1));
            return new RagService.Execution(new RagService.Answer(RagService.UNKNOWN,List.of(),false),List.of(),null,
                null,null,false,1,0,0,1,List.of());
        });
        Path questions = directory.resolve("questions.json");
        Files.writeString(questions,"[{\"id\":\"Q1\",\"pergunta\":\"Qual é o prazo?\"}]",StandardCharsets.UTF_8);
        AcademicEvaluation evaluation = new AcademicEvaluation(mapper,settings,rag,retriever,ollama,directory.resolve("results"));
        Path output = evaluation.runReference(questions,false,s -> { });
        var run = mapper.readTree(output.resolve("resultados.json").toFile());
        assertEquals(2,run.path("resultados").size()); assertFalse(run.path("somente_recuperacao").asBoolean(true));
        assertEquals(List.of("recursivo","semantico"),executed.stream().map(cfg -> cfg.chunking_ativo).toList());
        for (ProjectConfig cfg : executed) {
            assertEquals("sp21",cfg.perfil_execucao); assertFalse(cfg.reranking_ativo);
            assertEquals("hybrid",cfg.modo_recuperacao); assertEquals(original.modelo_llm,cfg.modelo_llm);
            assertEquals(original.modelo_embeddings,cfg.modelo_embeddings); assertEquals(original.top_k,cfg.top_k);
        }
        assertEquals("semantico",original.chunking_ativo); assertEquals("sp22",original.perfil_execucao);
        assertTrue(original.reranking_ativo); verify(settings,only()).load(); verifyNoInteractions(retriever);
        assertTrue(Files.readString(output.resolve("relatorio.md")).startsWith("# Referência SP 2.1"));
        assertEquals(output,evaluation.lastExport());
    }

    @Test void cliDelegatesOnlyToReferenceEvaluationAndValidatesItsArguments() throws Exception {
        SettingsService settings = mock(SettingsService.class); BasePreparation prep = mock(BasePreparation.class);
        RagService rag = mock(RagService.class); AcademicEvaluation evaluation = mock(AcademicEvaluation.class);
        OllamaClient ollama = mock(OllamaClient.class);
        when(evaluation.runReference(any(),eq(false),any())).thenReturn(Path.of("resultados","sp21-test"));
        DevTerminal terminal = new DevTerminal(settings,prep,rag,evaluation,ollama);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        terminal.execute(new String[]{"avaliar-sp21"},new ByteArrayInputStream(new byte[0]),
            new PrintStream(output,true,StandardCharsets.UTF_8),System.err);
        verify(evaluation,only()).runReference(eq(Path.of("testes","perguntas_academicas.json")),eq(false),any());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Resultados SP 2.1:"));
        assertDoesNotThrow(() -> DevTerminal.validateArguments(new String[]{"avaliar-sp21","custom.json"}));
        assertThrows(IllegalArgumentException.class,() -> DevTerminal.validateArguments(new String[]{"avaliar-sp21",""}));
        assertThrows(IllegalArgumentException.class,() -> DevTerminal.validateArguments(new String[]{"avaliar-sp21","q.json","alternativas"}));
        verifyNoInteractions(settings,prep,rag,ollama);
    }
}
