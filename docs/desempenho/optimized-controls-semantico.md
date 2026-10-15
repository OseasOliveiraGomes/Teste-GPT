# Desempenho Sylph — controls

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | semantico | true | false | 1 | completed | 124.452 | 14996 | 571 | true |
| ausente | semantico | true | false | 1 | completed | 341.007 | 16599 | 1811 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](optimized-controls-semantico.json).

## prazo / semantico / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

Confira os prazos em dias úteis: [...] ✓ Cadastro de empresa: 4 dias ✓ Análise Declaração de Dispensa: 5 dias ✓ Alteração do responsável da empresa: 5 dias ✓ Liberação do Termo de Compromisso e do Cadastro da Empresa: 7 dias ✓ Cadastro de Supervisor de Campo/ Supervisor Acadêmico/Preceptor: 4 dias ✓ Validação do Termo de Compromisso e do Cadastro da Empresa: 7 dias ✓ Vínculo do Supervisor/Empresa: 4 dias [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Caso a liberação imediata não seja possível e haja necessidade de correção de alguma [...] pelo Time de Estágios em até 7 dias uteis. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 41]

Estágios (ms): `{"retrievalExclusive":403,"reranking":0,"generation":75571,"auditInitial":6,"repair":13497,"auditRepair":30369,"auditTotal":30375,"total":124447,"contextCharacters":16904}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 2,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ "atribuicao sem vinculo agente-acao/modalidade: ] ✓ cadastro de empresa", "atribuicao sem vinculo agente-acao/modalidade: 7 dias ✓ cadastro de supervisor de campo/ supervisor academico/preceptor" ],
  "criteriaSignals" : [ {
    "id" : "prazo_empresa",
    "citedExpectedPages" : [ 4 ],
    "expectedPages" : [ 4 ],
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "lexicalCoverageSignal" : true
  } ],
  "missingGoldenPages" : [ ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## ausente / semantico / repetição 1

Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório?

Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório?”. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":378,"reranking":0,"generation":64501,"auditInitial":0,"repair":44270,"auditRepair":231820,"auditTotal":231820,"total":341007,"contextCharacters":15841}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível validar uma resposta com segurança. Não foi possível confirmar nas evidências consultadas: a pergunta: “Qual é o valor em reais do auxílio-transporte pago pela UNOPAR a todos os alunos do estágio obrigatório?”. Recomendo confirmar esses pontos com a coordenação do polo.", "Nenhuma afirmacao com citacao valida" ],
  "citationUnits" : 0,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "id" : "ausencia_explicita",
    "citedExpectedPages" : [ ],
    "expectedPages" : [ ],
    "expected" : "O manual consultado não estabelece um valor universal de auxílio-transporte. Reconhecer a ausência e não inventar quantia.",
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
    "completedUnder60Seconds" : 0,
    "maxMillis" : 341007,
    "completed" : 2,
    "attempted" : 2,
    "meanMillis" : 232729.5,
    "meanAttemptMillis" : 232729.5,
    "maxAttemptMillis" : 341007,
    "failedOrTimeout" : 0
  },
  "simples" : {
    "completedUnder60Seconds" : 0,
    "maxMillis" : 124452,
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 124452.0,
    "meanAttemptMillis" : 124452.0,
    "maxAttemptMillis" : 124452,
    "failedOrTimeout" : 0
  },
  "complexa" : {
    "completedUnder60Seconds" : 0,
    "maxMillis" : 0,
    "completed" : 0,
    "attempted" : 0,
    "meanMillis" : 0.0,
    "meanAttemptMillis" : 0.0,
    "maxAttemptMillis" : 0,
    "failedOrTimeout" : 0
  },
  "ausente" : {
    "completedUnder60Seconds" : 0,
    "maxMillis" : 341007,
    "completed" : 1,
    "attempted" : 1,
    "meanMillis" : 341007.0,
    "meanAttemptMillis" : 341007.0,
    "maxAttemptMillis" : 341007,
    "failedOrTimeout" : 0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```
