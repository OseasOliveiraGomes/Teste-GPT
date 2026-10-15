# Desempenho Sylph — optimized-candidate

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | false | false | 1 | completed | 50.329 | 8530 | 186 | false |
| supervisores | recursivo | false | false | 1 | completed | 212.102 | 18893 | 1449 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](optimized-candidate-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

1. O prazo para análise do cadastro de uma empresa é de 4 dias úteis. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":3108,"reranking":0,"generation":32600,"auditInitial":13455,"repair":0,"auditRepair":0,"auditTotal":13455,"total":50325,"contextCharacters":18109}`.

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

SUPERVISOR ACADÊMICO Supervisor Acadêmico, por sua vez, é o representante da Instituição de Ensino Superior (IES) encarregado de acompanhar o estágio. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

Em muitos casos, esse supervisor é o tutor da sala de aula. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

SUPERVISOR DE CAMPO É o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Biomedicina Supervisor de Campo Sim Não Enfermagem Enfermeiro do Serviço Sim Não Farmácia Supervisor de Campo Sim Não Fisioterapia Supervisor de Campo Sim Não Nutrição Nutricionista Sim Não Serviço Social Supervisor de Campo Sim Sim [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?”. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":1816,"reranking":0,"generation":62585,"auditInitial":54342,"repair":14944,"auditRepair":78393,"auditTotal":132735,"total":212101,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?”. Recomendo confirmar esses pontos com a coordenação do polo." ],
  "citationUnits" : 4,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ "atribuicao sem vinculo agente-acao/modalidade: supervisor academico supervisor academico por sua vez e o representante da instituicao de ensino superior ies encarregado de acompanhar o estagio" ],
  "criteriaSignals" : [ {
    "expectedPages" : [ 15 ],
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "lexicalCoverageSignal" : true,
    "id" : "academico_ies",
    "citedExpectedPages" : [ 15 ]
  }, {
    "expectedPages" : [ 16 ],
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "lexicalCoverageSignal" : true,
    "id" : "campo_empresa",
    "citedExpectedPages" : [ 16 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
  "all" : {
    "completed" : 2,
    "attempted" : 2,
    "meanMillis" : 131215.5,
    "meanAttemptMillis" : 131215.5,
    "maxAttemptMillis" : 212102,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 1,
    "maxMillis" : 212102
  },
  "simples" : {
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 50329.0,
    "meanAttemptMillis" : 50329.0,
    "maxAttemptMillis" : 50329,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 1,
    "maxMillis" : 50329
  },
  "complexa" : {
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 212102.0,
    "meanAttemptMillis" : 212102.0,
    "maxAttemptMillis" : 212102,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 212102
  },
  "ausente" : {
    "completed" : 0,
    "attempted" : 0,
    "meanMillis" : 0.0,
    "meanAttemptMillis" : 0.0,
    "maxAttemptMillis" : 0,
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Comparação

```json
{
  "currentSummary" : {
    "all" : {
      "completed" : 2,
      "attempted" : 2,
      "meanMillis" : 131215.5,
      "meanAttemptMillis" : 131215.5,
      "maxAttemptMillis" : 212102,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 1,
      "maxMillis" : 212102
    },
    "simples" : {
      "completed" : 1,
      "attempted" : 1,
      "meanMillis" : 50329.0,
      "meanAttemptMillis" : 50329.0,
      "maxAttemptMillis" : 50329,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 1,
      "maxMillis" : 50329
    },
    "complexa" : {
      "completed" : 1,
      "attempted" : 1,
      "meanMillis" : 212102.0,
      "meanAttemptMillis" : 212102.0,
      "maxAttemptMillis" : 212102,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 0,
      "maxMillis" : 212102
    },
    "ausente" : {
      "completed" : 0,
      "attempted" : 0,
      "meanMillis" : 0.0,
      "meanAttemptMillis" : 0.0,
      "maxAttemptMillis" : 0,
      "failedOrTimeout" : 0,
      "completedUnder60Seconds" : 0,
      "maxMillis" : 0
    },
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  },
  "baselinePhase" : "baseline",
  "pairs" : [ {
    "case" : "prazo/recursivo/rerank=false/r=1",
    "beforeMillis" : 126684,
    "afterMillis" : 50329,
    "beforeThinking" : true,
    "afterThinking" : false,
    "latencyReductionPercent" : 60.27201540841779,
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
      "promptEvalCount" : 8530,
      "promptEvalCountObservedCalls" : 3,
      "promptEvalCachedCount" : 3,
      "promptEvalCachedCountObservedCalls" : 2,
      "evalCount" : 186,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 0,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 15413497300,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 12109628000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 20269778000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 48895482300,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3,
      "weightedOutputTokensPerSecond" : 9.176222847630596,
      "weightedPromptTokensPerSecond" : 702.251134386622
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
      "retrievalExclusive" : 3108,
      "reranking" : 0,
      "generation" : 32600,
      "auditInitial" : 13455,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 13455,
      "total" : 50325,
      "contextCharacters" : 18109
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }, {
    "case" : "supervisores/recursivo/rerank=false/r=1",
    "beforeMillis" : 514056,
    "afterMillis" : 212102,
    "beforeThinking" : true,
    "afterThinking" : false,
    "latencyReductionPercent" : 58.73951476103771,
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
      "promptEvalCount" : 18893,
      "promptEvalCountObservedCalls" : 5,
      "promptEvalCachedCount" : 1463,
      "promptEvalCachedCountObservedCalls" : 4,
      "evalCount" : 1449,
      "evalCountObservedCalls" : 4,
      "thinkingCharacters" : 0,
      "thinkingCharactersObservedCalls" : 5,
      "loadDurationNanos" : 1627239500,
      "loadDurationNanosObservedCalls" : 5,
      "promptEvalDurationNanos" : 24521767000,
      "promptEvalDurationNanosObservedCalls" : 4,
      "evalDurationNanos" : 183503961000,
      "evalDurationNanosObservedCalls" : 4,
      "totalDurationNanos" : 212010025300,
      "totalDurationNanosObservedCalls" : 5,
      "calls" : 5,
      "weightedOutputTokensPerSecond" : 7.896287317743512,
      "weightedPromptTokensPerSecond" : 709.0027403000771
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
      "retrievalExclusive" : 1816,
      "reranking" : 0,
      "generation" : 62585,
      "auditInitial" : 54342,
      "repair" : 14944,
      "auditRepair" : 78393,
      "auditTotal" : 132735,
      "total" : 212101,
      "contextCharacters" : 17669
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
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
