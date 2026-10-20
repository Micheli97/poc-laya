# -*- coding: utf-8 -*-
# Bloco F: painel r8-resumo.html e r8-painel-dados.json. Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, bloco F: o painel

Junta o que os blocos C, D e E gravaram no Drive (`saida-r8/resultados/`, `resultados-*.json`, `imagem-r8-resumo.json`) e gera:
- **`r8-painel-dados.json`**: só os números e os exemplos, para montar o painel fora do Colab.
- **`r8-resumo.html`**: o painel, com as versões nomeadas como na apresentação: **Antes**, **Classificador**, **Classificador + agente + Laya (rodada 7)** e **Agente v3** (esta rodada), a tabela **Atende / Não atende** de cada critério fixo (`r8-criterios.json`), F1, F2, itens novos, busca, modo autônomo, quanto cada peça ajuda (com o consumo de LLM ao lado), a trilha de imagem e as escolhas calibradas na validação.

**Atende / Não atende.** Cada linha do `r8-criterios.json` vira "Atende", "Não atende" ou "Não medido", **com o mesmo destaque** nos três casos: uma falha aparece tão visível quanto um acerto. Nada é recalculado aqui: o que um bloco não mediu fica "não medido". Se um bloco não rodou, o painel diz qual.

**Fotos.** O painel **não embute as fotos do ABO** (licença CC BY-NC 4.0, uso interno): só o id da imagem. Se o time confirmar que o painel fica interno, ligue `EMBUTIR_FOTOS = True` na célula do HTML para miniaturas de 128 px.

''' + BLOCOS + "\n\n" + AVISOS)

code(r'''import subprocess, sys, os, json, time, shutil, html, base64
from pathlib import Path
from collections import defaultdict
from google.colab import drive
drive.mount("/content/drive")
DRIVE = Path("/content/drive/MyDrive/laya-teste-r8")
TRAB = Path("/content/trabalho"); TRAB.mkdir(parents=True, exist_ok=True)
SAIDA = DRIVE / "saida-r8"
for n in ("pipeline_v3.py", "fluxo_r8.py", "perguntas_poc.py", "r8-criterios.json"):
    shutil.copy(DRIVE / n, TRAB / n)
sys.path.insert(0, str(TRAB))
import fluxo_r8 as fl
import perguntas_poc as pq
ESTADO = fl.ler_json(SAIDA / "estado.json")
print("blocos concluidos:", {b: (ESTADO.get(b) or {}).get("quando") for b in "abcde"})
FALTAM = [b for b in "abcde" if not (ESTADO.get(b) or {}).get("ok")]
if FALTAM:
    print("ATENCAO: blocos nao concluidos: %s. O painel sai com 'nao medido' nos criterios deles." % FALTAM)
CRIT = fl.ler_json(DRIVE / "r8-criterios.json")
RES = {b: fl.ler_json(SAIDA / "resultados" / ("%s.json" % b)) for b in "cde"}
MEDIDAS = {}
for b in "cde":
    MEDIDAS.update((RES[b] or {}).get("medidas") or {})
# a busca "rodada 7 mantida" e a categoria "rodada 7 mantida" sao duas medidas do mesmo criterio: vale a pior
r7c, r7d = MEDIDAS.get("rodada7_mantida"), MEDIDAS.pop("rodada7_mantida_busca", None)
if r7c and r7d:
    MEDIDAS["rodada7_mantida"] = {"piora_pontos": max(r7c["piora_pontos"], r7d["piora_pontos"])}
elif r7d:
    MEDIDAS["rodada7_mantida"] = r7d
LINHAS = fl.checar_criterios(CRIT, MEDIDAS)
POR_ID_CRIT = {l["id"]: l for l in LINHAS}
VEREDITOS = {p["id"]: pq.veredito(p, POR_ID_CRIT) for p in pq.PERGUNTAS}      # a resposta de cada pergunta da PoC, so a partir dos criterios medidos
from collections import Counter
print(Counter(l["situacao"] for l in LINHAS))
for l in LINHAS:
    print("%-10s %-70s %s" % (l["situacao"].upper(), l["medida"][:70], l["falhas"] or ""))''')

md(r'''## Dados do painel
Reúne, por versão, as medidas de categoria e de atributos, a árvore (F1), os itens novos, a busca, o modo autônomo, a ablação, a imagem e as escolhas calibradas. `r8-painel-dados.json` guarda isso sem HTML.''')

code(r'''def ler(*partes):
    return fl.ler_json(SAIDA.joinpath(*partes))
F2 = ler("c", "resultados-f2.json"); ARV = ler("c", "resultados-arvore.json"); MIG = ler("c", "resultados-migracao.json"); F2LLM = ler("c", "resultados-f2-llm.json")
NOV = ler("d", "resultados-novos.json"); BUS = ler("d", "resultados-busca.json"); GRP = ler("d", "resultados-grupos.json"); AUT = ler("d", "resultados-autonomo.json"); ABL = ler("d", "resultados-ablacao.json")
SUB = ler("d", "subcategorias-criadas.json"); EXC = ler("d", "relatorio-de-excecoes.json"); IMG = ler("imagem-r8-resumo.json"); ESC = ler("r8-escolhas-val.json")
EXC_C = ler("c", "exemplos-c.json"); CORR = ler("c", "resultados-f2-correcao.json"); DIAGF2 = ler("c", "diagnostico-f2.json")
VERSOES = ["Antes", "Classificador", "Classificador + agente + Laya (rodada 7)", "Agente v3"]

def cat_linha(r):
    r8 = r.get("rodada8") or {}
    e = r8.get("erros_de_atributo") or {}
    g = lambda d, k: (d or {}).get(k)
    return {"aplicados_%": r.get("aplicados_%"), "acerto_dos_aplicados_%": r.get("acerto_subcategoria_aplicados_%"), "pioras_%": r.get("pioras_%_do_total"), "corretos_no_catalogo_%": r.get("corretos_no_catalogo_%"),
            "atributos_acerto_%": (r.get("atributos") or {}).get("acerto_inferiveis_%"), "nao_se_aplica_resolvido_%": g(e.get("nao_se_aplica"), "resolvidos_%"), "contradiz_resolvido_%": g(e.get("contradiz_anuncio"), "resolvidos_%"),
            "fora_do_normal_resolvido_%": g(e.get("fora_do_normal"), "resolvidos_%"), "sem_pista_nao_inventado_%": g(e.get("sem_pista"), "resolvidos_%"), "estragados_%": g(r8.get("estragados"), "%"),
            "nao_eletricos_no_filtro_%": g(r8.get("no_filtro_indevido"), "%"), "controles_voltagem_removida_%": g(r8.get("controles_eletricos"), "%")}
VERS = {v: cat_linha((F2.get("versoes") or {}).get(v) or {}) for v in VERSOES if (F2.get("versoes") or {}).get(v)}
def busca_linha(r):
    g = lambda k, m: (r.get(k) or {}).get(m)
    return {"todas_P@10_%": g("todas", "P@10_%"), "f2_P@10_%": g("f2", "P@10_%"), "com_modelo_P@10_%": g("com_modelo", "P@10_%"), "sem_resposta_vazias_%": g("sem_resposta", "vazia_ou_aviso_%"), "corrigidas_P@10_%": g("corrigidas", "P@10_%")}
PAINEL = {
    "perguntas_da_poc": [{**{k: v for k, v in p.items() if k != "onde"}, "onde": [list(o) for o in p["onde"]], "veredito": VEREDITOS[p["id"]]} for p in pq.PERGUNTAS],
    "pipeline": "pipeline_v3 (rodada 8)", "gerado_em": time.strftime("%Y-%m-%d %H:%M:%S"), "blocos": ESTADO, "blocos_que_faltam": FALTAM,
    "criterios": LINHAS, "versoes_categoria_e_atributos": VERS,
    "f1_arvore": {"versao_usada": ARV.get("versao_final"), "versoes": {k: {"teste": v.get("teste"), "todos": v.get("todos")} for k, v in (ARV.get("resultados") or {}).items()}, "arvore_nova": MIG.get("arvore_nova"),
                  "migracao": {k: cat_linha(v) for k, v in (MIG.get("versoes") or {}).items()}},
    "f2_com_llm": F2LLM, "itens_novos": NOV, "catalogo_existente": GRP, "busca": {k: busca_linha(v) for k, v in BUS.items()}, "autonomo": AUT, "ablacao": ABL, "subcategorias_novas": SUB, "relatorio_de_excecoes": EXC,
    "imagem": {k: IMG.get(k) for k in ("licenca", "dados", "modelos", "versoes_no_teste", "por_ruido", "versao5_na_amostra", "atributos", "custo", "exemplos", "medidas_dos_criterios")} if IMG else None,
    "correcao_de_regra_f2": CORR, "diagnostico_f2": DIAGF2, "escolhas_da_validacao": ESC, "exemplos": EXC_C, "tempos_medidos": {b: (ESTADO.get(b) or {}).get("quando") for b in "abcde"}, "treino_do_laya": (ESTADO.get("b") or {}),
    "estimativas": "os tempos do LEIA-ME sao ESTIMATIVAS; os tempos medidos estao em tempos_medidos e nos resultados de cada bloco; projecoes para 100 mil produtos ou 10 mil itens novos por dia estao marcadas como PROJECAO"}
(SAIDA / "r8-painel-dados.json").write_text(json.dumps(PAINEL, ensure_ascii=False, indent=1, default=str), encoding="utf-8")
print("r8-painel-dados.json gravado:", round((SAIDA / "r8-painel-dados.json").stat().st_size / 1024), "KB")''')

md(r'''## `r8-resumo.html`
O painel de uma página só, com tabelas e barras em HTML puro (sem biblioteca externa), legível no celular. Cada seção começa pela pergunta que responde. O que **não** foi medido aparece como "não medido", e o que **não atende** aparece em vermelho, com o mesmo peso visual de um acerto.''')

code(r'''EMBUTIR_FOTOS = False       # so ligue depois de confirmar com o time que o painel fica INTERNO (as fotos do ABO sao CC BY-NC 4.0)
esc = lambda x: html.escape("" if x is None else str(x))
def num(x, casas=1):
    if x is None:
        return "—"
    try:
        return ("%." + str(casas) + "f") % float(x) if not isinstance(x, int) else str(x)
    except Exception:
        return esc(x)
def barra(v, maximo=100.0, cor="var(--azul)"):
    if v is None:
        return "<span class='nm'>não medido</span>"
    p = max(0.0, min(100.0, 100.0 * float(v) / maximo))
    return "<div class='barra'><div style='width:%.1f%%;background:%s'></div><span>%s</span></div>" % (p, cor, num(v))
def tabela(cab, linhas, classes=""):
    h = "<div class='rolagem'><table class='%s'><thead><tr>%s</tr></thead><tbody>" % (classes, "".join("<th>%s</th>" % esc(c) for c in cab))
    for l in linhas:
        h += "<tr>%s</tr>" % "".join("<td>%s</td>" % (c if isinstance(c, str) and c.startswith("<") else esc(c)) for c in l)
    return h + "</tbody></table></div>"
def fmt_medido(m):
    if not m:
        return "—"
    return ", ".join("%s = %s" % (k, num(v, 3) if isinstance(v, float) else esc(v)) for k, v in m.items())
def fmt_meta(l):
    ps = ["%s ≥ %s" % (k, v) for k, v in (l["minimos"] or {}).items()] + ["%s ≤ %s" % (k, v) for k, v in (l["maximos"] or {}).items()]
    return "; ".join(ps)
SELO = {"atende": "<span class='selo ok'>ATENDE</span>", "nao atende": "<span class='selo no'>NÃO ATENDE</span>", "nao medido": "<span class='selo nm2'>NÃO MEDIDO</span>"}
cont = Counter(l["situacao"] for l in LINHAS)
H = []
CORES_VEREDITO = {"sim": "ok", "nao": "no", "parcial": "nm2", "nao_medido": "nm2"}
linhas_q = []
for p in pq.PERGUNTAS:
    v = VEREDITOS[p["id"]]
    resumo = "%d atendem, %d não atendem, %d não medidos" % (len(v["atendem"]), len(v["nao_atendem"]), len(v["nao_medidos"]))
    onde = "<br>".join("<b>%s</b> › %s<br><code>%s</code>" % (esc(nb), esc(sec), esc(arq)) for nb, sec, arq in p["onde"])
    linhas_q.append(["<b>%s</b>" % p["id"], esc(p["pergunta"]), "<span class='selo %s'>%s</span><br>%s" % (CORES_VEREDITO[v["status"]], esc(pq.ROTULO_STATUS[v["status"]].split(":")[0]), esc(resumo)), onde, esc(p["esperado"])])
H.append("<h2>0. As perguntas da PoC e as respostas</h2><p class='p'>Cada resposta vem <b>só</b> dos critérios fixos medidos (seção 1). “SIM” exige todos os critérios da pergunta medidos e atendidos; um critério não medido nunca conta como atendido.</p>")
H.append(tabela(["#", "Pergunta", "Resposta", "Onde é respondida (notebook › seção › arquivo)", "O que se espera"], linhas_q))
H.append("<h2>1. Critérios fixos: Atende / Não atende</h2><p class='p'>%s</p>" % " ".join("%s <b>%d</b>" % (SELO[k], v) for k, v in cont.items()))
H.append(tabela(["Critério", "Meta", "Medido", "Situação"], [[l["medida"], fmt_meta(l), fmt_medido(l["medido"]) + (" (" + "; ".join(l["falhas"]) + ")" if l["falhas"] else ""), SELO[l["situacao"]]] for l in LINHAS], "criterios"))

# ---- as quatro versoes
if VERS:
    cols = ["aplicados_%", "acerto_dos_aplicados_%", "pioras_%", "corretos_no_catalogo_%", "atributos_acerto_%", "nao_se_aplica_resolvido_%", "contradiz_resolvido_%", "fora_do_normal_resolvido_%", "sem_pista_nao_inventado_%", "estragados_%", "nao_eletricos_no_filtro_%"]
    H.append("<h2>2. As quatro versões (categoria e dado errado do vendedor, nos produtos de teste)</h2><p class='p'>Antes = como o vendedor cadastrou; Classificador = só o Naive Bayes; Classificador + agente + Laya = o voto-b da rodada 7; Agente v3 = esta rodada.</p>")
    H.append(tabela(["Versão"] + [c.replace("_%", " %").replace("_", " ") for c in cols], [[v] + [num(VERS[v].get(c)) for c in cols] for v in VERS], "versoes"))
# ---- correcao de regra da ficha (F2), feita depois da primeira execucao
if CORR:
    cols = list(next(iter(CORR["f2"].values())).keys())
    H.append("<h2>2b. Correção de regra da ficha (F2), feita <u>depois</u> da primeira execução</h2><p class='p'><b>É uma correção de regra feita depois de ver os resultados do bloco C.</b> %s Medição: %s Limiares novos: %s</p>" % (esc(CORR["regra_depois"]) + ".", esc(CORR["medicao"]) + ".", esc(CORR["limiares_novos"]) + "."))
    H.append("<h3>F2 (Agente v3, nos produtos de teste): antes e depois</h3>" + tabela(["Medida"] + list(CORR["f2"].keys()), [[c.replace("_%", " %").replace("_", " ")] + [num(CORR["f2"][k].get(c)) for k in CORR["f2"]] for c in cols]))
    H.append("<h3>Migração (com o atributo do nó legado): antes e depois</h3>" + tabela(["Medida"] + list(CORR["migracao"].keys()), [[c.replace("_%", " %").replace("_", " ")] + [num(CORR["migracao"][k].get(c)) for k in CORR["migracao"]] for c in cols]))
    ca = CORR.get("criterios_antes_e_depois") or {}
    H.append("<h3>Critérios afetados</h3>" + tabela(["Critério", "Antes (1ª execução)", "Depois"], [[k, fmt_medido((v or {}).get("antes")), fmt_medido((v or {}).get("depois")) if (v or {}).get("depois") else "não medido (LLM nos atributos desligado por decisão)"] for k, v in ca.items()]))
    H.append("<p class='p'>Faixas de confiança do Laya (teste, só para registro; o LLM nos atributos está desligado): %s</p>" % esc(json.dumps(CORR.get("faixas_do_laya_no_teste"), ensure_ascii=False)))
    for rot, d in (DIAGF2.get("diagnostico") or {}).items():
        linhas = []
        for g, dd in d["contagens"].items():
            for et, cts in dd.items():
                linhas.append([rot.split(" (")[0], g, et, cts.get("trocado", 0), cts.get("removido", 0), cts.get("valor_novo_certo", 0), cts.get("valor_novo_errado", 0)])
        if linhas:
            H.append("<h3>Diagnóstico da regra antiga: %s</h3>" % esc(rot) + tabela(["Recorte", "Grupo", "Etapa que alterou", "Trocados", "Removidos", "Valor novo certo", "Valor novo errado"], linhas))
# ---- F1
if ARV.get("versoes") or ARV.get("resultados"):
    linhas = []
    for nome, r in (ARV.get("resultados") or {}).items():
        t = r["teste"]
        linhas.append([nome, "%s / %s" % (num(t["nos_atributo"]["precisao_%"]), num(t["nos_atributo"]["cobertura_%"])), "%s / %s" % (num(t["nos_lixo"]["precisao_%"]), num(t["nos_lixo"]["cobertura_%"])),
                       num(t["destino_categoria_e_duplicado_acerto_%"]), num(t["atributo"]["destino_e_atributo_certos_%"]), num(r["todos"]["arvore_resultante"]["subcategorias_resultantes"], 0), num(r["todos"]["arvore_resultante"]["excesso_sobre_a_taxonomia_%"])])
    H.append("<h2>3. F1: a árvore de ~1.700 nós</h2><p class='p'>O agente consegue diagnosticar a árvore antiga, propor a enxuta e migrar os produtos recuperando o atributo escondido no nó? Nos 30% de nós de teste. “baseline_nome” é o nó mapeado pelo nome; “teto_perfeito” usa o gabarito dos nós (não é um agente). Versão usada na migração: <b>%s</b>.</p>" % esc(ARV.get("versao_final")))
    H.append(tabela(["Versão", "Nós-atributo prec/cob %", "Nós de lixo prec/cob %", "Destino %", "Atributo (destino e valor) %", "Subcategorias resultantes", "Excesso sobre a taxonomia %"], linhas))
    if MIG.get("versoes"):
        H.append("<h3>Migração dos produtos (teste)</h3>")
        H.append(tabela(["Versão", "Aplicados %", "Acerto %", "Pioras %", "Atributo do nó recuperado %"], [[k, num(v.get("aplicados_%")), num(v.get("acerto_subcategoria_aplicados_%")), num(v.get("pioras_%_do_total")),
                                                                                                           num(((v.get("rodada8") or {}).get("atributos_do_no_legado") or {}).get("%"))] for k, v in MIG["versoes"].items()]))
# ---- itens novos
if NOV.get("versoes"):
    linhas = []
    for nome, r in NOV["versoes"].items():
        r8 = r.get("rodada8") or {}
        linhas.append([nome, "%s / %s" % (num(r["duplicados"]["precisao_%"]), num(r["duplicados"]["cobertura_%"])), "%s / %s" % (num((r8.get("variacoes") or {}).get("precisao_%")), num((r8.get("variacoes") or {}).get("cobertura_%"))),
                       "%s / %s" % (num(r["fora_da_taxonomia"]["precisao_%"]), num(r["fora_da_taxonomia"]["cobertura_%"])), num((r8.get("fora_da_taxonomia") or {}).get("departamento_provavel_certo_%")),
                       "%s / %s" % (num(r["invalidos"]["precisao_%"]), num(r["invalidos"]["cobertura_%"])), num((r8.get("invalidos_limitrofes") or {}).get("%")), num(r["acerto_subcategoria_dos_classificados_%"])])
    H.append("<h2>4. Itens novos</h2><p class='p'>Duplicados (métrica oficial: o original exato), variações, fora da taxonomia, inválidos e os limítrofes barrados por engano. A rodada 7 foi: duplicados 75,0 / 77,2; fora da taxonomia 91,5 / 19,7.</p>")
    H.append(tabela(["Versão", "Duplicados prec/cob %", "Variações prec/cob %", "Fora da taxonomia prec/cob %", "Depto provável %", "Inválidos prec/cob %", "Limítrofes barrados %", "Classificados %"], linhas))
    if NOV.get("diagnostico_r7"):
        d = NOV["diagnostico_r7"]
        H.append("<h3>Diagnóstico dos erros de duplicado da rodada 7</h3>" + tabela(["Previstos", "Exatos", "Outra cópia do grupo", "Variação tratada como duplicado", "Produto diferente"], [[d.get("duplicados_previstos"), d.get("exatos"), d.get("outra_copia_do_grupo"), d.get("variacao_tratada_como_duplicado"), d.get("produto_diferente")]]))
    else:
        H.append("<p class='p nm'>Diagnóstico dos erros da rodada 7: <b>não executado</b> (faltam as previsões de itens novos da rodada 7 no Drive).</p>")
    if GRP:
        H.append("<h3>Catálogo existente: duplicados e variações propostos</h3>" + tabela(["", "Pares previstos", "Precisão por par %", "Pares verdadeiros", "Cobertura %"], [["duplicados", GRP["duplicados"]["pares_previstos"], num(GRP["duplicados"]["precisao_por_par_%"]), GRP["duplicados"]["pares_verdadeiros"], num(GRP["duplicados"]["cobertura_%"])],
                                                                                                   ["variações", GRP["variacoes"]["pares_previstos"], num(GRP["variacoes"]["precisao_por_par_%"]), GRP["variacoes"]["pares_verdadeiros"], num(GRP["variacoes"]["cobertura_%"])]]))
    if SUB.get("criacao"):
        c = SUB["criacao"]
        H.append("<h3>Subcategorias novas</h3><p class='p'>criadas <b>%d</b>, adiadas por volume <b>%d</b>, recusadas por equivalência <b>%d</b>; pureza média dos itens alocados %s%%; equivalentes a uma existente: %s.</p>" % (len(c["criadas"]), len(c["adiadas"]), len(c["recusadas"]), num(SUB.get("pureza_media_%")), SUB.get("equivalentes_a_existente")))
        H.append(tabela(["Nome sugerido", "Departamento", "Itens", "Tipo no gabarito (medida)", "Pureza"], [[g["nome"], g["departamento"], len(g["itens"]), g.get("tipo_majoritario_no_gabarito"), num(g.get("pureza"), 2)] for g in c["criadas"]]))
# ---- busca
if BUS:
    cols = ["todas_P@10_%", "f2_P@10_%", "com_modelo_P@10_%", "sem_resposta_vazias_%", "corrigidas_P@10_%"]
    H.append("<h2>5. Busca</h2><p class='p'>Consultas de índice par (as de índice ímpar calibraram a correção de digitação). “F2” = fogão bivolt e afins; “sem resposta” = voltam vazias ou com aviso; “com modelo” = capinha iphone 15 e afins.</p>")
    H.append(tabela(["Versão"] + cols, [[n] + [num(busca_linha(r).get(c)) for c in cols] for n, r in BUS.items()]))
# ---- autonomo
if AUT.get("operacao"):
    linhas = [[l["lote"], l["modo"], l["mudancas"], num(100 * (l.get("erro_real") or 0)), "—" if l["auditoria"].get("taxa_estimada") is None else num(100 * l["auditoria"]["taxa_estimada"]), "sim" if l["freio"].get("disparou") else "não", "sim" if l["revertido"] else "não"] for l in AUT["operacao"]["lotes"]]
    H.append("<h2>6. Modo autônomo: auditoria, freio e reversão</h2><p class='p'>Juiz da auditoria: <b>%s</b>. Limite do freio (validação): %s. Reversão: hash %s." % (esc(AUT.get("juiz")), esc((AUT.get("limite_do_freio") or {}).get("limite")), "IDÊNTICO" if (AUT.get("reversao") or {}).get("identico") else "DIFERENTE"))
    H.append(tabela(["Lote", "Modo", "Mudanças", "Erro real %", "Erro estimado %", "Freio disparou", "Revertido"], linhas))
    if EXC:
        H.append("<h3>Relatório de exceções (só para acompanhar)</h3><p class='p'>mantidos sem decisão: <b>%s</b> · lotes revertidos: %s · subcategorias criadas: %d · adiadas: %d · cadastros limítrofes aceitos: %s</p>" % (
            EXC["mantidos_sem_decisao"]["n"], esc(EXC["lotes_revertidos_pelo_freio"]), len(EXC["subcategorias_criadas"]), len(EXC["subcategorias_adiadas"]), EXC["cadastros_limitrofes_aceitos"]["n"]))
# ---- ablacao e consumo
if ABL.get("tabela"):
    cols = [c for c in ABL["tabela"][0].keys() if c != "versao"]
    H.append("<h2>7. Quanto cada peça ajuda, e quanto custa em LLM</h2><p class='p'>Amostra de %s produtos de teste. A última linha (com LLM) vem da amostra do bloco C.</p>" % ABL.get("amostra"))
    H.append(tabela(["Versão"] + [c.replace("_", " ") for c in cols], [[l["versao"]] + [num(l.get(c)) for c in cols] for l in ABL["tabela"]]))
# ---- imagem
if IMG:
    H.append("<h2>8. Texto + imagem (trilha com fotos reais, ABO)</h2><p class='p'>%s. Quantização do modelo grande: <b>%s</b> (%s).</p>" % (esc(IMG["licenca"]), esc(IMG["modelos"]["quantizacao_do_modelo_grande"]), esc(IMG["modelos"]["gpu"])))
    H.append(tabela(["Versão", "n", "Aplicados %", "Acerto dos aplicados %", "Pioras %", "Acerto final do catálogo %"], [[v["versao"], v["n"], num(v["aplicados_%"]), num(v["acerto_dos_aplicados_%"]), num(v["pioras_%"]), num(v["acerto_final_do_catalogo_%"])] for v in IMG["versoes_no_teste"]]))
    pr = IMG.get("por_ruido") or {}
    if pr.get("titulo_sem_nome"):
        H.append("<h3>Título sem o nome do produto (o ruído principal)</h3>" + tabela(["Versão", "Acerto final %"], [[v["versao"], num(v["acerto_final_do_catalogo_%"])] for v in pr["titulo_sem_nome"]]))
    H.append("<h3>Versão 5 (modelo grande em tudo) na amostra</h3>" + tabela(["Versão", "n", "Acerto final %"], [[v["versao"], v["n"], num(v["acerto_final_do_catalogo_%"])] for v in IMG["versao5_na_amostra"]]))
    a = IMG["atributos"]
    H.append("<h3>Cor</h3>" + tabela(["Medida", "Valor"], [["cor ausente: acerto no preenchido %", num(a["cor_ausente"]["acerto_no_preenchido_%"])], ["cor ausente: cobertura %", num(a["cor_ausente"]["cobertura_%"])],
                                                          ["contradiz a foto: detectada e corrigida %", num(a["cor_contradiz"]["detectadas_e_corrigidas_%"])], ["cores certas estragadas %", num(a["cores_certas_estragadas"]["%"])]]))
    c = IMG["custo"]
    H.append("<h3>Custo</h3><p class='p'>%s %% dos produtos vão ao modelo grande · SigLIP 2: %s imagens/s · modelo grande: %s s por foto · <b>PROJEÇÃO</b>: %s h para 100 mil produtos, %s h para 10 mil itens novos por dia.</p>" % (
        num(c["uso_do_modelo_grande_%"]), num(c["siglip2_imagens_por_s"]), num(c["modelo_grande_s_por_foto"], 2), num(c["PROJECAO_horas_para_100_mil_produtos"], 2), num(c["PROJECAO_horas_para_10_mil_itens_novos_por_dia"], 2)))
# ---- exemplos
ex = PAINEL["exemplos"] or {}
if ex:
    H.append("<h2>9. Exemplos lado a lado</h2>")
    if ex.get("churrasqueiras_e_fogoes"):
        H.append("<h3>Churrasqueiras e fogões a lenha: como chegaram e como ficaram</h3>" + tabela(["Título", "Categoria do vendedor", "Atributos do vendedor", "Subcategoria certa", "Decidiu", "Voltagem na ficha"], [[e["titulo"], e["categoria_do_vendedor"], str(e["atributos_do_vendedor"]), e["subcategoria_certa"], e["o_agente_decidiu"], e["voltagem_na_ficha"]] for e in ex["churrasqueiras_e_fogoes"]]))
    if ex.get("sem_pista"):
        H.append("<h3>Sem pista: o vendedor errou e nada no anúncio contradiz (o certo é não inventar)</h3>" + tabela(["Título", "Atributo", "O vendedor disse", "Valor certo (gabarito, só para medir)", "O agente deixou"], [[e["titulo"], e["chave"], e["atributo_do_vendedor"], e["valor_certo (gabarito)"], e["o_agente_deixou"]] for e in ex["sem_pista"]]))
    if ex.get("nos"):
        H.append("<h3>Nós da árvore antiga: diagnóstico, destino e motivo</h3>" + tabela(["Nó", "Caminho", "Diagnóstico", "Destino", "Atributo", "Motivo", "Juiz"], [[e["no"], e["caminho"], e["diagnostico"], e["destino"], str(e["atributo"]), e["motivo"], "aprovado" if e["aprovado_pelo_juiz"] else "rejeitado"] for e in ex["nos"]]))
if IMG and IMG.get("exemplos"):
    H.append("<h3>Imagem: o título ruim e o que cada versão decidiu</h3>")
    def miniatura(imagem_id):
        if not EMBUTIR_FOTOS:
            return imagem_id
        try:
            from PIL import Image
            import io
            im = Image.open(DRIVE / "abo-recorte" / "imagens" / (imagem_id + ".jpg")).convert("RGB"); im.thumbnail((128, 128))
            b = io.BytesIO(); im.save(b, "JPEG", quality=80)
            return "<img alt='%s' src='data:image/jpeg;base64,%s'>" % (esc(imagem_id), base64.b64encode(b.getvalue()).decode())
        except Exception:
            return imagem_id
    H.append(tabela(["Foto", "Título ruim", "Categoria certa", "Só texto", "Texto + imagem"], [[miniatura(e["imagem_id"]), e["titulo_ruim"], e["categoria_certa"], e["o_texto_sozinho_decidiu"], e["texto_mais_imagem_decidiu"]] for e in IMG["exemplos"]]))
# ---- escolhas da validacao
H.append("<h2>10. O que foi calibrado na validação (gravado antes do teste)</h2><p class='p'>Conteúdo de <code>r8-escolhas-val.json</code>.</p>")
H.append("<pre>%s</pre>" % esc(json.dumps({k: (v.get("escolha") if isinstance(v, dict) and "escolha" in v else v) for k, v in ESC.items()}, ensure_ascii=False, indent=1, default=str)[:6000]))
H.append("<h2>11. Avisos</h2><ul><li><b>Dados sintéticos.</b> Com dados reais de cliente, enviar produtos ao Colab, ao Drive, ao Kaggle ou ao Ollama Cloud precisa de validação do time.</li><li>A trilha de imagem usa o ABO (CC BY-NC 4.0): avaliação interna, sem redistribuir fotos.</li><li>Chaves só em Colab Secrets. A chave do Ollama colada numa conversa da rodada 6 deve ser rotacionada.</li><li>Tempos e projeções estão marcados como estimativa ou projeção.</li></ul>")
CSS = """:root{--bg:#fafafa;--fg:#1c1c1c;--mu:#666;--azul:#2b6cb0;--ok:#1a7f37;--no:#b42318;--card:#fff;--bd:#ddd}
@media (prefers-color-scheme: dark){:root{--bg:#111;--fg:#eee;--mu:#aaa;--azul:#6ea8fe;--ok:#4ade80;--no:#f87171;--card:#1b1b1b;--bd:#333}}
body{background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,sans-serif;margin:0;padding:16px;max-width:1200px;margin:auto}
h1{font-size:24px}h2{margin-top:32px;border-bottom:1px solid var(--bd);padding-bottom:4px}h3{margin-top:20px}.p{color:var(--mu)}.nm{color:var(--mu)}
.rolagem{overflow-x:auto}table{border-collapse:collapse;width:100%;background:var(--card);font-size:13px}th,td{border:1px solid var(--bd);padding:4px 8px;text-align:left;vertical-align:top}th{background:rgba(127,127,127,.12)}
.selo{display:inline-block;padding:2px 8px;border-radius:10px;font-weight:700;font-size:12px;color:#fff}.selo.ok{background:var(--ok)}.selo.no{background:var(--no)}.selo.nm2{background:#6b7280}
.barra{position:relative;background:rgba(127,127,127,.2);height:16px;min-width:100px}.barra div{height:100%}.barra span{position:absolute;left:4px;top:-1px;font-size:11px}pre{background:var(--card);border:1px solid var(--bd);padding:8px;overflow:auto;font-size:12px}"""
corpo = "".join(H)
doc = "<!doctype html><html lang='pt-BR'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Rodada 8: o painel</title><style>" + CSS + "</style></head><body>" \
      "<h1>Rodada 8: agente no padrão de mercado</h1><p class='p'>Árvore de 1.700 nós (F1), dado errado do vendedor (F2), itens novos, busca, modo autônomo e texto + imagem. Versões: <b>Antes</b>, <b>Classificador</b>, <b>Classificador + agente + Laya</b> (rodada 7) e <b>Agente v3</b> (esta rodada). " \
      "Dados sintéticos; trilha de imagem com o ABO (CC BY-NC 4.0, avaliação interna). Gerado em " + time.strftime("%Y-%m-%d %H:%M") + ".</p>" + corpo + "</body></html>"
(SAIDA / "r8-resumo.html").write_text(doc, encoding="utf-8")
print("r8-resumo.html gravado:", round(len(doc) / 1024), "KB em", SAIDA)
from IPython.display import HTML, display
display(HTML(doc))
fl.concluir_bloco(str(SAIDA), "f", {"criterios": dict(cont), "arquivos": ["r8-resumo.html", "r8-painel-dados.json"]})''')
