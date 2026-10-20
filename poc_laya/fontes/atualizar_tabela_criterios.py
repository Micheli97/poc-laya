# -*- coding: utf-8 -*-
"""Preenche, no README.md da PoC, a tabela de criterios por pergunta (entre os marcadores <!--CRITERIOS-->), a partir de drive/r8-criterios.json e drive/perguntas_poc.py.
Roda de qualquer pasta:  python poc_laya/fontes/atualizar_tabela_criterios.py"""
import json
import os
import sys

aqui = os.path.dirname(os.path.abspath(__file__))
raiz = os.path.normpath(os.path.join(aqui, ".."))
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(raiz, "drive"))
import perguntas_poc as pq  # noqa: E402

crit = {c["id"]: c for c in json.load(open(os.path.join(raiz, "drive", "r8-criterios.json"), encoding="utf-8"))["criterios"]}


def meta(c):
    ps = ["%s ≥ %s" % (k, v) for k, v in (c.get("minimos") or {}).items()] + ["%s ≤ %s" % (k, v) for k, v in (c.get("maximos") or {}).items()]
    return "; ".join(ps).replace("_", " ")


linhas = ["| Pergunta | Critério (id) | O que mede | Meta fixa | Medido no notebook |", "|---|---|---|---|---|"]
# em que notebook cada criterio e MEDIDO (grava a medida em resultados/c.json, d.json ou e.json)
MEDIDO_EM = {}
for cid in ("no_atributo", "no_lixo", "no_destino", "arvore_tamanho", "migracao", "atributo_no_legado", "nao_se_aplica", "contradiz_anuncio", "fora_do_normal", "sem_pista_inventado", "atributos_estragados",
            "nao_eletricos_no_filtro", "faixa_duvida_llm", "mantidos_sem_decisao", "ambiguidade", "consumo_llm_categoria", "consumo_llm_total"):
    MEDIDO_EM[cid] = "C"
for cid in ("novos_duplicados", "novos_variacoes", "duplicados_existente", "fora_taxonomia", "fora_taxonomia_dep", "invalidos", "novos_classificados", "subcategorias_novas", "busca_sem_resposta",
            "busca_com_modelo", "busca_bivolt", "auditoria_automatica", "freio_automatico", "reversao"):
    MEDIDO_EM[cid] = "D"
for cid in ("img_titulo_sem_nome", "img_teste_inteiro", "img_cor_nao_dita", "img_cor_contradiz", "img_modelo_grande"):
    MEDIDO_EM[cid] = "E"
MEDIDO_EM["rodada7_mantida"] = "C (categoria) e D (busca): vale a pior"
notebook_do = {}
for p in pq.PERGUNTAS:
    for cid in p["criterios"]:
        notebook_do[(p["id"], cid)] = MEDIDO_EM[cid]
for p in pq.PERGUNTAS:
    for cid in p["criterios"]:
        c = crit[cid]
        linhas.append("| **%s** | `%s` | %s | %s | %s |" % (p["id"], cid, c["medida"], meta(c), notebook_do[(p["id"], cid)]))
tabela = "\n".join(linhas)

readme = os.path.join(raiz, "README.md")
s = open(readme, encoding="utf-8").read()
ini, fim = "<!--CRITERIOS-->", "<!--/CRITERIOS-->"
a, b = s.index(ini) + len(ini), s.index(fim)
open(readme, "w", encoding="utf-8", newline="\n").write(s[:a] + "\n" + tabela + "\n" + s[b:])
usados = {cid for p in pq.PERGUNTAS for cid in p["criterios"]}
sem = sorted(set(crit) - usados)
print("tabela escrita: %d linhas | criterios sem pergunta: %s" % (len(linhas) - 2, sem))
