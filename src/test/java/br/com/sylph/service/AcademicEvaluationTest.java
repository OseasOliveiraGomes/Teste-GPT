package br.com.sylph.service;

import br.com.sylph.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcademicEvaluationTest {
    @TempDir Path dir;
    final ObjectMapper mapper=new ObjectMapper();
    final SettingsService settings=mock(SettingsService.class);
    final RagService rag=mock(RagService.class);
    final HybridRetriever retriever=mock(HybridRetriever.class);
    final OllamaClient ollama=mock(OllamaClient.class);
    AcademicEvaluation evaluation() { return new AcademicEvaluation(mapper,settings,rag,retriever,ollama,dir.resolve("results")); }

    @Test void variantsExplicitlyControlChunkingProfileRerankingAndReferenceRetrieval() {
        var variants=AcademicEvaluation.variants(false);
        assertEquals(6,variants.size()); assertEquals(2,variants.stream().filter(v->v.perfil().equals("sp21")).count());
        assertTrue(variants.stream().filter(v->v.perfil().equals("sp21")).noneMatch(AcademicEvaluation.Variant::reranking));
        assertEquals(10,AcademicEvaluation.variants(true).size());
    }
    @Test void noGoldMeansNoFabricatedPrecisionRecallOrMrr() {
        var metrics=AcademicEvaluation.metrics(List.of(),List.of(),8,null);
        assertNull(metrics.precisao_evidencias());assertNull(metrics.recall_at_k());assertNull(metrics.mrr());assertNull(metrics.taxa_citacoes_validas());
    }
    @Test void goldMetricsUseDistinctRelevantEvidenceAndRank() {
        var e1=new AcademicEvaluation.Evidence("c1","d","Manual","sha",List.of(2),"a",1,0,1);
        var e2=new AcademicEvaluation.Evidence("c2","d","Manual","sha",List.of(3),"b",1,0,1);
        var metrics=AcademicEvaluation.metrics(List.of("d#3","d#4"),List.of(e1,e2),2,1d);
        assertEquals(.5,metrics.precisao_evidencias());assertEquals(.5,metrics.recall_at_k());assertEquals(.5,metrics.mrr());
    }
    @Test void rejectsDuplicateIdsAndInvalidQuestions() throws Exception {
        Path file=dir.resolve("questions.json");
        Files.writeString(file,"[{\"id\":\"1\",\"pergunta\":\"A?\"},{\"id\":\"1\",\"pergunta\":\"B?\"}]");
        assertThrows(java.io.IOException.class,()->evaluation().loadQuestions(file));
        Files.writeString(file,"[]");assertThrows(java.io.IOException.class,()->evaluation().loadQuestions(file));
    }
    @Test void successfulAuditStillLeavesHumanCorrectnessPending() throws Exception {
        var cfg=new ProjectConfig();
        String text="Quatro dias. [Fonte: Manual | Página: 4]";
        var source=new RagService.Source("Manual",4,"Quatro dias.");
        var audit=new AnswerEvidence.Audit(List.of(text),List.of(),List.of(),true);
        when(rag.answerDetailed(anyString(),eq(cfg))).thenReturn(new RagService.Execution(new RagService.Answer(text,List.of(source),true),List.of(),null,audit,null,false,2,3,4,9,List.of()));
        var result=evaluation().evaluate(new AcademicEvaluation.Question("Q1","Qual é o prazo?",List.of()),new AcademicEvaluation.Variant("sp22","recursivo",false,"hybrid"),cfg,false);
        assertNull(result.avaliacao_humana().resposta_correta());assertEquals("PENDENTE",result.avaliacao_humana().status());
        assertTrue(result.auditoria().accepted());assertNull(result.metricas().recall_at_k());assertEquals(text,result.resposta());
    }
    @Test void comparisonCheckpointsFailuresExportsUtf8AndDoesNotMutateConfig() throws Exception {
        var cfg=new ProjectConfig(); when(settings.load()).thenReturn(cfg);
        when(ollama.health(any())).thenReturn(new OllamaClient.HealthStatus(false,List.of(),List.of(),1,"offline"));
        when(retriever.retrieveDetailed(any(),anyString())).thenThrow(new java.io.IOException("Ollama indisponível"));
        Path questions=dir.resolve("questions.json"); Files.writeString(questions,"[{\"id\":\"Q1\",\"pergunta\":\"Qual é o prazo?\"}]",StandardCharsets.UTF_8);
        Path output=evaluation().run(questions,true,false,s->{});
        var result=mapper.readTree(output.resolve("resultados.json").toFile());
        assertEquals(6,result.path("resultados").size()); assertTrue(result.path("somente_recuperacao").asBoolean());
        assertEquals("PENDENTE",result.path("resultados").get(0).path("avaliacao_humana").path("status").asText());
        assertTrue(result.path("resultados").get(0).path("erros_tecnicos").toString().contains("indisponível"));
        assertTrue(Files.readString(output.resolve("resultados.csv")).startsWith("\uFEFFpergunta_id"));
        assertTrue(Files.readString(output.resolve("relatorio.md")).contains("Qual é o prazo?"));
        assertEquals("sp22",cfg.perfil_execucao);assertFalse(cfg.reranking_ativo);verifyNoInteractions(rag);
    }
}
