# Catálogo sintético v2

Base de teste para medir, de forma justa, se um modelo (Laya, LLM, classificador treinado) consegue:

1. **sanear o catálogo atual**: subcategoria certa, ficha técnica (atributos) e marca;
2. **tratar itens novos**: classificar, detectar duplicados, produtos fora da taxonomia e cadastros inválidos;
3. **melhorar a busca**: entender a consulta (subcategoria, filtros, marca) e devolver resultados relevantes.

Tudo é sintético e reproduzível: a mesma semente gera exatamente os mesmos arquivos.

## Como gerar

Precisa de Node 18 ou mais novo. Leva uns 15 segundos.

```bash
node gerador/gerar-catalogo-v2.mjs
# opções: --produtos 100000 --novos 10000 --semente 20261008 --ruido 1 --saida dados
```

Para um teste com produtos que nenhum modelo viu, gere outra base com outra semente. Com `--ruido 1.5`, os anúncios ficam 50% mais bagunçados (30% de produtos difíceis, contra 22% na base original):

```bash
node gerador/gerar-catalogo-v2.mjs --semente 20261013 --ruido 1.5 --novos 0 --saida dados-novos-100k
node avaliacao/avaliar.mjs saneamento --dados dados-novos-100k --ids todos --pred previsoes.jsonl
```

A base original (semente 20261008, ruído 1) continua idêntica: os modelos treinados nela podem ser testados na nova.

## O que tem dentro

| Arquivo | Conteúdo |
|---|---|
| `dados/produtos.jsonl` | 100.000 anúncios do catálogo atual, bagunçados, cada um com `gabarito` |
| `dados/novos.jsonl` | 10.000 itens novos (cadastro de outubro/2026) |
| `dados/buscas.jsonl` | 900 consultas com a intenção correta |
| `dados/taxonomia.json` | 16 departamentos, 57 categorias, 205 subcategorias, critérios PT/EN, ficha técnica de cada subcategoria |
| `dados/splits.json` | golden (1.001), teste_rodadas (504), validação (2.002), treino (200 / 2.000 / 5.000 / 20.000), novos_teste (1.000) |
| `dados/estatisticas.json` | números da base |
| `dados/amostra.csv` | 300 produtos para abrir no Excel |
| `avaliacao/avaliar.mjs` | avaliador de referência (saneamento, novos e busca) |

### Um produto (real, da base gerada)

```json
{
  "id": "P0008585",
  "titulo": "Realme 12 Preto 48 MP AMOLED - Realme",
  "bullets": ["Desbloqueio por biometria.", "Desempenho para jogos e aplicativos."],
  "descricao": "Bateria de longa duração. Garantia de 3 meses. Conteúdo da embalagem: 1 smartphone. Produto novo, lacrado e com nota fiscal.",
  "marca_informada": "Realme", "modelo": "REA461", "ean": "7897473230781",
  "categoria_vendedor": "Celulares e telefonia > Smartphones > Celulares básicos",
  "tags": "smartphone,smartphones",
  "atributos": {"Memória interna": "64", "Cor principal": "ônix", "Rede": "4G", "Câmera": "48", "Bateria": "5000mAh", "Origem": "Nacional"},
  "preco": "1266", "condicao": "novo",
  "vendedor": {"id": "loja_090", "nome": "Digital Super 80"},
  "gabarito": {
    "caminho": "Celulares e telefonia > Smartphones > Smartphones",
    "marca": "Realme", "linha": "Realme 12",
    "atributos": {"armazenamento_gb": "64", "memoria_ram_gb": "12", "tela_pol": 6.5, "cor": "preto", "sistema_operacional": "Android", "rede": "4G", "camera_mp": "48", "bateria_mah": "5000", "nfc": "não", "dual_chip": "sim", "processador_cel": "MediaTek Helio", "tecnologia_tela_cel": "AMOLED", "taxa_hz": "120", "garantia_meses": "3", "origem": "nacional", "peso_produto_kg": 0.27},
    "atributos_inferiveis": ["armazenamento_gb", "cor", "rede", "camera_mp", "bateria_mah", "tecnologia_tela_cel", "garantia_meses", "origem"],
    "categoria_vendedor_status": "errada_confusao", "dificuldade": "dificil",
    "ruidos": ["titulo_so_com_linha", "categoria_vendedor_errada"]
  }
}
```

O título não diz "smartphone": só a linha comercial ("Realme 12"). O vendedor cadastrou em "Celulares básicos". Dos 16 atributos verdadeiros, 8 aparecem em algum campo do anúncio; os outros 8 o certo é responder `nao_informado`.

O **gabarito** traz a subcategoria (e o caminho completo), a ficha técnica verdadeira, a marca, a linha comercial, quais atributos aparecem em algum lugar do anúncio (`atributos_inferiveis`), quais o vendedor informou errado sem outra pista no texto (`atributos_errados_pelo_vendedor`, que ficam fora da conta), o status da categoria do vendedor, os ruídos aplicados, a dificuldade, e os vínculos `duplicado_de` e `variacao_de`.

**O agente nunca pode ler o gabarito.** Só o avaliador e os simuladores.

## Por que é mais difícil que a v1

| | v1 | v2 |
|---|---|---|
| Níveis da taxonomia | 2 (16 > 58) | 3 (16 > 57 > 205) |
| Atributos por produto | 4 fixos | 6,5 em média, até 18 (ficha técnica por subcategoria, 179 atributos distintos) |
| Atributos verdadeiros que aparecem no anúncio | todos | 83% (o resto deve virar `nao_informado`, não um chute) |
| Categoria do vendedor correta | ~70% | 51% (53% no golden) |
| Ruídos | 6 tipos | 33 tipos (título só com a linha comercial, título sem o nome do produto, título em inglês, unidade misturada, atributo conflitante, descrição de outro produto, marca com erro, EAN inválido...) |
| Vendedores | sem perfil | 400 com perfil (bom, médio, ruim), que muda a quantidade de erros |
| Duplicados e variações | não | 3,7% duplicados (outro vendedor, mesmo produto) e 8% em famílias de variação (cor, voltagem, tamanho) |
| Itens novos | não | 10.000, com marcas novas, vendedores novos, duplicados, produtos fora da taxonomia e cadastros inválidos |
| Buscas | 25 | 900, com intenção (subcategoria, filtros e marca) e relevância graduada |

O número de opções em cada nível é no máximo 16 (departamentos), 6 (categorias por departamento) e 10 (subcategorias por categoria). Isso respeita o limite recomendado para o Laya (menos de 20 opções por pergunta) se a classificação for em cascata.

### Distribuição

- Dificuldade: fácil 30%, médio 48%, difícil 22%.
- Tipo de item: produto 81,5%, consumível 8,1%, acessório 7,8%, kit 2,6%.
- Itens novos: normais 58,9%, duplicados do catálogo 14,9%, marca nova 8,8%, fora da taxonomia 7,7%, vendedor novo 7,6%, inválidos 2,1%.
- Buscas: atributo 366, cabeça 193, marca 130, sinônimo 87, erro de digitação 77, armadilhas 45, inglês 2.

## Linhas de base já medidas

**Categoria do vendedor** (golden, `node avaliacao/avaliar.mjs saneamento --baseline-vendedor`): 53% dos produtos com a subcategoria certa. Quando a categoria do vendedor mapeia para alguma subcategoria (72% dos produtos), ela acerta 74%.

**Busca atual por palavra** (`node avaliacao/avaliar.mjs busca --baseline-texto`):

| Tipo de consulta | Precisão nos 10 primeiros | Precisão da lista inteira | Cobertura | Sem resultado |
|---|---|---|---|---|
| Todas (900) | 83,1% | 77,6% | 51,5% | 6,2% |
| Armadilhas ("fogão", "carro", "capa") | 65,6% | 53,3% | 68,4% | 0% |
| Com atributo ("fogão 5 bocas inox") | 82,8% | 78% | 37,4% | 1,1% |
| Erro de digitação ("geladera") | 28% | 28% | 0,1% | 66,2% |
| Sinônimo ("refrigerador") | 96% | 90% | 22,9% | 0% |

A ordenação dessa linha de base põe primeiro quem tem todas as palavras no título, então a precisão nos 10 primeiros é generosa. O problema aparece na lista inteira, na cobertura e nas consultas com erro de digitação.

Exemplo: a busca por palavra "fogão" devolve 8.594 produtos, e só 9,7% são fogões.

**Navegação atual por categoria e filtros** (`node avaliacao/avaliar.mjs busca --baseline-filtros`): o cliente clica na categoria do vendedor e, em cada filtro, na faceta crua mais comum ("Bocas") e no valor mais comum que serve ("5bocas"). Precisão nos 10 primeiros 75,7%, cobertura 31,7%; nas consultas com filtro, cobertura de 10,6% e 7,1% sem resultado. O mesmo cálculo sobre um catálogo organizado: `--catalogo-previsto previsoes_saneamento.jsonl`.

**Classificador simples treinado no catálogo** (Naive Bayes com TF-IDF de título, atributos e descrição, no golden):

| Exemplos rotulados | Acerto da subcategoria |
|---|---|
| 200 | 35,6% |
| 2.000 | 91,5% |
| 5.000 | 96,5% |
| 20.000 | 97,9% |

Esse é o patamar que qualquer modelo com ajuste fino precisa superar. Atenção: os títulos sintéticos são mais regulares que os reais, então esses números são otimistas. A comparação entre modelos treinados com os mesmos exemplos continua justa.

## Como avaliar

```bash
node avaliacao/avaliar.mjs saneamento --pred minhas_previsoes.jsonl --ids golden
node avaliacao/avaliar.mjs novos --pred previsoes_novos.jsonl
node avaliacao/avaliar.mjs busca --pred previsoes_busca.jsonl
# --json para sair em JSON
```

Os formatos de previsão estão no topo de `avaliacao/avaliar.mjs`. Métricas principais:

- **Saneamento:** % aplicados, acerto da subcategoria (e da categoria e do departamento) entre os aplicados, **pioras** (o vendedor estava certo e o modelo trocou para errado), melhoras, corretos no catálogo, acerto dos atributos inferíveis, % de atributos inventados (valor dado quando o anúncio não tinha a informação), acerto da marca, tudo por dificuldade.
- **Novos:** precisão e cobertura para fora da taxonomia, duplicados e inválidos; acerto da subcategoria dos itens normais; acerto por tipo de item novo.
- **Busca:** no modo de intenção, subcategorias exatas, filtros e marca; no modo de lista, precisão nos 10 primeiros, NDCG@10, precisão da lista inteira, cobertura e % sem resultado.

## Regras

- O agente nunca lê `gabarito`. O treino usa só os ids de `treino_*` em `splits.json`, que simulam rótulos feitos pelo time.
- Duplicados e variações de um mesmo produto estão sempre no mesmo split, então não há vazamento entre treino e avaliação.
- Os itens fora da taxonomia só aparecem em `novos.jsonl`. O certo é sinalizar, não forçar uma subcategoria.

## Rodada 8 (`--rodada8`)

```bash
node gerador/gerar-catalogo-v2.mjs --rodada8 --semente 20261013 --ruido 1.5 --saida dados-r8
```
**Sem `--rodada8` a saída é idêntica à da rodada 7, byte a byte** (a conferência é a primeira célula do notebook: gera a base original, semente 20261008, e compara o sha256 de `produtos.jsonl`, `splits.json`, `taxonomia.json`, `novos.jsonl` e `buscas.jsonl`). A única mudança de código fora da opção foi extrair o item "fora da taxonomia" para uma função (`criarFora`), com o mesmo código e a mesma ordem de sorteios. O código novo está em `gerador/rodada8.mjs`, com um gerador de números próprio (`rnd8`) para os ruídos pós-processados.

**Subcategorias novas (9; a taxonomia vai de 205 para 214, dentro dos limites da cascata):**
`churrasqueiras_carvao` (Camping), `fogoes_lenha` (Fogões e fornos), `panelas_pressao` (Utilidades de cozinha), `escovas_dentes_manuais` e `escovas_dentes_eletricas` (Aparelhos de beleza), `ferramentas_manuais_avulsas` (Ferramentas manuais), `bicicletas_eletricas` (Ciclismo), `lavadoras_auto` (Acessórios automotivos) e `caixas_termicas_cozinha` (Utilidades de cozinha). As cinco não elétricas (e `bicicletas`) **não têm voltagem na ficha**; `confunde_com` aponta para a vizinha elétrica e de volta. As duas últimas existem só para a ambiguidade.

**Arquivos novos da base:**

| Arquivo | O que é | Quem lê |
|---|---|---|
| `arvore_legada.json` | ~1.700 nós `{id, nome, caminho, pai}`, **sem gabarito** | o agente |
| `arvore_gabarito.json` | por nó: `tipo` (`categoria`/`duplicado`/`atributo`/`lixo`), `destino`, `atributo {chave, valor}`, `estrutural`, `vazio` | só o avaliador (e o treino, nos nós de treino) |
| `arvore_splits.json` | nós de treino (70%) e de teste (30%); categoria e duplicados da mesma subcategoria ficam inteiros do mesmo lado | os dois |
| `fora_treino.jsonl` | itens fora da taxonomia dos tipos do **grupo de treino** (`split`: treino ou validacao) | treino do "nenhuma destas" e calibração |

**Árvore legada.** Um nó por subcategoria (às vezes com outro nome); duplicados em 2 a 4 nós ("Fogões", "Fogao", "Fogões e Fornos > Fogão de Piso", em ramos de outros departamentos, em "Antigos", em "Promoções"); valores de atributo como nós filhos ("Vestidos > Vestidos verde"); lixo ("Ofertas", "Black Friday 2023", "Outros", alguns sem produto); parte dos caminhos com 5 ou 6 níveis. Cada produto ganha `categoria_legada` (id do nó) e, no gabarito, `atributo_do_no_legado` e `categoria_legada_status` (`certo`, `atributo`, `errado` ou `lixo`: ~12% em nó errado ou em lixo).

**Ruídos de atributo (F2).** `gabarito.erros_de_atributo: [{chave, tipo, valor_informado, valor_certo}]`:
`nao_se_aplica` (~40% dos produtos das não elétricas trazem voltagem; metade também no título; `valor_certo` = null), `contradiz_anuncio` (~5%: o título diz o valor certo, o campo do vendedor diz outro), `fora_do_normal` (~1%: valor numérico fora da faixa da ficha ou unidade trocada, ex. peso em gramas no campo de quilos, fogão de 12 bocas), `sem_pista` (os `atributos_errados_pelo_vendedor` que já existiam, agora medidos). Controles: elétricos vizinhos com voltagem certa levam `controle_voltagem: true`.

**Ambiguidade.** Três pares com duas casas legítimas: lavadora de alta pressão (`lavadoras_alta_pressao` × `lavadoras_auto`), aspirador portátil (`aspiradores` × `aspiradores_auto`) e caixa térmica (`coolers` × `caixas_termicas_cozinha`). ~30% dos produtos de cada lado ganham no título a dica do outro uso e `gabarito.subcategorias_aceitas: [principal, secundária]`; `taxonomia.sobreposicoes` publica os pares e a regra.
**Regra do principal (regra de negócio, por função do produto):** a principal é a subcategoria da função declarada: o que o produto foi feito para fazer. Lavadora de alta pressão: Ferramentas quando o uso geral é calçada, quintal e parede; Automotivo quando o anúncio é para lavar veículos. Aspirador portátil: Eletroportáteis quando é de tomada e de uso doméstico; Automotivo quando é de 12 V para o veículo. Caixa térmica: Camping quando é cooler de lazer; Utilidades quando é para alimentos e marmita. No gabarito o produto mantém em `subcategoria` a da sua função e a outra entra em `subcategorias_aceitas`.

**Buscas novas** (`buscas.jsonl`): `f2` ("fogão bivolt", "fogão 220v", "churrasqueira bivolt", "churrasqueira a carvão", "fogão a lenha", "furadeira 220v"; os não elétricos entram em `armadilhas`), `sem_resposta` (`n_relevantes` 0: "xptoz", "trator agrícola", "geladera de praia nuclear"...), `com_modelo` ("capinha iphone 15", "película galaxy s23", "cartucho hp 664"...; relevante só o que bate o modelo, que vem em `filtros.compatibilidade` e em `modelo`). "Galaxy S23" e "HP 664" foram acrescentados às listas de modelos, para existirem produtos deles.

**Itens novos** (`novos.jsonl`, só com `--rodada8`):
- *Duplicados mais realistas* (~6% dos novos): apontam para um produto que já tem cópias no catálogo; `duplicado_de` é o original do grupo; `pista_duplicado` = `ean_igual`, `sem_ean`, `titulo_reescrito` ou `marca_diferente`.
- *Variações* (~5%): `tipo_novo: variacao_do_catalogo`, `variacao_de`, `eixo_variacao`; **não** são duplicados (o valor do eixo vai no título).
- *Fora da taxonomia sem vazamento:* os oito tipos se dividem em dois grupos (`GRUPOS_FORA` em `rodada8.mjs`). Os itens novos do teste são **só** dos tipos do grupo de teste (`gabarito.grupo_fora: teste`); os tipos do grupo de treino ficam em `fora_treino.jsonl` (treino e validação). O agente tem que reconhecer um tipo que nunca viu.
- *Inválidos limítrofes* (~1%, `tipo_novo: invalido_limitrofe`, `invalido: false`): "Kit 3 panos", "Adesivo R$ 1,00", "Pilha AA", "iPhone 13 128GB", marca desconhecida.

**Avaliador.**
```bash
node avaliacao/avaliar.mjs arvore     --pred nos.jsonl --dados dados-r8 [--ids teste|treino|todos]
node avaliacao/avaliar.mjs arvore     --baseline-legado-nome --dados dados-r8     # mapeia o nó pelo nome (linha de base realista)
node avaliacao/avaliar.mjs arvore     --baseline-legado      --dados dados-r8     # mapeamento perfeito (teto, usa o gabarito dos nós)
node avaliacao/avaliar.mjs saneamento --baseline-legado-nome --ids todos --dados dados-r8   # produto herda o destino do nó legado
node avaliacao/avaliar.mjs grupos     --pred grupos.jsonl --dados dados-r8        # duplicados e variações do catálogo existente
```
O `saneamento` ganha o bloco `rodada8`: `erros_de_atributo` por tipo (corrigidos, neutralizados, tirados do filtro, passaram, trocados por outro valor, inventados), `estragados` (atributos certos que o agente trocou ou removeu), `no_filtro_indevido` (não elétricos com voltagem na previsão), `controles_eletricos`, `atributos_do_no_legado` e `ambiguidade` (principal certa e precisão da secundária). O `novos` ganha o bloco `rodada8` (duplicados oficial e por grupo, erros por motivo, por pista, variações, fora da taxonomia com departamento provável e nome equivalente por similaridade de palavras, mais uma amostra para conferir à mão, e limítrofes barrados). A `busca` ganha os grupos `f2`, `sem_resposta` (vazia ou com `aviso: nenhum_resultado`; fica fora de `todas`), `com_modelo` e `corrigidas`. Em bases sem `--rodada8` nada disso aparece e a saída não muda.
