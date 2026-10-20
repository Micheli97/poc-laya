# -*- coding: utf-8 -*-
"""
perguntas_poc.py: as nove perguntas da PoC, onde cada uma e respondida (notebook, secao, arquivo de resultado), quais criterios fixos (r8-criterios.json) a medem e o que se espera.
Python puro, sem dependencias: e importado pelos notebooks (para escrever o quadro "perguntas que este notebook responde"), pelo fluxo_r8.py (para dar o veredito de cada pergunta
no painel) e pelo construtor dos notebooks. O README.md da PoC repete este mapa com contexto e indicacoes.
"""

NB_C = "laya_r8_c_f1_e_f2.ipynb"
NB_D = "laya_r8_d_novos_e_busca.ipynb"
NB_E = "imagem_r8.ipynb"
NB_F = "laya_r8_f_painel.ipynb"
NB_A = "laya_r8_a_bases_e_paridade.ipynb"
NB_B = "laya_r8_b_treino_laya.ipynb"

PERGUNTAS = [
    {
        "id": "P1", "grupo": "central", "titulo": "Catalogo existente organizado",
        "pergunta": "O catalogo existente fica organizado? Cada produto vai para a categoria certa e a ficha tecnica fica padronizada, sem estragar o que ja estava certo?",
        "onde": [
            (NB_C, "F1, passo 1 e 2 / passo 3: diagnostico da arvore antiga e migracao dos 100 mil", "saida-r8/c/resultados-arvore.json, saida-r8/c/resultados-migracao.json"),
            (NB_C, "F2: dado errado do vendedor (categoria e ficha tecnica nos produtos de teste)", "saida-r8/c/resultados-f2.json"),
            (NB_C, "Correcao de regra da ficha (F2), feita depois da primeira execucao (diagnostico + regra v2)", "saida-r8/c/resultados-f2-correcao.json, saida-r8/c/diagnostico-f2.json"),
            (NB_D, "Itens novos > catalogo existente: duplicados e familias de variacao", "saida-r8/d/resultados-grupos.json"),
        ],
        "criterios": ["rodada7_mantida", "mantidos_sem_decisao", "atributos_estragados", "nao_se_aplica", "contradiz_anuncio", "fora_do_normal", "sem_pista_inventado", "nao_eletricos_no_filtro", "faixa_duvida_llm",
                      "no_atributo", "no_lixo", "no_destino", "arvore_tamanho", "migracao", "atributo_no_legado", "duplicados_existente"],
        "sem_criterio": "Aplicados, acerto dos aplicados, pioras e acerto dos atributos inferiveis, por versao (Antes, Classificador, Classificador + agente + Laya, Agente v3): tabela do painel (secao 2).",
        "esperado": "Categoria certa em pelo menos 98% dos aplicados, pioras de no maximo 0,5% e atributos certos estragados em no maximo 1%; arvore de ~1.700 nos reduzida a no maximo a taxonomia nova + 5%.",
    },
    {
        "id": "P2", "grupo": "central", "titulo": "Busca mais eficiente",
        "pergunta": "A busca fica mais eficiente? Por texto (inclusive com erro de digitacao) e por filtros, o cliente encontra mais produtos certos?",
        "onde": [(NB_D, "Busca (texto, filtros, erro de digitacao; antes e depois)", "saida-r8/d/resultados-busca.json")],
        "criterios": ["busca_bivolt", "rodada7_mantida"],
        "sem_criterio": "P@10, NDCG e cobertura por tipo de consulta (cabeca, atributo, marca, sinonimo, erro_digitacao, corrigidas) e a navegacao por categoria e filtros: tabela do painel (secao 5).",
        "esperado": "P@10 e cobertura maiores que a busca por palavra sobre o catalogo como chegou; sem piorar mais de 0,5 ponto em relacao a rodada 7; \"fogao bivolt\" e afins com +10 pontos de P@10.",
    },
    {
        "id": "P3", "grupo": "central", "titulo": "Itens novos entram certos",
        "pergunta": "Os itens novos entram certos? Os cadastros novos sao classificados corretamente e os invalidos sao barrados?",
        "onde": [(NB_D, "Itens novos (classificados e invalidos)", "saida-r8/d/resultados-novos.json")],
        "criterios": ["novos_classificados", "invalidos"],
        "sem_criterio": "Acerto por tipo de item novo (normal, marca nova, vendedor novo...): campo por_tipo do resultado.",
        "esperado": "Acerto de pelo menos 98% nos classificados; invalidos com precisao e cobertura de pelo menos 95%.",
    },
    {
        "id": "P4", "grupo": "pendente", "titulo": "Duplicados, variacoes, fora da taxonomia, limitrofes",
        "pergunta": "Itens novos pendentes: o agente acha duplicados, separa duplicado de variacao (outra cor ou voltagem do mesmo produto), reconhece produto fora da taxonomia e nao barra cadastro legitimo por engano?",
        "onde": [
            (NB_D, "Itens novos (duplicados com original exato e por grupo, variacoes, fora da taxonomia, limitrofes)", "saida-r8/d/resultados-novos.json"),
            (NB_D, "Subcategorias novas e fora da taxonomia, com a sugestao de nome", "saida-r8/d/subcategorias-criadas.json"),
            (NB_D, "Diagnostico dos erros da rodada 7 (outra copia, variacao ou produto diferente)", "saida-r8/d/diagnostico-r7.json"),
        ],
        "criterios": ["novos_duplicados", "novos_variacoes", "fora_taxonomia", "fora_taxonomia_dep", "subcategorias_novas", "invalidos"],
        "sem_criterio": "Erros de duplicado por motivo, por pista (EAN igual, sem EAN, titulo reescrito, marca diferente) e a amostra de nomes sugeridos para conferir a olho.",
        "esperado": "Duplicados com precisao >= 95% e cobertura >= 80% (a rodada 7 fez 75,0% / 77,2%); variacoes >= 90% / 80%; fora da taxonomia >= 90% / 70% (a rodada 7: 91,5% / 19,7%); limitrofes barrados por engano <= 2%.",
    },
    {
        "id": "P5", "grupo": "pendente", "titulo": "Ambiguidade",
        "pergunta": "Ambiguidade: quando um produto pode estar em duas categorias (lavadora de alta pressao em Ferramentas ou em Automotivo), o agente escolhe a principal e sugere a secundaria?",
        "onde": [(NB_C, "Ambiguidade", "saida-r8/c/resultados-f2.json (bloco rodada8 > ambiguidade)")],
        "criterios": ["ambiguidade"],
        "sem_criterio": "Principal certa, aceita qualquer das duas e quantas secundarias foram sugeridas.",
        "esperado": "Principal certa em pelo menos 95% e secundaria sugerida com precisao de pelo menos 80%.",
    },
    {
        "id": "P6", "grupo": "pendente", "titulo": "Busca sem exagero",
        "pergunta": "Busca sem exagero: uma consulta sem resposta volta vazia, em vez de forcar um resultado? E uma consulta com modelo (\"capinha iphone 15\") respeita o modelo?",
        "onde": [(NB_D, "Busca (grupos sem_resposta e com_modelo; exemplos \"geladera de praia nuclear\", \"capinha iphone 15\")", "saida-r8/d/resultados-busca.json")],
        "criterios": ["busca_sem_resposta", "busca_com_modelo"],
        "sem_criterio": "Precisao da correcao de digitacao (grupo corrigidas) e o limiar escolhido na validacao (r8-escolhas-val.json > busca_correcao).",
        "esperado": "Pelo menos 90% das consultas sem resposta voltam vazias ou com aviso; consultas com modelo com P@10 de pelo menos 90%.",
    },
    {
        "id": "P7", "grupo": "pendente", "titulo": "Texto + imagem",
        "pergunta": "Texto + imagem: a foto do produto melhora a categoria e os atributos visuais quando o texto do vendedor e ruim? E quanto do trabalho da para resolver com um modelo de imagem pequeno, deixando o grande so para as disputas?",
        "onde": [(NB_E, "As cinco versoes, no teste / Atributos visuais: cor e material / Custo, exemplos e resultado", "saida-r8/imagem-r8-resumo.json")],
        "criterios": ["img_titulo_sem_nome", "img_teste_inteiro", "img_cor_nao_dita", "img_cor_contradiz", "img_modelo_grande"],
        "sem_criterio": "As cinco versoes lado a lado, por tipo de ruido, o custo (imagens por segundo, tempo, % que vai ao modelo grande) e a PROJECAO para 100 mil produtos.",
        "esperado": "Texto + imagem com +15 pontos sobre so texto quando o titulo nao traz o nome do produto e +3 pontos no teste inteiro; modelo grande em no maximo 10% dos produtos, com a versao 4 a no maximo 1 ponto da versao 5.",
    },
    {
        "id": "P8", "grupo": "pendente", "titulo": "Automacao total com seguranca",
        "pergunta": "Automacao total com seguranca: da para aplicar tudo sem aprovacao humana, com acoes reversiveis, auditoria automatica e freio quando a taxa de erro sobe?",
        "onde": [
            (NB_D, "Modo autonomo: auditoria, freio e reversao", "saida-r8/d/resultados-autonomo.json, saida-r8/d/jornal-acoes.jsonl"),
            (NB_D, "Relatorio de excecoes", "saida-r8/d/relatorio-de-excecoes.json"),
        ],
        "criterios": ["auditoria_automatica", "freio_automatico", "reversao"],
        "sem_criterio": "Erro real x erro estimado de cada lote, o limite do freio (calibrado na validacao) e o juiz usado (LLM ou regras, quando nao ha chave).",
        "esperado": "Erro estimado a no maximo 1 ponto do erro real; o lote ruim injetado pausado e revertido (100%); reversao deixando o catalogo identico (mesmo hash).",
    },
    {
        "id": "P9", "grupo": "pendente", "titulo": "Custo de LLM",
        "pergunta": "Custo: as novidades melhoram a qualidade sem aumentar o uso de LLM? A meta e que nao mais de 5,4% dos produtos vao ao LLM na categoria.",
        "onde": [
            (NB_C, "F2: versao com LLM nas disputas de categoria (amostra) e estimativa de chamadas antes de rodar", "saida-r8/c/resultados-f2-llm.json"),
            (NB_D, "Quanto cada peca ajuda (qualidade e consumo lado a lado)", "saida-r8/d/resultados-ablacao.json"),
        ],
        "criterios": ["consumo_llm_categoria", "consumo_llm_total"],
        "sem_criterio": "Chamadas por produto, tokens de entrada e saida e % de atributos ao LLM, por versao (tabela da ablacao).",
        "esperado": "No maximo 5,4% dos produtos vao ao LLM na categoria (o valor da rodada 7); atributos ao LLM em no maximo 10% dos avaliados.",
    },
]

POR_ID = {p["id"]: p for p in PERGUNTAS}


def perguntas_do_notebook(arquivo: str) -> list:
    """As perguntas que o notebook `arquivo` ajuda a responder."""
    return [p for p in PERGUNTAS if any(o[0] == arquivo for o in p["onde"])]


def quadro_markdown(arquivo: str) -> str:
    """O quadro 'Perguntas da PoC respondidas neste notebook' para a primeira celula de texto de cada notebook."""
    ps = perguntas_do_notebook(arquivo)
    if not ps:
        return ""
    linhas = ["### Perguntas da PoC que este notebook responde", "", "| # | Pergunta | Onde neste notebook | Resultado gravado |", "|---|---|---|---|"]
    for p in ps:
        for (nb, secao, arq) in p["onde"]:
            if nb == arquivo:
                linhas.append("| **%s** | %s | %s | `%s` |" % (p["id"], p["pergunta"], secao, arq.replace("|", "/")))
    linhas += ["", "O README da PoC (`poc_laya/README.md`) explica o contexto, a indicacao e o que se espera de cada pergunta; o painel (notebook F) junta as respostas."]
    return "\n".join(linhas)


def tag(*ids) -> str:
    """O rotulo curto que aparece nos titulos de secao: '(responde P1, P4)'."""
    return "(responde %s)" % ", ".join(ids)


def veredito(pergunta: dict, linhas_por_id: dict) -> dict:
    """O veredito de uma pergunta a partir das linhas de criterio ja checadas (fluxo_r8.checar_criterios). Nunca afirma 'sim' sem todos os criterios medidos e atendidos.
    Devolve {status: sim|nao|parcial|nao_medido, atendem, nao_atendem, nao_medidos, criterios: [...]}."""
    atendem, nao, nm = [], [], []
    for cid in pergunta["criterios"]:
        l = linhas_por_id.get(cid)
        s = l["situacao"] if l else "nao medido"
        (atendem if s == "atende" else nao if s == "nao atende" else nm).append(cid)
    if not pergunta["criterios"]:
        status = "nao_medido"
    elif nao:
        status = "nao"
    elif nm and atendem:
        status = "parcial"
    elif nm:
        status = "nao_medido"
    else:
        status = "sim"
    return {"id": pergunta["id"], "status": status, "atendem": atendem, "nao_atendem": nao, "nao_medidos": nm, "criterios": pergunta["criterios"]}


ROTULO_STATUS = {"sim": "SIM: todos os criterios medidos atendem", "nao": "NAO: ha criterio que nao atende", "parcial": "PARCIAL: parte dos criterios nao foi medida", "nao_medido": "NAO MEDIDO"}
