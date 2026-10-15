# Desempenho Sylph — baseline

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | true | false | 1 | completed | 126.684 | 8598 | 437 | false |
| supervisores | recursivo | true | false | 1 | completed | 514.056 | 19362 | 1927 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](baseline-core.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

1. O prazo para análise do cadastro de uma empresa é de **7 dias úteis**, conforme indicado no documento.  
[Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":6847,"reranking":0,"generation":90530,"auditInitial":27055,"repair":0,"auditRepair":0,"auditTotal":27055,"total":126673,"contextCharacters":20166}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "id" : "prazo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias.",
    "expectedPages" : [ 4 ],
    "citedExpectedPages" : [ 4 ]
  } ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

SUPERVISOR ACADÊMICO Supervisor Acadêmico, por sua vez, é o representante da Instituição de Ensino Superior (IES) encarregado de acompanhar o estágio. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

Em muitos casos, esse supervisor é o tutor da sala de aula. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

SUPERVISOR DE CAMPO É o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Nos cursos de Biomedicina, Enfermagem, Farmácia, Fisioterapia, Nutrição e Serviço Social, antes de associar o aluno, primeiramente será necessário cadastrar o supervisor de campo no sistema colaborar. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

Estágios (ms): `{"retrievalExclusive":116,"reranking":0,"generation":239027,"auditInitial":89566,"repair":46351,"auditRepair":138879,"auditTotal":228445,"total":514055,"contextCharacters":19621}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 4,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ "atribuicao sem vinculo agente-acao/modalidade: supervisor academico supervisor academico por sua vez e o representante da instituicao de ensino superior ies encarregado de acompanhar o estagio" ],
  "criteriaSignals" : [ {
    "id" : "academico_ies",
    "lexicalCoverageSignal" : true,
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição.",
    "expectedPages" : [ 15 ],
    "citedExpectedPages" : [ 15 ]
  }, {
    "id" : "campo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa.",
    "expectedPages" : [ 16 ],
    "citedExpectedPages" : [ 16 ]
  } ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
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
```
