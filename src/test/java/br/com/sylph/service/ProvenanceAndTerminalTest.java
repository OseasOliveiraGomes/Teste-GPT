package br.com.sylph.service;

import br.com.sylph.config.*;
import br.com.sylph.model.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvenanceAndTerminalTest {
    @Test void crossPageChunkCannotAssignSecondPageTextToFirstPage() throws Exception {
        var cfg=new ProjectConfig(); var settings=mock(SettingsService.class); var retriever=mock(HybridRetriever.class);
        var ollama=mock(OllamaClient.class);var store=mock(CsvKnowledgeStore.class);
        when(settings.load()).thenReturn(cfg);
        var chunk=new KnowledgeChunk("stable","Manual",2,"semantico","Prazo: 4 dias.\n\nAnálise: 7 dias.",new double[]{1},List.of(new SourceSpan(2,0,14,"Prazo: 4 dias."),new SourceSpan(3,0,16,"Análise: 7 dias.")),"doc","hash","nomic-embed-text","2");
        when(retriever.retrieve(cfg,"Qual é o prazo?")).thenReturn(List.of(new RankedChunk(chunk,1,1,1)));
        when(ollama.chat(eq(cfg),anyString(),anyString())).thenReturn("Análise: 7 dias. [Trecho: stable | Página: 3]");
        when(ollama.auditAnswer(eq(cfg),anyString(),anyList(),anyString())).thenAnswer(call->{
            List<RagService.Source> sources=call.getArgument(2);assertEquals(2,sources.size());
            assertEquals("Prazo: 4 dias.",sources.get(0).trecho());assertEquals("Análise: 7 dias.",sources.get(1).trecho());
            assertFalse(sources.get(0).trecho().contains("7"));
            return new AnswerEvidence.Audit(List.of(call.<String>getArgument(3)),List.of(),List.of(),true);
        });
        var answer=new RagService(settings,retriever,ollama,store).answer("Qual é o prazo?");
        assertEquals(3,answer.fontes().getFirst().pagina());assertTrue(answer.resposta().contains("Página: 3"));
    }
    @Test void developerFeaturesDelegateAndKeepStudentMenuSimple() throws Exception {
        var settings=mock(SettingsService.class);var prep=mock(BasePreparation.class);var rag=mock(RagService.class);
        var evaluation=mock(AcademicEvaluation.class);var ollama=mock(OllamaClient.class);
        when(prep.listDocuments()).thenReturn(List.of());when(prep.chunkStatistics()).thenReturn(Map.of("recursivo",2));
        when(evaluation.run(any(),eq(true),eq(false),any())).thenReturn(Path.of("resultados","teste"));
        var terminal=new DevTerminal(settings,prep,rag,evaluation,ollama);var output=new ByteArrayOutputStream();
        terminal.execute(new String[]{"dev"},new ByteArrayInputStream("reranking on\ndocumentos\nestatisticas\ncomparar\nsair\n".getBytes(StandardCharsets.UTF_8)),new PrintStream(output,true,StandardCharsets.UTF_8),System.err);
        verify(settings).changeReranking(true);verify(prep).listDocuments();verify(prep).chunkStatistics();
        verify(evaluation).run(eq(Path.of("testes","perguntas_academicas.json")),eq(true),eq(false),any());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Resultados: resultados"));verifyNoInteractions(rag,ollama);
    }
    @Test void advancedCliValidatesRequiredArguments() {
        for(String[] invalid:List.of(new String[]{"adicionar"},new String[]{"remover",""},new String[]{"reranking","maybe"},new String[]{"avaliar","q.json","invalid"}))assertThrows(IllegalArgumentException.class,()->DevTerminal.validateArguments(invalid));
        assertDoesNotThrow(()->DevTerminal.validateArguments(new String[]{"avaliar","q.json","alternativas"}));
    }
}
