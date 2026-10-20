package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indice do catalogo atual para achar duplicados de um item novo (o mesmo produto anunciado por outro vendedor). Sem modelo: so chaves.
 * <ul>
 *   <li><b>EAN</b> igual: duplicado (se varios produtos tem o mesmo EAN, vale o de titulo mais parecido);</li>
 *   <li><b>codigo de modelo</b> em comum (do campo modelo, do titulo ou da descricao: "CO6C64", "WAL-6504") somado a um titulo parecido
 *       ou a mesma marca: candidato forte, que pode passar por um desempate (nenhum, Laya, LLM) porque uma variacao do mesmo produto
 *       (outra cor ou voltagem) tambem casa assim;</li>
 *   <li>similaridade de titulo sozinha NAO decide: titulos curtos e genericos ("Fogao 4 bocas") colidem demais.</li>
 * </ul>
 * O catalogo e lido em fluxo, e o indice guarda so inteiros (palavras viram numeros) e um resumo curto de cada produto para o desempate.
 * Os limiares vem da configuracao e foram ajustados nos novos que nao estao em novos_teste.
 */
public final class IndiceDeDuplicados {

    /** @param ean o EAN do candidato e igual ao do novo; @param modelos codigos de modelo em comum; @param sim parecenca do titulo (0 a 1) ponderada por IDF */
    public record Candidato(String id, boolean ean, int modelos, int modelosRaros, double sim, boolean marca, int atributos, String resumo) {}

    /** @param precisaDesempate true na regra de modelo: pode ser variacao do mesmo produto, e nao duplicado */
    public record Decisao(String duplicadoDe, String motivo, Candidato candidato, boolean precisaDesempate) {
        static final Decisao NENHUMA = new Decisao(null, "sem-candidato", null, false);
    }

    private static final Set<String> PROMO = Set.of("promocao", "oferta", "super", "mega", "frete", "gratis", "envio", "imediato", "lancamento", "black", "friday", "original",
            "lacrado", "liquidacao", "queima", "estoque", "relampago", "premium", "pronta", "entrega", "2026", "com", "nf", "brinde", "mais", "vendido");
    private static final Pattern MODELO = Pattern.compile("\\b(?=[a-z0-9-]*\\d)(?=[a-z0-9-]*[a-z])[a-z0-9]+(?:-[a-z0-9]+)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIDADE = Pattern.compile("\\d+(gb|tb|mah|kg|ml|cm|mm|hz|pol|w|v|l)|\\d+[a-z]{1,3}");

    private final Map<String, Integer> dic = new HashMap<>();
    private final ResolvedorMarca marcas;
    private final double limiarSimilaridade;
    private final int limiteDeModelosRaros = 3;

    private String[] ids = new String[0], eans = new String[0], resumos = new String[0];
    private int[][] titulo = new int[0][], modelos = new int[0][], atributos = new int[0][];
    private int[] marca = new int[0];
    private final Map<String, int[]> porEan = new HashMap<>();
    private final Map<Integer, int[]> postModelo = new HashMap<>(), postTitulo = new HashMap<>();

    /** @param limiarSimilaridade titulo parecido o bastante para a regra de modelo (padrao 0,3, ajustado nos novos de ajuste) */
    public IndiceDeDuplicados(ResolvedorMarca marcas, double limiarSimilaridade) { this.marcas = marcas; this.limiarSimilaridade = limiarSimilaridade; }

    public int tamanho() { return ids.length; }

    // ------------------------------------------------------------------ construcao (em fluxo)

    public static IndiceDeDuplicados doCatalogo(Path produtosJsonl, ResolvedorMarca marcas, double limiarSimilaridade) throws IOException {
        IndiceDeDuplicados x = new IndiceDeDuplicados(marcas, limiarSimilaridade);
        x.construir(produtosJsonl);
        return x;
    }

    /** Lista de inteiros que cresce sem boxing. */
    private static final class Lista {
        int[] v = new int[4]; int n;
        void add(int x) { if (n == v.length) v = Arrays.copyOf(v, n * 2); v[n++] = x; }
        int[] fim() { return Arrays.copyOf(v, n); }
    }

    private void construir(Path arq) throws IOException {
        List<String> idL = new ArrayList<>(), eanL = new ArrayList<>(), resL = new ArrayList<>();
        List<int[]> titL = new ArrayList<>(), modL = new ArrayList<>(), atrL = new ArrayList<>();
        Lista marcaL = new Lista();
        Map<String, Lista> eanP = new HashMap<>();
        Map<Integer, Lista> modP = new HashMap<>(), titP = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(arq, StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.isBlank()) continue;
                Map<String, Object> m = Json.mapa(Json.parse(l));
                ProdutoEntrada p = paraProduto(m);
                int idx = idL.size();
                idL.add(p.id());
                String ean = p.ean() == null || p.ean().isBlank() ? null : p.ean().trim();
                eanL.add(ean);
                if (ean != null) eanP.computeIfAbsent(ean, k -> new Lista()).add(idx);
                int[] t = ids(palavras(p.titulo()));
                titL.add(t);
                for (int w : t) titP.computeIfAbsent(w, k -> new Lista()).add(idx);
                int[] mo = ids(codigosDeModelo(p));
                modL.add(mo);
                for (int w : mo) modP.computeIfAbsent(w, k -> new Lista()).add(idx);
                atrL.add(ids(palavrasDosAtributos(p)));
                String mk = marcaChave(p);
                marcaL.add(mk.isEmpty() ? -1 : id(mk));
                resL.add(resumo(p, mk));
            }
        }
        ids = idL.toArray(new String[0]); eans = eanL.toArray(new String[0]); resumos = resL.toArray(new String[0]);
        titulo = titL.toArray(new int[0][]); modelos = modL.toArray(new int[0][]); atributos = atrL.toArray(new int[0][]); marca = marcaL.fim();
        eanP.forEach((k, v) -> porEan.put(k, v.fim()));
        modP.forEach((k, v) -> postModelo.put(k, v.fim()));
        titP.forEach((k, v) -> postTitulo.put(k, v.fim()));
    }

    @SuppressWarnings("unchecked")
    private static ProdutoEntrada paraProduto(Map<String, Object> m) {
        Map<String, String> at = new LinkedHashMap<>();
        Json.mapa(m.get("atributos")).forEach((k, v) -> at.put(k, String.valueOf(v)));
        return new ProdutoEntrada((String) m.get("id"), (String) m.get("sku"), (String) m.get("titulo"), (String) m.get("descricao"), (String) m.get("categoria_vendedor"),
                (String) m.get("tags"), (String) m.get("marca_informada"), at, m.get("bullets") instanceof List<?> b ? b.stream().map(String::valueOf).toList() : null,
                (String) m.get("modelo"), (String) m.get("ean"), (String) m.get("preco"), (String) m.get("condicao"),
                m.get("vendedor") instanceof Map<?, ?> v ? Map.of("nome", String.valueOf(v.get("nome"))) : null);
    }

    // ------------------------------------------------------------------ chaves

    private int id(String token) { return dic.computeIfAbsent(token, k -> dic.size()); }
    private int idOuMenos1(String token) { Integer i = dic.get(token); return i == null ? -1 : i; }

    private int[] ids(Collection<String> tokens) {
        int[] out = new int[tokens.size()];
        int i = 0;
        for (String t : tokens) out[i++] = id(t);
        Arrays.sort(out);
        return out;
    }

    static Set<String> palavras(String texto) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : FichaCodigo.chave(texto).split(" ")) if (w.length() >= 2 && !PROMO.contains(w)) out.add(w);
        return out;
    }

    private static Set<String> palavrasDosAtributos(ProdutoEntrada p) {
        Set<String> out = new LinkedHashSet<>();
        if (p.atributos() != null) for (String v : p.atributos().values()) out.addAll(palavras(v));
        return out;
    }

    /** Codigos de modelo: o campo modelo e tokens de letras e numeros do titulo e da descricao ("CO6C64", "WAL-6504"), menos medidas ("500ml", "5000mah"). */
    static Set<String> codigosDeModelo(ProdutoEntrada p) {
        Set<String> out = new LinkedHashSet<>();
        String campo = FichaCodigo.chave(p.modelo()).replace(" ", "");
        if (campo.length() >= 4) out.add(campo);
        String txt = (p.titulo() == null ? "" : p.titulo()) + " " + (p.descricao() == null ? "" : p.descricao().replaceAll("<[^>]+>", " "));
        Matcher m = MODELO.matcher(txt);
        while (m.find()) {
            String k = m.group().toLowerCase(Locale.ROOT).replace("-", "");
            if (k.length() >= 4 && !UNIDADE.matcher(k).matches()) out.add(k);
        }
        return out;
    }

    private String marcaChave(ProdutoEntrada p) {
        String b = marcas.resolver(p);
        return b == null || b.equals(ResolvedorMarca.NAO_IDENTIFICADA) ? "" : FichaCodigo.chave(b);
    }

    private String resumo(ProdutoEntrada p, String marcaChave) {
        StringBuilder sb = new StringBuilder("Titulo: ").append(trunc(p.titulo(), 120));
        if (!marcaChave.isEmpty()) sb.append(" | Marca: ").append(marcaChave);
        if (p.modelo() != null && !p.modelo().isBlank()) sb.append(" | Modelo: ").append(p.modelo().trim());
        if (p.ean() != null && !p.ean().isBlank()) sb.append(" | EAN: ").append(p.ean().trim());
        if (p.preco() != null && !p.preco().isBlank()) sb.append(" | Preco: ").append(p.preco().trim());
        if (p.atributos() != null && !p.atributos().isEmpty())
            sb.append(" | Atributos: ").append(trunc(String.join(", ", p.atributos().entrySet().stream().limit(8).map(e -> e.getKey() + "=" + e.getValue()).toList()), 200));
        return sb.toString();
    }

    private static String trunc(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) : s; }

    // ------------------------------------------------------------------ busca

    private double idf(int token) {
        int[] pl = postTitulo.get(token);
        return Math.log((double) ids.length / ((pl == null ? 0 : pl.length) + 1));
    }

    /** Os candidatos do item novo, do mais parecido para o menos. */
    public List<Candidato> candidatos(ProdutoEntrada p) {
        Set<String> tituloT = palavras(p.titulo());
        Set<String> modeloT = codigosDeModelo(p), atrT = palavrasDosAtributos(p);
        String mk = marcaChave(p);
        int[] tit = new int[tituloT.size()];
        int ti = 0;
        for (String w : tituloT) { int i = idOuMenos1(w); if (i >= 0) tit[ti++] = i; }
        tit = Arrays.copyOf(tit, ti);

        Set<Integer> cand = new LinkedHashSet<>();
        String ean = p.ean() == null || p.ean().isBlank() ? null : p.ean().trim();
        if (ean != null && porEan.containsKey(ean)) for (int i : porEan.get(ean)) cand.add(i);
        for (String k : modeloT) {
            int d = idOuMenos1(k);
            int[] pl = d < 0 ? null : postModelo.get(d);
            if (pl != null && pl.length <= 8) for (int i : pl) cand.add(i);
        }
        Map<Integer, Double> acc = new HashMap<>();
        for (int w : tit) {
            int[] pl = postTitulo.get(w);
            if (pl == null || pl.length > 400) continue;
            double x = idf(w);
            for (int i : pl) acc.merge(i, x, Double::sum);
        }
        acc.entrySet().stream().sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()).limit(10).forEach(e -> cand.add(e.getKey()));

        Set<Integer> tituloSet = new HashSet<>();
        for (int w : tit) tituloSet.add(w);
        Set<Integer> atrSet = new HashSet<>();
        for (String w : atrT) { int i = idOuMenos1(w); if (i >= 0) atrSet.add(i); }
        int marcaId = mk.isEmpty() ? -2 : idOuMenos1(mk);

        List<Candidato> out = new ArrayList<>();
        for (int i : cand) {
            double wi = 0, wu = 0;
            Set<Integer> uniao = new HashSet<>(tituloSet);
            for (int w : titulo[i]) uniao.add(w);
            Set<Integer> catT = new HashSet<>();
            for (int w : titulo[i]) catT.add(w);
            for (int w : uniao) { double x = idf(w); wu += x; if (tituloSet.contains(w) && catT.contains(w)) wi += x; }
            int mm = 0, rar = 0;
            Set<Integer> catM = new HashSet<>();
            for (int w : modelos[i]) catM.add(w);
            for (String k : modeloT) {
                int d = idOuMenos1(k);
                if (d >= 0 && catM.contains(d)) { mm++; int[] pl = postModelo.get(d); if (pl != null && pl.length <= limiteDeModelosRaros) rar++; }
            }
            int ai = 0;
            for (int w : atributos[i]) if (atrSet.contains(w)) ai++;
            boolean eanIgual = ean != null && ean.equals(eans[i]);
            out.add(new Candidato(ids[i], eanIgual, mm, rar, wu == 0 ? 0 : wi / wu, marcaId >= 0 && marca[i] == marcaId, ai, resumos[i]));
        }
        out.sort(Comparator.comparing((Candidato c) -> !c.ean()).thenComparing(c -> -c.sim()));
        return out;
    }

    /** Resumo do item novo no mesmo formato do que o indice guarda dos produtos do catalogo (para o desempate). */
    public String resumoDoNovo(ProdutoEntrada p) { return resumo(p, marcaChave(p)); }

    /** Aplica as regras: EAN igual decide; codigo de modelo em comum com titulo parecido ou mesma marca e candidato (com desempate). */
    public Decisao decidir(ProdutoEntrada p) {
        List<Candidato> c = candidatos(p);
        for (Candidato x : c) if (x.ean()) return new Decisao(x.id(), "ean", x, false);
        for (Candidato x : c) if (x.modelos() > 0 && (x.sim() >= limiarSimilaridade || x.marca())) return new Decisao(x.id(), "modelo", x, true);
        return Decisao.NENHUMA;
    }
}
