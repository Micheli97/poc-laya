package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;
import br.com.projeto.curadoria.core.Taxonomia.AtributoDef;
import br.com.projeto.curadoria.core.Taxonomia.Pergunta;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Converte os exemplos rotulados do catalogo v2 (treino_N) para o formato de ajuste fino do Laya ({state, questions, expected}), com
 * EXATAMENTE as perguntas que o agente faz em producao:
 * <ul>
 *   <li>estado do {@link Preparador} v2, sem a linha da categoria do vendedor;</li>
 *   <li>cascata de 3 niveis em ingles: departamento; categoria (com 2 departamentos em ~50% dos produtos, o certo em 1o ou em 2o, como no top-2);
 *       subcategoria dentro da categoria;</li>
 *   <li>"nenhuma destas": em ~{@code fracaoNenhuma} das linhas de subcategoria a subcategoria certa SAI das opcoes e a resposta e
 *       {@link Taxonomia#NENHUMA_DESTAS} (simula o item fora da taxonomia). A opcao existe em todas as perguntas de subcategoria;</li>
 *   <li>ficha tecnica: uma linha por produto com uma pergunta de escolha por atributo enumerado (valores da taxonomia + nao_informado),
 *       a mesma montagem do {@link FichaComModelo}. O rotulo e o valor verdadeiro se o anuncio o traz (atributos_inferiveis) ou nao_informado.</li>
 * </ul>
 * Os rotulos (subcategoria e atributos) vem do gabarito dos ids de treino, como se fossem rotulos do time; o agente nunca le o gabarito e o golden,
 * o teste_rodadas e a validacao nunca entram. A validacao do treino (10%) e separada POR PRODUTO.
 */
public final class GeradorTreinoLayaV2 {

    static final Melhorias CASCATA = new Melhorias(true, true, true, true, true, false, true, 0.7, "en");

    public record Resultado(List<Map<String, Object>> linhasTreino, List<Map<String, Object>> linhasValidacao, List<String> idsTreino, List<String> idsValidacao,
                            Map<String, Integer> porPergunta) {}

    /** Um produto de treino: o anuncio e os rotulos. */
    public record Rotulado(ProdutoEntrada produto, String subcategoria, Map<String, Object> atributos, Set<String> inferiveis, Set<String> errosDoVendedor) {}

    private final Taxonomia tx;
    private final Preparador preparador = new Preparador(false, true);
    private final double fracaoNenhuma;

    public GeradorTreinoLayaV2(Taxonomia tx, double fracaoNenhuma) { this.tx = tx; this.fracaoNenhuma = fracaoNenhuma; }

    public Resultado gerar(List<Rotulado> exemplos, Set<String> proibidos, long semente, double fracaoValidacao) {
        Set<String> vistos = new HashSet<>();
        for (Rotulado e : exemplos) {
            String id = e.produto().id();
            if (!vistos.add(id)) throw new IllegalArgumentException("produto repetido no treino: " + id);
            if (proibidos.contains(id)) throw new IllegalArgumentException("produto de avaliacao no treino: " + id);
            if (!tx.categoriaValida(e.subcategoria())) throw new IllegalArgumentException("subcategoria fora da taxonomia em " + id + ": " + e.subcategoria());
        }
        List<Rotulado> ordem = new ArrayList<>(exemplos);
        Collections.shuffle(ordem, new Random(semente));
        int nVal = (int) Math.round(ordem.size() * fracaoValidacao);
        List<Rotulado> val = ordem.subList(0, nVal), trn = ordem.subList(nVal, ordem.size());
        Map<String, Integer> contagem = new LinkedHashMap<>();
        List<Map<String, Object>> t = new ArrayList<>(), v = new ArrayList<>();
        for (Rotulado e : trn) t.addAll(linhasDoProduto(e, semente, contagem));
        for (Rotulado e : val) v.addAll(linhasDoProduto(e, semente, contagem));
        return new Resultado(t, v, trn.stream().map(e -> e.produto().id()).toList(), val.stream().map(e -> e.produto().id()).toList(), contagem);
    }

    private List<Map<String, Object>> linhasDoProduto(Rotulado e, long semente, Map<String, Integer> contagem) {
        ProdutoEntrada p = e.produto();
        String state = preparador.preparar(p).state();
        String sub = e.subcategoria(), grupo = tx.grupoDe(sub), depto = tx.departamentoDe(sub);
        Random r = new Random(semente * 31 + p.id().hashCode());
        List<Map<String, Object>> out = new ArrayList<>();

        out.add(linha(p.id() + "#depto", state, tx.perguntasNivel1(CASCATA), "departamento", depto, "departamento", contagem));
        out.add(linha(p.id() + "#cat", state, tx.perguntasGrupo(List.of(depto), CASCATA), "categoria", grupo, "categoria-1depto", contagem));
        if (r.nextDouble() < 0.5) { // top-2: o departamento certo em 1o ou em 2o, com outro departamento qualquer
            List<String> outros = tx.perguntasNivel1(CASCATA).get("departamento").criterios().keySet().stream().filter(d -> !d.equals(depto)).toList();
            String segundo = outros.get(r.nextInt(outros.size()));
            List<String> deptos = r.nextBoolean() ? List.of(depto, segundo) : List.of(segundo, depto);
            Map<String, Pergunta> q = tx.perguntasGrupo(deptos, CASCATA);
            if (q.get("categoria").criterios().containsKey(grupo)) out.add(linha(p.id() + "#cat2", state, q, "categoria", grupo, "categoria-2deptos", contagem));
        }
        // subcategoria: em ~fracaoNenhuma das linhas a certa sai das opcoes e a resposta e "nenhuma destas"
        boolean nenhuma = fracaoNenhuma > 0 && tx.folhasDoGrupo(grupo).size() >= 3 && r.nextDouble() < fracaoNenhuma;
        if (nenhuma) out.add(linha(p.id() + "#sub-nenhuma", state, tx.perguntasFolha(grupo, CASCATA, fracaoNenhuma > 0, sub), "subcategoria", Taxonomia.NENHUMA_DESTAS, "subcategoria-nenhuma", contagem));
        else out.add(linha(p.id() + "#sub", state, tx.perguntasFolha(grupo, CASCATA, fracaoNenhuma > 0, null), "subcategoria", sub, "subcategoria", contagem));

        // ficha tecnica: todas as perguntas de atributo enumerado numa linha so
        List<AtributoDef> enums = tx.atributosDe(sub).stream().filter(a -> "enum".equals(a.tipo()) && !a.valores().isEmpty() && a.valores().size() + 1 <= 20).toList();
        if (!enums.isEmpty()) {
            Map<String, Pergunta> perguntas = FichaComModelo.perguntasDeAtributos(enums, true);
            Map<String, Object> esperado = new LinkedHashMap<>();
            for (AtributoDef a : enums) {
                Object real = e.atributos().get(a.chave());
                boolean dado = real != null && e.inferiveis().contains(a.chave()) && !e.errosDoVendedor().contains(a.chave());
                String valor = dado ? canonico(a, real) : null;
                esperado.put(a.chave(), valor == null ? FichaTecnica.NAO_INFORMADO : valor);
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", p.id() + "#ficha");
            l.put("state", state);
            l.put("questions", Json.parse(Classificador.ModeloHttp.perguntasJson(perguntas)));
            l.put("expected", esperado);
            contagem.merge("ficha-linhas", 1, Integer::sum);
            contagem.merge("ficha-perguntas", enums.size(), Integer::sum);
            out.add(l);
        }
        return out;
    }

    /** O valor verdadeiro, escrito como o esquema o escreve ("220V", "sim", "64"); fora das opcoes vira nulo (nao_informado). */
    private static String canonico(AtributoDef a, Object real) {
        String s = real instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(real);
        for (String v : a.valores()) if (FichaCodigo.chave(v).equals(FichaCodigo.chave(s))) return v;
        return null;
    }

    private Map<String, Object> linha(String idLinha, String state, Map<String, Pergunta> perguntas, String qid, String resposta, String tipo, Map<String, Integer> contagem) {
        if (!perguntas.get(qid).criterios().containsKey(resposta)) throw new IllegalStateException(idLinha + ": resposta " + resposta + " nao esta nas opcoes de " + qid);
        contagem.merge(tipo, 1, Integer::sum);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", idLinha);
        m.put("state", state);
        m.put("questions", Json.parse(Classificador.ModeloHttp.perguntasJson(perguntas)));
        m.put("expected", new LinkedHashMap<>(Map.of(qid, resposta)));
        return m;
    }

    // ------------------------------------------------------------------ arquivos e linha de comando

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        Path catalogo = Path.of(a.getOrDefault("catalogo", "../catalogo-v2/dados")), saida = Path.of(a.getOrDefault("saida", "avaliacao/laya-treino-v2"));
        String conjunto = Objects.requireNonNull(a.get("treino"), "--treino (ex.: treino_2000)"), prefixo = Objects.requireNonNull(a.get("prefixo"), "--prefixo");
        double nenhuma = Double.parseDouble(a.getOrDefault("nenhuma", "0.08"));
        long semente = Long.parseLong(a.getOrDefault("semente", "42"));
        Taxonomia tx = new Taxonomia(catalogo.resolve("taxonomia.json"));

        Map<String, Object> splits = (Map<String, Object>) Json.parse(Files.readString(catalogo.resolve("splits.json")));
        Set<String> ids = new HashSet<>((List<String>) splits.get(conjunto));
        Set<String> proibidos = new HashSet<>();
        for (String c : List.of("golden", "teste_rodadas", "validacao", "novos_teste")) if (splits.get(c) instanceof List<?> l) l.forEach(x -> proibidos.add(String.valueOf(x)));
        List<Rotulado> exemplos = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(catalogo.resolve("produtos.jsonl"), StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf("\"id\":\"");
                if (i < 0) continue;
                String id = l.substring(i + 6, l.indexOf('"', i + 6));
                if (!ids.contains(id)) continue;
                Map<String, Object> p = (Map<String, Object>) Json.parse(l);
                Map<String, Object> g = Json.mapa(p.get("gabarito"));
                Map<String, String> at = new LinkedHashMap<>();
                Json.mapa(p.get("atributos")).forEach((k, v) -> at.put(k, String.valueOf(v)));
                ProdutoEntrada pe = new ProdutoEntrada(id, (String) p.get("sku"), (String) p.get("titulo"), (String) p.get("descricao"), (String) p.get("categoria_vendedor"),
                        (String) p.get("tags"), (String) p.get("marca_informada"), at, p.get("bullets") instanceof List<?> b ? b.stream().map(String::valueOf).toList() : null,
                        (String) p.get("modelo"), (String) p.get("ean"), (String) p.get("preco"), (String) p.get("condicao"), null);
                exemplos.add(new Rotulado(pe, (String) g.get("subcategoria"), Json.mapa(g.get("atributos")),
                        new HashSet<>(strings(g.get("atributos_inferiveis"))), new HashSet<>(strings(g.get("atributos_errados_pelo_vendedor")))));
            }
        }
        if (exemplos.size() != ids.size()) throw new IllegalStateException("produtos achados " + exemplos.size() + " de " + ids.size());
        Resultado res = new GeradorTreinoLayaV2(tx, nenhuma).gerar(exemplos, proibidos, semente, 0.1);
        Files.createDirectories(saida);
        gravar(saida.resolve(prefixo + ".train.jsonl"), res.linhasTreino());
        gravar(saida.resolve(prefixo + ".val.jsonl"), res.linhasValidacao());
        Files.writeString(saida.resolve(prefixo + ".ids.json"), "{\"treino\":" + Json.escrever(res.idsTreino()) + ",\"validacao\":" + Json.escrever(res.idsValidacao()) + "}\n", StandardCharsets.UTF_8);
        int folhas = res.porPergunta().getOrDefault("subcategoria", 0) + res.porPergunta().getOrDefault("subcategoria-nenhuma", 0);
        System.out.printf("%s (%s, nenhuma-destas %.0f%%): %d produtos (%d treino, %d validacao) -> %d linhas de treino, %d de validacao%n  por pergunta: %s%n  proporcao real de 'nenhuma destas' nas linhas de subcategoria: %.1f%%%n",
                prefixo, conjunto, nenhuma * 100, exemplos.size(), res.idsTreino().size(), res.idsValidacao().size(), res.linhasTreino().size(), res.linhasValidacao().size(), res.porPergunta(),
                folhas == 0 ? 0 : 100.0 * res.porPergunta().getOrDefault("subcategoria-nenhuma", 0) / folhas);
        System.out.println("LEMBRETE: rotulos SINTETICOS (do gabarito dos ids de treino). Com dados reais de cliente, revise a politica de dados antes de usar o Kaggle.");
    }

    private static List<String> strings(Object o) { return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(); }

    private static void gravar(Path arq, List<Map<String, Object>> linhas) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : linhas) sb.append(Json.escrever(m)).append('\n');
        Files.writeString(arq, sb.toString(), StandardCharsets.UTF_8);
    }
}
