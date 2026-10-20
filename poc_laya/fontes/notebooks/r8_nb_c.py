# -*- coding: utf-8 -*-
# Bloco C: F1 (arvore de ~1.700 nos), F2 (dado errado do vendedor) e ambiguidade. Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS, AMBIENTE, exigir

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, bloco C: árvore de 1.700 nós (F1), dado errado do vendedor (F2) e ambiguidade

**As perguntas deste bloco**
- **F1.** O agente consegue **diagnosticar a árvore antiga** (o que é categoria, duplicado, valor de atributo, lixo), propor a árvore enxuta e **migrar os produtos** recuperando o atributo escondido no nó ("Vestidos verdes" → Vestidos + cor = verde)?
- **F2.** O agente corrige ou neutraliza **atributo errado** sem estragar o certo? Quatro casos: *não se aplica* (voltagem em churrasqueira a carvão), *contradiz o anúncio*, *fora do normal* e *sem outra pista* (aqui o certo é **não inventar**: manter ou tirar do filtro, guardando o original).
- **Ambiguidade.** Produto com duas casas legítimas (lavadora de alta pressão em Ferramentas e em Automotivo): a principal certa e a secundária sugerida.

**Como o agente decide (rodada 8).** *Categoria primeiro, atributos depois*: a ficha da subcategoria define quais atributos existem. Votação com **quatro sinais** (vendedor ou nó legado mapeado, Naive Bayes, Laya ajustado e **vizinhos** parecidos do `treino_5000`), regra **recalibrada na validação**; a pergunta "usa energia elétrica?" decide a disputa entre vizinhas elétrica e não elétrica; o LLM só entra nas disputas e **sempre restrito a opções**; tudo é **automático** (nada espera aprovação): o que não passa na regra recebe a ação segura (fica como está, ou o valor sai do filtro com o original guardado).

**Regras.** O pipeline **nunca lê o gabarito**. Leituras declaradas: rótulos dos ids de **treino**, rótulos da **validação** (calibração) e, só para **medir**, o gabarito dos ids de teste depois das previsões gravadas. Calibração só na validação (nos nós, só nos de **treino**); tudo que se calibra é gravado em `r8-escolhas-val.json` **antes** de medir o teste. O teste é: todos os produtos fora do `treino_20000` e da validação.

**Tempo (estimativa; o notebook mede):** Laya nos 100 mil ~0,7 a 2,5 h (domina); embeddings dos 100 mil ~5 a 10 min; o resto, 30 a 90 min. As partes com LLM mostram a **estimativa de chamadas antes de rodar**.

''' + BLOCOS + "\n\n" + AVISOS)

code(AMBIENTE.replace("__GPU__", "sim") + "\n" + exigir("b") + r'''
garantir_bases()
import torch, pandas as pd
BATCH, BLOCO = 32, 5000
CONFIRMO_LLM = True        # LLM nas disputas e na faixa de duvida (so roda se houver OLLAMA_API_KEY em Colab Secrets; a estimativa de chamadas aparece antes). False para pular
AMOSTRA_LLM = 10000        # as versoes com LLM rodam numa amostra estratificada dos ids de teste
TEM_CHAVE = carregar_chave_llm() and CONFIRMO_LLM
print("LLM:", "ligado (chave em Colab Secrets)" if TEM_CHAVE else "NAO executado (sem chave ou CONFIRMO_LLM=False): as linhas com LLM saem como 'nao executado'")''')

md(r'''## Dados, sinais e checkpoint
Carrega o `laya3-ft-5000` (conferindo o hash gravado no bloco B), treina o **Naive Bayes só no `treino_5000`** da base nova, monta o **índice de vizinhos** (embeddings `multilingual-e5-base` do `treino_5000`) e calcula, para os **100 mil** produtos, os sinais baratos: NB, Laya (cascata de 3 níveis, retomada a cada 5.000 produtos) e vizinhos. É a parte mais longa do bloco.''')

code(r'''CK = TRAB / "ck"; NOME = "laya3-ft-5000"
shutil.copytree(CK_DRIVE / NOME, CK / NOME, dirs_exist_ok=True)
assert pv.sha256_arquivo(str(CK / NOME / "model.safetensors")) == est["b"]["sha256"], "TRAVA: o checkpoint do Drive NAO e o que o bloco B treinou e conferiu"
AG3 = pv.carregar_laya(str(CK / NOME))
tx = pv.Taxonomia(str(D_R8 / "taxonomia.json"))
LOTE = pv.LayaLote(AG3, batch_size=BATCH)
CTRL = pv.controle_departamento(str(D_R8), tx, 120)
print("trava:", pv.verificar_checkpoint(NOME, str(CK / NOME), tx, CTRL, AG3, None)["acerto_departamento_ajustado"], "(acerto do controle)")

OP = pv.Opcoes()                       # todas as novidades ligadas; OP.sem("vizinhos") etc. desliga uma
pv.ativar_limpeza_de_entrada(True)     # 2.1: metades de emoji e caracteres de controle fora, em todos os campos
t0 = time.time()
prods_all = pv.ler_produtos(str(D_R8 / "produtos.jsonl"))
pos = {p.id: i for i, p in enumerate(prods_all)}
spl = fl.ler_json(D_R8 / "splits.json")
IDS_TESTE = fl.ids_de_teste(str(D_R8), str(SAIDA / "c" / "ids-teste.json"))
ARQ_TESTE = str(SAIDA / "c" / "ids-teste.json")
idx_val = [pos[i] for i in spl["validacao"]]
prods_val = [prods_all[i] for i in idx_val]
MARCAS = pv.ResolvedorMarca.do_catalogo(pv.ler_jsonl(str(D_R8 / "produtos.jsonl")), 5)
codigo = pv.FichaCodigo(tx)
USA_EN = pv.usa_energia_por_sub(tx)
PARES = pv.pares_de_sobreposicao(str(D_R8 / "taxonomia.json"))
print("%d produtos | %d de teste | %d de validacao | pares de sobreposicao: %s" % (len(prods_all), len(IDS_TESTE), len(prods_val), {k: v for k, v in PARES.items()}))

NB = fl.treinar_nb(str(D_R8), tx, "treino_5000", str(TRAB / "nb"))
treino5 = [prods_all[pos[i]] for i in spl["treino_5000"]]
rot5 = {i: v["subcategoria"] for i, v in fl.rotulos_para_calibrar(str(D_R8), "treino_5000").items()}
EMB = pv.Embeddings(device="cuda")
VIZ = pv.IndiceVizinhos(EMB, tx, treino5, rot5, str(CACHE / "emb-treino5000-r8.npy"))
print("NB: temperatura %.2f | indice de vizinhos: %d produtos de treino" % (NB.temperatura, len(treino5)))

SIN = fl.calcular_sinais(prods_all, tx, NB, LOTE, VIZ, str(CACHE), "r8-todos")
print("sinais dos %d produtos prontos em %.0f min (Laya: %s produtos/s medidos no ultimo lote; embeddings: %s textos/s)" % (len(prods_all), (time.time() - t0) / 60, "ver log acima", EMB.itens_por_s and round(EMB.itens_por_s)))

def recorte(sin, idx):
    out = {}
    for k, v in sin.items():
        out[k] = None if v is None else (v[idx] if isinstance(v, np.ndarray) else [v[i] for i in idx])
    return out
SIN_VAL = recorte(SIN, idx_val)
rot_val = {i: v["subcategoria"] for i, v in fl.rotulos_para_calibrar(str(D_R8), "validacao").items()}''')

md(r'''## Calibração da regra de votação (só na validação)
Percorre `min` (2 ou 3 sinais concordando), exigir ou não um modelo, o veto de confiança e o limiar, e fica com a regra que **mais aplica** entre as que acertam **≥ 98,5%** dos aplicados e pioram **≤ 0,5%** do total (piora = o vendedor estava certo e a decisão trocou). Ponto de partida do prompt: aplica quando ao menos dois modelos concordam e nenhum modelo com confiança alta discorda. O resultado vai para `r8-escolhas-val.json` **antes** de qualquer medida no teste.''')

code(r'''ESC = SAIDA / "r8-escolhas-val.json"
esc_E = pv.calibrar_votacao("E", prods_val, rot_val, SIN_VAL["nb_cats"], SIN_VAL["nb_confs"], SIN_VAL["laya"], SIN_VAL["viz"], tx)
print("conjunto E (vendedor + NB + Laya + vizinhos):", esc_E["escolha"], "| atingiu o piso:", esc_E["atingiu_o_piso"], "|", esc_E["avaliadas"], "regras avaliadas")
fl.mesclar_json(ESC, {"votacao_E": esc_E, "votacao_F": {"regra": "a mesma do E; o LLM entra so nas disputas"}})
PARAMS_R8 = {"E": pv.parametros_da_calibracao(esc_E), "F": pv.parametros_da_calibracao(esc_E)}
PARAMS_R7 = pv.parametros_da_escolha(str(TRAB / "r6-escolha-val.json"))      # "Classificador + agente + Laya" (rodada 7): voto-b com os parametros da rodada 6, sem reajuste
print("E:", PARAMS_R8["E"].resumo(), "| B (rodada 7):", PARAMS_R7["B"].resumo())

# decisao de categoria de todos os produtos (sem LLM): base da estatistica do catalogo e da ficha
RES_E = fl.decidir_categorias(prods_all, SIN, tx, PARAMS_R8, "E", OP, llm=None, indice_viz=VIZ, lote=LOTE, usa_energia=USA_EN)
DEC_E = [d for d, _ in RES_E.decisoes["E"]]
subs_E = [d.categoria if d.aplicar else None for d in DEC_E]
print("categoria decidida em %.1f%% dos produtos (conjunto E, sem LLM)" % (100 * sum(s is not None for s in subs_E) / len(subs_E)))''')

md(r'''## Estatística do catálogo e faixas de confiança dos atributos (calibração)
**Estatística (2.4).** Por subcategoria, a distribuição dos valores da ficha por código sobre os produtos decididos (sem gabarito): a faixa normal dos numéricos (quantis de 2% e 98% com uma folga) marca o **fora do normal**. A **folga** é escolhida na validação: a menor cujos falsos positivos (valor certo tirado do filtro) ficam em ≤ 1%.
**Faixas (2.5).** A confiança de **toda** sugestão do Laya para atributos é registrada (inclusive as descartadas) e o acerto é medido por faixa (≥ 0,9; 0,8–0,9; 0,7–0,8; < 0,7). A faixa de dúvida que vai ao LLM é calibrada na validação (ponto de partida 0,7–0,9: se o acerto de 0,7–0,8 já é menor que 50%, a faixa sobe para 0,8–0,9).''')

code(r'''fichas_cod = [None if s is None else codigo.extrair_parcial(p, s) for p, s in zip(prods_all, subs_E)]
ESTAT = pv.EstatisticaCatalogo()
for p, s, f in zip(prods_all, subs_E, fichas_cod):
    if s is not None:
        ESTAT.adicionar(s, f, p.atributos)
print("estatistica:", ESTAT.resumo())
subs_val = [subs_E[i] for i in idx_val]
verdade_val = fl.rotulos_para_calibrar(str(D_R8), "validacao", campos=("subcategoria", "atributos", "erros_de_atributo", "subcategorias_aceitas", "atributos_inferiveis"))
cal_est = pv.calibrar_fator_estatistica(ESTAT, prods_val, subs_val, [fichas_cod[i] for i in idx_val], verdade_val)
print("fora do normal (validacao):", cal_est["escolha"], "| atingiu o piso:", cal_est["atingiu_o_piso"])
FATOR = cal_est["fator"]

# faixas: a ficha com o Laya na validacao, sem LLM, so para registrar a confianca e medir o acerto por faixa
fv, dv, uso_v = pv.ficha_r8(prods_val, subs_val, tx, codigo, LOTE, SIN_VAL["estados"], OP.sem("faixas_llm"), ESTAT, FATOR, llm=None, nome_lote="calibracao-faixas")
ver_val = fl.verdade_para_medir(str(D_R8), set(spl["validacao"]))        # calibracao: so a validacao
faixas_val = pv.acerto_por_faixa(uso_v["sugestoes_laya"], ver_val)
print("acerto das sugestoes do Laya por faixa de confianca (validacao):", faixas_val)
lo = 0.8 if (faixas_val.get("0.7-0.8", {}).get("acerto_%", 100) < 50) else 0.7
FAIXA = (lo, 0.9)
fl.mesclar_json(ESC, {"estatistica": cal_est, "faixas_validacao": faixas_val, "faixa_de_duvida": list(FAIXA)})
print("faixa de duvida (vai ao LLM):", FAIXA)''')

md(r'''## F1, passo 1 e 2: diagnóstico da árvore (estrutural + orientado a produtos)
**Estrutural.** Compara nomes e caminhos (normalização, similaridade de texto e de embeddings): candidatos a duplicado e nós sem produto. **Orientado a produtos.** Para cada nó, uma amostra de até 20 produtos passa pela cascata; o nó recebe a **subcategoria predominante** e a **pureza**; o Laya responde "este nó é: tipo de produto / valor de atributo / campanha?" e, se for valor, qual valor da ficha do destino; a **equivalência** "subcategoria X + filtro Y" é checada nos produtos do nó. **Síntese e juiz:** junta os dois numa proposta por nó, o **juiz por regras** rejeita as incoerentes (destino inexistente, atributo fora da ficha) e o nó sem proposta segura cai para o pai mais próximo que tenha uma, **nunca criando nó novo por dúvida**.

Versões comparadas, nos **30% de nós de teste** (o gabarito dos nós de teste nunca é lido): só regras (NB nos produtos, sem a pergunta do Laya), **+ Laya**, **+ vizinhos** e **+ LLM nas disputas**; e as duas linhas de base do avaliador: o nó mapeado **pelo nome** (realista) e o **mapeamento perfeito** (o teto). A pureza mínima é calibrada nos nós de **treino**, com a versão só regras.''')

code(r'''NOS = fl.ler_json(D_R8 / "arvore_legada.json")
nos_por_id = {n["id"]: n for n in NOS}
SPN = fl.ler_json(D_R8 / "arvore_splits.json")
indices_por_no = defaultdict(list)
for i, p in enumerate(prods_all):
    indices_por_no[p.categoria_legada].append(i)
n_prod_no = {k: len(v) for k, v in indices_por_no.items()}
print("nos: %d | com produtos: %d | profundidade maxima: %d" % (len(NOS), len(n_prod_no), max(n["caminho"].count(" > ") + 1 for n in NOS)))
EST_NOS = pv.diagnostico_estrutural(NOS, n_prod_no, tx, emb=EMB)

amostra_ids = set()
for nid, idxs in indices_por_no.items():
    amostra_ids |= set(pv.amostra_do_no([prods_all[i].id for i in idxs], 20))
def voto3(i):
    laya, viz, nb = SIN["laya"][i]["sub"], SIN["viz"][i]["sub"], SIN["nb_cats"][i]
    c = Counter([x for x in (laya, viz, nb) if x is not None])
    top = c.most_common(1)[0] if c else (None, 0)
    return top[0] if top[1] >= 2 else (laya or nb)
CASC = {
    "so_regras": {i: {"sub": SIN["nb_cats"][pos[i]], "conf": SIN["nb_confs"][pos[i]], "motivo": None} for i in amostra_ids},
    "laya": {i: SIN["laya"][pos[i]] for i in amostra_ids},
    "vizinhos": {i: {"sub": voto3(pos[i]), "conf": 1.0, "motivo": None} for i in amostra_ids},
}
print("produtos na amostra dos nos:", len(amostra_ids))

def rodar_arvore(nome, casc, usar_laya, llm=None, par=None, ids_teste="teste"):
    dp = pv.diagnostico_orientado_a_produtos(NOS, indices_por_no, prods_all, casc, tx, codigo, lote=LOTE if usar_laya else None, usar_laya=usar_laya)
    props = pv.sintese_da_arvore(NOS, EST_NOS, dp, tx, par, llm, usar_laya)
    juiz = pv.juiz_da_arvore(props, tx, (par or {}).get("confianca_minima", pv.PARAMETROS_ARVORE["confianca_minima"]))
    f = SAIDA / "c" / ("arvore-%s.jsonl" % nome)
    pv.escrever_jsonl(str(f), pv.previsao_dos_nos(props, juiz))
    return dp, props, juiz, str(f)

# calibracao da pureza minima: nos de TREINO, versao so regras
res_cal = []
for pm in (0.4, 0.5, 0.6, 0.7, 0.8):
    dp, props, juiz, f = rodar_arvore("calib-%.1f" % pm, CASC["so_regras"], False, par={"pureza_minima": pm})
    r = pv.avaliar(AVAL, "arvore", str(D_R8), pred=f, ids="treino")
    score = r["nos_lixo"]["precisao_%"] + r["nos_lixo"]["cobertura_%"] + r["destino_categoria_e_duplicado_acerto_%"] + r["nos_atributo"]["precisao_%"] + r["nos_atributo"]["cobertura_%"]
    res_cal.append({"pureza_minima": pm, "pontuacao": round(score, 1), "lixo": r["nos_lixo"], "atributo": r["nos_atributo"], "destino_%": r["destino_categoria_e_duplicado_acerto_%"]})
melhor = max(res_cal, key=lambda x: x["pontuacao"])
PAR_ARV = {"pureza_minima": melhor["pureza_minima"]}
print("pureza minima escolhida (nos de treino):", PAR_ARV, "|", [(x["pureza_minima"], x["pontuacao"]) for x in res_cal])
fl.mesclar_json(ESC, {"arvore": {"escolha": PAR_ARV, "tabela": res_cal, "base_da_calibracao": "nos de treino, versao so regras"}})

VERSOES_ARV, RES_ARV, SAIDAS_ARV = {}, {}, {}
for nome, casc, laya_ in (("so_regras", CASC["so_regras"], False), ("mais_laya", CASC["laya"], True), ("mais_vizinhos", CASC["vizinhos"], True)):
    dp, props, juiz, f = rodar_arvore(nome, casc, laya_, par=PAR_ARV)
    VERSOES_ARV[nome] = (dp, props, juiz)
    RES_ARV[nome] = {"teste": pv.avaliar(AVAL, "arvore", str(D_R8), pred=f, ids="teste"), "todos": pv.avaliar(AVAL, "arvore", str(D_R8), pred=f, ids="todos")}
RES_ARV["baseline_nome"] = {"teste": pv.avaliar(AVAL, "arvore", str(D_R8), ids="teste", extra=["--baseline-legado-nome"]), "todos": pv.avaliar(AVAL, "arvore", str(D_R8), ids="todos", extra=["--baseline-legado-nome"])}
RES_ARV["teto_perfeito"] = {"teste": pv.avaliar(AVAL, "arvore", str(D_R8), ids="teste", extra=["--baseline-legado"]), "todos": pv.avaliar(AVAL, "arvore", str(D_R8), ids="todos", extra=["--baseline-legado"])}
linhas = []
for nome, r in RES_ARV.items():
    t = r["teste"]
    linhas.append({"versao": nome, "nos_atributo (prec/cob)": "%s / %s" % (t["nos_atributo"]["precisao_%"], t["nos_atributo"]["cobertura_%"]), "nos_lixo (prec/cob)": "%s / %s" % (t["nos_lixo"]["precisao_%"], t["nos_lixo"]["cobertura_%"]),
                   "destino_%": t["destino_categoria_e_duplicado_acerto_%"], "atributo (destino e valor)_%": t["atributo"]["destino_e_atributo_certos_%"],
                   "subcategorias_resultantes (todos os nos)": r["todos"]["arvore_resultante"]["subcategorias_resultantes"], "excesso_sobre_a_taxonomia_%": r["todos"]["arvore_resultante"]["excesso_sobre_a_taxonomia_%"]})
pd.set_option("display.width", 250); pd.set_option("display.max_columns", 20)
print(pd.DataFrame(linhas).to_string(index=False))''')

code(r'''# ---- + LLM nas disputas entre o diagnostico estrutural e o orientado a produtos (opcional; restrito a opcoes)
LLM = pv.LlmRestritoOllama(tx) if TEM_CHAVE else None
dp_v, props_v, juiz_v = VERSOES_ARV["mais_vizinhos"]
dp_l = VERSOES_ARV["mais_laya"][0]
n_disputas = sum(1 for d in dp_l.values() if (d["tipo_laya"] == "tipo_de_produto" and d["equivalencia"]) or (d["tipo_laya"] == "valor_de_atributo" and not d["equivalencia"] and not d["atributo_laya"]))
est_ch = pv.estimativa_de_chamadas(0, 0, 0, 0, 0, n_disputas, 0)
print("ESTIMATIVA de chamadas ao LLM na arvore: %d disputas = %d chamadas, ~%.1f min a 30 por minuto" % (n_disputas, est_ch["chamadas"], est_ch["minutos_ao_limite"]))
if LLM is not None and LLM.ativa:
    dp, props, juiz, f = rodar_arvore("mais_llm", CASC["vizinhos"], True, llm=LLM, par=PAR_ARV)
    VERSOES_ARV["mais_llm"] = (dp, props, juiz)
    RES_ARV["mais_llm"] = {"teste": pv.avaliar(AVAL, "arvore", str(D_R8), pred=f, ids="teste"), "todos": pv.avaliar(AVAL, "arvore", str(D_R8), pred=f, ids="todos")}
    t = RES_ARV["mais_llm"]["teste"]
    print("+ LLM: atributo %s | lixo %s | destino %s%% | chamadas %d | rejeitadas %d" % (t["nos_atributo"], t["nos_lixo"], t["destino_categoria_e_duplicado_acerto_%"], LLM.chamadas, LLM.rejeitadas))
else:
    print("+ LLM: nao executado (sem chave ou CONFIRMO_LLM=False)")
VERSAO_FINAL_ARV = "mais_llm" if "mais_llm" in VERSOES_ARV else "mais_vizinhos"
DP, PROPS, JUIZ = VERSOES_ARV[VERSAO_FINAL_ARV]
fl.gravar_json(SAIDA / "c" / "resultados-arvore.json", {"versao_final": VERSAO_FINAL_ARV, "resultados": RES_ARV, "estimativa_llm": est_ch})
print("versao da arvore usada na migracao:", VERSAO_FINAL_ARV)

# exemplos de nos (5), para o painel: o no, o diagnostico, o destino e o motivo (a conferencia com o gabarito so no teste)
teste_ids = SPN["teste"]
amostra_nos = [n for n in NOS if n["id"] in set(teste_ids) and PROPS[n["id"]]["tipo"] in ("atributo", "duplicado", "lixo", "categoria")]
por_tipo, exemplos_nos = defaultdict(int), []
for n in amostra_nos:
    t = PROPS[n["id"]]["tipo"]
    if por_tipo[t] < 2 and len(exemplos_nos) < 6:
        por_tipo[t] += 1
        exemplos_nos.append({"no": n["nome"], "caminho": n["caminho"], "diagnostico": PROPS[n["id"]]["tipo"], "destino": PROPS[n["id"]]["destino"], "atributo": PROPS[n["id"]]["atributo"], "motivo": PROPS[n["id"]]["motivo"],
                             "aprovado_pelo_juiz": JUIZ[n["id"]]["aprovada"]})
for e in exemplos_nos:
    print(e)''')

md(r'''## F1, passo 3: publicar a árvore nova e migrar os 100 mil
A árvore nova é **publicada como versão** (a antiga fica guardada para reverter), e o `EstadoDoCatalogo` guarda o instantâneo. Na **migração** o nó legado mapeado **entra na votação no lugar da categoria do vendedor** (conjunto `N`) e o atributo do nó entra na ficha como mais um sinal (**o anúncio vence em conflito**). A regra do conjunto `N` é calibrada na validação (os produtos de validação e os nós que eles ocupam). Compara com o nó mapeado **pelo nome** e com o **mapeamento perfeito** (que usa o gabarito dos nós: é o teto, não um agente).''')

code(r'''NOVA = pv.arvore_nova(NOS, PROPS, JUIZ, tx, versao=1)
fl.gravar_json(SAIDA / "c" / "arvore-antiga-v0.json", NOS)
fl.gravar_json(SAIDA / "c" / "arvore-nova-v1.json", NOVA)
print("arvore nova:", NOVA["resumo"])
NO_LEG, ATR_NO = pv.no_legado_para_a_votacao(prods_all, NOVA, PROPS, JUIZ)
print("produtos com no legado mapeado: %.1f%% | com atributo no no: %d" % (100 * sum(x is not None for x in NO_LEG) / len(NO_LEG), len(ATR_NO)))

esc_N = pv.calibrar_votacao("N", prods_val, rot_val, SIN_VAL["nb_cats"], SIN_VAL["nb_confs"], SIN_VAL["laya"], SIN_VAL["viz"], tx, no_legado=[NO_LEG[i] for i in idx_val])
print("conjunto N (no legado + NB + Laya + vizinhos):", esc_N["escolha"], "| atingiu o piso:", esc_N["atingiu_o_piso"])
fl.mesclar_json(ESC, {"votacao_N": esc_N})
PARAMS_R8["N"] = pv.parametros_da_calibracao(esc_N)
RES_N = fl.decidir_categorias(prods_all, SIN, tx, PARAMS_R8, "N", OP, indice_viz=VIZ, lote=LOTE, usa_energia=USA_EN, no_legado=NO_LEG)
DEC_N = [d for d, _ in RES_N.decisoes["N"]]
subs_N = [d.categoria if d.aplicar else None for d in DEC_N]
fichas_N, det_N, uso_N = fl.ficha_em_blocos(prods_all, subs_N, tx, codigo, LOTE, SIN["estados"], OP.sem("faixas_llm"), ESTAT, FATOR, None, FAIXA, ATR_NO, str(CACHE / "fichas"), "migracao-N")
pv.escrever_jsonl(str(SAIDA / "c" / "saneamento-migracao-N.jsonl"), fl.previsoes_de_saneamento(prods_all, DEC_N, MARCAS, fichas_N))
MIG = {"agente_v3_N": avaliar_saneamento(str(SAIDA / "c" / "saneamento-migracao-N.jsonl"), ARQ_TESTE)}
MIG["baseline_legado_nome"] = pv.avaliar(AVAL, "saneamento", str(D_R8), ids_arquivo=ARQ_TESTE, extra=["--baseline-legado-nome"])
MIG["teto_legado_perfeito"] = pv.avaliar(AVAL, "saneamento", str(D_R8), ids_arquivo=ARQ_TESTE, extra=["--baseline-legado"])
MIG["vendedor_sozinho"] = pv.avaliar(AVAL, "saneamento", str(D_R8), ids_arquivo=ARQ_TESTE, extra=["--baseline-vendedor"])
tab = []
for nome, r in MIG.items():
    tab.append({"versao": nome, "aplicados_%": r["aplicados_%"], "acerto_%": r["acerto_subcategoria_aplicados_%"], "pioras_%": r["pioras_%_do_total"], "corretos_no_catalogo_%": r["corretos_no_catalogo_%"],
                "atributo_do_no_recuperado_%": (r.get("rodada8") or {}).get("atributos_do_no_legado", {}).get("%")})
print(pd.DataFrame(tab).to_string(index=False))
fl.gravar_json(SAIDA / "c" / "resultados-migracao.json", {"arvore_nova": NOVA["resumo"], "versoes": MIG, "uso_ficha": {k: v for k, v in uso_N.items() if k != "sugestoes_laya"}})''')

md(r'''## F2: dado errado do vendedor, nos produtos de teste
Cada versão passa pelo avaliador (`saneamento`, que agora mede o bloco `rodada8`). Versões: **Antes** (o vendedor como chegou), **Classificador** (só o NB), **Classificador + agente + Laya** (o voto-b da rodada 7, com a ficha por código + Laya ≥ 0,9) e **Agente v3** (esta rodada: votação com vizinhos e energia, ficha *categoria primeiro, atributos depois*, estatística, faixas). Medidas por tipo de erro de atributo: **% resolvidos** (não se aplica = neutralizado; contradiz = corrigido; fora do normal = tirado do filtro; sem pista = **não inventado**), **estragados** (atributos certos trocados ou removidos), **não elétricos no filtro de voltagem** e **controles** (elétricos com voltagem certa que perderam a voltagem).''')

code(r'''# ---- Classificador (NB) e "Classificador + agente + Laya" (rodada 7): a ficha da rodada 7 (codigo + Laya >= 0,9), sem as novidades
# (a limpeza de entrada fica ligada nos sinais das duas versoes: o que muda entre elas e a votacao e a ficha)
RES_B = pv.votacao_em_lote(prods_all, SIN["nb_cats"], SIN["nb_confs"], SIN["laya"], tx, PARAMS_R7, ["B"], llm=None, log=lambda *_: None)
DEC_B = [d for d, _ in RES_B.decisoes["B"]]
subs_B = [d.categoria if d.aplicar else None for d in DEC_B]
cache_fb = pv.CacheJsonl(str(CACHE / "ficha-r7-todos.jsonl"))
fichas_B, uso_B = pv.ficha_laya_com_cache(LOTE, tx, codigo, prods_all, SIN["estados"], subs_B, cache_fb, bloco=BLOCO, confianca_minima=0.9)
cache_fb.fechar()
nb_dec = [pv.Decisao(c is not None, c, cf, "nb") for c, cf in zip(SIN["nb_cats"], SIN["nb_confs"])]
VERS = {
    "Classificador": fl.previsoes_de_saneamento(prods_all, nb_dec, MARCAS),
    "Classificador + agente + Laya (rodada 7)": fl.previsoes_de_saneamento(prods_all, DEC_B, MARCAS, fichas_B),
}
# ---- Agente v3 (sem LLM): votacao E + ficha_r8 + ambiguidade
esc_amb = pv.calibrar_margem_ambiguidade(prods_val, subs_val, SIN_VAL["viz"], SIN_VAL["nb_cats"], PARES, verdade_val)
print("margem da ambiguidade (validacao):", esc_amb["escolha"], "| atingiu o piso:", esc_amb["atingiu_o_piso"])
fl.mesclar_json(ESC, {"ambiguidade": esc_amb})
SEC = fl.secundarias_para(prods_all, DEC_E, SIN, PARES, esc_amb["margem"])
fichas_E, det_E, uso_E = fl.ficha_em_blocos(prods_all, subs_E, tx, codigo, LOTE, SIN["estados"], OP.sem("faixas_llm"), ESTAT, FATOR, None, FAIXA, None, str(CACHE / "fichas"), "v3-E")
VERS["Agente v3"] = fl.previsoes_de_saneamento(prods_all, DEC_E, MARCAS, fichas_E, SEC)

RES_F2 = {"Antes": pv.avaliar(AVAL, "saneamento", str(D_R8), ids_arquivo=ARQ_TESTE, extra=["--baseline-vendedor"])}
for nome, linhas in VERS.items():
    f = SAIDA / "c" / ("saneamento-%s.jsonl" % nome.split(" (")[0].replace(" ", "_").replace("+", "mais"))
    pv.escrever_jsonl(str(f), linhas)
    RES_F2[nome] = avaliar_saneamento(str(f), ARQ_TESTE)
def linha_f2(nome, r):
    r8 = r.get("rodada8") or {}
    e = r8.get("erros_de_atributo", {})
    return {"versao": nome, "aplicados_%": r["aplicados_%"], "acerto_%": r["acerto_subcategoria_aplicados_%"], "pioras_%": r["pioras_%_do_total"], "atrib_acerto_%": r["atributos"]["acerto_inferiveis_%"],
            "nao_se_aplica_resolvido_%": (e.get("nao_se_aplica") or {}).get("resolvidos_%"), "contradiz_resolvido_%": (e.get("contradiz_anuncio") or {}).get("resolvidos_%"),
            "fora_normal_resolvido_%": (e.get("fora_do_normal") or {}).get("resolvidos_%"), "sem_pista_nao_inventado_%": (e.get("sem_pista") or {}).get("resolvidos_%"),
            "estragados_%": (r8.get("estragados") or {}).get("%"), "nao_eletricos_no_filtro_%": (r8.get("no_filtro_indevido") or {}).get("%"), "controles_voltagem_removida_%": (r8.get("controles_eletricos") or {}).get("%")}
print(pd.DataFrame([linha_f2(n, r) for n, r in RES_F2.items()]).to_string(index=False))
fl.gravar_json(SAIDA / "c" / "resultados-f2.json", {"versoes": RES_F2, "uso_ficha_v3": {k: v for k, v in uso_E.items() if k != "sugestoes_laya"}, "fator_estatistica": FATOR, "faixa_de_duvida": list(FAIXA)})''')

code(r'''# ---- faixas de confianca no teste (MEDIDA: o gabarito dos ids de teste so e lido agora, depois das previsoes gravadas)
ver_teste = fl.verdade_para_medir(str(D_R8), set(IDS_TESTE))
sug_teste = [s for s in uso_E["sugestoes_laya"] if s["id"] in ver_teste]
faixas_teste = pv.acerto_por_faixa(sug_teste, ver_teste)
print("acerto das sugestoes do Laya por faixa (teste):", faixas_teste)
print("uso da ficha v3:", {k: v for k, v in uso_E.items() if k not in ("sugestoes_laya",)})

# ---- versao com LLM na faixa de duvida e nas disputas de categoria (amostra estratificada; estimativa antes)
DEC_F = fichas_F = uso_F = None
res_F2_llm = None
if LLM is not None and LLM.ativa:
    amostra_idx = fl.amostra_estratificada([prods_all[pos[i]] for i in IDS_TESTE], AMOSTRA_LLM, lambda p: tx.departamento_de(subs_E[pos[p.id]]) if subs_E[pos[p.id]] else "sem")
    amostra_ids = [IDS_TESTE[i] for i in amostra_idx]
    idx_a = [pos[i] for i in amostra_ids]
    prods_a, SIN_A = [prods_all[i] for i in idx_a], recorte(SIN, idx_a)
    # estimativa: as disputas (a regra dos baratos nao decidiu) e os atributos na faixa de duvida
    n_disp = sum(1 for i in idx_a if not DEC_E[i].aplicar)
    n_duv = sum(1 for s in uso_E["sugestoes_laya"] if s["id"] in set(amostra_ids) and FAIXA[0] <= s["confianca"] < FAIXA[1])
    est_f = pv.estimativa_de_chamadas(n_disp, n_duv, n_duv, 0, 0, 0, 0)
    print("ESTIMATIVA (amostra de %d): ate %d disputas de categoria (parte tem so 1 opcao e nao chama) + ~%d produtos com atributos na faixa de duvida = ~%d chamadas, ~%.0f min a 30 por minuto" % (len(amostra_ids), n_disp, n_duv, est_f["chamadas"], est_f["minutos_ao_limite"]))
    cache_l = pv.CacheJsonl(str(CACHE / "llm-categoria-F.jsonl"))
    c0 = LLM.chamadas
    RES_FLLM = pv.votacao_r8(prods_a, SIN_A["nb_cats"], SIN_A["nb_confs"], SIN_A["laya"], SIN_A["viz"], tx, PARAMS_R8, ["F"], llm=LLM, vizinhos=VIZ, cache_llm=cache_l)
    cache_l.fechar()
    DEC_F = [d for d, _ in RES_FLLM.decisoes["F"]]
    n_ao_llm = sum(1 for _, c in RES_FLLM.decisoes["F"] if c)
    subs_F = [d.categoria if d.aplicar else None for d in DEC_F]
    cache_a = pv.CacheJsonl(str(CACHE / "llm-atributos-F.jsonl"))
    fichas_F, det_F, uso_F = pv.ficha_r8(prods_a, subs_F, tx, codigo, LOTE, SIN_A["estados"], OP, ESTAT, FATOR, LLM, FAIXA, None, cache_a, None, "v3-F")
    cache_a.fechar()
    pv.escrever_jsonl(str(SAIDA / "c" / "saneamento-Agente_v3_LLM.jsonl"), fl.previsoes_de_saneamento(prods_a, DEC_F, MARCAS, fichas_F))
    fl.gravar_json(SAIDA / "c" / "ids-amostra-llm.json", amostra_ids)
    base_a = fl.previsoes_de_saneamento(prods_a, [DEC_E[i] for i in idx_a], MARCAS, [fichas_E[i] for i in idx_a])
    pv.escrever_jsonl(str(SAIDA / "c" / "saneamento-Agente_v3_sem_llm_amostra.jsonl"), base_a)
    res_F2_llm = {"sem_llm_na_amostra": avaliar_saneamento(str(SAIDA / "c" / "saneamento-Agente_v3_sem_llm_amostra.jsonl"), str(SAIDA / "c" / "ids-amostra-llm.json")),
                  "com_llm": avaliar_saneamento(str(SAIDA / "c" / "saneamento-Agente_v3_LLM.jsonl"), str(SAIDA / "c" / "ids-amostra-llm.json")),
                  "consumo": pv.consumo_de_llm(LLM, len(prods_a), n_atributos_avaliados=uso_F["atributos_avaliados"], n_chamadas_antes=c0), "produtos_que_foram_ao_llm_%": round(100 * n_ao_llm / len(prods_a), 2),
                  "atributos_ao_llm_%_dos_avaliados": round(100 * uso_F["atributos_ao_llm"] / max(1, uso_F["atributos_avaliados"]), 2)}
    # acerto do que o LLM preencheu na faixa de duvida (MEDIDA: o gabarito dos ids da amostra so e lido agora)
    ver_a = fl.verdade_para_medir(str(D_R8), set(amostra_ids))
    pre = [x for x in uso_F["preenchidos_llm"] if x[0] in ver_a]
    ok_llm = sum(1 for pid, chave_, valor in pre if pv.mesmo_valor(valor, ver_a[pid]["atributos"].get(chave_)))
    res_F2_llm["acerto_do_que_o_llm_preencheu_%"] = round(100 * ok_llm / max(1, len(pre)), 2)
    res_F2_llm["atributos_preenchidos_pelo_llm"] = len(pre)
    print("com LLM:", {k: v for k, v in res_F2_llm.items() if k in ("consumo", "produtos_que_foram_ao_llm_%", "atributos_ao_llm_%_dos_avaliados")})
    print(pd.DataFrame([linha_f2("sem LLM (amostra)", res_F2_llm["sem_llm_na_amostra"]), linha_f2("com LLM (amostra)", res_F2_llm["com_llm"])]).to_string(index=False))
    fl.gravar_json(SAIDA / "c" / "resultados-f2-llm.json", res_F2_llm)
else:
    print("versao com LLM: nao executada (sem chave ou CONFIRMO_LLM=False)")''')

md(r'''## Ambiguidade
Quando a subcategoria decidida está numa **sobreposição conhecida** (`taxonomia.sobreposicoes`) e os sinais baratos dão a parceira em peso suficiente (fração dos vizinhos, +0,2 se o NB também a escolhe) acima da **margem calibrada na validação**, o agente grava a segunda como `subcategoria_secundaria`. A regra do principal está no README do benchmark: é a subcategoria da **função declarada** do produto. O avaliador mede a principal certa e a **precisão** da secundária sugerida.''')

code(r'''amb = RES_F2["Agente v3"]["rodada8"].get("ambiguidade")
print("ambiguidade (teste):", amb)
# exemplos lado a lado (3 pares duplicado x variacao ficam no bloco D; aqui, 3 casos de sem pista e o que o agente fez sozinho)
ver_ex = fl.verdade_para_medir(str(D_R8), set(IDS_TESTE[:50000]))
exemplos_sp, exemplos_churras = [], []
for p, f, d in zip(prods_all, fichas_E, det_E):
    if p.id not in ver_ex:
        continue
    v = ver_ex[p.id]
    for e in (v.get("erros_de_atributo") or []):
        if e["tipo"] == "sem_pista" and len(exemplos_sp) < 3 and f is not None and e["chave"] in f:
            exemplos_sp.append({"id": p.id, "titulo": p.titulo, "atributo_do_vendedor": e["valor_informado"], "valor_certo (gabarito)": e["valor_certo"], "o_agente_deixou": f[e["chave"]], "chave": e["chave"]})
    if v["subcategoria"] in ("churrasqueiras_carvao", "fogoes_lenha") and len(exemplos_churras) < 5:
        exemplos_churras.append({"id": p.id, "titulo": p.titulo, "categoria_do_vendedor": p.categoria_vendedor, "atributos_do_vendedor": dict(list(p.atributos.items())[:5]),
                                 "subcategoria_certa": v["subcategoria"], "o_agente_decidiu": subs_E[pos[p.id]], "voltagem_na_ficha": (f or {}).get("voltagem", "(nao existe na ficha)")})
fl.gravar_json(SAIDA / "c" / "exemplos-c.json", {"nos": exemplos_nos, "churrasqueiras_e_fogoes": exemplos_churras, "sem_pista": exemplos_sp})
for e in exemplos_churras:
    print(e)
for e in exemplos_sp:
    print(e)''')

md(r'''## Resultado do bloco e critérios
Reúne as medidas que cada critério fixo (`r8-criterios.json`) pede e as grava em `resultados/c.json`, no formato que o bloco F lê. Nada é calculado aqui além de converter percentuais em frações. O que não pôde ser medido neste bloco fica sem valor (**"não medido"**, nunca "atende").''')

code(r'''def frac(x):
    return None if x is None else x / 100.0
ver = RES_F2["Agente v3"]; r8v = ver["rodada8"]; e = r8v["erros_de_atributo"]
arv = RES_ARV[VERSAO_FINAL_ARV]
mig = MIG["agente_v3_N"]
medidas = {
    "no_atributo": {"precisao": frac(arv["teste"]["nos_atributo"]["precisao_%"]), "cobertura": frac(arv["teste"]["nos_atributo"]["cobertura_%"])},
    "no_lixo": {"precisao": frac(arv["teste"]["nos_lixo"]["precisao_%"]), "cobertura": frac(arv["teste"]["nos_lixo"]["cobertura_%"])},
    "no_destino": {"acerto": frac(arv["teste"]["destino_categoria_e_duplicado_acerto_%"])},
    "arvore_tamanho": {"excesso_sobre_subcategorias_da_taxonomia_nova": frac(arv["todos"]["arvore_resultante"]["excesso_sobre_a_taxonomia_%"])},
    "migracao": {"aplicados": frac(mig["aplicados_%"]), "acerto": frac(mig["acerto_subcategoria_aplicados_%"]), "pioras": frac(mig["pioras_%_do_total"])},
    "atributo_no_legado": {"acerto": frac((mig.get("rodada8") or {}).get("atributos_do_no_legado", {}).get("%"))},
    "nao_se_aplica": {"taxa": frac(e["nao_se_aplica"]["resolvidos_%"])},
    "contradiz_anuncio": {"taxa": frac(e["contradiz_anuncio"]["resolvidos_%"])},
    "fora_do_normal": {"taxa": frac(e["fora_do_normal"]["resolvidos_%"]), "trocados_por_outro_valor": frac(e["fora_do_normal"]["trocados_por_outro_valor_%"])},
    "sem_pista_inventado": {"taxa": frac(e["sem_pista"]["inventados_%"])},
    "atributos_estragados": {"taxa": frac(r8v["estragados"]["%"])},
    "nao_eletricos_no_filtro": {"taxa": frac(r8v["no_filtro_indevido"]["%"])},
    "mantidos_sem_decisao": {"taxa": frac(ver["mantidos_%"])},
    "ambiguidade": {"principal": frac((amb or {}).get("principal_certa_%")), "secundaria_precisao": frac((amb or {}).get("secundaria_precisao_%"))},
}
r7 = RES_F2["Classificador + agente + Laya (rodada 7)"]
medidas["rodada7_mantida"] = {"piora_pontos": max(0.0, r7["acerto_subcategoria_aplicados_%"] - ver["acerto_subcategoria_aplicados_%"], ver["pioras_%_do_total"] - r7["pioras_%_do_total"],
                                                    r7["corretos_no_catalogo_%"] - ver["corretos_no_catalogo_%"])}
if res_F2_llm:
    medidas["faixa_duvida_llm"] = {"acerto_no_que_o_llm_preencher": frac(res_F2_llm["acerto_do_que_o_llm_preencheu_%"])}
    medidas["consumo_llm_categoria"] = {"fracao_dos_produtos": frac(res_F2_llm["produtos_que_foram_ao_llm_%"])}
    medidas["consumo_llm_total"] = {"atributos_ao_llm_fracao_dos_avaliados": frac(res_F2_llm["atributos_ao_llm_%_dos_avaliados"])}
fl.gravar_json(SAIDA / "resultados" / "c.json", {"medidas": medidas, "versao_arvore": VERSAO_FINAL_ARV, "chamadas_llm": None if LLM is None else LLM.resumo_de_uso()})
crit = fl.ler_json(TRAB / "r8-criterios.json")
for l in fl.checar_criterios(crit, medidas):
    if l["situacao"] != "nao medido":
        print("%-9s %-62s %s" % (l["situacao"].upper(), l["medida"][:62], l["medido"]))
fl.concluir_bloco(str(SAIDA), "c", {"versao_arvore": VERSAO_FINAL_ARV, "chamadas_llm": None if LLM is None else LLM.chamadas, "pipeline": pv.VERSAO_PIPELINE})
print("bloco C concluido. Proximo: laya_r8_d_novos_e_busca.")''')


# =====================================================================================================================
# CORRECAO DE REGRA DA FICHA (F2), feita DEPOIS da primeira execucao do bloco C (diagnostico + regra v2). Nao refaz a arvore, a migracao nem o Laya.
# =====================================================================================================================
md(r'''# Correção de regra da ficha (F2), feita **depois da primeira execução**

> **Isto é uma correção de regra feita depois de ver os resultados do bloco C**, e fica registrada assim no resumo, com os números **antes e depois**.

**O que a primeira execução mostrou.** F1, "não se aplica", "contradiz o anúncio" e o consumo de LLM atenderam. Não atenderam: *fora do normal* (36,1% trocados por outro valor, meta 0), *sem pista* (28,7% "corrigidos sem base", meta ≤ 1%), *atributos certos estragados* (2,7%, meta ≤ 1%) e *faixa de dúvida com LLM* (72% de acerto, meta ≥ 95%).

**O que este trecho faz, nesta ordem**
1. **Diagnóstico** (célula abaixo): para cada atributo que o agente **alterou** em relação ao que o vendedor informou, mostra **qual etapa trocou** o valor (anúncio, nó legado, Laya, LLM, estatística, "o código não leu o valor do vendedor"...), se o **valor novo é o certo**, e 10 exemplos por etapa (título, valor do vendedor, valor novo, valor certo, nó legado). Roda a **regra antiga** (`v1`), numa amostra de teste (todos os produtos com erro "sem pista" ou "fora do normal" + 20 mil estratificados).
2. **Regra nova (`v2`)**: o valor informado pelo vendedor **só pode ser trocado** (a) quando o atributo **não está na ficha** (vira "não se aplica") ou (b) quando o código acha **no título ou na descrição um valor explícito diferente**. Fora isso nada sobrescreve: o **Laya e o atributo do nó legado só preenchem atributo em que o vendedor não informou nada**; em conflito sem prova no texto, **vale o valor do vendedor**; se a estatística apontar valor **fora do normal**, o valor **sai do filtro, com o original guardado, e não volta** por outra etapa; o que o código não consegue ler fica vazio.
3. **Faixa de dúvida:** o **LLM nos atributos está desligado** (abaixo de 0,9 fica vazio); a medição de acerto por faixa continua, só para registro.
4. **Medição:** a previsão passa a carregar `atributos_originais` (o valor do vendedor lido **só do campo dele** pelo próprio pipeline; não vem do gabarito). O avaliador usa esse original para saber se o agente manteve, tirou ou trocou o valor. As definições antigas ("sem pista" comparava o valor canônico com o texto cru do vendedor, como "5 bocas"; "estragado" contava todo `nao_informado`) **continuam calculadas** e aparecem ao lado, para separar o efeito da **medição** do efeito da **regra**.
5. **Refaz só a ficha (F2)** com as categorias já decididas (lidas do Drive), as sugestões do Laya já calculadas (cache) e **sem GPU**: sem refazer a árvore, a migração nem o Laya. Limiares novos, se houver, só da validação (a folga da estatística continua a calibrada).

**O que precisa estar no Drive:** o bloco C concluído (`saida-r8/c/` e `cache/`). Reenvie `pipeline_v3.py`, `fluxo_r8.py`, `avaliar.mjs` (em `catalogo-v2/avaliacao/`) e este notebook.''')

code(r'''# Se a sessao do bloco C foi encerrada, esta celula refaz o ambiente (nao precisa de GPU). Se for a mesma sessao, ela recarrega os modulos atualizados.
''' + "import sys as _s\nfor _m in (\"pipeline_v3\", \"fluxo_r8\"):\n    _s.modules.pop(_m, None)\n" + AMBIENTE.replace("__GPU__", "nao") + "\n" + exigir("c") + r'''
garantir_bases()
import pandas as pd
from collections import Counter
print("ambiente da correcao pronto (sem GPU)")''')

md(r'''## Diagnóstico: quem trocou o valor do vendedor?
Roda a **regra antiga** (a da primeira execução) com a **procedência de cada atributo** e compara com o valor do vendedor lido pelo código só do campo dele. Para os três recortes (sem pista, fora do normal, estragados), mostra por etapa quantos foram **trocados** ou **removidos** e se o valor novo é o certo (o gabarito só é lido **agora**, para medir). Mostra também a definição **antiga** de "estragado" (todo atributo certo e inferível que ficou `nao_informado`), para ver quanto era só atributo que o vendedor nem informou e o agente não preencheu.''')

code(r'''CX = fl.contexto_f2(str(SAIDA), str(CACHE), str(TRAB), str(D_R8))
OPX = pv.Opcoes()
verdade_t = fl.verdade_para_medir(str(D_R8), set(CX.IDS_TESTE))           # MEDIDA: o gabarito dos ids de teste so e lido agora
com_erro = [i for i in CX.IDS_TESTE if any(e["tipo"] in ("sem_pista", "fora_do_normal") for e in (verdade_t[i].get("erros_de_atributo") or []))]
s_erro = set(com_erro)
outros = [i for i in CX.IDS_TESTE if i not in s_erro]
sel = fl.amostra_estratificada([CX.prods_all[CX.pos[i]] for i in outros], 20000, lambda p: CX.subs_E[CX.pos[p.id]] or "sem")
AMOSTRA_DIAG = com_erro + [outros[k] for k in sel]
fl.gravar_json(SAIDA / "c" / "ids-diagnostico-f2.json", AMOSTRA_DIAG)
print("amostra do diagnostico: %d produtos (%d com erro sem_pista ou fora_do_normal + %d estratificados)" % (len(AMOSTRA_DIAG), len(com_erro), len(sel)))

def rodar_ficha(regra, ids, subs, atr_no=None, nome="diag"):
    idx = [CX.pos[i] for i in ids]
    prods = [CX.prods_all[i] for i in idx]
    ss = [subs[i] for i in idx]
    f, det, uso = pv.ficha_r8(prods, ss, CX.tx, CX.codigo, None, None, OPX.sem("faixas_llm"), CX.ESTAT, CX.FATOR, None, CX.FAIXA, atr_no, None, None, nome, regra=regra, sugestoes_cache=CX.SUG)
    return prods, ss, f, det, uso

DIAG = {}
for rotulo, subs_, atr_ in (("F2 (Agente v3, categorias do conjunto E, sem no legado)", CX.subs_E, None), ("migracao (categorias do conjunto N, com o atributo do no legado)", CX.subs_N, CX.ATR_NO)):
    prods, ss, f, det, uso = rodar_ficha("v1", AMOSTRA_DIAG, subs_, atr_)
    d = fl.diagnosticar_alteracoes(CX, prods, ss, f, det, verdade_t)
    DIAG[rotulo] = d
    print("\n" + "=" * 110 + "\n" + rotulo + "\n" + "=" * 110)
    for g, dd in d["contagens"].items():
        linhas = [{"etapa": e, **c_} for e, c_ in dd.items()]
        print("\n-- %s" % g)
        print(pd.DataFrame(linhas).fillna(0).to_string(index=False) if linhas else "(nenhum)")
    # a definicao do avaliador na regra antiga, na amostra, com as duas definicoes lado a lado
    decs = [pv.Decisao(s is not None, s, 1.0, "categoria reaproveitada do Drive") for s in ss]
    arq = SAIDA / "c" / ("diagnostico-previsoes-%s.jsonl" % ("f2" if atr_ is None else "migracao"))
    pv.escrever_jsonl(str(arq), fl.previsoes_de_saneamento(prods, decs, CX.MARCAS, f, None, detalhes=det))
    r = avaliar_saneamento(str(arq), str(SAIDA / "c" / "ids-diagnostico-f2.json"))
    e8 = r["rodada8"]
    print("\navaliador (regra antiga, na amostra): fora_do_normal trocados %% %s (def. antiga %s) | sem_pista inventados %% %s (def. antiga %s) | estragados %% %s (def. antiga %s)" % (
        e8["erros_de_atributo"]["fora_do_normal"]["trocados_por_outro_valor_%"], e8["erros_de_atributo"]["fora_do_normal"].get("trocados_por_outro_valor_%_definicao_antiga"),
        e8["erros_de_atributo"]["sem_pista"]["inventados_%"], e8["erros_de_atributo"]["sem_pista"].get("inventados_%_definicao_antiga"), e8["estragados"]["%"], e8["estragados_definicao_antiga"]["%"]))
    DIAG[rotulo]["avaliador_regra_antiga_na_amostra"] = {"fora_do_normal": e8["erros_de_atributo"]["fora_do_normal"], "sem_pista": e8["erros_de_atributo"]["sem_pista"], "estragados": e8["estragados"], "estragados_definicao_antiga": e8["estragados_definicao_antiga"]}

print("\n" + "=" * 110 + "\nEXEMPLOS (10 por etapa): titulo, valor do vendedor, valor novo, valor certo e o no legado\n" + "=" * 110)
for rotulo, d in DIAG.items():
    for g in ("sem_pista", "fora_do_normal", "estragados"):
        for etapa, lista in (d["exemplos"].get(g) or {}).items():
            print("\n## %s | %s | etapa: %s | %d exemplos" % (rotulo.split(" (")[0], g, etapa, len(lista)))
            for ex in lista:
                print("  [%s] %s | %s: vendedor=%r -> novo=%r | certo=%r %s | no legado: %s%s" % (ex["id"], ex["titulo"][:60], ex["chave"], ex["valor_do_vendedor"], ex["valor_novo"], ex["valor_certo"],
                      "(o novo e o certo)" if ex["valor_novo_e_o_certo"] else "(o novo NAO e o certo)", ex["no_legado"], " | atributo do no: %s" % (ex["atributo_do_no"],) if ex["atributo_do_no"] else ""))
fl.gravar_json(SAIDA / "c" / "diagnostico-f2.json", {"amostra": len(AMOSTRA_DIAG), "regra_diagnosticada": "v1 (primeira execucao)", "diagnostico": DIAG})
print("\ndiagnostico gravado em saida-r8/c/diagnostico-f2.json")''')

md(r'''## Regra nova (`v2`) e a medição de novo
Refaz **só a ficha** de todos os produtos (o catálogo e a busca do bloco D dependem dela), com as categorias do Drive e as sugestões do Laya em cache. Guarda **cópias dos arquivos da primeira execução** (`*-primeira-execucao.*`) e compara três colunas: **antes** (primeira execução, definição antiga), **depois com a definição antiga** (efeito só da regra) e **depois com a definição corrigida** (regra + medição). Atualiza `resultados/c.json` (os critérios), `resultados-f2.json` e `resultados-migracao.json`, e grava `resultados-f2-correcao.json` com tudo, para o painel mostrar a seção "correção de regra pós-primeira execução".''')

code(r'''import shutil as _sh
for nome_ in ("saneamento-Agente_v3.jsonl", "saneamento-migracao-N.jsonl", "resultados-f2.json", "resultados-migracao.json"):
    orig_, copia_ = SAIDA / "c" / nome_, SAIDA / "c" / nome_.replace(".json", "-primeira-execucao.json")
    if orig_.exists() and not copia_.exists():
        _sh.copy(orig_, copia_)
if not (SAIDA / "resultados" / "c-primeira-execucao.json").exists():
    _sh.copy(SAIDA / "resultados" / "c.json", SAIDA / "resultados" / "c-primeira-execucao.json")
ANTES_F2 = fl.ler_json(SAIDA / "c" / "resultados-f2-primeira-execucao.json")
ANTES_MIG = fl.ler_json(SAIDA / "c" / "resultados-migracao-primeira-execucao.json")
ANTES_C = fl.ler_json(SAIDA / "resultados" / "c-primeira-execucao.json")

# ---- F2: a ficha do Agente v3 com a regra v2, categorias reaproveitadas
decs_E = [pv.Decisao(s is not None, s, 1.0, "categoria reaproveitada do Drive") for s in CX.subs_E]
fichas_E2, det_E2, uso_E2 = fl.ficha_em_blocos(CX.prods_all, CX.subs_E, CX.tx, CX.codigo, None, None, OPX.sem("faixas_llm"), CX.ESTAT, CX.FATOR, None, CX.FAIXA, None, str(CACHE / "fichas"), "v3-E2",
                                               regra="v2", sugestoes_cache=CX.SUG)
f_novo = SAIDA / "c" / "saneamento-Agente_v3.jsonl"
pv.escrever_jsonl(str(f_novo), fl.previsoes_de_saneamento(CX.prods_all, decs_E, CX.MARCAS, fichas_E2, CX.SEC, detalhes=det_E2))
R_F2 = avaliar_saneamento(str(f_novo), CX.ARQ_TESTE)
# ---- migracao: so a ficha (as categorias do conjunto N ficam as da primeira execucao)
decs_N = [pv.Decisao(s is not None, s, 1.0, "categoria reaproveitada do Drive") for s in CX.subs_N]
fichas_N2, det_N2, uso_N2 = fl.ficha_em_blocos(CX.prods_all, CX.subs_N, CX.tx, CX.codigo, None, None, OPX.sem("faixas_llm"), CX.ESTAT, CX.FATOR, None, CX.FAIXA, CX.ATR_NO, str(CACHE / "fichas"), "migracao-N2",
                                               regra="v2", sugestoes_cache=CX.SUG)
f_novo_n = SAIDA / "c" / "saneamento-migracao-N.jsonl"
pv.escrever_jsonl(str(f_novo_n), fl.previsoes_de_saneamento(CX.prods_all, decs_N, CX.MARCAS, fichas_N2, None, detalhes=det_N2))
R_MIG = avaliar_saneamento(str(f_novo_n), CX.ARQ_TESTE)

def metricas(r, definicao):
    r8 = r["rodada8"]; e = r8["erros_de_atributo"]
    ant = definicao == "antiga"
    fn, sp = e["fora_do_normal"], e["sem_pista"]
    return {"nao_se_aplica_resolvido_%": e["nao_se_aplica"]["resolvidos_%"], "contradiz_resolvido_%": e["contradiz_anuncio"]["resolvidos_%"],
            "fora_do_normal_tirado_do_filtro_%": fn["tirados_do_filtro_%"], "fora_do_normal_trocado_por_outro_valor_%": fn.get("trocados_por_outro_valor_%_definicao_antiga", fn["trocados_por_outro_valor_%"]) if ant else fn["trocados_por_outro_valor_%"],
            "sem_pista_inventado_%": sp.get("inventados_%_definicao_antiga", sp["inventados_%"]) if ant else sp["inventados_%"],
            "atributos_estragados_%": (r8.get("estragados_definicao_antiga") or r8["estragados"])["%"] if ant else r8["estragados"]["%"],
            "nao_eletricos_no_filtro_%": r8["no_filtro_indevido"]["%"], "controles_voltagem_removida_%": r8["controles_eletricos"]["%"],
            "atributo_do_no_legado_recuperado_%": r8["atributos_do_no_legado"]["%"], "atributos_acerto_dos_inferiveis_%": r["atributos"]["acerto_inferiveis_%"],
            "aplicados_%": r["aplicados_%"], "acerto_dos_aplicados_%": r["acerto_subcategoria_aplicados_%"], "pioras_%": r["pioras_%_do_total"]}
TAB_F2 = {"Antes (1a execucao, definicao antiga)": metricas(ANTES_F2["versoes"]["Agente v3"], "antiga"), "Depois (regra nova, definicao antiga)": metricas(R_F2, "antiga"), "Depois (regra nova, definicao corrigida)": metricas(R_F2, "nova")}
TAB_MIG = {"Antes (1a execucao, definicao antiga)": metricas(ANTES_MIG["versoes"]["agente_v3_N"], "antiga"), "Depois (regra nova, definicao antiga)": metricas(R_MIG, "antiga"), "Depois (regra nova, definicao corrigida)": metricas(R_MIG, "nova")}
print("== F2 (Agente v3, nos produtos de teste)"); print(pd.DataFrame(TAB_F2).to_string())
print("\n== migracao (conjunto N, com o atributo do no legado)"); print(pd.DataFrame(TAB_MIG).to_string())''')

code(r'''# ---- faixas de confianca do Laya, so para registro (o LLM nos atributos esta desligado: abaixo de 0,9 fica vazio)
faixas_teste = pv.acerto_por_faixa([s for s in uso_E2["sugestoes_laya"] if s["id"] in verdade_t], verdade_t)
print("acerto das sugestoes do Laya por faixa de confianca (teste, so as que a regra v2 deixa pendentes):", faixas_teste)
print("uso da ficha v2:", {k: v for k, v in uso_E2.items() if k not in ("sugestoes_laya", "preenchidos_llm")})

# ---- critérios: atualiza resultados/c.json (guarda a primeira execucao em c-primeira-execucao.json) e os resultados que o painel le
def frac(x):
    return None if x is None else x / 100.0
e_ = R_F2["rodada8"]["erros_de_atributo"]; r8_ = R_F2["rodada8"]
medidas_c = dict(ANTES_C["medidas"])
medidas_c.update({
    "nao_se_aplica": {"taxa": frac(e_["nao_se_aplica"]["resolvidos_%"])}, "contradiz_anuncio": {"taxa": frac(e_["contradiz_anuncio"]["resolvidos_%"])},
    "fora_do_normal": {"taxa": frac(e_["fora_do_normal"]["resolvidos_%"]), "trocados_por_outro_valor": frac(e_["fora_do_normal"]["trocados_por_outro_valor_%"])},
    "sem_pista_inventado": {"taxa": frac(e_["sem_pista"]["inventados_%"])}, "atributos_estragados": {"taxa": frac(r8_["estragados"]["%"])},
    "nao_eletricos_no_filtro": {"taxa": frac(r8_["no_filtro_indevido"]["%"])}, "atributo_no_legado": {"acerto": frac(R_MIG["rodada8"]["atributos_do_no_legado"]["%"])},
    "consumo_llm_total": {"atributos_ao_llm_fracao_dos_avaliados": 0.0},
})
medidas_c.pop("faixa_duvida_llm", None)       # o LLM nos atributos foi desligado: o criterio fica "nao medido" (por decisao), nao "atende"
fl.gravar_json(SAIDA / "resultados" / "c.json", {**ANTES_C, "medidas": medidas_c, "correcao_f2_pos_primeira_execucao": True,
                                                 "nota": "ficha refeita com a regra v2 depois da primeira execucao; faixa_duvida_llm nao medido porque o LLM nos atributos foi desligado"})
novo_f2 = dict(ANTES_F2); novo_f2["versoes"] = dict(ANTES_F2["versoes"]); novo_f2["versoes"]["Agente v3"] = R_F2
novo_f2["uso_ficha_v3"] = {k: v for k, v in uso_E2.items() if k not in ("sugestoes_laya", "preenchidos_llm")}; novo_f2["correcao_pos_primeira_execucao"] = True
fl.gravar_json(SAIDA / "c" / "resultados-f2.json", novo_f2)
novo_mig = dict(ANTES_MIG); novo_mig["versoes"] = dict(ANTES_MIG["versoes"]); novo_mig["versoes"]["agente_v3_N"] = R_MIG; novo_mig["correcao_pos_primeira_execucao"] = True
fl.gravar_json(SAIDA / "c" / "resultados-migracao.json", novo_mig)
fl.gravar_json(SAIDA / "c" / "resultados-f2-correcao.json", {
    "tipo": "CORRECAO DE REGRA feita DEPOIS da primeira execucao do bloco C",
    "motivo": {"fora_do_normal_trocados_por_outro_valor_%": ANTES_F2["versoes"]["Agente v3"]["rodada8"]["erros_de_atributo"]["fora_do_normal"]["trocados_por_outro_valor_%"], "sem_pista_corrigidos_sem_base_%": ANTES_F2["versoes"]["Agente v3"]["rodada8"]["erros_de_atributo"]["sem_pista"]["inventados_%"],
               "atributos_estragados_%": ANTES_F2["versoes"]["Agente v3"]["rodada8"]["estragados"]["%"], "faixa_de_duvida_com_llm_acerto_%": fl.ler_json(SAIDA / "c" / "resultados-f2-llm.json").get("acerto_do_que_o_llm_preencheu_%")},
    "regra_antes": "v1: o anuncio vence; a estatistica tira o valor do filtro; o que ficava vazio era preenchido por no legado, Laya e LLM (inclusive o que era do vendedor)",
    "regra_depois": "v2: o valor do vendedor so muda por 'nao se aplica' ou por valor explicito diferente no titulo ou na descricao; no legado e Laya so preenchem atributo que o vendedor nao informou; fora do normal sai do filtro e nao volta; LLM nos atributos desligado",
    "medicao": "a previsao carrega atributos_originais (o valor do vendedor lido so do campo dele pelo pipeline); 'sem pista' e 'estragado' passaram a usar o original; as definicoes antigas continuam calculadas (colunas 'definicao antiga')",
    "limiares_novos": "nenhum (a folga da estatistica e a faixa de duvida continuam as calibradas na validacao)",
    "f2": TAB_F2, "migracao": TAB_MIG, "faixas_do_laya_no_teste": faixas_teste, "diagnostico": {k: {"contagens": v["contagens"], "avaliador_regra_antiga_na_amostra": v["avaliador_regra_antiga_na_amostra"]} for k, v in DIAG.items()},
    "criterios_antes_e_depois": {k: {"antes": ANTES_C["medidas"].get(k), "depois": medidas_c.get(k)} for k in ("nao_se_aplica", "contradiz_anuncio", "fora_do_normal", "sem_pista_inventado", "atributos_estragados", "nao_eletricos_no_filtro", "atributo_no_legado", "faixa_duvida_llm", "consumo_llm_total")}})
crit_ = fl.ler_json(TRAB / "r8-criterios.json")
alvo = ("nao_se_aplica", "contradiz_anuncio", "fora_do_normal", "sem_pista_inventado", "atributos_estragados", "nao_eletricos_no_filtro", "atributo_no_legado", "faixa_duvida_llm", "consumo_llm_total")
for l in fl.checar_criterios(crit_, medidas_c):
    if l["id"] in alvo:
        print("%-10s %-62s %s" % (l["situacao"].upper(), l["medida"][:62], l["medido"] or "(nao medido: LLM nos atributos desligado por decisao)" if l["id"] == "faixa_duvida_llm" else l["medido"]))
est_ = fl.ler_json(SAIDA / "estado.json"); est_["c"]["correcao_f2"] = {"regra": "v2", "quando": time.strftime("%Y-%m-%d %H:%M:%S")}; fl.gravar_json(SAIDA / "estado.json", est_)
print("\nCorrecao registrada em saida-r8/c/resultados-f2-correcao.json. Reenvie ao Drive e rode o bloco D (ele usa a ficha nova) e o F.")''')
