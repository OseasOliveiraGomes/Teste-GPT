# Sylph UNOPAR

A Sylph é uma tutora acadêmica local que consulta o Manual de Estágio Obrigatório da UNOPAR e outros documentos selecionados pelo usuário. As respostas apresentam fontes e páginas, passam por auditoria de evidências e podem ser reparadas com trechos já recuperados.

O projeto preserva Java 21, Spring Boot 3.5.16, Maven, Apache PDFBox 3.0.8, Ollama e armazenamento em CSV. A interface é exclusivamente pelo terminal; a aplicação não inicia servidor HTTP. As chamadas HTTP são feitas ao Ollama local.

> Protótipo acadêmico independente, sem homologação institucional. Uma citação verificável ou auditoria aprovada não substitui a revisão do conteúdo.

## Instalação no Windows

Instale o [JDK 21](https://adoptium.net/), o [Apache Maven](https://maven.apache.org/) e o [Ollama](https://ollama.com/download). Configure Java e Maven no `PATH`; o projeto pode ser aberto diretamente no VS Code.

Na raiz do repositório, confira:

```powershell
java --version
mvn --version
ollama --version
ollama list
```

O `Modelfile` atual usa `qwen3:8b`; a configuração de exemplo seleciona `sylph-unopar` para geração e `nomic-embed-text` para embeddings. Quando decidir instalar esses modelos, execute explicitamente:

```powershell
ollama pull qwen3:8b
ollama pull nomic-embed-text
ollama create sylph-unopar -f Modelfile
```

A aplicação não baixa modelos nem troca silenciosamente os nomes configurados. Mantenha o Ollama em execução; se o serviço estiver parado, execute `ollama serve` em outro terminal.

Crie a configuração apenas se ela ainda não existir:

```powershell
if (-not (Test-Path -LiteralPath config.json)) {
    Copy-Item -LiteralPath config.example.json -Destination config.json
}
```

O `config.json` existente foi preservado. Valores antigos continuam válidos; campos novos ausentes recebem os padrões de `ProjectConfig`. O exemplo inclui todos os campos disponíveis e usa `top_k: 4`; a configuração local pode usar outros valores.

Compile e execute a partir da raiz, onde ficam `config.json`, `dados/` e `testes/`:

```powershell
mvn clean test
mvn clean package
java -jar target/sylph-unopar-java-1.0.0.jar
```

Para validar os modelos antes de preparar o índice:

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar diagnostico
java -jar target/sylph-unopar-java-1.0.0.jar modelos
```

## Consulta do estudante

A inicialização sem argumentos mantém o menu simples:

```text
[1] Chunking Recursivo
[2] Chunking Semântico
[0] Sair
```

Escolha um método e digite a pergunta. Durante a sessão, `metodo` abre novamente a seleção, `ajuda` apresenta os comandos e `sair` encerra a aplicação. A escolha interativa vale para a sessão e não altera automaticamente a configuração salva. Saudações e agradecimentos simples dispensam a recuperação documental.

Exemplo:

```text
Qual é o prazo para análise do cadastro de uma empresa?
```

Sem evidência suficiente, a resposta reconhece a limitação e orienta a consulta à coordenação. Perguntas devem conter de 1 a 2.000 caracteres. Os detalhes técnicos ficam nas ferramentas de desenvolvimento; mantenha `debug_rag: false` para a consulta normal.

## Preparação e documentos

O manual principal continua sendo `dados/Manual Estagio Obrigatorio UNOPAR 2026-2.pdf`. PDF precisa conter texto extraível; não há OCR. TXT e Markdown devem estar em UTF-8 e recebem uma página lógica, número 1.

Na primeira preparação, arquivos PDF, TXT, MD e Markdown diretamente em `dados/` são descobertos. Nas atualizações seguintes, somente os documentos registrados são processados; use `adicionar` para registrar novos arquivos:

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar preparar
java -jar target/sylph-unopar-java-1.0.0.jar documentos
java -jar target/sylph-unopar-java-1.0.0.jar adicionar "C:\Materiais\regulamento.md"
java -jar target/sylph-unopar-java-1.0.0.jar estatisticas
```

`preparar` compara SHA-256 dos documentos e a configuração de chunking/modelo. Documentos inalterados reutilizam seus chunks e embeddings. Para atualizar um documento registrado, altere o arquivo de origem e execute novamente `preparar`. Nomes de arquivos iguais são rejeitados para evitar citações ambíguas.

`remover` recebe o identificador mostrado por `documentos` e publica um índice sem esse documento. O arquivo de origem e as gerações anteriores permanecem disponíveis:

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar remover "doc-identificador-mostrado-pelo-comando-documentos"
```

Para recalcular tudo, use `recriar`. Se o manifesto estiver corrompido, esse comando preserva uma cópia para diagnóstico e redescobre os documentos em `dados/`; arquivos externos precisam ser adicionados novamente.

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar recriar
```

A preparação usa lock, arquivos temporários, validação e publicação do manifesto após concluir a geração. Falhas de extração, embeddings ou validação não substituem o índice ativo anterior. Os CSVs legados `dados/base_recursivo.csv` e `dados/base_semantico.csv` continuam legíveis e não são sobrescritos pela preparação avançada.

Estrutura dos artefatos locais:

```text
dados/
├── Manual Estagio Obrigatorio UNOPAR 2026-2.pdf
├── base_recursivo.csv             # referência legada, se existente
├── base_semantico.csv             # referência legada, se existente
├── indice-manifesto.json          # aponta para a geração ativa
├── indices/g-.../
│   ├── manifesto.json
│   ├── base_recursivo.csv
│   └── base_semantico.csv
├── referencia/                    # referência calculada separadamente
└── relatorios/                    # chunking: JSON, CSV e Markdown
resultados/<execucao>/              # comparações e avaliações
```

O manifesto registra documento, nome, caminho local, hash, páginas físicas, modelo de embeddings, dimensão, versão e integridade dos arquivos. Chunks registram identificadores estáveis, documento, método e intervalos `[início, fim)` em cada página de texto limpo. Quando um chunk atravessa páginas, cada trecho mantém sua página; a montagem das fontes não atribui todo o conteúdo à primeira página.

## Chunking e medições reais

O recursivo procura limites de seções e subtítulos, parágrafos, linhas, frases e palavras, nessa ordem. Aplica máximo, tratamento de blocos curtos e overlap configuráveis. Regras e itens de lista que cabem no limite são mantidos inteiros; regras maiores precisam ser divididas para respeitar o máximo. O overlap é alinhado a palavras e contabilizado no relatório.

O semântico usa unidades textuais, embeddings e cosseno entre unidades adjacentes para detectar mudanças de assunto. Mínimo e máximo controlam a fragmentação; unidades longas são divididas por limites textuais. Embeddings iguais são reaproveitados, sem excluir ocorrências repetidas da fonte. Textos com menos de 700 caracteres também podem apresentar transições reais. `chunk_cross_pages` permite continuidade entre páginas nos dois métodos.

O [diagnóstico original](docs/evolucao/diagnostico-inicial.json) encontrou **56 chunks recursivos e 57 semânticos**, em **56 páginas extraídas**; 55 páginas tinham as mesmas divisões. O algoritmo semântico antigo frequentemente retornava a própria página ou delegava ao recursivo antes de usar embeddings. O processamento separado por página também limitava as diferenças.

Na execução local de 14/10/2026 com o manual preservado, a nova extração reteve as **57 páginas físicas com texto**, incluindo uma página curta antes descartada. O relatório medido apresentou:

| Medida | Recursivo avançado | Semântico avançado |
|---|---:|---:|
| Chunks | 41 | 85 |
| Tamanho mínimo / máximo | 507 / 1.344 | 280 / 1.301 |
| Tamanho médio, caracteres | 1.085,878 | 430,988 |
| Páginas cobertas | 57 | 57 |
| Seções detectadas por heurística | 83 | 83 |
| Cobertura de caracteres não brancos | 100% | 100% |
| Cobertura bruta, incluindo espaços | 100% | 99,784% |
| Repetição da fonte, incluindo overlap | 17,510% | 0% |
| Tempo de chunking | 705 ms | 58.558 ms |

Esses tempos incluem os embeddings de unidades usados pelo semântico e excluem os embeddings finais dos chunks. Dependem do hardware, caches e modelos. Os métodos avançados produziram 0% de segmentos idênticos e 47,584% de sobreposição textual pela média simétrica do melhor Jaccard de conjuntos de palavras. Essa medida não é a mesma que cobertura da fonte, overlap intencional ou percentual de páginas com divisões idênticas do diagnóstico antigo.

A diferença na cobertura bruta semântica corresponde a espaços nas fronteiras dos segmentos; nenhum caractere não branco do texto limpo foi perdido. A cobertura é relativa à extração textual, não às imagens, tabelas ou informações que o PDFBox não consiga extrair. Quantidade de chunks, diferenças nas divisões e cobertura textual não demonstram qualidade das respostas.

Relatórios novos são gerados em `dados/relatorios/` durante a preparação. Eles registram distribuição de tamanhos, páginas, seções, cobertura, duplicações potenciais, overlap e tempos. Documentos curtos ou semanticamente uniformes podem produzir divisões semelhantes; o sistema não força diferenças para atingir contagens desejadas.

## Recuperação, reranking e auditoria

A recuperação separa busca vetorial, BM25, união dos candidatos, fusão com RRF e heurísticas, reranking opcional e expansão do contexto. A busca lexical preserva candidatos com correspondência de termos mesmo quando o cosseno é baixo, incluindo siglas, nomes, códigos e números de um dígito. Limites de candidatos e contexto controlam o custo.

O reranker usa o LLM configurado em **uma chamada estruturada por lote**, com notas de 0 a 10. IDs, quantidade, duplicações e valores são validados estritamente; empates preservam a ordem híbrida. O lote contém chunks completos, respeita limites de caracteres/candidatos e usa timeout próprio. Falha HTTP, timeout ou JSON inválido mantém automaticamente a classificação híbrida original. Os estados `disabled`, `applied`, `fallback` e `no_candidates` aparecem nos diagnósticos e avaliações.

O padrão é `reranking_ativo: false`. Para alternar e salvar a preferência:

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar reranking on
java -jar target/sylph-unopar-java-1.0.0.jar reranking off
```

As verificações existentes de citação, responsabilidade, auditoria e reparo foram preservadas. IDs de trechos podem ser resolvidos para documento e página somente quando correspondem às fontes recuperadas. Validações programáticas complementam a análise do LLM, verificando fontes, páginas, citações literais, números, negativas e atribuições. No reparo, o modelo seleciona evidências existentes e o Java fornece os trechos; a seleção não autoriza inventar texto documental.

As verificações semânticas e lexicais têm limites. Perguntas compostas podem receber resposta parcial; ausência de informação ou conflitos exigem cautela e consulta à coordenação. Documentos e perguntas são tratados como dados não confiáveis nos prompts; instruções encontradas dentro deles não devem alterar a função da tutora.

## Modo desenvolvedor

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar dev
```

| Comando no prompt `dev>` | Efeito |
|---|---|
| `status` | Método salvo, disponibilidade da base, reranking e índice |
| `diagnostico` | Conectividade, modelos ausentes, latência e índice |
| `modelos` | Modelos configurados e instalados |
| `modo recursivo` / `modo semantico` | Salva o método padrão em `config.json` |
| `reranking on` / `reranking off` | Salva a preferência de reranking |
| `preparar` / `recriar` | Atualiza ou recalcula as bases avançadas |
| `preparar-referencia` | Calcula a referência separada quando necessária |
| `documentos` / `estatisticas` | Documentos registrados e estatísticas |
| `adicionar "caminho"` / `remover documentoId` | Modifica os documentos do índice |
| `perguntar` | Solicita uma pergunta livre |
| `testar` | Solicita uma pergunta e registra resultado com revisão pendente |
| `trechos` | Mostra evidências, páginas, cosseno, BM25 e pontuações da última consulta |
| `falhas` | Mostra diagnósticos, auditoria, reparo e chamadas recentes ao Ollama |
| `comparar [perguntas.json] [alternativas]` | Executa comparação da recuperação |
| `avaliar [perguntas.json] [alternativas]` | Executa perguntas com geração, auditoria e reparo |
| `exportar` | Informa a pasta já exportada pela última avaliação |
| `sair` | Encerra a sessão |

Comandos de manutenção, `modo`, `reranking`, `comparar`, `avaliar` e `perguntar "pergunta"` também aceitam execução direta como argumentos do JAR. `status`, `trechos`, `falhas`, `testar` e `exportar` são comandos da sessão `dev`.

## SP 2.1 e SP 2.2: comparação reproduzível

`perfil_execucao: "sp21"` usa os CSVs legados quando existem e a recuperação de referência preservada; reranking fica desativado nesse perfil. `perfil_execucao: "sp22"` usa o índice avançado, parâmetros configuráveis e reranking quando solicitado. O padrão é `sp22`.

Quando os CSVs legados não estiverem disponíveis, prepare a referência isolada:

```powershell
java -jar target/sylph-unopar-java-1.0.0.jar preparar-referencia
```

Ela usa os algoritmos antigos por página e fica em `dados/referencia/`, sem substituir os CSVs originais. Os arquivos legados continuam tendo prioridade. Uma referência recalculada usa a extração atual, que preserva páginas curtas; por isso suas contagens podem diferir das bases históricas. Para reproduzir exatamente o experimento histórico, preserve seus CSVs, hashes, modelos e configuração.

As dez perguntas acadêmicas estão em `testes/perguntas_academicas.json`. A execução padrão testa seis variantes por pergunta: SP 2.1 e SP 2.2 sem reranking, e SP 2.2 com reranking, para cada um dos dois métodos. `alternativas` acrescenta busca somente vetorial e somente lexical em SP 2.2 sem reranking, totalizando dez variantes por pergunta.

```powershell
# Recuperação e reranking: 10 perguntas x 6 variantes = 60 registros
java -jar target/sylph-unopar-java-1.0.0.jar comparar

# Geração, auditoria e eventual reparo: 60 registros
java -jar target/sylph-unopar-java-1.0.0.jar avaliar

# Inclui referências vetorial/lexical: 100 registros
java -jar target/sylph-unopar-java-1.0.0.jar avaliar testes/perguntas_academicas.json alternativas
```

`comparar` não gera nem audita respostas finais; ainda usa embeddings e, nas variantes correspondentes, o LLM de reranking. `avaliar` executa o fluxo completo e pode demorar consideravelmente no hardware local. Os dois comandos usam a mesma configuração base, alteram explicitamente as variáveis da variante e não salvam essas alterações em `config.json`.

Cada execução cria `resultados/<execucao>/` com perguntas, configuração original, inventário do Ollama, manifesto/hashes disponíveis, `resultados.json`, `resultados.csv` e `relatorio.md`. Resultados são exportados após cada caso, incluindo falhas técnicas. A configuração por variante, respostas, evidências, IDs, documentos/hashes, páginas, pontuações, auditorias, reparo e tempos permanecem registrados. CSV utiliza UTF-8 com BOM e CRLF para abertura no Windows.

A revisão humana começa como `PENDENTE`, com campos para resposta correta, parcialmente correta, incorreta, omissão, alucinação, citação correta e observações. Preencha uma cópia dos resultados para preservar o registro original. O sistema não transforma aprovação de auditoria em avaliação humana de acerto.

`evidencias_relevantes` vem vazio nas perguntas fornecidas. Somente adicione gabaritos após fundamentação documental: são aceitos IDs de chunk, `docId#pagina` ou `Nome do Documento#pagina`. Com essas anotações, são calculados precisão das evidências, Recall@K e MRR. Sem gabarito, as métricas ficam nulas e `NÃO AVALIADO`. A taxa programática de citações representa unidades associadas a evidências pelo parser; não mede veracidade. Cosseno e nota de reranking também não medem correção factual.

## Configuração

[`config.example.json`](config.example.json) contém todos os valores padrão. As opções de chunking alteram o índice: execute `preparar` após modificá-las. Mudar o modelo de embeddings também exige reindexação. Se substituir os pesos de um modelo mantendo o mesmo nome, reinicie a aplicação e execute `recriar` explicitamente; a identidade textual do modelo não descreve sozinha essa alteração.

| Campo | Padrão | Uso |
|---|---:|---|
| `chunking_ativo` | `recursivo` | Método padrão: `recursivo` ou `semantico` |
| `perfil_execucao` | `sp22` | Perfil: `sp21` ou `sp22` |
| `modelo_llm` | `sylph-unopar` | Modelo de geração, auditoria, reparo e reranking |
| `modelo_embeddings` | `nomic-embed-text` | Modelo de embeddings; alteração exige preparar novamente |
| `ollama_url` | `http://127.0.0.1:11434` | Apenas HTTP local, sem credenciais, query ou caminho adicional |
| `debug_rag` | `false` | Logs técnicos; habilite somente no desenvolvimento |
| `chunk_max_chars` | 1350 | Máximo de caracteres; válido de 100 a 16.000 |
| `chunk_min_chars` | 280 | Mínimo desejado para evitar fragmentação; limitado pelo máximo |
| `chunk_overlap_chars` | 200 | Overlap recursivo, menor que o máximo; semântico não repete a fonte |
| `semantic_unit_chars` | 220 | Referência de tamanho para dividir unidades semânticas longas |
| `limiar_semantico` | 0.66 | Transição quando cosseno adjacente fica abaixo do limiar, respeitando tamanhos |
| `chunk_cross_pages` | `true` | Permite segmentos entre páginas com metadados por trecho |
| `semantic_embedding_cache_size` | 20000 | Quantidade máxima de unidades no cache do chunker; 0 desativa retenção |
| `modo_recuperacao` | `hybrid` | `hybrid`, `vetorial` ou `lexical`; alternativas controladas em SP 2.2 |
| `top_k` | 4 | Quantidade primária selecionada, de 1 a 10, antes da expansão |
| `candidatos_recuperacao` | 12 | Limite por ranking; união avançada tem até duas vezes esse valor |
| `min_similaridade` | 0.35 | Corte vetorial; a via lexical avançada mantém candidatos com BM25 positivo |
| `peso_vetorial` | 0.65 | Peso do cosseno não negativo na pontuação híbrida |
| `peso_lexical` | 0.20 | Peso do BM25 normalizado |
| `peso_cobertura` | 0.10 | Peso da presença de termos da pergunta |
| `peso_rrf` | 0.05 | Peso do RRF na pontuação híbrida |
| `bm25_k1` | 1.5 | Saturação da frequência de termos |
| `bm25_b` | 0.75 | Normalização pelo comprimento do chunk |
| `rrf_constante` | 60 | Constante da fusão por posição |
| `expansao_paginas` | 2 | Alcance da expansão no mesmo documento, de 0 a 5 páginas |
| `trechos_contexto` | 24 | Máximo de trechos após expansão avançada |
| `limite_contexto_caracteres` | 16000 | Orçamento de texto dos trechos selecionados |
| `reranking_ativo` | `false` | Reranking opcional; ignorado no perfil SP 2.1 |
| `candidatos_reranking` | 12 | Máximo de candidatos avaliados no lote |
| `reranking_limite_caracteres` | 18000 | Limite do payload do lote; chunks completos |
| `reranking_timeout_segundos` | 45 | Timeout da chamada estruturada; falha usa ordem híbrida |
| `ollama_timeout_segundos` | 360 | Timeout das chamadas comuns |
| `ollama_tentativas` | 2 | Tentativas limitadas para falhas transitórias, de 1 a 4 |
| `ollama_keep_alive_segundos` | 900 | Residência solicitada dos modelos, de 0 a 86.400 s; 0 libera após a chamada. Falta de memória ainda pode causar descarregamento pelo Ollama |
| `cache_embeddings_entradas` | 2048 | Cache do cliente Ollama; 0 desativa retenção; limite adicional de 64 MiB |
| `max_documento_bytes` | 52428800 | Limite de arquivo: 50 MiB |
| `max_documento_caracteres` | 5000000 | Limite de texto extraído por documento |
| `temperatura_llm` | 0.1 | Temperatura da geração normal |
| `seed_llm` | 42 | Seed solicitada ao Ollama; não garante identidade entre ambientes |
| `llm_think` | `true` | Preserva o modo de geração normal; reranking estruturado usa `think: false` |

Índices e estruturas BM25 são reutilizados enquanto a revisão do armazenamento permanece válida. O cliente valida dimensões, valores finitos e respostas JSON; mantém diagnóstico de latência e tentativas sem registrar conteúdo documental nas mensagens de falha. Mudanças detectadas no inventário de modelos invalidam o cache correspondente do cliente Ollama. Esses controles reduzem trabalho repetido, mas não tornam o modelo uma fonte de verdade.

O comando `falhas` do modo desenvolvedor também mostra tempos separados de recuperação, reranking, geração, auditoria, reparo e segunda auditoria. As últimas 30 chamadas conservam os contadores nativos de tokens, tempo de carregamento e velocidade de inferência do Ollama. Um timeout de geração não repete automaticamente toda a inferência; falhas transitórias de conexão e HTTP continuam com tentativas limitadas. A consulta normal mantém o terminal simples.

O contexto une apenas intervalos de origem compatíveis, sem cortar evidências, e indica lacunas entre intervalos separados. A auditoria recebe cada fonte completa uma vez e referências por afirmação; todas as verificações programáticas e o fluxo de reparo permanecem ativos. As medições, tentativas rejeitadas e limites de comparação estão no [relatório de desempenho](docs/desempenho/RELATORIO-FINAL.md); a reprodução está em [COMO-MEDIR.md](docs/desempenho/COMO-MEDIR.md). O Thinking permanece ligado na configuração de referência: desligá-lo apresentou regressões na pergunta composta durante os testes.

## Arquitetura e referência RagTutor

| Responsabilidade | Classes principais |
|---|---|
| Extração, documentos e preparação | `PdfPages`, `BasePreparation` |
| Segmentação avançada e referência histórica | `TextChunker`, `BaselineTextChunker`, `SourceSpan` |
| CSV, manifesto, integridade e cache | `CsvKnowledgeStore`, `IndexManifest`, `IndexedDocument`, `KnowledgeChunk` |
| Recuperação e reranking | `HybridRetriever`, `LlmReranker` |
| Contexto, geração e reparo | `RagService`, `OllamaClient` |
| Citações e evidências | `StableCitations`, `AnswerEvidence`, `AttributionEvidence`, `ClaimGrounding`, `EvidenceConflicts` |
| Comparação e exportação | `AcademicEvaluation`, `ChunkingReport` |
| Terminal e configuração | `DevTerminal`, `SettingsService`, `ProjectConfig` |

A referência complementar inspecionada foi o arquivo local `RagTutor.zip`, contendo `RagTutor.java` e `README.md`. Foram aproveitadas as ideias de indexação de vários documentos locais e reranking após recuperação híbrida. No RagTutor, `notaRerank` chama o chat por candidato e extrai o primeiro número com regex. Na Sylph, a adaptação usa um lote JSON com schema, validação estrita, limites, timeout e fallback. A Sylph mantém sua arquitetura Spring/Maven, PDFBox, CSV, dois métodos de chunking e auditoria/reparo; não depende do código ou dos documentos do RagTutor para executar e não impõe uma meta artificial de chunks.

## Testes e diagnóstico

A suíte comum usa mocks e respostas controladas para chunking semântico, embeddings, recuperação, reranking, falhas HTTP, CSV, manifesto, auditoria, reparo, terminal e exportações. Ela não exige servidor Ollama. O diagnóstico real é opt-in:

```powershell
mvn clean test
mvn clean package
mvn '-Dtest=LiveRagDiagnosticTest' '-Dsylph.live=true' '-Dsylph.mode=recursivo' test
mvn '-Dtest=LiveRagDiagnosticTest' '-Dsylph.live=true' '-Dsylph.mode=semantico' test
```

Os dois últimos comandos requerem bases preparadas, Ollama e modelos instalados; podem demorar e produzir diagnósticos reais. Consulte os relatórios Surefire em `target/surefire-reports/`. Os testes de integração simulados e a validação de citações continuam separados da revisão humana das dez respostas acadêmicas.

No PowerShell, argumentos Maven com pontos precisam ficar entre aspas. Se a sincronização do OneDrive ou um atributo de somente leitura em artefatos gerados bloquear `mvn clean`, use uma nova pasta de build sem alterar documentos ou índices:

```powershell
mvn '-Dsylph.build.directory=target-local' clean test
mvn '-Dsylph.build.directory=target-local' clean package
java -jar target-local/sylph-unopar-java-1.0.0.jar
```

Esse parâmetro muda apenas a saída do build; sem ele, os comandos padrão e o JAR em `target/` permanecem válidos. Para problemas de acentuação em terminais antigos, use UTF-8 no terminal; os arquivos de configuração, perguntas e relatórios são UTF-8.

Se um modelo estiver ausente, `diagnostico` e `modelos` informam a situação; instale o modelo escolhido explicitamente. Para vetor incompatível, confira `modelo_embeddings` e prepare novamente. Para índice corrompido, preserve os registros e use `recriar`. Não edite o manual ou os CSVs para obter uma resposta desejada.

## Repositório e limites

O `.gitignore` cobre builds, caches locais, temporários, logs, índices regeneráveis, resultados, novos backups, documentos locais em `dados/` e configurações locais. Isso não remove arquivos já rastreados: o manual, `config.json` e backups preexistentes permanecem no repositório. Revise a autorização de distribuição dos materiais e o conteúdo desses arquivos antes de publicar; esta alteração não apaga nem desversiona o histórico.

Permanecem necessários gabaritos documentados e avaliação humana para concluir sobre correção, omissões, alucinações e superioridade entre variantes. Não há OCR, banco vetorial externo ou interface web. A auditoria semântica depende do LLM e é complementada por verificações programáticas, que também têm limitações. Mais documentos podem introduzir conflitos; mantenha fontes identificadas e revise evidências recuperadas.

Próximas melhorias possíveis, condicionadas a evidências: gabaritos revisados para as dez perguntas, anotação de fronteiras temáticas, estudo de sensibilidade dos parâmetros, avaliação de relevância do reranking por pergunta e OCR opcional para materiais que realmente precisem dele.
