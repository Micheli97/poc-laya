# -*- coding: utf-8 -*-
# Bloco B: treino do Laya (laya3-ft-5000). Cada item: ("md" | "code", texto).
from r8_nb_comum import AVISOS, BLOCOS, AMBIENTE, exigir

CEL = []
def md(t): CEL.append(("md", t))
def code(t): CEL.append(("code", t))

md(r'''# Rodada 8, bloco B: ajuste fino do Laya (`laya3-ft-5000`)

**O que este bloco faz**
1. **Portão de paridade das linhas de treino.** O ajuste fino da rodada 6 usou as linhas geradas pelo `GeradorTreinoLayaV2` (Java). Aqui elas são geradas em **Python** (porta do gerador, com o mesmo `java.util.Random` e a mesma ordem de opções). O notebook gera as linhas da **base original** e as compara com o `questions.json` do `laya2-ft-5000` (uma pergunta por id, a de uma das linhas de treino): os ids precisam ser os mesmos e a pergunta de cada id precisa ser **igual** (instruções e opções, na mesma ordem) a uma das geradas em Python. Se divergir, o notebook **para, mostra a diferença** e oferece o **plano B**: instalar `openjdk-17-jdk-headless` no Colab e rodar o gerador Java **original** para as perguntas antigas (`USAR_PLANO_B = True`).
2. Gera as linhas da **base nova** (`--rodada8`): as perguntas antigas (pelo caminho que passou no portão) mais as **novas**, só em Python: "este produto usa energia elétrica?", o diagnóstico de nós da árvore (**só dos nós de treino**) e a opção "nenhuma destas" com os tipos de fora da taxonomia do **grupo de treino** (os do teste nunca aparecem).
3. Treina o `laya3-ft-5000` a partir do **mesmo checkpoint base** (`multilingual`) e com a mesma configuração da rodada 6 (4 épocas, micro-lote 8, acumulação 8, amp, checkpointing); **pula** se já existir no Drive.
4. **Trava do novo checkpoint** (nome, hash do `model.safetensors`, pergunta de controle de departamento e de energia).

**GPU.** Prefira a **L4 (24 GB)**; a T4 (16 GB) também serve (na rodada 6 o pico foi de 6,2 GB). **Tempo (estimativa):** na rodada 6, 5.000 exemplos levaram 8.502 s (~2 h 22 min) na T4 do Kaggle; aqui as linhas novas acrescentam cerca de 30%, então espere **~3 h na T4 (estimativa; a L4 deve ser mais rápida, não medido)**. Se a sessão cair no meio do treino, o que estava treinando se perde (a retomada no meio não foi verificada); checkpoints já terminados ficam no Drive.

''' + BLOCOS + "\n\n" + AVISOS)

code(AMBIENTE.replace("__GPU__", "sim") + "\n" + exigir("a") + "\ngarantir_bases(original=True)\nimport torch\nprint('GPU:', torch.cuda.get_device_name(0), round(torch.cuda.get_device_properties(0).total_memory / 2**30, 1), 'GB')\nUSAR_PLANO_B = False   # True so se o portao das linhas falhar")

md(r'''## Portão: as linhas de treino em Python reproduzem o gerador Java?
Gera as linhas do `treino_5000` da **base original** (prefixo `laya2-5000`, semente 42, 8% de "nenhuma destas") e compara com o `questions.json` do `laya2-ft-5000`. Mostra também as contagens de linhas contra o `treino_gpu.json` do checkpoint (informativo: o significado exato de `train_items` não foi verificado).''')

code(r'''pv.ativar_limpeza_de_entrada(False)      # o portao compara com o gerador Java ORIGINAL, que nao tinha a limpeza de entrada
tx0 = pv.Taxonomia(str(D_ORIG / "taxonomia.json"))
exemplos0, proibidos0 = pv.exemplos_de_treino(str(D_ORIG), "treino_5000", tx0)
t0 = time.time()
tr0, va0, ids_tr0, ids_va0, cont0 = pv.linhas_de_treino_do_laya(tx0, exemplos0, proibidos0, semente=42, fracao_validacao=0.1, fracao_nenhuma=0.08)
print("linhas Python (base original): %d de treino, %d de validacao em %.0fs | por pergunta: %s" % (len(tr0), len(va0), time.time() - t0, dict(cont0)))
qj = json.load(open(CK_DRIVE / "laya2-ft-5000" / "questions.json", encoding="utf-8"))
cmp0 = pv.comparar_linhas_com_questions_json(tr0 + va0, qj)
tg = fl.ler_json(CK_DRIVE / "laya2-ft-5000" / "treino_gpu.json")
contagens = {"checkpoint_train_items": tg.get("train_items"), "checkpoint_calibration_items": tg.get("calibration_items"), "checkpoint_eval_items": tg.get("eval_items"),
             "python_linhas_treino": len(tr0), "python_linhas_validacao": len(va0)}
print("contagens (informativo):", contagens)
resumo_portao = {k: cmp0[k] for k in ("ok", "perguntas_no_checkpoint", "perguntas_no_python", "n_diferentes")}
print("portao:", resumo_portao)
if not cmp0["ok"]:
    print("FALTAM no Python:", cmp0["faltam_no_python"][:15])
    print("SOBRAM no Python:", cmp0["sobram_no_python"][:15])
    for d in cmp0["diferentes"][:5]:
        print("DIFERENTE", d["id"], "\n  no checkpoint:", d["no_checkpoint"], "\n  em Python    :", d["python_exemplo"])
fl.gravar_json(SAIDA / "b" / "portao-linhas.json", {"comparacao": cmp0, "contagens": contagens})
if not cmp0["ok"] and not USAR_PLANO_B:
    raise RuntimeError("PORTAO DAS LINHAS FALHOU: as linhas em Python NAO reproduzem as do checkpoint laya2-ft-5000. Veja a diferenca acima. Para seguir, ligue o PLANO B (USAR_PLANO_B = True na celula de ambiente): "
                       "instala o JDK no Colab e roda o gerador Java original para as perguntas antigas (a pasta java-fonte/ precisa estar no Drive).")
LINHAS_ANTIGAS = "python" if cmp0["ok"] else "plano-b"
print("portao das linhas:", "OK (Python == checkpoint)" if cmp0["ok"] else "FALHOU: seguindo pelo plano B")''')

code(r'''# ---- PLANO B: so roda se o portao falhou
JAVA_LINHAS = {}
if LINHAS_ANTIGAS == "plano-b":
    fonte = DRIVE / "java-fonte"
    assert (fonte / "src" / "main" / "java").exists(), "Falta %s/src/main/java (copie a pasta src/main/java do projeto para o Drive)." % fonte
    saida_j = TRAB / "java-linhas-orig"
    arq = pv.plano_b_java(str(fonte), str(D_ORIG), str(saida_j), "treino_5000", "laya2-5000", 0.08, 42)
    linhas_j = list(pv.ler_jsonl(arq)) + list(pv.ler_jsonl(arq.replace(".train.jsonl", ".val.jsonl")))
    cmp_j = pv.comparar_linhas_com_questions_json(linhas_j, qj)
    print("o gerador Java ORIGINAL reproduz o checkpoint?", cmp_j["ok"], "| diferentes:", cmp_j["n_diferentes"])
    fl.gravar_json(SAIDA / "b" / "plano-b-java.json", {"java_vs_checkpoint": {k: cmp_j[k] for k in ("ok", "n_diferentes", "faltam_no_python", "sobram_no_python")}, "linhas_java": len(linhas_j)})
    if not cmp_j["ok"]:
        raise RuntimeError("Nem o gerador Java original reproduz o questions.json do checkpoint: o checkpoint foi treinado com outra versao do gerador. Pare e investigue.")
    print("plano B ok: as perguntas antigas da base nova virao do gerador Java original.")
else:
    print("plano B nao necessario.")''')

md(r'''## As linhas da base nova
- **Perguntas antigas** (departamento, categoria com 1 e 2 departamentos, subcategoria com "nenhuma destas" em 8%, ficha técnica): do gerador que passou no portão.
- **Perguntas novas (só em Python):** (1) `usa_energia` (sim / não / não dá para saber), uma linha por produto, rótulo pela **ficha da subcategoria** (voltagem, potência, bateria...), nunca pelo anúncio; (2) `tipo_do_no` e `atributo_do_no`, **só dos nós de treino** da árvore legada (o gabarito dos nós de teste nunca é lido), com os títulos dos produtos do `treino_20000` pendurados em cada nó; (3) `nenhuma_destas` para os tipos de fora da taxonomia do **grupo de treino** (`fora_treino.jsonl`, split treino); os tipos do grupo de teste nunca entram.''')

code(r'''pv.ativar_limpeza_de_entrada(True)       # as linhas da base nova ja usam a limpeza de entrada (2.1: sem metades de emoji nem caracteres de controle)
tx = pv.Taxonomia(str(D_R8 / "taxonomia.json"))
usa_en = pv.usa_energia_por_sub(tx)
print("subcategorias que usam energia:", sum(usa_en.values()), "de", len(usa_en), "| ex. churrasqueiras_carvao:", usa_en["churrasqueiras_carvao"], "| churrasqueiras_eletricas:", usa_en["churrasqueiras_eletricas"])
exemplos8, proibidos8 = pv.exemplos_de_treino(str(D_R8), "treino_5000", tx)
if LINHAS_ANTIGAS == "python":
    tr8, va8, ids_tr8, ids_va8, cont8 = pv.linhas_de_treino_do_laya(tx, exemplos8, proibidos8, semente=42, fracao_validacao=0.1, fracao_nenhuma=0.08)
else:
    saida_j8 = TRAB / "java-linhas-r8"
    arq8 = pv.plano_b_java(str(DRIVE / "java-fonte"), str(D_R8), str(saida_j8), "treino_5000", "laya3-5000", 0.08, 42)
    tr8, va8 = list(pv.ler_jsonl(arq8)), list(pv.ler_jsonl(arq8.replace(".train.jsonl", ".val.jsonl")))
    cont8 = {"origem": "java"}
print("perguntas antigas: %d de treino, %d de validacao" % (len(tr8), len(va8)))

# ---- energia
l_energia = pv.linhas_de_energia(tx, exemplos8, usa_en)
# ---- nos da arvore legada: so os nos de treino
spn = fl.ler_json(D_R8 / "arvore_splits.json")
treino_nos = set(spn["treino"])
nos = fl.ler_json(D_R8 / "arvore_legada.json")
gab_treino = {n["id"]: n for n in fl.ler_json(D_R8 / "arvore_gabarito.json") if n["id"] in treino_nos}      # o gabarito dos nos de teste e descartado aqui
nos_treino = [n for n in nos if n["id"] in gab_treino]
ok_ids = set(fl.ler_json(D_R8 / "splits.json")["treino_20000"])
titulos = defaultdict(list)
for m in pv.ler_jsonl(str(D_R8 / "produtos.jsonl")):
    if m["id"] in ok_ids and m.get("categoria_legada"):
        titulos[m["categoria_legada"]].append(m["titulo"])
titulos = {k: sorted(v, key=lambda t: pv.hash6(t.encode("utf-8")))[:5] for k, v in titulos.items()}
l_nos = pv.linhas_de_nos_do_laya(tx, nos_treino, gab_treino, titulos)
# ---- nenhuma destas (tipos de fora do grupo de treino)
fora_tr = [m for m in pv.ler_jsonl(str(D_R8 / "fora_treino.jsonl")) if m["split"] == "treino"]
l_fora = pv.linhas_fora_da_taxonomia(tx, fora_tr)
novas = l_energia + l_nos + l_fora
novas = pv.java_shuffle(list(novas), 42)
n_val = int(len(novas) * 0.1 + 0.5)
novas_val, novas_tr = novas[:n_val], novas[n_val:]
TREINO, VAL = tr8 + novas_tr, va8 + novas_val
por_pergunta = Counter(next(iter(l["expected"])) for l in TREINO)
print("linhas novas: energia %d | nos %d (de %d nos de treino) | nenhuma_destas %d (%d itens fora do grupo de treino)" % (len(l_energia), len(l_nos), len(nos_treino), len(l_fora), len(fora_tr)))
print("TOTAL: %d de treino, %d de validacao | por pergunta (treino):" % (len(TREINO), len(VAL)), dict(por_pergunta))
# nenhum tipo do grupo de TESTE pode aparecer no treino
tipos_teste = set(pv_ for pv_ in ("paineis_solares", "patinetes_eletricos", "fechaduras_digitais", "cameras_seguranca"))
assert not any(m["gabarito"].get("sugestao_nova_subcategoria") in tipos_teste for m in fora_tr), "tipo do grupo de teste no treino"
DADOS_T = DRIVE / "laya-treino" / "dados"; DADOS_T.mkdir(parents=True, exist_ok=True)
pv.escrever_jsonl(str(DADOS_T / "laya3-5000.train.jsonl"), TREINO)
pv.escrever_jsonl(str(DADOS_T / "laya3-5000.val.jsonl"), VAL)
fl.gravar_json(DADOS_T / "laya3-5000.resumo.json", {"linhas_treino": len(TREINO), "linhas_validacao": len(VAL), "por_pergunta_treino": dict(por_pergunta), "origem_das_perguntas_antigas": LINHAS_ANTIGAS,
                                                  "nos_de_treino": len(nos_treino), "itens_fora_do_grupo_de_treino": len(fora_tr)})
print("linhas gravadas em", DADOS_T)''')

md(r'''## Treino
Mesma configuração da rodada 6, a partir do `multilingual`. O notebook mede o tempo e o pico de VRAM, e copia o checkpoint para `laya-ft/laya3-ft-5000/` do Drive só ao terminar.''')

code(r'''import laya.train as _lt
from laya.train import TrainConfig, finetune
_orig = _lt.calibration_records
def _no_dispositivo(model, tok, items, device, *a, **k):
    model.to(device)
    return _orig(model, tok, items, device, *a, **k)
_lt.calibration_records = _no_dispositivo      # laya 0.4.0: o "antes" e medido com o modelo ainda na CPU; so move o modelo, nao altera o calculo

NOME = "laya3-ft-5000"
destino_final = CK_DRIVE / NOME
LOCAL = Path("/content/treino-local"); LOCAL.mkdir(exist_ok=True)
if (destino_final / "model.safetensors").exists() and (destino_final / "treino_gpu.json").exists():
    print("[%s] ja esta no Drive: pulando o treino" % NOME)
    INFO_TREINO = fl.ler_json(destino_final / "treino_gpu.json")
else:
    cfg = TrainConfig(epochs=4, micro_batch=8, grad_accum=8, amp=True, gradient_checkpointing=True, eval_data=str(DADOS_T / "laya3-5000.val.jsonl"), seed=0)
    local = LOCAL / NOME
    shutil.rmtree(local, ignore_errors=True)
    torch.cuda.reset_peak_memory_stats()
    t0 = time.time()
    r = finetune(str(DADOS_T / "laya3-5000.train.jsonl"), "multilingual", str(local), cfg, device="cuda")
    INFO_TREINO = {"segundos": round(time.time() - t0, 1), "gpu": torch.cuda.get_device_name(0), "pico_vram_alocada_mib": round(torch.cuda.max_memory_allocated() / 2**20), "train_items": r["train_items"],
                   "calibration_items": r["calibration_items"], "eval_items": r["eval_items"], "temperature": r["temperature"], "epoch_loss": r["epoch_loss"], "eval_mode": r["train_report"]["eval_mode"],
                   "antes": r["train_report"]["before"], "depois": r["train_report"]["after"]}
    json.dump(INFO_TREINO, open(local / "treino_gpu.json", "w"), indent=2)
    shutil.rmtree(local / "checkpoint_latest", ignore_errors=True)
    shutil.copytree(local, destino_final, dirs_exist_ok=True)
    shutil.rmtree(local, ignore_errors=True)
print({k: INFO_TREINO.get(k) for k in ("segundos", "gpu", "pico_vram_alocada_mib", "eval_mode", "train_items")}, "| acerto antes/depois:", INFO_TREINO["antes"]["accuracy"], "->", INFO_TREINO["depois"]["accuracy"])
HASH_L3 = pv.sha256_arquivo(str(destino_final / "model.safetensors"))
fl.mesclar_json(CK_DRIVE / "hashes-r8.json", {NOME: HASH_L3})
print("sha256 do model.safetensors de %s: %s" % (NOME, HASH_L3))''')

md(r'''## Trava do novo checkpoint
Carrega o `laya3-ft-5000`, imprime nome e hash e confere, em itens de **validação da base nova**: (1) o departamento de 120 produtos (≥ 90%, e superar o Laya base em 20 pontos); (2) a pergunta de energia em 120 produtos (≥ 90% de acerto contra a ficha da subcategoria). Mede também, sem exigir nada, quanto o modelo responde "nenhuma destas" nos itens de fora da taxonomia da validação.''')

code(r'''CK = TRAB / "ck"; CK.mkdir(exist_ok=True)
shutil.copytree(CK_DRIVE / NOME, CK / NOME, dirs_exist_ok=True)
CONTROLE8 = pv.controle_departamento(str(D_R8), tx, 120)
BASE = None
try:
    BASE = pv.carregar_laya("multilingual")
except Exception as e:
    print("Laya base indisponivel (%s): confiro so o acerto absoluto." % type(e).__name__)
AG3 = pv.carregar_laya(str(CK / NOME))
TRAVA3 = pv.verificar_checkpoint(NOME, str(CK / NOME), tx, CONTROLE8, AG3, BASE)
del BASE; torch.cuda.empty_cache()
LOTE3 = pv.LayaLote(AG3, batch_size=32)
print(NOME, "| sha256:", TRAVA3["sha256_model_safetensors"], "| departamento: ajustado %.1f%%" % (100 * TRAVA3["acerto_departamento_ajustado"]))

# energia: 120 produtos da validacao da base nova, rotulo = ficha da subcategoria
rot_val = fl.rotulos_para_calibrar(str(D_R8), "validacao")
ids_c = [p.id for p in CONTROLE8[0]]
estados_c, _ = pv.estados_dos_produtos(CONTROLE8[0])
res_en = LOTE3.decidir_lote(estados_c, {pv.PERGUNTA_ENERGIA_ID: pv.PERGUNTA_ENERGIA})
acerto_en = sum(1 for i, r in zip(ids_c, res_en) if r[pv.PERGUNTA_ENERGIA_ID][0] == ("sim" if usa_en[rot_val[i]["subcategoria"]] else "nao")) / len(ids_c)
print("controle de energia: %.1f%% de acerto" % (100 * acerto_en))
assert acerto_en >= 0.90, "TRAVA: o checkpoint nao aprendeu a pergunta de energia (%.1f%%)" % (100 * acerto_en)

# nenhuma destas nos itens de fora da taxonomia da validacao (informativo)
fora_val = [m for m in pv.ler_jsonl(str(D_R8 / "fora_treino.jsonl")) if m["split"] == "validacao"][:200]
pf = [pv.Produto(m) for m in fora_val]
cs = pv.cascata_laya_com_nenhuma(LOTE3, tx, [pv.preparar(p)[0] for p in pf])
taxa_nenhuma = sum(1 for c in cs if c["nenhuma"]) / max(1, len(cs))
print("itens de fora da taxonomia (validacao, tipos do grupo de treino) respondidos como 'nenhuma destas': %.1f%% de %d" % (100 * taxa_nenhuma, len(cs)))
fl.gravar_json(SAIDA / "b" / "trava-laya3.json", {"checkpoint": TRAVA3, "acerto_energia": acerto_en, "nenhuma_destas_na_validacao_%": round(100 * taxa_nenhuma, 1), "treino": INFO_TREINO,
                                                 "origem_das_perguntas_antigas": LINHAS_ANTIGAS})
fl.concluir_bloco(str(SAIDA), "b", {"checkpoint": NOME, "sha256": TRAVA3["sha256_model_safetensors"], "segundos_de_treino": INFO_TREINO.get("segundos"), "gpu": INFO_TREINO.get("gpu"),
                                    "origem_das_perguntas_antigas": LINHAS_ANTIGAS})
print("bloco B concluido. Proximo: laya_r8_c_f1_e_f2.")''')
