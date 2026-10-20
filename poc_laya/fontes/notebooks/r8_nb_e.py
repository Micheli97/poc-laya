# -*- coding: utf-8 -*-
# Bloco E: trilha de texto + imagem (imagem_r8.ipynb). Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, trilha de imagem: a foto melhora a categoria e os atributos visuais?

> ## ⚠ Licença e uso dos dados
> Esta trilha usa o **Amazon Berkeley Objects (ABO)**, produtos reais com fotos de catálogo, sob a licença **CC BY-NC 4.0 (uso não comercial)**. Use **só para avaliação interna da POC**: **não redistribua as imagens, não as inclua no repositório, não as use em produção**. Nenhuma imagem vai para serviço externo: os modelos (SigLIP 2 e Qwen2.5-VL-7B) são **abertos e baixados no Colab**. **Antes de rodar, confirme com o time que o uso interno está ok.** Se não estiver, a alternativa é uma amostra de fotos do próprio cliente, com validação do time.

**A pergunta.** Quando o texto do vendedor é ruim (título só com a linha comercial, cor errada ou ausente), a **foto** melhora a **categoria** e os **atributos visuais** (cor, material)? Quanto custa, e quanto do trabalho dá para resolver com o modelo de imagem **pequeno**, deixando o **grande** só para as disputas?

**Por que um conjunto à parte.** A base fictícia não tem fotos. Aqui, produtos **reais com foto**, com os **mesmos tipos de erro do vendedor injetados** no título e nos atributos (a foto fica intacta):

| Ruído (no teste) | O que acontece |
|---|---|
| título sem o nome do produto | só marca + linha comercial ("Linha Premium 2024 Preto") |
| categoria do vendedor errada | vizinha ou genérica |
| cor ausente | sem cor no título e nos atributos |
| cor que contradiz a foto | o atributo diz preto, o produto é branco |
| controle | sem ruído |

**As cinco versões comparadas**
1. **Antes**: como o vendedor cadastrou.  2. **Só texto**: texto + vizinhos de texto (e5) + NB.  3. **Texto + imagem pequena**: + vizinhos por imagem (SigLIP 2) e atributos visuais.  4. **+ modelo de visão nas disputas** (Qwen2.5-VL-7B só no que os sinais baratos não decidem).  5. **Modelo de visão em tudo**, numa amostra de 1 a 2 mil, como referência de teto e de custo.

**Regras.** Critérios fixos antes (`r8-criterios.json`); calibração só na **validação**; gabarito (tipo, cor e material do ABO) só para treino/validação e para **medir**; retomada pelo Drive (o recorte de imagens, os embeddings e as respostas do modelo grande ficam em `saida-r8/e/` e `abo-recorte/`). **Quantização do modelo grande:** a **L4 (24 GB) em bf16** é a preferida; na T4, 8 bits; 4 bits só se não couber; a quantização aparece no resultado e no painel e vale para as versões 4 e 5. **Tempo (estimativa; o notebook mede):** metadados e recorte 15 a 40 min; SigLIP 2 em ~20 mil imagens 5 a 10 min; modelo grande nas disputas e na amostra da versão 5: de 1 a 3 h.

''' + BLOCOS + "\n\n" + AVISOS)

code(r'''import subprocess, sys, os, json, time, shutil, hashlib, math, random, io, re, gzip, tarfile
from pathlib import Path
from collections import Counter, defaultdict
import numpy as np
r = subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv,noheader", shell=True, capture_output=True, text=True)
print("GPU:", r.stdout.strip() or "NENHUMA")
assert r.stdout.strip(), "Sem GPU: Ambiente de execucao > Alterar o tipo > GPU (de preferencia L4)."
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "-U", "transformers", "accelerate", "bitsandbytes", "sentence-transformers", "scikit-learn", "pillow", "requests", "pandas"], check=True)
from google.colab import drive
drive.mount("/content/drive")
DRIVE = Path("/content/drive/MyDrive/laya-teste-r8")
TRAB = Path("/content/trabalho"); TRAB.mkdir(parents=True, exist_ok=True)
SAIDA = DRIVE / "saida-r8"; E_DIR = SAIDA / "e"; E_DIR.mkdir(parents=True, exist_ok=True)
RECORTE = DRIVE / "abo-recorte"; (RECORTE / "imagens").mkdir(parents=True, exist_ok=True)
for n in ("pipeline_v3.py", "fluxo_r8.py", "r8-criterios.json"):
    assert (DRIVE / n).exists(), "Falta %s em %s" % (n, DRIVE)
    shutil.copy(DRIVE / n, TRAB / n)
sys.path.insert(0, str(TRAB))
import pipeline_v3 as pv
import fluxo_r8 as fl
import torch, pandas as pd
GPU = torch.cuda.get_device_properties(0)
VRAM_GB = GPU.total_memory / 2**30
BF16_OK = torch.cuda.is_bf16_supported() and GPU.major >= 8
if VRAM_GB >= 20 and BF16_OK:
    QUANT = "bf16"
elif VRAM_GB >= 14:
    QUANT = "8bit"
else:
    QUANT = "4bit"
print("GPU %s | %.1f GB | bf16 nativo: %s | quantizacao do modelo grande escolhida: %s" % (GPU.name, VRAM_GB, BF16_OK, QUANT))
if QUANT != "bf16":
    print("ATENCAO: fora de bf16 (%s). A quantizacao fica registrada no resultado e no painel e vale para as versoes 4 e 5 (a versao 5 usa a mesma das versoes 3 e 4, as que usam o modelo grande)." % QUANT)
CONFIRMO_USO_INTERNO_ABO = True   # marque True so depois de confirmar com o time que o uso interno do ABO (CC BY-NC 4.0) esta ok
assert CONFIRMO_USO_INTERNO_ABO, "Confirme com o time o uso interno do ABO (CC BY-NC 4.0) antes de baixar os dados."
CRITERIOS = fl.ler_json(TRAB / "r8-criterios.json")''')

md(r'''## Dados: medir antes de baixar
O ABO é servido pelo bucket público `amazon-berkeley-objects.s3.amazonaws.com`. **Só o recorte é baixado.** Primeiro o notebook **mede e mostra o tamanho** dos arquivos remotos (metadados e imagens pequenas) e testa se o bucket serve **imagens avulsas**; se servir, baixa só as do recorte; se for só o `.tar`, lê em **streaming** e extrai só as do recorte. O recorte vai para o **Drive** (`abo-recorte/`) e não é baixado de novo.''')

code(r'''import requests
BASE = "https://amazon-berkeley-objects.s3.amazonaws.com"
def tam_remoto(caminho):
    try:
        r = requests.head(BASE + caminho, allow_redirects=True, timeout=30)
        return r.status_code, int(r.headers.get("Content-Length", -1))
    except Exception as e:
        return type(e).__name__, -1
TAMANHOS = {n: tam_remoto(p) for n, p in {"metadados dos produtos (abo-listings.tar)": "/archives/abo-listings.tar", "tabela de imagens (images.csv.gz)": "/images/metadata/images.csv.gz",
                                         "imagens pequenas, tudo (abo-images-small.tar)": "/archives/abo-images-small.tar"}.items()}
for n, (st, tam) in TAMANHOS.items():
    print("%-50s status %s | %s" % (n, st, "tamanho desconhecido" if tam < 0 else "%.1f MB" % (tam / 2**20)))
print("(as imagens pequenas inteiras NAO serao baixadas: so o recorte)")

ABO = TRAB / "abo"; ABO.mkdir(exist_ok=True)
def baixar(caminho, destino):
    if destino.exists() and destino.stat().st_size > 0:
        return
    with requests.get(BASE + caminho, stream=True, timeout=120) as r:
        r.raise_for_status()
        with open(destino, "wb") as f:
            for bloco in r.iter_content(1 << 20):
                f.write(bloco)
baixar("/archives/abo-listings.tar", ABO / "abo-listings.tar")
baixar("/images/metadata/images.csv.gz", ABO / "images.csv.gz")
import csv
IMG_PATH = {}
with gzip.open(ABO / "images.csv.gz", "rt", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        IMG_PATH[row["image_id"]] = row["path"]
print("tabela de imagens:", len(IMG_PATH))
LISTAGEM = []
with tarfile.open(ABO / "abo-listings.tar") as tf:
    for m in tf:
        if m.isfile() and m.name.endswith(".json.gz"):
            with gzip.open(tf.extractfile(m), "rt", encoding="utf-8") as g:
                for linha in g:
                    LISTAGEM.append(json.loads(linha))
print("produtos nos metadados:", len(LISTAGEM))''')

code(r'''def _en(campos):
    """O primeiro valor em ingles de um campo multilingue do ABO (lista de {language_tag, value})."""
    for c in campos or []:
        if str(c.get("language_tag", "")).lower().startswith("en") and c.get("value"):
            return c["value"]
    return None
def _primeiro(campos):
    for c in campos or []:
        if c.get("value"):
            return c["value"]
    return None
def _cor(campos):
    for c in campos or []:
        sv = c.get("standardized_values")
        if sv:
            return str(sv[0]).strip().lower()
        if c.get("value") and str(c.get("language_tag", "")).lower().startswith("en"):
            return str(c["value"]).strip().lower()
    return None
PROD = []
for L in LISTAGEM:
    titulo, tipo, img = _en(L.get("item_name")), _primeiro(L.get("product_type")), L.get("main_image_id")
    if not (titulo and tipo and img and img in IMG_PATH):
        continue
    PROD.append({"item_id": L["item_id"], "titulo": titulo, "marca": _en(L.get("brand")) or _primeiro(L.get("brand")) or "", "tipo": str(tipo).upper(), "cor": _cor(L.get("color")),
                 "material": (_en(L.get("material")) or "").strip().lower() or None, "imagem": img, "caminho": IMG_PATH[img]})
print("com titulo em ingles, tipo e foto principal:", len(PROD))
vol = Counter(p["tipo"] for p in PROD)
print("os 40 tipos mais volumosos:", vol.most_common(40))

# recorte: 15 a 25 tipos com volume e com pares que se confundem pelo texto (cadeira x banqueta, luminaria x lampada, capa de celular x pelicula...)
PARES_ALVO = [("CHAIR", "STOOL"), ("LAMP", "LIGHT"), ("CASE", "PROTECTOR"), ("SHOE", "BOOT"), ("SOFA", "BED"), ("TABLE", "DESK"), ("RUG", "CURTAIN"), ("PILLOW", "BLANKET")]
VOL_MIN = 300
escolhidos = []
for a, b in PARES_ALVO:
    for chave_ in (a, b):
        achados = sorted((t for t, n in vol.items() if chave_ in t and n >= VOL_MIN and t not in escolhidos), key=lambda t: -vol[t])
        if achados and len(escolhidos) < 25:
            escolhidos.append(achados[0])
for t, n in vol.most_common():
    if len(escolhidos) >= 18:
        break
    if n >= VOL_MIN and t not in escolhidos:
        escolhidos.append(t)
TIPOS = sorted(escolhidos)
assert 15 <= len(TIPOS) <= 25, "o recorte tem %d tipos (queremos 15 a 25): ajuste PARES_ALVO ou VOL_MIN" % len(TIPOS)
sel = [p for p in PROD if p["tipo"] in set(TIPOS)]
# no maximo 20 mil produtos, com tetos por tipo para nao deixar um tipo dominar
rng = random.Random(20261013)
por_tipo = defaultdict(list)
for p in sel:
    por_tipo[p["tipo"]].append(p)
teto = max(400, 20000 // len(TIPOS))
RECORTE_PROD = []
for t in TIPOS:
    lst = sorted(por_tipo[t], key=lambda p: hashlib.sha256(p["item_id"].encode()).hexdigest())
    RECORTE_PROD.extend(lst[:teto])
RECORTE_PROD = RECORTE_PROD[:20000]
print("recorte: %d produtos em %d tipos: %s" % (len(RECORTE_PROD), len(TIPOS), {t: sum(1 for p in RECORTE_PROD if p["tipo"] == t) for t in TIPOS}))
cob = {"cor": round(sum(1 for p in RECORTE_PROD if p["cor"]) / len(RECORTE_PROD), 3), "material": round(sum(1 for p in RECORTE_PROD if p["material"]) / len(RECORTE_PROD), 3), "marca": round(sum(1 for p in RECORTE_PROD if p["marca"]) / len(RECORTE_PROD), 3), "tipo": 1.0}
print("campos do gabarito presentes (fracao dos produtos):", cob)

# treino 5 mil, validacao 2 mil e teste: o mesmo produto (item_id) e produtos com o mesmo titulo e marca ficam do mesmo lado
grupo = lambda p: hashlib.sha256((p["marca"].lower() + "|" + re.sub(r"[^a-z0-9]+", " ", p["titulo"].lower())[:40]).encode()).hexdigest()
chaves = sorted({grupo(p) for p in RECORTE_PROD}, key=lambda k: hashlib.sha256((k + "20261013").encode()).hexdigest())
lado_do_grupo = {}
tam_grupo = Counter(grupo(p) for p in RECORTE_PROD)
acum = 0
for k in chaves:
    lado_do_grupo[k] = "treino" if acum < 5000 else ("validacao" if acum < 7000 else "teste")
    acum += tam_grupo[k]
for p in RECORTE_PROD:
    p["split"] = lado_do_grupo[grupo(p)]
print("splits:", Counter(p["split"] for p in RECORTE_PROD))
fl.gravar_json(E_DIR / "recorte.json", {"tipos": TIPOS, "produtos": RECORTE_PROD, "cobertura_dos_campos": cob})''')

code(r'''# ---- baixar so as imagens do recorte para o Drive (retoma). Antes: mede o tamanho medio e mostra a estimativa.
def url_imagem(p):
    return "%s/images/small/%s" % (BASE, p["caminho"])
amostra = random.Random(1).sample(RECORTE_PROD, 40)
tam = []
for p in amostra:
    try:
        r = requests.head(url_imagem(p), allow_redirects=True, timeout=30)
        if r.status_code == 200:
            tam.append(int(r.headers.get("Content-Length", 0)))
    except Exception:
        pass
AVULSAS = len(tam) >= 30
print("o bucket serve imagens AVULSAS:", AVULSAS, "| medidas: %d de 40" % len(tam))
if AVULSAS:
    medio = sum(tam) / len(tam)
    print("tamanho medio de uma imagem pequena: %.1f KB | recorte inteiro estimado: %.0f MB (so as %d imagens do recorte; o tar completo tem %.1f MB e NAO sera baixado)" %
          (medio / 1024, medio * len(RECORTE_PROD) / 2**20, len(RECORTE_PROD), TAMANHOS["imagens pequenas, tudo (abo-images-small.tar)"][1] / 2**20))
else:
    print("sem imagens avulsas: leitura do .tar em STREAMING, extraindo so as %d imagens do recorte (o tar tem %.1f MB e e lido de uma vez, sem gravar inteiro)" %
          (len(RECORTE_PROD), TAMANHOS["imagens pequenas, tudo (abo-images-small.tar)"][1] / 2**20))
def arq_img(p):
    return RECORTE / "imagens" / (p["imagem"] + ".jpg")
faltam_ = [p for p in RECORTE_PROD if not arq_img(p).exists()]
print("imagens do recorte ja no Drive: %d | a baixar: %d" % (len(RECORTE_PROD) - len(faltam_), len(faltam_)))
t0 = time.time()
if faltam_ and AVULSAS:
    from concurrent.futures import ThreadPoolExecutor
    def um(p):
        for _ in range(3):
            try:
                r = requests.get(url_imagem(p), timeout=60)
                if r.status_code == 200:
                    arq_img(p).write_bytes(r.content)
                    return 1
            except Exception:
                time.sleep(1)
        return 0
    with ThreadPoolExecutor(max_workers=16) as ex:
        ok_ = sum(ex.map(um, faltam_))
elif faltam_:
    quer = {"small/" + p["caminho"]: p for p in faltam_}
    ok_ = 0
    with requests.get(BASE + "/archives/abo-images-small.tar", stream=True, timeout=300) as r:
        r.raise_for_status()
        with tarfile.open(fileobj=r.raw, mode="r|") as tf:
            for m in tf:
                nome = re.sub(r"^.*?images/", "", m.name) if "images/" in m.name else m.name
                for cand in (nome, nome.replace("small/", "", 1), "small/" + nome):
                    if m.isfile() and cand in quer:
                        arq_img(quer[cand]).write_bytes(tf.extractfile(m).read())
                        ok_ += 1
                        break
                if ok_ >= len(quer):
                    break
else:
    ok_ = 0
print("imagens baixadas agora: %d em %.0fs" % (ok_, time.time() - t0))
RECORTE_PROD = [p for p in RECORTE_PROD if arq_img(p).exists()]
assert len(RECORTE_PROD) > 5000, "poucas imagens disponiveis (%d)" % len(RECORTE_PROD)
print("recorte final com foto:", len(RECORTE_PROD), "| splits:", Counter(p["split"] for p in RECORTE_PROD))''')

md(r'''## Ruído do vendedor (injetado)
**Só no teste e na validação** (o treino mantém o cadastro original). Cada produto de teste e de validação recebe **um** dos cinco ruídos (cerca de 20% cada, o controle sem ruído incluído), sobre o **título** e os **atributos** (a foto fica intacta). A validação recebe o mesmo gerador com **outra semente**, só para calibrar. O texto que os modelos veem **não inclui a categoria do vendedor** (a pista mais enganosa); ela só alimenta o sinal "vendedor" da votação e a versão **Antes**.''')

code(r'''LINHAS = ["Linha Premium 2024", "Colecao Classic", "Serie Pro Plus", "Edicao Limitada", "Linha Home Comfort", "Design Studio", "Linha Essencial", "Colecao Urbana", "Serie Max"]
GENERICAS = ["Diversos", "Outros", "Promocoes", "Casa e Jardim", "Geral"]
def legivel(t):
    return t.replace("_", " ").title()
def vizinhos_de_tipo(t):
    """Os tipos que se confundem com t: o par do recorte (cadeira x banqueta...) ou, na falta, outro tipo qualquer."""
    for a, b in PARES_ALVO:
        for x, y in ((a, b), (b, a)):
            if x in t:
                alvo = [u for u in TIPOS if y in u and u != t]
                if alvo:
                    return alvo
    return [u for u in TIPOS if u != t]
COR_MAIS = None
def injetar(p, rng, ruido, cores):
    """O anuncio do vendedor para o produto, com o ruido dado. Devolve {titulo, atributos, categoria_vendedor}."""
    titulo, marca, cor, mat = p["titulo"], p["marca"], p["cor"], p["material"]
    atrs = {}
    if cor:
        atrs["Cor"] = cor
    if mat:
        atrs["Material"] = mat
    cat = legivel(p["tipo"])
    if ruido == "titulo_sem_nome":
        titulo = ("%s %s %s" % (marca, rng.choice(LINHAS), (cor or "").title())).strip()
    elif ruido == "categoria_errada":
        cat = legivel(rng.choice(vizinhos_de_tipo(p["tipo"]))) if rng.random() < 0.7 else rng.choice(GENERICAS)
    elif ruido == "cor_ausente":
        atrs.pop("Cor", None)
        if cor:
            titulo = re.sub(r"\b%s\b" % re.escape(cor), "", titulo, flags=re.IGNORECASE)
            titulo = re.sub(r"\s+", " ", titulo).strip()
    elif ruido == "cor_contradiz" and cor:
        outras = [c for c in cores if c != cor]
        atrs["Cor"] = rng.choice(outras)
    return {"titulo": titulo, "atributos": atrs, "categoria_vendedor": cat}
RUIDOS = ["titulo_sem_nome", "categoria_errada", "cor_ausente", "cor_contradiz", "controle"]
cores_treino = [c for c, _ in Counter(p["cor"] for p in RECORTE_PROD if p["split"] == "treino" and p["cor"]).most_common(12)]
mats_treino = [m for m, _ in Counter(p["material"] for p in RECORTE_PROD if p["split"] == "treino" and p["material"]).most_common(10)]
print("opcoes de cor (as 12 mais comuns no treino):", cores_treino)
print("opcoes de material (as 10 mais comuns no treino):", mats_treino)
ANUNCIO = {}
for p in RECORTE_PROD:
    if p["split"] == "treino":
        ANUNCIO[p["item_id"]] = {"titulo": p["titulo"], "atributos": {**({"Cor": p["cor"]} if p["cor"] else {}), **({"Material": p["material"]} if p["material"] else {})}, "categoria_vendedor": legivel(p["tipo"]), "ruido": "treino"}
        continue
    rng = random.Random(hashlib.sha256((p["item_id"] + ("20261013" if p["split"] == "teste" else "99")).encode()).hexdigest())
    ruido = RUIDOS[int(rng.random() * 5)]
    if ruido == "cor_contradiz" and not (p["cor"] and p["cor"] in cores_treino):
        ruido = "controle"
    if ruido in ("cor_ausente",) and not p["cor"]:
        ruido = "controle"
    a = injetar(p, rng, ruido, cores_treino)
    a["ruido"] = ruido
    ANUNCIO[p["item_id"]] = a
for sp in ("validacao", "teste"):
    print(sp, dict(Counter(ANUNCIO[p["item_id"]]["ruido"] for p in RECORTE_PROD if p["split"] == sp)))
print("exemplo:", ANUNCIO[next(p["item_id"] for p in RECORTE_PROD if p["split"] == "teste")])
fl.gravar_json(E_DIR / "ruidos.json", {p["item_id"]: ANUNCIO[p["item_id"]]["ruido"] for p in RECORTE_PROD})''')

md(r'''## Sinais baratos: texto e imagem pequena
- **Texto:** embeddings `multilingual-e5-base` do título, marca e atributos (**sem** a categoria do vendedor), os **vizinhos** entre os 5 mil produtos de treino e um **Naive Bayes** (TF-IDF) treinado no treino.
- **Imagem pequena (sempre):** **SigLIP 2 base**: (1) **vizinhos por imagem** entre as fotos de treino → voto de categoria; (2) a foto comparada com as **opções de cor e de material** da ficha ("a photo of a *black* product") → atributo visual **com confiança**.
- **Votação** com o mesmo formato do agente (`decidir`): vendedor, NB, vizinhos de texto e, na versão 3, vizinhos por imagem. A regra (quantos sinais concordam, veto, limiar) é **calibrada na validação** e gravada antes de medir o teste.''')

code(r'''from PIL import Image
from transformers import AutoModel, AutoProcessor
t0 = time.time()
SIGLIP = "google/siglip2-base-patch16-224"
sm = AutoModel.from_pretrained(SIGLIP, torch_dtype=torch.float16).to("cuda").eval()
sp_ = AutoProcessor.from_pretrained(SIGLIP)
def emb_imagens(prods, lote=64):
    out = []
    for i in range(0, len(prods), lote):
        imgs = [Image.open(arq_img(p)).convert("RGB") for p in prods[i:i + lote]]
        with torch.no_grad():
            x = sp_(images=imgs, return_tensors="pt").to("cuda")
            f = sm.get_image_features(pixel_values=x["pixel_values"].half())
            out.append(torch.nn.functional.normalize(f.float(), dim=-1).cpu().numpy())
    return np.concatenate(out)
def emb_textos_siglip(textos):
    with torch.no_grad():
        x = sp_(text=textos, padding="max_length", max_length=64, truncation=True, return_tensors="pt").to("cuda")
        f = sm.get_text_features(**x)
        return torch.nn.functional.normalize(f.float(), dim=-1).cpu().numpy()
EMB_IMG_ARQ = E_DIR / "siglip-img.npy"
ids_todos = [p["item_id"] for p in RECORTE_PROD]
if EMB_IMG_ARQ.exists() and np.load(EMB_IMG_ARQ).shape[0] == len(RECORTE_PROD):
    IMG_EMB = np.load(EMB_IMG_ARQ)
    img_por_s = None
else:
    t1 = time.time()
    IMG_EMB = emb_imagens(RECORTE_PROD)
    img_por_s = len(RECORTE_PROD) / (time.time() - t1)
    np.save(EMB_IMG_ARQ, IMG_EMB)
print("SigLIP 2: %s imagens/s | embeddings das fotos: %s" % (None if img_por_s is None else round(img_por_s), IMG_EMB.shape))
LOGIT_SCALE, LOGIT_BIAS = float(sm.logit_scale.exp().item()), float(sm.logit_bias.item())
def zero_shot(opcoes, modelo_prompt):
    """Probabilidades por opcao para cada foto: softmax do cosseno (com a escala do SigLIP) entre a foto e o texto de cada opcao."""
    T = emb_textos_siglip([modelo_prompt % o for o in opcoes])
    logit = LOGIT_SCALE * (IMG_EMB @ T.T)
    logit = logit - logit.max(axis=1, keepdims=True)
    e = np.exp(logit)
    return e / e.sum(axis=1, keepdims=True)
PROB_COR = zero_shot(cores_treino, "a photo of a %s product")
PROB_MAT = zero_shot(mats_treino, "a photo of a product made of %s") if mats_treino else None
idx = {p["item_id"]: i for i, p in enumerate(RECORTE_PROD)}
# acerto do zero-shot de cor no treino (informativo)
tr = [p for p in RECORTE_PROD if p["split"] == "treino" and p["cor"] in cores_treino]
ac = np.mean([cores_treino[int(np.argmax(PROB_COR[idx[p["item_id"]]]))] == p["cor"] for p in tr])
print("SigLIP 2, cor por zero-shot, nos produtos de treino com cor conhecida: %.1f%% de acerto (informativo)" % (100 * ac))''')

code(r'''from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
def texto_modelo(i):
    a = ANUNCIO[i]
    return "%s | marca: %s | %s" % (a["titulo"], RECORTE_PROD[idx[i]]["marca"], ", ".join("%s %s" % (k, v) for k, v in a["atributos"].items()))
TEXTOS = [texto_modelo(i) for i in ids_todos]
EMB_T = pv.Embeddings(device="cuda")
TXT_EMB = EMB_T.codificar(["passage: " + t for t in TEXTOS], str(E_DIR / "e5-texto.npy"))
split_ = np.array([p["split"] for p in RECORTE_PROD])
tipo_ = np.array([p["tipo"] for p in RECORTE_PROD])
i_tr = np.where(split_ == "treino")[0]
vec = TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True)
X = vec.fit_transform([TEXTOS[i] for i in i_tr])
nb = MultinomialNB(alpha=0.1).fit(X, tipo_[i_tr])
PNB = nb.predict_proba(vec.transform(TEXTOS))
NB_CAT = nb.classes_[PNB.argmax(axis=1)]; NB_CONF = PNB.max(axis=1)
def vizinhos_votos(emb, k=10):
    idxs, sims = pv.vizinhos_mais_proximos(emb, emb[i_tr], k + 1, excluir_mesmo_indice=False)
    cat, conf, cont_ = [], [], []
    for r in range(len(emb)):
        pares = [(int(j), float(s)) for j, s in zip(idxs[r], sims[r]) if i_tr[int(j)] != r][:k]
        c = Counter(tipo_[i_tr[j]] for j, _ in pares)
        top = c.most_common(1)[0]
        cat.append(top[0]); conf.append(top[1] / len(pares)); cont_.append(dict(c))
    return cat, conf, cont_
TXT_CAT, TXT_CONF, TXT_CONT = vizinhos_votos(TXT_EMB)
IMG_CAT, IMG_CONF, IMG_CONT = vizinhos_votos(IMG_EMB)
def mapear_vendedor(i):
    n = re.sub(r"[^a-z]", "", ANUNCIO[i]["categoria_vendedor"].lower())
    for t in TIPOS:
        if re.sub(r"[^a-z]", "", t.lower()) == n:
            return t
    return None
VEND = [mapear_vendedor(i) for i in ids_todos]
def acerto_(lista, sp):
    ii = np.where(split_ == sp)[0]
    return round(100 * np.mean([lista[i] == tipo_[i] for i in ii]), 1)
print("acerto de cada sinal sozinho (validacao): vendedor %s%% | NB %s%% | vizinhos de texto %s%% | vizinhos de imagem %s%%" % (acerto_(VEND, "validacao"), acerto_(list(NB_CAT), "validacao"), acerto_(TXT_CAT, "validacao"), acerto_(IMG_CAT, "validacao")))
print("acerto de cada sinal sozinho (teste):     vendedor %s%% | NB %s%% | vizinhos de texto %s%% | vizinhos de imagem %s%%" % (acerto_(VEND, "teste"), acerto_(list(NB_CAT), "teste"), acerto_(TXT_CAT, "teste"), acerto_(IMG_CAT, "teste")))''')

md(r'''## Calibração na validação: regra de votação e limiares dos atributos visuais
**Votação.** Para cada combinação de sinais (versão 2: vendedor + NB + vizinhos de texto; versão 3: + vizinhos de imagem), percorre `min`, exigir um modelo, veto e limiar e fica com a regra que mais aplica entre as que acertam ≥ 98,5% dos aplicados e pioram ≤ 0,5%. **Atributos visuais.** (a) *Preencher a cor quando o texto não diz:* a menor confiança do SigLIP 2 cujo acerto da cor preenchida chega a 90%; abaixo dela, mas acima de um piso, a cor vai ao modelo grande (versão 4). (b) *Cor que contradiz a foto:* o limiar de confiança da imagem e a probabilidade máxima da cor do vendedor, que detectam a contradição sem estragar cor certa (≤ 1% das cores certas trocadas). Tudo vai para `r8-escolhas-val.json` antes de medir o teste.''')

code(r'''def baratos_de(i, usar_imagem):
    r = [pv.semvoto("vendedor", "sem-mapeamento") if VEND[i] is None else pv.voto("vendedor", VEND[i], pv.CONFIANCA_VENDEDOR, False),
         pv.voto("supervisionado", NB_CAT[i], float(NB_CONF[i]), True), pv.voto("texto", TXT_CAT[i], TXT_CONF[i], True)]
    if usar_imagem:
        r.append(pv.voto("imagem", IMG_CAT[i], IMG_CONF[i], True))
    return r
def calibrar_regra(usar_imagem, acerto_min=0.985, pioras_max=0.005):
    ii = np.where(split_ == "validacao")[0]
    usados = ["vendedor", "supervisionado", "texto"] + (["imagem"] if usar_imagem else [])
    bar = {i: baratos_de(i, usar_imagem) for i in ii}
    melhor, tabela = None, []
    for minimo in (2, 3):
        for modelo in (True, False):
            for veto in (0.6, 0.7, 0.8, 0.9, 1.01):
                for limiar in (0.3, 0.4, 0.5, 0.6, 0.7):
                    par = pv.Parametros(minimo, modelo, veto, limiar, {"supervisionado", "texto", "imagem"}).com_sinais(usados)
                    ap = ok = pior = 0
                    for i in ii:
                        d = pv.decidir(bar[i], par)
                        if d.aplicar:
                            ap += 1; ok += d.categoria == tipo_[i]; pior += (VEND[i] == tipo_[i]) and d.categoria != tipo_[i]
                    linha = {"min": minimo, "modelo": modelo, "veto": veto, "limiar": limiar, "aplicados_%": round(100 * ap / len(ii), 2), "acerto_%": round(100 * ok / max(1, ap), 2), "pioras_%": round(100 * pior / len(ii), 3)}
                    tabela.append(linha)
                    if ok / max(1, ap) >= acerto_min and pior / len(ii) <= pioras_max and (melhor is None or (ap, -pior) > (melhor[0], -melhor[1])):
                        melhor = (ap, pior, linha)
    esc = melhor[2] if melhor else max((l for l in tabela if l["aplicados_%"] > 0), key=lambda l: (l["acerto_%"], -l["pioras_%"]))
    return {"escolha": esc, "atingiu_o_piso": melhor is not None, "usados": usados}
R2, R3 = calibrar_regra(False), calibrar_regra(True)
print("regra da versao 2 (so texto):", R2["escolha"], "| piso:", R2["atingiu_o_piso"])
print("regra da versao 3 (+ imagem):", R3["escolha"], "| piso:", R3["atingiu_o_piso"])
PAR2 = pv.Parametros(R2["escolha"]["min"], R2["escolha"]["modelo"], R2["escolha"]["veto"], R2["escolha"]["limiar"], {"supervisionado", "texto", "imagem"}).com_sinais(R2["usados"])
PAR3 = pv.Parametros(R3["escolha"]["min"], R3["escolha"]["modelo"], R3["escolha"]["veto"], R3["escolha"]["limiar"], {"supervisionado", "texto", "imagem"}).com_sinais(R3["usados"])

# ---- atributos visuais: cor
ii_val = [i for i in np.where(split_ == "validacao")[0]]
def cor_img(i):
    k = int(np.argmax(PROB_COR[i]))
    return cores_treino[k], float(PROB_COR[i][k])
# (a) preencher a cor quando o texto nao diz: produtos de validacao com ruido cor_ausente e cor conhecida
ausentes = [i for i in ii_val if ANUNCIO[ids_todos[i]]["ruido"] == "cor_ausente" and RECORTE_PROD[i]["cor"] in cores_treino]
tab_f = []
for lim in (0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9):
    pr = [(cor_img(i)[0] == RECORTE_PROD[i]["cor"]) for i in ausentes if cor_img(i)[1] >= lim]
    tab_f.append({"limiar": lim, "preenchidos": len(pr), "acerto_%": round(100 * np.mean(pr), 1) if pr else None, "cobertura_%": round(100 * len(pr) / max(1, len(ausentes)), 1)})
ok_f = [t for t in tab_f if t["acerto_%"] is not None and t["acerto_%"] >= 90]
LIM_PREENCHER = min((t["limiar"] for t in ok_f), default=0.9)
LIM_DUVIDA = max(0.25, LIM_PREENCHER - 0.25)       # entre LIM_DUVIDA e LIM_PREENCHER a cor vai ao modelo grande
print("cor ausente (validacao): ", tab_f, "-> preenche com confianca >=", LIM_PREENCHER, "| duvida a partir de", LIM_DUVIDA)
# (b) cor que contradiz a foto: o atributo do vendedor diz uma cor e a foto diz outra com confianca
cont_i = [i for i in ii_val if ANUNCIO[ids_todos[i]]["ruido"] == "cor_contradiz"]
certas = [i for i in ii_val if ANUNCIO[ids_todos[i]]["ruido"] in ("controle", "titulo_sem_nome", "categoria_errada") and RECORTE_PROD[i]["cor"] in cores_treino]
def detecta(i, lim_img, max_vend):
    cor_v = ANUNCIO[ids_todos[i]]["atributos"].get("Cor")
    if cor_v not in cores_treino:
        return False
    c, conf = cor_img(i)
    return c != cor_v and conf >= lim_img and PROB_COR[i][cores_treino.index(cor_v)] <= max_vend
tab_c = []
for lim_img in (0.4, 0.5, 0.6, 0.7, 0.8):
    for max_vend in (0.05, 0.1, 0.2, 0.3):
        det = np.mean([detecta(i, lim_img, max_vend) and cor_img(i)[0] == RECORTE_PROD[i]["cor"] for i in cont_i]) if cont_i else 0
        est_ = np.mean([detecta(i, lim_img, max_vend) for i in certas]) if certas else 0
        tab_c.append({"limiar_imagem": lim_img, "max_prob_da_cor_do_vendedor": max_vend, "detectadas_e_corrigidas_%": round(100 * det, 1), "cores_certas_estragadas_%": round(100 * est_, 2)})
ok_c = [t for t in tab_c if t["cores_certas_estragadas_%"] <= 1.0]
ESC_C = max(ok_c, key=lambda t: t["detectadas_e_corrigidas_%"]) if ok_c else min(tab_c, key=lambda t: t["cores_certas_estragadas_%"])
print("cor que contradiz a foto (validacao):", ESC_C)
fl.mesclar_json(SAIDA / "r8-escolhas-val.json", {"imagem": {"regra_versao2": R2, "regra_versao3": R3, "preencher_cor": {"limiar": LIM_PREENCHER, "duvida_a_partir_de": LIM_DUVIDA, "tabela": tab_f},
                                                              "cor_contradiz": {"escolha": ESC_C, "tabela": tab_c}}})''')

md(r'''## Modelo de visão grande, só nas disputas (Qwen2.5-VL-7B-Instruct)
Recebe a **foto**, o **título** e as **opções**, e responde em **JSON restrito às opções** (resposta fora da lista é rejeitada e contada). Só vai para ele o que os sinais baratos **não decidem**: (1) a categoria quando a votação da versão 3 não aplica; (2) a cor quando a confiança do SigLIP 2 está na faixa de dúvida. É a mesma ideia das duas chamadas da Shopify: **categoria primeiro, atributos depois**, com a ficha da categoria. A **quantização** vem da VRAM (bf16 na L4, 8 bits na T4, 4 bits só se não couber) e é registrada.''')

code(r'''from transformers import Qwen2_5_VLForConditionalGeneration, BitsAndBytesConfig
QWEN = "Qwen/Qwen2.5-VL-7B-Instruct"
t0 = time.time()
kw = {"device_map": "auto"}
if QUANT == "bf16":
    kw["torch_dtype"] = torch.bfloat16
elif QUANT == "8bit":
    kw["quantization_config"] = BitsAndBytesConfig(load_in_8bit=True); kw["torch_dtype"] = torch.float16
else:
    kw["quantization_config"] = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_compute_dtype=torch.float16); kw["torch_dtype"] = torch.float16
try:
    qm = Qwen2_5_VLForConditionalGeneration.from_pretrained(QWEN, **kw).eval()
except torch.cuda.OutOfMemoryError:
    torch.cuda.empty_cache()
    if QUANT == "bf16":
        QUANT = "8bit"
        kw = {"device_map": "auto", "quantization_config": BitsAndBytesConfig(load_in_8bit=True), "torch_dtype": torch.float16}
    else:
        QUANT = "4bit"
        kw = {"device_map": "auto", "quantization_config": BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_compute_dtype=torch.float16), "torch_dtype": torch.float16}
    print("sem memoria: carregando em", QUANT)
    qm = Qwen2_5_VLForConditionalGeneration.from_pretrained(QWEN, **kw).eval()
qp = AutoProcessor.from_pretrained(QWEN, min_pixels=64 * 28 * 28, max_pixels=256 * 28 * 28)
qp.tokenizer.padding_side = "left"
print("Qwen2.5-VL-7B carregado em %.0fs | quantizacao: %s | VRAM usada: %.1f GB" % (time.time() - t0, QUANT, torch.cuda.memory_allocated() / 2**30))
QWEN_STATS = {"chamadas": 0, "segundos": 0.0, "rejeitadas": 0, "imagens": 0}
QCACHE = pv.CacheJsonl(str(E_DIR / "qwen-cache.jsonl"))

def perguntar_qwen(i, opcoes_cat=None, opcoes_cor=None):
    """Uma chamada ao modelo de visao para o produto i: JSON restrito as opcoes. Devolve {"categoria": ..., "cor": ...} com so as respostas validas."""
    chave_c = "%s|%s|%s" % (ids_todos[i], ",".join(opcoes_cat or []), ",".join(opcoes_cor or []))
    if chave_c in QCACHE:
        return QCACHE[chave_c]
    a = ANUNCIO[ids_todos[i]]
    partes = ["Voce ve a foto de um produto de loja online e o anuncio do vendedor. O titulo e os atributos do vendedor podem estar errados ou incompletos: confie na foto.\n", "Titulo do vendedor: %s\n" % a["titulo"], "Atributos do vendedor: %s\n" % (a["atributos"] or "nenhum")]
    campos = []
    if opcoes_cat:
        partes.append("Categoria, escolha UMA destas opcoes exatamente como escritas: %s\n" % "; ".join(opcoes_cat)); campos.append('"categoria": "<opcao>"')
    if opcoes_cor:
        partes.append("Cor principal do produto, escolha UMA destas opcoes: %s\n" % "; ".join(opcoes_cor)); campos.append('"cor": "<opcao>"')
    partes.append("Responda so com um JSON no formato {%s}." % ", ".join(campos))
    msgs = [{"role": "user", "content": [{"type": "image"}, {"type": "text", "text": "".join(partes)}]}]
    texto = qp.apply_chat_template(msgs, tokenize=False, add_generation_prompt=True)
    img = Image.open(arq_img(RECORTE_PROD[i])).convert("RGB")
    t1 = time.time()
    x = qp(text=[texto], images=[img], return_tensors="pt", padding=True).to(qm.device)
    with torch.no_grad():
        saida = qm.generate(**x, max_new_tokens=48, do_sample=False)
    resp = qp.batch_decode(saida[:, x["input_ids"].shape[1]:], skip_special_tokens=True)[0]
    QWEN_STATS["chamadas"] += 1; QWEN_STATS["segundos"] += time.time() - t1; QWEN_STATS["imagens"] += 1
    out = {}
    try:
        j = json.loads(resp[resp.find("{"):resp.rfind("}") + 1])
        if opcoes_cat and j.get("categoria") in opcoes_cat:
            out["categoria"] = j["categoria"]
        elif opcoes_cat:
            QWEN_STATS["rejeitadas"] += 1
        if opcoes_cor and j.get("cor") in opcoes_cor:
            out["cor"] = j["cor"]
        elif opcoes_cor:
            QWEN_STATS["rejeitadas"] += 1
    except Exception:
        QWEN_STATS["rejeitadas"] += 1
    QCACHE.gravar([(chave_c, out)])
    return out
# velocidade medida em 20 fotos
teste_i = [i for i in np.where(split_ == "teste")[0][:20]]
t1 = time.time()
for i in teste_i:
    perguntar_qwen(i, opcoes_cat=TIPOS[:6])
seg_por_img = (time.time() - t1) / 20
print("modelo grande: %.2f s por foto (medido em 20 fotos, com cache de respostas); SigLIP 2: %s imagens/s" % (seg_por_img, "ja no cache" if img_por_s is None else round(img_por_s)))''')

md(r'''## As cinco versões, no teste
Todas medem **a categoria final do catálogo** (a decisão do agente onde aplicou e a categoria do vendedor onde manteve), **aplicados**, **acerto dos aplicados** e **pioras** (o vendedor estava certo e o agente trocou), no teste inteiro e por tipo de ruído; e os **atributos de cor** (preencher quando o texto não diz, detectar a cor que contradiz a foto, sem estragar cor certa). A **versão 5** (modelo grande em tudo) roda numa **amostra estratificada de 1.500 produtos de teste** e as versões 2, 3 e 4 são comparadas com ela **na mesma amostra**. A estimativa do tempo aparece antes.''')

code(r'''ii_teste = list(np.where(split_ == "teste")[0])
def final_cat(decisao_cat, i):
    return decisao_cat if decisao_cat is not None else VEND[i]
def metricas_cat(cats_aplicadas, ii, rotulo):
    """cats_aplicadas: dict i -> categoria aplicada (ou ausente = mantido). O catalogo final usa a categoria do vendedor onde mantem."""
    ap = [i for i in ii if i in cats_aplicadas]
    certo = [cats_aplicadas[i] == tipo_[i] for i in ap]
    pior = sum(1 for i in ap if VEND[i] == tipo_[i] and cats_aplicadas[i] != tipo_[i])
    fin = [final_cat(cats_aplicadas.get(i), i) == tipo_[i] for i in ii]
    return {"versao": rotulo, "n": len(ii), "aplicados_%": round(100 * len(ap) / len(ii), 1), "acerto_dos_aplicados_%": round(100 * np.mean(certo), 1) if certo else None, "pioras_%": round(100 * pior / len(ii), 2),
            "acerto_final_do_catalogo_%": round(100 * np.mean(fin), 1)}
def aplicar(par, usar_imagem, ii):
    out = {}
    for i in ii:
        d = pv.decidir(baratos_de(i, usar_imagem), par)
        if d.aplicar:
            out[i] = d.categoria
    return out
V2 = aplicar(PAR2, False, ii_teste)
V3 = aplicar(PAR3, True, ii_teste)
# versao 4: onde a votacao da versao 3 NAO decide, o modelo grande escolhe entre as candidatas dos sinais baratos (+ as mais votadas pelos vizinhos de imagem), restrito a opcoes
disputas = [i for i in ii_teste if i not in V3]
est_s = len(disputas) * seg_por_img
print("versao 4: %d produtos em disputa (%.1f%% do teste) -> ESTIMATIVA %.0f min com o modelo grande (%.2f s por foto)" % (len(disputas), 100 * len(disputas) / len(ii_teste), est_s / 60, seg_por_img))
def candidatas(i):
    c = []
    for x in (VEND[i], NB_CAT[i], TXT_CAT[i], IMG_CAT[i]):
        if x is not None and x not in c:
            c.append(x)
    for t_, _ in sorted(IMG_CONT[i].items(), key=lambda kv: -kv[1]):
        if t_ not in c and len(c) < 6:
            c.append(t_)
    return c[:6]
V4 = dict(V3)
t0 = time.time()
for n_, i in enumerate(disputas):
    c = candidatas(i)
    r = perguntar_qwen(i, opcoes_cat=c)
    if r.get("categoria") and sum(1 for x in (VEND[i], NB_CAT[i], TXT_CAT[i], IMG_CAT[i]) if x == r["categoria"]) >= 1:
        V4[i] = r["categoria"]          # so vale se ao menos um sinal barato concorda com o modelo grande
    if (n_ + 1) % 500 == 0:
        print("  modelo grande: %d/%d | %.0f s | rejeitadas %d" % (n_ + 1, len(disputas), time.time() - t0, QWEN_STATS["rejeitadas"]))
USO_GRANDE = len(disputas) / len(ii_teste)
print("versao 4 pronta em %.0f min | uso do modelo grande: %.1f%% dos produtos" % ((time.time() - t0) / 60, 100 * USO_GRANDE))
TAB = [metricas_cat({i: VEND[i] for i in ii_teste if VEND[i] is not None}, ii_teste, "1. Antes (categoria do vendedor)"), metricas_cat(V2, ii_teste, "2. So texto"), metricas_cat(V3, ii_teste, "3. Texto + imagem pequena"),
       metricas_cat(V4, ii_teste, "4. + modelo de visao nas disputas")]
pd.set_option("display.width", 250)
print(pd.DataFrame(TAB).to_string(index=False))
# por tipo de ruido
POR_RUIDO = {}
for ru in RUIDOS:
    sub = [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] == ru]
    POR_RUIDO[ru] = [metricas_cat({i: VEND[i] for i in sub if VEND[i] is not None}, sub, "1. Antes"), metricas_cat({i: c for i, c in V2.items() if i in set(sub)}, sub, "2. So texto"),
                     metricas_cat({i: c for i, c in V3.items() if i in set(sub)}, sub, "3. + imagem pequena"), metricas_cat({i: c for i, c in V4.items() if i in set(sub)}, sub, "4. + modelo grande")]
    print("\n== ruido: %s (%d produtos)" % (ru, len(sub)))
    print(pd.DataFrame(POR_RUIDO[ru])[["versao", "n", "aplicados_%", "acerto_dos_aplicados_%", "pioras_%", "acerto_final_do_catalogo_%"]].to_string(index=False))''')

code(r'''# ---- versao 5: o modelo grande em TUDO, numa amostra estratificada (por tipo de ruido) de ate 1.500 produtos de teste
N5 = 1500
por_r = defaultdict(list)
for i in ii_teste:
    por_r[ANUNCIO[ids_todos[i]]["ruido"]].append(i)
amostra5 = []
for ru, l in por_r.items():
    l = sorted(l, key=lambda i: hashlib.sha256(ids_todos[i].encode()).hexdigest())
    amostra5.extend(l[:max(1, round(N5 * len(l) / len(ii_teste)))])
print("versao 5: %d produtos -> ESTIMATIVA %.0f min (modelo grande em tudo, com todas as %d categorias como opcao)" % (len(amostra5), len(amostra5) * seg_por_img * 1.3 / 60, len(TIPOS)))
V5 = {}
t0 = time.time()
for n_, i in enumerate(amostra5):
    r = perguntar_qwen(i, opcoes_cat=TIPOS)
    if r.get("categoria"):
        V5[i] = r["categoria"]
    if (n_ + 1) % 300 == 0:
        print("  versao 5: %d/%d | %.0f s" % (n_ + 1, len(amostra5), time.time() - t0))
S5 = set(amostra5)
cmp5 = [metricas_cat({i: VEND[i] for i in amostra5 if VEND[i] is not None}, amostra5, "1. Antes"), metricas_cat({i: c for i, c in V2.items() if i in S5}, amostra5, "2. So texto"),
        metricas_cat({i: c for i, c in V3.items() if i in S5}, amostra5, "3. + imagem pequena"), metricas_cat({i: c for i, c in V4.items() if i in S5}, amostra5, "4. + modelo grande nas disputas"),
        metricas_cat(V5, amostra5, "5. Modelo grande em tudo")]
print(pd.DataFrame(cmp5).to_string(index=False))
QWEN_SEG_POR_FOTO = QWEN_STATS["segundos"] / max(1, QWEN_STATS["chamadas"])''')

md(r'''## Atributos visuais: cor e material
- **Cor quando o texto não diz** (ruído `cor_ausente`): o agente preenche com a cor da foto se a confiança do SigLIP 2 chega ao limiar calibrado; na faixa de dúvida, pergunta ao modelo grande (restrito às cores da ficha). Mede o **acerto no que for preenchido**.
- **Cor que contradiz a foto** (ruído `cor_contradiz`): detecta e corrige se o SigLIP 2 vê outra cor com confiança e dá pouca probabilidade à cor do vendedor. Mede a **taxa detectada e corrigida** e as **cores certas estragadas** (nos produtos sem contradição, o agente trocou uma cor certa).
- **Material** (informativo): zero-shot do SigLIP 2 contra o material do ABO quando o anúncio não traz.''')

code(r'''def acerto_cor():
    # (a) cor ausente
    aus = [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] == "cor_ausente" and RECORTE_PROD[i]["cor"] in cores_treino]
    preench, certos_, ao_grande, ok_grande = 0, 0, 0, 0
    for i in aus:
        c, conf = cor_img(i)
        if conf >= LIM_PREENCHER:
            preench += 1; certos_ += c == RECORTE_PROD[i]["cor"]
        elif conf >= LIM_DUVIDA:
            r = perguntar_qwen(i, opcoes_cor=cores_treino)
            ao_grande += 1
            if r.get("cor"):
                preench += 1; certos_ += r["cor"] == RECORTE_PROD[i]["cor"]; ok_grande += r["cor"] == RECORTE_PROD[i]["cor"]
    # (b) contradiz
    cont_ = [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] == "cor_contradiz"]
    det = sum(1 for i in cont_ if detecta(i, ESC_C["limiar_imagem"], ESC_C["max_prob_da_cor_do_vendedor"]) and cor_img(i)[0] == RECORTE_PROD[i]["cor"])
    detectadas_qualquer = sum(1 for i in cont_ if detecta(i, ESC_C["limiar_imagem"], ESC_C["max_prob_da_cor_do_vendedor"]))
    cert = [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] in ("controle", "titulo_sem_nome", "categoria_errada") and RECORTE_PROD[i]["cor"] in cores_treino]
    estrag = sum(1 for i in cert if detecta(i, ESC_C["limiar_imagem"], ESC_C["max_prob_da_cor_do_vendedor"]))
    return {"cor_ausente": {"produtos": len(aus), "preenchidos": preench, "acerto_no_preenchido_%": round(100 * certos_ / max(1, preench), 1), "cobertura_%": round(100 * preench / max(1, len(aus)), 1), "foram_ao_modelo_grande": ao_grande,
                            "acerto_do_modelo_grande_%": round(100 * ok_grande / max(1, ao_grande), 1) if ao_grande else None},
            "cor_contradiz": {"produtos": len(cont_), "detectadas_e_corrigidas_%": round(100 * det / max(1, len(cont_)), 1), "detectadas_qualquer_%": round(100 * detectadas_qualquer / max(1, len(cont_)), 1)},
            "cores_certas_estragadas": {"produtos": len(cert), "estragadas": estrag, "%": round(100 * estrag / max(1, len(cert)), 2)}}
COR = acerto_cor()
print(json.dumps(COR, ensure_ascii=False, indent=1))
# material (informativo): preenche so quando o anuncio nao traz e a confianca do SigLIP 2 e alta
mat_ok = mat_n = 0
if PROB_MAT is not None:
    for i in ii_teste:
        if RECORTE_PROD[i]["material"] in mats_treino and not ANUNCIO[ids_todos[i]]["atributos"].get("Material"):
            k = int(np.argmax(PROB_MAT[i]))
            if PROB_MAT[i][k] >= 0.6:
                mat_n += 1; mat_ok += mats_treino[k] == RECORTE_PROD[i]["material"]
print("material (informativo): %d preenchidos com confianca >= 0,6, acerto %.1f%%" % (mat_n, 100 * mat_ok / max(1, mat_n)))''')

md(r'''## Custo, exemplos e resultado
**Custo:** % dos produtos que vão ao modelo grande, imagens por segundo de cada modelo na GPU, tempo total e a **projeção** (marcada como projeção) para 100 mil produtos e para 10 mil itens novos por dia. **Exemplos lado a lado:** 6 produtos com a foto (mostradas aqui; o resumo guarda só o id da imagem, para não redistribuir fotos), o título ruim, o que o texto sozinho decidiu e o que texto + imagem decidiu. Grava `imagem-r8-resumo.json` e `resultados/e.json`.''')

code(r'''siglip_ips = img_por_s or (len(RECORTE_PROD) / max(1.0, 60.0))
seg_sig = 1.0 / siglip_ips
seg_q = QWEN_SEG_POR_FOTO
def projecao(n):
    # por produto: embeddings (SigLIP) + a parte que vai ao modelo grande (USO_GRANDE)
    return round(n * (seg_sig + USO_GRANDE * seg_q) / 3600, 2)
CUSTO = {"uso_do_modelo_grande_%": round(100 * USO_GRANDE, 2), "siglip2_imagens_por_s": None if img_por_s is None else round(img_por_s, 1), "modelo_grande_s_por_foto": round(seg_q, 2), "modelo_grande_fotos_por_s": round(1 / seg_q, 3) if seg_q else None,
         "quantizacao": QUANT, "gpu": GPU.name, "tempo_total_do_teste_min": round((QWEN_STATS["segundos"]) / 60, 1), "respostas_rejeitadas_do_modelo_grande": QWEN_STATS["rejeitadas"],
         "PROJECAO_horas_para_100_mil_produtos": projecao(100000), "PROJECAO_horas_para_10_mil_itens_novos_por_dia": projecao(10000),
         "observacao": "as duas horas sao PROJECAO linear: (embedding por foto + fracao que vai ao modelo grande x segundos por foto) x n; nao foram medidas em 100 mil"}
print(json.dumps(CUSTO, ensure_ascii=False, indent=1))
# exemplos: 6 produtos de teste em que o texto sozinho errou e o texto + imagem acertou (ou, na falta, os primeiros de titulo_sem_nome)
exs = [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] == "titulo_sem_nome" and (V2.get(i) != tipo_[i]) and (V4.get(i) == tipo_[i])][:6]
if len(exs) < 6:
    exs += [i for i in ii_teste if ANUNCIO[ids_todos[i]]["ruido"] == "titulo_sem_nome" and i not in exs][:6 - len(exs)]
EXEMPLOS = []
from IPython.display import display
for i in exs:
    EXEMPLOS.append({"imagem_id": RECORTE_PROD[i]["imagem"], "titulo_ruim": ANUNCIO[ids_todos[i]]["titulo"], "categoria_certa": tipo_[i], "o_texto_sozinho_decidiu": V2.get(i) or "(manteve a do vendedor: %s)" % (VEND[i],),
                     "texto_mais_imagem_decidiu": V4.get(i) or V3.get(i) or "(manteve)", "ruido": ANUNCIO[ids_todos[i]]["ruido"]})
    img = Image.open(arq_img(RECORTE_PROD[i])).convert("RGB"); img.thumbnail((160, 160)); display(img)
    print(EXEMPLOS[-1])''')

code(r'''def frac(x):
    return None if x is None else x / 100.0
sem_nome = POR_RUIDO["titulo_sem_nome"]
ganho_sem_nome = sem_nome[3]["acerto_final_do_catalogo_%"] - sem_nome[1]["acerto_final_do_catalogo_%"]       # versao 4 contra so texto, no ruido principal
ganho_geral = TAB[3]["acerto_final_do_catalogo_%"] - TAB[1]["acerto_final_do_catalogo_%"]
c5 = {x["versao"]: x for x in cmp5}
dif_4_5 = c5["5. Modelo grande em tudo"]["acerto_final_do_catalogo_%"] - c5["4. + modelo grande nas disputas"]["acerto_final_do_catalogo_%"]
MEDIDAS_E = {
    "img_titulo_sem_nome": {"ganho_sobre_so_texto_pontos": round(ganho_sem_nome, 1)},
    "img_teste_inteiro": {"ganho_sobre_so_texto_pontos": round(ganho_geral, 1), "pioras": frac(TAB[3]["pioras_%"])},
    "img_cor_nao_dita": {"acerto_no_preenchido": frac(COR["cor_ausente"]["acerto_no_preenchido_%"])},
    "img_cor_contradiz": {"detectada": frac(COR["cor_contradiz"]["detectadas_e_corrigidas_%"]), "cores_certas_estragadas": frac(COR["cores_certas_estragadas"]["%"])},
    "img_modelo_grande": {"fracao_dos_produtos": USO_GRANDE, "versao4_abaixo_da_versao5_pontos": round(max(0.0, dif_4_5), 1)},
}
RESUMO = {"licenca": "ABO, CC BY-NC 4.0: so avaliacao interna; imagens nao redistribuidas nem no repositorio; modelos abertos baixados no Colab; nenhuma imagem vai a servico externo",
          "dados": {"produtos": len(RECORTE_PROD), "tipos": TIPOS, "cobertura_dos_campos": cob, "splits": dict(Counter(p["split"] for p in RECORTE_PROD)), "opcoes_de_cor": cores_treino, "opcoes_de_material": mats_treino,
                    "tamanhos_remotos_mb": {n: (None if t < 0 else round(t / 2**20, 1)) for n, (_, t) in TAMANHOS.items()}, "imagens_avulsas": AVULSAS},
          "modelos": {"texto": "intfloat/multilingual-e5-base", "imagem_pequena": SIGLIP, "imagem_grande": QWEN, "quantizacao_do_modelo_grande": QUANT, "gpu": GPU.name},
          "regras_calibradas_na_validacao": {"versao2": R2, "versao3": R3, "preencher_cor": {"limiar": LIM_PREENCHER, "duvida": LIM_DUVIDA}, "cor_contradiz": ESC_C},
          "versoes_no_teste": TAB, "por_ruido": POR_RUIDO, "versao5_na_amostra": cmp5, "atributos": COR, "material_informativo": {"preenchidos": mat_n, "acerto_%": round(100 * mat_ok / max(1, mat_n), 1)},
          "custo": CUSTO, "exemplos": EXEMPLOS, "medidas_dos_criterios": MEDIDAS_E}
fl.gravar_json(SAIDA / "imagem-r8-resumo.json", RESUMO)
fl.gravar_json(SAIDA / "resultados" / "e.json", {"medidas": MEDIDAS_E, "quantizacao": QUANT})
QCACHE.fechar()
for l in fl.checar_criterios(CRITERIOS, MEDIDAS_E):
    if l["situacao"] != "nao medido":
        print("%-9s %-62s %s" % (l["situacao"].upper(), l["medida"][:62], l["medido"]))
fl.concluir_bloco(str(SAIDA), "e", {"quantizacao": QUANT, "gpu": GPU.name, "produtos": len(RECORTE_PROD)})
print("bloco E concluido. Proximo: laya_r8_f_painel.")''')
