# poc_laya: PoC do agente de curadoria de catálogo

Tudo o que é necessário para **executar de novo os testes** da PoC e **responder, com número, às nove perguntas** abaixo: o agente que organiza o catálogo, melhora a busca e trata os itens novos, com o Laya ajustado, o Naive Bayes da biblioteca **scikit-learn**, vizinhos por embeddings e o LLM só nas disputas.

> **Dados sintéticos por padrão.** Os notebooks geram um catálogo fictício reproduzível (`drive/catalogo-v2`) e o medem com um gabarito. Para validar com **dados reais** leia a seção [8](#8-dados-reais-o-que-fazer-e-o-que-ainda-nao-esta-pronto): o contrato de dados está descrito, mas **o adaptador para dados reais ainda não foi construído**. Para **treinar em uma base nova com estrutura nova** (taxonomia e campos diferentes) no Colab, leia a seção [11](#11-treinar-em-uma-base-nova-com-estrutura-nova-no-colab). Com dados reais de cliente, enviar produtos ao Colab, ao Google Drive ou ao Ollama Cloud **precisa de validação do time**.

## Sumário
1. [As nove perguntas e onde cada uma é respondida](#1-as-nove-perguntas-e-onde-cada-uma-e-respondida)
2. [O agente em um minuto (contexto)](#2-o-agente-em-um-minuto-contexto)
3. [Estrutura da pasta](#3-estrutura-da-pasta)
4. [Como executar (passo a passo)](#4-como-executar-passo-a-passo) — inclui o **checklist do que levar para o Colab** (4.1)
5. [Cada notebook: o que faz, o que grava, o que se espera](#5-cada-notebook-o-que-faz-o-que-grava-o-que-se-espera)
6. [Cada pergunta: contexto, como é medida, como ler, o que se espera](#6-cada-pergunta-contexto-como-e-medida-como-ler-o-que-se-espera)
7. [O Naive Bayes agora é o da biblioteca scikit-learn](#7-o-naive-bayes-agora-e-o-da-biblioteca-scikit-learn)
8. [Dados reais: o que fazer e o que ainda não está pronto](#8-dados-reais-o-que-fazer-e-o-que-ainda-nao-esta-pronto)
9. [Critérios fixos por pergunta](#9-criterios-fixos-por-pergunta)
10. [Estado, limites e histórico](#10-estado-limites-e-historico)
11. [Treinar em uma base nova, com estrutura nova, no Colab](#11-treinar-em-uma-base-nova-com-estrutura-nova-no-colab)

---

## 1. As nove perguntas e onde cada uma é respondida

As três **perguntas centrais** (P1 a P3) e as seis **pendentes** (P4 a P9). Cada pergunta é respondida por um ou mais notebooks; o notebook **F** junta tudo no painel `r8-resumo.html`, seção **0**, que dá o **veredito** de cada pergunta (SIM / NÃO / PARCIAL / NÃO MEDIDO) **calculado só a partir dos critérios fixos medidos**. Um critério não medido **nunca** conta como atendido.

| # | Pergunta | Onde é respondida (notebook › seção) | Arquivo de resultado (em `MyDrive/laya-teste-r8/saida-r8/`) |
|---|---|---|---|
| **P1** | **O catálogo existente fica organizado?** Cada produto vai para a categoria certa e a ficha técnica fica padronizada, sem estragar o que já estava certo? | **C** › *F1, passos 1 a 3* (árvore antiga e migração); *F2: dado errado do vendedor* (categoria e ficha); *Correção de regra da ficha (F2)*. **D** › *Itens novos › catálogo existente* (duplicados e variações já cadastrados) | `c/resultados-f2.json`, `c/resultados-arvore.json`, `c/resultados-migracao.json`, `c/resultados-f2-correcao.json`, `d/resultados-grupos.json` |
| **P2** | **A busca fica mais eficiente?** Por texto (inclusive com erro de digitação) e por filtros, o cliente encontra mais produtos certos? | **D** › *Busca* (antes × depois: busca por palavra, navegação por categoria e filtros, erro de digitação) | `d/resultados-busca.json` |
| **P3** | **Os itens novos entram certos?** Os cadastros novos são classificados corretamente e os inválidos são barrados? | **D** › *Itens novos* | `d/resultados-novos.json` |
| **P4** | **Itens novos pendentes:** o agente acha duplicados, separa duplicado de variação (outra cor ou voltagem do mesmo produto), reconhece produto fora da taxonomia e não barra cadastro legítimo por engano? | **D** › *Diagnóstico dos erros da rodada 7*; *Itens novos*; *Subcategorias novas e fora da taxonomia* | `d/resultados-novos.json`, `d/subcategorias-criadas.json`, `d/diagnostico-r7.json` |
| **P5** | **Ambiguidade:** quando um produto pode estar em duas categorias (lavadora de alta pressão em Ferramentas ou em Automotivo), o agente escolhe a principal e sugere a secundária? | **C** › *Ambiguidade* | `c/resultados-f2.json` (bloco `rodada8 › ambiguidade`) |
| **P6** | **Busca sem exagero:** uma consulta sem resposta volta vazia, em vez de forçar um resultado? E uma consulta com modelo (“capinha iphone 15”) respeita o modelo? | **D** › *Busca* (grupos `sem_resposta` e `com_modelo`) | `d/resultados-busca.json` |
| **P7** | **Texto + imagem:** a foto do produto melhora a categoria e os atributos visuais quando o texto do vendedor é ruim? E quanto do trabalho dá para resolver com um modelo de imagem pequeno, deixando o grande só para as disputas? | **E** (`imagem_r8`) › *As cinco versões*; *Atributos visuais*; *Custo, exemplos e resultado* | `imagem-r8-resumo.json` |
| **P8** | **Automação total com segurança:** dá para aplicar tudo sem aprovação humana, com ações reversíveis, auditoria automática e freio quando a taxa de erro sobe? | **D** › *Modo autônomo: auditoria, freio e reversão*; *Relatório de exceções* | `d/resultados-autonomo.json`, `d/relatorio-de-excecoes.json`, `d/jornal-acoes.jsonl` |
| **P9** | **Custo:** as novidades melhoram a qualidade sem aumentar o uso de LLM? A meta é que não mais de **5,4%** dos produtos vão ao LLM na categoria. | **C** › *F2* (versão com LLM nas disputas) e **D** › *Quanto cada peça ajuda* (qualidade e consumo lado a lado) | `c/resultados-f2-llm.json`, `d/resultados-ablacao.json` |

Os notebooks **A** e **B** não respondem a pergunta nenhuma: preparam a base e o checkpoint do Laya e têm as **travas** que impedem medir com o modelo errado. O mesmo mapa está **dentro de cada notebook** (quadro “Perguntas da PoC que este notebook responde” na primeira célula de texto, e o rótulo *(responde P1, P4…)* em cada título de seção), e em `drive/perguntas_poc.py`.

---

## 2. O agente em um minuto (contexto)

**Problema.** Um catálogo de e-commerce chega bagunçado: categoria do vendedor errada ou genérica, atributos escritos de mil jeitos (“5 bocas”, “5 Bocas”, “cinco”), voltagem em produto que não usa energia, duplicados, cadastros inválidos, e uma árvore de categorias antiga com ~1.700 nós (duplicados, valores de atributo como categoria, lixo de campanha).

**O que o agente faz.** *Agente* aqui **não** é “um LLM”: é um **processo de decisão por votação de sinais independentes**, com o LLM apenas como um sinal opcional nas disputas.

| Sinal | O que é | Onde |
|---|---|---|
| **Vendedor** (ou **nó legado** mapeado, na migração) | a categoria que o vendedor informou | regra |
| **Naive Bayes** | classificador de texto treinado só nos exemplos rotulados de treino | **scikit-learn** (seção 7) |
| **Laya ajustado** | modelo de linguagem pequeno com ajuste fino, perguntas de escolha em 3 níveis (departamento › categoria › subcategoria) e a opção “nenhuma destas” | `laya3-ft-5000` (treinado no bloco B) |
| **Vizinhos** | os 10 produtos de treino mais parecidos (embeddings `multilingual-e5-base`) votam | `pipeline_v3.py` |
| **Pergunta de energia** | “este produto usa energia elétrica?” decide a disputa entre vizinhas (churrasqueira a carvão × elétrica) | Laya |
| **LLM** (opcional) | **só nas disputas**, sempre **restrito a opções**; resposta fora da lista é rejeitada e contada | Ollama Cloud |

A **regra de votação** (quantos sinais precisam concordar, veto por confiança alta, limiar) é **calibrada só na validação** e gravada em `r8-escolhas-val.json` **antes** de medir o teste. O **teste** é tudo que não é treino nem validação; o **gabarito só é lido para medir** (e para rotular treino e validação).

**Ficha técnica, “categoria primeiro, atributos depois”.** Decidida a subcategoria, a ficha dela define quais atributos existem. O valor do vendedor **só muda** se o atributo **não está na ficha** (vira “não se aplica”) ou se o código acha **no título ou na descrição um valor explícito diferente**. Fora isso nada sobrescreve: Laya e nó legado só preenchem atributo que o vendedor **não** informou; valor **fora do normal** pela estatística do catálogo sai do filtro (o original fica guardado).

**Automático, mas seguro.** Nenhuma etapa espera aprovação humana; a segurança vem de ações **reversíveis** (duplicado desativado e redirecionado, variação como SKU, árvore publicada como versão, subcategoria nova removível), **auditoria automática por amostragem** (um juiz confere uma amostra de cada lote), **freio** (pausa e reverte um lote quando o erro estimado passa do limite calibrado) e um **relatório de exceções** só para acompanhar.

**Java e Python.** O agente de produção em Spring Boot está em `agente-curadoria/` (na raiz do repositório). **O que roda nesta PoC é a porta Python** (`drive/pipeline_v3.py`), que foi validada contra o agente Java e a rodada 7 (portão de paridade, notebook A). Só a limpeza de entrada do `Preparador` foi levada ao Java; votação com vizinhos, LLM restrito, itens novos e modo autônomo existem **só em Python**.

---

## 3. Estrutura da pasta

```
poc_laya/
├── README.md                          este arquivo
├── drive/                             TUDO que sobe para MyDrive/laya-teste-r8/ (estrutura plana, como os notebooks esperam)
│   ├── laya_r8_a_bases_e_paridade.ipynb   A: portão do hash, base nova, trava do checkpoint, paridade
│   ├── laya_r8_b_treino_laya.ipynb        B: linhas de treino, portão com o gerador Java, treino do laya3, trava
│   ├── laya_r8_c_f1_e_f2.ipynb            C: árvore (F1), dado errado do vendedor (F2), ambiguidade; correção de regra no fim
│   ├── laya_r8_d_novos_e_busca.ipynb      D: itens novos, catálogo existente, busca, modo autônomo, quanto cada peça ajuda
│   ├── imagem_r8.ipynb                    E: trilha de texto + imagem (fotos reais do ABO)
│   ├── laya_r8_f_painel.ipynb             F: painel r8-resumo.html com a resposta de cada pergunta
│   ├── pipeline_v3.py                     a lógica do agente em Python (fonte da verdade; editado direto)
│   ├── fluxo_r8.py                        encadeia as peças (sinais, votação, ficha, previsões) e checa os critérios
│   ├── perguntas_poc.py                   as nove perguntas, onde cada uma é respondida e o veredito (Python puro)
│   ├── r8-criterios.json                  os critérios fixos (Atende / Não atende), gravados antes do código
│   ├── r6-escolha-val.json, r6-escolha-val-d.json, r6-referencias.json   parâmetros e números da rodada 6/7 (paridade)
│   ├── taxonomia.json                     a taxonomia ORIGINAL (usada na paridade)
│   ├── catalogo-v2/                       gerador da base sintética (--rodada8) e avaliador de referência (Node 18+)
│   └── java-fonte/                        só o pacote core do agente Java: PLANO B do portão das linhas de treino
└── fontes/                            só para quem quer MUDAR os notebooks
    ├── notebooks/                         r8_nb_*.py (o texto de cada notebook) e construir_notebooks.py (gera os .ipynb em drive/)
    └── atualizar_tabela_criterios.py      refaz a tabela da seção 9 a partir de r8-criterios.json e perguntas_poc.py
```
**Os checkpoints do Laya não estão no repositório** (≈1,8 GB cada): `laya-ft/laya2-ft-5000/` você copia para o Drive; `laya-ft/laya3-ft-5000/` o bloco B treina.

Para mudar um notebook: edite `fontes/notebooks/r8_nb_X.py` e rode `python poc_laya/fontes/notebooks/construir_notebooks.py` (só biblioteca padrão). Para mudar a lógica do agente: edite `drive/pipeline_v3.py` ou `drive/fluxo_r8.py` direto.

---

## 4. Como executar (passo a passo)

1. **Drive.** Copie **o conteúdo de `poc_laya/drive/`** para `MyDrive/laya-teste-r8/` (a estrutura plana é a que os notebooks esperam). Copie à parte `laya-ft/laya2-ft-5000/` (a pasta, não o `.zip`: `model.safetensors`, `rl_agent_config.json`, `questions.json`, `treino_gpu.json`, `encoder/`, `tokenizer/`). Opcional: `saida-r7/` com as previsões de itens novos da rodada 7 (`novos*.jsonl`) para o diagnóstico dos erros de duplicado.
2. **Colab.** Abra cada notebook pelo Drive (duplo clique › *Abrir com › Google Colab*) e use *Ambiente de execução › Alterar o tipo de ambiente de execução › GPU*: **L4 (24 GB) preferida** nos blocos B e E; a T4 serve para todos (o modelo de visão do bloco E roda em 8 bits nela).
3. **Secrets** (ícone de chave, “Acesso pelo notebook” ligado): `OLLAMA_API_KEY` para as partes com LLM (disputas, auditoria, itens novos). **Sem ela, essas partes aparecem como “não executadas” e o resto roda.** Nunca cole chave numa célula. *(A chave do Ollama que foi colada numa conversa da rodada 6 deve ser rotacionada.)*
4. **Ordem:** **A › B › C › D › F**. O **E** (imagem) é independente e pode rodar em outra sessão. Cada bloco grava o resultado no Drive (`saida-r8/estado.json` marca o que terminou) e **o seguinte recusa rodar se o anterior não terminou**.
5. **Se o Colab desconectar:** reconecte e rode as células de novo; o progresso (Laya a cada 5.000 produtos, embeddings, fichas, votos do LLM, respostas do modelo de visão) está em `saida-r8/cache/` e `saida-r8/e/`.
6. **Bloco E:** antes de rodar, **confirme com o time o uso interno do ABO** (licença CC BY-NC 4.0). O notebook mede e mostra o tamanho dos downloads e baixa só o recorte.

**Tempo estimado por bloco (ESTIMATIVA, não medição; cada notebook mede e imprime os tempos reais):**

| Bloco | Estimativa | O que domina |
|---|---|---|
| A | 0,5 a 1,2 h (a paridade roda duas vezes, a segunda reaproveita o cache do Laya) | gerar as bases, paridade |
| B | 2,5 a 4 h na T4 (a L4 deve ser mais rápida, **não medido**) | ajuste fino do Laya |
| C | 2 a 4,5 h | Laya nos 100 mil produtos |
| D | 1,5 a 3 h sem LLM; mais ~1 a 2 h com | grupos do catálogo, itens novos, busca, ablação |
| E | 1 a 3,5 h | download do recorte, modelo de visão grande |
| F | minutos | gerar o HTML |

Projeções para 100 mil produtos ou 10 mil itens novos por dia aparecem marcadas como **PROJEÇÃO**.


### 4.1 Checklist: o que levar para o Colab, onde está cada arquivo e o que já foi testado

Tudo vai para a mesma pasta do Drive, **`MyDrive/laya-teste-r8/`** (estrutura plana: é onde os notebooks procuram). “Local” é o caminho no seu computador; “repo” é este repositório.

| # | Item | Já foi testado? | Onde está hoje | Onde vai no Drive | Quem usa |
|---|---|---|---|---|---|
| 1 | **6 notebooks** (`laya_r8_a…`, `_b…`, `_c…`, `_d…`, `imagem_r8`, `laya_r8_f_painel`) | A, B e C rodaram na versão anterior; a versão desta pasta (com o Naive Bayes do scikit-learn e o mapa de perguntas) **não** | repo: `poc_laya/drive/*.ipynb` | `laya-teste-r8/` (raiz) | todos |
| 2 | **`pipeline_v3.py`** (a lógica do agente em Python, com o Naive Bayes do scikit-learn) | a lógica foi validada contra o Java na rodada 6/7 (portão de paridade); o **scikit-learn ainda não rodou no Colab** (só em dados de brinquedo) | repo: `poc_laya/drive/pipeline_v3.py` | raiz | A a F |
| 3 | **`fluxo_r8.py`**, **`perguntas_poc.py`**, **`r8-criterios.json`** | sim (rodaram no C); `perguntas_poc.py` é novo | repo: `poc_laya/drive/` | raiz | B a F |
| 4 | **`r6-escolha-val.json`**, **`r6-escolha-val-d.json`**, **`r6-referencias.json`**, **`taxonomia.json`** (a original) | sim | repo: `poc_laya/drive/` | raiz | A (paridade), C (versão “rodada 7”) |
| 5 | **`catalogo-v2/`** (gerador da base sintética com `--rodada8` e avaliador `avaliar.mjs`; **sem** a pasta `dados`) | gerador: hash conferido na rodada 6; base `--rodada8` gerada no bloco A; avaliador: usado no C | repo: `poc_laya/drive/catalogo-v2/` | `laya-teste-r8/catalogo-v2/` | A a F (Node 18+ é instalado pelo notebook) |
| 6 | **`java-fonte/`** (só o pacote `core` do agente Java) | não (é o plano B) | repo: `poc_laya/drive/java-fonte/` | `laya-teste-r8/java-fonte/` | só se o portão das linhas de treino do B falhar |
| 7 | **Laya `laya2-ft-5000`** (checkpoint ajustado da rodada 6: `model.safetensors`, `rl_agent_config.json`, `questions.json`, `treino_gpu.json`, `encoder/`, `tokenizer/`) | **sim**: 94,6% de acerto sozinho no golden (rodada 6) e passou na trava e na paridade | **fora do repo** (≈1,8 GB). Local: `C:\Users\paula\Downloads\results\laya2-ft-5000` (a pasta, não o `.zip`). sha256 do `model.safetensors`: `10221499db39e94229f5b1ae0fdf3277f2171d73a2883f3a15a0220c9fc7a2d7` | `laya-teste-r8/laya-ft/laya2-ft-5000/` | A (trava e paridade), B (portão das linhas) |
| 8 | **Laya `laya3-ft-5000`** (checkpoint da rodada 8) | **sim**: treinado e travado no bloco B (o hash fica em `laya-ft/hashes-r8.json` e em `saida-r8/estado.json › b`) | **fora do repo**. Foi gerado dentro do Colab e **só existe no seu Drive**, em `laya-teste-r8/laya-ft/laya3-ft-5000/`. **Não precisa subir de novo**; para guardá-lo, baixe essa pasta do Drive | já está em `laya-teste-r8/laya-ft/laya3-ft-5000/` | C, D |
| 9 | **Naive Bayes** | o **código escrito à mão**: testado (paridade com a rodada 6/7). O **scikit-learn**: ainda **não** no Colab | **não é um arquivo**: é treinado a cada execução nos exemplos do `treino_5000` (segundos) e vive em `pipeline_v3.py` | — | C, D, E |
| 10 | **Embeddings e vizinhos** (`intfloat/multilingual-e5-base`) | sim (rodaram no C) | baixados do Hugging Face pelo próprio notebook; o índice é gravado em `saida-r8/cache/*.npy` | — | C, D, E |
| 11 | **SigLIP 2 e Qwen2.5-VL-7B** (bloco E) | não | baixados do Hugging Face pelo notebook; as fotos do ABO ficam em `abo-recorte/` (**nunca no repositório**) | — | E |
| 12 | **`saida-r7/`** (previsões de itens novos da rodada 7, `novos*.jsonl`) | — (opcional) | no seu Drive da rodada 7: `MyDrive/laya-teste/saida-r7/previsoes/novos-<rótulo>.jsonl` | `laya-teste-r8/saida-r7/` | D (diagnóstico dos erros de duplicado; sem ela a etapa é pulada) |
| 13 | **Chave do LLM** | — | Colab › Secrets (ícone de chave) | secret `OLLAMA_API_KEY`, “Acesso pelo notebook” ligado | C, D (sem ela, as partes com LLM ficam “não executadas”) |

**Resumo do que você precisa ter no Drive para testar do zero:** os itens **1 a 5** (a pasta `poc_laya/drive/` inteira), o **item 7** (copiado à parte) e o **item 13**. O item 8 se gera sozinho no bloco B (ou já existe, se você já rodou o B).

#### Já rodei os blocos A a C com a versão anterior: o que fazer ao trocar para esta pasta
Troque a **raiz** do Drive pelos arquivos de `poc_laya/drive/` (substitua os notebooks, `pipeline_v3.py`, `fluxo_r8.py`, `catalogo-v2/avaliacao/avaliar.mjs` e acrescente `perguntas_poc.py`). **Não precisa retreinar o Laya** (`laya3-ft-5000` continua válido). Mas, como o **Naive Bayes mudou**, parte do que está gravado ficou **desatualizado** e você deve **apagar** para não misturar resultados dos dois:

| Pasta ou arquivo no Drive (`saida-r8/`) | Fazer | Por quê |
|---|---|---|
| `cache/r8-todos-laya.jsonl`, `cache/r8-todos-emb.npy`, `cache/emb-treino5000-r8.npy`, `cache/emb-novos-r8.npy` | **manter** | não dependem do Naive Bayes (é o Laya e os embeddings) e são a parte lenta |
| `cache/fichas/` (todos os `v3-E*`, `migracao-N*`, `abl-*`), `cache/ficha-r7-todos.jsonl`, `cache/fichas-ablacao/`, `cache/llm-*.jsonl` | **apagar** | foram calculados com as categorias decididas pelo Naive Bayes antigo. Efeito colateral: o Laya refaz as sugestões de atributo na GPU (30 a 70 min, estimativa) |
| `r8-escolhas-val.json` | **apagar** | a regra de votação foi calibrada com o Naive Bayes antigo; é recalibrada no bloco C |
| `c/`, `d/`, `resultados/` | **apagar** (ou renomear para `*-nb-antigo`) | resultados do Naive Bayes antigo |
| `estado.json` | **manter** as chaves `a` e `b`; **remover** `c`, `d`, `e`, `f` | os blocos seguintes recusam rodar se o anterior não terminou |
| `a/` | manter; o bloco A, ao rodar de novo, grava a paridade nova em subpastas (`a/paridade/codigo` e `a/paridade/sklearn`) | assim você vê o efeito da troca do Naive Bayes |

Depois: rode o **A** (paridade com o código escrito à mão = portão; com o scikit-learn = informativa), pule o **B** (o `laya3` já existe, ele se pula sozinho) e rode **C › D › F**. O **E** não depende do Naive Bayes do pipeline e pode ficar como está.

---

## 5. Cada notebook: o que faz, o que grava, o que se espera

### A · `laya_r8_a_bases_e_paridade` (não responde pergunta; prepara e trava)
- **Portão do hash (1ª célula).** Gera a base original **sem** `--rodada8` e compara o sha256 de `produtos.jsonl`, `splits.json`, `taxonomia.json`, `novos.jsonl` e `buscas.jsonl` com os da rodada 6. **Se diferir, nada mais roda.** *Espera:* os cinco “IGUAL”.
- **Base nova** (`--rodada8`, semente 20261013, ruído 1,5): 100 mil produtos, 9 subcategorias novas, ruídos de atributo, árvore legada de ~1.700 nós, ambiguidade, buscas novas e itens novos. Confere os limites da cascata (≤ 16 departamentos, ≤ 6 categorias por departamento, ≤ 10 subcategorias por categoria).
- **Trava do `laya2-ft-5000`:** imprime nome e hash do `model.safetensors` e acerta o departamento de 120 produtos de validação (≥ 90% e 20 pontos acima do Laya base). *Por quê:* na rodada 6 um servidor respondeu com o modelo base achando que era o ajustado.
- **Paridade (duas passadas).** (1) **Portão:** o pipeline com o Naive Bayes escrito à mão reproduz a rodada 6/7 (0,5 ponto no saneamento e na ficha; 1 ponto nos itens novos e na busca). (2) **Informativa:** repete com o Naive Bayes do scikit-learn e mostra o *efeito da troca* linha a linha. *Espera:* a passada 1 sem nenhuma linha fora da tolerância; na passada 2, diferenças pequenas.
- *Grava:* `a/portao-hash-original.json`, `a/hashes-base-r8.json`, `a/paridade.json`, `a/paridade-sklearn.json`.

### B · `laya_r8_b_treino_laya` (não responde pergunta; prepara e trava)
- **Portão das linhas de treino:** gera em Python as linhas de ajuste fino da base original e compara com o `questions.json` do `laya2-ft-5000`. Se divergir, **para e mostra a diferença**; `USAR_PLANO_B = True` instala o JDK 17 no Colab e roda o gerador Java original (`java-fonte/`).
- Gera as linhas da base nova (perguntas antigas + energia + diagnóstico de nós, só nos nós de treino + “nenhuma destas” com os tipos de fora da taxonomia do grupo de treino) e **treina o `laya3-ft-5000`** a partir do mesmo checkpoint base (`multilingual`), com a mesma configuração da rodada 6. Pula se já existir no Drive.
- **Trava do novo checkpoint:** nome, hash, controle de departamento (≥ 90%) e de energia (≥ 90%).
- *Grava:* `laya-ft/laya3-ft-5000/`, `b/portao-linhas.json`, `b/trava-laya3.json`.

### C · `laya_r8_c_f1_e_f2` (responde P1, P5, parte de P9)
- Sinais dos 100 mil produtos (Naive Bayes **scikit-learn**, Laya, vizinhos); **calibração na validação** da regra de votação, da estatística do catálogo (folga do “fora do normal”), da faixa de dúvida e da pureza dos nós; **F1** (diagnóstico da árvore em quatro versões, publicação da árvore nova como versão, migração); **F2** (dado errado do vendedor); **ambiguidade**.
- No fim do notebook: **correção de regra da ficha** (diagnóstico de quem trocou o valor do vendedor + regra `v2`, sem GPU), feita depois da primeira execução.
- *Grava:* `r8-escolhas-val.json` (antes de medir o teste), `c/…`, `resultados/c.json`.

### D · `laya_r8_d_novos_e_busca` (responde P1 em parte, P2, P3, P4, P6, P8, P9)
- Reconstrói o contexto do C **sem recalibrar**; calibra o que é de itens novos e busca (limiar de embedding, “fora da taxonomia”, volume mínimo de subcategoria nova, correção de digitação, limite do freio); mede **itens novos**, **catálogo existente**, **busca**, **modo autônomo** e **quanto cada peça ajuda**.
- *Grava:* `d/…`, `resultados/d.json`.

### E · `imagem_r8` (responde P7)
- Fotos reais do **Amazon Berkeley Objects** (CC BY-NC 4.0, só avaliação interna): recorte de 15 a 25 tipos de produto e até 20 mil produtos; ruídos injetados no título e nos atributos (a foto fica intacta); **SigLIP 2** (imagem pequena, sempre) e **Qwen2.5-VL-7B** (só nas disputas; bf16 na L4, 8 bits na T4, 4 bits só se não couber); cinco versões comparadas.
- *Grava:* `imagem-r8-resumo.json`, `resultados/e.json`; as fotos ficam em `abo-recorte/` do seu Drive (**não** vão para o repositório).

### F · `laya_r8_f_painel` (junta P1 a P9)
- Gera `r8-resumo.html` e `r8-painel-dados.json`. **Seção 0:** a resposta de cada pergunta; seção 1: Atende / Não atende de cada critério; depois as quatro versões nomeadas (**Antes**, **Classificador**, **Classificador + agente + Laya** (rodada 7) e **Agente v3**), F1, a correção de regra, itens novos, busca, modo autônomo, ablação com consumo de LLM, imagem, exemplos lado a lado e as escolhas calibradas. As fotos do ABO **não** são embutidas (só o id), a menos que você ligue `EMBUTIR_FOTOS`.

---

## 6. Cada pergunta: contexto, como é medida, como ler, o que se espera

Convenção: **Atende / Não atende / Não medido** vêm de `r8-criterios.json` (fixo antes do código). O veredito da pergunta é **SIM** só se todos os critérios dela foram medidos e atendem.

### P1 · O catálogo existente fica organizado?
- **Contexto.** O catálogo atual chega com categoria do vendedor errada, atributos inconsistentes e uma árvore antiga. A pergunta é dupla: a **categoria** certa e a **ficha técnica padronizada**, **sem piorar o que já estava certo**.
- **Como é medido.** O avaliador (`avaliar.mjs saneamento`) compara a previsão com o gabarito nos produtos de **teste**, para as quatro versões. *Categoria:* **aplicados** (decididos pelo agente), **acerto dos aplicados**, **pioras** (o vendedor estava certo e o agente trocou) e **mantidos sem decisão**. *Ficha:* acerto dos atributos inferíveis, atributos **inventados**, atributos certos **estragados**, “não se aplica” neutralizado, “contradiz o anúncio” corrigido, “fora do normal” tirado do filtro, “sem pista” **não inventado**, não elétricos fora do filtro de voltagem. *Árvore (F1):* nós-atributo e nós de lixo detectados, destino dos nós de categoria e duplicados, tamanho da árvore resultante, migração dos produtos e atributo recuperado do nó (“Vestidos verdes” → Vestidos + cor = verde). *Catálogo existente:* duplicados e famílias de variação propostos, por par.
- **Como ler.** Na seção 2 do painel, compare *Antes* com *Agente v3*. **Pioras** é a medida direta de “estragar o que já estava certo” na categoria; **atributos certos estragados** é a mesma medida na ficha. A coluna “definição antiga” × “corrigida” (seção 2b) existe porque, na primeira execução, duas medidas de ficha contavam como erro uma simples normalização (ver [10](#10-estado-limites-e-historico)).
- **O que se espera.** Categoria certa em ≥ 98% dos aplicados e pioras ≤ 0,5% na migração; atributos certos estragados ≤ 1%; sem pista inventado ≤ 1%; fora do normal tirado do filtro ≥ 80% e **nenhum trocado por outro valor**; árvore resultante no máximo a taxonomia nova + 5%. A rodada 7 (sem as novidades) levou o catálogo de 51% para 95% de subcategoria certa, com 0,2% de pioras: a PoC **não pode piorar isso em mais de 0,5 ponto**.
- **Se não atender, olhe:** `c/diagnostico-f2.json` (qual etapa trocou o valor, com 10 exemplos por etapa) e `c/resultados-f2-correcao.json` (antes e depois).

### P2 · A busca fica mais eficiente?
- **Contexto.** Hoje a busca é por palavra sobre o catálogo como chegou: não entende “geladera” nem filtra por “220V” porque cada vendedor escreve o atributo de um jeito.
- **Como é medido.** `avaliar.mjs busca` nas consultas de **índice par** (as de índice ímpar calibraram a correção de digitação). *Busca por texto:* **P@10**, NDCG@10 e cobertura, por tipo de consulta (cabeça, atributo, marca, sinônimo, **erro de digitação**). *Por filtros:* navegação por categoria e filtros, em que o cliente clica na categoria e nos filtros certos e o que muda é o catálogo por baixo (o erro de digitação só vale na busca por texto). Catálogos: **Antes** (como chegou), **rodada 7** e **Agente v3**; os itens novos aceitos entram no catálogo.
- **Como ler.** Seção 5 do painel. Compare a linha *Antes* com *Agente v3* em P@10 e cobertura; o grupo `erro_digitacao` mostra o texto, e a navegação por filtros mostra os filtros.
- **O que se espera.** Mais produtos certos que a busca por palavra sobre o catálogo como chegou; **sem piorar mais de 0,5 ponto** em relação à rodada 7; “fogão bivolt” e afins com **+10 pontos de P@10** sobre a busca por palavra (critério `busca_bivolt`).

### P3 · Os itens novos entram certos?
- **Contexto.** Todo dia chegam cadastros novos; cada um precisa de categoria e os inválidos (“teste”, preço R$ 0, título vazio) precisam ser barrados antes de entrar.
- **Como é medido.** `avaliar.mjs novos` em `novos_teste`: **acerto da subcategoria dos classificados** e **precisão e cobertura de inválidos**. Versões: rodada 7 (NB + regras), Agente v3 sem LLM e com LLM nas disputas.
- **Como ler.** Seção 4 do painel, colunas *Classificados %* e *Inválidos prec/cob %*.
- **O que se espera.** Classificados ≥ 98% de acerto (a rodada 7 fez 99,7%); inválidos com precisão e cobertura ≥ 95%.

### P4 · Duplicados, variações, fora da taxonomia e cadastro legítimo
- **Contexto.** São as pendências da rodada 7: duplicados 75,0% de precisão e 77,2% de cobertura; fora da taxonomia 91,5% e 19,7%. Concorrentes de enriquecimento de catálogo cobrem duplicados; a PoC precisa cobrir também.
- **Como é medido.** *Duplicados:* a métrica oficial exige o **original exato** do grupo (comparável à rodada 7); como diagnóstico há a métrica **por grupo** (apontou qualquer cópia do mesmo grupo) e a contagem de erros por motivo (outra cópia do grupo, variação tratada como duplicado, produto diferente) e por pista (EAN igual, sem EAN, título reescrito, marca diferente). *Variações:* precisão, cobertura e acerto do eixo (cor, voltagem, tamanho…); variação **não** é duplicado e vira SKU do mesmo produto. *Fora da taxonomia:* precisão e cobertura, departamento provável e nome sugerido (por similaridade, com uma **amostra para conferir à mão**); os tipos do teste **nunca** aparecem no treino nem na validação (o agente reconhece um tipo que nunca viu). *Subcategorias novas:* criadas automaticamente só com volume mínimo e um juiz que confirma que não equivalem a uma existente nem a uma existente + filtro. *Limítrofes:* cadastros curtos mas legítimos (“Kit 3 panos”, “Pilha AA”, “iPhone 13 128GB”) barrados por engano.
- **Como ler.** Seção 4 do painel e `d/subcategorias-criadas.json`. O diagnóstico dos erros da rodada 7 só roda se você copiar as previsões dela (item 1 da seção 4); sem elas fica “não executado”.
- **O que se espera.** Duplicados ≥ 95% / ≥ 80%; variações ≥ 90% / ≥ 80%; fora da taxonomia ≥ 90% / ≥ 70% e departamento provável ≥ 85%; subcategorias criadas sem nenhuma equivalente a uma existente e com ≥ 90% dos itens alocados certos; limítrofes barrados por engano ≤ 2%.

### P5 · Ambiguidade
- **Contexto.** Alguns produtos têm duas casas legítimas (lavadora de alta pressão em Ferramentas e em Automotivo; aspirador portátil; caixa térmica em Camping e em Utilidades). O gabarito guarda a **principal** e a **aceita**; a regra do principal é de negócio, **por função declarada do produto** (escrita em `drive/catalogo-v2/README.md`).
- **Como é medido.** Nos produtos ambíguos do teste: **principal certa** e **precisão da secundária** sugerida (`subcategoria_secundaria`), com a margem calibrada na validação.
- **Como ler.** `c/resultados-f2.json › rodada8 › ambiguidade` (principal certa, aceita qualquer das duas, secundárias sugeridas e precisão).
- **O que se espera.** Principal certa ≥ 95%; secundária sugerida com precisão ≥ 80%.

### P6 · Busca sem exagero
- **Contexto.** Uma busca esperta demais “conserta” demais: “geladera de praia nuclear” vira geladeira, e “capinha iphone 15” devolve capinhas de qualquer modelo.
- **Como é medido.** Grupo `sem_resposta`: % que volta **vazia ou com aviso** de “nenhum resultado” (fica fora das médias de “todas”). Grupo `com_modelo`: **P@10** em que só é relevante o que bate o modelo (o modelo vira filtro obrigatório; sem produto desse modelo a lista volta vazia). A correção de digitação só acontece acima de um limiar de similaridade e de frequência calibrado na validação.
- **Como ler.** Seção 5 do painel (colunas *sem_resposta vazias %* e *com_modelo P@10 %*) e os exemplos impressos no notebook D.
- **O que se espera.** ≥ 90% das consultas sem resposta voltam vazias ou com aviso; consultas com modelo com P@10 ≥ 90%.

### P7 · Texto + imagem
- **Contexto.** A base sintética não tem fotos, então esta pergunta tem trilha própria: fotos reais (ABO) com **os mesmos tipos de erro do vendedor injetados** (título só com a linha comercial, categoria errada, cor ausente, cor que contradiz a foto, controle).
- **Como é medido.** **Cinco versões:** (1) Antes; (2) só texto (embeddings + vizinhos + NB); (3) + imagem pequena (SigLIP 2: vizinhos por imagem e atributos visuais com confiança); (4) + modelo de visão grande nas disputas; (5) modelo de visão grande em tudo, numa amostra de 1.500. Mede categoria (aplicados, acerto, pioras, **acerto final do catálogo**) no teste inteiro e por tipo de ruído; cor (acerto no que preenche, contradições detectadas, cores certas estragadas); **custo** (imagens por segundo, % que vai ao modelo grande, tempo, PROJEÇÃO para 100 mil produtos).
- **Como ler.** Seção 8 do painel. O ruído principal é “título sem o nome do produto”: é onde a foto deve ajudar mais.
- **O que se espera.** Texto + imagem ≥ só texto + **15 pontos** no título sem o nome do produto e ≥ + **3 pontos** no teste inteiro, pioras ≤ 0,5%; cor preenchida com ≥ 90% de acerto; cor que contradiz a foto detectada em ≥ 85% com ≤ 1% de cores certas estragadas; modelo grande em **≤ 10%** dos produtos, com a versão 4 a no máximo **1 ponto** da versão 5.

### P8 · Automação total com segurança
- **Contexto.** Para rodar em escala ninguém pode aprovar produto por produto. A segurança precisa vir do processo.
- **Como é medido.** (1) **Reversão:** aplica um lote, reverte e compara o **hash** do catálogo. (2) **Auditoria automática:** um juiz confere uma amostra (300) de cada lote e estima a taxa de erro (com intervalo de Wilson); o **erro real** é medido pelo gabarito só depois, para comparar. (3) **Freio:** um lote **propositalmente ruim** (as subcategorias embaralhadas) precisa ser pausado e **revertido** sozinho; o limite vem da validação. (4) **Jornal** de toda ação (valor anterior, motivo, sinais, versão do modelo) e **relatório de exceções**.
- **Como ler.** Seção 6 do painel: cada lote com erro real × estimado, se o freio disparou e se foi revertido. Sem a chave do LLM o juiz é **por regras** (só para exercitar o freio) e o painel marca isso.
- **O que se espera.** Erro estimado a no máximo **1 ponto** do erro real (com 300 itens por lote o intervalo é de cerca de ±3 pontos: o critério **pode falhar por causa da amostra**, e o painel mostra a diferença como ela é); lote ruim revertido 100%; reversão com hash idêntico.

### P9 · Custo de LLM
- **Contexto.** Se a qualidade só melhora mandando tudo ao LLM, a solução não escala (a 30 chamadas por minuto, 2 milhões de produtos levariam semanas).
- **Como é medido.** % de produtos que vão ao LLM **na categoria** (meta: não mais que **5,4%**, o valor da rodada 7); % dos atributos que vão ao LLM; chamadas por produto e tokens de entrada e saída, **por versão**, ao lado da qualidade (tabela *Quanto cada peça ajuda*: versão completa e sem vizinhos, sem estatística, sem a pergunta de energia, sem “categoria primeiro”). O LLM sempre responde restrito a opções e as respostas rejeitadas são contadas. A **estimativa de chamadas é mostrada antes** de cada etapa que usa o LLM.
- **Como ler.** Seção 7 do painel. **Sem a chave do LLM este critério fica “não medido”**, e isso é o correto.
- **O que se espera.** ≤ 5,4% dos produtos ao LLM na categoria; ≤ 10% dos atributos avaliados ao LLM. *(Após a correção de regra, o LLM nos atributos está desligado: o consumo de atributos é 0 e o critério da faixa de dúvida com LLM fica “não medido”, por decisão.)*

---

## 7. O Naive Bayes agora é o da biblioteca scikit-learn

**O que mudou.** Antes, o pipeline usava um Naive Bayes **escrito à mão** (a porta Python do `ClassificadorTexto.java`, com TF-IDF em ponto fixo). Nesta PoC `pv.NaiveBayes` é a classe `NaiveBayesSklearn` (em `drive/pipeline_v3.py`): `sklearn.naive_bayes.MultinomialNB(alpha=0,1)` sobre `sklearn.feature_extraction.text.TfidfVectorizer` (sem normalização L2, idf suavizado, que é a mesma fórmula do código). Os **termos** são os mesmos (título ×3, valores dos atributos, destaques e descrição) e a confiança continua calibrada por **temperatura** em 20% dos exemplos separados antes. A **interface não mudou** (`treinar`, `prever_lote`, `temperatura`), então nenhum notebook precisou mudar a chamada. O código antigo continua em `NaiveBayesCodigo`, **só** para a paridade e para comparar.

**Diferenças conhecidas.** (1) a *a priori* de classe é a da biblioteca (frequência observada, sem o suavizado do código); (2) o TF-IDF não é arredondado em ponto fixo; (3) o embaralhamento da calibração usa `numpy` (semente), não o `Random` do Java. Por isso os números **não são idênticos** aos da rodada 6/7.

**Como saber o efeito da troca.** O bloco A roda a paridade **duas vezes** com os mesmos dados e o mesmo Laya: com o código (portão, prova que o pipeline reproduz o agente Java) e com o scikit-learn (informativa), e grava a tabela *efeito da troca* em `a/paridade-sklearn.json`. Se o efeito for de décimos de ponto, a biblioteca substitui o código sem perda.

**O que foi e não foi verificado.** A classe foi testada localmente só em **dados de brinquedo** (acerto e calibração equivalentes aos do código escrito à mão); **no Colab ainda não foi executada**. O bloco E (imagem) já usava `MultinomialNB`/`TfidfVectorizer` do scikit-learn desde o início.

---

## 8. Dados reais: o que fazer e o que ainda não está pronto

**Objetivo:** validar a PoC com o catálogo do cliente. **O adaptador para dados reais não foi construído**: os notebooks geram a base sintética (`garantir_bases()`, bloco A) e várias medidas dependem de artefatos que só o gerador produz (`erros_de_atributo`, `arvore_gabarito.json`, `fora_treino.jsonl`, `categoria_legada_status`). O que existe é o **contrato de dados**:

**Arquivos que o pipeline lê** (formato JSONL, um objeto por linha; `taxonomia.json` no formato de `drive/taxonomia.json`):

| Arquivo | Conteúdo |
|---|---|
| `produtos.jsonl` | o catálogo: `id`, `titulo`, `descricao`, `bullets` (lista), `marca_informada`, `modelo`, `ean`, `categoria_vendedor`, `tags`, `atributos` (dicionário cru do vendedor), `preco`, `condicao`, `vendedor {id, nome}`, `data_cadastro`; na migração, `categoria_legada` (id do nó) |
| `taxonomia.json` | departamentos › categorias › subcategorias, com `termos`, `confunde_com`, critérios pt e en e a **ficha** (atributos, tipo, valores) de cada subcategoria; `sobreposicoes` para a ambiguidade |
| `splits.json` | listas de ids: `treino_5000` (e `treino_2000`, `treino_20000`), `validacao`, `golden`, `teste_rodadas`, `novos_teste` |
| `novos.jsonl` | os itens novos (mesmos campos) |
| `buscas.jsonl` | consultas com a `intencao` (subcategorias, filtros, marca) para medir a busca |
| `arvore_legada.json`, `arvore_splits.json` | a árvore antiga (`id`, `nome`, `caminho`, `pai`) e a divisão de nós em treino e teste |

**Gabarito (campo `gabarito` de cada produto) — só para treinar, calibrar e medir:** `subcategoria` (e `departamento`, `categoria`), `atributos` (valores corretos), `atributos_inferiveis` (quais aparecem em algum lugar do anúncio), `categoria_vendedor_status` (`correta`, `errada_irma`, `generica`, `vazia`…), `marca`, `dificuldade`; para itens novos, `duplicado_de`, `variacao_de`, `fora_da_taxonomia`, `invalido`; para ambiguidade, `subcategorias_aceitas`. **O pipeline nunca lê o gabarito**: só o avaliador (e a rotulagem de treino e de validação).

**Roteiro sugerido:**
1. **Rotular com o time** um conjunto de ouro (≥ 1.000 produtos) e um treino (`treino_5000`: categoria correta, que o time já tenha ou revise) — sem rótulos reais não há como medir nem treinar.
2. **Montar os arquivos acima** a partir do catálogo real e da árvore real, e apontar `D_R8` para eles (substituir `garantir_bases()` e o portão do hash, que só valem para a base sintética).
3. **Retreinar** o Laya (bloco B) e o Naive Bayes nos rótulos reais; rodar C, D e F. O bloco E precisa de fotos do próprio cliente no lugar do ABO.
4. **Medidas sem gabarito real** (ex.: `erros_de_atributo`, `fora_treino.jsonl`) ficam “não medidas” até que o time as rotule; o painel mostra “não medido”, não “atende”.
5. **Privacidade:** enviar produtos reais ao Colab, ao Drive, ao Kaggle ou ao Ollama Cloud **precisa de validação do time**; deixe `OLLAMA_API_KEY` fora do ambiente até lá (as partes com LLM aparecem como “não executadas”).

---

## 9. Critérios fixos por pergunta

Gravados em `drive/r8-criterios.json` **antes** do código e nunca alterados depois. Um critério pode aparecer em mais de uma pergunta. (Tabela gerada por `fontes/atualizar_tabela_criterios.py`.)

<!--CRITERIOS-->
| Pergunta | Critério (id) | O que mede | Meta fixa | Medido no notebook |
|---|---|---|---|---|
| **P1** | `rodada7_mantida` | Rodada 7 mantida: categoria certa, pioras e busca | piora pontos ≤ 0.5 | C (categoria) e D (busca): vale a pior |
| **P1** | `mantidos_sem_decisao` | Produtos mantidos sem decisao | taxa ≤ 0.05 | C |
| **P1** | `atributos_estragados` | Atributos certos estragados | taxa ≤ 0.01 | C |
| **P1** | `nao_se_aplica` | Nao se aplica neutralizado | taxa ≥ 0.95 | C |
| **P1** | `contradiz_anuncio` | Contradiz o anuncio corrigido | taxa ≥ 0.9 | C |
| **P1** | `fora_do_normal` | Fora do normal tirado do filtro (nenhum trocado por outro valor) | taxa ≥ 0.8; trocados por outro valor ≤ 0 | C |
| **P1** | `sem_pista_inventado` | Sem pista: corrigido sem base (inventado) | taxa ≤ 0.01 | C |
| **P1** | `nao_eletricos_no_filtro` | Nao eletricos no filtro de voltagem | taxa ≤ 0.01 | C |
| **P1** | `faixa_duvida_llm` | Faixa de duvida com LLM | acerto no que o llm preencher ≥ 0.95 | C |
| **P1** | `no_atributo` | Nos-atributo detectados | precisao ≥ 0.9; cobertura ≥ 0.85 | C |
| **P1** | `no_lixo` | Nos de lixo detectados | precisao ≥ 0.9; cobertura ≥ 0.8 | C |
| **P1** | `no_destino` | Destino dos nos de categoria e duplicados | acerto ≥ 0.95 | C |
| **P1** | `arvore_tamanho` | Arvore resultante | excesso sobre subcategorias da taxonomia nova ≤ 0.05 | C |
| **P1** | `migracao` | Migracao dos produtos | aplicados ≥ 0.7; acerto ≥ 0.98; pioras ≤ 0.005 | C |
| **P1** | `atributo_no_legado` | Atributo do no legado recuperado | acerto ≥ 0.9 | C |
| **P1** | `duplicados_existente` | Duplicados e variacoes no catalogo existente | precisao por par ≥ 0.95 | D |
| **P2** | `busca_bivolt` | Busca fogao bivolt e afins | ganho precisao top10 sobre linha de base pontos ≥ 10 | D |
| **P2** | `rodada7_mantida` | Rodada 7 mantida: categoria certa, pioras e busca | piora pontos ≤ 0.5 | C (categoria) e D (busca): vale a pior |
| **P3** | `novos_classificados` | Itens novos classificados (rodada 7: 99,7%) | acerto ≥ 0.98 | D |
| **P3** | `invalidos` | Invalidos | precisao ≥ 0.95; cobertura ≥ 0.95; limitrofes barrados por engano ≤ 0.02 | D |
| **P4** | `novos_duplicados` | Duplicados (itens novos), original exato | precisao ≥ 0.95; cobertura ≥ 0.8 | D |
| **P4** | `novos_variacoes` | Variacoes (itens novos) | precisao ≥ 0.9; cobertura ≥ 0.8 | D |
| **P4** | `fora_taxonomia` | Fora da taxonomia | precisao ≥ 0.9; cobertura ≥ 0.7 | D |
| **P4** | `fora_taxonomia_dep` | Fora da taxonomia: departamento provavel | acerto ≥ 0.85 | D |
| **P4** | `subcategorias_novas` | Subcategorias novas criadas | precisao dos itens alocados ≥ 0.9; equivalentes a existente ≤ 0 | D |
| **P4** | `invalidos` | Invalidos | precisao ≥ 0.95; cobertura ≥ 0.95; limitrofes barrados por engano ≤ 0.02 | D |
| **P5** | `ambiguidade` | Ambiguidade | principal ≥ 0.95; secundaria precisao ≥ 0.8 | C |
| **P6** | `busca_sem_resposta` | Busca sem resposta | vazias ou com aviso ≥ 0.9 | D |
| **P6** | `busca_com_modelo` | Busca com modelo | precisao top10 ≥ 0.9 | D |
| **P7** | `img_titulo_sem_nome` | Imagem: categoria com titulo sem o nome do produto | ganho sobre so texto pontos ≥ 15 | E |
| **P7** | `img_teste_inteiro` | Imagem: categoria no teste inteiro | ganho sobre so texto pontos ≥ 3; pioras ≤ 0.005 | E |
| **P7** | `img_cor_nao_dita` | Imagem: cor quando o texto nao diz | acerto no preenchido ≥ 0.9 | E |
| **P7** | `img_cor_contradiz` | Imagem: cor que contradiz a foto | detectada ≥ 0.85; cores certas estragadas ≤ 0.01 | E |
| **P7** | `img_modelo_grande` | Imagem: uso do modelo de visao grande | fracao dos produtos ≤ 0.1; versao4 abaixo da versao5 pontos ≤ 1.0 | E |
| **P8** | `auditoria_automatica` | Auditoria automatica (juiz por amostragem) | distancia do erro real pontos ≤ 1.0 | D |
| **P8** | `freio_automatico` | Freio automatico: lote com erro injetado, pausa e reverte | lote revertido ≥ 1.0 | D |
| **P8** | `reversao` | Reversao: acoes do lote revertidas deixam o catalogo identico (hash) | hash identico ≥ 1.0 | D |
| **P9** | `consumo_llm_categoria` | Consumo de LLM na categoria: % de produtos que vao ao LLM <= rodada 7 | fracao dos produtos ≤ 0.054 | C |
| **P9** | `consumo_llm_total` | Consumo de LLM total: chamadas por produto informadas por etapa | atributos ao llm fracao dos avaliados ≤ 0.1 | C |
<!--/CRITERIOS-->

---

## 10. Estado, limites e histórico

- **Estado ao empacotar.** Os blocos **A, B e C foram executados** no Colab (o C teve de ser executado para chegar à correção abaixo); **não há resultados dos blocos D, E e F neste repositório**. Os notebooks foram escritos e conferidos só na sintaxe (`py_compile`, `pyflakes`, JSON dos notebooks, `node --check`, `mvn compile`); os testes Java novos (limpeza de entrada do `Preparador`) foram só compilados.
- **O Naive Bayes do scikit-learn ainda não foi executado no Colab** (seção 7); os blocos A e B rodaram com o código escrito à mão.
- **Primeira execução do bloco C.** F1, “não se aplica”, “contradiz o anúncio” e consumo de LLM atenderam. **Não atenderam:** fora do normal (36,1% trocados por outro valor, meta 0), sem pista (28,7% “corrigidos sem base”, meta ≤ 1%), atributos certos estragados (2,7%, meta ≤ 1%) e faixa de dúvida com LLM (72% de acerto, meta ≥ 95%).
- **Correção de regra feita depois da primeira execução** (célula no fim do bloco C, registrada como tal no painel, com os números antes e depois): (1) um **diagnóstico** mostra qual etapa trocou cada valor do vendedor (anúncio, nó legado, Laya, LLM, estatística…) com 10 exemplos por etapa; (2) a **regra `v2`**: o valor do vendedor só muda por “não se aplica” ou por valor explícito diferente no título ou na descrição, Laya e nó legado só preenchem o que o vendedor não informou, fora do normal sai do filtro e não volta, **LLM nos atributos desligado**; (3) a medição passou a usar o **original** do vendedor lido pelo próprio pipeline (`atributos_originais`), com as definições antigas calculadas ao lado. **A causa não estava confirmada** ao empacotar (as hipóteses eram o encadeamento das etapas e duas definições de medição); o diagnóstico é quem decide. Os destaques (“Chave: valor”) do anúncio entram em “valor explícito”; se o diagnóstico mostrar que causam erro, restringir a título e descrição.
- **Limites.** Calibração só na validação (nos nós da árvore, nos nós de **treino**); a auditoria com 300 itens por lote tem intervalo largo; a versão “Classificador + agente + Laya (rodada 7)” na base nova usa os **mesmos sinais** (Laya `laya3`) e a votação e a ficha da rodada 7; no bloco E, o gabarito é o do ABO (tipo, cor, material), que tem erros e lacunas; tempos são estimativas e projeções estão marcadas.
- **Licenças.** A trilha de imagem usa o ABO (CC BY-NC 4.0): só avaliação interna; não redistribua as imagens, não as inclua no repositório, não as use em produção.


---

## 11. Treinar em uma base nova, com estrutura nova, no Colab

**Resposta curta.** Para treinar em uma base com estrutura nova você precisa de **quatro coisas**: (1) descrever a estrutura nova na `taxonomia.json`; (2) entregar **produtos rotulados** e separá-los em treino, validação, golden e teste; (3) **adaptar os pontos do código que são do catálogo sintético** (lista na tabela abaixo); (4) rodar no Colab **B › C › D › F**. Dos modelos, só o **Laya** precisa de treino novo (**~2,5 a 4 h na T4, estimativa**); o Naive Bayes (segundos), o índice de vizinhos (minutos) e todas as calibrações (regra de votação, limiares, margens) **se refazem sozinhos** nos blocos C e D, só na validação. O guia de treino do Laya em detalhe está em [`docs/treinar-o-modelo.md`](../docs/treinar-o-modelo.md).

> **Honestidade sobre o estado do código.** A PoC foi escrita para a base sintética. Hoje os notebooks **não têm um “modo base nova”**: os passos 3 e 5 abaixo são edições que você (ou eu, se pedir) faz uma vez. Eu **não** executei nada disto.

### 11.1 O que “estrutura nova” significa (o que muda em cada caso)

| O que é novo | Taxonomia | Laya | Naive Bayes + vizinhos | Código |
|---|---|---|---|---|
| **Outro catálogo, mesma taxonomia e mesmos campos** | igual | **não precisa** retreinar (mas confira o desempenho na validação) | retreina (segundos) | só os pontos de dados (passo 3) |
| **Taxonomia nova** (outros departamentos, categorias e subcategorias) | **nova** | **retreina** | retreina | passo 3 |
| **Campos ou atributos novos** (ficha técnica diferente) | ficha nova | **retreina** (a ficha usa uma pergunta por atributo) | não | regras de unidades e aliases, se houver unidade nova |
| **Nível a mais na árvore** | nova | retreina | retreina | **sim**: a cascata é fixa em 3 níveis (departamento › categoria › subcategoria) |

**Limites do desenho** (de [`docs/treinar-o-modelo.md`](../docs/treinar-o-modelo.md), seção 2): o Laya só vê perguntas com **até ~20 opções**. A estrutura sintética usa no máximo 16 departamentos, 6 categorias por departamento, 10 subcategorias por categoria e, na ficha com o Laya, atributos enumerados com até 19 valores. Uma árvore real maior (o caso dos ~1.700 nós) **não vai direto para a cascata**: é o que o F1 (bloco C) diagnostica e enxuga antes. Se uma categoria passar de ~20 subcategorias, a resposta certa pode nem aparecer nas opções: redistribua em mais categorias.

### 11.2 Passo a passo

**Passo 1 · Escreva a `taxonomia.json` da base nova** (formato de `drive/taxonomia.json`; contrato na seção 8). Para cada subcategoria: `id`, `nome`, `nome_en`, `criterio` e `criterio_en` (**o critério em inglês é o que o Laya lê**; sem ele cai para o português), `caminho`, `termos` (o dicionário da busca) e `atributos` (`chave`, `nome`, `nome_en`, `tipo` enum/num/texto, `valores`, `unidade`). Preencha também `categorias_genericas` (“Outros”, “Diversos”…), `sinonimos_valor` (como o vendedor escreve cada valor) e, se houver, `sobreposicoes` (pares com duas casas legítimas). Respeite os limites acima. *Um bom critério explicita o que **não** inclui* (“Não inclui: …”), porque é o que separa as subcategorias vizinhas.

**Passo 2 · Reúna produtos rotulados e separe os conjuntos** (`produtos.jsonl` + `splits.json`):

| Conjunto | Para que serve | Quantos (guia; estimativa para taxonomias maiores) |
|---|---|---|
| `treino_5000` (e `treino_2000`, `treino_20000`) | treina o Laya, o Naive Bayes e o índice de vizinhos | **~20 exemplos por subcategoria no mínimo** (na base atual, 5.000 para 205 subcategorias deram 96,5% de acerto no Naive Bayes e 94,6% no Laya sozinho; com 2.000 foram 91,0% e 88,7%) |
| `validacao` | **só** escolher parâmetros (regra de votação, limiares) | ≥ 2.000 |
| `golden` e `teste_rodadas` | **só medir** | ≥ 1.000 e o restante |
| `novos` e `novos_teste` | itens novos (se for responder P3 e P4) | ≥ 1.000 |

Regras: **nenhum id em dois conjuntos**; golden e teste **nunca** entram no treino (o pipeline recusa); duplicados e variações do mesmo produto ficam **do mesmo lado** do corte (senão o teste “já viu” o produto). O rótulo do treino é a **subcategoria correta**, revisada pelo time (na base sintética vem do campo `gabarito`, só dos ids de treino).

**Passo 3 · Adapte o código (uma vez).** O que é do catálogo sintético e precisa mudar:

| Onde | O que é do sintético | O que fazer |
|---|---|---|
| `fontes/notebooks/r8_nb_comum.py` › `garantir_bases()` e `drive/fluxo_r8.py` | gera a base com Node (`gerar-catalogo-v2.mjs`) e confere hashes | trocar por **copiar** `MyDrive/laya-teste-r8/dados-novos/*` para `D_R8` (e tirar a conferência de hash) |
| `fontes/notebooks/r8_nb_comum.py` › `SAIDA` | `saida-r8/` | usar **uma pasta de saída nova** (ex.: `saida-<nome-da-base>/`). **Os caches são por id de produto**: se a base nova reaproveitar ids (P0000001…), o Laya e os embeddings antigos seriam lidos como se fossem da base nova |
| Bloco **A** | portão do hash da base original, paridade com a rodada 6/7 (`r6-referencias.json`) | **pular** (só valem para a base sintética). Os blocos seguintes exigem `estado.json` com o A concluído: registre `{"a": {"ok": true}}` ou tire o `exigir("a")` |
| Bloco **B** (`laya_r8_b_treino_laya`) | usa `arvore_legada/gabarito/splits.json`, `fora_treino.jsonl`, e um `assert` com os nomes dos tipos de fora da taxonomia do teste | sem árvore legada: **remova** as linhas de nós; sem `fora_treino.jsonl`: sem a opção “nenhuma destas” (ou crie exemplos de fora da taxonomia com o time); apague o `assert` dos tipos do teste; troque `NOME = "laya3-ft-5000"` por um nome novo |
| `drive/pipeline_v3.py` › `usa_energia_por_sub` | lista de atributos que indicam energia elétrica (`voltagem`, `potencia_w`…) | ajuste à ficha da base nova, ou **desligue** a pergunta de energia (`Opcoes(pergunta_energia=False)`) se o catálogo não tiver esse tipo de disputa |
| `drive/pipeline_v3.py` › `EIXOS_DE_VARIACAO`, `PALAVRAS_DE_CAMPANHA`, `_PROMO` | eixos de variação (cor, voltagem, tamanho…), vocabulário de campanha e de promoção **em português** | ajuste aos eixos, ao idioma e ao vocabulário do catálogo novo |
| Bloco **C** | F1 só existe se há árvore antiga; F2 usa `erros_de_atributo` e `atributos_inferiveis` do gabarito | sem árvore: pule o F1; sem esses campos no gabarito, as medidas do F2 correspondentes ficam **não medidas** (o painel mostra “não medido”) |
| `drive/r8-criterios.json` | metas fixas para a base sintética | **revise as metas** com o time **antes** de rodar, e grave-as antes (a regra da PoC é fixar o critério antes de ver o resultado) |

**Passo 4 · Suba para o Drive** `MyDrive/laya-teste-r8/` (o pacote `drive/` já adaptado) e `MyDrive/laya-teste-r8/dados-novos/` (`taxonomia.json`, `produtos.jsonl`, `splits.json`, e `novos.jsonl`, `buscas.jsonl`, `arvore_*` se houver). Copie `laya-ft/` **só se** for reaproveitar um checkpoint.

**Passo 5 · Treine no Colab (GPU L4 de preferência, T4 serve; mantenha a aba aberta).**
1. **Bloco B**: gera as linhas de treino do Laya com **as mesmas perguntas que o agente faz em produção** (departamento, categoria com 1 e 2 departamentos, subcategoria com “nenhuma destas”, ficha técnica) a partir do `treino_5000`, e treina a partir do checkpoint base `multilingual` (4 épocas, micro-lote 8, acumulação 8, amp e checkpointing: a configuração da rodada 6; pico de ~6,2 GB de VRAM). Grava em `laya-ft/<nome-novo>/` e **só copia para o Drive ao terminar** (se a sessão cair no meio, o que estava treinando se perde; checkpoints já prontos são pulados).
2. **Trava do checkpoint** (no final do B e no início do C): imprime nome e hash do `model.safetensors` e acerta o **departamento de 120 produtos de validação** (≥ 90% e 20 pontos acima do Laya base). **Não pule**: na rodada 6 um servidor respondeu com o modelo base achando que era o ajustado.
3. **Bloco C**: treina o Naive Bayes (scikit-learn) e monta o índice de vizinhos no `treino_5000`; **calibra só na validação** e grava `r8-escolhas-val.json` antes de medir.
4. **Blocos D e F**: itens novos, busca, modo autônomo e o painel com a resposta de cada pergunta.

**Passo 6 · Confira antes de acreditar nos números.**
- O `golden` e o `teste_rodadas` **não** entram em nenhum treino (o pipeline recusa); o painel mostra “não medido” onde faltar gabarito, nunca “atende”.
- Compare *Antes* × *Agente v3* e olhe as **pioras** (o que estava certo e piorou).
- Sem a chave do LLM, as partes com LLM aparecem como “não executadas”: isso é o esperado, não um erro.

### 11.3 Tempo e custo (estimativas, não medições)

| Etapa | Estimativa |
|---|---|
| Linhas de treino + treino do Laya (5.000 exemplos) | 2,5 a 4 h na T4 (na rodada 6, 5.000 exemplos levaram 8.502 s na T4 do Kaggle; as perguntas novas acrescentam ~30%) |
| Naive Bayes | segundos |
| Embeddings e índice de vizinhos | 5 a 10 min para 100 mil produtos |
| Laya na cascata sobre 100 mil produtos | 0,7 a 2,5 h |
| Chamadas ao LLM | 30 por minuto no plano gratuito; o notebook **mostra a estimativa antes** e retoma no erro 429 |

### 11.4 Privacidade
Com **dados reais de cliente**, enviar produtos ao Colab, ao Google Drive, ao Kaggle ou ao Ollama Cloud **precisa de validação do time**. Rotule e treine com dados que o time autorizou; deixe `OLLAMA_API_KEY` fora do ambiente até lá. Chaves **só** em Colab Secrets.

### 11.5 Só quero treinar o Laya (sem rodar tudo)
Rode **B** (e a trava dele). O resto (C, D, F) só é necessário para **medir**. Se for só treinar e servir, [`docs/treinar-o-modelo.md`](../docs/treinar-o-modelo.md) tem o passo a passo do treino e do servidor com trava de identidade.

### 11.6 O que ainda não está pronto
Um **seletor `FONTE_DE_DADOS = "sintetico" | "nova"`** que faça os passos 3 e 5 sozinho (copiar `dados-novos/`, pular A, pular F1 sem árvore, saída separada e alerta de ids repetidos). Hoje isso é edição manual.
