# Revisão factual das medições de desempenho

Revisão textual por confronto direto entre perguntas, respostas completas, páginas citadas e spans do gabarito registrados nos JSON. A auditoria do próprio modelo e os sinais lexicais do benchmark são evidências de execução, não um gabarito de correção. Esta revisão técnica pelo agente não substitui uma eventual avaliação acadêmica humana.

## Baseline — `baseline-core.json`

### `prazo` — erro factual pré-existente confirmado

- Pergunta: “Qual é o prazo para análise do cadastro de uma empresa?”
- Controles: recursivo, reranking desligado, thinking ligado, `top_k=8`.
- Tempo medido: **126,684 s**. É o tempo de uma resposta concluída tecnicamente, mas **factualmente incorreta**.
- Resposta final: “O prazo para análise do cadastro de uma empresa é de **7 dias úteis**”, citando a página 4 do manual.
- Evidência na própria página citada: o cabeçalho estabelece prazos em dias úteis, e a linha pertinente diz **“Cadastro de empresa: 4 dias”**. As linhas de **liberação** e **validação do Termo de Compromisso e do Cadastro da Empresa** indicam **7 dias**. São procedimentos diferentes.
- Conclusão: a resposta associou o prazo de outro procedimento à análise do cadastro de empresa. A resposta esperada para a pergunta é **4 dias úteis**.
- Recuperação: o trecho com a linha correta estava disponível no contexto e está reproduzido em `execution.answer.fontes` e `goldenSourceSpans`. Portanto, este caso não decorre da ausência da evidência correta na recuperação.
- Citação: documento/página existem e são pertinentes ao tema, mas a página não sustenta o prazo afirmado para esse procedimento. Uma citação existente não basta para estabelecer o apoio factual da afirmação.
- Auditoria: `firstAudit.valid=true`, sem `issues` ou `missing`, e a afirmação incorreta foi mantida em `approved`. `repaired=false`; nenhum reparo foi disparado. Houve falso positivo da auditoria existente.
- Guardas e triagem: a verificação numérica aceitava a mera presença do número **7** na página; não vinculava esse número à linha de **cadastro de empresa**. O sinal lexical da versão inicial do benchmark também retornou verdadeiro porque encontrava **4** na própria citação `[... Página: 4]`. Esse sinal foi corrigido no relatório de futuras execuções para ignorar o texto das citações; a evidência original do baseline foi preservada.
- Relação com a otimização: **defeito confirmado antes das alterações de desempenho**. Não deve ser apresentado como regressão causada pela otimização, nem como resposta correta do baseline. Uma resposta futura com 4 dias úteis corrige este caso; uma resposta futura com 7 dias úteis continua incorreta, ainda que seja mais rápida.

Evidência original: [baseline-core.json](baseline-core.json), item `results[0]`, campos `execution.answer`, `execution.firstAudit`, `execution.repaired` e `goldenSourceSpans`.

### `supervisores` — resposta final sustentada após reparo

- Pergunta: diferença entre supervisor acadêmico e supervisor de campo e responsável por cada função.
- Tempo medido: **514,056 s** (8 min 34,056 s). Houve reparo.
- Geração inicial: citava a página **14** para uma comparação que exige as páginas **15 e 16**. Acrescentava que o acadêmico seria responsável por “garantir o cumprimento das exigências acadêmicas e pelo vínculo com o curso”, atribuição que não está sustentada pelo trecho citado. A auditoria e as guardas de atribuição rejeitaram essas unidades. A geração inicial não deve ser tomada como a resposta entregue ao aluno.
- Resposta final após reparo: o acadêmico é o representante da IES encarregado de acompanhar o estágio (página 15); **em muitos casos** é o tutor da sala de aula (página 15); o supervisor de campo é designado pela empresa concedente para orientar e acompanhar o estudante no estágio nas instalações da empresa (página 16); nos seis cursos listados, seu cadastro precede a associação do aluno (página 16).
- Confronto: as quatro unidades finais reproduzem afirmações literais dos respectivos spans. A condição “em muitos casos” foi mantida; não universaliza o papel do tutor. A lista de cursos e a ordem de cadastro foram preservadas. As responsabilidades e os atores não foram trocados.
- Citações finais: páginas **15 e 16**, ambas correspondentes ao conteúdo afirmado. A atribuição adicional sem apoio e a citação à página 14 desapareceram da resposta final.
- Auditoria após reparo: quatro unidades aprovadas, sem questões ou lacunas registradas. A revisão direta das fontes confirma essas unidades, independentemente do resultado positivo da auditoria.
- Parecer: **resposta final sustentada no escopo da pergunta**. A preservação de auditoria, guardas e reparo foi necessária para obter esse resultado.

Evidência original: [baseline-core.json](baseline-core.json), item `results[1]`, campos `execution.generatedText`, `execution.firstAudit`, `execution.answer`, `execution.repairAudit` e `goldenSourceSpans`.

## Ablação isolada — `thinking-off-original.json`

Esta fase executa o bytecode original instrumentado, alterando somente o controle de thinking para falso. Não inclui a redução de duplicações de contexto/auditoria, cache de reranking ou demais alterações candidatas. O resultado não aprova automaticamente o mesmo controle dentro de uma implementação posterior.

### `prazo` — mesmo erro factual do baseline

- Tempo: **81,444 s**, contra 126,684 s no baseline; redução observada de 35,71% nesta amostra.
- Resposta: novamente **7 dias úteis**, citando a página 4. O erro continua: a linha de cadastro de empresa estabelece **4 dias úteis**.
- Auditoria aprovou e nenhum reparo foi acionado. Não houve correção factual.
- Limite da comparação: o carregamento nativo acumulado foi 2,161 s, contra 20,466 s no primeiro caso do baseline, e a geração reutilizou 7.460 tokens de prompt cacheados contra zero. A diferença total não pode ser atribuída exclusivamente ao thinking.

### `supervisores` — trechos sustentados, reparo inválido e aviso indevido

- Tempo: **558,666 s**, contra 514,056 s no baseline; **8,68% mais lento** nesta amostra.
- As três unidades preservadas na resposta final são sustentadas pelas páginas 15 e 16: designação/função do campo; cadastro prévio nos seis cursos listados; papel do acadêmico como representante da IES, com a condição de tutor em muitos casos. Não se identificou troca factual de atores nessas unidades preservadas.
- Contudo, a resposta termina afirmando que não foi possível confirmar exatamente a diferença e os responsáveis perguntados, embora as próprias unidades preservadas já apresentem essas informações sustentadas. Esse aviso de ausência/falha é indevido para o conteúdo disponível e constitui piora do resultado entregue.
- A auditoria inicial rejeitou outras unidades por atribuição ou citação literal inválida, mantendo as unidades sustentadas. O reparo consumiu **295,766 s** e **1.854 tokens de saída**, mas resultou em **falha no protocolo de reparo**. A auditoria posterior local rejeitou o texto sem citação; não houve uma segunda chamada LLM de auditoria. O fallback preservou as unidades anteriormente aprovadas, acompanhado do aviso acima.
- Parecer: **rejeitar desligar o thinking como solução isolada/padrão com base nesta execução**. Houve piora da latência máxima e falha de reparo com aviso indevido. A média quase inalterada dos dois casos não demonstra um benefício confiável; o caso simples ainda está factualmente errado.

Evidência original: [thinking-off-original.json](thinking-off-original.json), campos `execution.answer`, `execution.firstAudit`, `execution.repairAudit`, `ollamaCalls` e `stageMillis` de cada caso.

## Candidato otimizado — `optimized-candidate-core.json`

### `prazo` — resposta correta na amostra candidata

- Tempo: **50,329 s**, contra 126,684 s no baseline, redução observada de **60,27%**. A amostra comum ficou abaixo de 60 segundos.
- Resposta final: “O prazo para análise do cadastro de uma empresa é de **4 dias úteis**”, com citação da página 4.
- Confronto: corresponde à linha “Cadastro de empresa: 4 dias” sob o cabeçalho “prazos em dias úteis”. Não confunde cadastro com liberação/validação. Documento e página citados são corretos.
- Auditoria: aprovou essa unidade, sem questões ou lacunas; nenhum reparo foi necessário. O confronto direto com a fonte confirma o acerto, independentemente dessa aprovação.
- Contexto: 18.109 caracteres, contra 20.166 no baseline; a linha de evidência necessária foi preservada.
- Parecer: **resposta sustentada e correção do erro pré-existente neste caso**. Esse resultado vale para a amostra, não garante o conjunto enquanto o caso complexo e os controles adicionais não forem revisados.

### `supervisores` — definições sustentadas, mas aviso indevido impede aprovação global

- Tempo: **212,102 s**, contra 514,056 s no baseline; redução observada de **58,74%**. Houve reparo e segunda auditoria.
- As definições finais do acadêmico (página 15), sua condição de tutor em muitos casos (página 15) e do campo designado pela empresa (página 16) estão sustentadas. A responsabilidade básica de cada função está identificada sem troca de atores.
- A geração inicial ainda citava somente a página 15 para conteúdo do campo localizado na página 16; essas unidades não foram aceitas. O reparo voltou a produzir definições literais com as páginas corretas.
- Entretanto, `repairAudit` contém quatro unidades aprovadas **e duas questões** de unidades consideradas insuficientes. O fallback entrega os trechos aprovados, mas encerra com “Não foi possível confirmar nas evidências consultadas: a pergunta [Q10 inteira]”. As definições preservadas já respondem à diferença e aos responsáveis. O aviso é indevido para o conteúdo disponível e não existia na resposta final do baseline.
- A quarta unidade final é uma tabela achatada com nomes de cursos e pares “Sim Não”, sem cabeçalhos na resposta. Sua sequência consta na página 16, mas a apresentação perdeu a identificação explícita das duas colunas (necessidade de cadastro/vínculo); não equivale a uma explicação clara dessas condições. A pergunta core não exigia essa tabela, e a linha adicional do baseline sobre cadastro prévio também era complementar.
- Parecer: **as definições centrais são sustentadas e a latência melhorou, mas este artefato candidato não é aprovado globalmente como resultado final**. Corrigir e revalidar a seleção/apresentação do reparo e o aviso de ausência preservando todas as guardas. Uma configuração posterior deve ser medida em nova evidência, sem sobrescrever esta tentativa.

Evidência original: [optimized-candidate-core.json](optimized-candidate-core.json), campos `execution.answer`, `execution.firstAudit`, `execution.repairAudit`, `execution.generatedText` e `goldenSourceSpans`.

As tabelas provisórias de tempos/tokens estão em [comparacao-provisoria.md](comparacao-provisoria.md). Comparações de latência devem distinguir respostas corretas, respostas incorretas e execuções interrompidas.

## Segunda tentativa com thinking desligado — `optimized-final-core.json` — rejeitada

O nome do arquivo contém “final”, mas **esta execução foi rejeitada** após revisar a pergunta composta. Nome de artefato não significa aprovação.

### `prazo` — resposta sustentada

- Tempo: **33,038 s**. Resposta: **4 dias úteis**, citando a página 4. Corresponde ao prazo específico de cadastro de empresa no manual.
- Auditoria sem questões/lacunas; nenhum reparo necessário. A revisão direta da fonte confirma a afirmação.
- Parecer deste caso: **sustentado**, com correção do erro de 7 dias do baseline. O carregamento nativo acumulado foi apenas 0,047 s, por isso esta execução não é uma medição de modelo frio.

### `supervisores` — regressão de cobertura confirmada

- Tempo: **166,547 s**, com reparo e segunda auditoria.
- A resposta final contém apenas uma orientação da página **14** para reimprimir documentos após corrigir o responsável da empresa, alheia à pergunta, e a definição de supervisor de campo da página **16**.
- A definição e o responsável pelo papel de **supervisor acadêmico**, presentes na página **15** e na resposta final do baseline, desapareceram. O conteúdo que distingue as duas funções ficou incompleto.
- Os dois trechos entregues constam literalmente nas páginas citadas, mas isso não estabelece que respondam integralmente à pergunta. A segunda auditoria os aprovou e declarou `missing=[]`, apesar da omissão evidente. Este é um falso negativo da verificação de completude nessa amostra.
- Parecer: **regressão confirmada; rejeitar esta tentativa**, apesar do ganho de velocidade e do prazo simples correto. Não promover `think=false` à configuração padrão com estes resultados. Nenhuma guarda é removida para aceitar a resposta.

## Configuração com precisão priorizada — `optimized-precision-core.json`

**Core concluído e aceito nesta revisão técnica independente.** Mantém o conjunto otimizado e o thinking da geração em **true**, conforme a escolha local inicial. Uma amostra por pergunta; não constitui avaliação acadêmica humana ou garantia para perguntas não exercitadas.

### `prazo` — sustentado, corrigindo o erro do baseline

- Tempo: **59,482 s**, contra 126,684 s no baseline; redução observada de **53,05%**.
- Resposta final: “O prazo para análise do cadastro de uma empresa é de **4 dias úteis**”, citando a página **4**.
- A linha específica e o cabeçalho de dias úteis da fonte sustentam integralmente a resposta. Nenhuma afirmação extra foi acrescentada, e não se confundiu o procedimento com liberação/validação.
- Uma unidade aprovada na auditoria inicial, sem questões/lacunas; nenhum reparo. A revisão direta confirma o acerto.
- Parecer: **aceito neste caso**, com correção do defeito pré-existente. O tempo abaixo de 60 segundos foi observado com modelo/cache já aquecidos; não é garantia de partida fria.

### `supervisores` — cobertura central e citações corretas após reparo

- Tempo: **328,507 s** (5 min 28,507 s), contra 514,056 s no baseline; redução observada de **36,10%**.
- Resposta final: duas definições literais. O **acadêmico** é o representante da IES encarregado de acompanhar o estágio, citado na página **15**. O **campo** é o profissional designado pela empresa concedente para orientar e acompanhar o estudante durante a execução do estágio nas instalações da empresa, citado na página **16**.
- Confronto: ambas as afirmações constam literalmente nas páginas citadas. Identificam separadamente função, vínculo institucional e ator responsável por cada papel, satisfazendo as duas partes da pergunta core.
- Não há orientação irrelevante de reimpressão, tabela sem cabeçalhos, nem aviso indevido de ausência. A resposta não afirma que o acadêmico seja sempre tutor. O fato complementar de ser tutor em muitos casos e regras de cadastro por curso não foram solicitados na Q10 core; a sua ausência não elimina nenhuma parte do pedido. Condições/exceções exigem o controle específico separado.
- Geração inicial: usou citações em formato livre `Fonte: Página ... do documento`, fora do protocolo aceito, e acrescentou atribuições não sustentadas. O parser/validador rejeitou toda essa geração; nenhum conteúdo bruto foi aprovado por omissão.
- **Auditoria inicial de 0 ms não significa remoção de segurança**: a guarda local já existente retornou rejeição porque havia zero unidades com citação válida. Não havia unidade admissível a encaminhar ao auditor LLM nesse ponto. O fluxo acionou reparo extrativo (**14,089 s**) e uma auditoria LLM posterior completa (**39,891 s**), que aprovou as duas definições sem questões/lacunas. Esse caminho seguro difere do caminho percorrido no baseline e afeta a comparação de tempos.
- Parecer: **aceito no escopo da pergunta core**, preservando fatos e páginas necessários. A primeira geração ainda é um gargalo (**274,454 s**, maior que os 239,027 s do baseline); o ganho total veio dos demais componentes e do caminho de validação/reparo observado, não de aceleração garantida de toda chamada.

### Decisão do conjunto core

A configuração com **thinking ligado** é a aceita para o conjunto medido: prazo corrigido e diferença/responsáveis dos supervisores sustentados. Média **193,995 s**, máximo **328,507 s**, contra 320,370 s e 514,056 s no baseline. Reduções observadas: **39,45% na média** e **36,10% no máximo**. As tentativas mais rápidas com thinking desligado continuam rejeitadas; nenhum dado foi sobrescrito.

Evidência original: [optimized-precision-core.json](optimized-precision-core.json), respostas, fontes, `firstAudit`, `repairAudit`, chamadas nativas e tempos por estágio. Controles adicionais de chunking semântico, ausência e reranking permanecem separados da aceitação core e terão revisão própria.

## Probe isolado de reranking — original e otimizado

Este probe recupera evidências, sem gerar resposta. Não recebe classificação de acerto de uma resposta inexistente.

- Os **15 IDs de candidatos e sua ordem híbrida inicial são iguais** entre original e primeira execução otimizada. Ambos mantêm `top_k=8`, pontuam **12 candidatos** e retornam status `applied`, sem fallback.
- O primeiro resultado pontuado nos dois casos é o mesmo chunk, com spans das páginas 3/4, contendo literalmente a linha **“Cadastro de empresa: 4 dias”** da página 4 e o cabeçalho de dias úteis. Recebe nota **10** em ambos. A evidência específica solicitada foi preservada no contexto final.
- A identidade da citação é mantida em Java: IDs locais usados para pontuação não substituem o vínculo com o documento/página/chunk original.
- Tempo exclusivo do reranker: **37,664 para 21,659 s (−42,49%)** na primeira chamada otimizada; tokens entrada **4.978 para 4.380** e saída **171 para 105**. A repetição otimizada durou 16,963 s com 4.379 tokens de prompt cacheados, mas ainda houve chamada de reranking; não é prova de cache de notas/resultado.
- A recuperação repetida com reranking desligado durou **15 ms**, sem nova chamada HTTP de embedding, após 548 ms na primeira. Isso comprova reutilização neste processo/pergunta, não uma garantia universal de 15 ms.
- Parecer: **probe de desempenho/proveniência satisfatório no caso exercitado**, sem perda da evidência central. Reranking permanece escolha manual; esse probe não substitui uma execução completa de geração/auditoria com reranking ligado para outras perguntas.

Evidências: [baseline-reranking.json](baseline-reranking.json) e [optimized-reranking.json](optimized-reranking.json).

## Controles com chunking semântico — `optimized-controls-semantico.json`

Mesmos modelos, `top_k=8`, thinking ligado e reranking desligado. Estes casos são controles adicionais, não substituem as duas perguntas do comparativo recursivo nem compõem sua média.

### `prazo` — fato central correto, apresentação excessiva

- Tempo: **124,452 s**, com reparo (**13,497 s**) e auditoria posterior (**30,369 s**). Não ficou abaixo de 60 segundos.
- A resposta final reproduz a tabela da página **4**, incluindo **“Cadastro de empresa: 4 dias”** sob o cabeçalho de dias úteis. O prazo específico está correto e não foi trocado por 7 dias.
- Acrescenta um fragmento da página **41**, sobre correção/liberação e 7 dias úteis, com lacuna `[...]` explícita. Esse trecho está presente na fonte recuperada, mas é **irrelevante para o prazo específico solicitado** e pouco claro fora de seu contexto. Não é necessária essa informação para responder à pergunta; a resposta não é um exemplo de concisão adequada.
- A primeira geração usou o nome do documento com acentos diferentes dos metadados e citou uma página adicional. As citações não passaram na guarda de identidade; o conteúdo bruto foi rejeitado e o reparo refez citações para as fontes reais. A auditoria inicial de 6 ms foi local, sem chamada LLM, porque não restaram unidades com citação admissível.
- Parecer: **fato central sustentado com chunking semântico, mas apresentação extra/fragmentada deve permanecer visível como limitação**. Este caso não prova equivalência de latência entre escolhas manuais de chunking.

### `ausente` — sem invenção de quantia, mas falha técnica da auditoria

- Tempo: **341,007 s**. A resposta final informa que não foi possível validar/confirmar o valor e recomenda confirmação com a coordenação. `encontrou=false`, sem fontes e **sem inventar quantia de auxílio-transporte**.
- A geração inicial já declarou não encontrar a informação. Não apresentou unidades factuais com citação válida; a guarda local rejeitou sem auditoria LLM inicial e o fluxo acionou reparo.
- O reparo consumiu **44,270 s**; a auditoria posterior consumiu **231,820 s**, emitindo **1.349 tokens**, e foi rejeitada por **“Protocolo de auditoria invalido”**, `valid=false`. O fallback conservador impediu a publicação de fatos não validados.
- Parecer: **contenção factual preservada nesta amostra, porém o protocolo não foi robusto e a latência permaneceu excessiva**. Não classificar o teste como uma confirmação limpa de ausência: houve falha técnica de auditoria, corretamente rejeitada, e a resposta final descreve essa dificuldade. Não tratar a inexistência de valor inventado como prova de que todo o fluxo terminou corretamente.

Evidência: [optimized-controls-semantico.json](optimized-controls-semantico.json). Esta falha revelou um gargalo adicional no tamanho/validade da saída de auditoria. O novo protocolo de provas por IDs está em desenvolvimento/validação separada; **não está aprovado por esta revisão** e precisa de novos casos reais antes de substituir o protocolo do core aceito.

## Tentativa de release com provas por IDs em array — `release-core.json` — rejeitada

**Rejeitada.** O caso simples passou, mas o complexo regrediu por falha no protocolo da auditoria. O JAR de hash `BD72B9D5D7B322C03235DD589669E5DE755A2BF6BE788BF925537B22BB23E346` não está aprovado para entrega final.

### `prazo` — primeira amostra sustentada, acima de 60 segundos

- Tempo: **66,687 s**. Resposta final: **4 dias úteis**, com citação da página **4**, sem atribuições extras ou troca de procedimento. A linha específica de cadastro de empresa e o cabeçalho de dias úteis sustentam a afirmação.
- Auditoria por IDs aprovou a unidade; a revisão direta confirma o acerto. Foram **8,145 s e 67 tokens de saída** na auditoria, contra 11,255 s e 125 tokens na referência intermediária de cópia de citações.
- O tempo total ficou maior que os 59,482 s intermediários porque as condições de geração diferem: a primeira chamada do release reutilizou apenas **3 tokens** de prompt, contra 7.406 na referência intermediária. A geração consumiu **57,127 s**. A melhora da auditoria não implica automaticamente total menor com prefill diferente.
- Parecer deste caso: **sustentado**, mas a meta de menos de 60 segundos **não foi alcançada nesta amostra do novo JAR**. O tempo de 59,482 s anterior não é apresentado como tempo desse binário. Uma repetição aquecida futura deverá ficar separada do comparativo principal.

### `supervisores` — regressão reportada no término, exportação final bloqueada

- O agente principal observou término em **318,309 s**, mas a resposta final passou a declarar ausência de informação, omitindo as duas definições já comprovadas nas páginas 15/16.
- O protocolo de saída usava um array de claims. O modelo repetiu IDs de unidades apesar do schema; o decoder estrito rejeitou a auditoria. A contenção permaneceu ativa, mas o fallback de ausência é indevido para a pergunta com evidência disponível, configurando regressão de cobertura.
- Parecer: **rejeitar esta tentativa**. O caso simples correto e a melhora da auditoria nesse caso não autorizam aceitar a omissão no complexo. O controle ausente semântico desse binário não deve ser presumido executado/aprovado.
- Limite de evidência: a exportação atômica final falhou com `AccessDenied` na pasta sincronizada pelo OneDrive, deixando o JSON principal com status antigo `running` e fazendo o Maven sinalizar falha de I/O. O diagnóstico do término complexo acima foi fornecido pelo agente principal; não é apresentado como revisão independente de uma exportação final completa ainda indisponível. A falha de exportação não transforma a resposta do modelo em timeout nem comprova continuidade da inferência.

### Próxima correção — pendente de nova medição

A proposta seguinte muda o protocolo wire de claims para objeto indexado pelas chaves de IDs exatos das unidades; Java converte para o array verificado pelo decoder original. Ainda não está aprovada nesta revisão. As medições novas usarão diretório temporário fora do OneDrive, sem leitura dos arquivos enquanto o processo estiver escrevendo; só depois serão copiadas para documentação. O harness também recebeu retentativas curtas para locks e preservação em arquivo de recuperação único. O core intermediário continua referência preservada, sem transferência automática de aprovação ao próximo release.

## Tentativa com claims em objeto — `release-keyed-core.json` — rejeitada

Arquivo concluído/copiado e lido nesta revisão independente. O novo formato corrigiu a forma de identificar as unidades, mas **não preservou o resultado factual completo do core**. Seu tempo médio não é apresentado como ganho de uma versão aprovada.

### `prazo` — sustentado

- Tempo: **56,155 s**. Resposta de **4 dias úteis**, com citação da página **4**, sustentada pela linha específica do manual. Uma unidade aprovada na auditoria inicial, sem questões/lacunas e sem reparo.
- Parecer do caso: correto. O tempo abaixo de 60 segundos pertence a esta **tentativa rejeitada no conjunto**, não à versão final aceita.

### `supervisores` — ausência indevida após reparo irrelevante

- Tempo: **306,583 s**. Resposta final: não foi possível validar/confirmar as duas partes da pergunta, `encontrou=false`, sem fontes. Isso omite as definições disponíveis nas páginas **15 e 16** e respondidas no baseline reparado e na referência intermediária.
- A geração inicial ainda inventava atribuições amplas sobre garantir requisitos acadêmicos/regulamentares e padrões práticos, com citações que não apoiavam a afirmação inteira. As guardas de atribuição rejeitaram corretamente as quatro unidades; nenhuma delas foi aprovada sem apoio.
- O campo `repairedText` permite verificar a causa posterior: o reparo selecionou **quatro sentenças somente da página 24**, sobre cadastro em reedição, convocação e declaração de regularidade profissional. Elas não definem os dois tipos de supervisores nem respondem à diferença/responsáveis solicitados.
- A segunda auditoria foi um protocolo **válido** (`valid=true`), mas rejeitou as quatro unidades por irrelevância e registrou as duas lacunas. Portanto, esta falha não é atribuída a JSON inválido, IDs duplicados ou erro de exportação. As guardas impediram entregar o procedimento irrelevante; o fluxo não conseguiu recuperar a resposta central.
- Parecer: **regressão confirmada; rejeitar o conjunto com esse wire de auditoria**. Não apresentar média **181,369 s** ou máximo **306,583 s** como números de uma versão factual aprovada. O acerto da pergunta simples não compensa a perda da resposta composta.

### Decisão após a tentativa

O agente principal determinou reverter somente o wire de auditoria para a representação intermediária já validada, com tabela de fontes completas e citações textuais, mantendo os demais ganhos. A nova auditoria por IDs fica **rejeitada**, apesar dos testes automatizados e da redução de tokens observada em casos individuais. O controle ausente semântico foi concluído depois e tem a revisão separada a seguir; seu resultado não compensa a regressão do core.

### Controle ausente semântico do objeto — concluído depois, ainda experimental

O arquivo concluído [release-keyed-absent-semantico.json](release-keyed-absent-semantico.json) foi copiado para documentação e revisado separadamente. Não pertence à versão final, pois o protocolo em objeto foi rejeitado pelo core.

- Tempo: **85,277 s**. Final sem quantia de auxílio-transporte, sem fontes, `encontrou=false`, com aviso de que não foi possível validar/confirmar e recomendação de consultar a coordenação.
- A primeira geração declarou não encontrar a informação, mas citou a página 33. A auditoria inicial foi válida e rejeitou a unidade: aquela página não comprova o valor nem a ausência universal pretendida. O reparo devolveu a mensagem padrão de informação não encontrada, sem citações; a verificação posterior local registrou a pergunta em `missing`, sem nova chamada LLM.
- Não houve protocolo inválido nessa amostra. Auditoria LLM inicial: **21,626 s e 207 tokens de saída**. Reparo: **15,940 s e 59 tokens**. Não são a mesma auditoria/caminho da tentativa anterior de 341,007 s, que tinha auditoria LLM posterior inválida de 231,820 s.
- Parecer: **nenhuma quantia inventada e nenhuma evidência falsa liberada**, mas o usuário recebe uma dificuldade de validação em vez de uma confirmação limpa de informação ausente. A melhora de tempo deste controle isolado não reabilita o protocolo reprovado na pergunta composta.
- Limites: a geração reaproveitou 7.123 tokens de prompt, contra 3 no controle semântico anterior; mudaram tanto o caminho de validação quanto o estado de cache. Não atribuir toda a diferença de tempo ao formato de auditoria.

As execuções seguintes `release-accepted-core` e `release-accepted-absent-semantico` usam o wire textual restaurado. Foram lidas somente após concluídas/copiadas, e sua revisão final está a seguir; o nome dos arquivos não foi usado como aprovação antecipada.

## Versão final com auditoria textual restaurada — aprovada no conjunto exercitado

Os dois arquivos finais foram concluídos com Maven `SUCCESS`, copiados para documentação e lidos nesta revisão independente. A aprovação abaixo se baseia no confronto das respostas com os spans, e não apenas em `valid=true` da auditoria. O thinking da geração permanece ligado, `top_k=8`, modelos e corpus preservados.

### `release-accepted-core`: prazo

- **61,509 s**. Resposta: **4 dias úteis**, com página **4**, sem alegação adicional. A linha específica e o cabeçalho de dias úteis da fonte sustentam integralmente a resposta; corrige o erro de 7 dias do baseline.
- Uma unidade aprovada na auditoria inicial, sem questões/lacunas; não foi necessário reparo. Tempo de geração 47,861 s e auditoria 12,132 s.
- Parecer: **sustentado**. O tempo ficou **1,509 s acima de 60**, portanto não se declara a meta atingida para essa amostra do JAR final.

### `release-accepted-core`: supervisores

- **394,670 s**. Resposta final identifica o **acadêmico como representante da IES encarregado de acompanhar o estágio** (página **15**) e o **campo como profissional designado pela empresa para orientar e acompanhar o estudante nas instalações da empresa** (página **16**). Ambas são definições literais dos respectivos spans e respondem às duas partes centrais da Q10.
- Não há omissão de uma das funções, troca de atores, orientação irrelevante de reimpressão, ausência indevida ou atribuição inventada na resposta entregue.
- A geração inicial continha quatro unidades com atribuições ampliadas e apoio insuficiente. Mesmo quando o auditor LLM as considerou apoiadas, as guardas Java de atribuição as rejeitaram; não foram liberadas. O reparo extrativo de **14,908 s** produziu duas definições e duas regras de cadastro; a segunda auditoria de **74,235 s** aprovou as quatro unidades, sem questões/lacunas. A auditoria inicial de **75,294 s** também foi executada: não houve remoção de chamada para obter o tempo final.
- **Ressalva de clareza:** a regra adicional acadêmica começa por “Nesses casos”, sem repetir antes dela a frase que enumera os cursos. A expressão condicional e “em alguns casos” permanecem; a unidade de campo logo a seguir enumera os mesmos seis cursos, e a fonte completa da página 15 traz o antecedente. Não se identificou afirmação universal explícita nem contradição concreta no pedido core, mas esse trecho adicional não é uma orientação autossuficiente para todos os cursos. A ressalva não é apagada pela auditoria positiva.
- Parecer: **core sustentado, sem regressão factual concreta identificada nas definições/responsáveis solicitados em relação ao baseline reparado**, com a ressalva de apresentação acima. Não se transforma essa aprovação da Q10 em aprovação do caso específico de condições/exceções não executado.

### `release-accepted-absent-semantico`: informação ausente

- **92,788 s**, com chunking semântico. Final sem quantia de auxílio-transporte, sem fontes e `encontrou=false`, orientando confirmar com a coordenação.
- A geração declarou não encontrar a informação, mas citou a página 33. A auditoria inicial válida rejeitou a unidade porque a página não comprova o valor solicitado. O reparo devolveu a mensagem padrão de informação não encontrada, sem citações. A verificação posterior local registrou a pergunta em `missing`; não houve nova chamada LLM e não houve protocolo inválido.
- Tempos: geração **45,474 s**, auditoria inicial **44,111 s**, reparo **2,790 s**, posterior local **0 s**. Saída somada **661 tokens**, entrada **13.933**. As duas estruturas de auditoria foram válidas; `valid=true` significa validade do protocolo, não aprovação de uma quantia ausente.
- Parecer: **contenção factual preservada**: nenhum valor inventado ou fonte falsa foi liberado. O texto final é um fallback conservador de dificuldade de validação/confirmação, não prova documental exaustiva de inexistência do benefício. O controle anterior com protocolo inválido continua preservado como limitação histórica, sem fingir que percorreu o mesmo caminho.

### Decisão e identificação final

O conjunto final de duas perguntas core tem média **228,090 s** e máximo **394,670 s**, contra **320,370/514,056 s** no baseline: **−28,80% na média**, **−23,22% no máximo** e **−51,45% no simples**. Entrada **27.960 → 27.717 (−0,87%)**; saída **2.364 → 2.703 (+14,34%)**. O aumento de saída é declarado; não se alega queda geral de tokens.

Resultados pertencem ao JAR final de **17.596.781 bytes**, SHA-256 **`E34D7F5EEBBD961DFB3DE7EE7E52DCB923262A3B5D4C6B146E3F272E852B0138`**. Maven final: **342 testes, 338 passaram, 4 skips, 0 falhas e 0 erros**; menu e launcher de validação encerraram com código **0**, conforme conferência do agente principal. Configuração e manifesto mantiveram seus hashes originais.

Esta é uma **revisão técnica pelo agente**, não avaliação acadêmica humana. Há uma amostra final por pergunta, condições de cache/residência diferentes e o caso complexo continua acima de seis minutos. As tentativas rejeitadas e os seus melhores tempos não são promovidos a resultado final. O relatório canônico consolidado é [RELATORIO-FINAL.md](RELATORIO-FINAL.md).
