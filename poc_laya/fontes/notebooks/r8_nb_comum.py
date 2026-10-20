# -*- coding: utf-8 -*-
# Trechos de celula compartilhados pelos notebooks da rodada 8 (cada notebook e um bloco: a, b, c, d, e, f). So texto: nada roda aqui.

AVISOS = r'''**Antes de rodar**
- **Dados sintéticos.** Com **dados reais de cliente**, enviar produtos ao Colab, ao Google Drive, ao Kaggle ou ao Ollama Cloud **precisa de validação do time**. Isto vale para qualquer bloco.
- **Chaves só em Colab Secrets** (`OLLAMA_API_KEY`, `HF_TOKEN`): ícone de chave, à esquerda, com "Acesso pelo notebook" ligado. Nunca cole chave numa célula, no Drive ou num relatório. A chave do Ollama que foi colada numa conversa da rodada 6 **deve ser rotacionada**.
- **Cada bloco grava o resultado no Drive** (`MyDrive/laya-teste-r8/saida-r8/`) e o próximo retoma de lá. Se a sessão cair, rode as células de novo: o progresso (Laya, embeddings, votos do LLM) fica em `saida-r8/cache/`.
- Tempos que aparecem aqui são **estimativas**; o notebook mede e imprime os reais. Projeções para mais produtos estão marcadas como **projeção**.'''

BLOCOS = r'''| Bloco | Notebook | O que faz |
|---|---|---|
| **a** | `laya_r8_a_bases_e_paridade` | confere o hash da base original (**portão**), gera a base nova com `--rodada8` e prova que o `pipeline_v3` com as novidades desligadas reproduz a rodada 7 (**paridade**) |
| **b** | `laya_r8_b_treino_laya` | linhas de treino do Laya em Python (**portão** de paridade com o gerador Java), perguntas novas e ajuste fino do `laya3-ft-5000` |
| **c** | `laya_r8_c_f1_e_f2` | vizinhos e estatística, calibração, **F1** (árvore de ~1.700 nós e migração), **F2** (dado errado do vendedor) e ambiguidade |
| **d** | `laya_r8_d_novos_e_busca` | itens novos (duplicados, variações, fora da taxonomia, inválidos), catálogo existente, busca, modo autônomo (auditoria, freio, reversão) e quanto cada peça ajuda |
| **e** | `imagem_r8` | trilha de texto + imagem com fotos reais (ABO, CC BY-NC 4.0) |
| **f** | `laya_r8_f_painel` | junta tudo no painel `r8-resumo.html` e em `r8-painel-dados.json`, com Atende / Não atende por critério |'''

# ---- celula de ambiente de TODOS os blocos menos o a: monta o Drive, copia os arquivos, instala o necessario e importa o pipeline_v3
AMBIENTE = r'''import subprocess, sys, os, json, time, shutil, hashlib, math, random
from pathlib import Path
from collections import Counter, defaultdict
import numpy as np

r = subprocess.run("nvidia-smi --query-gpu=name,memory.total --format=csv,noheader", shell=True, capture_output=True, text=True)
print("GPU:", r.stdout.strip() or "NENHUMA (este bloco precisa de GPU: Ambiente de execução > Alterar o tipo > GPU)")
if "__GPU__" == "sim":
    assert r.stdout.strip(), "Sem GPU."
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "laya==0.4.0", "sentence-transformers", "scipy", "matplotlib", "requests", "pandas"], check=True)
from importlib.metadata import version
print("laya", version("laya"), "| sentence-transformers", version("sentence-transformers"))
assert version("laya") == "0.4.0", "o treino usou o laya 0.4.0"
r = subprocess.run("node --version", shell=True, capture_output=True, text=True)
if r.returncode != 0:
    subprocess.run("apt-get -qq update && apt-get -qq install -y nodejs", shell=True, check=True)
    r = subprocess.run("node --version", shell=True, capture_output=True, text=True)
print("node", r.stdout.strip()); assert int(r.stdout.strip().lstrip("v").split(".")[0]) >= 18, "precisa do Node 18 ou mais novo"

from google.colab import drive
drive.mount("/content/drive")
DRIVE = Path("/content/drive/MyDrive/laya-teste-r8")
TRAB = Path("/content/trabalho"); TRAB.mkdir(parents=True, exist_ok=True)
SAIDA = DRIVE / "saida-r8"; SAIDA.mkdir(parents=True, exist_ok=True)
CACHE = SAIDA / "cache"; CACHE.mkdir(exist_ok=True)
CK_DRIVE = DRIVE / "laya-ft"

OBRIGATORIOS = ["catalogo-v2", "pipeline_v3.py", "fluxo_r8.py", "perguntas_poc.py", "r8-criterios.json", "r6-escolha-val.json", "r6-escolha-val-d.json", "r6-referencias.json", "taxonomia.json"]
faltam = [n for n in OBRIGATORIOS if not (DRIVE / n).exists()]
if faltam:
    raise FileNotFoundError("Faltam em %s: %s. Veja o LEIA-ME.md." % (DRIVE, faltam))
for n in OBRIGATORIOS:
    origem, destino = DRIVE / n, TRAB / n
    if origem.is_dir():
        shutil.copytree(origem, destino, dirs_exist_ok=True, ignore=shutil.ignore_patterns("dados", "dados-*"))
    else:
        shutil.copy(origem, destino)
sys.path.insert(0, str(TRAB))
import pipeline_v3 as pv
import fluxo_r8 as fl
GER = TRAB / "catalogo-v2" / "gerador" / "gerar-catalogo-v2.mjs"
AVAL = str(TRAB / "catalogo-v2" / "avaliacao" / "avaliar.mjs")
D_ORIG, D_R8 = TRAB / "dados-orig", TRAB / "dados-r8"
SEMENTE_R8, RUIDO_R8 = 20261013, 1.5

def gerar(saida, *extra):
    if (saida / "produtos.jsonl").exists() and (saida / "splits.json").exists():
        return
    subprocess.run(["node", str(GER), "--saida", str(saida), *extra], check=True)

def garantir_bases(original=False):
    """Gera de novo (a semente fixa garante os mesmos arquivos) e confere o hash da base nova contra o gravado pelo bloco a. A original so se pedir."""
    gerar(D_R8, "--rodada8", "--semente", str(SEMENTE_R8), "--ruido", str(RUIDO_R8))
    ref = fl.ler_json(SAIDA / "a" / "hashes-base-r8.json")
    atual = {n: pv.sha256_arquivo(str(D_R8 / n)) for n in ref}
    dif = [n for n in ref if atual[n] != ref[n]]
    if dif:
        raise RuntimeError("A base --rodada8 gerada agora NAO e igual a do bloco a (%s): a versao do gerador mudou. Refaça do bloco a." % dif)
    if original:
        gerar(D_ORIG)
    sp = json.load(open(D_R8 / "splits.json", encoding="utf-8"))
    if "todos" not in sp:   # o avaliador de novos so aceita nomes de split em --ids; "todos" e so uma lista de ids, nao muda nenhum dado
        sp["todos"] = [m["id"] for m in pv.ler_jsonl(str(D_R8 / "novos.jsonl"))]
        json.dump(sp, open(D_R8 / "splits.json", "w", encoding="utf-8"))

def carregar_chave_llm():
    """A chave do Ollama entra so pela variavel de ambiente, vinda do Colab Secrets. Nunca e impressa. Devolve True se existe."""
    try:
        from google.colab import userdata
        os.environ["OLLAMA_API_KEY"] = userdata.get("OLLAMA_API_KEY")
    except Exception:
        pass
    return bool(os.environ.get("OLLAMA_API_KEY"))

def estado_bloco(b):
    return fl.ler_json(SAIDA / "estado.json").get(b)
def avaliar_saneamento(prev_jsonl, ids_arquivo, dados=None, extra=None):
    return pv.avaliar(AVAL, "saneamento", str(dados or D_R8), pred=prev_jsonl, ids_arquivo=ids_arquivo, extra=extra)

print("ambiente pronto | pipeline:", pv.VERSAO_PIPELINE)'''

# ---- portao de que o bloco anterior terminou
def exigir(bloco):
    return "est = fl.exigir_bloco(str(SAIDA), \"%s\")\nprint(\"bloco %s: ok em\", est[\"%s\"][\"quando\"])" % (bloco, bloco, bloco)


# ---- chamada comum de avaliacao
AVALIAR_AUX = r'''def avaliar_saneamento(prev_jsonl, ids_arquivo, dados=None, extra=None):
    return pv.avaliar(AVAL, "saneamento", str(dados or D_R8), pred=prev_jsonl, ids_arquivo=ids_arquivo, extra=extra)'''
