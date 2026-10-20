# -*- coding: utf-8 -*-
# Bloco D: itens novos, catalogo existente, busca, modo autonomo e quanto cada peca ajuda. Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS, AMBIENTE, exigir

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, bloco D: itens novos, catálogo existente, busca e modo autônomo

**As perguntas deste bloco**
- **Itens novos (pendências da rodada 7).** *Duplicados:* o agente aponta o produto **original** certo e separa duplicado de variação? (rodada 7: 75,0% de precisão e 77,2% de cobertura; critério ≥ 95% / ≥ 80%.) *Variações:* o mesmo modelo em outra cor, voltagem ou tamanho vira **SKU do mesmo produto**, nos itens novos e no catálogo existente? *Fora da taxonomia:* reconhece o tipo de produto que **nunca viu** em vez de forçar a vizinha, e sugere uma subcategoria nova? (rodada 7: 91,5% / 19,7%; critério ≥ 90% / ≥ 70%.) *Inválidos sem exagero:* o 100% da rodada 7 vale também para os casos **limítrofes**?
- **Busca.** Consulta **sem resposta** volta vazia; consulta **com modelo** ("capinha iphone 15") não ignora o modelo; a correção de digitação só acontece com limiar; "fogão bivolt" e afins melhoram porque a ficha agora trata o "não se aplica".
- **Modo autônomo.** Auditoria automática por amostragem, **freio** que pausa e reverte um lote ruim, **reversão** que devolve o catálogo idêntico (hash) e o relatório de exceções.
- **Quanto cada peça ajuda**, com o consumo de LLM ao lado de cada versão.

**Como os itens novos são tratados.** Candidatos (EAN igual; marca + modelo; vizinhos por embedding acima de um limiar calibrado na validação) → comparação dos **eixos de variação** (todos iguais: duplicado; só um eixo diferente: variação; mais de um eixo ou subcategoria diferente: produtos diferentes) → **resolver para o original** (seguir o grupo até o primeiro produto, nunca apontar para uma cópia) → disputas ao LLM (restrito a `duplicado | variacao | diferente`). **Fora da taxonomia:** quatro sinais ("nenhuma destas" do Laya, distância dos vizinhos, discordância entre modelos e a confirmação do LLM só nos candidatos); a subcategoria nova só é **criada** com volume mínimo e juiz; até lá o item fica na categoria do vendedor e fora dos filtros.

**Regras.** Pipeline sem gabarito; calibração só na validação, gravada em `r8-escolhas-val.json` antes das medidas do teste; chaves só em Colab Secrets; estimativa de chamadas ao LLM mostrada **antes** de cada etapa que o usa (30 por minuto, espera e retomada no 429). **Tempo (estimativa; o notebook mede):** reconstruir o contexto ~10 a 20 min; itens novos e catálogo existente ~20 a 60 min sem LLM; busca ~15 a 30 min; autônomo (com LLM) ~1 h; quanto cada peça ajuda ~30 a 60 min.

''' + BLOCOS + "\n\n" + AVISOS)

code(AMBIENTE.replace("__GPU__", "sim") + "\n" + exigir("c") + r'''
garantir_bases()
import torch, pandas as pd
CONFIRMO_LLM = True        # LLM nas disputas de duplicado, no fora da taxonomia, na auditoria. So roda com OLLAMA_API_KEY em Colab Secrets; a estimativa aparece antes
TAMANHO_AUDITORIA = 300    # produtos auditados por lote (a amostra pequena deixa a estimativa imprecisa; 300 da +-3 pontos)
TEM_CHAVE = carregar_chave_llm() and CONFIRMO_LLM
print("LLM:", "ligado" if TEM_CHAVE else "NAO executado (as linhas com LLM saem como 'nao executado')")''')

md(r'''## Contexto
Reconstrói o que o bloco C deixou no Drive, **sem recalibrar nada**: checkpoint (conferindo o hash), NB, índice de vizinhos, sinais dos 100 mil (cache do Laya e dos embeddings), a regra de votação gravada em `r8-escolhas-val.json`, a decisão de categoria do conjunto E e a ficha do Agente v3.''')

code(r'''t0 = time.time()
C = fl.carregar_contexto(str(SAIDA), str(CACHE), str(TRAB), str(D_R8), str(CK_DRIVE), est["b"]["sha256"])
tx, LOTE, OP, prods_all, pos, spl = C.tx, C.LOTE, C.OP, C.prods_all, C.pos, C.spl
MARCAS, codigo, EMB, VIZ, NB, SIN, ESC = C.MARCAS, C.codigo, C.EMB, C.VIZ, C.NB, C.SIN, SAIDA / "r8-escolhas-val.json"
IDS_TESTE, ARQ_TESTE = C.IDS_TESTE, C.ARQ_TESTE
print("contexto pronto em %.0f min | regra E: %s | fator da estatistica %.2f | faixa de duvida %s" % ((time.time() - t0) / 60, C.PARAMS_R8["E"].resumo(), C.FATOR, C.FAIXA))
LLM = pv.LlmRestritoOllama(tx) if TEM_CHAVE else None
novos_todos = pv.ler_produtos(str(D_R8 / "novos.jsonl"))
print("itens novos:", len(novos_todos), "| novos_teste:", len(spl["novos_teste"]))''')

md(r'''## Diagnóstico dos erros da rodada 7 (antes de mexer)
Dos duplicados que o agente da rodada 7 apontou **errado**: quantos eram **outra cópia do mesmo grupo**, quantos eram **variação tratada como duplicado** e quantos eram **produto diferente**. Usa as previsões de itens novos da rodada 7 gravadas no Drive (copie-as para `laya-teste-r8/saida-r7/`; o notebook procura `novos*.jsonl`) e o gabarito da base da rodada 7 (gerada de novo, sem `--rodada8`). **É medida:** o gabarito só é lido depois, para contar os motivos. Sem as previsões, a etapa é pulada (e o painel marca "não executado").''')

code(r'''DIAG_R7 = None
cands = sorted((DRIVE / "saida-r7").glob("**/novos*.jsonl")) if (DRIVE / "saida-r7").exists() else []
print("previsoes de itens novos da rodada 7 encontradas:", [str(c.relative_to(DRIVE)) for c in cands])
if cands:
    D_R7 = TRAB / "dados-r7"
    gerar(D_R7, "--semente", str(SEMENTE_R8), "--ruido", str(RUIDO_R8))        # a base da rodada 7: a mesma semente SEM --rodada8
    previsoes_r7 = list(pv.ler_jsonl(str(cands[0])))
    gab_novos = {m["id"]: m.get("gabarito") or {} for m in pv.ler_jsonl(str(D_R7 / "novos.jsonl"))}
    gab_prod = {m["id"]: m.get("gabarito") or {} for m in pv.ler_jsonl(str(D_R7 / "produtos.jsonl"))}
    DIAG_R7 = pv.diagnostico_erros_da_rodada7(previsoes_r7, gab_novos, gab_prod)
    print("diagnostico dos duplicados da rodada 7 (arquivo %s):" % cands[0].name, DIAG_R7)
    fl.gravar_json(SAIDA / "d" / "diagnostico-r7.json", {"arquivo": cands[0].name, **DIAG_R7})
else:
    print("PULADO: sem as previsoes da rodada 7 (copie saida-r7/novos*.jsonl para laya-teste-r8/saida-r7/ e rode de novo esta celula).")''')

md(r'''## Calibração (só na validação)
- **Limiar de similaridade de embedding** para candidatos a duplicado: o menor cuja precisão de "mesmo grupo" chega a 95% entre os vizinhos dos 2.000 produtos de validação (os grupos de duplicados ficam inteiros num lado do split).
- **Fora da taxonomia:** a distância dos vizinhos e quantos sinais bastam, medidos com os itens de fora da taxonomia do **grupo de treino** (`fora_treino.jsonl`, split validação) contra produtos normais da validação. Os tipos do teste nunca aparecem aqui.
- **Volume mínimo** para criar uma subcategoria nova, nos mesmos itens.
- **Limite do freio:** a taxa de erro estimada de lotes bons e de um lote ruim da validação.
Tudo vai para `r8-escolhas-val.json` antes de medir.''')

code(r'''# ---- limiar de embedding (duplicados)
ver_val = fl.rotulos_para_calibrar(str(D_R8), "validacao", campos=("duplicado_de", "variacao_de"))
cal_emb = pv.calibrar_similaridade_embedding(C.prods_val, C.SIN_VAL["vetores"], ver_val)
print("limiar de embedding (validacao):", cal_emb["escolha"], "| atingiu o piso:", cal_emb["atingiu_o_piso"])

# ---- fora da taxonomia: sinais nos itens de fora (grupo de treino, validacao) x produtos normais da validacao
fora_val = [m for m in pv.ler_jsonl(str(D_R8 / "fora_treino.jsonl")) if m["split"] == "validacao"]
pf = [pv.Produto(m) for m in fora_val]
normais = C.prods_val[:len(pf)]
def sinais_para(prods, parm):
    est_, _ = pv.estados_dos_produtos(prods)
    cats, confs = NB.prever_lote([(p.titulo, p.atributos, pv.texto_longo(p)) for p in prods])
    laya = pv.cascata_laya_com_nenhuma(LOTE, tx, est_)
    vet = EMB.codificar([pv.texto_para_embedding(p) for p in prods])
    viz = VIZ.consultar(vet, [p.id for p in prods])
    return [pv.sinais_de_fora(l, v, c, cf, parm) for l, v, c, cf in zip(laya, viz, cats, confs)], [v["sim_max"] for v in viz]
parm0 = dict(pv.PARAMETROS_NOVOS, distancia_fora=-1.0)     # sem o sinal de distancia (ele depende do limiar que se esta calibrando)
sf, df_ = sinais_para(pf, parm0)
sn, dn = sinais_para(normais, parm0)
cal_fora = pv.calibrar_fora_da_taxonomia(sf, sn, df_, dn)
print("fora da taxonomia (validacao):", cal_fora["escolha"], "| atingiu o piso:", cal_fora["atingiu_o_piso"])

# ---- volume minimo da subcategoria nova: grupos formados a partir das sugestoes (nome do titulo) dos itens de fora da validacao
tipos_val = {m["id"]: m["gabarito"]["sugestao_nova_subcategoria"] for m in fora_val}        # calibracao: gabarito da VALIDACAO
tab_vol = []
for vol in (5, 10, 20, 40):
    cr = pv.CriadorDeSubcategorias(tx, vol, 0.6)
    for p, m in zip(pf, fora_val):
        cr.adicionar(p.id, pv._nome_do_titulo(p), m["gabarito"]["departamento_provavel"], [])      # calibracao na VALIDACAO: o departamento vem do gabarito dos itens de fora do grupo de treino
    res = cr.criar(None)
    pureza = []
    for g in res["criadas"]:
        c_ = Counter(tipos_val[i] for i in g["itens"])
        pureza.append(c_.most_common(1)[0][1] / len(g["itens"]))
    tab_vol.append({"volume": vol, "criadas": len(res["criadas"]), "adiadas": len(res["adiadas"]), "pureza_media_%": round(100 * sum(pureza) / max(1, len(pureza)), 1)})
print(pd.DataFrame(tab_vol).to_string(index=False))
ok_vol = [t for t in tab_vol if t["criadas"] > 0 and t["pureza_media_%"] >= 90]
VOLUME = min(ok_vol, key=lambda t: t["volume"])["volume"] if ok_vol else 10
PAR_NOVOS = dict(pv.PARAMETROS_NOVOS, sim_embedding=cal_emb["limiar"], distancia_fora=cal_fora["distancia_fora"], sinais_fora_minimos=cal_fora["sinais_fora_minimos"], volume_minimo_subcategoria=VOLUME)
fl.mesclar_json(ESC, {"embedding_duplicados": cal_emb, "fora_da_taxonomia": cal_fora, "volume_subcategoria_nova": {"escolha": VOLUME, "tabela": tab_vol}, "parametros_novos": PAR_NOVOS})
print("parametros dos itens novos:", PAR_NOVOS)''')

md(r'''## Itens novos
Trata todos os itens novos da base (inclusive os extras da rodada 8: duplicados que apontam para um produto que já tem cópias, variações, inválidos limítrofes e os tipos de fora da taxonomia do grupo de teste) e mede nos `novos_teste`. Três versões: **rodada 7** (NB + regras, o que a rodada 7 fazia sem LLM), **v3 sem LLM** e **v3 com LLM nas disputas**. O catálogo existente entra pelo `CatalogoDeIdentidade`: cada cópia aponta para o **original** do grupo e cada família de variações tem uma raiz (a mesma procedimento do catálogo existente, abaixo).''')

code(r'''t0 = time.time()
indice_dup = pv.IndiceDeDuplicados.do_catalogo(prods_all, MARCAS, 0.3)
print("indice de duplicados: %d produtos em %.0fs" % (len(indice_dup.ids), time.time() - t0))
# ---- o catalogo existente: grupos de duplicados e familias de variacao (a mesma regra), antes dos itens novos
t0 = time.time()
IDENT = pv.agrupar_catalogo(prods_all, C.subs_E, tx, codigo, indice_dup, SIN["vetores"], PAR_NOVOS, llm=None)
print("grupos do catalogo existente em %.0f min" % ((time.time() - t0) / 60))
f_grupos = SAIDA / "d" / "grupos-catalogo.jsonl"
pv.escrever_jsonl(str(f_grupos), pv.linhas_de_grupos(prods_all, IDENT))
GRUPOS = pv.avaliar(AVAL, "grupos", str(D_R8), pred=str(f_grupos), ids_arquivo=ARQ_TESTE)
print("catalogo existente (teste):", GRUPOS)
fl.gravar_json(SAIDA / "d" / "resultados-grupos.json", GRUPOS)''')

code(r'''# ---- sinais dos itens novos: NB, Laya com "nenhuma destas" e vizinhos; classificacao pela votacao E
estados_n, _ = pv.estados_dos_produtos(novos_todos)
nb_n, nbc_n = NB.prever_lote([(p.titulo, p.atributos, pv.texto_longo(p)) for p in novos_todos])
laya_n = pv.cascata_laya_com_nenhuma(LOTE, tx, estados_n)
vet_n = EMB.codificar([pv.texto_para_embedding(p) for p in novos_todos], str(CACHE / "emb-novos-r8.npy"))
viz_n = VIZ.consultar(vet_n)
res_n = pv.votacao_r8(novos_todos, nb_n, nbc_n, laya_n, viz_n, tx, C.PARAMS_R8, ["E"], llm=None, vizinhos=VIZ)
dec_n = [d for d, _ in res_n.decisoes["E"]]
subs_n = [d.categoria if d.aplicar else None for d in dec_n]
print("itens novos classificados pela votacao E: %.1f%%" % (100 * sum(s is not None for s in subs_n) / len(subs_n)))

ids_nt = set(spl["novos_teste"])
def avaliar_novos(preds, nome):
    f = SAIDA / "d" / ("novos-%s.jsonl" % nome); pv.escrever_jsonl(str(f), [p for p in preds if p["id"] in ids_nt])
    return pv.avaliar(AVAL, "novos", str(D_R8), pred=str(f), ids="novos_teste")

# ---- rodada 7: NB + regras, sem LLM (a mesma funcao da paridade)
pv.ativar_limpeza_de_entrada(False)
preds_r7, _ = pv.tratar_novos(novos_todos, indice_dup, [None] * len(novos_todos), nbc_n, llm=None, desempate="nenhum", fora="baixa-confianca", fora_confianca=0.2)
pv.ativar_limpeza_de_entrada(True)
NOV = {"Rodada 7 (NB + regras)": avaliar_novos(preds_r7, "r7")}
# ---- v3 sem LLM
preds_v3, det_v3, criador_v3, exc_v3, cont_v3 = pv.tratar_novos_v3(novos_todos, subs_n, vet_n, SIN["vetores"], prods_all, C.subs_E, IDENT, tx, codigo, indice_dup, laya_n, viz_n, nb_n, nbc_n, PAR_NOVOS, llm=None)
NOV["Agente v3 (sem LLM)"] = avaliar_novos(preds_v3, "v3-sem-llm")
print("disputas de duplicado:", cont_v3)''')

code(r'''# ---- v3 com LLM nas disputas e no fora da taxonomia (so os candidatos). Estimativa ANTES.
est_n = pv.estimativa_de_chamadas(0, 0, 0, cont_v3["candidatos_a_fora"] * 1, cont_v3["itens_com_disputa"], 0, 0)
print("ESTIMATIVA: %d disputas de duplicado + %d candidatos a fora da taxonomia = ~%d chamadas, ~%.0f min a 30 por minuto" % (cont_v3["itens_com_disputa"], cont_v3["candidatos_a_fora"], est_n["chamadas"], est_n["minutos_ao_limite"]))
preds_llm = det_llm = criador_llm = exc_llm = None
if LLM is not None and LLM.ativa:
    c0 = LLM.chamadas
    preds_llm, det_llm, criador_llm, exc_llm, cont_llm = pv.tratar_novos_v3(novos_todos, subs_n, vet_n, SIN["vetores"], prods_all, C.subs_E, IDENT, tx, codigo, indice_dup, laya_n, viz_n, nb_n, nbc_n, PAR_NOVOS, llm=LLM)
    NOV["Agente v3 (com LLM nas disputas)"] = avaliar_novos(preds_llm, "v3-llm")
    consumo_novos = pv.consumo_de_llm(LLM, len(novos_todos), n_chamadas_antes=c0)
    print("consumo (itens novos):", consumo_novos)
else:
    consumo_novos = None
    print("versao com LLM: nao executada")
def linha_novos(nome, r):
    r8 = r.get("rodada8") or {}
    return {"versao": nome, "dup_oficial prec/cob": "%s / %s" % (r["duplicados"]["precisao_%"], r["duplicados"]["cobertura_%"]), "dup_por_grupo prec/cob": "%s / %s" % (r8.get("duplicados_por_grupo", {}).get("precisao_%"), r8.get("duplicados_por_grupo", {}).get("cobertura_%")),
            "variacoes prec/cob": "%s / %s" % (r8.get("variacoes", {}).get("precisao_%"), r8.get("variacoes", {}).get("cobertura_%")), "fora prec/cob": "%s / %s" % (r["fora_da_taxonomia"]["precisao_%"], r["fora_da_taxonomia"]["cobertura_%"]),
            "fora dep_%": r8.get("fora_da_taxonomia", {}).get("departamento_provavel_certo_%"), "inval prec/cob": "%s / %s" % (r["invalidos"]["precisao_%"], r["invalidos"]["cobertura_%"]),
            "limitrofes barrados_%": r8.get("invalidos_limitrofes", {}).get("%"), "classificados_%": r["acerto_subcategoria_dos_classificados_%"]}
print(pd.DataFrame([linha_novos(n, r) for n, r in NOV.items()]).to_string(index=False))
print("erros de duplicado por motivo (v3):", (NOV["Agente v3 (sem LLM)"].get("rodada8") or {}).get("erros_de_duplicado_por_motivo"))
print("por pista (v3):", (NOV["Agente v3 (sem LLM)"].get("rodada8") or {}).get("duplicados_por_pista"))
fl.gravar_json(SAIDA / "d" / "resultados-novos.json", {"versoes": NOV, "diagnostico_r7": DIAG_R7, "consumo_com_llm": consumo_novos, "contagens_v3": cont_v3, "excecoes": (exc_llm or exc_v3)[:200]})''')

md(r'''## Subcategorias novas e fora da taxonomia, com a sugestão de nome
Os itens fora da taxonomia são agrupados por sugestão (nome e departamento, por similaridade); a subcategoria só é **criada** com o volume mínimo calibrado e se o juiz confirma que **não é equivalente** a uma existente nem a uma existente + filtro. O resto fica **adiado** (na categoria do vendedor, marcado como fora da taxonomia). **Medida:** a pureza dos itens alocados (pelo tipo de produto do gabarito, só agora) e a conferência por amostra do nome sugerido (o avaliador compara por palavras; a amostra aparece para você conferir à mão).''')

code(r'''criador = criador_llm or criador_v3
CRIACAO = criador.criar(LLM if (LLM is not None and LLM.ativa) else None)
print("subcategorias criadas: %d | adiadas (volume): %d | recusadas (equivalentes): %d" % (len(CRIACAO["criadas"]), len(CRIACAO["adiadas"]), len(CRIACAO["recusadas"])))
ver_n = fl.verdade_para_medir(str(D_R8), {i for g in CRIACAO["criadas"] for i in g["itens"]}, arquivo="novos.jsonl", campos=("sugestao_nova_subcategoria", "nome_sugerido", "fora_da_taxonomia"))
pur, equiv_a_existente = [], 0
for g in CRIACAO["criadas"]:
    tipos = Counter((ver_n.get(i) or {}).get("sugestao_nova_subcategoria") for i in g["itens"])
    top, k = tipos.most_common(1)[0]
    pur.append(k / len(g["itens"]))
    g["tipo_majoritario_no_gabarito"], g["pureza"] = top, round(k / len(g["itens"]), 3)
    if any(pv.similaridade_de_nomes(g["nome"], tx.nome_categoria[f]) >= 0.8 for f in tx.categorias()):
        equiv_a_existente += 1
    print({k_: v for k_, v in g.items() if k_ != "itens"}, "| itens:", len(g["itens"]))
amostra_nomes = (NOV.get("Agente v3 (com LLM nas disputas)") or NOV["Agente v3 (sem LLM)"])["rodada8"]["fora_da_taxonomia"]["amostra_para_conferir"]
print("amostra de nomes sugeridos (confira a olho):")
for a in amostra_nomes:
    print("  ", a)
fl.gravar_json(SAIDA / "d" / "subcategorias-criadas.json", {"criacao": CRIACAO, "pureza_media_%": round(100 * sum(pur) / max(1, len(pur)), 1), "equivalentes_a_existente": equiv_a_existente, "amostra_de_nomes": amostra_nomes})''')

md(r'''## Busca
Catálogos comparados: **Antes** (como chegou; busca por palavra e navegação por categoria do vendedor e atributos crus), **rodada 7** (catálogo organizado pelo voto-b com a ficha por código + Laya e a `BuscaV2`) e **Agente v3** (catálogo organizado pelo voto E com a ficha da rodada 8 e a `BuscaR8`: sem resposta, correção com limiar e modelo obrigatório). Os itens novos aceitos entram nos catálogos (`--incluir-novos`). **Calibração:** o limiar da correção de digitação é escolhido nas consultas de **índice ímpar** e a medida vale nas de **índice par** (a base não tem um split de consultas). A busca com LLM das rodadas anteriores **não** é repetida aqui.''')

code(r'''# ---- catalogos organizados (so produtos aplicados, como o agente faz) + itens novos aceitos
def aceitos_de(preds):
    return [(not p["invalido"]) and (not p["fora_da_taxonomia"]) and (p["duplicado_de"] is None) and (p.get("subcategoria") is not None) for p in preds]
fichas_n, det_n_, uso_n = pv.ficha_r8(novos_todos, [p.get("subcategoria") for p in preds_v3], tx, codigo, LOTE, estados_n, OP.sem("faixas_llm"), C.ESTAT, C.FATOR, None, C.FAIXA, None, None, None, "ficha-novos", regra="v2")
acc_v3 = aceitos_de(preds_v3)
def catalogo_de(decisoes, fichas):
    cat = pv.organizar_catalogo(prods_all, decisoes, fichas, MARCAS, tx, SIN["nb_confs"])
    pv.acrescentar_ao_catalogo(cat, novos_todos, acc_v3, [p.get("subcategoria") for p in preds_v3], fichas_n, nbc_n, MARCAS)
    return cat
CAT_R7 = catalogo_de(C.DEC_B, C.fichas_B)
CAT_V3 = catalogo_de(C.DEC_E, C.fichas_E)
modelos = pv.modelos_compativeis(str(D_R8 / "taxonomia.json"))
buscas = list(pv.ler_jsonl(str(D_R8 / "buscas.jsonl")))
val_q = [b for i, b in enumerate(buscas) if i % 2 == 1]
teste_q = [b for i, b in enumerate(buscas) if i % 2 == 0]
fabrica = lambda s, fq: pv.BuscaR8(tx, CAT_V3, MARCAS, codigo, llm=None, modelos=modelos, similaridade_minima=s, frequencia_minima=fq)
cal_busca = pv.calibrar_busca(fabrica, val_q)
print("correcao de digitacao (consultas de validacao):", cal_busca["escolha"], "| atingiu o piso:", cal_busca["atingiu_o_piso"])
fl.mesclar_json(ESC, {"busca_correcao": cal_busca})
B3 = pv.BuscaR8(tx, CAT_V3, MARCAS, codigo, llm=None, modelos=modelos, similaridade_minima=cal_busca["escolha"]["similaridade_minima"], frequencia_minima=cal_busca["escolha"]["frequencia_minima"])
B7 = pv.BuscaV2(tx, CAT_R7, MARCAS, codigo, llm=None)
d_bt = TRAB / "dados-busca-r8"; d_bt.mkdir(exist_ok=True)
pv.escrever_jsonl(str(d_bt / "buscas.jsonl"), teste_q)
for n in ("produtos.jsonl", "novos.jsonl", "splits.json", "taxonomia.json"):
    if not (d_bt / n).exists():
        os.symlink(str(D_R8 / n), str(d_bt / n))
def lista_busca(b_obj):
    return pv.previsao_de_busca(b_obj, teste_q, 200, "sinonimos")
def avaliar_busca(linhas, nome, extra=None):
    f = SAIDA / "d" / ("busca-%s.jsonl" % nome); pv.escrever_jsonl(str(f), linhas)
    return pv.avaliar(AVAL, "busca", str(d_bt), pred=str(f), extra=["--incluir-novos"] + (extra or []))
BUSCA = {"Antes (busca por palavra)": pv.avaliar(AVAL, "busca", str(d_bt), extra=["--baseline-texto", "--incluir-novos"]),
         "Antes (categoria e filtros do vendedor)": pv.avaliar(AVAL, "busca", str(d_bt), extra=["--baseline-filtros", "--incluir-novos"])}
BUSCA["Rodada 7 (BuscaV2, catalogo organizado)"] = avaliar_busca(lista_busca(B7), "r7")
BUSCA["Agente v3 (BuscaR8, catalogo organizado)"] = avaliar_busca(lista_busca(B3), "v3")
# navegacao por categoria e filtros no catalogo organizado (o cliente clica na categoria e nos filtros certos)
f7 = str(SAIDA / "c" / "saneamento-Classificador_mais_agente_mais_Laya.jsonl"); f3 = str(SAIDA / "c" / "saneamento-Agente_v3.jsonl")
def novos_previstos(preds, fichas):
    return [{"id": p["id"], "status": "APLICADO", "subcategoria": p["subcategoria"], "atributos": dict(f or {})} for p, f, ok in zip(preds, fichas, aceitos_de(preds)) if ok]
pv.escrever_jsonl(str(SAIDA / "d" / "novos-previstos-v3.jsonl"), novos_previstos(preds_v3, fichas_n))
BUSCA["Rodada 7 (navegacao por filtros)"] = pv.avaliar(AVAL, "busca", str(d_bt), extra=["--catalogo-previsto", f7, "--incluir-novos", "--novos-previstos", str(SAIDA / "d" / "novos-previstos-v3.jsonl")])
BUSCA["Agente v3 (navegacao por filtros)"] = pv.avaliar(AVAL, "busca", str(d_bt), extra=["--catalogo-previsto", f3, "--incluir-novos", "--novos-previstos", str(SAIDA / "d" / "novos-previstos-v3.jsonl")])
def linha_busca(nome, r):
    g = lambda k, m: (r.get(k) or {}).get(m)
    return {"versao": nome, "todas P@10_%": g("todas", "P@10_%"), "f2 P@10_%": g("f2", "P@10_%"), "com_modelo P@10_%": g("com_modelo", "P@10_%"), "sem_resposta vazia_%": g("sem_resposta", "vazia_ou_aviso_%"),
            "erro_digitacao P@10_%": g("erro_digitacao", "P@10_%"), "corrigidas P@10_%": g("corrigidas", "P@10_%")}
print(pd.DataFrame([linha_busca(n, r) for n, r in BUSCA.items()]).to_string(index=False))
fl.gravar_json(SAIDA / "d" / "resultados-busca.json", BUSCA)''')

code(r'''# ---- a busca "fogao bivolt" e afins, antes e depois, e exemplos de consulta sem resposta, com modelo e corrigida
for q in ("fogão bivolt", "fogão a lenha", "churrasqueira bivolt", "churrasqueira a carvão", "capinha iphone 15", "geladera de praia nuclear", "xptoz"):
    r3 = B3.buscar(q, "sinonimos"); r7 = B7.buscar(q, "sinonimos")
    print("%-28s v3: %4d resultados%s | rodada 7: %4d resultados | entendeu: %s" % (q, len(r3["resultados"]), " [%s]" % r3.get("aviso") if r3.get("aviso") else "", len(r7["resultados"]), r3["intencao"]["subcategorias"][:2]))''')

md(r'''## Modo autônomo: auditoria, freio e reversão
**Auditoria automática por amostragem:** um LLM como juiz confere uma amostra das decisões aplicadas de cada lote (correto | incorreto) e estima a taxa de erro (intervalo de Wilson); **o erro real do lote** é medido pelo gabarito só agora, para comparar com a estimativa (critério: no máximo 1 ponto de diferença; com amostra pequena o intervalo é largo, e o notebook mostra a diferença **como ela é**). **Freio:** se o limite inferior do intervalo da taxa estimada passa do limite calibrado, o lote é pausado e revertido, e o agente volta a operar só com as decisões mais seguras. **Teste do freio:** um lote **propositalmente ruim** (as subcategorias embaralhadas, como um checkpoint embaralhado). **Teste de reversão:** aplica, reverte e compara o hash do catálogo. Sem a chave do LLM, a auditoria usa um **juiz por regras** (concordância com o NB ou o Laya sozinhos) só para exercitar o freio, e o painel marca isso.''')

code(r'''estado = pv.EstadoDoCatalogo(prods_all, [tx.categoria_do_vendedor(p.categoria_vendedor) for p in prods_all])
nome_juiz_regras = None
if LLM is not None and LLM.ativa:
    auditor = pv.Auditor(LLM, tx, TAMANHO_AUDITORIA)
else:
    nome_juiz_regras = "regras"
    def juiz_regras(pid, sub):
        i = pos[pid]
        return "correto" if sub in (SIN["nb_cats"][i], (SIN["laya"][i] or {}).get("sub")) else "incorreto"
    auditor = pv.Auditor(None, tx, TAMANHO_AUDITORIA, juiz_alternativo=juiz_regras)
print("auditor:", "LLM" if nome_juiz_regras is None else "juiz por regras (sem chave)", "| amostra por lote:", TAMANHO_AUDITORIA)
prods_por_id = {p.id: p for p in prods_all}

# ---- limite do freio: lotes BONS e um lote RUIM da validacao (so a validacao)
ids_val = spl["validacao"]
aplic_val = [i for i in ids_val if C.subs_E[pos[i]] is not None]
dec_val = {i: C.subs_E[pos[i]] for i in aplic_val}
taxas_bons = []
for k in range(0, min(len(aplic_val), 1500), 500):
    parte = {i: dec_val[i] for i in aplic_val[k:k + 500]}
    a = auditor.auditar("val-bom-%d" % k, prods_por_id, parte)
    if a.get("executada"):
        taxas_bons.append(a["taxa_estimada"])
ruim_val = pv.lote_propositalmente_ruim({i: dec_val[i] for i in aplic_val[:500]}, tx)
a_ruim = auditor.auditar("val-ruim", prods_por_id, ruim_val)
taxas_ruins = [a_ruim["taxa_estimada"]] if a_ruim.get("executada") else None
cal_freio = pv.calibrar_limite_do_freio(taxas_bons, taxas_ruins)
print("limite do freio (validacao):", cal_freio, "| lotes bons:", taxas_bons, "| lote ruim:", taxas_ruins)
fl.mesclar_json(ESC, {"freio": cal_freio, "juiz_da_auditoria": nome_juiz_regras or "llm"})
FREIO = pv.Freio(cal_freio["limite"], amostra_minima=min(30, TAMANHO_AUDITORIA))
JORNAL = pv.Jornal(pv.VERSAO_PIPELINE, str(SAIDA / "d" / "jornal-acoes.jsonl"))

# ---- lotes do TESTE: tres bons e um ruim no meio; cada um aplicado, auditado e, se o freio dispara, revertido
teste_aplic = [i for i in IDS_TESTE if C.subs_E[pos[i]] is not None]
lotes = []
for k in range(3):
    ids_k = teste_aplic[k * 5000:(k + 1) * 5000]
    lotes.append(("lote-%d-bom" % (k + 1), [{"id": i, "sub": C.subs_E[pos[i]], "ficha": dict(C.fichas_E[pos[i]] or {})} for i in ids_k], {i: C.subs_E[pos[i]] for i in ids_k}))
ids_r = teste_aplic[15000:20000]
ruim = pv.lote_propositalmente_ruim({i: C.subs_E[pos[i]] for i in ids_r}, tx)
lotes.insert(2, ("lote-ruim-injetado", [{"id": i, "sub": ruim[i], "ficha": dict(C.fichas_E[pos[i]] or {})} for i in ids_r], ruim))
hash_inicial = estado.hash()
seguro = lambda m: C.DEC_E[pos[m["id"]]].confianca_media >= 0.9      # depois do freio so entram as decisoes mais seguras (confianca media >= 0,9)
OPER = pv.operar_em_lotes(estado, lotes, prods_por_id, auditor, FREIO, JORNAL, filtro_seguro=seguro, log=print)
# o erro real de cada lote (MEDIDA: gabarito so agora)
ver_l = fl.verdade_para_medir(str(D_R8), set(IDS_TESTE), campos=("subcategoria",))
for item, (nome, mud, dec) in zip(OPER["lotes"], lotes):
    erro_real = sum(1 for i, s in dec.items() if s != ver_l[i]["subcategoria"]) / max(1, len(dec))
    item["erro_real"] = round(erro_real, 4)
    est_ = item["auditoria"].get("taxa_estimada")
    item["diferenca_estimado_menos_real_pontos"] = None if est_ is None else round(100 * (est_ - erro_real), 2)
    print("%-20s modo %-6s | erro real %5.1f%% | estimado %s | freio: %s | revertido: %s" % (nome, item["modo"], 100 * erro_real, "-" if est_ is None else "%.1f%%" % (100 * est_), item["freio"].get("disparou"), item["revertido"]))
ruim_item = next(i for i in OPER["lotes"] if i["lote"] == "lote-ruim-injetado")
rev = pv.teste_de_reversao(estado, [{"id": i, "sub": "fogoes", "ficha": {}} for i in teste_aplic[:3000]])
print("reversao:", rev)
JORNAL.fechar()
fl.gravar_json(SAIDA / "d" / "resultados-autonomo.json", {"operacao": OPER, "reversao": rev, "limite_do_freio": cal_freio, "juiz": nome_juiz_regras or "llm", "acoes_registradas": len(JORNAL.acoes),
                                                         "hash_inicial": hash_inicial[:16], "hash_final": estado.hash()[:16]})''')

md(r'''## Relatório de exceções
O que o time recebe **só para acompanhar** (ninguém aprova nada): o que foi mantido sem decisão, os lotes que o freio reverteu, as subcategorias criadas, adiadas e recusadas, os cadastros limítrofes aceitos e as respostas do LLM rejeitadas por estarem fora da lista.''')

code(r'''mantidos = [{"id": p.id, "titulo": p.titulo, "motivo": d.motivo} for p, d in zip(prods_all, C.DEC_E) if not d.aplicar]
REL = pv.relatorio_de_excecoes(mantidos, OPER, CRIACAO, exc_llm or exc_v3, LLM)
fl.gravar_json(SAIDA / "d" / "relatorio-de-excecoes.json", REL)
print("mantidos sem decisao: %d (%.1f%% dos produtos) | lotes revertidos: %s | criadas: %d | adiadas: %d | limitrofes aceitos: %d" % (REL["mantidos_sem_decisao"]["n"], 100 * REL["mantidos_sem_decisao"]["n"] / len(prods_all),
      REL["lotes_revertidos_pelo_freio"], len(REL["subcategorias_criadas"]), len(REL["subcategorias_adiadas"]), REL["cadastros_limitrofes_aceitos"]["n"]))
print("chamadas ao LLM neste bloco:", None if LLM is None else LLM.resumo_de_uso()["chamadas"])''')

md(r'''## Quanto cada peça ajuda
A mesma tabela com a **versão completa** e a versão **sem cada novidade** (sem vizinhos, sem estatística, sem a pergunta de energia, sem a ficha "categoria primeiro"), numa amostra estratificada de 20 mil produtos de teste. Ao lado da qualidade, o **consumo**: % de produtos e de atributos que vão ao LLM, chamadas por produto e **tokens estimados** (entrada + saída). As versões sem LLM têm consumo zero; a linha com LLM vem da amostra do bloco C (e dos itens novos acima). O "sem LLM" da versão completa é a própria versão E.''')

code(r'''AMOSTRA_ABL = 20000
idx_ab = fl.amostra_estratificada([prods_all[pos[i]] for i in IDS_TESTE], AMOSTRA_ABL, lambda p: tx.departamento_de(C.subs_E[pos[p.id]]) if C.subs_E[pos[p.id]] else "sem")
ids_ab = [IDS_TESTE[i] for i in idx_ab]
fl.gravar_json(SAIDA / "d" / "ids-ablacao.json", ids_ab)
idx_a = [pos[i] for i in ids_ab]
prods_a = [prods_all[i] for i in idx_a]
SIN_A = C.recorte(SIN, idx_a)
ABL = {}
def versao(nome, opcoes, usar_energia=True, fichar=True):
    res = pv.votacao_r8(prods_a, SIN_A["nb_cats"], SIN_A["nb_confs"], SIN_A["laya"], SIN_A["viz"] if opcoes.vizinhos else None, tx, C.PARAMS_R8, ["E"], llm=None, vizinhos=VIZ,
                        energia=fl.energia_para(prods_a, SIN_A, tx, C.USA_EN, LOTE) if (opcoes.pergunta_energia and usar_energia) else None)
    dec = [d for d, _ in res.decisoes["E"]]
    subs = [d.categoria if d.aplicar else None for d in dec]
    fichas, det, uso = fl.ficha_em_blocos(prods_a, subs, tx, codigo, LOTE, SIN_A["estados"], opcoes.sem("faixas_llm"), C.ESTAT, C.FATOR, None, C.FAIXA, None, str(CACHE / "fichas-ablacao"), "abl-" + nome, log=lambda *_: None, regra="v2")
    sec = fl.secundarias_para(prods_a, dec, SIN_A, C.PARES, C.esc["ambiguidade"]["margem"]) if opcoes.vizinhos else None
    f = SAIDA / "d" / ("ablacao-%s.jsonl" % nome); pv.escrever_jsonl(str(f), fl.previsoes_de_saneamento(prods_a, dec, MARCAS, fichas, sec))
    return avaliar_saneamento(str(f), str(SAIDA / "d" / "ids-ablacao.json"))
ABL["Completa (sem LLM)"] = versao("completa", OP)
ABL["sem vizinhos"] = versao("sem-vizinhos", OP.sem("vizinhos"))
ABL["sem estatistica"] = versao("sem-estatistica", OP.sem("estatistica"))
ABL["sem pergunta de energia"] = versao("sem-energia", OP.sem("pergunta_energia"))
ABL["sem 'categoria primeiro'"] = versao("sem-categoria-primeiro", OP.sem("ficha_categoria_primeiro"))
llm_c = fl.ler_json(SAIDA / "c" / "resultados-f2-llm.json")
def linha_abl(nome, r, consumo=None):
    r8 = r.get("rodada8") or {}; e = r8.get("erros_de_atributo", {})
    return {"versao": nome, "aplicados_%": r["aplicados_%"], "acerto_%": r["acerto_subcategoria_aplicados_%"], "pioras_%": r["pioras_%_do_total"], "nao_se_aplica_%": (e.get("nao_se_aplica") or {}).get("resolvidos_%"),
            "contradiz_%": (e.get("contradiz_anuncio") or {}).get("resolvidos_%"), "fora_normal_%": (e.get("fora_do_normal") or {}).get("resolvidos_%"), "estragados_%": (r8.get("estragados") or {}).get("%"),
            "nao_eletricos_no_filtro_%": (r8.get("no_filtro_indevido") or {}).get("%"), "ambiguidade_sec_prec_%": (r8.get("ambiguidade") or {}).get("secundaria_precisao_%"),
            "% produtos ao LLM": 0.0 if consumo is None else consumo.get("produtos_que_foram_ao_llm_%"), "% atributos ao LLM": 0.0 if consumo is None else consumo.get("atributos_ao_llm_%_dos_avaliados"),
            "chamadas por produto": 0.0 if consumo is None else consumo["consumo"]["chamadas_por_produto"], "tokens (entrada+saida)": 0 if consumo is None else consumo["consumo"]["tokens_total"]}
tabela_abl = [linha_abl(n, r) for n, r in ABL.items()]
if llm_c:
    tabela_abl.append(linha_abl("Completa + LLM (amostra de 10 mil da 1a execucao do bloco C; la o LLM tambem preenchia atributos, hoje desligado)", llm_c["com_llm"], llm_c))
print(pd.DataFrame(tabela_abl).to_string(index=False))
fl.gravar_json(SAIDA / "d" / "resultados-ablacao.json", {"versoes": ABL, "tabela": tabela_abl, "amostra": len(ids_ab)})''')

md(r'''## Resultado do bloco e critérios
Reúne as medidas que cada critério fixo pede em `resultados/d.json` (o bloco F lê e junta com o C e o E). **Duplicados e variações no catálogo existente:** precisão por par ≥ 95%. **Busca "fogão bivolt" e afins:** precisão nos 10 primeiros contra a linha de base **busca por palavra** (a busca de hoje). O que não foi medido fica "não medido".''')

code(r'''def frac(x):
    return None if x is None else x / 100.0
v3 = NOV.get("Agente v3 (com LLM nas disputas)") or NOV["Agente v3 (sem LLM)"]
r8n = v3["rodada8"]
b3, bt, b7 = BUSCA["Agente v3 (BuscaR8, catalogo organizado)"], BUSCA["Antes (busca por palavra)"], BUSCA["Rodada 7 (BuscaV2, catalogo organizado)"]
f2_v3, f2_base = (b3.get("f2") or {}).get("P@10_%"), (bt.get("f2") or {}).get("P@10_%")
aud_lotes = [i for i in OPER["lotes"] if i["auditoria"].get("executada")]
dif_max = max((abs(i["diferenca_estimado_menos_real_pontos"]) for i in aud_lotes if i["diferenca_estimado_menos_real_pontos"] is not None), default=None)
medidas = {
    "novos_duplicados": {"precisao": frac(v3["duplicados"]["precisao_%"]), "cobertura": frac(v3["duplicados"]["cobertura_%"])},
    "novos_variacoes": {"precisao": frac(r8n["variacoes"]["precisao_%"]), "cobertura": frac(r8n["variacoes"]["cobertura_%"])},
    "duplicados_existente": {"precisao_por_par": frac(GRUPOS["duplicados"]["precisao_por_par_%"])},
    "fora_taxonomia": {"precisao": frac(v3["fora_da_taxonomia"]["precisao_%"]), "cobertura": frac(v3["fora_da_taxonomia"]["cobertura_%"])},
    "fora_taxonomia_dep": {"acerto": frac(r8n["fora_da_taxonomia"]["departamento_provavel_certo_%"])},
    "invalidos": {"precisao": frac(v3["invalidos"]["precisao_%"]), "cobertura": frac(v3["invalidos"]["cobertura_%"]), "limitrofes_barrados_por_engano": frac(r8n["invalidos_limitrofes"]["%"])},
    "novos_classificados": {"acerto": frac(v3["acerto_subcategoria_dos_classificados_%"])},
    "subcategorias_novas": {"equivalentes_a_existente": equiv_a_existente, "precisao_dos_itens_alocados": (sum(pur) / len(pur)) if pur else None},
    "busca_sem_resposta": {"vazias_ou_com_aviso": frac((b3.get("sem_resposta") or {}).get("vazia_ou_aviso_%"))},
    "busca_com_modelo": {"precisao_top10": frac((b3.get("com_modelo") or {}).get("P@10_%"))},
    "busca_bivolt": {"ganho_precisao_top10_sobre_linha_de_base_pontos": None if (f2_v3 is None or f2_base is None) else round(f2_v3 - f2_base, 1)},
    "auditoria_automatica": {"distancia_do_erro_real_pontos": dif_max},
    "freio_automatico": {"lote_revertido": 1.0 if (ruim_item["revertido"] and ruim_item.get("hash_igual_ao_de_antes")) else 0.0} if aud_lotes else None,
    "reversao": {"hash_identico": 1.0 if rev["identico"] else 0.0},
}
medidas = {k: v for k, v in medidas.items() if v}
medidas["rodada7_mantida_busca"] = {"piora_pontos": max(0.0, (b7.get("todas") or {}).get("P@10_%", 0) - (b3.get("todas") or {}).get("P@10_%", 0))}
fl.gravar_json(SAIDA / "resultados" / "d.json", {"medidas": medidas, "juiz_da_auditoria": nome_juiz_regras or "llm", "chamadas_llm": None if LLM is None else LLM.resumo_de_uso()})
crit = fl.ler_json(TRAB / "r8-criterios.json")
for l in fl.checar_criterios(crit, medidas):
    if l["situacao"] != "nao medido":
        print("%-9s %-62s %s" % (l["situacao"].upper(), l["medida"][:62], l["medido"]))
fl.concluir_bloco(str(SAIDA), "d", {"chamadas_llm": None if LLM is None else LLM.chamadas, "juiz_da_auditoria": nome_juiz_regras or "llm", "pipeline": pv.VERSAO_PIPELINE})
print("bloco D concluido. Proximo: imagem_r8 (bloco E) e depois laya_r8_f_painel.")''')
