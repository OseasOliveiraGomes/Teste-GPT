# Desempenho Sylph — optimized-final

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | false | false | 1 | completed | 33.038 | 8625 | 172 | false |
| supervisores | recursivo | false | false | 1 | completed | 166.547 | 17851 | 1202 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](optimized-final-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de 4 dias úteis. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":439,"reranking":0,"generation":19685,"auditInitial":11832,"repair":0,"auditRepair":0,"auditTotal":11832,"total":33032,"contextCharacters":18109}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "citedExpectedPages" : [ 4 ],
    "id" : "prazo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "expectedPages" : [ 4 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

Oriente o aluno apenas a reimprimir o Termo de Compromisso e o cadastro da empresa atualizado no AVA, após o nome do novo responsável estar corrigido no sistema. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 14]

SUPERVISOR DE CAMPO É o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Estágios (ms): `{"retrievalExclusive":76,"reranking":0,"generation":52128,"auditInitial":59995,"repair":12886,"auditRepair":41447,"auditTotal":101442,"total":166547,"contextCharacters":17669}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 2,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "citedExpectedPages" : [ ],
    "id" : "academico_ies",
    "lexicalCoverageSignal" : false,
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "expectedPages" : [ 15 ]
  }, {
    "citedExpectedPages" : [ 16 ],
    "id" : "campo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "expectedPages" : [ 16 ]
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
  "all" : {
    "meanMillis" : 99792.5,
    "attempted" : 2,
    "completed" : 2,
    "maxMillis" : 166547,
    "completedUnder60Seconds" : 1,
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 166547,
    "meanAttemptMillis" : 99792.5
  },
  "simples" : {
    "meanMillis" : 33038.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 33038,
    "completedUnder60Seconds" : 1,
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 33038,
    "meanAttemptMillis" : 33038.0
  },
  "complexa" : {
    "meanMillis" : 166547.0,
    "attempted" : 1,
    "completed" : 1,
    "maxMillis" : 166547,
    "completedUnder60Seconds" : 0,
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 166547,
    "meanAttemptMillis" : 166547.0
  },
  "ausente" : {
    "meanMillis" : 0.0,
    "attempted" : 0,
    "completed" : 0,
    "maxMillis" : 0,
    "completedUnder60Seconds" : 0,
    "failedOrTimeout" : 0,
    "maxAttemptMillis" : 0,
    "meanAttemptMillis" : 0.0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Comparação

```json
{
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
  },
  "pairs" : [ {
    "case" : "prazo/recursivo/rerank=false/r=1",
    "beforeMillis" : 126684,
    "afterMillis" : 33038,
    "beforeThinking" : true,
    "afterThinking" : false,
    "latencyReductionPercent" : 73.92093713491838,
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
      "promptEvalCount" : 8625,
      "promptEvalCountObservedCalls" : 3,
      "promptEvalCachedCount" : 6,
      "promptEvalCachedCountObservedCalls" : 2,
      "evalCount" : 172,
      "evalCountObservedCalls" : 2,
      "thinkingCharacters" : 0,
      "thinkingCharactersObservedCalls" : 3,
      "loadDurationNanos" : 47406800,
      "loadDurationNanosObservedCalls" : 3,
      "promptEvalDurationNanos" : 11830317000,
      "promptEvalDurationNanosObservedCalls" : 2,
      "evalDurationNanos" : 18200216000,
      "evalDurationNanosObservedCalls" : 2,
      "totalDurationNanos" : 31677994200,
      "totalDurationNanosObservedCalls" : 3,
      "calls" : 3,
      "weightedOutputTokensPerSecond" : 9.450437291513463,
      "weightedPromptTokensPerSecond" : 726.6077485497641
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
      "retrievalExclusive" : 439,
      "reranking" : 0,
      "generation" : 19685,
      "auditInitial" : 11832,
      "repair" : 0,
      "auditRepair" : 0,
      "auditTotal" : 11832,
      "total" : 33032,
      "contextCharacters" : 18109
    },
    "reviewRequired" : [ "Texto mudou; confrontar cada afirmação com gabarito." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }, {
    "case" : "supervisores/recursivo/rerank=false/r=1",
    "beforeMillis" : 514056,
    "afterMillis" : 166547,
    "beforeThinking" : true,
    "afterThinking" : false,
    "latencyReductionPercent" : 67.601389731858,
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
      "promptEvalCount" : 17851,
      "promptEvalCountObservedCalls" : 5,
      "promptEvalCachedCount" : 1479,
      "promptEvalCachedCountObservedCalls" : 4,
      "evalCount" : 1202,
      "evalCountObservedCalls" : 4,
      "thinkingCharacters" : 0,
      "thinkingCharactersObservedCalls" : 5,
      "loadDurationNanos" : 83021900,
      "loadDurationNanosObservedCalls" : 5,
      "promptEvalDurationNanos" : 22991253000,
      "promptEvalDurationNanosObservedCalls" : 4,
      "evalDurationNanos" : 140899010000,
      "evalDurationNanosObservedCalls" : 4,
      "totalDurationNanos" : 166477108800,
      "totalDurationNanosObservedCalls" : 5,
      "calls" : 5,
      "weightedOutputTokensPerSecond" : 8.530932900096317,
      "weightedPromptTokensPerSecond" : 710.1831292100522
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
      "retrievalExclusive" : 76,
      "reranking" : 0,
      "generation" : 52128,
      "auditInitial" : 59995,
      "repair" : 12886,
      "auditRepair" : 41447,
      "auditTotal" : 101442,
      "total" : 166547,
      "contextCharacters" : 17669
    },
    "reviewRequired" : [ "Fontes finais mudaram; comparar suporte e páginas.", "Texto mudou; confrontar cada afirmação com gabarito.", "Possível perda de cobertura: academico_ies. Exige revisão, sinal lexical não comprova erro." ],
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  } ],
  "baselinePhase" : "baseline",
  "currentSummary" : {
    "all" : {
      "meanMillis" : 99792.5,
      "attempted" : 2,
      "completed" : 2,
      "maxMillis" : 166547,
      "completedUnder60Seconds" : 1,
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 166547,
      "meanAttemptMillis" : 99792.5
    },
    "simples" : {
      "meanMillis" : 33038.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 33038,
      "completedUnder60Seconds" : 1,
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 33038,
      "meanAttemptMillis" : 33038.0
    },
    "complexa" : {
      "meanMillis" : 166547.0,
      "attempted" : 1,
      "completed" : 1,
      "maxMillis" : 166547,
      "completedUnder60Seconds" : 0,
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 166547,
      "meanAttemptMillis" : 166547.0
    },
    "ausente" : {
      "meanMillis" : 0.0,
      "attempted" : 0,
      "completed" : 0,
      "maxMillis" : 0,
      "completedUnder60Seconds" : 0,
      "failedOrTimeout" : 0,
      "maxAttemptMillis" : 0,
      "meanAttemptMillis" : 0.0
    },
    "factualClassification" : "PENDENTE_REVISAO_HUMANA"
  }
}
```
