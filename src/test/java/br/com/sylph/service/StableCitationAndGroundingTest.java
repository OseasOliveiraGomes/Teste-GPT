package br.com.sylph.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StableCitationAndGroundingTest {
    @Test void identicalRulesWithDifferentQuantitiesAreFlaggedWithoutConflatingDifferentSubjects() {
        assertEquals(1,EvidenceConflicts.detect(List.of(new RagService.Source("A",1,"O prazo é de 4 dias."),new RagService.Source("B",1,"O prazo é de 7 dias."))).size());
        assertTrue(EvidenceConflicts.detect(List.of(new RagService.Source("A",1,"Cadastro: prazo de 4 dias."),new RagService.Source("B",1,"Alteração: prazo de 7 dias."))).isEmpty());
    }
    @Test void stableIdOnlyResolvesItsExactPageAndDocument() {
        var a=new RagService.Source("A",3,"Prazo 4 dias",List.of("idA"),"docA");
        var b=new RagService.Source("B",4,"Prazo 7 dias",List.of("idB"),"docB");
        assertEquals("Prazo. [Fonte: A | Página: 3]",StableCitations.resolve("Prazo. [Trecho: idA | Página: 3]",List.of(a,b)));
        assertTrue(StableCitations.resolve("Prazo. [Trecho: idA | Página: 4]",List.of(a,b)).contains("Trecho:"));
        assertFalse(AnswerEvidence.parse(StableCitations.resolve("Prazo. [Trecho: inventado | Página: 3]",List.of(a,b)),List.of(a,b)).issues().isEmpty());
    }
    @Test void twoDocumentsWithTheSameOpaqueIdCannotForgeDocumentAttribution() {
        var a=new RagService.Source("A",3,"Texto A",List.of("id"),"a");
        var b=new RagService.Source("B",3,"Texto B",List.of("id"),"b");
        assertTrue(StableCitations.resolve("Texto. [Trecho: id | Página: 3]",List.of(a,b)).contains("Trecho:"));
    }
    @Test void guardRejectsInventedNumbersAndUnsupportedNegativeClaims() {
        var source=new RagService.Source("Manual",4,"O prazo é 4 dias úteis.");
        assertFalse(ClaimGrounding.unsupported("O prazo é 30 dias.",List.of(source)).isEmpty());
        assertFalse(ClaimGrounding.unsupported("Não precisa enviar documentos.",List.of(source)).isEmpty());
        assertTrue(ClaimGrounding.unsupported("1. O prazo é 4 dias úteis.",List.of(source)).isEmpty());
    }
}
