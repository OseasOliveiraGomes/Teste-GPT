package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {
    @Test void sharedAuditPayloadPreservesEveryUnitAndItsFullEvidenceWithoutRepeatingPages() throws Exception {
        var mapper = new ObjectMapper();
        String longPage = "O aluno não pode enviar sem aprovação expressa do orientador. ".repeat(100);
        var first = new RagService.Source("Manual", 7, longPage, java.util.List.of("chunk-7"), "doc-1");
        var second = new RagService.Source("Manual", 8,
            "O coordenador deve assinar somente após a conferência; o aluno é o destinatário.", java.util.List.of("chunk-8"), "doc-1");
        var distinctFragment = new RagService.Source("Manual", 7,
            "A dispensa não altera a obrigação de aprovação.", java.util.List.of("chunk-7b"), "doc-1");
        var units = java.util.List.of(
            new AnswerEvidence.Unit(1, "Unidade original 1.", java.util.List.of(first)),
            new AnswerEvidence.Unit(2, "Unidade original 2.", java.util.List.of(first, second)),
            new AnswerEvidence.Unit(3, "Unidade original 3.", java.util.List.of(distinctFragment, first)),
            new AnswerEvidence.Unit(4, "Unidade original 4.", java.util.List.of(first)));
        var parsed = new AnswerEvidence.Parsed(units, java.util.List.of());
        var payload = mapper.valueToTree(OllamaClient.buildAuditPayload("pergunta", parsed));
        assertEquals("pergunta", payload.path("question").asText());
        assertEquals(3, payload.path("evidence").size(), "Trechos diferentes da mesma página devem continuar disponíveis");
        var sourcesById = new java.util.HashMap<Integer, com.fasterxml.jackson.databind.JsonNode>();
        for (var entry : payload.path("evidence")) sourcesById.put(entry.path("id").asInt(), entry.path("source"));
        for (int i = 0; i < units.size(); i++) {
            var original = units.get(i); var sent = payload.path("units").get(i);
            assertEquals(original.id(), sent.path("id").asInt());
            assertEquals(original.text(), sent.path("text").asText());
            var reconstructed = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for (var reference : sent.path("evidence")) reconstructed.add(sourcesById.get(reference.asInt()));
            var expected = original.evidence().stream().map(mapper::valueToTree).toList();
            assertEquals(expected, reconstructed,
                "Cada unidade precisa conservar exatamente suas fontes, inclusive texto, condições e proveniência");
        }
        String compact = mapper.writeValueAsString(payload);
        String repeated = mapper.writeValueAsString(java.util.Map.of("question", "pergunta", "units", units));
        assertTrue(compact.length() < repeated.length() / 2, "Páginas repetidas devem ser transmitidas uma vez");
        var otherProvenance = new RagService.Source(first.documento(), first.pagina(), first.trecho(),
            java.util.List.of("outro-chunk"), "outro-documento");
        var separate = mapper.valueToTree(OllamaClient.buildAuditPayload("pergunta", new AnswerEvidence.Parsed(
            java.util.List.of(new AnswerEvidence.Unit(1, "Mesma página, proveniências diferentes.",
                java.util.List.of(first, otherProvenance))), java.util.List.of())));
        assertEquals(2, separate.path("evidence").size(), "Metadados de proveniência distintos não devem ser descartados");
    }

    @Test void semanticAuditFailsClosedAndChecksCoverageWithThinkFalse() throws Exception {
        var mapper = new ObjectMapper();
        var verdict = new AtomicReference<String>();
        var request = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = mapper.writeValueAsBytes(java.util.Map.of("message", java.util.Map.of("content", verdict.get())));
            exchange.sendResponseHeaders(200, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        server.start();
        try {
            var cfg = new ProjectConfig();
            cfg.ollama_url = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new OllamaClient(mapper);
            var sources = java.util.List.of(new RagService.Source("Manual",1,"Texto comprovado."));
            String answer = "Texto comprovado. [Fonte: Manual | Página: 1]";
            for (String bad : java.util.List.of("invalid", "{}", "{\"claims\":[],\"missing\":[]}",
                "{\"claims\":[{\"id\":1,\"supported\":false,\"reason\":\"Nao consta\",\"quotes\":[]}],\"missing\":[]}",
                "{\"claims\":[{\"id\":1,\"supported\":\"true\",\"quotes\":[]}],\"missing\":[]}")) {
                verdict.set(bad);
                assertFalse(client.auditAnswer(cfg, "question", sources, answer).accepted());
            }
            verdict.set("{\"claims\":[{\"id\":1,\"supported\":true,\"reason\":\"Explicito\",\"quotes\":[{\"documento\":\"Manual\",\"pagina\":1,\"texto\":\"Texto comprovado.\"}]}],\"missing\":[]}");
            assertTrue(client.auditAnswer(cfg, "question", sources, answer).accepted());
            var payload = mapper.readTree(request.get());
            assertTrue(payload.path("think").isBoolean());
            assertFalse(payload.path("think").asBoolean(true));
            assertEquals("json", payload.path("format").asText());
            assertTrue(payload.path("messages").get(0).path("content").asText().contains("SOMENTE suas paginas citadas"));
        } finally { server.stop(0); }
    }

    @Test void repairBindsLiteralEvidenceToItsRealCitationAndRejectsFabrication() throws Exception {
        var mapper = new ObjectMapper();
        var request = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = mapper.writeValueAsBytes(java.util.Map.of("message",java.util.Map.of("content",body.get())));
            exchange.sendResponseHeaders(200,reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        server.start();
        try {
            var cfg = new ProjectConfig();
            cfg.ollama_url = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new OllamaClient(mapper);
            var sources = java.util.List.of(new RagService.Source("Manual",8,"Cadastro correto."),new RagService.Source("Manual",9,"Assinatura correta."));
            var audit = new AnswerEvidence.Audit(java.util.List.of(),java.util.List.of("Fonte errada"),java.util.List.of(),true);
            body.set("{\"items\":[{\"evidenceId\":1,\"text\":\"Cadastro correto.\"},{\"evidenceId\":2,\"text\":\"Assinatura correta.\"},{\"evidenceId\":2,\"text\":\"Inventado\"},{\"evidenceId\":99,\"text\":\"Fonte falsa\"}],\"missing\":[]}");
            String repaired = client.repairAnswer(cfg,"question",sources,"previous",audit);
            assertTrue(repaired.contains("P\u00e1gina: 8]"));
            assertTrue(repaired.contains("P\u00e1gina: 9]"));
            assertFalse(repaired.contains("Inventado"));
            assertFalse(repaired.contains("Fonte falsa"));
            var payload = mapper.readTree(request.get());
            assertTrue(payload.path("think").isBoolean());
            assertFalse(payload.path("think").asBoolean(true));
            var schema = payload.path("format");
            assertEquals("object", schema.path("type").asText());
            assertFalse(schema.path("additionalProperties").asBoolean(true));
            assertEquals(mapper.readTree("[\"ids\",\"missing\"]"), schema.path("required"));
            assertEquals(2, schema.path("properties").size());
            var ids = schema.path("properties").path("ids");
            assertEquals("array", ids.path("type").asText()); assertTrue(ids.path("uniqueItems").asBoolean());
            assertEquals(2, ids.path("maxItems").asInt());
            assertEquals("integer", ids.path("items").path("type").asText());
            assertEquals(1, ids.path("items").path("minimum").asInt());
            assertEquals(2, ids.path("items").path("maximum").asInt());
            assertEquals("string", schema.path("properties").path("missing").path("items").path("type").asText());
            // Even if a server ignores the schema, it cannot supply the rendered factual text.
            body.set("{\"ids\":[2,999],\"missing\":[],\"text\":\"Responsabilidade inventada pelo modelo.\"}");
            assertEquals("Assinatura correta. [Fonte: Manual | Página: 9]",
                client.repairAnswer(cfg,"question",sources,"previous",audit));
            body.set("{\"ids\":[{\"id\":1,\"text\":\"Texto inventado.\"}],\"missing\":[]}");
            assertFalse(client.repairAnswer(cfg,"question",sources,"previous",audit).contains("Texto inventado"));
            body.set("{\"items\":[],\"missing\":[\"Sem informacao\"]}");
            assertEquals(RagService.UNKNOWN,client.repairAnswer(cfg,"question",sources,"previous",audit));
        } finally { server.stop(0); }
    }

    @Test void sendsGroundingRulesWithThinkTrueAndReturnsOnlyContent() throws Exception {
        var mapper = new ObjectMapper();
        var request = new AtomicReference<String>();
        var responseMessage = new AtomicReference<java.util.Map<String, String>>(java.util.Map.of(
            "content", "  Resposta de teste  ",
            "thinking", "Campo separado que não deve aparecer na resposta ao aluno."));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = mapper.writeValueAsBytes(java.util.Map.of("message", responseMessage.get()));
            exchange.sendResponseHeaders(200, reply.length);
            try (var out = exchange.getResponseBody()) { out.write(reply); }
        });
        server.start();
        try {
            var cfg = new ProjectConfig();
            cfg.ollama_url = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new OllamaClient(mapper);
            assertEquals("Resposta de teste", client.chat(cfg, "Cadastro e assinatura?",
                "Documento: Manual\nPágina: 7\nConteúdo: Assinatura obrigatória."));
            var payload = mapper.readTree(request.get());
            assertTrue(payload.path("think").isBoolean());
            assertTrue(payload.path("think").asBoolean(false));
            assertFalse(payload.path("stream").asBoolean(true));
            String prompt = payload.path("messages").get(0).path("content").asText();
            assertEquals("system", payload.path("messages").get(0).path("role").asText());
            assertTrue(prompt.contains("cujo conteúdo sustenta diretamente essa afirmação"));
            assertTrue(prompt.contains("não deduza prazos"));
            assertTrue(prompt.contains("responda a cada um separadamente"));
            assertTrue(prompt.contains("Antes de concluir, verifique se todos os itens foram respondidos"));
            assertTrue(prompt.contains("Não crie explicações além das informações explícitas"));
            assertEquals("user", payload.path("messages").get(1).path("role").asText());
            prompt = payload.path("messages").get(1).path("content").asText();
            assertTrue(prompt.contains("Página: 7\nConteúdo: Assinatura obrigatória."));
            assertTrue(prompt.endsWith("PERGUNTA DO ALUNO: Cadastro e assinatura?"));

            // Thinking cannot replace absent or blank content, even when it is nonempty.
            for (var message : java.util.List.of(
                    java.util.Map.of("thinking", "Campo separado sem content."),
                    java.util.Map.of("thinking", "Campo separado com content vazio.", "content", "   "))) {
                responseMessage.set(message);
                IOException error = assertThrows(IOException.class, () -> client.chat(cfg, "Pergunta", "Contexto"));
                assertEquals("Ollama retornou uma resposta vazia.", error.getMessage());
            }
        } finally { server.stop(0); }
    }
}
