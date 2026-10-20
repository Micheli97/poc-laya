package br.com.projeto.curadoria.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Taxonomia alvo, lida de um JSON mantido pelo negocio (nao fica no codigo).
 * Gera as perguntas enviadas ao modelo de decisao.
 */
public final class Taxonomia {

    public record Pergunta(String instrucoes, LinkedHashMap<String, String> criterios) {}

    final LinkedHashMap<String, String> departamentos = new LinkedHashMap<>();
    final LinkedHashMap<String, String> departamentosEn = new LinkedHashMap<>();
    final Map<String, LinkedHashMap<String, String>> categoriasPorDepto = new HashMap<>();
    final Map<String, LinkedHashMap<String, String>> categoriasPorDeptoEn = new HashMap<>();
    final Map<String, String> deptoDaCategoria = new HashMap<>();
    final Map<String, String> nomeCategoria = new HashMap<>();
    final Map<String, String> tipoPadrao = new HashMap<>();          // categoria -> tipo (ausente = perguntar ao modelo)
    final Set<String> categoriasEletricas = new HashSet<>();
    final Set<String> categoriasComCor = new HashSet<>();
    final LinkedHashMap<String, String> tipos = new LinkedHashMap<>();
    final LinkedHashMap<String, String> voltagens = new LinkedHashMap<>();
    final LinkedHashMap<String, String> cores = new LinkedHashMap<>();
    final LinkedHashMap<String, List<String>> sinonimosCor = new LinkedHashMap<>();
    final Set<String> termosGenericosVendedor = new HashSet<>();
    final Map<String, String> aliasMarca = new HashMap<>();
    /** nome da categoria sem acento, sem pontuacao e em minusculas -> id */
    private final Map<String, String> categoriaPorNomeNormalizado = new LinkedHashMap<>();
    /** Hash do conteudo do arquivo: muda quando a taxonomia muda (entra na versao dos sinais). */
    private final String versao;

    // ---- taxonomia v2 (3 niveis: departamento > categoria > subcategoria). No resto do agente, "categoria" = a FOLHA:
    // na v1 e a categoria, na v2 e a subcategoria. Os niveis intermediarios (categoria da v2) aqui se chamam "grupo".
    /** chave de sinonimos_valor com as escritas de sim/nao (valem para todo atributo booleano) */
    public static final String BOOLEANO = "_booleano";
    private boolean v2;
    final Map<String, LinkedHashMap<String, String>> gruposPorDepto = new HashMap<>();
    final Map<String, LinkedHashMap<String, String>> gruposPorDeptoEn = new HashMap<>();
    final Map<String, LinkedHashMap<String, String>> folhasPorGrupo = new LinkedHashMap<>();
    final Map<String, LinkedHashMap<String, String>> folhasPorGrupoEn = new HashMap<>();
    final Map<String, String> deptoDoGrupo = new HashMap<>();
    final Map<String, String> grupoDaFolha = new HashMap<>();
    final Map<String, String> nomeGrupo = new HashMap<>();
    final Map<String, String> caminhoDaFolha = new HashMap<>();
    final Map<String, String> tipoDaFolha = new HashMap<>();
    final Map<String, List<String>> termosDaFolha = new LinkedHashMap<>();
    final Map<String, String> nomeEnDaFolha = new HashMap<>();
    final Map<String, List<AtributoDef>> atributosPorFolha = new HashMap<>();
    final Map<String, DefinicaoAtributo> definicaoAtributo = new LinkedHashMap<>();
    final Map<String, Map<String, List<String>>> sinonimosValor = new LinkedHashMap<>();
    /** nome ou caminho da folha, normalizado -> id da folha (mapeia a categoria que o vendedor escreveu) */
    private final Map<String, String> folhaPorNomeOuCaminho = new LinkedHashMap<>();
    final Set<String> categoriasGenericas = new HashSet<>();

    /** Um atributo da ficha tecnica de uma subcategoria. tipo: enum | num | texto. */
    public record AtributoDef(String chave, String nome, String nomeEn, String tipo, List<String> valores, Double min, Double max,
                              String unidade, List<String> aliasesVendedor) {}

    /** O que e comum a todas as subcategorias que usam o atributo: nome, unidade e como o vendedor costuma escrever a chave. */
    public record DefinicaoAtributo(String chave, String nome, String nomeEn, String unidade, List<String> aliasesVendedor) {}

    @SuppressWarnings("unchecked")
    public Taxonomia(Path arquivo) throws IOException {
        String conteudo = Files.readString(arquivo);
        this.versao = ClassificadorTexto.hashDe(conteudo);
        Map<String, Object> t = (Map<String, Object>) Json.parse(conteudo);
        if (ehV2(t)) { carregarV2(t); return; }
        for (Object o : (List<Object>) t.get("departamentos")) {
            Map<String, Object> d = (Map<String, Object>) o;
            String id = (String) d.get("id");
            departamentos.put(id, d.get("nome") + ": " + d.get("criterio"));
            departamentosEn.put(id, d.get("nome") + ": " + ou((String) d.get("criterio_en"), (String) d.get("criterio")));
            LinkedHashMap<String, String> cats = new LinkedHashMap<>();
            LinkedHashMap<String, String> catsEn = new LinkedHashMap<>();
            for (Object co : (List<Object>) d.get("categorias")) {
                Map<String, Object> c = (Map<String, Object>) co;
                String cid = (String) c.get("id");
                cats.put(cid, c.get("nome") + ": " + c.get("criterio"));
                catsEn.put(cid, c.get("nome") + ": " + ou((String) c.get("criterio_en"), (String) c.get("criterio")));
                deptoDaCategoria.put(cid, id);
                nomeCategoria.put(cid, (String) c.get("nome"));
                categoriaPorNomeNormalizado.put(normalizarParaCasamento((String) c.get("nome")), cid);
                if (c.get("tipo_padrao") instanceof String tp) tipoPadrao.put(cid, tp);
                if (Boolean.TRUE.equals(c.get("eletrico"))) categoriasEletricas.add(cid);
                if (Boolean.TRUE.equals(c.get("tem_cor"))) categoriasComCor.add(cid);
            }
            categoriasPorDepto.put(id, cats);
            categoriasPorDeptoEn.put(id, catsEn);
        }
        ((Map<String, Object>) t.get("tipo_item")).forEach((k, v) -> tipos.put(k, (String) v));
        ((Map<String, Object>) t.get("voltagem")).forEach((k, v) -> voltagens.put(k, (String) v));
        ((Map<String, Object>) t.get("cor")).forEach((k, v) -> cores.put(k, (String) v));
        ((Map<String, Object>) t.get("marcas")).forEach((canon, als) -> {
            for (Object a : (List<Object>) als) aliasMarca.put(Texto.normalizar((String) a), canon);
        });
        if (t.get("sinonimos_cor") instanceof Map<?, ?> sc)
            sc.forEach((k, v) -> sinonimosCor.put((String) k, ((List<Object>) v).stream().map(Object::toString).toList()));
        if (t.get("termos_genericos_vendedor") instanceof List<?> tg)
            for (Object g : tg) termosGenericosVendedor.add(normalizarParaCasamento(g.toString()));
    }

    // =====================================================================
    // Perguntas ao modelo
    // =====================================================================

    /** 1a chamada da cascata (comportamento original): departamento, tipo, voltagem e cor. */
    public Map<String, Pergunta> perguntasNivel1() { return perguntasNivel1(Melhorias.nenhuma()); }

    /** 1a chamada: so pergunta o que o codigo nao resolve. Com as melhorias 2 e 3 ligadas, so o departamento. */
    public Map<String, Pergunta> perguntasNivel1(Melhorias m) {
        boolean en = m.ingles();
        Map<String, Pergunta> q = new LinkedHashMap<>();
        q.put("departamento", new Pergunta(en
                ? "Which department of an e-commerce store should this product be listed in? Ignore the seller's category if it contradicts the title."
                : "Em qual departamento de um e-commerce este produto deve ficar? Ignore a categoria do vendedor se ela contradisser o titulo.",
                en ? departamentosEn : departamentos));
        if (!m.tipoPorCategoria()) q.put("tipo_item", perguntaTipo(en));
        if (!m.atributosPorCodigo()) {
            q.put("voltagem", new Pergunta("Qual a voltagem do produto?", voltagens));
            q.put("cor", new Pergunta("Qual a cor principal do produto?", cores));
        }
        return q;
    }

    /** Pergunta de tipo (usada na 1a chamada, ou depois da categoria quando ela nao tem tipo_padrao). */
    public Pergunta perguntaTipo(boolean en) {
        return new Pergunta(en
                ? "Is the listing for the main product, a spare part or accessory FOR another product, or a toy/miniature?"
                : "O anuncio e do produto principal, de uma peca/acessorio PARA outro produto, ou de um brinquedo/miniatura?", tipos);
    }

    /** Perguntas para classificar uma CONSULTA de busca (sem voltagem e cor). */
    public Map<String, Pergunta> perguntasConsulta() { return perguntasConsulta(Melhorias.nenhuma()); }

    public Map<String, Pergunta> perguntasConsulta(Melhorias m) {
        Map<String, Pergunta> n1 = perguntasNivel1(m);
        Map<String, Pergunta> q = new LinkedHashMap<>();
        q.put("departamento", n1.get("departamento"));
        if (n1.containsKey("tipo_item")) q.put("tipo_item", n1.get("tipo_item"));
        return q;
    }

    /** 2a chamada: categoria SO entre as do departamento escolhido. */
    public Map<String, Pergunta> perguntasNivel2(String depto) { return perguntasCategoria(List.of(depto), Melhorias.nenhuma()); }

    /**
     * 2a chamada: categoria entre as dos departamentos dados (1 normalmente, 2 no modo top-2).
     * Com mais de um departamento, no maximo {@link Melhorias#MAX_OPCOES_CATEGORIA} opcoes, alternando entre eles
     * para que o 2o departamento nunca fique sem representacao.
     */
    public Map<String, Pergunta> perguntasCategoria(List<String> deptos, Melhorias m) {
        boolean en = m.ingles();
        LinkedHashMap<String, String> opcoes = new LinkedHashMap<>();
        if (deptos.size() == 1) {
            opcoes.putAll((en ? categoriasPorDeptoEn : categoriasPorDepto).get(deptos.get(0)));
        } else {
            List<Iterator<Map.Entry<String, String>>> its = deptos.stream()
                    .map(d -> (en ? categoriasPorDeptoEn : categoriasPorDepto).get(d).entrySet().iterator()).toList();
            boolean algum = true;
            while (algum && opcoes.size() < Melhorias.MAX_OPCOES_CATEGORIA) {
                algum = false;
                for (var it : its) {
                    if (it.hasNext() && opcoes.size() < Melhorias.MAX_OPCOES_CATEGORIA) { var e = it.next(); opcoes.put(e.getKey(), e.getValue()); algum = true; }
                }
            }
        }
        String instr = deptos.size() == 1
                ? (en ? "Which category of this department does the product fit in?" : "Em qual categoria deste departamento o produto se encaixa?")
                : (en ? "Which category does the product fit in? The options come from the most likely departments."
                      : "Em qual categoria o produto se encaixa? As opcoes vem dos departamentos mais provaveis.");
        return Map.of("categoria", new Pergunta(instr, opcoes));
    }

    // =====================================================================
    // Taxonomia v2: departamento > categoria (grupo) > subcategoria (folha), com ficha tecnica por subcategoria
    // =====================================================================
    private static boolean ehV2(Map<String, Object> t) {
        if (!(t.get("departamentos") instanceof List<?> ds) || ds.isEmpty() || !(ds.get(0) instanceof Map<?, ?> d)) return false;
        return d.get("categorias") instanceof List<?> cs && !cs.isEmpty() && cs.get(0) instanceof Map<?, ?> c && c.containsKey("subcategorias");
    }

    @SuppressWarnings("unchecked")
    private void carregarV2(Map<String, Object> t) {
        v2 = true;
        Map<String, Object> defs = t.get("atributos") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        defs.forEach((chave, v) -> {
            Map<String, Object> d = (Map<String, Object>) v;
            definicaoAtributo.put(chave, new DefinicaoAtributo(chave, (String) d.get("nome"), (String) d.get("nome_en"), (String) d.get("unidade"), strings(d.get("aliases_vendedor"))));
        });
        // sinonimos_valor: por atributo (voltagem, cor: valor canonico -> escritas) e, no nivel de cima, as escritas de sim/nao (valem para todo atributo booleano)
        if (t.get("sinonimos_valor") instanceof Map<?, ?> sv) sv.forEach((chave, vals) -> {
            if (vals instanceof Map<?, ?> m) {
                Map<String, List<String>> porValor = new LinkedHashMap<>();
                ((Map<String, Object>) m).forEach((canonico, lista) -> porValor.put(canonico, strings(lista)));
                sinonimosValor.put((String) chave, porValor);
            } else {
                sinonimosValor.computeIfAbsent(BOOLEANO, k -> new LinkedHashMap<>()).put((String) chave, strings(vals));
            }
        });
        for (String g : strings(t.get("categorias_genericas"))) categoriasGenericas.add(normalizarParaCasamento(g));

        for (Object o : (List<Object>) t.get("departamentos")) {
            Map<String, Object> d = (Map<String, Object>) o;
            String did = (String) d.get("id");
            departamentos.put(did, d.get("nome") + ": " + d.get("criterio"));
            departamentosEn.put(did, ou((String) d.get("nome_en"), (String) d.get("nome")) + ": " + ou((String) d.get("criterio_en"), (String) d.get("criterio")));
            LinkedHashMap<String, String> grupos = new LinkedHashMap<>(), gruposEn = new LinkedHashMap<>();
            for (Object go : (List<Object>) d.get("categorias")) {
                Map<String, Object> g = (Map<String, Object>) go;
                String gid = (String) g.get("id");
                grupos.put(gid, g.get("nome") + ": " + g.get("criterio"));
                gruposEn.put(gid, ou((String) g.get("nome_en"), (String) g.get("nome")) + ": " + ou((String) g.get("criterio_en"), (String) g.get("criterio")));
                deptoDoGrupo.put(gid, did);
                nomeGrupo.put(gid, (String) g.get("nome"));
                LinkedHashMap<String, String> folhas = new LinkedHashMap<>(), folhasEn = new LinkedHashMap<>();
                for (Object fo : (List<Object>) g.get("subcategorias")) {
                    Map<String, Object> f = (Map<String, Object>) fo;
                    String fid = (String) f.get("id");
                    folhas.put(fid, f.get("nome") + ": " + f.get("criterio"));
                    folhasEn.put(fid, ou((String) f.get("nome_en"), (String) f.get("nome")) + ": " + ou((String) f.get("criterio_en"), (String) f.get("criterio")));
                    deptoDaCategoria.put(fid, did);
                    grupoDaFolha.put(fid, gid);
                    nomeCategoria.put(fid, (String) f.get("nome"));
                    caminhoDaFolha.put(fid, (String) f.get("caminho"));
                    termosDaFolha.put(fid, strings(f.get("termos")));
                    if (f.get("nome_en") instanceof String ne) nomeEnDaFolha.put(fid, ne);
                    if (f.get("tipo") instanceof String tp) { tipoDaFolha.put(fid, tp); tipoPadrao.put(fid, tp); }
                    for (String nomeOuCaminho : new String[]{(String) f.get("nome"), (String) f.get("caminho")}) {
                        if (nomeOuCaminho == null) continue;
                        String k = normalizarParaCasamento(nomeOuCaminho);
                        String ja = folhaPorNomeOuCaminho.get(k);
                        // o mesmo texto em duas folhas (ex.: dois "Acessorios") nao mapeia para nenhuma
                        folhaPorNomeOuCaminho.put(k, ja == null || ja.equals(fid) ? fid : "");
                    }
                    List<AtributoDef> atrs = new ArrayList<>();
                    for (Object ao : (List<Object>) f.get("atributos")) {
                        Map<String, Object> a = (Map<String, Object>) ao;
                        String chave = (String) a.get("chave");
                        DefinicaoAtributo def = definicaoAtributo.get(chave);
                        atrs.add(new AtributoDef(chave, (String) a.get("nome"), (String) a.get("nome_en"), (String) a.get("tipo"), strings(a.get("valores")),
                                a.get("min") instanceof Number n ? n.doubleValue() : null, a.get("max") instanceof Number n ? n.doubleValue() : null,
                                (String) a.get("unidade"), def == null ? List.of() : def.aliasesVendedor()));
                    }
                    atributosPorFolha.put(fid, List.copyOf(atrs));
                }
                folhasPorGrupo.put(gid, folhas);
                folhasPorGrupoEn.put(gid, folhasEn);
            }
            gruposPorDepto.put(did, grupos);
            gruposPorDeptoEn.put(did, gruposEn);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    public boolean tresNiveis() { return v2; }
    /** v2: os termos (sinonimos) que o time cadastrou para a subcategoria, para a busca por dicionario. */
    public List<String> termosDaFolha(String folha) { return termosDaFolha.getOrDefault(folha, List.of()); }
    public String nomeEnDaFolha(String folha) { return nomeEnDaFolha.get(folha); }
    public String nomeDaFolha(String folha) { return nomeCategoria.get(folha); }
    public boolean grupoValido(String g) { return g != null && nomeGrupo.containsKey(g); }
    public String grupoDe(String folha) { return grupoDaFolha.get(folha); }
    public String departamentoDoGrupo(String grupo) { return deptoDoGrupo.get(grupo); }
    public String nomeDoGrupo(String grupo) { return nomeGrupo.get(grupo); }
    public String caminhoDe(String folha) { return caminhoDaFolha.get(folha); }
    public Set<String> folhasDoGrupo(String grupo) { return folhasPorGrupo.containsKey(grupo) ? folhasPorGrupo.get(grupo).keySet() : Set.of(); }
    public List<AtributoDef> atributosDe(String folha) { return atributosPorFolha.getOrDefault(folha, List.of()); }
    public DefinicaoAtributo definicaoDoAtributo(String chave) { return definicaoAtributo.get(chave); }
    public Map<String, List<String>> sinonimosDeValor(String chave) { return sinonimosValor.getOrDefault(chave, Map.of()); }
    public boolean categoriaGenerica(String textoDoVendedor) { return categoriasGenericas.contains(normalizarParaCasamento(textoDoVendedor)); }

    /**
     * v2, 2a pergunta da cascata: a categoria (grupo) entre as dos departamentos dados (1 normalmente, 2 no modo top-2).
     * Com mais de um departamento, alterna entre eles, no maximo {@link Melhorias#MAX_OPCOES_CATEGORIA} opcoes.
     */
    public Map<String, Pergunta> perguntasGrupo(List<String> deptos, Melhorias m) {
        boolean en = m.ingles();
        List<Map<String, String>> fontes = deptos.stream().map(d -> (Map<String, String>) (en ? gruposPorDeptoEn : gruposPorDepto).get(d)).toList();
        LinkedHashMap<String, String> opcoes = alternar(fontes);
        String instr = deptos.size() == 1
                ? (en ? "Which product category of this department does the listing belong to?" : "Em qual categoria deste departamento o anuncio se encaixa?")
                : (en ? "Which product category does the listing belong to? The options come from the most likely departments."
                      : "Em qual categoria o anuncio se encaixa? As opcoes vem dos departamentos mais provaveis.");
        return Map.of("categoria", new Pergunta(instr, opcoes));
    }

    /** v2: "id: nome: criterio" das categorias (grupos), uma por linha, na ordem da taxonomia (1a etapa do LLM). */
    public List<String> linhasDeGruposParaPrompt(int maxCriterio) {
        List<String> linhas = new ArrayList<>();
        gruposPorDepto.forEach((depto, grupos) -> grupos.forEach((id, nomeECriterio) ->
                linhas.add(id + ": " + (nomeECriterio.length() > maxCriterio ? nomeECriterio.substring(0, maxCriterio) + "..." : nomeECriterio))));
        return linhas;
    }

    /** v2: "id: nome: criterio" das subcategorias de uma categoria (2a etapa do LLM), com o "Nao inclui" do criterio. */
    public List<String> linhasDeFolhasParaPrompt(String grupo, int maxCriterio) {
        List<String> linhas = new ArrayList<>();
        folhasPorGrupo.getOrDefault(grupo, new LinkedHashMap<>()).forEach((id, nomeECriterio) ->
                linhas.add(id + ": " + (nomeECriterio.length() > maxCriterio ? nomeECriterio.substring(0, maxCriterio) + "..." : nomeECriterio)));
        return linhas;
    }

    /** v2, 3a pergunta da cascata: a subcategoria (folha) dentro da categoria (grupo) escolhida. */
    public Map<String, Pergunta> perguntasFolha(String grupo, Melhorias m) { return perguntasFolha(grupo, m, false, null); }

    /** Resposta que significa "o produto nao e nenhuma das subcategorias listadas" (item fora da taxonomia). */
    public static final String NENHUMA_DESTAS = "nenhuma_destas";

    /**
     * @param comNenhuma acrescenta a opcao {@link #NENHUMA_DESTAS} (o Laya ajustado com exemplos de "nenhuma destas" aprende a usa-la)
     * @param excluir    id de subcategoria que sai das opcoes; so o gerador de treino usa, para fabricar o exemplo de "nenhuma destas"
     */
    public Map<String, Pergunta> perguntasFolha(String grupo, Melhorias m, boolean comNenhuma, String excluir) {
        boolean en = m.ingles();
        LinkedHashMap<String, String> opcoes = new LinkedHashMap<>((en ? folhasPorGrupoEn : folhasPorGrupo).get(grupo));
        if (excluir != null) opcoes.remove(excluir);
        if (comNenhuma) opcoes.put(NENHUMA_DESTAS, en ? "None of the above: the product does not belong to any of the subcategories listed."
                : "Nenhuma destas: o produto nao pertence a nenhuma das subcategorias listadas.");
        String instr = en ? "Which specific subcategory does the product fit in? Ignore the seller's category if it contradicts the title."
                : "Em qual subcategoria o produto se encaixa? Ignore a categoria do vendedor se ela contradisser o titulo.";
        return Map.of("subcategoria", new Pergunta(instr, opcoes));
    }

    private static LinkedHashMap<String, String> alternar(List<Map<String, String>> fontes) {
        LinkedHashMap<String, String> opcoes = new LinkedHashMap<>();
        if (fontes.size() == 1) { opcoes.putAll(fontes.get(0)); return opcoes; }
        List<Iterator<Map.Entry<String, String>>> its = fontes.stream().map(f -> f.entrySet().iterator()).toList();
        boolean algum = true;
        while (algum && opcoes.size() < Melhorias.MAX_OPCOES_CATEGORIA) {
            algum = false;
            for (var it : its) if (it.hasNext() && opcoes.size() < Melhorias.MAX_OPCOES_CATEGORIA) { var e = it.next(); opcoes.put(e.getKey(), e.getValue()); algum = true; }
        }
        return opcoes;
    }

    /**
     * v2: mapeia o texto da categoria do vendedor para uma subcategoria. Procura o nome ou o caminho completo de subcategoria mais longo
     * contido no texto (palavra inteira, sem acento nem caixa). Texto vazio, generico ("Diversos", "Ofertas"), sem nenhum nome ou ambiguo = null.
     */
    private String subcategoriaDoVendedor(String texto) {
        String n = " " + normalizarParaCasamento(texto) + " ";
        if (n.isBlank() || categoriasGenericas.contains(n.trim())) return null;
        String melhor = null;
        int tam = 0;
        for (var e : folhaPorNomeOuCaminho.entrySet()) {
            String k = e.getKey();
            if (k.isBlank() || k.length() < tam || !n.contains(" " + k + " ")) continue;
            if (k.length() > tam) { melhor = e.getValue(); tam = k.length(); }
            else if (!e.getValue().equals(melhor)) melhor = "";
        }
        return melhor == null || melhor.isEmpty() ? null : melhor;
    }

    // =====================================================================
    // Consultas ao dicionario
    // =====================================================================
    public String versao() { return versao; }
    public boolean departamentoValido(String d) { return d != null && departamentos.containsKey(d); }
    public boolean categoriaValida(String c) { return c != null && deptoDaCategoria.containsKey(c); }
    public String departamentoDe(String categoria) { return deptoDaCategoria.get(categoria); }
    public String nomeDaCategoria(String categoria) { return nomeCategoria.get(categoria); }
    public boolean tipoValido(String t) { return t != null && tipos.containsKey(t); }
    public boolean voltagemValida(String v) { return v != null && voltagens.containsKey(v); }
    public boolean corValida(String c) { return c != null && cores.containsKey(c); }
    public Map<String, String> aliasesDeMarca() { return aliasMarca; }
    public Set<String> categorias() { return deptoDaCategoria.keySet(); }

    /** tipo_padrao da categoria; null quando a categoria tem tipo misto e o modelo precisa decidir. */
    public String tipoPadrao(String categoria) { return tipoPadrao.get(categoria); }
    public boolean eletrica(String categoria) { return categoriasEletricas.contains(categoria); }
    public boolean temCor(String categoria) { return categoriasComCor.contains(categoria); }
    public Map<String, List<String>> sinonimosDeCor() { return sinonimosCor; }

    /**
     * Mapeia o texto livre da categoria do vendedor para uma categoria da taxonomia.
     * Procura o nome de categoria mais longo contido no texto (palavra inteira, sem acento e sem caixa).
     * Devolve null ("sem sinal") quando o texto e vazio ou generico ("Diversos", "Outros", "Promocoes"),
     * quando nenhum nome aparece, ou quando aparecem duas categorias sem relacao entre si ("Fogoes e Cooktops"):
     * nesse caso nao ha como saber qual o vendedor quis dizer.
     */
    public String categoriaDoVendedor(String textoVendedor) {
        if (v2) return subcategoriaDoVendedor(textoVendedor);
        String n =" " + normalizarParaCasamento(textoVendedor) + " ";
        String semGenericos = n;
        for (String g : termosGenericosVendedor) semGenericos = semGenericos.replace(" " + g + " ", " ");
        if (semGenericos.isBlank()) return null;

        List<Map.Entry<String, String>> achados = new ArrayList<>();
        for (var e : categoriaPorNomeNormalizado.entrySet())
            if (!e.getKey().isBlank() && n.contains(" " + e.getKey() + " ")) achados.add(e);
        // descarta os que sao so parte de um achado maior ("Fornos" dentro de "Fornos eletricos")
        List<Map.Entry<String, String>> finais = achados.stream()
                .filter(a -> achados.stream().noneMatch(b -> b != a && b.getKey().length() > a.getKey().length() && b.getKey().contains(a.getKey())))
                .toList();
        return finais.size() == 1 ? finais.get(0).getValue() : null;
    }

    /** "id: Nome: criterio curto" de TODAS as categorias, na ordem da taxonomia (usado no prompt da segunda opiniao). */
    public List<String> linhasDeCategoriasParaPrompt(int maxCriterio) {
        List<String> linhas = new ArrayList<>();
        if (v2) { // 205 subcategorias, na ordem da taxonomia
            folhasPorGrupo.values().forEach(fs -> fs.forEach((id, nomeECriterio) ->
                    linhas.add(id + ": " + (nomeECriterio.length() > maxCriterio ? nomeECriterio.substring(0, maxCriterio) + "..." : nomeECriterio))));
            return linhas;
        }
        for (String depto : departamentos.keySet()) {
            categoriasPorDepto.get(depto).forEach((id, nomeECriterio) -> {
                String t = nomeECriterio;
                int ponto = t.indexOf(". ");
                if (ponto > 0) t = t.substring(0, ponto + 1); // primeira frase do criterio
                linhas.add(id + ": " + (t.length() > maxCriterio ? t.substring(0, maxCriterio) + "..." : t));
            });
        }
        return linhas;
    }

    /** Sem acento, minusculas, hifen e barra viram espaco: "Micro-ondas" e "micro ondas" casam. */
    static String normalizarParaCasamento(String s) {
        return Texto.normalizar(s).replaceAll("[/\\-]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String ou(String preferido, String alternativa) {
        return preferido == null || preferido.isBlank() ? alternativa : preferido;
    }
}
