# Desempenho Sylph — optimized-precision

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | true | false | 1 | completed | 59.482 | 8620 | 335 | false |
| supervisores | recursivo | true | false | 1 | completed | 328.507 | 17047 | 1586 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](optimized-precision-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de **4 dias úteis**. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":380,"reranking":0,"generation":46558,"auditInitial":11255,"repair":0,"auditRepair":0,"auditTotal":11255,"total":59477,"contextCharacters":18109}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "expectedPages" : [ 4 ],
    "citedExpectedPages" : [ 4 ],
    "id" : "prazo_empresa",
    "lexicalCoverageSignal" : true
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

SUPERVISOR ACADÊMICO Supervisor Acadêmico, por sua vez, é o representante da Instituição de Ensino Superior (IES) encarregado de acompanhar o estágio. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

SUPERVISOR DE CAMPO É o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Estágios (ms): `{"retrievalExclusive":53,"reranking":0,"generation":274454,"auditInitial":0,"repair":14089,"auditRepair":39891,"auditTotal":39891,"total":328507,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 2,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ "atribuicao sem vinculo agente-acao/modalidade: supervisor academico supervisor academico por sua vez e o representante da instituicao de ensino superior ies encarregado de acompanhar o estagio" ],
  "criteriaSignals" : [ {
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "expectedPages" : [ 15 ],
    "citedExpectedPages" : [ 15 ],
    "id" : "academico_ies",
    "lexicalCoverageSignal" : true
  }, {
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "expectedPages" : [ 16 ],
    "citedExpectedPages" : [ 16 ],
    "id" : "campo_empresa",
    "lexicalCoverageSignal" : true
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
  "all" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 328507,
    "meanAttemptMillis" : 193994.5,
    "meanMillis" : 193994.5,
    "attempted" : 2,
    "completed" : 2,
    "maxMillis" : 328507,
    "completedUnder60Seconds" : 1
  },
  "simples" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 59482,
    "meanAttemptMillis" : 59482.0,
    "meanMillis" : 59482.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 59482,
    "completedUnder60Seconds" : 1
  },
  "complexa" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 328507,
    "meanAttemptMillis" : 328507.0,
    "meanMillis" : 328507.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 328507,
    "completedUnder60Seconds" : 0
  },
  "ausente" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 0,
    "meanAttemptMillis" : 0.0,
    "meanMillis" : 0.0,
    "attempted" : 0,
    "completed" : 0,
    "maxMillis" : 0,
    "completedUnder60Seconds" : 0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Comparação

```json
{
  "pairs" : [ {
    "case" : "prazo/recursivo/rerank=false/r=1",
    "beforeMillis" : 126684,
    "afterMillis" : 59482,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 53.04695146979887,
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
      "promptEvalCount" : 8620,
      "promptEvalCountObservedCalls" : 3,
      "promptEvalCachedCount" : 8109,
      "promptEvalCachedCountObservedCalls" : 2,
      "evalCount" : 335,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 737,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 51150700,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 1324111000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 54842196000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 57831937100,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3,
      "weightedOutputTokensPerSecond" : 6.108435191034291,
      "weightedPromptTokensPerSecond" : 368.54916241916277
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
      "retrievalExclusive" : 380,
      "reranking" : 0,
      "generation" : 46558,
      "auditInitial" : 11255,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 11255,
      "total" : 59477,
      "contextCharacters" : 18109
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }, {
    "case" : "supervisores/recursivo/rerank=false/r=1",
    "beforeMillis" : 514056,
    "afterMillis" : 328507,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 36.09509469785393,
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
      "promptEvalCount" : 17047,
      "promptEvalCountObservedCalls" : 4,
      "promptEvalCachedCount" : 8102,
      "promptEvalCachedCountObservedCalls" : 3,
      "evalCount" : 1586,
      "evalCountObservedCalls" : 3,
      "thinkingCharacters" : 3853,
      "thinkingCharactersObservedCalls" : 4,
      "loadDurationNanos" : 61590000,
      "loadDurationNanosObservedCalls" : 4,
      "promptEvalDurationNanos" : 13256045000,
      "promptEvalDurationNanosObservedCalls" : 3,
      "evalDurationNanos" : 312134658000,
      "evalDurationNanosObservedCalls" : 3,
      "totalDurationNanos" : 328431867800,
      "totalDurationNanosObservedCalls" : 4,
      "calls" : 4,
      "weightedOutputTokensPerSecond" : 5.081140332708584,
      "weightedPromptTokensPerSecond" : 671.4672438121627
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
      "retrievalExclusive" : 53,
      "reranking" : 0,
      "generation" : 274454,
      "auditInitial" : 0,
      "repair" : 14089,
      "auditRepair" : 39891,
      "auditTotal" : 39891,
      "total" : 328507,
      "contextCharacters" : 17669
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  } ],
  "baselinePhase" : "baseline",
  "currentSummary" : {
    "all" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 328507,
      "meanAttemptMillis" : 193994.5,
      "meanMillis" : 193994.5,
      "attempted" : 2,
      "completed" : 2,
      "maxMillis" : 328507,
      "completedUnder60Seconds" : 1
    },
    "simples" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 59482,
      "meanAttemptMillis" : 59482.0,
      "meanMillis" : 59482.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 59482,
      "completedUnder60Seconds" : 1
    },
    "complexa" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 328507,
      "meanAttemptMillis" : 328507.0,
      "meanMillis" : 328507.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 328507,
      "completedUnder60Seconds" : 0
    },
    "ausente" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 0,
      "meanAttemptMillis" : 0.0,
      "meanMillis" : 0.0,
      "attempted" : 0,
      "completed" : 0,
      "maxMillis" : 0,
      "completedUnder60Seconds" : 0
    },
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  },
  "acceptance" : "NÃO APROVADO AUTOMATICAMENTE: rejeitar otimizações com regressão factual confirmada; sinais de cobertura exigem revisão das respostas completas e fontes.",
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
  }
}
```
