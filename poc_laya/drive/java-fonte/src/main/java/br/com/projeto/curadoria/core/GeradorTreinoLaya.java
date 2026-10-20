package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;
import br.com.projeto.curadoria.core.Taxonomia.Pergunta;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Converte os exemplos rotulados (os mesmos do Naive Bayes) para o formato de ajuste fino do Laya
 * ({state, questions, expected}), usando EXATAMENTE o que o agente em producao manda ao Laya:
 * <ul>
 *   <li>o estado vem do {@link Preparador} sem a linha da categoria do vendedor (como nas rodadas de votacao);</li>
 *   <li>as perguntas vem da {@link Taxonomia} em ingles, na cascata: departamento, depois categoria do departamento;</li>
 *   <li>em parte dos produtos tambem sai a pergunta de categoria com 2 departamentos (o modo top-2 do agente),
 *       com o departamento certo em primeiro ou em segundo (na producao a 1a opcao e o palpite do modelo, que pode errar);</li>
 *   <li>a serializacao das perguntas e a mesma do cliente HTTP do agente.</li>
 * </ul>
 * O rotulo vem do arquivo de treino (nunca do gabarito do produto). Descricao e marca vem do catalogo pelo mesmo id,
 * porque em producao o Laya recebe esses campos e o treino com texto mais curto enviesaria o ajuste fino.
 * <p>
 * A validacao (10%) e separada POR PRODUTO. Nenhum produto do golden set nem das rodadas (id ate P0005100) entra.
 * Os rotulos continuam SINTETICOS (vem do gabarito do simulador): o resultado mede o metodo, nao o catalogo real.
 */
public final class GeradorTreinoLaya {

    /** O ultimo produto usado nas rodadas e simulacoes. Nada ate ele pode entrar no treino. */
    public static final int ULTIMO_ID_DAS_RODADAS = 5100;
    /** Fracao dos produtos com a pergunta extra de categoria com 2 departamentos. */
    static final double FRACAO_TOP2 = 0.5;
    /** Mesmas melhorias da cascata do Laya nas rodadas de votacao: so influenciam o idioma e o conteudo das perguntas. */
    static final Melhorias CASCATA = new Melhorias(true, true, true, true, true, false, true, 0.7, "en");

    public record Resultado(List<Map<String, Object>> linhasTreino, List<Map<String, Object>> linhasValidacao,
                            List<String> idsTreino, List<String> idsValidacao, Map<String, Integer> porPergunta) {}

    private record Exemplo(String id, String categoria) {}

    private final Taxonomia tx;
    private final Preparador preparador = new Preparador(false);

    public GeradorTreinoLaya(Taxonomia tx) { this.tx = tx; }

    /**
     * @param exemplos produtos rotulados (id e categoria certa), na ordem do arquivo de treino
     * @param catalogo produtos completos por id (titulo, descricao, marca, atributos)
     * @param proibidos ids que nao podem entrar (o golden set)
     */
    Resultado gerar(List<Map<String, String>> exemplos, Map<String, ProdutoEntrada> catalogo, Set<String> proibidos, long semente, double fracaoValidacao) {
        List<Exemplo> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (Map<String, String> e : exemplos) {
            String id = e.get("id"), cat = e.get("categoria");
            if (!vistos.add(id)) throw new IllegalArgumentException("produto repetido no treino: " + id);
            if (proibidos.contains(id)) throw new IllegalArgumentException("produto do golden set no treino: " + id);
            if (numero(id) <= ULTIMO_ID_DAS_RODADAS) throw new IllegalArgumentException("produto das rodadas (id ate P" + String.format("%07d", ULTIMO_ID_DAS_RODADAS) + ") no treino: " + id);
            if (!tx.categoriaValida(cat)) throw new IllegalArgumentException("categoria fora da taxonomia no produto " + id + ": " + cat);
            if (!catalogo.containsKey(id)) throw new IllegalArgumentException("produto sem texto no catalogo: " + id);
            lista.add(new Exemplo(id, cat));
        }
        List<Exemplo> ordem = new ArrayList<>(lista);
        Collections.shuffle(ordem, new Random(semente));
        int nVal = (int) Math.round(ordem.size() * fracaoValidacao);
        List<Exemplo> val = ordem.subList(0, nVal), trn = ordem.subList(nVal, ordem.size());

        Map<String, Integer> porPergunta = new LinkedHashMap<>();
        List<Map<String, Object>> linhasT = new ArrayList<>(), linhasV = new ArrayList<>();
        for (Exemplo e : trn) linhasT.addAll(linhasDoProduto(e, catalogo.get(e.id()), semente, porPergunta));
        for (Exemplo e : val) linhasV.addAll(linhasDoProduto(e, catalogo.get(e.id()), semente, porPergunta));
        return new Resultado(linhasT, linhasV, trn.stream().map(Exemplo::id).toList(), val.stream().map(Exemplo::id).toList(), porPergunta);
    }

    /** Uma linha por pergunta, como o agente em producao pergunta (cada chamada tem uma pergunta so). */
    private List<Map<String, Object>> linhasDoProduto(Exemplo e, ProdutoEntrada p, long semente, Map<String, Integer> contagem) {
        String state = preparador.preparar(p).state();
        String depto = tx.departamentoDe(e.categoria());
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(linha(e.id() + "#depto", state, tx.perguntasNivel1(CASCATA), "departamento", depto, "departamento", contagem));
        out.add(linha(e.id() + "#cat", state, tx.perguntasCategoria(List.of(depto), CASCATA), "categoria", e.categoria(), "categoria-1depto", contagem));

        Random r = new Random(semente * 31 + e.id().hashCode());
        if (r.nextDouble() < FRACAO_TOP2) {
            List<String> outros = tx.perguntasNivel1(CASCATA).get("departamento").criterios().keySet().stream().filter(d -> !d.equals(depto)).toList();
            String segundo = outros.get(r.nextInt(outros.size()));
            List<String> deptos = r.nextBoolean() ? List.of(depto, segundo) : List.of(segundo, depto);
            Map<String, Pergunta> q = tx.perguntasCategoria(deptos, CASCATA);
            // a lista de 2 departamentos e cortada em 20 opcoes: sem a categoria certa nao ha o que ensinar
            if (q.get("categoria").criterios().containsKey(e.categoria()))
                out.add(linha(e.id() + "#cat2", state, q, "categoria", e.categoria(), "categoria-2deptos", contagem));
        }
        return out;
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

    /** Serializa o que o gerador monta: mapas, listas, textos, numeros e booleanos. */
    static String escrever(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return Json.str(s);
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (var e : m.entrySet()) { if (sb.length() > 1) sb.append(','); sb.append(Json.str(String.valueOf(e.getKey()))).append(':').append(escrever(e.getValue())); }
            return sb.append('}').toString();
        }
        if (o instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            for (Object x : it) { if (sb.length() > 1) sb.append(','); sb.append(escrever(x)); }
            return sb.append(']').toString();
        }
        if (o instanceof Double d && d == Math.rint(d)) return String.valueOf(d.longValue());
        return String.valueOf(o);
    }

    private static int numero(String id) { return Integer.parseInt(id.substring(1)); }

    // ------------------------------------------------------------------ arquivos

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lerJsonl(Path arq) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(arq, StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) if (!l.isBlank()) out.add((Map<String, Object>) Json.parse(l));
        }
        return out;
    }

    /** Le do catalogo so os ids pedidos. NAO le o gabarito: so titulo, descricao, marca, tags e atributos. */
    @SuppressWarnings("unchecked")
    static Map<String, ProdutoEntrada> lerCatalogo(Path arq, Set<String> ids) throws IOException {
        Map<String, ProdutoEntrada> out = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(arq, StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.isBlank()) continue;
                int i = l.indexOf("\"id\":\"");
                if (i < 0) continue;
                String id = l.substring(i + 6, l.indexOf('"', i + 6));
                if (!ids.contains(id)) continue;
                Map<String, Object> m = (Map<String, Object>) Json.parse(l);
                Map<String, String> at = new LinkedHashMap<>();
                if (m.get("atributos") instanceof Map<?, ?> a) a.forEach((k, v) -> at.put(String.valueOf(k), String.valueOf(v)));
                out.put(id, new ProdutoEntrada(id, (String) m.get("sku"), (String) m.get("titulo"), (String) m.get("descricao"),
                        (String) m.get("categoria_vendedor"), (String) m.get("tags"), (String) m.get("marca_informada"), at));
            }
        }
        return out;
    }

    private static void gravar(Path arq, List<Map<String, Object>> linhas) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : linhas) sb.append(escrever(m)).append('\n');
        Files.createDirectories(arq.toAbsolutePath().getParent());
        Files.writeString(arq, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Uso: java -cp target/classes br.com.projeto.curadoria.core.GeradorTreinoLaya --treino avaliacao/treino.jsonl --prefixo laya-2000
     * [--produtos simulacao/dados/produtos.jsonl] [--golden avaliacao/golden-set.jsonl] [--taxonomia simulacao/dados/taxonomia.json]
     * [--saida avaliacao/laya-treino] [--semente 42] [--validacao 0.1]
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        Path treino = Path.of(Objects.requireNonNull(a.get("treino"), "--treino")), saida = Path.of(a.getOrDefault("saida", "avaliacao/laya-treino"));
        String prefixo = Objects.requireNonNull(a.get("prefixo"), "--prefixo");
        long semente = Long.parseLong(a.getOrDefault("semente", "42"));
        double fVal = Double.parseDouble(a.getOrDefault("validacao", "0.1"));
        Taxonomia tx = new Taxonomia(Path.of(a.getOrDefault("taxonomia", "simulacao/dados/taxonomia.json")));

        List<Map<String, String>> exemplos = new ArrayList<>();
        for (Map<String, Object> m : lerJsonl(treino)) {
            if (m.containsKey("_meta")) continue;
            exemplos.add(Map.of("id", (String) m.get("id"), "categoria", (String) m.get("categoria")));
        }
        Set<String> golden = new HashSet<>();
        for (Map<String, Object> m : lerJsonl(Path.of(a.getOrDefault("golden", "avaliacao/golden-set.jsonl")))) if (!m.containsKey("_meta")) golden.add((String) m.get("id"));
        Set<String> ids = new HashSet<>();
        exemplos.forEach(e -> ids.add(e.get("id")));
        Map<String, ProdutoEntrada> catalogo = lerCatalogo(Path.of(a.getOrDefault("produtos", "simulacao/dados/produtos.jsonl")), ids);

        Resultado r = new GeradorTreinoLaya(tx).gerar(exemplos, catalogo, golden, semente, fVal);
        gravar(saida.resolve(prefixo + ".train.jsonl"), r.linhasTreino());
        gravar(saida.resolve(prefixo + ".val.jsonl"), r.linhasValidacao());
        Files.writeString(saida.resolve(prefixo + ".ids.json"), "{\"treino\":" + escrever(r.idsTreino()) + ",\"validacao\":" + escrever(r.idsValidacao()) + "}\n", StandardCharsets.UTF_8);
        System.out.printf("%s: %d produtos (%d treino, %d validacao) -> %d linhas de treino, %d de validacao | por pergunta: %s%n", prefixo, exemplos.size(),
                r.idsTreino().size(), r.idsValidacao().size(), r.linhasTreino().size(), r.linhasValidacao().size(), r.porPergunta());
        System.out.println("LEMBRETE: rotulos SINTETICOS (do gabarito). Em producao o time rotula o catalogo real.");
    }
}
