# Desempenho Sylph — release-proofids

Status: running. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | true | false | 1 | completed | 66.687 | 8715 | 283 | false |
| supervisores | recursivo | true | false | 1 | completed | 318.309 | 19898 | 1612 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](release-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de **4 dias úteis**, conforme especificado no documento.  
[Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":380,"reranking":0,"generation":57127,"auditInitial":8145,"repair":0,"auditRepair":0,"auditTotal":8145,"total":66682,"contextCharacters":18109}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "lexicalCoverageSignal" : true,
    "id" : "prazo_empresa",
    "citedExpectedPages" : [ 4 ],
    "expectedPages" : [ 4 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?”. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":93,"reranking":0,"generation":227005,"auditInitial":26594,"repair":19625,"auditRepair":44980,"auditTotal":71574,"total":318309,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?”. Recomendo confirmar esses pontos com a coordenação do polo.", "Nenhuma afirmacao com citacao valida" ],
  "citationUnits" : 0,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "lexicalCoverageSignal" : false,
    "id" : "academico_ies",
    "citedExpectedPages" : [ ],
    "expectedPages" : [ 15 ]
  }, {
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "lexicalCoverageSignal" : false,
    "id" : "campo_empresa",
    "citedExpectedPages" : [ ],
    "expectedPages" : [ 16 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```
