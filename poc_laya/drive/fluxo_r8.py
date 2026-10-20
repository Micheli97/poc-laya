# -*- coding: utf-8 -*-
"""
fluxo_r8.py: o encadeamento das pecas do pipeline_v3 para os notebooks da rodada 8 (sinais, votacao, ficha, previsoes) e a checagem dos criterios.
Nao tem logica nova de agente: so chama o pipeline_v3. NUNCA le o gabarito, exceto as funcoes `verdade_para_medir` e `rotulos_para_calibrar`, que sao a leitura
declarada: a primeira so para MEDIR (depois da previsao gravada), a segunda so nos splits de treino e validacao.
"""
from __future__ import annotations

import json
import os
import time
from collections import defaultdict

import numpy as np
import pipeline_v3 as pv


# ---- estado dos blocos no Drive -----------------------------------------------------------------------------------------------
def ler_json(caminho, padrao=None):
    caminho = str(caminho)
    if os.path.exists(caminho):
        with open(caminho, "r", encoding="utf-8") as f:
            return json.load(f)
    return {} if padrao is None else padrao


def gravar_json(caminho, obj):
    caminho = str(caminho)
    os.makedirs(os.path.dirname(os.path.abspath(caminho)), exist_ok=True)
    tmp = caminho + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1, default=str)
    os.replace(tmp, caminho)


def mesclar_json(caminho, novo: dict):
    """Acrescenta chaves a um JSON do Drive sem apagar o resto (usado em r8-escolhas-val.json, que cresce a cada bloco)."""
    atual = ler_json(caminho)
    atual.update(novo)
    gravar_json(caminho, atual)
    return atual


def exigir_bloco(saida, bloco: str):
    est = ler_json(os.path.join(saida, "estado.json"))
    if not (est.get(bloco) or {}).get("ok"):
        raise RuntimeError("O bloco %s ainda nao terminou (ou falhou): rode-o antes deste. Estado: %s" % (bloco.upper(), json.dumps(est)[:400]))
    return est


def concluir_bloco(saida, bloco: str, info: dict):
    est = ler_json(os.path.join(saida, "estado.json"))
    est[bloco] = dict(info, ok=True, quando=time.strftime("%Y-%m-%d %H:%M:%S"))
    gravar_json(os.path.join(saida, "estado.json"), est)


# ---- conjuntos de ids ----------------------------------------------------------------------------------------------------------
def ids_de_teste(d_dados: str, destino: str) -> list:
    """Os ids em que se MEDE: tudo que nao e treino (treino_20000) nem validacao. Grava a lista para o avaliador (--ids-arquivo)."""
    sp = ler_json(os.path.join(d_dados, "splits.json"))
    fora = set(sp["treino_20000"]) | set(sp["validacao"])
    ids = [m["id"] for m in pv.ler_jsonl(os.path.join(d_dados, "produtos.jsonl")) if m["id"] not in fora]
    gravar_json(destino, ids)
    return ids


def amostra_estratificada(produtos: list, n: int, chave_fn, semente: int = 7) -> list:
    """n indices de `produtos`, proporcionais aos grupos de `chave_fn(produto)` (deterministica pelo hash do id)."""
    grupos = defaultdict(list)
    for i, p in enumerate(produtos):
        grupos[chave_fn(p)].append(i)
    out = []
    total = len(produtos)
    for k, idxs in sorted(grupos.items(), key=lambda kv: str(kv[0])):
        quota = max(1, round(n * len(idxs) / total))
        ordem = sorted(idxs, key=lambda i: pv.hash6(("%s|%d" % (produtos[i].id, semente)).encode("utf-8")))
        out.extend(ordem[:quota])
    return sorted(out)[:n]


# ---- leituras do gabarito (declaradas) -----------------------------------------------------------------------------------------
def rotulos_para_calibrar(d_dados: str, split: str, campos=("subcategoria",)) -> dict:
    """SO treino e validacao (o pipeline_v3 recusa golden, teste_rodadas e novos_teste). E a leitura que a CALIBRACAO usa."""
    return pv.rotulos_do_split(d_dados, split, campos)


def verdade_para_medir(d_dados: str, ids: set, arquivo: str = "produtos.jsonl", campos=("subcategoria", "atributos", "atributos_inferiveis", "erros_de_atributo", "subcategorias_aceitas",
                                                                                       "duplicado_de", "variacao_de", "tipo_novo", "categoria_legada_status")) -> dict:
    """LEITURA DO GABARITO SO PARA MEDIR, depois que as previsoes ja foram gravadas (analises por faixa e por tipo, erro real de um lote auditado). Nada que entre aqui volta para
    o pipeline."""
    out = {}
    for m in pv.ler_jsonl(os.path.join(d_dados, arquivo)):
        if m["id"] in ids:
            g = m.get("gabarito") or {}
            out[m["id"]] = {c: g.get(c) for c in campos}
            out[m["id"]]["inferiveis"] = set(g.get("atributos_inferiveis") or [])
    return out


# ---- sinais, votacao, ficha e previsoes ---------------------------------------------------------------------------------------
def treinar_nb(d_dados: str, tx, split: str, destino: str):
    """Naive Bayes treinado SO nos ids de um split de treino da base indicada (a unica leitura do gabarito do pipeline: o rotulo desses ids)."""
    arq = os.path.join(destino, "treino-%s.jsonl" % split)
    os.makedirs(destino, exist_ok=True)
    pv.gerar_treino_v2(d_dados, split, arq)
    return pv.NaiveBayes.treinar(pv.ler_treino(arq, tx), 42)


def calcular_sinais(produtos: list, tx, nb, lote, indice_viz, cache_dir: str, prefixo: str, bloco: int = 5000, log=print) -> dict:
    """Os sinais baratos de uma lista de produtos: NB, Laya (cascata de 3 niveis em lotes, com retomada) e vizinhos (embeddings, com cache em disco)."""
    estados, titulos = pv.estados_dos_produtos(produtos)
    docs = [(p.titulo, p.atributos, pv.texto_longo(p)) for p in produtos]
    nb_cats, nb_confs = nb.prever_lote(docs)
    laya = None
    if lote is not None:
        cache = pv.CacheJsonl(os.path.join(cache_dir, prefixo + "-laya.jsonl"))
        laya = pv.cascata_com_cache(lote, tx, [p.id for p in produtos], estados, cache, bloco=bloco, log=log)
        cache.fechar()
    viz = vetores = None
    if indice_viz is not None:
        vetores = indice_viz.emb.codificar([pv.texto_para_embedding(p) for p in produtos], os.path.join(cache_dir, prefixo + "-emb.npy"), log)
        viz = indice_viz.consultar(vetores, [p.id for p in produtos])
    return {"estados": estados, "titulos": titulos, "nb_cats": nb_cats, "nb_confs": nb_confs, "laya": laya, "viz": viz, "vetores": vetores}


def energia_para(produtos: list, sinais: dict, tx, usa_energia: dict, lote) -> list:
    """O voto da pergunta "usa energia eletrica?" nos produtos em que as candidatas dos sinais baratos diferem em energia (churrasqueira a carvao x eletrica...)."""
    cand = []
    for i, p in enumerate(produtos):
        catv = tx.categoria_do_vendedor(p.categoria_vendedor)
        rs = pv.resultados_baratos_r8(catv, sinais["nb_cats"][i], sinais["nb_confs"][i], sinais["laya"][i] if sinais["laya"] else None, sinais["viz"][i] if sinais["viz"] else None)
        c = pv.candidatas_em_disputa(rs)
        par = pv.par_eletrico_em_disputa(c, usa_energia, tx)
        cand.append(list(par) if par else None)
    return pv.voto_de_energia(lote, sinais["estados"], cand, usa_energia)


def decidir_categorias(produtos: list, sinais: dict, tx, params: dict, conjunto: str, opcoes, llm=None, indice_viz=None, lote=None, usa_energia: dict = None,
                       no_legado: list = None, permitir_llm=None, cache_llm=None, log=print):
    """A votacao da rodada 8 para um conjunto (E: sem LLM; F: com LLM nas disputas; N: migracao com o no legado). Liga a pergunta de energia e os vizinhos conforme as opcoes."""
    energia = energia_para(produtos, sinais, tx, usa_energia, lote) if (opcoes.pergunta_energia and lote is not None and usa_energia) else None
    viz = sinais["viz"] if opcoes.vizinhos else None
    return pv.votacao_r8(produtos, sinais["nb_cats"], sinais["nb_confs"], sinais["laya"], viz, tx, params, [conjunto], llm=llm if opcoes.llm_restrito else None, vizinhos=indice_viz,
                         no_legado=no_legado, energia=energia, permitir_llm=permitir_llm, cache_llm=cache_llm, log=log)


def previsoes_de_saneamento(produtos: list, decisoes: list, marcas, fichas: list = None, secundarias: list = None, rotulo_nao_aplicado: bool = True, detalhes: list = None) -> list:
    """As linhas do avaliador (modo saneamento). `decisoes`: lista de Decisao; `fichas`: por produto (ou None); `secundarias`: por produto (ou None); `detalhes`: os detalhes do
    ficha_r8, de onde vem `atributos_originais` (o valor que o vendedor informou, lido so do campo dele pelo proprio codigo: o ORIGINAL guardado, que o avaliador usa para saber
    se o agente manteve, tirou ou trocou o valor; nao vem do gabarito)."""
    out = []
    for i, (p, d) in enumerate(zip(produtos, decisoes)):
        m = marcas.resolver(p)
        linha = pv.previsao_saneamento(p, d, m, None if fichas is None else fichas[i])
        if secundarias is not None and secundarias[i] is not None and d.aplicar:
            linha["subcategoria_secundaria"] = secundarias[i]
        if detalhes is not None and d.aplicar and detalhes[i] is not None:
            linha["atributos_originais"] = dict(detalhes[i].get("vendedor_canonico") or {})
        out.append(linha)
    return out


def secundarias_para(produtos: list, decisoes: list, sinais: dict, pares: dict, margem: float) -> list:
    out = []
    for i, d in enumerate(decisoes):
        if not d.aplicar or sinais["viz"] is None:
            out.append(None)
            continue
        sec, _ = pv.subcategoria_secundaria(d.categoria, sinais["viz"][i], sinais["nb_cats"][i], pares, margem)
        out.append(sec)
    return out


# ---- criterios -----------------------------------------------------------------------------------------------------------------
def checar_criterios(criterios: dict, medidas: dict) -> list:
    """Atende / Nao atende por criterio. `medidas`: criterio -> {chave: valor}, com as MESMAS chaves de "minimos" e "maximos" do r8-criterios.json (frações 0-1 ou pontos, como la).
    Criterio sem medida fica "nao medido" (e nunca conta como atende)."""
    saida = []
    for c in criterios["criterios"]:
        m = medidas.get(c["id"])
        linha = {"id": c["id"], "medida": c["medida"], "minimos": c.get("minimos"), "maximos": c.get("maximos"), "medido": m, "situacao": "nao medido", "falhas": []}
        if m:
            falhas = []
            for k, v in (c.get("minimos") or {}).items():
                if m.get(k) is None:
                    falhas.append("%s nao medido" % k)
                elif m[k] < v:
                    falhas.append("%s %.4g < %.4g" % (k, m[k], v))
            for k, v in (c.get("maximos") or {}).items():
                if m.get(k) is None:
                    falhas.append("%s nao medido" % k)
                elif m[k] > v:
                    falhas.append("%s %.4g > %.4g" % (k, m[k], v))
            linha["falhas"] = falhas
            linha["situacao"] = "atende" if not falhas else ("nao medido" if all(f.endswith("nao medido") for f in falhas) else "nao atende")
        saida.append(linha)
    return saida


def pct(x):
    """Percentual do avaliador (0-100, ou None) -> fracao (0-1, ou None)."""
    return None if x is None else x / 100.0


def ficha_em_blocos(produtos: list, subs: list, tx, codigo, lote, estados: list, opcoes, estat, fator: float, llm, faixa, atributos_do_no, cache_dir: str, prefixo: str,
                    bloco: int = 10000, jornal=None, cache_llm=None, log=print, regra: str = "v1", sugestoes_cache: dict = None):
    """pv.ficha_r8 em blocos de `bloco` produtos, gravando cada bloco no Drive (retoma se a sessao cair). Devolve (fichas, detalhes, uso somado). O jornal so registra os blocos
    executados agora (os que vieram do cache nao tem as acoes)."""
    n = len(produtos)
    fichas, detalhes, uso_total = [None] * n, [None] * n, None
    os.makedirs(cache_dir, exist_ok=True)
    for ini in range(0, n, bloco):
        fim = min(n, ini + bloco)
        arq = os.path.join(cache_dir, "%s-%07d.json" % (prefixo, ini))
        if os.path.exists(arq):
            d = ler_json(arq)
            f, det, uso = d["fichas"], d["detalhes"], d["uso"]
        else:
            f, det, uso = pv.ficha_r8(produtos[ini:fim], subs[ini:fim], tx, codigo, lote, None if estados is None else estados[ini:fim], opcoes, estat, fator, llm, faixa, atributos_do_no, cache_llm, jornal, prefixo, log=log,
                                      regra=regra, sugestoes_cache=sugestoes_cache)
            gravar_json(arq, {"fichas": [None if x is None else dict(x) for x in f], "detalhes": det, "uso": uso})
        fichas[ini:fim], detalhes[ini:fim] = f, det
        if uso_total is None:
            uso_total = json.loads(json.dumps(uso))
        else:
            for k, v in uso.items():
                if isinstance(v, dict):
                    for kk, vv in v.items():
                        uso_total[k][kk] += vv
                elif isinstance(v, list):
                    uso_total[k].extend(v)
                else:
                    uso_total[k] += v
        log("  ficha: %d/%d produtos" % (fim, n))
    return fichas, detalhes, uso_total


class Ctx:
    """Os objetos que os blocos d e f reconstroem a partir do que o bloco c gravou no Drive (escolhas da validacao, caches do Laya e dos embeddings, fichas)."""


def carregar_contexto(saida: str, cache: str, trab: str, d_r8: str, ck_drive: str, hash_esperado: str, batch: int = 32, com_laya: bool = True, log=print) -> Ctx:
    """Reconstroi, sem recalibrar nada, o contexto do bloco c: taxonomia, Laya (conferindo o hash do checkpoint), NB, indice de vizinhos, sinais dos 100 mil (do cache), a regra da votacao
    gravada em r8-escolhas-val.json, a decisao de categoria do conjunto E e a ficha do Agente v3 (do cache do bloco c). Tambem a versao da rodada 7 (conjunto B) para comparar."""
    import shutil
    c = Ctx()
    c.pv, c.d_r8 = pv, d_r8
    c.tx = pv.Taxonomia(os.path.join(d_r8, "taxonomia.json"))
    c.OP = pv.Opcoes()
    pv.ativar_limpeza_de_entrada(True)
    esc = ler_json(os.path.join(saida, "r8-escolhas-val.json"))
    c.esc = esc
    if "votacao_E" not in esc:
        raise RuntimeError("r8-escolhas-val.json nao tem a calibracao do bloco c (votacao_E): rode o bloco C inteiro antes")
    c.PARAMS_R8 = {"E": pv.parametros_da_calibracao(esc["votacao_E"]), "F": pv.parametros_da_calibracao(esc["votacao_E"])}
    if "votacao_N" in esc:
        c.PARAMS_R8["N"] = pv.parametros_da_calibracao(esc["votacao_N"])
    c.PARAMS_R7 = pv.parametros_da_escolha(os.path.join(trab, "r6-escolha-val.json"))
    c.FATOR, c.FAIXA = esc["estatistica"]["fator"], tuple(esc["faixa_de_duvida"])
    c.LOTE = None
    if com_laya:
        nome = "laya3-ft-5000"
        local = os.path.join(trab, "ck", nome)
        shutil.copytree(os.path.join(ck_drive, nome), local, dirs_exist_ok=True)
        if pv.sha256_arquivo(os.path.join(local, "model.safetensors")) != hash_esperado:
            raise RuntimeError("TRAVA: o checkpoint do Drive NAO e o que o bloco B treinou e conferiu")
        c.AG = pv.carregar_laya(local)
        c.LOTE = pv.LayaLote(c.AG, batch_size=batch)
        ctrl = pv.controle_departamento(d_r8, c.tx, 120)
        pv.verificar_checkpoint(nome, local, c.tx, ctrl, c.AG, None)
    c.prods_all = pv.ler_produtos(os.path.join(d_r8, "produtos.jsonl"))
    c.pos = {p.id: i for i, p in enumerate(c.prods_all)}
    c.spl = ler_json(os.path.join(d_r8, "splits.json"))
    c.ARQ_TESTE = os.path.join(saida, "c", "ids-teste.json")
    c.IDS_TESTE = ler_json(c.ARQ_TESTE) if os.path.exists(c.ARQ_TESTE) else ids_de_teste(d_r8, c.ARQ_TESTE)
    c.idx_val = [c.pos[i] for i in c.spl["validacao"]]
    c.prods_val = [c.prods_all[i] for i in c.idx_val]
    c.MARCAS = pv.ResolvedorMarca.do_catalogo(pv.ler_jsonl(os.path.join(d_r8, "produtos.jsonl")), 5)
    c.codigo = pv.FichaCodigo(c.tx)
    c.USA_EN = pv.usa_energia_por_sub(c.tx)
    c.PARES = pv.pares_de_sobreposicao(os.path.join(d_r8, "taxonomia.json"))
    c.NB = treinar_nb(d_r8, c.tx, "treino_5000", os.path.join(trab, "nb"))
    c.treino5 = [c.prods_all[c.pos[i]] for i in c.spl["treino_5000"]]
    rot5 = {i: v["subcategoria"] for i, v in rotulos_para_calibrar(d_r8, "treino_5000").items()}
    c.EMB = pv.Embeddings(device="cuda")
    c.VIZ = pv.IndiceVizinhos(c.EMB, c.tx, c.treino5, rot5, os.path.join(cache, "emb-treino5000-r8.npy"))
    c.SIN = calcular_sinais(c.prods_all, c.tx, c.NB, c.LOTE, c.VIZ, cache, "r8-todos", log=log)

    def recorte(sin, idx):
        return {k: (None if v is None else (v[idx] if isinstance(v, np.ndarray) else [v[i] for i in idx])) for k, v in sin.items()}
    c.recorte = recorte
    c.SIN_VAL = recorte(c.SIN, c.idx_val)
    c.rot_val = {i: v["subcategoria"] for i, v in rotulos_para_calibrar(d_r8, "validacao").items()}
    res_e = decidir_categorias(c.prods_all, c.SIN, c.tx, c.PARAMS_R8, "E", c.OP, llm=None, indice_viz=c.VIZ, lote=c.LOTE, usa_energia=c.USA_EN)
    c.DEC_E = [d for d, _ in res_e.decisoes["E"]]
    c.subs_E = [d.categoria if d.aplicar else None for d in c.DEC_E]
    fichas_cod = [None if s is None else c.codigo.extrair_parcial(p, s) for p, s in zip(c.prods_all, c.subs_E)]
    c.ESTAT = pv.EstatisticaCatalogo()
    for p, s, f in zip(c.prods_all, c.subs_E, fichas_cod):
        if s is not None:
            c.ESTAT.adicionar(s, f, p.atributos)
    # a ficha do Agente v3 usa a regra v2 (correcao pos-primeira-execucao: o valor do vendedor so muda por "nao se aplica" ou por valor explicito no texto; LLM nos atributos desligado)
    sug = sugestoes_do_cache(os.path.join(cache, "fichas"), ("v3-E", "migracao-N"))
    c.fichas_E, c.det_E, c.uso_E = ficha_em_blocos(c.prods_all, c.subs_E, c.tx, c.codigo, c.LOTE, c.SIN["estados"], c.OP.sem("faixas_llm"), c.ESTAT, c.FATOR, None, c.FAIXA, None,
                                                   os.path.join(cache, "fichas"), "v3-E2", log=log, regra="v2", sugestoes_cache=sug if sug else None)
    # a versao da rodada 7 (voto-b com os parametros da rodada 6 e a ficha por codigo + Laya >= 0,9)
    res_b = pv.votacao_em_lote(c.prods_all, c.SIN["nb_cats"], c.SIN["nb_confs"], c.SIN["laya"], c.tx, c.PARAMS_R7, ["B"], llm=None, log=lambda *_: None)
    c.DEC_B = [d for d, _ in res_b.decisoes["B"]]
    c.subs_B = [d.categoria if d.aplicar else None for d in c.DEC_B]
    cache_fb = pv.CacheJsonl(os.path.join(cache, "ficha-r7-todos.jsonl"))
    c.fichas_B, _ = pv.ficha_laya_com_cache(c.LOTE, c.tx, c.codigo, c.prods_all, c.SIN["estados"], c.subs_B, cache_fb, bloco=5000, confianca_minima=0.9, log=lambda *_: None)
    cache_fb.fechar()
    return c


# ---- correcao de regra da ficha (F2) feita depois da primeira execucao do bloco C ------------------------------------------------
def sugestoes_do_cache(cache_dir: str, prefixos) -> dict:
    """As sugestoes do Laya para atributos que a primeira execucao do bloco C ja calculou e gravou em blocos (cache/fichas/<prefixo>-<n>.json): {(id, chave): (valor, confianca)}.
    Servem para refazer a ficha com outra regra SEM chamar o Laya de novo (a pergunta de cada atributo so depende do texto do produto)."""
    import glob
    out = {}
    for pre in prefixos:
        for arq in sorted(glob.glob(os.path.join(cache_dir, pre + "-*.json"))):
            for s_ in (ler_json(arq).get("uso") or {}).get("sugestoes_laya") or []:
                out.setdefault((s_["id"], s_["chave"]), (s_["valor"], s_["confianca"]))
    return out


def subs_de_previsoes(arquivo: str, produtos: list):
    """As categorias JA DECIDIDAS pela primeira execucao (o jsonl de saneamento gravado no Drive): lista de subcategoria (ou None) na ordem dos produtos, e a secundaria."""
    por_id = {}
    for m in pv.ler_jsonl(arquivo):
        por_id[m["id"]] = m
    subs, sec = [], []
    for p in produtos:
        m = por_id.get(p.id) or {}
        subs.append(m.get("subcategoria") if m.get("status") == "APLICADO" else None)
        sec.append(m.get("subcategoria_secundaria"))
    return subs, sec


def contexto_f2(saida: str, cache: str, trab: str, d_r8: str, log=print) -> Ctx:
    """O contexto minimo para refazer SO a parte de atributos (F2): sem GPU e sem Laya. Le do Drive as categorias ja decididas, as escolhas da validacao e as sugestoes do Laya em
    cache; recalcula a estatistica do catalogo (por codigo) e o resto e leitura."""
    c = Ctx()
    c.pv = pv
    c.tx = pv.Taxonomia(os.path.join(d_r8, "taxonomia.json"))
    c.OP = pv.Opcoes()
    pv.ativar_limpeza_de_entrada(True)
    c.esc = ler_json(os.path.join(saida, "r8-escolhas-val.json"))
    c.FATOR, c.FAIXA = c.esc["estatistica"]["fator"], tuple(c.esc["faixa_de_duvida"])
    c.prods_all = pv.ler_produtos(os.path.join(d_r8, "produtos.jsonl"))
    c.pos = {p.id: i for i, p in enumerate(c.prods_all)}
    c.spl = ler_json(os.path.join(d_r8, "splits.json"))
    c.ARQ_TESTE = os.path.join(saida, "c", "ids-teste.json")
    c.IDS_TESTE = ler_json(c.ARQ_TESTE) if os.path.exists(c.ARQ_TESTE) else ids_de_teste(d_r8, c.ARQ_TESTE)
    c.idx_val = [c.pos[i] for i in c.spl["validacao"]]
    c.prods_val = [c.prods_all[i] for i in c.idx_val]
    c.MARCAS = pv.ResolvedorMarca.do_catalogo(pv.ler_jsonl(os.path.join(d_r8, "produtos.jsonl")), 5)
    c.codigo = pv.FichaCodigo(c.tx)
    c.PARES = pv.pares_de_sobreposicao(os.path.join(d_r8, "taxonomia.json"))
    c.subs_E, c.SEC = subs_de_previsoes(os.path.join(saida, "c", "saneamento-Agente_v3.jsonl"), c.prods_all)
    c.subs_N, _ = subs_de_previsoes(os.path.join(saida, "c", "saneamento-migracao-N.jsonl"), c.prods_all)
    log("categorias reaproveitadas do Drive: %d aplicadas no Agente v3, %d na migracao" % (sum(s is not None for s in c.subs_E), sum(s is not None for s in c.subs_N)))
    c.ESTAT = pv.EstatisticaCatalogo()
    for p, s_ in zip(c.prods_all, c.subs_E):
        if s_ is not None:
            c.ESTAT.adicionar(s_, c.codigo.extrair_parcial(p, s_), p.atributos)
    c.SUG = sugestoes_do_cache(os.path.join(cache, "fichas"), ("v3-E", "migracao-N"))
    log("sugestoes do Laya em cache: %d" % len(c.SUG))
    # o atributo do no legado de cada produto (da arvore nova publicada pela primeira execucao)
    nova = ler_json(os.path.join(saida, "c", "arvore-nova-v1.json"))
    mapa = nova.get("mapeamento") or {}
    c.ATR_NO = {}
    for p in c.prods_all:
        m = mapa.get(p.categoria_legada)
        if m and m.get("destino") and m.get("atributo"):
            c.ATR_NO[p.id] = (m["atributo"]["chave"], m["atributo"]["valor"])
    c.NOS = {n["id"]: n for n in ler_json(os.path.join(d_r8, "arvore_legada.json"))}
    c.MAPA_NOS = mapa
    return c


def diagnosticar_alteracoes(c, produtos: list, subs: list, fichas: list, detalhes: list, verdade: dict, exemplos_por_etapa: int = 10) -> dict:
    """DIAGNOSTICO (so mede; o gabarito `verdade` vem de verdade_para_medir depois das previsoes). Para cada atributo cujo valor o agente ALTEROU em relacao ao que o vendedor informou
    (o valor canonico lido so do campo do vendedor), diz qual ETAPA trocou (anuncio, no legado, Laya, LLM, estatistica ou outra) e se o valor novo e o certo.
    Recortes: sem_pista e fora_do_normal (erros injetados); estragados (o vendedor estava certo e o agente trocou ou tirou: a definicao corrigida); e estragados_definicao_antiga
    (a do avaliador na primeira execucao: todo atributo certo e inferivel que o agente deixou como nao_informado ou com outro valor, tenha o vendedor informado ou nao).
    Devolve contagens por etapa e exemplos (titulo, valor do vendedor, valor novo, valor certo e o no legado do produto)."""
    from collections import Counter
    grupos_ = ("sem_pista", "fora_do_normal", "estragados", "estragados_definicao_antiga")
    cont = {g: defaultdict(Counter) for g in grupos_}
    exemplos = {g: defaultdict(list) for g in grupos_}
    erros_por_id = {}
    for pid, v in verdade.items():
        for e in v.get("erros_de_atributo") or []:
            erros_por_id.setdefault(pid, {})[e["chave"]] = e

    def registrar(g, etapa, tipo, certo, ex):
        cont[g][etapa][tipo] += 1
        cont[g][etapa]["valor_novo_certo" if certo else "valor_novo_errado"] += 1
        if len(exemplos[g][etapa]) < exemplos_por_etapa:
            exemplos[g][etapa].append(ex)

    for p, sub, f, det in zip(produtos, subs, fichas, detalhes):
        if sub is None or f is None or det is None or p.id not in verdade:
            continue
        real_todos, inferiveis = verdade[p.id]["atributos"], verdade[p.id]["inferiveis"]
        erros_do_produto = erros_por_id.get(p.id) or {}
        no = c.NOS.get(p.categoria_legada) or {}
        for chave_, final in f.items():
            informou = chave_ in det["informou"]
            orig = det["vendedor_canonico"].get(chave_)
            etapa = det["origem"].get(chave_, "?")
            real = real_todos.get(chave_)
            removido = final in (NAO_INFORMADO_, NAO_SE_APLICA_)
            certo = (not removido) and pv.mesmo_valor(final, real)
            rotulo = "codigo_nao_leu_o_valor_do_vendedor" if etapa == "vendedor_ilegivel" else etapa
            ex = {"id": p.id, "titulo": p.titulo, "chave": chave_, "valor_do_vendedor": orig, "valor_novo": final, "valor_certo": real, "etapa": rotulo, "no_legado": no.get("caminho"),
                  "atributo_do_no": c.ATR_NO.get(p.id), "valor_novo_e_o_certo": bool(certo)}
            # definicao ANTIGA de "estragado": atributo certo (inferivel, fora dos erros) que ficou nao_informado ou com outro valor
            if chave_ in inferiveis and chave_ not in erros_do_produto and real is not None and (removido or not certo):
                etapa_antiga = "nao_preenchido_pelo_agente (o vendedor nao informou)" if (removido and not informou) else rotulo
                registrar("estragados_definicao_antiga", etapa_antiga, "removido" if removido else "trocado", False, ex)
            if not informou:
                continue
            alterado = removido or not pv.mesmo_valor(final, orig)
            if not alterado:
                continue
            tipo = "removido" if removido else "trocado"
            erro = erros_do_produto.get(chave_)
            if erro and erro["tipo"] == "sem_pista":
                registrar("sem_pista", rotulo, tipo, certo, ex)
            if erro and erro["tipo"] == "fora_do_normal":
                registrar("fora_do_normal", rotulo, tipo, certo, ex)
            if orig is not None and pv.mesmo_valor(orig, real):
                registrar("estragados", rotulo, tipo, False, ex)
    return {"contagens": {g: {e: dict(c_) for e, c_ in d.items()} for g, d in cont.items()}, "exemplos": {g: {e: l for e, l in d.items()} for g, d in exemplos.items()}}


NAO_INFORMADO_, NAO_SE_APLICA_ = pv.NAO_INFORMADO, pv.NAO_SE_APLICA
