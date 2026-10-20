# -*- coding: utf-8 -*-
"""Monta os seis notebooks da PoC em ../../drive/ a partir dos arquivos r8_nb_*.py desta pasta.
Acrescenta a cada notebook (1) o quadro "perguntas da PoC que este notebook responde" e (2) o rotulo "(responde Pn)" nos titulos de secao que respondem a uma pergunta, tudo a
partir de drive/perguntas_poc.py. So escreve arquivos e confere a sintaxe das celulas (ast.parse); nao roda nada do agente.

Uso (de qualquer pasta):  python poc_laya/fontes/notebooks/construir_notebooks.py
"""
import ast
import importlib
import json
import os
import sys

aqui = os.path.dirname(os.path.abspath(__file__))
drive = os.path.normpath(os.path.join(aqui, "..", "..", "drive"))
sys.dont_write_bytecode = True
sys.path.insert(0, aqui)
sys.path.insert(0, drive)
import perguntas_poc as pq  # noqa: E402

NOTEBOOKS = [("r8_nb_a", pq.NB_A, "T4"), ("r8_nb_b", pq.NB_B, "L4"), ("r8_nb_c", pq.NB_C, "L4"), ("r8_nb_d", pq.NB_D, "L4"), ("r8_nb_e", pq.NB_E, "L4"), ("r8_nb_f", pq.NB_F, "")]

# titulo de secao (inicio da primeira linha da celula de texto) -> perguntas que a secao responde
TAGS = {
    pq.NB_C: {
        "## F1, passo 1 e 2": ["P1"], "## F1, passo 3": ["P1"], "## F2: dado errado do vendedor": ["P1", "P9"], "## Ambiguidade": ["P5"], "## Resultado do bloco e critérios": ["P1", "P5", "P9"],
        "# Correção de regra da ficha (F2)": ["P1"], "## Diagnóstico: quem trocou o valor do vendedor?": ["P1"], "## Regra nova (`v2`) e a medição de novo": ["P1"],
    },
    pq.NB_D: {
        "## Diagnóstico dos erros da rodada 7": ["P4"], "## Itens novos": ["P1", "P3", "P4"], "## Subcategorias novas e fora da taxonomia": ["P4"], "## Busca": ["P2", "P6"],
        "## Modo autônomo: auditoria, freio e reversão": ["P8"], "## Relatório de exceções": ["P8"], "## Quanto cada peça ajuda": ["P9"], "## Resultado do bloco e critérios": ["P1", "P2", "P3", "P4", "P6", "P8", "P9"],
    },
    pq.NB_E: {"## As cinco versões, no teste": ["P7"], "## Atributos visuais: cor e material": ["P7"], "## Custo, exemplos e resultado": ["P7"]},
    pq.NB_F: {"## `r8-resumo.html`": ["P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9"]},
}
SEM_PERGUNTA = ("> **Este notebook não responde diretamente a nenhuma pergunta da PoC**: prepara o que os outros usam (a base, o checkpoint do Laya e as travas que impedem medir com o modelo errado). "
                "As perguntas P1 a P9 são respondidas nos notebooks C, D e E e reunidas no F (veja o README da PoC).")

SO_JUNTA = ("> **Este notebook junta as respostas às nove perguntas da PoC (P1 a P9)**: a seção 0 do painel `r8-resumo.html` dá a resposta de cada uma, calculada só a partir dos critérios medidos, "
            "e diz em que notebook, seção e arquivo ela foi medida.")

total = 0
for modulo, arquivo, gpu in NOTEBOOKS:
    m = importlib.import_module(modulo)
    cells = []
    primeira_md = True
    for tipo, texto in m.CEL:
        if tipo == "md":
            linhas_ = texto.split("\n")
            for prefixo, ids in TAGS.get(arquivo, {}).items():
                if linhas_[0].startswith(prefixo) and "(responde" not in linhas_[0]:
                    linhas_[0] = linhas_[0] + " " + pq.tag(*ids)
            texto = "\n".join(linhas_)
            if primeira_md:
                texto = texto.rstrip("\n") + "\n\n" + (pq.quadro_markdown(arquivo) or (SO_JUNTA if arquivo == pq.NB_F else SEM_PERGUNTA))
                primeira_md = False
        else:
            try:
                ast.parse(texto)
            except SyntaxError as e:
                raise SystemExit("erro de sintaxe em %s, celula %d: %s\n%s" % (arquivo, len(cells) + 1, e, "\n".join(texto.split("\n")[max(0, (e.lineno or 1) - 3):(e.lineno or 1) + 2])))
        linhas = texto.strip("\n").split("\n")
        fonte = [l + "\n" for l in linhas[:-1]] + [linhas[-1]]
        if tipo == "md":
            cells.append({"cell_type": "markdown", "metadata": {}, "source": fonte})
        else:
            cells.append({"cell_type": "code", "metadata": {}, "execution_count": None, "outputs": [], "source": fonte})
    meta = {"colab": {"provenance": [], "name": arquivo, **({"gpuType": gpu} if gpu else {})}, "kernelspec": {"display_name": "Python 3", "name": "python3"}, "language_info": {"name": "python"}}
    if gpu:
        meta["accelerator"] = "GPU"
    nb = {"cells": cells, "nbformat": 4, "nbformat_minor": 4, "metadata": meta}
    with open(os.path.join(drive, arquivo), "w", encoding="utf-8") as f:
        json.dump(nb, f, ensure_ascii=False, indent=1)
    json.load(open(os.path.join(drive, arquivo), encoding="utf-8"))      # o JSON do notebook precisa abrir
    print("%-34s %3d celulas (%d de codigo)" % (arquivo, len(cells), sum(1 for c in cells if c["cell_type"] == "code")))
    total += len(cells)
print("total:", total, "celulas | notebooks em", drive)
