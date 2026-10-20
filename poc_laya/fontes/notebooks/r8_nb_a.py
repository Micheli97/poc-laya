# -*- coding: utf-8 -*-
# Bloco A: bases e paridade. Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS, AMBIENTE

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, bloco A: bases e paridade

**Licença e privacidade do conjunto de dados desta rodada.** Este bloco usa só a **base sintética** (`catalogo-v2`). A trilha de imagem (bloco E) usa o catálogo público **Amazon Berkeley Objects (ABO), licença CC BY-NC 4.0**: só para **avaliação interna**, sem redistribuir as imagens nem usá-las em produção e sem colocá-las no repositório; **confirme com o time se o uso interno está ok antes de rodar o bloco E**.

**O que este bloco faz**
1. **Portão do hash (primeira célula).** Gera a base original (semente 20261008) **sem** a opção `--rodada8` e compara o sha256 de `produtos.jsonl`, `splits.json`, `taxonomia.json`, `novos.jsonl` e `buscas.jsonl` com os da rodada 6. Se qualquer um diferir, **nada mais roda**: o gerador mudou e a rodada 8 não pode ser comparada com a 7.
2. Instala o ambiente e gera a **base nova** (`--rodada8`, semente 20261013, ruído 1,5) com os ruídos F2, a árvore legada de ~1.700 nós, a ambiguidade, as buscas e os itens novos novos.
3. **Paridade:** o `pipeline_v3` com todas as novidades desligadas (`Opcoes.v2()`) e o Naive Bayes escrito à mão reproduz os números da rodada 6/7 na base original (tolerância de 0,5 ponto no saneamento e na ficha; 1 ponto nos itens novos e na busca). Se falhar, **para**. Em seguida repete com o **Naive Bayes da biblioteca scikit-learn**, que é o que esta PoC usa, e mostra o efeito da troca (informativo).

''' + BLOCOS + "\n\n" + AVISOS)

code(r'''# ===== PORTAO DO HASH: se falhar, nada mais roda =====
import subprocess, sys, os, json, hashlib, shutil, time
from pathlib import Path
from google.colab import drive
drive.mount("/content/drive")
DRIVE = Path("/content/drive/MyDrive/laya-teste-r8")
TRAB = Path("/content/trabalho"); TRAB.mkdir(parents=True, exist_ok=True)
SAIDA = DRIVE / "saida-r8"; (SAIDA / "a").mkdir(parents=True, exist_ok=True)
assert (DRIVE / "catalogo-v2" / "gerador" / "gerar-catalogo-v2.mjs").exists(), "Falta %s/catalogo-v2 (veja o LEIA-ME.md)." % DRIVE

r = subprocess.run("node --version", shell=True, capture_output=True, text=True)
if r.returncode != 0:
    subprocess.run("apt-get -qq update && apt-get -qq install -y nodejs", shell=True, check=True)
    r = subprocess.run("node --version", shell=True, capture_output=True, text=True)
print("node", r.stdout.strip()); assert int(r.stdout.strip().lstrip("v").split(".")[0]) >= 18, "precisa do Node 18 ou mais novo"

shutil.copytree(DRIVE / "catalogo-v2", TRAB / "catalogo-v2", dirs_exist_ok=True, ignore=shutil.ignore_patterns("dados", "dados-*"))
D0 = TRAB / "dados-orig"
if not (D0 / "produtos.jsonl").exists():
    subprocess.run(["node", str(TRAB / "catalogo-v2" / "gerador" / "gerar-catalogo-v2.mjs"), "--saida", str(D0)], check=True)   # SEM --rodada8

HASH_ORIGINAL = {   # calculados na maquina do projeto, rodada 6
    "produtos.jsonl": "1580a317feb3e71a7afc9a698bc300581f945de7376085b28a901df4d279821d",
    "splits.json": "36b75a580152530ecd4a1c5dc1ec638c306a4179c7db9b7571e4196799dec8e7",
    "taxonomia.json": "46fff591b4b102602c32074c5cfc4c399bf744b9be5701744310569b1d49ceb0",
    "novos.jsonl": "c1bf5c844881f35c8dc8e92263b18e290430fb723d82fa6641fcb56e9822ef06",
    "buscas.jsonl": "67c3c14f7214900a2a2cbda1a08a245e95fea700fbede963b29779d6195c7f6b",
}
def _sha(caminho):
    h = hashlib.sha256()
    with open(caminho, "rb") as f:
        for b in iter(lambda: f.read(1 << 24), b""):
            h.update(b)
    return h.hexdigest()
obtido = {n: _sha(D0 / n) for n in HASH_ORIGINAL}
iguais = {n: obtido[n] == HASH_ORIGINAL[n] for n in HASH_ORIGINAL}
for n in HASH_ORIGINAL:
    print("%-16s %s  %s" % (n, "IGUAL" if iguais[n] else "DIFERENTE", obtido[n][:16] + "..."))
json.dump({"hashes_obtidos": obtido, "hashes_esperados": HASH_ORIGINAL, "iguais": iguais, "ok": all(iguais.values())}, open(SAIDA / "a" / "portao-hash-original.json", "w"), indent=1)
if not all(iguais.values()):
    raise RuntimeError("PORTAO DO HASH FALHOU: a base original gerada NAO e identica a da rodada 6 (%s). O gerador mudou (confira a pasta catalogo-v2 do Drive). Nada mais roda." % [n for n, ok in iguais.items() if not ok])
print("PORTAO DO HASH OK: a saida sem --rodada8 e identica byte a byte a da rodada 6.")''')

md(r'''## Ambiente e a base nova (`--rodada8`)
Instala o que falta (`laya==0.4.0`, `sentence-transformers`), confere a GPU e gera a **base nova**. A taxonomia nova tem **9 subcategorias a mais** (churrasqueiras a carvão, fogões a lenha, panelas de pressão, escovas de dente manuais e elétricas, ferramentas manuais avulsas, bicicletas elétricas, lavadoras para veículos, caixas térmicas para alimentos), sempre dentro dos limites da cascata (até 16 departamentos, 6 categorias por departamento e 10 subcategorias por categoria, 20 opções por pergunta). Os hashes da base nova ficam gravados: os blocos seguintes conferem que o gerador não mudou.''')

code(AMBIENTE.replace("__GPU__", "sim"))

code(r'''t0 = time.time()
gerar(D_R8, "--rodada8", "--semente", str(SEMENTE_R8), "--ruido", str(RUIDO_R8))
ARQ_R8 = ("produtos.jsonl", "novos.jsonl", "buscas.jsonl", "taxonomia.json", "splits.json", "arvore_legada.json", "arvore_gabarito.json", "arvore_splits.json", "fora_treino.jsonl", "estatisticas.json")
HASH_R8 = {n: pv.sha256_arquivo(str(D_R8 / n)) for n in ARQ_R8}
anterior = fl.ler_json(SAIDA / "a" / "hashes-base-r8.json")
if anterior and anterior != HASH_R8:
    raise RuntimeError("A base --rodada8 gerada agora e diferente da gravada num bloco a anterior: %s" % [n for n in anterior if anterior.get(n) != HASH_R8.get(n)])
fl.gravar_json(SAIDA / "a" / "hashes-base-r8.json", HASH_R8)
print("base nova gerada em %.0fs" % (time.time() - t0))

est = json.load(open(D_R8 / "estatisticas.json", encoding="utf-8"))
r8 = est["rodada8"]
print({k: est[k] for k in ("produtos", "novos", "buscas", "departamentos", "categorias", "subcategorias")})
print("erros de atributo:", r8["erros_de_atributo"])
print("arvore legada:", r8["arvore"])
print("novos extras:", r8["novos_extras"], "| fora da taxonomia:", r8["fora_da_taxonomia"], "| ambiguos:", r8["ambiguos"])
print("buscas por tipo:", est["buscas_por_tipo"])
# a taxonomia nova respeita os limites da cascata
tx0 = json.load(open(D_R8 / "taxonomia.json", encoding="utf-8"))
assert len(tx0["departamentos"]) <= 16
assert max(len(d["categorias"]) for d in tx0["departamentos"]) <= 6
assert max(len(c["subcategorias"]) for d in tx0["departamentos"] for c in d["categorias"]) <= 10
assert est["subcategorias"] == 214, est["subcategorias"]
assert est["produtos"] == 100000 and est["novos"] >= 10000
assert 1500 <= r8["arvore"]["nos"] <= 1900, "a arvore legada deveria ter ~1.700 nos"
assert r8["arvore"]["profundidade_max"] >= 5
ficha_nao_eletrica = {s["id"]: [a["chave"] for a in s["atributos"]] for d in tx0["departamentos"] for c in d["categorias"] for s in c["subcategorias"] if s["id"] in ("churrasqueiras_carvao", "fogoes_lenha", "panelas_pressao", "escovas_dentes_manuais", "ferramentas_manuais_avulsas")}
assert all("voltagem" not in v for v in ficha_nao_eletrica.values()), ficha_nao_eletrica
print("taxonomia nova OK: %d subcategorias; as nao eletricas nao tem voltagem na ficha" % est["subcategorias"])''')

md(r'''## Trava do checkpoint
**Trava.** O `laya2-ft-5000` é carregado com `laya.load(<pasta>, device="cuda")`; o notebook imprime o **nome e o hash do `model.safetensors`** e faz a pergunta de controle (departamento de 120 produtos da validação da **base original**): o ajustado precisa acertar pelo menos 90% e superar o Laya base em 20 pontos. Na rodada 6 um servidor respondeu com o modelo base achando que era o ajustado.

Os checkpoints do Laya vão em `laya-ft/laya2-ft-5000/` do Drive. A paridade com a rodada 6/7 vem logo depois da trava.''')

code(r'''import torch
pv.ativar_limpeza_de_entrada(False)           # paridade: a limpeza de entrada (2.1) fica desligada, como na rodada 7
BATCH, BLOCO = 32, 5000
gerar(D_ORIG)
tx = pv.Taxonomia(str(D_ORIG / "taxonomia.json"))
CONTROLE = pv.controle_departamento(str(D_ORIG), tx, 120)
CK = TRAB / "ck"; CK.mkdir(exist_ok=True)
nome_ck = "laya2-ft-5000"
assert (CK_DRIVE / nome_ck / "model.safetensors").exists(), "Falta %s" % (CK_DRIVE / nome_ck)
shutil.copytree(CK_DRIVE / nome_ck, CK / nome_ck, dirs_exist_ok=True)
BASE = None
try:
    BASE = pv.carregar_laya("multilingual")
except Exception as e:
    print("Laya base indisponivel para o controle (%s): confiro so o acerto absoluto." % type(e).__name__)
AG5 = pv.carregar_laya(str(CK / nome_ck))
TRAVA = pv.verificar_checkpoint(nome_ck, str(CK / nome_ck), tx, CONTROLE, AG5, BASE)
print(nome_ck, "| sha256 do model.safetensors:", TRAVA["sha256_model_safetensors"], "| controle: ajustado %.1f%%" % (100 * TRAVA["acerto_departamento_ajustado"]))
del BASE; torch.cuda.empty_cache()
fl.gravar_json(SAIDA / "a" / "trava-laya2.json", TRAVA)
LOTE5 = pv.LayaLote(AG5, batch_size=BATCH)''')

md(r'''## Paridade com a rodada 6/7 e o efeito de trocar o Naive Bayes
**Nesta PoC o Naive Bayes é o da biblioteca** (`scikit-learn`: `MultinomialNB` sobre `TfidfVectorizer`), não o código escrito à mão que era a porta do `ClassificadorTexto.java`. Como os números de referência da rodada 6/7 foram medidos com o Naive Bayes do Java, a paridade roda **duas vezes**, com os mesmos dados e o mesmo Laya (as respostas do Laya são reaproveitadas do cache):

1. **Com o Naive Bayes escrito à mão (`NaiveBayesCodigo`) — é o portão.** Prova que o pipeline Python reproduz o agente Java e a rodada 7 (0,5 ponto no saneamento e na ficha; 1 ponto nos itens novos e na busca). Se falhar, **para**: nada do que vem depois é comparável.
2. **Com o Naive Bayes do scikit-learn — informativo, não bloqueia.** Mostra, linha a linha, **quanto os números mudam por trocar o código pela biblioteca** (coluna "efeito da troca"). É o que vale daqui para a frente (todos os blocos usam o scikit-learn).

Se o efeito da troca for pequeno (décimos de ponto), a biblioteca pode substituir o código sem perda. Se for grande em alguma linha, a PoC não quebra, mas essa linha deixa de ser comparável com a rodada 6/7.''')

code(r'''import pandas as pd
REF = json.load(open(TRAB / "r6-referencias.json", encoding="utf-8"))
PARAMS = pv.parametros_da_escolha(str(TRAB / "r6-escolha-val.json"))
PARAMS["D"] = pv.parametros_da_escolha(str(TRAB / "r6-escolha-val-d.json"))["D"]
print({k: v.resumo() for k, v in PARAMS.items()})
(TRAB / "v2").mkdir(exist_ok=True)
for n in (2000,):
    k = pv.gerar_treino_v2(str(D_ORIG), "treino_%d" % n, str(TRAB / "v2" / ("treino-%d.jsonl" % n)))
    print("treino_%d: %d exemplos" % (n, k))
EXEMPLOS_2000 = pv.ler_treino(str(TRAB / "v2" / "treino-2000.jsonl"), tx)      # o voto-b da rodada 6 usou o NB de 2.000 exemplos
MARCAS_ORIG = pv.ResolvedorMarca.do_catalogo(pv.ler_jsonl(str(D_ORIG / "produtos.jsonl")), 5)
spl = json.load(open(D_ORIG / "splits.json", encoding="utf-8"))
CACHE_PAR = SAIDA / "a" / "paridade" / "laya-cache"; CACHE_PAR.mkdir(parents=True, exist_ok=True)      # o Laya e o mesmo nas duas passadas: as respostas ficam em cache
cat_orig = pv.ler_produtos(str(D_ORIG / "produtos.jsonl"))
INDICE_ORIG = pv.IndiceDeDuplicados.do_catalogo(cat_orig, MARCAS_ORIG, 0.3)
novos_t = pv.ler_produtos(str(D_ORIG / "novos.jsonl"), set(spl["novos_teste"]))
docs_c = [(p.titulo, p.atributos, pv.texto_longo(p)) for p in cat_orig]
buscas_o = list(pv.ler_jsonl(str(D_ORIG / "buscas.jsonl")))
teste_q = [b for i, b in enumerate(buscas_o) if i % 4 != 0]            # as consultas de teste da rodada 6 (1 em cada 4 foi de ajuste)
d_busca = TRAB / "dados-busca-teste"; d_busca.mkdir(exist_ok=True)
pv.escrever_jsonl(str(d_busca / "buscas.jsonl"), teste_q)
for n in ("produtos.jsonl", "splits.json", "taxonomia.json"):
    if not (d_busca / n).exists():
        os.symlink(str(D_ORIG / n), str(d_busca / n))

def rodar_paridade(NBX, impl, obrigatorio):
    """A paridade com a rodada 6/7 usando o Naive Bayes NBX. `impl` so nomeia a pasta de saida; `obrigatorio` marca se a linha e um portao."""
    PARID = []
    pdir = SAIDA / "a" / "paridade" / impl; pdir.mkdir(parents=True, exist_ok=True)

    def bloco_conjunto(rotulo, ids):
        prods = pv.ler_produtos(str(D_ORIG / "produtos.jsonl"), set(ids))
        estados, _ = pv.estados_dos_produtos(prods)
        docs = [(p.titulo, p.atributos, pv.texto_longo(p)) for p in prods]
        cats, confs = NBX.prever_lote(docs)
        cache = pv.CacheJsonl(str(CACHE_PAR / ("laya2-5000-%s.jsonl" % rotulo)))
        laya = pv.cascata_com_cache(LOTE5, tx, [p.id for p in prods], estados, cache, bloco=BLOCO, log=lambda *_: None)
        cache.fechar()
        res = pv.votacao_em_lote(prods, cats, confs, laya, tx, PARAMS, ["B", "D"], llm=None, log=lambda *_: None)
        marcas = [MARCAS_ORIG.resolver(p) for p in prods]
        def sinal(subs):
            return [{"id": p.id, "status": "APLICADO" if s else "MANTIDO", "subcategoria": s, "marca": m} for p, s, m in zip(prods, subs, marcas)]
        previsoes = {"nb2000_sozinho": sinal(cats), "laya_ft5000_sozinho": sinal([l["sub"] for l in laya]),
                     "voto_b_ft5000": [pv.previsao_saneamento(p, d, m) for p, (d, _), m in zip(prods, res.decisoes["B"], marcas)],
                     "voto_d": [pv.previsao_saneamento(p, d, m) for p, (d, _), m in zip(prods, res.decisoes["D"], marcas)]}
        return prods, estados, cats, confs, laya, res, previsoes

    GOLDEN = None
    for rotulo in ("golden", "teste_rodadas"):
        prods, estados, cats, confs, laya, res, previsoes = bloco_conjunto(rotulo, spl[rotulo])
        for nome, linhas in previsoes.items():
            f = pdir / ("%s-%s.jsonl" % (nome, rotulo)); pv.escrever_jsonl(str(f), linhas)
            r = pv.avaliar(AVAL, "saneamento", str(D_ORIG), pred=str(f), ids=rotulo)
            ref = REF["saneamento"][rotulo][nome]
            for campo, chave_r in (("aplicados", "aplicados_%"), ("acerto", "acerto_subcategoria_aplicados_%"), ("pioras", "pioras_%_do_total")):
                PARID.append({"grupo": "saneamento", "rotulo": rotulo, "obrigatorio": obrigatorio, **pv.comparar_paridade("%s / %s / %s" % (nome, rotulo, campo), r[chave_r], ref[campo], 0.5)})
        if rotulo == "golden":
            GOLDEN = (prods, estados, cats, confs, laya, res)

    prods, estados, cats, confs, laya, res = GOLDEN
    par_ficha = pv.Parametros(1, True, 1.01, 0.5, {"llm", "supervisionado"})
    dec_f = [pv.decidir(pv.resultados_baratos(tx.categoria_do_vendedor(p.categoria_vendedor), c, cf, None), par_ficha) for p, c, cf in zip(prods, cats, confs)]
    subs_f = [d.categoria if d.aplicar else None for d in dec_f]
    codigo = pv.FichaCodigo(tx)
    marcas_g = [MARCAS_ORIG.resolver(p) for p in prods]
    fichas_cod = [None if s is None else codigo.extrair(p, s) for p, s in zip(prods, subs_f)]
    fichas_laya, uso_f = pv.ficha_com_laya(LOTE5, tx, codigo, prods, estados, subs_f, 0.9)
    for nome, fichas in (("codigo", fichas_cod), ("codigo_laya_ft5000", fichas_laya)):
        linhas = [pv.previsao_saneamento(p, d, m, f) for p, d, m, f in zip(prods, dec_f, marcas_g, fichas)]
        f = pdir / ("ficha-%s.jsonl" % nome); pv.escrever_jsonl(str(f), linhas)
        r = pv.avaliar(AVAL, "saneamento", str(D_ORIG), pred=str(f), ids="golden")
        ref = REF["ficha"]["golden"][nome]
        PARID.append({"grupo": "ficha", "rotulo": "golden", "obrigatorio": obrigatorio, **pv.comparar_paridade("ficha %s / acerto dos inferiveis" % nome, r["atributos"]["acerto_inferiveis_%"], ref["atributos_acerto"], 0.5)})
        PARID.append({"grupo": "ficha", "rotulo": "golden", "obrigatorio": obrigatorio, **pv.comparar_paridade("ficha %s / inventados" % nome, r["atributos"]["inventados_%_dos_nao_inferiveis"], ref["atributos_inventados"], 0.5)})

    # itens novos (NB + regras, sem LLM)
    cats_n, confs_n = NBX.prever_lote([(p.titulo, p.atributos, pv.texto_longo(p)) for p in novos_t])
    preds_n, det_n = pv.tratar_novos(novos_t, INDICE_ORIG, [None] * len(novos_t), confs_n, llm=None, desempate="nenhum", fora="baixa-confianca", fora_confianca=0.2)
    f = pdir / "novos-teste-nb.jsonl"; pv.escrever_jsonl(str(f), preds_n)
    rn = pv.avaliar(AVAL, "novos", str(D_ORIG), pred=str(f), ids="novos_teste")
    refn = REF["novos"]["novos_teste_nb_baixa_confianca_0_2"]
    for grupo, chave_a in (("fora", "fora_da_taxonomia"), ("duplicados", "duplicados"), ("invalidos", "invalidos")):
        for m in ("precisao_%", "cobertura_%"):
            PARID.append({"grupo": "novos", "rotulo": "novos_teste", "obrigatorio": obrigatorio, **pv.comparar_paridade("novos %s / %s" % (grupo, m), rn[chave_a][m], refn[grupo if grupo != "fora" else "fora"][m], 1.0)})

    # busca por sinonimos nas consultas de teste
    CAT_BARATO = pv.catalogo_curado_barato(cat_orig, NBX, tx, MARCAS_ORIG, pv.FichaCodigo(tx), docs_c)
    BUSCA_ORIG = pv.BuscaV2(tx, CAT_BARATO, MARCAS_ORIG, pv.FichaCodigo(tx), llm=None)
    inten, lista = [], []
    for b in teste_q:
        r = BUSCA_ORIG.buscar(b["q"], "sinonimos")                          # so o texto da consulta entra: nunca a intencao verdadeira
        i = r["intencao"]
        inten.append({"id": b["id"], "subcategorias": i["subcategorias"], "filtros": i["filtros"], "marca": i["marca"]})
        lista.append({"id": b["id"], "resultados": r["resultados"]})
    pv.escrever_jsonl(str(pdir / "busca-intencao.jsonl"), inten); pv.escrever_jsonl(str(pdir / "busca-lista.jsonl"), lista)
    bi = pv.avaliar(AVAL, "busca", str(d_busca), pred=str(pdir / "busca-intencao.jsonl"))["todas"]
    bl = pv.avaliar(AVAL, "busca", str(d_busca), pred=str(pdir / "busca-lista.jsonl"))["todas"]
    refb = REF["busca"]["sinonimos_675_consultas_de_teste"]
    for nome, obtido_, ref in (("subcategorias exatas", bi["subcategorias_exatas_%"], refb["intencao"]["subcategorias_exatas_%"]), ("filtros (cobertura)", bi["filtros_cobertura_%"], refb["intencao"]["filtros_cobertura_%"]),
                              ("P@10", bl["P@10_%"], refb["lista"]["P@10_%"]), ("cobertura da lista", bl["cobertura_%"], refb["lista"]["cobertura_%"])):
        PARID.append({"grupo": "busca", "rotulo": "675 consultas", "obrigatorio": obrigatorio, **pv.comparar_paridade("busca sinonimos / " + nome, obtido_, ref, 1.0)})
    return PARID''')

code(r'''pd.set_option("display.width", 220); pd.set_option("display.max_rows", 200)
# ---- 1) PORTAO: o Naive Bayes escrito a mao (a porta do Java) reproduz a rodada 6/7
NB2_CODIGO = pv.NaiveBayesCodigo.treinar(EXEMPLOS_2000, 42)
PARID_CODIGO = rodar_paridade(NB2_CODIGO, "codigo", True)
df_c = pd.DataFrame(PARID_CODIGO)
print("== PORTAO: Naive Bayes escrito a mao (NaiveBayesCodigo) x rodada 6/7")
print(df_c[["grupo", "item", "obtido", "rodada6", "diferenca", "tolerancia", "ok"]].to_string(index=False))
fl.gravar_json(SAIDA / "a" / "paridade.json", PARID_CODIGO)
if not bool(df_c["ok"].all()):
    raise RuntimeError("PARIDADE FALHOU em %d linha(s): %s. O pipeline Python nao reproduz o agente; o bloco B nao pode seguir." % ((~df_c["ok"]).sum(), list(df_c[~df_c["ok"]]["item"])))
print("PARIDADE OK: o pipeline_v3 (Naive Bayes escrito a mao) reproduz a rodada 6/7 dentro das tolerancias.")

# ---- 2) INFORMATIVO: o Naive Bayes do scikit-learn (o que a PoC usa dai em diante)
NB2_SK = pv.NaiveBayes.treinar(EXEMPLOS_2000, 42)
print("\nNaive Bayes em uso na PoC:", NB2_SK.implementacao, "| temperatura %.2f | acerto na calibracao %.1f%%" % (NB2_SK.temperatura, 100 * NB2_SK.acerto_calibracao))
PARID_SK = rodar_paridade(NB2_SK, "sklearn", False)
df_s = pd.DataFrame(PARID_SK)
cmp = df_c[["grupo", "item", "obtido", "rodada6"]].rename(columns={"obtido": "codigo"}).merge(df_s[["item", "obtido"]].rename(columns={"obtido": "sklearn"}), on="item")
cmp["efeito_da_troca"] = (cmp["sklearn"] - cmp["codigo"]).round(2)
cmp["sklearn_vs_rodada6"] = (cmp["sklearn"] - cmp["rodada6"]).round(2)
print("\n== INFORMATIVO: Naive Bayes do scikit-learn x escrito a mao x rodada 6/7 (nao bloqueia)")
print(cmp.to_string(index=False))
maior = cmp.reindex(cmp["efeito_da_troca"].abs().sort_values(ascending=False).index).head(5)
print("\nmaiores efeitos da troca (pontos percentuais):", [(r["item"], r["efeito_da_troca"]) for _, r in maior.iterrows()])
fl.gravar_json(SAIDA / "a" / "paridade-sklearn.json", {"implementacao": NB2_SK.implementacao, "tabela": cmp.to_dict(orient="records"), "temperatura": NB2_SK.temperatura, "acerto_calibracao": NB2_SK.acerto_calibracao})
fl.concluir_bloco(str(SAIDA), "a", {"paridade_linhas": len(PARID_CODIGO), "hash_original": "igual", "trava_laya2_sha256": TRAVA["sha256_model_safetensors"], "pipeline": pv.VERSAO_PIPELINE,
                                    "naive_bayes_da_poc": NB2_SK.implementacao, "maior_efeito_da_troca_do_nb_pontos": float(cmp["efeito_da_troca"].abs().max())})
print("bloco A concluido. Proximo: laya_r8_b_treino_laya.")''')
