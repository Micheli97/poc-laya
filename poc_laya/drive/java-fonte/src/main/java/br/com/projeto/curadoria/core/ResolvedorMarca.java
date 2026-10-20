package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Marca resolvida por codigo, sem IA. Dois modos:
 * <ul>
 *   <li><b>dicionario</b> (catalogo v1): apelidos da taxonomia + tolerancia a 1 erro de digitacao;</li>
 *   <li><b>lexico</b> (catalogo v2): nao ha dicionario. Marcas conhecidas = valores de {@code marca_informada} que se repetem no
 *       proprio catalogo. A marca informada vale quando e conhecida ou quando e NOVA (nao generica, nem o nome da loja); marca generica
 *       ("Importado", "OEM", "Sem marca") ou nome da loja viram {@code nao_identificada}, a nao ser que o titulo (e depois a descricao,
 *       os destaques e os atributos) traga uma marca conhecida. Marca informada conhecida que nao aparece no titulo perde para uma marca
 *       conhecida que aparece nele. Uma marca nova nunca e trocada pela "mais parecida".</li>
 * </ul>
 */
public final class ResolvedorMarca {
    public static final String NAO_IDENTIFICADA = "nao_identificada";

    /** Marcas que nao dizem marca nenhuma (comparacao sem acento nem caixa). */
    private static final Set<String> GENERICAS = Set.of("generico", "generica", "sem marca", "importado", "importada", "oem", "original", "diversas",
            "diversos", "nacional", "generic", "nao informado", "outros", "outras", "sem marca definida", "marca propria", "no brand", "unbranded");

    private final Map<String, String> alias;                 // modo dicionario
    private final Map<String, Integer> contagem;             // modo lexico: marca normalizada -> quantas vezes o catalogo a informa
    private final Map<String, String> exibicao;              // modo lexico: marca normalizada -> grafia mais comum
    private final int minimoNoCatalogo;
    private final List<String> conhecidas;                   // modo lexico: marcas com contagem >= minimo, as mais longas primeiro

    /** Modo dicionario (v1). */
    public ResolvedorMarca(Taxonomia tx) {
        this.alias = tx.aliasesDeMarca();
        this.contagem = null; this.exibicao = null; this.minimoNoCatalogo = 0; this.conhecidas = null;
    }

    private ResolvedorMarca(Map<String, Integer> contagem, Map<String, String> exibicao, int minimo) {
        this.alias = null;
        this.contagem = contagem; this.exibicao = exibicao; this.minimoNoCatalogo = minimo;
        this.conhecidas = contagem.entrySet().stream().filter(e -> e.getValue() >= minimo).map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())).toList();
    }

    public boolean modoLexico() { return contagem != null; }

    /** Constroi o lexico (modo v2) lendo o catalogo em fluxo, linha a linha: so conta marca_informada, nunca guarda os produtos. */
    public static ResolvedorMarca doCatalogo(Path produtosJsonl, int minimo) throws IOException {
        Map<String, Integer> cont = new HashMap<>();
        Map<String, Map<String, Integer>> grafias = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(produtosJsonl, StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.isBlank()) continue;
                Map<String, Object> m = Json.mapa(Json.parse(l));
                String inf = m.get("marca_informada") instanceof String s ? s.trim() : "";
                String loja = Json.mapa(m.get("vendedor")).get("nome") instanceof String s ? s : "";
                String k = chave(inf);
                if (k.isEmpty() || GENERICAS.contains(k) || k.equals(chave(loja))) continue;
                cont.merge(k, 1, Integer::sum);
                grafias.computeIfAbsent(k, x -> new HashMap<>()).merge(inf, 1, Integer::sum);
            }
        }
        Map<String, String> exib = new HashMap<>();
        grafias.forEach((k, g) -> exib.put(k, g.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).get().getKey()));
        return new ResolvedorMarca(cont, exib, minimo);
    }

    /** Lexico a partir de uma lista (testes). */
    public static ResolvedorMarca doLexico(Collection<String> marcas) {
        Map<String, Integer> cont = new HashMap<>();
        Map<String, String> exib = new HashMap<>();
        for (String m : marcas) { cont.put(chave(m), 1000); exib.put(chave(m), m); }
        return new ResolvedorMarca(cont, exib, 1);
    }

    public int tamanhoDoLexico() { return conhecidas == null ? 0 : conhecidas.size(); }

    // ------------------------------------------------------------------ resolver

    public String resolver(String marcaInformada, String titulo) {
        if (modoLexico()) return resolver(new ProdutoEntrada(null, null, titulo, null, null, null, marcaInformada, null));
        for (String fonte : List.of(nvl(marcaInformada), nvl(titulo))) {
            String n = Texto.normalizar(fonte);
            if (n.isBlank()) continue;
            String direto = alias.get(n);
            if (direto != null) return direto;
            String[] palavras = n.split(" ");
            for (int tam = 3; tam >= 1; tam--) {
                for (int i = 0; i + tam <= palavras.length; i++) {
                    String trecho = String.join(" ", Arrays.copyOfRange(palavras, i, i + tam));
                    String m = alias.get(trecho);
                    if (m != null) return m;
                    if (trecho.length() >= 5) {
                        for (var e : alias.entrySet()) {
                            String k = e.getKey();
                            if (k.length() >= 5 && Math.abs(k.length() - trecho.length()) <= 1 && Texto.levenshtein(k, trecho) <= 1) return e.getValue();
                        }
                    }
                }
            }
        }
        return NAO_IDENTIFICADA;
    }

    /** A marca do produto. No modo lexico usa todos os campos do anuncio; no modo dicionario, so a marca informada e o titulo. */
    public String resolver(ProdutoEntrada p) {
        if (!modoLexico()) return resolver(p.marcaInformada(), p.titulo());
        String informada = nvl(p.marcaInformada()).trim();
        String k = chave(informada);
        String loja = p.vendedor() == null ? "" : chave(nvl(p.vendedor().get("nome")));
        boolean utilizavel = !k.isEmpty() && !GENERICAS.contains(k) && !k.equals(loja);
        boolean conhecida = utilizavel && contagem.getOrDefault(k, 0) >= minimoNoCatalogo;

        String titulo = chave(nvl(p.titulo()));
        String doTitulo = primeiraConhecida(titulo);
        if (utilizavel && conhecida) {
            // a informada vale, a menos que o titulo nomeie outra marca conhecida e nao a nomeie
            if (doTitulo != null && !contem(titulo, k)) return exibicao.get(doTitulo);
            return exibicao.get(k);
        }
        if (doTitulo != null) return exibicao.get(doTitulo);
        if (utilizavel) return informada; // marca nova: fica como o anunciante escreveu
        String doResto = primeiraConhecida(chave(restoDoTexto(p)));
        return doResto != null ? exibicao.get(doResto) : NAO_IDENTIFICADA;
    }

    /** Marca conhecida do lexico que aparece no texto (uma consulta de busca, por exemplo), na grafia mais comum do catalogo; nulo se nenhuma. So no modo lexico. */
    public String marcaNoTexto(String texto) {
        if (!modoLexico()) return null;
        String k = primeiraConhecida(chave(texto));
        return k == null ? null : exibicao.get(k);
    }

    private static String restoDoTexto(ProdutoEntrada p) {
        StringBuilder sb = new StringBuilder(nvl(p.descricao()));
        if (p.bullets() != null) for (String b : p.bullets()) sb.append(' ').append(nvl(b));
        if (p.atributos() != null) for (String v : p.atributos().values()) sb.append(' ').append(nvl(v));
        return sb.toString();
    }

    /** A marca conhecida que aparece primeiro no texto (a mais longa em caso de empate de posicao). */
    private String primeiraConhecida(String textoNormalizado) {
        String t = " " + textoNormalizado + " ";
        String melhor = null;
        int pos = Integer.MAX_VALUE;
        for (String b : conhecidas) { // as mais longas primeiro: a primeira a casar numa posicao ganha o empate
            int i = t.indexOf(" " + b + " ");
            if (i >= 0 && i < pos) { melhor = b; pos = i; }
        }
        return melhor;
    }

    private static boolean contem(String textoNormalizado, String marcaNormalizada) { return (" " + textoNormalizado + " ").contains(" " + marcaNormalizada + " "); }

    /** Sem acento, minusculas, so letras e numeros separados por um espaco ("Fisher-Price" e "fisher price" casam). */
    static String chave(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
