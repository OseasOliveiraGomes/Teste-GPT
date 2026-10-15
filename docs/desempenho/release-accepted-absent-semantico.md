# Desempenho Sylph — release-accepted

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| ausente | semantico | true | false | 1 | completed | 92.788 | 13933 | 661 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](release-accepted-absent-semantico.json).

## ausente / semantico / repetição 1

Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório?

Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":359,"reranking":0,"generation":45474,"auditInitial":44111,"repair":2790,"auditRepair":0,"auditTotal":44111,"total":92786,"contextCharacters":15841}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório. Recomendo confirmar esses pontos com a coordenação do polo.", "Nenhuma afirmacao com citacao valida" ],
  "citationUnits" : 0,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expected" : "O manual consultado não estabelece um valor universal de auxílio-transporte. Reconhecer a ausência e não inventar quantia.",
    "lexicalCoverageSignal" : true,
    "id" : "ausencia_explicita",
    "citedExpectedPages" : [ ],
    "expectedPages" : [ ]
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
    "completedUnder60Seconds" : 0,
    "maxMillis" : 92788,
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 92788.0,
    "meanAttemptMillis" : 92788.0,
    "maxAttemptMillis" : 92788
  },
  "simples" : {
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 0,
    "completed" : 0,
    "attempted" : 0,
    "meanMillis" : 0.0,
    "meanAttemptMillis" : 0.0,
    "maxAttemptMillis" : 0
  },
  "complexa" : {
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 0,
    "completed" : 0,
    "attempted" : 0,
    "meanMillis" : 0.0,
    "meanAttemptMillis" : 0.0,
    "maxAttemptMillis" : 0
  },
  "ausente" : {
    "failedOrTimeout" : 0,
    "completedUnder60Seconds" : 0,
    "maxMillis" : 92788,
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 92788.0,
    "meanAttemptMillis" : 92788.0,
    "maxAttemptMillis" : 92788
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```
