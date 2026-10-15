# Medição de desempenho da Sylph

O teste `SylphPerformanceTest` faz chamadas reais somente com `-Dsylph.perf=true`. Usa os modelos já instalados, o índice existente e uma cópia da configuração local. Não prepara índices, não instala modelos/dependências e não salva escolhas de chunking ou reranking.

Uma instância de `OllamaClient`, `CsvKnowledgeStore`, `HybridRetriever` e `RagService` é compartilhada por todos os casos do processo. O teste não faz aquecimento oculto. A primeira pergunta e as repetições devem ser distinguidas pelo campo `order`; executar outro processo reinicia os caches Java, mas não necessariamente descarrega modelos residentes no Ollama. O estado de residência e a carga externa da máquina também afetam comparações.

## Execução

No PowerShell, passar propriedades Maven como argumentos individuais entre aspas. Use um nome novo por execução para preservar a evidência anterior.

Em uma pasta de projeto sincronizada por OneDrive, prefira gerar os resultados fora da pasta sincronizada e copiá-los para `docs/desempenho` **depois que o processo terminar**. Não abrir/ler o JSON enquanto o harness o atualiza. Exemplo de diretório de trabalho das evidências:

```powershell
$sylphPerfOutput = Join-Path $env:LOCALAPPDATA 'Temp/SylphPerformance'
mvn "-Dtest=SylphPerformanceTest" "-Dsylph.perf=true" "-Dsylph.perf.phase=baseline" "-Dsylph.perf.runName=baseline-core" "-Dsylph.perf.output=$sylphPerfOutput" test
```

Os comandos abaixo mantêm o destino padrão para referência. Ao usar o diretório temporário recomendado, passe `sylph.perf.output` em cada execução e ajuste `sylph.perf.baseline` para o JSON concluído nesse mesmo diretório.

```powershell
mvn "-Dtest=SylphPerformanceTest" "-Dsylph.perf=true" "-Dsylph.perf.phase=baseline" "-Dsylph.perf.runName=baseline-core" test
```

Depois das otimizações, repetir as mesmas perguntas, métodos e quantidade de repetições. O caminho de baseline gera uma comparação no relatório da nova execução.

```powershell
mvn "-Dtest=SylphPerformanceTest" "-Dsylph.perf=true" "-Dsylph.perf.phase=optimized" "-Dsylph.perf.runName=optimized-core" "-Dsylph.perf.baseline=docs/desempenho/baseline-core.json" test
```

Variantes adicionais devem ter nomes próprios, para que resultados exploratórios não substituam a comparação principal.

```powershell
mvn "-Dtest=SylphPerformanceTest" "-Dsylph.perf=true" "-Dsylph.perf.phase=thinking" "-Dsylph.perf.runName=thinking-comparison" "-Dsylph.perf.cases=prazo,supervisores" "-Dsylph.perf.thinking=true,false" test
mvn "-Dtest=SylphPerformanceTest" "-Dsylph.perf=true" "-Dsylph.perf.phase=controls" "-Dsylph.perf.runName=controls" "-Dsylph.perf.cases=ausente,condicoes" "-Dsylph.perf.reranking=false,true" test
```

| Propriedade | Padrão | Uso |
|---|---|---|
| `sylph.perf.phase` | `baseline` | Identificação da fase; não altera a implementação. |
| `sylph.perf.runName` | fase e horário local | Nome simples e único dos arquivos JSON/Markdown. |
| `sylph.perf.output` | `docs/desempenho` | Diretório de evidências. |
| `sylph.perf.cases` | `prazo,supervisores` | Seleção entre `prazo`, `supervisores`, `condicoes`, `ausente`. |
| `sylph.perf.methods` | escolha local | `recursivo`, `semantico` ou ambos separados por vírgula. |
| `sylph.perf.repetitions` | `1` | Repetições da mesma matriz, entre 1 e 20. |
| `sylph.perf.thinking` | `local` | `local`, `true`, `false` ou lista de variantes. |
| `sylph.perf.reranking` | `local` | `local`, `true`, `false` ou lista de variantes. |
| `sylph.perf.caseTimeoutSeconds` | `1200` | Limite total por pergunta; também se aplicam os timeouts HTTP configurados. |
| `sylph.perf.baseline` | sem comparação | Caminho do JSON de uma execução anterior. |

O teste preserva `top_k`, candidatos e limites locais de recuperação. O override de thinking altera a geração inicial; auditoria, reparo e reranking mantêm `think: false` e as mesmas verificações.

## Evidências e leitura

Os arquivos são atualizados atomicamente ao iniciar e ao concluir cada caso. Se o processo parar, o último caso permanece `running`. Quando um limite é atingido, o resultado é `timeout` ou `error`, com as chamadas e a execução parcial disponíveis. Se uma chamada não encerrar após cancelamento, o teste para para evitar carga sobreposta no Ollama.

Uma trava de leitura/sincronização também pode deixar a versão principal com status antigo. O harness tenta novamente erros de substituição por até aproximadamente 1 segundo por arquivo, sem perder o sinal de interrupção da thread. Se o destino continuar bloqueado, preserva o conteúdo completo em `<nome>.recovery-<UUID>.json`/`.md` único e imprime o caminho; se até essa renomeação falhar, mantém o `.tmp` intacto. Essa falha de publicação não é transformada em falha do RAG. Conferir os caminhos de recuperação no log antes de interpretar um `running` antigo como inferência ainda ativa. O Markdown aponta para o JSON efetivamente salvo quando houver recuperação.

O JSON contém a configuração efetiva, hashes, manifesto do índice, inventário de modelos, resposta completa, fontes, auditorias inicial/final, tentativa de reparo, métricas Ollama por chamada e totais. `-1` em um campo nativo significa que o servidor não forneceu a métrica; o total fica `null` quando nenhuma chamada a informou. Nanossegundos são as unidades nativas do Ollama. Tokens de pensamento, quando presentes, fazem parte do `eval_count`; `thinkingCharacters` mede caracteres de pensamento devolvidos, não estima tokens.

Os tempos por estágio são exclusivos: recuperação exclui reranking; geração exclui reparo; auditoria inicial e auditoria após reparo são separadas. O tempo total inclui as demais operações Java e validações. Somar `auditTotal` com seus dois componentes duplicaria a auditoria. Chamadas em erro não equivalem a uma resposta concluída, mesmo quando consumiram vários minutos ou tokens.

Média e máximo contam apenas casos `completed`; a quantidade de erros/timeouts aparece separadamente. Uma única amostra mede esse caso e não estabelece percentil ou uma garantia de latência. Relatar também o máximo das tentativas interrompidas e a quantidade de amostras, quando houver.

## Comparação factual

O gabarito é explícito por pergunta e traz os spans reais das páginas pertinentes. No caso simples, o prazo de cadastro de empresa é **4 dias úteis** (página 4), diferente da liberação/validação em 7 dias. Na pergunta composta, o acadêmico representa a IES (página 15), e o profissional de campo é designado pela empresa (página 16). A condição de ser tutor não é universal. O caso `condicoes` exige preservar as diferenças de cadastro/vínculo entre os cursos. O caso `ausente` solicita um valor universal que não deve ser inventado.

As verificações programáticas reexecutam a análise de citações e guardas de números, negações e atribuições, além de mostrar sinais de cobertura dos critérios. Esses sinais servem à triagem: uma paráfrase pode mudar o sinal sem mudar a precisão. Nem uma auditoria interna positiva nem a presença das palavras atribui automaticamente um acerto factual.

Antes de aceitar uma otimização, revisar cada afirmação de antes/depois contra as fontes citadas e o gabarito, incluindo atores, modalidade, condições, exceções, informação ausente e comportamento do reparo. Rejeitar a otimização se houver regressão factual confirmada. Uma resposta mais curta que omite um requisito solicitado não equivale à resposta completa só por ter terminado mais rápido.

## Reranking isolado

`RerankingPerformanceTest` executa apenas `retrieveDetailed`, primeiro com reranking desligado e depois ligado, sem gerar/auditar/reparar respostas. Uma instância de cliente/store/retriever é compartilhada, portanto a recuperação desligada aquece o embedding da mesma pergunta e os índices Java. Comparar separadamente `rerankingMillis`, que não inclui esse aquecimento. O JSON preserva candidatos, textos/spans, scores, ordem final, fallback e métricas nativas de cada chamada.

```powershell
mvn "-Dtest=RerankingPerformanceTest" "-Dsylph.perf.rerankingOnly=true" "-Dsylph.perf.phase=baseline" "-Dsylph.perf.runName=baseline-reranking" test
mvn "-Dtest=RerankingPerformanceTest" "-Dsylph.perf.rerankingOnly=true" "-Dsylph.perf.phase=optimized" "-Dsylph.perf.runName=optimized-reranking" "-Dsylph.perf.rerankingRepetitions=2" test
```

O caso padrão é `prazo`; `sylph.perf.cases=supervisores` seleciona a pergunta composta. `sylph.perf.method` escolhe o chunking, preservando a escolha local por padrão. `sylph.perf.rerankingRepetitions` varia de 1 a 5; uma segunda execução ligada permite inspecionar se o cache de reranking evita outra chamada, sem supor antecipadamente que isso ocorrerá. Os controles locais de `top_k`, candidatos e timeout são mantidos.
