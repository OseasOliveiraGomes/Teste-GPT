# Desempenho Sylph — release-keyed

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | true | false | 1 | completed | 56.155 | 8726 | 273 | false |
| supervisores | recursivo | true | false | 1 | completed | 306.583 | 18722 | 1550 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](release-keyed-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de **4 dias úteis**. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":371,"reranking":0,"generation":47025,"auditInitial":7677,"repair":0,"auditRepair":0,"auditTotal":7677,"total":56152,"contextCharacters":18109}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expectedPages" : [ 4 ],
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "lexicalCoverageSignal" : true,
    "id" : "prazo_empresa",
    "citedExpectedPages" : [ 4 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: A diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR não foi identificada nas evidências fornecidas; Quem é responsável por cada função (supervisor acadêmico e supervisor de campo) não foi identificado nas evidências fornecidas. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":48,"reranking":0,"generation":231426,"auditInitial":18063,"repair":15024,"auditRepair":42007,"auditTotal":60070,"total":306576,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: A diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR não foi identificada nas evidências fornecidas; Quem é responsável por cada função (supervisor acadêmico e supervisor de campo) não foi identificado nas evidências fornecidas. Recomendo confirmar esses pontos com a coordenação do polo.", "Nenhuma afirmacao com citacao valida" ],
  "citationUnits" : 0,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expectedPages" : [ 15 ],
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "lexicalCoverageSignal" : false,
    "id" : "academico_ies",
    "citedExpectedPages" : [ ]
  }, {
    "expectedPages" : [ 16 ],
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "lexicalCoverageSignal" : false,
    "id" : "campo_empresa",
    "citedExpectedPages" : [ ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
  "all" : {
    "attempted" : 2,
    "meanMillis" : 181369.0,
    "meanAttemptMillis" : 181369.0,
    "maxAttemptMillis" : 306583,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 1,
    "maxMillis" : 306583,
    "completed" : 2
  },
  "simples" : {
    "attempted" : 1,
    "meanMillis" : 56155.0,
    "meanAttemptMillis" : 56155.0,
    "maxAttemptMillis" : 56155,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 1,
    "maxMillis" : 56155,
    "completed" : 1
  },
  "complexa" : {
    "attempted" : 1,
    "meanMillis" : 306583.0,
    "meanAttemptMillis" : 306583.0,
    "maxAttemptMillis" : 306583,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 306583,
    "completed" : 1
  },
  "ausente" : {
    "attempted" : 0,
    "meanMillis" : 0.0,
    "meanAttemptMillis" : 0.0,
    "maxAttemptMillis" : 0,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 0,
    "completed" : 0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Comparação

```json
{
  "currentSummary" : {
    "all" : {
      "attempted" : 2,
      "meanMillis" : 181369.0,
      "meanAttemptMillis" : 181369.0,
      "maxAttemptMillis" : 306583,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 1,
      "maxMillis" : 306583,
      "completed" : 2
    },
    "simples" : {
      "attempted" : 1,
      "meanMillis" : 56155.0,
      "meanAttemptMillis" : 56155.0,
      "maxAttemptMillis" : 56155,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 1,
      "maxMillis" : 56155,
      "completed" : 1
    },
    "complexa" : {
      "attempted" : 1,
      "meanMillis" : 306583.0,
      "meanAttemptMillis" : 306583.0,
      "maxAttemptMillis" : 306583,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 0,
      "maxMillis" : 306583,
      "completed" : 1
    },
    "ausente" : {
      "attempted" : 0,
      "meanMillis" : 0.0,
      "meanAttemptMillis" : 0.0,
      "maxAttemptMillis" : 0,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 0,
      "maxMillis" : 0,
      "completed" : 0
    },
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  },
  "baselinePhase" : "baseline",
  "pairs" : [ {
    "case" : "prazo/recursivo/rerank=false/r=1",
    "beforeMillis" : 126684,
    "afterMillis" : 56155,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 55.673171039752454,
    "beforeOllamaTotals" : {
      "promptEvalCount" : 8598,
      "promptEvalCountObservedCalls" : 3,
      "evalCount" : 437,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 718,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 20465680200,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 17063522000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 83059285000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 122236516300,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3
    },
    "afterOllamaTotals" : {
      "promptEvalCount" : 8726,
      "promptEvalCountObservedCalls" : 3,
      "promptEvalCachedCount" : 7414,
      "promptEvalCachedCountObservedCalls" : 2,
      "evalCount" : 273,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 737,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 69286900,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 2055577000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 50610813000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 54726794400,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3,
      "weightedOutputTokensPerSecond" : 5.39410422037678,
      "weightedPromptTokensPerSecond" : 627.074539168321
    },
    "beforeRepaired" : false,
    "afterRepaired" : false,
    "beforeStageMillis" : {
      "retrievalExclusive" : 6847,
      "reranking" : 0,
      "generation" : 90530,
      "auditInitial" : 27055,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 27055,
      "total" : 126673,
      "contextCharacters" : 20166
    },
    "afterStageMillis" : {
      "retrievalExclusive" : 371,
      "reranking" : 0,
      "generation" : 47025,
      "auditInitial" : 7677,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 7677,
      "total" : 56152,
      "contextCharacters" : 18109
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }, {
    "case" : "supervisores/recursivo/rerank=false/r=1",
    "beforeMillis" : 514056,
    "afterMillis" : 306583,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 40.359999688749866,
    "beforeOllamaTotals" : {
      "promptEvalCount" : 19362,
      "promptEvalCountObservedCalls" : 5,
      "evalCount" : 1927,
      "evalCountObservedCalls" : 4,
      "thinkingCharacters" : 2728,
      "thinkingCharactersObservedCalls" : 5,
      "loadDurationNanos" : 688601700,
      "loadDurationNanosObservedCalls" : 5,
      "promptEvalDurationNanos" : 37173286000,
      "promptEvalDurationNanosObservedCalls" : 4,
      "evalDurationNanos" : 473342970000,
      "evalDurationNanosObservedCalls" : 4,
      "totalDurationNanos" : 513381100000,
      "totalDurationNanosObservedCalls" : 5,
      "calls" : 5
    },
    "afterOllamaTotals" : {
      "promptEvalCount" : 18722,
      "promptEvalCountObservedCalls" : 5,
      "promptEvalCachedCount" : 1638,
      "promptEvalCachedCountObservedCalls" : 4,
      "evalCount" : 1550,
      "evalCountObservedCalls" : 4,
      "thinkingCharacters" : 3024,
      "thinkingCharactersObservedCalls" : 5,
      "loadDurationNanos" : 87075600,
      "loadDurationNanosObservedCalls" : 5,
      "promptEvalDurationNanos" : 25538016000,
      "promptEvalDurationNanosObservedCalls" : 4,
      "evalDurationNanos" : 279164834000,
      "evalDurationNanosObservedCalls" : 4,
      "totalDurationNanos" : 306350271500,
      "totalDurationNanosObservedCalls" : 5,
      "calls" : 5,
      "weightedOutputTokensPerSecond" : 5.552275255414155,
      "weightedPromptTokensPerSecond" : 667.2405561966913
    },
    "beforeRepaired" : true,
    "afterRepaired" : true,
    "beforeStageMillis" : {
      "retrievalExclusive" : 116,
      "reranking" : 0,
      "generation" : 239027,
      "auditInitial" : 89566,
      "repair" : 46351,
      "auditRepair" : 138879,
      "auditTotal" : 228445,
      "total" : 514055,
      "contextCharacters" : 19621
    },
    "afterStageMillis" : {
      "retrievalExclusive" : 48,
      "reranking" : 0,
      "generation" : 231426,
      "auditInitial" : 18063,
      "repair" : 15024,
      "auditRepair" : 42007,
      "auditTotal" : 60070,
      "total" : 306576,
      "contextCharacters" : 17669
    },
    "reviewRequired" : [ "Mudança no indicador encontrou; revisar cobertura/ausência.", "Fontes finais mudaram; comparar suporte e páginas.", "Texto mudou; confrontar cada afirmação com gabarito.", "Possível perda de cobertura: academico_ies. Exige revisão, sinal lexical não comprova erro.", "Possível perda de cobertura: campo_empresa. Exige revisão, sinal lexical não comprova erro." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  } ],
  "baselineSummary" : {
    "all" : {
      "meanMillis" : 320370.0,
      "completed" : 2,
      "maxMillis" : 514056,
      "attempted" : 2,
      "failedOrTimeout" : 0
    },
    "simples" : {
      "meanMillis" : 126684.0,
      "completed" : 1,
      "maxMillis" : 126684,
      "attempted" : 1,
      "failedOrTimeout" : 0
    },
    "complexa" : {
      "meanMillis" : 514056.0,
      "completed" : 1,
      "maxMillis" : 514056,
      "attempted" : 1,
      "failedOrTimeout" : 0
    },
    "ausente" : {
      "meanMillis" : 0.0,
      "completed" : 0,
      "maxMillis" : 0,
      "attempted" : 0,
      "failedOrTimeout" : 0
    },
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  },
  "acceptance" : "NÃO APROVADO AUTOMATICAMENTE: rejeitar otimizações com regressão factual confirmada; sinais de cobertura exigem revisão das respostas completas e fontes."
}
```
