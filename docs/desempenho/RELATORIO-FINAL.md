# Desempenho da Sylph — resultado final de 15/10/2026

A versão final reduziu a **média de 320,370 para 228,090 segundos (−28,80%)** e o **máximo de 514,056 para 394,670 segundos (−23,22%)** nas duas perguntas comparadas. As respostas finais foram confrontadas diretamente com as fontes: o prazo passou a ser corretamente informado como **4 dias úteis**, e a resposta composta preservou as duas funções/responsáveis com citações das páginas **15 e 16**.

A pergunta comum final levou **61,509 s**: houve redução de **51,45%**, mas a meta de menos de 60 segundos **não foi atingida nessa amostra do JAR entregue**. A composta ainda levou 6 min 34,670 s. Nenhuma tentativa mais rápida com regressão factual foi escolhida como resultado final.

## Gargalo identificado

O baseline da pergunta composta consumiu **239,027 s de geração**, **228,445 s nas duas auditorias** e **46,351 s no reparo**, contra apenas **0,116 s de recuperação**. Portanto, o atraso superior a seis minutos foi dominado pelo fluxo de inferência/validação/reparo, e não pelo acesso ao índice RAG.

O Ollama informou **19.362 tokens de entrada** somados entre chamadas e **1.927 de saída** nesse caso; a geração avançou a aproximadamente **3,62 tokens/s**. No primeiro caso simples, o carregamento acumulado foi **20,466 s**. As medições usam contadores e durações nativos, sem estimar tokens por caracteres.

## Alterações entregues

- Instrumentação separa recuperação, reranking, geração, auditoria inicial, reparo e auditoria pós-reparo. Registra chamadas completas por caso, tentativas, erros, tokens, cache de prompt e durações nativas de carregamento/prefill/inferência. Pensamento é registrado somente como quantidade de caracteres retornados e no `eval_count` total; seu conteúdo textual não é armazenado.
- O contexto reúne somente intervalos de origem compatíveis por documento/página e offsets, removendo sobreposições idênticas. Mantém texto, proveniência, IDs e ordem; lacunas entre intervalos permanecem explicitamente marcadas. Não corta evidências nem reduz `top_k`.
- A auditoria recebe cada fonte completa uma vez e referências por afirmação, reduzindo duplicação do payload. A versão entregue mantém **citações textuais verificadas pelo decoder original**: os protocolos experimentais de provas por IDs foram rejeitados após testes reais.
- Foi reduzido trabalho repetido de tokenização/estatísticas lexicais. A reutilização de snapshots CSV, do **cliente HTTP e do cache LRU de embeddings já existia e foi medida/preservada**, sem apresentá-la como cache novo criado nesta tarefa. O armazenamento continua validado antes de reutilizar seu snapshot; verificações de integridade, dimensões e modelos permanecem ativas.
- As chamadas usam `keep_alive` configurável, padrão **900 s**. Uma geração já enviada que excede seu timeout não é repetida automaticamente; falhas de conexão e condições transitórias continuam tratadas. Isso evita aguardar e possivelmente enfileirar inferência duplicada.
- A geração recebe instruções de concisão documental, preservando sujeitos, qualificadores, citações e todos os itens solicitados. O reparo continua extrativo e seu schema limita as seleções aos IDs de trechos realmente disponíveis, mantendo a validação completa em Java.
- O reranker usa IDs locais ao lote na requisição, conservando a proveniência estável em Java. Seu schema exige o tamanho real do lote e IDs dentro dele. Continua opcional, com validação das notas, timeout e fallback para a ordem híbrida.
- O harness exporta cada caso de forma incremental, tenta novamente bloqueios de escrita por aproximadamente 1 s e preserva arquivo de recuperação único se necessário. Os experimentos finais foram escritos fora do OneDrive e copiados para documentação depois do término.

Foram preservados **`top_k=8`**, modelos instalados, corpus e escolhas manuais de chunking/reranking. Não houve troca de modelo nem instalação de dependências. Auditoria semântica, validação de citações, números/negações, responsabilidades, reparo e tratamento conservador de informação ausente continuam ativos. O **thinking da geração permanece ligado**, como na configuração local original; auditoria e reranking estruturados continuam sem thinking. O terminal permanece simplificado.

## Comparação final antes/depois

Uma amostra por pergunta/fase, com chunking recursivo e reranking desligado. O baseline simples estava **factualmente errado**: respondia 7 dias úteis quando a fonte estabelece 4. Assim, a tabela mede conclusões técnicas e também informa sua correção; não apresenta o baseline inteiro como conjunto de respostas corretas.

| Caso | Baseline (s) | JAR final (s) | Redução | Revisão técnica da resposta final |
|---|---:|---:|---:|---|
| Prazo de cadastro de empresa | 126,684 | **61,509** | **51,45%** | 4 dias úteis, página 4; corrige defeito pré-existente |
| Supervisores acadêmico/campo | 514,056 | **394,670** | **23,22%** | Duas definições/responsáveis sustentados, páginas 15/16; regras adicionais de cadastro com ressalva de clareza |
| **Média** | **320,370** | **228,090** | **28,80%** | Duas amostras |
| **Máximo** | **514,056** | **394,670** | **23,22%** | Composta permanece acima de seis minutos |

Estágios em segundos, sem duplicar componentes: recuperação exclui reranking; geração exclui reparo; auditorias são separadas. O total de parede inclui demais operações Java.

| Fase/caso | Recuperação | Reranking | Geração | Auditoria 1 | Reparo | Auditoria 2 |
|---|---:|---:|---:|---:|---:|---:|
| Baseline / prazo | 6,847 | 0 | 90,530 | 27,055 | 0 | 0 |
| Baseline / supervisores | 0,116 | 0 | 239,027 | 89,566 | 46,351 | 138,879 |
| Final / prazo | 0,398 | 0 | 47,861 | 12,132 | 0 | 0 |
| Final / supervisores | 0,047 | 0 | 230,174 | 75,294 | 14,908 | 74,235 |

Na composta final, a primeira geração continha atribuições e citações sem apoio suficiente. As guardas rejeitaram suas quatro unidades; o reparo extrativo e a segunda auditoria foram executados. A resposta entregue conserva as duas definições e dois trechos de cadastro nas páginas corretas, sem aviso indevido de ausência. **O ganho não veio de remover auditoria ou dispensar reparo.**

Tokens de entrada somam todas as chamadas que fornecem a métrica, incluindo embedding da pergunta. Saída inclui tokens de pensamento quando ativado.

| Fase/caso | Tokens entrada | Tokens saída | Caracteres pensamento | Contexto (caracteres) | Reparo |
|---|---:|---:|---:|---:|---|
| Baseline / prazo | 8.598 | 437 | 718 | 20.166 | Não |
| Baseline / supervisores | 19.362 | 1.927 | 2.728 | 19.621 | Sim |
| Final / prazo | 8.620 | 335 | 737 | 18.109 | Não |
| Final / supervisores | 19.097 | 2.368 | 3.024 | 17.669 | Sim |
| **Total baseline** | **27.960** | **2.364** | | | |
| **Total final** | **27.717** | **2.703** | | | |

A entrada caiu **0,87%**, mas a saída **aumentou 14,34%**. Não se alega redução geral de tokens. O contexto em caracteres diminuiu 10,20%/9,95%, porém instruções, auditorias e quantidade de saída também afetam os contadores. As gerações finais avançaram a **4,54 tokens/s** no simples e **4,45 tokens/s** no complexo, por `eval_count / eval_duration`; velocidades de chamadas diferentes não isolam uma aceleração do motor pelo código.

## Conferência factual e limitações da resposta

A [revisão factual](revisao-factual.md) foi realizada pelo agente por confronto direto com respostas e spans das fontes; **não é apresentada como avaliação acadêmica humana**. O prazo final corresponde à linha de cadastro de empresa da página 4. Na composta, o acadêmico é o representante da IES encarregado de acompanhar o estágio, e o campo é designado pela empresa para orientar e acompanhar o estudante nas suas instalações, com páginas 15 e 16 respectivas. Não se identificou troca de atores ou perda de uma das duas partes centrais da Q10 frente ao baseline reparado.

Há uma ressalva de apresentação: o trecho adicional de cadastro acadêmico começa por **“Nesses casos”**, sem reproduzir antes dele a frase que enumera os cursos. A expressão condicional e **“em alguns casos”** foram preservadas; a unidade seguinte enumera os mesmos seis cursos para o cadastro de campo, e a fonte completa da página 15 contém o antecedente. Não se identificou afirmação universal explícita nem contradição factual concreta no pedido core, mas esse trecho extra **não é uma orientação autossuficiente para todos os cursos**. Essa limitação fica registrada, sem transformar a auditoria positiva em prova automática de acerto. O caso real específico `condicoes` não foi executado, e não se declara aprovação desse caso.

## Reranking e reutilização de recursos

O probe isolado de recuperação não gera/audita respostas. Mantém os mesmos recursos no processo e executa reranking desligado/depois ligado.

| Reranking ligado / pergunta de prazo | Tempo exclusivo (s) | Tokens entrada | Tokens saída | Status |
|---|---:|---:|---:|---|
| Original | 37,664 | 4.978 | 171 | `applied` |
| Otimizado, primeira chamada | **21,659** | **4.380** | **105** | `applied` |
| Otimizado, repetição | 16,963 | 4.380 | 105 | `applied` |

A primeira chamada caiu **42,49%**, com entrada −12,01% e saída −38,60%. Os 15 candidatos e sua ordem híbrida inicial foram preservados, assim como `top_k=8`, lote pontuado de 12 e evidência da página 4; o mesmo trecho relevante ficou em primeiro, com nota 10. Não houve fallback nesses probes. Isso confirma preservação da evidência exercitada, não acerto de uma resposta que o probe não gera.

A repetição de 16,963 s ainda fez chamada HTTP e reutilizou **4.379 tokens de prompt** no Ollama: **não é cache de resultado do reranker**. Já a recuperação desligada repetida caiu de **548 para 15 ms**, sem nova chamada HTTP de embedding, demonstrando reutilização da pergunta/recursos Java. Não se mistura a repetição aquecida com a primeira chamada como se fossem partidas frias. Fontes: [original](baseline-reranking.json) e [otimizado](optimized-reranking.json).

## Controle final de informação ausente e chunking semântico

O controle final, separado da média core, perguntou o valor universal de auxílio-transporte e levou **92,788 s**: recuperação 0,359 s; geração 45,474 s; auditoria inicial 44,111 s; reparo 2,790 s; verificação posterior local 0 s. Ambas as estruturas de auditoria foram válidas; não houve protocolo inválido. Entrada: **13.933 tokens**; saída: **661 tokens**.

A geração declarou não encontrar o valor, mas citou a página 33. A auditoria rejeitou essa evidência insuficiente; o reparo devolveu a mensagem padrão de informação não encontrada, sem citações. O final manteve **`encontrou=false`, nenhuma fonte, nenhuma quantia inventada e orientação para confirmar com a coordenação**. O texto final descreve dificuldade de validação/confirmação: é um fallback conservador, não prova documental exaustiva de inexistência do benefício. A proteção foi preservada.

O controle semântico anterior levou 341,007 s, com uma auditoria posterior de 231,820 s/1.349 tokens rejeitada por protocolo inválido. O controle final foi válido e mais rápido, mas percorreu outro caminho e usou cache de prompt diferente; não se atribui toda a diferença ao código. O controle de prazo semântico anterior informou corretamente 4 dias úteis em 124,452 s, com um fragmento extra irrelevante da página 41, limitação preservada no histórico. Fontes: [controle final](release-accepted-absent-semantico.json) e [controle anterior](optimized-controls-semantico.json).

## Otimizações rejeitadas

Desligar o thinking isoladamente piorou a máxima para **558,666 s**, não corrigiu o prazo e produziu reparo inválido. Tentativas posteriores mais rápidas sem thinking omitiram informação ou acrescentaram aviso de ausência indevido; foram rejeitadas. O thinking da geração permaneceu ligado.

Os protocolos experimentais de provas por IDs também foram rejeitados: o array devolveu IDs duplicados; o objeto produziu um reparo irrelevante da página 24 e terminou sem as definições disponíveis. O wire foi revertido para fontes completas compartilhadas e citações textuais, mantendo os demais ganhos e as guardas. Os arquivos com nomes históricos “final”, “release” ou “accepted” só recebem aprovação após revisão; seus nomes não são evidência de acerto. [Revisão detalhada](revisao-factual.md) e [tabelas históricas](comparacao-provisoria.md) preservam as tentativas, sem sobrescrevê-las.

## Testes e JAR final

- **Maven final:** `mvn -Dmaven.repo.local=.m2 -o -B package` — **342 testes, 338 passaram, 4 skips, 0 falhas e 0 erros**.
- **Testes reais finais Ollama:** core recursivo e controle ausente semântico concluídos com Maven `SUCCESS`; respostas/guardas/reparos conferidos acima. Probes reais de reranking concluídos. As experiências rejeitadas permanecem identificadas como tais.
- **Terminal:** menu `[1] Recursivo / [2] Semântico / [0] Sair`; entrada 0 encerrou com código **0**. `executar-sp21.ps1 -ValidarSomente` encerrou com código **0**, com classes presentes.
- **JAR:** [sylph-unopar-java-1.0.0.jar](../../target/sylph-unopar-java-1.0.0.jar), **17.596.781 bytes**.
- **SHA-256:** `E34D7F5EEBBD961DFB3DE7EE7E52DCB923262A3B5D4C6B146E3F272E852B0138`.
- Configuração local e manifesto do índice mantiveram seus hashes originais; não foram reconstruídos para os experimentos.

Identificação e hash: [JAR-IDENTIFICACAO.json](JAR-IDENTIFICACAO.json) e [JAR-SHA256.txt](JAR-SHA256.txt). Os dados finais de desempenho pertencem a [release-accepted-core.json](release-accepted-core.json) e [release-accepted-absent-semantico.json](release-accepted-absent-semantico.json), sem transferir os tempos melhores de versões intermediárias ao JAR entregue.

## Limites de interpretação

O equipamento observado usa Core i5-13420H, aproximadamente 25,46 GB de RAM e RTX 4050 Laptop de 6.141 MiB. Cada fase reinicia caches Java, mas não força descarregamento dos modelos ou limpeza de cache de prompt do Ollama. O carregamento acumulado simples/complexo foi **20,466/0,689 s no baseline** e **0,107/0,074 s no final**. As gerações finais reaproveitaram **7.411/3 tokens de prompt**, contra **0/3 no baseline**. No controle ausente final, geração/reparo reutilizaram 7.123/5.293 tokens. Estado de residência, cache, carga externa e caminhos de validação diferem; as reduções descrevem essas amostras, sem isolar a contribuição de cada edição.

Há uma amostra final por pergunta, sem percentis ou garantia de latência. O simples final ficou **1,509 s acima** da meta de 60 s, e o complexo continua acima de seis minutos. Resultados abaixo de 60 s de tentativas rejeitadas ou da referência intermediária não são usados como promessa do JAR final. Nenhum modelo foi trocado e nenhuma evidência foi descartada para forçar essa meta.

Não há aquecimento oculto no harness: `health` consulta metadados sem gerar inferência e o gabarito é lido depois do caso cronometrado. Reutilização demonstrada não substitui repetições controladas frio/quente. A auditoria continua sujeita a erros; guardas, reparo, revisão e o fallback conservador permanecem necessários.

Evidências adicionais: [baseline](baseline-core.json), [referência intermediária](optimized-precision-core.json), [ambiente](ambiente.json), [residência observada](residencia-baseline.json) e [reprodução](COMO-MEDIR.md).
