# Desempenho Sylph — release-accepted

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | true | false | 1 | completed | 61.509 | 8620 | 335 | false |
| supervisores | recursivo | true | false | 1 | completed | 394.670 | 19097 | 2368 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](release-accepted-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de **4 dias úteis**. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":398,"reranking":0,"generation":47861,"auditInitial":12132,"repair":0,"auditRepair":0,"auditTotal":12132,"total":61507,"contextCharacters":18109}`.

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

Nesses casos, antes de associar ao aluno, primeiramente será necessário cadastrar o supervisor acadêmico no sistema colaborar e, em alguns casos, vinculá-lo à empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

Nos cursos de Biomedicina, Enfermagem, Farmácia, Fisioterapia, Nutrição e Serviço Social, antes de associar o aluno, primeiramente será necessário cadastrar o supervisor de campo no sistema colaborar. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Estágios (ms): `{"retrievalExclusive":47,"reranking":0,"generation":230174,"auditInitial":75294,"repair":14908,"auditRepair":74235,"auditTotal":149529,"total":394670,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 4,
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
    "maxAttemptMillis" : 394670,
    "meanAttemptMillis" : 228089.5,
    "meanMillis" : 228089.5,
    "attempted" : 2,
    "completed" : 2,
    "maxMillis" : 394670,
    "completedUnder60Seconds" : 0
  },
  "simples" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 61509,
    "meanAttemptMillis" : 61509.0,
    "meanMillis" : 61509.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 61509,
    "completedUnder60Seconds" : 0
  },
  "complexa" : {
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 394670,
    "meanAttemptMillis" : 394670.0,
    "meanMillis" : 394670.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 394670,
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
    "afterMillis" : 61509,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 51.446907265321585,
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
      "promptEvalCachedCount" : 8114,
      "promptEvalCachedCountObservedCalls" : 2,
      "evalCount" : 335,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 737,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 107334200,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 932089000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 56222434000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 60020204100,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3,
      "weightedOutputTokensPerSecond" : 5.958475579339023,
      "weightedPromptTokensPerSecond" : 518.190859456554
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
      "retrievalExclusive" : 398,
      "reranking" : 0,
      "generation" : 47861,
      "auditInitial" : 12132,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 12132,
      "total" : 61507,
      "contextCharacters" : 18109
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }, {
    "case" : "supervisores/recursivo/rerank=false/r=1",
    "beforeMillis" : 514056,
    "afterMillis" : 394670,
    "beforeThinking" : true,
    "afterThinking" : true,
    "latencyReductionPercent" : 23.22431797313911,
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
      "promptEvalCount" : 19097,
      "promptEvalCountObservedCalls" : 5,
      "promptEvalCachedCount" : 1920,
      "promptEvalCachedCountObservedCalls" : 4,
      "evalCount" : 2368,
      "evalCountObservedCalls" : 4,
      "thinkingCharacters" : 3024,
      "thinkingCharactersObservedCalls" : 5,
      "loadDurationNanos" : 74139400,
      "loadDurationNanosObservedCalls" : 5,
      "promptEvalDurationNanos" : 24789096000,
      "promptEvalDurationNanosObservedCalls" : 4,
      "evalDurationNanos" : 368470716000,
      "evalDurationNanosObservedCalls" : 4,
      "totalDurationNanos" : 394595905700,
      "totalDurationNanosObservedCalls" : 5,
      "calls" : 5,
      "weightedOutputTokensPerSecond" : 6.426562267162637,
      "weightedPromptTokensPerSecond" : 691.150657531037
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
      "retrievalExclusive" : 47,
      "reranking" : 0,
      "generation" : 230174,
      "auditInitial" : 75294,
      "repair" : 14908,
      "auditRepair" : 74235,
      "auditTotal" : 149529,
      "total" : 394670,
      "contextCharacters" : 17669
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  } ],
  "baselinePhase" : "baseline",
  "currentSummary" : {
    "all" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 394670,
      "meanAttemptMillis" : 228089.5,
      "meanMillis" : 228089.5,
      "attempted" : 2,
      "completed" : 2,
      "maxMillis" : 394670,
      "completedUnder60Seconds" : 0
    },
    "simples" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 61509,
      "meanAttemptMillis" : 61509.0,
      "meanMillis" : 61509.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 61509,
      "completedUnder60Seconds" : 0
    },
    "complexa" : {
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 394670,
      "meanAttemptMillis" : 394670.0,
      "meanMillis" : 394670.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 394670,
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
