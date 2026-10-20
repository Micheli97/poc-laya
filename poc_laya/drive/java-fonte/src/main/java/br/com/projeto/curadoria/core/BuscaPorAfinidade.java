package br.com.projeto.curadoria.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entende a busca do cliente SEM chamar o modelo, em duas etapas:
 *  1. sinonimo: termos mantidos pelo negocio (taxonomia/sinonimos-busca.json) -> categoria + tipo;
 *  2. afinidade: aprendida do proprio catalogo curado. Para cada primeira palavra de titulo, conta em quais
 *     categorias os produtos curados caem; se a consulta comeca por uma palavra dominante, usa essa categoria.
 * O modelo fica como ultimo recurso (quem chama decide).
 */
public final class BuscaPorAfinidade {

    public static final String SINONIMO = "sinonimo";
    public static final String AFINIDADE = "afinidade";

    public record Termo(String termo, String categoria, String tipo) {}

    public record Resolucao(String categoria, String tipo, double confianca, String origem) {}

    /** Palavras que ligam um complemento ao termo ("grelha PARA fogao"): a busca nao e sobre o termo sozinho. */
    private static final Set<String> CONECTORES = Set.of("de", "da", "do", "das", "dos", "para", "pra", "com", "sem", "p", "pro");
    /** Palavras sem valor para identificar o assunto de um titulo ou de uma busca. */
    private static final Set<String> IRRELEVANTES = Set.of("de", "da", "do", "das", "dos", "para", "pra", "com", "sem", "e", "em", "no", "na", "o", "a", "os", "as",
            "um", "uma", "kit", "conjunto", "novo", "nova", "original");

    private final Taxonomia tx;
    private final List<Termo> termos;
    private final int minProdutos;
    private final double minParticipacao;

    /** primeira palavra (radical) -> categoria -> contagem */
    private final Map<String, Map<String, Integer>> contagens = new ConcurrentHashMap<>();
    /** id do produto -> {palavra, categoria} ja contados, para nao contar duas vezes o mesmo produto */
    private final Map<String, String[]> contadoPorProduto = new ConcurrentHashMap<>();

    public BuscaPorAfinidade(Taxonomia tx, List<Termo> termos, int minProdutos, double minParticipacao) {
        this.tx = tx;
        this.termos = termos.stream().sorted(Comparator.comparingInt((Termo t) -> radicais(t.termo()).size()).reversed()).toList();
        this.minProdutos = minProdutos;
        this.minParticipacao = minParticipacao;
        for (Termo t : termos)
            if (!tx.categoriaValida(t.categoria())) throw new IllegalStateException("sinonimo \"" + t.termo() + "\" aponta para categoria inexistente: " + t.categoria());
    }

    /** Le o JSON {"termos":[{"termo","categoria","tipo"}]}. Arquivo ausente = sem sinonimos (so afinidade e modelo). */
    @SuppressWarnings("unchecked")
    public static BuscaPorAfinidade carregar(Path arquivo, Taxonomia tx, int minProdutos, double minParticipacao) throws IOException {
        List<Termo> lista = new ArrayList<>();
        if (arquivo != null && Files.exists(arquivo)) {
            Map<String, Object> raiz = (Map<String, Object>) Json.parse(Files.readString(arquivo));
            for (Object o : (List<Object>) raiz.get("termos")) {
                Map<String, Object> m = (Map<String, Object>) o;
                String cat = (String) m.get("categoria");
                String tipo = m.get("tipo") instanceof String s ? s : tx.tipoPadrao(cat);
                lista.add(new Termo((String) m.get("termo"), cat, tipo));
            }
        }
        return new BuscaPorAfinidade(tx, lista, minProdutos, minParticipacao);
    }

    public int quantidadeDeTermos() { return termos.size(); }

    // =====================================================================
    // 1. sinonimo
    // =====================================================================
    public Optional<Resolucao> porSinonimo(String consulta) {
        List<String> q = radicais(consulta);
        if (q.isEmpty()) return Optional.empty();
        for (Termo t : termos) { // do termo mais longo para o mais curto
            List<String> tr = radicais(t.termo());
            int pos = indiceDe(q, tr);
            if (pos < 0) continue;
            boolean igual = tr.size() == q.size();
            // termo contido em busca maior so vale se o resto nao for um complemento ("grelha PARA fogao" nao e fogao)
            if (!igual && restoTemConector(q, pos, tr.size())) continue;
            return Optional.of(new Resolucao(t.categoria(), t.tipo() != null ? t.tipo() : tx.tipoPadrao(t.categoria()), 1.0, SINONIMO));
        }
        return Optional.empty();
    }

    // =====================================================================
    // 2. afinidade aprendida do catalogo curado
    // =====================================================================

    /** Registra que o produto (curado: aprovado ou revisado) ficou nesta categoria. */
    public void aprender(String idProduto, String tituloLimpo, String categoria) {
        String palavra = primeiraPalavra(tituloLimpo);
        if (palavra == null || categoria == null) return;
        String[] antes = contadoPorProduto.put(idProduto, new String[]{palavra, categoria});
        if (antes != null) {
            if (antes[0].equals(palavra) && antes[1].equals(categoria)) return;
            contagens.computeIfPresent(antes[0], (k, m) -> { m.merge(antes[1], -1, Integer::sum); return m; });
        }
        contagens.computeIfAbsent(palavra, k -> new ConcurrentHashMap<>()).merge(categoria, 1, Integer::sum);
    }

    /** Categoria dominante da primeira palavra relevante da consulta, se tiver amostra e participacao suficientes. */
    public Optional<Resolucao> porAfinidade(String consulta) {
        String palavra = primeiraPalavra(consulta);
        if (palavra == null) return Optional.empty();
        Map<String, Integer> m = contagens.get(palavra);
        if (m == null) return Optional.empty();
        int total = m.values().stream().mapToInt(Integer::intValue).sum();
        if (total < minProdutos) return Optional.empty();
        var topo = m.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        double participacao = (double) topo.getValue() / total;
        String tipo = tx.tipoPadrao(topo.getKey());
        if (participacao < minParticipacao || tipo == null) return Optional.empty();
        return Optional.of(new Resolucao(topo.getKey(), tipo, Math.round(participacao * 1000) / 1000.0, AFINIDADE));
    }

    // =====================================================================
    // texto
    // =====================================================================
    static String primeiraPalavra(String texto) {
        for (String t : Taxonomia.normalizarParaCasamento(texto).split(" ")) {
            if (t.length() < 2 || IRRELEVANTES.contains(t) || t.chars().allMatch(Character::isDigit)) continue;
            return radical(t);
        }
        return null;
    }

    static List<String> radicais(String texto) {
        String n = Taxonomia.normalizarParaCasamento(texto);
        return n.isEmpty() ? List.of() : Arrays.stream(n.split(" ")).map(BuscaPorAfinidade::radical).toList();
    }

    /** Singular simples: "fogoes" -> "fogao", "pneus" -> "pneu". Nao precisa ser linguisticamente correto, so consistente. */
    static String radical(String t) {
        if (t.length() > 4 && t.endsWith("oes")) return t.substring(0, t.length() - 3) + "ao";
        if (t.length() > 3 && t.endsWith("s")) return t.substring(0, t.length() - 1);
        return t;
    }

    private static int indiceDe(List<String> texto, List<String> trecho) {
        if (trecho.isEmpty()) return -1;
        for (int i = 0; i + trecho.size() <= texto.size(); i++)
            if (texto.subList(i, i + trecho.size()).equals(trecho)) return i;
        return -1;
    }

    private static boolean restoTemConector(List<String> q, int pos, int tam) {
        for (int i = 0; i < q.size(); i++)
            if ((i < pos || i >= pos + tam) && CONECTORES.contains(q.get(i))) return true;
        return false;
    }
}
