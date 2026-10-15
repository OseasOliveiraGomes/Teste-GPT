# Desempenho Sylph — thinking-off

Status: completed. Avaliação factual humana: PENDENTE.

Sem aquecimento oculto. Cliente, RAG, retriever e store reutilizados. Configuração local preservada. Tempos e tokens de tentativas interrompidas não são tempos de resposta validada.

| Caso | Método | Think | Rerank | Repetição | Status | Total (s) | Tokens entrada | Tokens saída | Reparo |
|---|---|---|---|---|---|---:|---:|---:|---|
| prazo | recursivo | false | false | 1 | completed | 81.444 | 8593 | 265 | false |
| supervisores | recursivo | false | false | 1 | completed | 558.666 | 14979 | 3315 | true |

Resultados completos, configuração, auditorias, fontes, critérios, métricas nativas e respostas: [JSON](thinking-off-original.json).

## prazo / recursivo / repetição 1

Qual é o prazo para análise do cadastro de uma empresa?

O prazo para análise do cadastro de uma empresa é de 7 dias úteis. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 4]

Estágios (ms): `{"retrievalExclusive":3833,"reranking":0,"generation":26478,"auditInitial":49786,"repair":0,"auditRepair":0,"auditTotal":49786,"total":81440,"contextCharacters":20166}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ ],
  "citationUnits" : 1,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ ],
  "criteriaSignals" : [ {
    "expectedPages" : [ 4 ],
    "citedExpectedPages" : [ 4 ],
    "id" : "prazo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Cadastro de empresa: 4 dias, em dias úteis; distinguir liberação/validação de 7 dias."
  } ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## supervisores / recursivo / repetição 1

Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função?

2. O Supervisor de Campo é o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

4. Nos cursos de Biomedicina, Enfermagem, Farmácia, Fisioterapia, Nutrição e Serviço Social, antes de associar o aluno, primeiramente será necessário cadastrar o supervisor de campo no sistema colaborar. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 16]

5. O Supervisor Acadêmico, por sua vez, é o representante da Instituição de Ensino Superior (IES) encarregado de acompanhar o estágio. Em muitos casos, esse supervisor é o tutor da sala de aula. [Fonte: Manual Estagio Obrigatorio UNOPAR 2026-2.pdf | Página: 15]

Não foi possível confirmar nas evidências consultadas: Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função. Recomendo confirmar esses pontos com a coordenação do polo.

Estágios (ms): `{"retrievalExclusive":88,"reranking":0,"generation":111578,"auditInitial":151219,"repair":295766,"auditRepair":0,"auditTotal":151219,"total":558666,"contextCharacters":19621}`.

Triagem factual (não comprova acerto):

```json
{
  "citationParseIssues" : [ "Texto sem citacao: Não foi possível confirmar nas evidências consultadas: Qual é a diferença entre supervisor acadêmico e supervisor de campo no estágio obrigatório da UNOPAR? Quem é responsável por cada função. Recomendo confirmar esses pontos com a coordenação do polo." ],
  "citationUnits" : 3,
  "groundingIssues" : [ ],
  "responsibilityIssues" : [ "atribuicao sem vinculo agente-acao/modalidade: o supervisor academico por sua vez e o representante da instituicao de ensino superior ies encarregado de acompanhar o estagio" ],
  "criteriaSignals" : [ {
    "expectedPages" : [ 15 ],
    "citedExpectedPages" : [ 15 ],
    "id" : "academico_ies",
    "lexicalCoverageSignal" : true,
    "expected" : "Acadêmico representa a IES e acompanha o estágio; em muitos casos é o tutor, sem universalizar essa condição."
  }, {
    "expectedPages" : [ 16 ],
    "citedExpectedPages" : [ 16 ],
    "id" : "campo_empresa",
    "lexicalCoverageSignal" : true,
    "expected" : "Campo é designado pela empresa concedente para orientar e acompanhar o estudante na empresa."
  } ],
  "classification" : "PENDENTE_REVISAO_HUMANA"
}
```

## Resumo

```json
{
  "all" : {
    "attempted" : 2,
    "failedOrTimeout" : 0,
    "meanMillis" : 320055.0,
    "completed" : 2,
    "maxMillis" : 558666
  },
  "simples" : {
    "attempted" : 1,
    "failedOrTimeout" : 0,
    "meanMillis" : 81444.0,
    "completed" : 1,
    "maxMillis" : 81444
  },
  "complexa" : {
    "attempted" : 1,
    "failedOrTimeout" : 0,
    "meanMillis" : 558666.0,
    "completed" : 1,
    "maxMillis" : 558666
  },
  "ausente" : {
    "attempted" : 0,
    "failedOrTimeout" : 0,
    "meanMillis" : 0.0,
    "completed" : 0,
    "maxMillis" : 0
  },
  "factualClassification" : "PENDENTE_REVISAO_HUMANA"
}
```
