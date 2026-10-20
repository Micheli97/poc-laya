package br.com.projeto.curadoria.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * O catalogo ja curado (subcategoria, marca e ficha tecnica de cada produto), em memoria, para a busca. Uma linha do arquivo curado por produto,
 * na mesma ordem do catalogo; o titulo vem do catalogo, lido em paralelo, em fluxo. Guarda so inteiros e pequenos mapas (100 mil produtos cabem em ~100 MB).
 */
public final class CatalogoCurado {

    public record Item(String id, String sub, boolean votacao, double conf, String marcaChave, Map<String, String> filtros, int[] titulo) {}

    private final List<Item> itens = new ArrayList<>();
    private final Map<String, Integer> dic = new HashMap<>();
    private final Map<String, List<Integer>> porSub = new HashMap<>();
    private final Map<Integer, int[]> porToken = new HashMap<>();
    private final Map<String, Integer> frequencia = new HashMap<>();   // palavra do titulo -> em quantos produtos aparece

    public int tamanho() { return itens.size(); }
    public Map<String, Integer> vocabulario() { return frequencia; }

    /** Palavras do titulo (sem acento, minusculas, sem plural) usadas na busca textual. */
    public static List<String> palavras(String texto) {
        List<String> out = new ArrayList<>();
        for (String w : FichaCodigo.chave(texto).split(" ")) if (w.length() >= 2) out.add(radical(w));
        return out;
    }

    /** Tira o plural simples: "fogoes" e "fogao" casam, "geladeiras" e "geladeira" tambem. */
    public static String radical(String w) {
        if (w.length() > 4 && w.endsWith("oes")) return w.substring(0, w.length() - 3) + "ao";
        if (w.length() > 4 && w.endsWith("aes")) return w.substring(0, w.length() - 3) + "ao";
        if (w.length() > 4 && w.endsWith("ais")) return w.substring(0, w.length() - 3) + "al";
        if (w.length() > 4 && w.endsWith("eis")) return w.substring(0, w.length() - 3) + "el";
        if (w.length() > 4 && (w.endsWith("res") || w.endsWith("zes"))) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    public static CatalogoCurado carregar(Path curado, Path produtosJsonl) throws IOException {
        CatalogoCurado c = new CatalogoCurado();
        Map<Integer, List<Integer>> post = new HashMap<>();
        try (BufferedReader rc = Files.newBufferedReader(curado, StandardCharsets.UTF_8); BufferedReader rp = Files.newBufferedReader(produtosJsonl, StandardCharsets.UTF_8)) {
            String lc, lp;
            while ((lc = rc.readLine()) != null && (lp = rp.readLine()) != null) {
                if (lc.isBlank()) continue;
                Map<String, Object> o = Json.mapa(Json.parse(lc)), p = Json.mapa(Json.parse(lp));
                if (!Objects.equals(o.get("id"), p.get("id"))) throw new IOException("catalogo curado fora de ordem em " + o.get("id") + " / " + p.get("id"));
                Map<String, String> filtros = new LinkedHashMap<>();
                Json.mapa(o.get("filtros")).forEach((k, v) -> filtros.put(k, String.valueOf(v)));
                Set<Integer> tok = new TreeSet<>();
                for (String w : palavras((String) p.get("titulo"))) { tok.add(c.dic.computeIfAbsent(w, k -> c.dic.size())); c.frequencia.merge(w, 1, Integer::sum); }
                int[] t = tok.stream().mapToInt(Integer::intValue).toArray();
                String marca = o.get("marca") instanceof String m && !m.equals(ResolvedorMarca.NAO_IDENTIFICADA) ? FichaCodigo.chave(m) : "";
                Item it = new Item((String) o.get("id"), (String) o.get("sub"), "votacao".equals(o.get("origem")), ((Number) o.get("confNb")).doubleValue(), marca, filtros, t);
                int idx = c.itens.size();
                c.itens.add(it);
                c.porSub.computeIfAbsent(it.sub(), k -> new ArrayList<>()).add(idx);
                for (int w : t) post.computeIfAbsent(w, k -> new ArrayList<>()).add(idx);
            }
        }
        post.forEach((k, v) -> c.porToken.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        return c;
    }

    // ------------------------------------------------------------------ busca

    /**
     * Produtos das subcategorias da intencao, da marca e com TODOS os filtros (se houver), ordenados por: palavras da consulta no titulo, produto
     * decidido pela votacao antes do decidido so pelo NB, confianca do NB.
     */
    public List<String> buscar(Collection<String> subs, Map<String, String> filtros, String marcaChave, List<String> palavrasDaConsulta, int limite) {
        List<int[]> cand = new ArrayList<>();
        for (String s : subs) {
            List<Integer> l = porSub.get(s);
            if (l == null) continue;
            for (int i : l) {
                Item it = itens.get(i);
                if (marcaChave != null && !marcaChave.isEmpty() && !marcaChave.equals(it.marcaChave())) continue;
                boolean ok = true;
                for (var f : filtros.entrySet()) if (!f.getValue().equalsIgnoreCase(it.filtros().get(f.getKey()))) { ok = false; break; }
                if (!ok) continue;
                int casadas = 0;
                for (String w : palavrasDaConsulta) { Integer d = dic.get(w); if (d != null && Arrays.binarySearch(it.titulo(), d) >= 0) casadas++; }
                cand.add(new int[]{i, casadas});
            }
        }
        cand.sort((a, b) -> {
            if (a[1] != b[1]) return b[1] - a[1];
            Item x = itens.get(a[0]), y = itens.get(b[0]);
            if (x.votacao() != y.votacao()) return x.votacao() ? -1 : 1;
            return Double.compare(y.conf(), x.conf());
        });
        List<String> out = new ArrayList<>();
        for (int[] c : cand) { out.add(itens.get(c[0]).id()); if (out.size() >= limite) break; }
        return out;
    }

    /** Busca por palavra, como a de hoje: todas as palavras da consulta no titulo. Serve de reserva quando a intencao nao e entendida. */
    public List<String> buscarPorTexto(List<String> palavrasDaConsulta, int limite) {
        List<Integer> ids = new ArrayList<>();
        for (String w : palavrasDaConsulta) ids.add(dic.getOrDefault(w, -1));
        if (ids.isEmpty() || ids.contains(-1)) return List.of();
        int[] base = null;
        for (int d : ids) { int[] pl = porToken.get(d); if (pl == null) return List.of(); if (base == null || pl.length < base.length) base = pl; }
        List<String> out = new ArrayList<>();
        for (int i : base) {
            boolean todas = true;
            for (int d : ids) if (Arrays.binarySearch(itens.get(i).titulo(), d) < 0) { todas = false; break; }
            if (todas) { out.add(itens.get(i).id()); if (out.size() >= limite) break; }
        }
        return out;
    }
}
