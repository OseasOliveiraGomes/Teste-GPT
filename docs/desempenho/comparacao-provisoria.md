# Medições provisórias de desempenho

Resultados observados com os modelos instalados, índice existente, chunking recursivo, reranking desligado e `top_k=8`. Uma amostra por caso/fase; não são percentis nem garantia de latência. A revisão factual detalhada está em [revisao-factual.md](revisao-factual.md). As tentativas com thinking desligado foram rejeitadas; o conjunto `optimized-precision-core` com thinking ligado foi aceito no escopo das duas perguntas core.

**Este arquivo preserva as tentativas intermediárias.** A comparação do JAR final é `release-accepted-core`: simples **61,509 s**, composto **394,670 s**, média **228,090 s**; reduções vs baseline **51,45%/23,22%/28,80%**. O controle ausente final levou **92,788 s**, sem quantia inventada ou protocolo inválido. Os valores de 59,482/328,507 s abaixo são da referência intermediária, não do binário entregue. Resultado consolidado, tokens, ressalvas e identificação: [RELATORIO-FINAL.md](RELATORIO-FINAL.md).

## Tempos por estágio

Valores em segundos. Recuperação exclui reranking; geração exclui reparo; auditorias são apresentadas separadamente. O total da coluna final é o tempo externo de parede registrado pelo benchmark e pode diferir em milissegundos da soma dos estágios internos.

| Fase | Caso | Recuperação | Rerank | Geração | Auditoria 1 | Reparo | Auditoria 2 | Total | Revisão do resultado |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| Baseline / think=true | Prazo | 6,847 | 0 | 90,530 | 27,055 | 0 | 0 | **126,684** | Incorreto: 7 em vez de 4 dias úteis |
| Baseline / think=true | Supervisores | 0,116 | 0 | 239,027 | 89,566 | 46,351 | 138,879 | **514,056** | Final sustentado após reparo |
| Original / think=false | Prazo | 3,833 | 0 | 26,478 | 49,786 | 0 | 0 | **81,444** | Mesmo erro factual |
| Original / think=false | Supervisores | 0,088 | 0 | 111,578 | 151,219 | 295,766 | 0 | **558,666** | Trechos sustentados, falha de reparo e aviso indevido |
| Candidato otimizado | Prazo | 3,108 | 0 | 32,600 | 13,455 | 0 | 0 | **50,329** | Correto: 4 dias úteis, página 4 |
| Candidato otimizado | Supervisores | 1,816 | 0 | 62,585 | 54,342 | 14,944 | 78,393 | **212,102** | Definições sustentadas, aviso indevido; requer nova validação |
| Segunda tentativa / think=false, rejeitada | Prazo | 0,439 | 0 | 19,685 | 11,832 | 0 | 0 | **33,038** | Caso correto; versão rejeitada por omissão na composta |
| Segunda tentativa / think=false, rejeitada | Supervisores | 0,076 | 0 | 52,128 | 59,995 | 12,886 | 41,447 | **166,547** | Omite acadêmico e inclui orientação irrelevante; regressão |
| Aceita / think=true | Prazo | 0,380 | 0 | 46,558 | 11,255 | 0 | 0 | **59,482** | Correto: 4 dias úteis, página 4 |
| Aceita / think=true | Supervisores | 0,053 | 0 | 274,454 | 0 | 14,089 | 39,891 | **328,507** | Duas definições corretas, páginas 15/16, sem aviso indevido |

O baseline complexo excede seis minutos por combinar geração (46,50% do total), duas auditorias (44,44%) e reparo (9,02%). Recuperação representa aproximadamente 0,02% desse caso; não explica a espera de mais de oito minutos. Na ablação, o reparo sozinho passa a consumir 52,94% do total. Nenhuma checagem deve ser removida com base nesses números; eles mostram onde reduzir trabalho redundante e saída desnecessária mantendo as decisões de segurança.

## Tokens e carregamento nativos do Ollama

Entrada soma `prompt_eval_count` de todas as chamadas que informaram a métrica, incluindo embedding da pergunta. Saída soma `eval_count` das chamadas de chat; tokens de pensamento, quando ativados, estão incluídos nesse número. Caracteres de pensamento não são uma contagem de tokens. Os tempos de carregamento estão incluídos nos tempos das chamadas, não devem ser somados novamente ao total de parede.

| Fase | Caso | Chamadas HTTP | Tokens entrada | Tokens saída | Caracteres pensamento | Carga acumulada (s) | Contexto (caracteres) |
|---|---|---:|---:|---:|---:|---:|---:|
| Baseline / think=true | Prazo | 3 | 8.598 | 437 | 718 | 20,466 | 20.166 |
| Baseline / think=true | Supervisores | 5 | 19.362 | 1.927 | 2.728 | 0,689 | 19.621 |
| Original / think=false | Prazo | 3 | 8.593 | 265 | 0 | 2,161 | 20.166 |
| Original / think=false | Supervisores | 4 | 14.979 | 3.315 | 0 | 0,078 | 19.621 |
| Candidato otimizado | Prazo | 3 | 8.530 | 186 | 0 | 15,413 | 18.109 |
| Candidato otimizado | Supervisores | 5 | 18.893 | 1.449 | 0 | 1,627 | 17.669 |
| Segunda tentativa / think=false, rejeitada | Prazo | 3 | 8.625 | 172 | 0 | 0,047 | 18.109 |
| Segunda tentativa / think=false, rejeitada | Supervisores | 5 | 17.851 | 1.202 | 0 | 0,083 | 17.669 |
| Aceita / think=true | Prazo | 3 | 8.620 | 335 | 737 | 0,051 | 18.109 |
| Aceita / think=true | Supervisores | 4 | 17.047 | 1.586 | 3.853 | 0,062 | 17.669 |

Na complexa com thinking desligado, a geração caiu de 812 para 441 tokens de saída, mas o reparo passou de 68 para 1.854 tokens, e a primeira auditoria de 433 para 1.020. Portanto, desligar o pensamento da geração não basta para reduzir a saída e a latência de todo o fluxo.

Velocidade de inferência por `eval_count / eval_duration`, sem incluir carregamento/prefill:

| Fase/caso | Geração (tokens/s) | Auditoria 1 (tokens/s) | Reparo (tokens/s) | Auditoria 2 (tokens/s) |
|---|---:|---:|---:|---:|
| Baseline / prazo | 3,81 | 8,90 | — | — |
| Baseline / supervisores | 3,62 | 4,98 | 2,38 | 4,60 |
| Original think=false / prazo | 2,27 | 4,74 | — | — |
| Original think=false / supervisores | 4,37 | 6,96 | 6,40 | — |
| Candidato / prazo | 5,57 | 12,25 | — | — |
| Candidato / supervisores | 4,68 | 9,86 | 6,04 | 8,85 |
| Aceita / prazo | 4,67 | 12,62 | — | — |
| Aceita / supervisores | 4,52 | — | 5,73 | 9,24 |

Estas velocidades são observações de chamadas diferentes, sujeitas ao contexto, cache, residência e carga da máquina; não estabelecem isoladamente uma aceleração do motor de inferência pela alteração do código.

## Média e máximo provisórios

As estatísticas abaixo contam conclusões técnicas, inclusive a resposta simples factualmente errada e o fallback com reparo inválido. Não representam tempo médio de respostas factualmente aprovadas.

| Fase | Amostras | Média (s) | Máximo (s) | Redução da média vs baseline | Redução do máximo vs baseline |
|---|---:|---:|---:|---:|---:|
| Baseline / think=true | 2 | 320,370 | 514,056 | — | — |
| Original / think=false | 2 | 320,055 | 558,666 | 0,10% | **−8,68%** |
| Candidato otimizado | 2 | 131,216 | 212,102 | **59,04%** | **58,74%** |
| Segunda tentativa / think=false, rejeitada | 2 | 99,793 | 166,547 | 68,85% | 67,60% |
| **Aceita / think=true** | **2** | **193,995** | **328,507** | **39,45%** | **36,10%** |

O controle isolado `think=false` é rejeitado como solução padrão nesta experiência: a máxima piorou e o reparo falhou, sem corrigir o erro factual simples. No primeiro caso candidato, a latência caiu 60,27% e a resposta corrigiu o prazo para 4 dias úteis, ficando em 50,329 s. O conjunto candidato reduziu média e máximo, mas a resposta composta terminou com um aviso de ausência indevido; exige correção e nova validação antes de adoção final. A melhoria dos tempos, por si, não satisfaz a restrição de preservar a confiabilidade.

A segunda tentativa, registrada como `optimized-final-core.json`, também foi rejeitada: a composta passou a omitir o supervisor acadêmico e incluiu uma orientação irrelevante de reimpressão. O tempo menor não compensa a perda de cobertura. A execução seguinte, `optimized-precision-core.json`, mantém o thinking da geração ligado e recebe avaliação independente.

O conjunto `optimized-precision-core` foi aceito após o confronto direto com as fontes: corrige o prazo simples e mantém ambas as definições/responsáveis na composta, sem aviso indevido. A auditoria inicial complexa não fez chamada LLM porque a guarda local rejeitou previamente uma geração sem citações no formato válido; o reparo e a auditoria posterior completa foram executados. Isso é comportamento já existente, não remoção de uma verificação. A geração complexa em si aumentou para 274,454 s, portanto a redução do total não prova que cada chamada ficou mais rápida.

## Controles semânticos separados do comparativo core

| Caso | Total (s) | Tokens entrada | Tokens saída | Reparo (s) | Auditoria posterior (s) | Parecer |
|---|---:|---:|---:|---:|---:|---|
| Prazo | 124,452 | 14.996 | 571 | 13,497 | 30,369 | 4 dias úteis corretos; fragmento extra irrelevante da página 41 |
| Ausente | 341,007 | 16.599 | 1.811 | 44,270 | 231,820 | Sem quantia inventada, mas auditoria inválida e fallback conservador |

A auditoria posterior do ausente produziu 1.349 tokens e falhou no protocolo. A contenção de fatos não validados foi preservada, mas essa tentativa não autoriza declarar robustez global. O protocolo de auditoria por provas/IDs proposto após o diagnóstico precisa de avaliação própria; ainda não foi aceito. Estes controles usam chunking semântico e perguntas diferentes: não são amostras adicionais para a média core recursiva, nem uma medição causal do efeito do chunking.

## Tentativas posteriores de auditoria por IDs — rejeitadas

- O protocolo de claims em array acertou o simples em 66,687 s, mas regrediu no complexo, retornando ausência indevida após IDs duplicados rejeitados. A exportação final do complexo sofreu bloqueio de arquivo; o tempo de 318,309 s é o término observado pelo agente principal, sem atribuir completude ao JSON principal desatualizado.
- O protocolo de claims em objeto (`release-keyed-core.json`) acertou o simples em 56,155 s, mas o complexo de 306,583 s terminou sem resposta: o reparo selecionou somente sentenças irrelevantes da página 24, rejeitadas por uma auditoria válida. Este arquivo foi concluído/copiado e revisado diretamente.
- Ambos são tentativas **rejeitadas**. A média de 181,369 s do objeto não substitui os 193,995 s da referência intermediária aceita. Não se mede a velocidade de uma resposta ausente como se fosse resposta completa correta. O wire da auditoria retorna à representação intermediária com tabela de fontes/citações textuais, preservando os demais ganhos.

## Limites de frio/quente e de causalidade

- Cada fase cria um processo Java novo; seu cache de embeddings começa em zero e atinge uma entrada após a primeira pergunta. A pergunta composta é diferente da simples: houve nova chamada de embedding, não um acerto de cache dessa pergunta.
- Dentro de cada processo, cliente, store, retriever e RAG são reutilizados. O índice lexical e o CSV já estão carregados no segundo caso. As perguntas diferentes não formam uma comparação controlada de cache frio/quente da mesma pergunta.
- O benchmark não força descarregamento do Ollama entre fases. `health` consulta metadados, sem gerar embeddings/respostas. Os modelos e o cache de prompt podem permanecer residentes por uso anterior. Portanto, “primeiro caso no processo Java” não significa “modelo frio”.
- O Ollama registrou 16,074 s de carregamento do LLM na primeira geração baseline, contra 0,011 s na geração simples de thinking desligado. Esta última também reutilizou 7.460 tokens de prompt cacheados, contra zero na primeira. A redução observada de 35,71% no caso simples mistura esses fatores com o efeito do thinking.
- Na versão **aceita com thinking ligado**, as gerações simples/complexa reutilizaram **7.406/7.368** tokens de prompt, contra **0/3** no baseline. Carregamento acumulado: **0,051/0,062 s**, contra **20,466/0,689 s**. Os ganhos da tabela são do fluxo observado em estados de residência/cache diferentes; não isolam a contribuição de cada edição.
- Há uma amostra por pergunta, sem controle da carga externa da máquina. Repetições da mesma pergunta, metadados da máquina, cache e durações nativas devem acompanhar qualquer estimativa de ganho generalizável.
- O reranking estava desligado nas duas fases: zero neste fluxo significa ausência de chamada, não prova de que reranking ativado seja gratuito.

O ambiente registrado tem Intel Core i5-13420H, 12 processadores lógicos, aproximadamente 25,46 GB de RAM e RTX 4050 Laptop de 6.141 MiB. Durante o baseline simples, o Ollama reportou 6,60 GB de tamanho residente do modelo Sylph e 4,19 GB de VRAM, com contexto 8.192. Esses dados e a inferência observada de poucos tokens/s ajudam a caracterizar o limite do equipamento, mas uma única leitura de residência/utilização não permite atribuir toda a latência ao hardware.

Fontes locais: [baseline-core.json](baseline-core.json), [thinking-off-original.json](thinking-off-original.json), [ambiente.json](ambiente.json), [residencia-baseline.json](residencia-baseline.json). Os JSON originais permanecem preservados.
