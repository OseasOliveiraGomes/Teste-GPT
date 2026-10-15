package br.com.sylph.service;

import br.com.sylph.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local integration is opt-in and never assigns human correctness labels. */
@EnabledIfSystemProperty(named="sylph.academic.live",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveAcademicEvaluationTest {
    private static final ObjectMapper MAPPER=new ObjectMapper();
    private static final List<String> OUTPUTS=new ArrayList<>();
    private AcademicEvaluation evaluator() throws Exception {
        ProjectConfig cfg=new SettingsService(MAPPER).load();
        // Explicit experimental controls applied equally to every variant, never saved to config.json.
        cfg.llm_think=false; cfg.ollama_timeout_segundos=180; cfg.ollama_tentativas=1;
        SettingsService settings=mock(SettingsService.class);when(settings.load()).thenReturn(cfg);
        OllamaClient ollama=new OllamaClient(MAPPER);var health=ollama.health(cfg);
        assertTrue(health.available(),health.message()); assertTrue(health.missingModels().isEmpty(),health.message());
        var store=new CsvKnowledgeStore(MAPPER);var retrieval=new HybridRetriever(ollama,store);
        var rag=new RagService(settings,retrieval,ollama,store);
        return new AcademicEvaluation(MAPPER,settings,rag,retrieval,ollama);
    }
    @Test @Order(1) void compareTenQuestionsIncludingLexicalAndVectorReferences() throws Exception {
        Path output=evaluator().run(Path.of("testes","perguntas_academicas.json"),true,true,System.out::println);
        assertEquals(100,MAPPER.readTree(output.resolve("resultados.json").toFile()).path("resultados").size());
        OUTPUTS.add(output.toString()); record();
    }
    @Test @Order(2) void generateAndAuditAllTenQuestionsAcrossSixControlledVariants() throws Exception {
        Path output=evaluator().run(Path.of("testes","perguntas_academicas.json"),false,false,System.out::println);
        assertEquals(60,MAPPER.readTree(output.resolve("resultados.json").toFile()).path("resultados").size());
        OUTPUTS.add(output.toString());record();
    }
    private static void record() throws Exception {
        Path output=Path.of("docs","evolucao","experimentos-locais.json");Files.createDirectories(output.getParent());
        AcademicEvaluation.atomic(output,MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("diretorios",OUTPUTS,"controles",Map.of("llm_think",false,"ollama_timeout_segundos",180,"ollama_tentativas",1),"avaliacao_humana","PENDENTE")));
    }
}
