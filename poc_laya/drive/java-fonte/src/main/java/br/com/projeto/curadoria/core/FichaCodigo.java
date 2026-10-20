package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;
import br.com.projeto.curadoria.core.Taxonomia.AtributoDef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ficha tecnica por codigo, sem IA: le o esquema da subcategoria (atributos, valores, unidades, aliases e sinonimos da taxonomia v2)
 * e procura o valor nos campos do anuncio. Nunca inventa: atributo sem evidencia fica sem valor ({@link #extrairParcial}) ou
 * {@code nao_informado}.
 *
 * <p>Fontes, da mais confiavel para a menos: pares "chave: valor" do vendedor, da tabela "Especificacoes tecnicas" da descricao e dos
 * destaques (a chave tem de casar com o nome, a chave ou um alias do atributo); depois o texto livre do titulo e dos destaques
 * (voltagem, cor, numero com unidade, valor do enum como palavra inteira); e a frase "Garantia de N meses" da descricao. Cada fonte vota
 * no valor canonico com um peso; vence o valor com mais peso, e o empate perde para a fonte mais confiavel. Anuncios com atributo
 * conflitante entre as fontes (ruido do catalogo) sao decididos por esse voto.
 *
 * <p>Valores numericos: o numero e convertido para a unidade do esquema (kW para W, ml para L, mm para cm, g para kg, TB para GB...).
 * Numero sem unidade num campo com chave vale na unidade do esquema. No texto livre, numero com unidade so vale quando nenhum outro
 * atributo da subcategoria usa a mesma grandeza (largura, altura e profundidade, por exemplo, so saem por chave).
 */
public final class FichaCodigo implements FichaTecnica {

    static final double PESO_CHAVE = 3.0, PESO_DESTAQUE_CHAVE = 3.0, PESO_TEXTO_LIVRE = 2.0, PESO_FRASE = 3.0, MINIMO = 1.5;
    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern TABELA = Pattern.compile("(?i)especifica[cç][oõ]es t[eé]cnicas:\\s*");
    private static final Pattern GARANTIA = Pattern.compile("(?i)garantia(?: do (?:fabricante|fornecedor))?(?: de)?\\s*:?\\s*(\\d+)\\s*(meses|mes|anos?)\\b");

    /** Grandeza -> (palavra da unidade -> fator para a unidade base da grandeza). */
    private record Grandeza(String nome, Map<String, Double> unidades, String base) {}

    private static final Map<String, Grandeza> GRANDEZAS = new LinkedHashMap<>();
    /** unidade do esquema (sem acento, minuscula) -> grandeza */
    private static final Map<String, String> GRANDEZA_DA_UNIDADE = new HashMap<>();

    private static void grandeza(String nome, String base, Object... pares) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], ((Number) pares[i + 1]).doubleValue());
        GRANDEZAS.put(nome, new Grandeza(nome, m, base));
    }

    static {
        grandeza("massa", "kg", "kg", 1, "quilos", 1, "quilo", 1, "quilogramas", 1, "g", 0.001, "gramas", 0.001, "grama", 0.001);
        grandeza("massa_g", "g", "g", 1, "gramas", 1, "grama", 1, "kg", 1000, "quilos", 1000, "quilo", 1000);
        grandeza("comprimento", "cm", "cm", 1, "mm", 0.1, "m", 100, "metros", 100, "metro", 100);
        grandeza("comprimento_mm", "mm", "mm", 1, "cm", 10);
        grandeza("comprimento_m", "m", "m", 1, "metros", 1, "metro", 1, "cm", 0.01);
        grandeza("volume", "l", "l", 1, "litros", 1, "litro", 1, "lts", 1, "ml", 0.001);
        grandeza("volume_ml", "ml", "ml", 1, "l", 1000, "litros", 1000, "litro", 1000);
        grandeza("potencia", "w", "w", 1, "watts", 1, "watt", 1, "kw", 1000);
        grandeza("potencia_rms", "w", "w", 1, "watts", 1, "rms", 1);
        grandeza("energia", "kwh", "kwh", 1, "kwh mes", 1, "kwh/mes", 1);
        grandeza("meses", "meses", "meses", 1, "mes", 1, "anos", 12, "ano", 12);
        grandeza("gb", "gb", "gb", 1, "tb", 1024);
        grandeza("mah", "mah", "mah", 1, "ah", 1000);
        grandeza("ah", "ah", "ah", 1, "mah", 0.001);
        grandeza("pol", "pol", "pol", 1, "polegadas", 1, "polegada", 1, "\"", 1, "''", 1, "in", 1);
        grandeza("horas", "h", "h", 1, "horas", 1, "hora", 1);
        for (String u : new String[]{"mp", "hz", "btus", "psi", "garrafas", "portas", "bocas", "programas", "servicos", "velocidades", "canais", "fios", "lugares",
                "degraus", "jogadores", "unidades", "marchas", "pessoas", "materias", "folhas", "pecas", "paginas", "lumens", "rpm", "hdmi", "c", "km/h", "v"})
            grandeza(u, u, u, 1);
        GRANDEZA_DA_UNIDADE.put("l", "volume"); GRANDEZA_DA_UNIDADE.put("ml", "volume_ml"); GRANDEZA_DA_UNIDADE.put("kwh/mes", "energia");
        GRANDEZA_DA_UNIDADE.put("cm", "comprimento"); GRANDEZA_DA_UNIDADE.put("kg", "massa"); GRANDEZA_DA_UNIDADE.put("g", "massa_g");
        GRANDEZA_DA_UNIDADE.put("w", "potencia"); GRANDEZA_DA_UNIDADE.put("w rms", "potencia_rms"); GRANDEZA_DA_UNIDADE.put("mm", "comprimento_mm");
        GRANDEZA_DA_UNIDADE.put("m", "comprimento_m"); GRANDEZA_DA_UNIDADE.put("meses", "meses"); GRANDEZA_DA_UNIDADE.put("gb", "gb");
        GRANDEZA_DA_UNIDADE.put("mah", "mah"); GRANDEZA_DA_UNIDADE.put("ah", "ah"); GRANDEZA_DA_UNIDADE.put("\"", "pol"); GRANDEZA_DA_UNIDADE.put("h", "horas");
        GRANDEZA_DA_UNIDADE.put("°c", "c"); GRANDEZA_DA_UNIDADE.put("km/h", "km/h");
        for (String u : new String[]{"mp", "hz", "btus", "psi", "garrafas", "portas", "bocas", "programas", "servicos", "velocidades", "canais", "fios", "lugares",
                "degraus", "jogadores", "unidades", "marchas", "pessoas", "materias", "folhas", "pecas", "paginas", "lumens", "rpm", "hdmi"}) GRANDEZA_DA_UNIDADE.put(u, u);
    }

    private final Taxonomia tx;

    public FichaCodigo(Taxonomia tx) { this.tx = tx; }

    @Override public String nome() { return "codigo"; }

    @Override
    public Map<String, String> extrair(ProdutoEntrada p, Preparador.Preparado prep, String subcategoria) {
        Map<String, String> parcial = extrairParcial(p, subcategoria);
        Map<String, String> out = new LinkedHashMap<>();
        parcial.forEach((k, v) -> out.put(k, v == null ? NAO_INFORMADO : v));
        return out;
    }

    /** Um valor por atributo da subcategoria; nulo quando o codigo nao achou evidencia (e o Laya ou o LLM podem tentar). */
    public Map<String, String> extrairParcial(ProdutoEntrada p, String subcategoria) {
        Fontes f = Fontes.de(p);
        List<AtributoDef> defs = tx.atributosDe(subcategoria);
        Map<String, String> out = new LinkedHashMap<>();
        for (AtributoDef a : defs) out.put(a.chave(), resolver(a, f, defs));
        return out;
    }

    // ------------------------------------------------------------------ fontes

    private record Par(String fonte, String chave, String valor) {}

    private record Fontes(List<Par> pares, String titulo, List<String> destaquesLivres, String descricao) {
        static Fontes de(ProdutoEntrada p) {
            List<Par> pares = new ArrayList<>();
            if (p.atributos() != null) p.atributos().forEach((k, v) -> pares.add(new Par("vendedor", chave(k), v == null ? "" : v.trim())));
            String desc = p.descricao() == null ? "" : p.descricao().replaceAll("(?i)</p>|<br\\s*/?>", "\n").replaceAll("<[^>]+>", " ").replaceAll("[ \\t]+", " ").trim();
            Matcher m = TABELA.matcher(desc);
            String semTabela = desc;
            if (m.find()) {
                int ini = m.end();
                int fimLinha = desc.indexOf('\n', ini);
                String resto = fimLinha < 0 ? desc.substring(ini) : desc.substring(ini, fimLinha);
                Matcher fimFrase = Pattern.compile("\\.\\s+(?=[A-ZÀ-Ú])").matcher(resto);
                String tabela = fimFrase.find() ? resto.substring(0, fimFrase.start()) : resto;
                for (String par : tabela.split("\\s\\|\\s")) {
                    int dp = par.indexOf(':');
                    if (dp > 0) pares.add(new Par("tabela", chave(par.substring(0, dp)), par.substring(dp + 1).trim().replaceAll("\\.$", "")));
                }
                semTabela = desc.substring(0, m.start()) + " " + desc.substring(ini + tabela.length());
            }
            List<String> livres = new ArrayList<>();
            if (p.bullets() != null) for (String b : p.bullets()) {
                if (b == null || b.isBlank()) continue;
                int dp = b.indexOf(':');
                if (dp > 0 && dp < 40) pares.add(new Par("destaque", chave(b.substring(0, dp)), b.substring(dp + 1).trim().replaceAll("\\.$", "")));
                else livres.add(b.trim());
            }
            return new Fontes(pares, p.titulo() == null ? "" : p.titulo(), livres, semTabela);
        }
    }

    // ------------------------------------------------------------------ resolucao de um atributo

    private record Cand(String valor, double peso, int ordem) {}

    private String resolver(AtributoDef a, Fontes f, List<AtributoDef> todos) {
        List<Cand> cands = new ArrayList<>();
        Set<String> nomes = nomesDoAtributo(a);
        for (Par par : f.pares()) {
            String chavePar = ehBooleano(a) ? semPrefixo(par.chave()) : par.chave();
            if (!nomes.contains(chavePar)) continue;
            String v = converter(a, par.valor());
            if (v == null && ehBooleano(a) && chave(par.valor()).equals(par.chave())) v = "sim"; // "Certificacao Inmetro: Certificacao Inmetro"
            if (v == null) continue;
            switch (par.fonte()) {
                case "destaque" -> cands.add(new Cand(v, PESO_DESTAQUE_CHAVE, 2));
                case "tabela" -> cands.add(new Cand(v, PESO_CHAVE, 0));
                default -> cands.add(new Cand(v, PESO_CHAVE, 1));
            }
        }
        if (ehBooleano(a)) {
            for (String destaque : f.destaquesLivres()) {
                String v = booleanoSolto(a, nomes, destaque);
                if (v != null) cands.add(new Cand(v, PESO_TEXTO_LIVRE, 3));
            }
        }
        for (String livre : textosLivres(f)) {
            String v = livre(a, livre, todos);
            if (v != null) cands.add(new Cand(v, PESO_TEXTO_LIVRE, 3));
        }
        if (a.chave().equals("garantia_meses")) {
            Matcher g = GARANTIA.matcher(f.descricao() + " " + f.titulo() + " " + String.join(" ", f.destaquesLivres()));
            while (g.find()) {
                double meses = Double.parseDouble(g.group(1)) * (g.group(2).toLowerCase(Locale.ROOT).startsWith("ano") ? 12 : 1);
                String v = canonicoNumerico(a, meses);
                if (v != null) cands.add(new Cand(v, PESO_FRASE, 4));
            }
        }
        return escolher(cands);
    }

    private static final List<String> PREFIXOS_SIM = List.of("com", "possui", "tem", "acompanha", "inclui", "incluso"), PREFIXOS_NAO = List.of("sem", "nao possui", "nao tem", "nao acompanha");

    /** Destaque solto que e o nome do atributo booleano ("Certificacao Inmetro", "Com maleta", "Sem maleta"): sim ou nao. */
    private static String booleanoSolto(AtributoDef a, Set<String> nomes, String destaque) {
        String d = chave(destaque);
        if (nomes.contains(d)) return "sim";
        for (String p : PREFIXOS_SIM) if (d.startsWith(p + " ") && nomes.contains(d.substring(p.length() + 1))) return "sim";
        for (String p : PREFIXOS_NAO) if (d.startsWith(p + " ") && nomes.contains(d.substring(p.length() + 1))) return "não";
        return null;
    }

    private static String semPrefixo(String chaveNormalizada) {
        for (String p : PREFIXOS_SIM) if (chaveNormalizada.startsWith(p + " ")) return chaveNormalizada.substring(p.length() + 1);
        return chaveNormalizada;
    }

    private static List<String> textosLivres(Fontes f) {
        List<String> l = new ArrayList<>();
        l.add(f.titulo());
        l.addAll(f.destaquesLivres());
        return l;
    }

    private static String escolher(List<Cand> cands) {
        if (cands.isEmpty()) return null;
        Map<String, double[]> soma = new LinkedHashMap<>(); // valor -> {peso total, melhor (menor) ordem}
        for (Cand c : cands) {
            double[] s = soma.computeIfAbsent(c.valor(), k -> new double[]{0, 99});
            s[0] += c.peso();
            s[1] = Math.min(s[1], c.ordem());
        }
        String melhor = null;
        double[] ms = null;
        for (var e : soma.entrySet()) {
            double[] s = e.getValue();
            if (melhor == null || s[0] > ms[0] || (s[0] == ms[0] && s[1] < ms[1])) { melhor = e.getKey(); ms = s; }
        }
        return ms[0] >= MINIMO ? melhor : null;
    }

    // ------------------------------------------------------------------ nomes (chaves)

    Set<String> nomesDoAtributo(AtributoDef a) {
        Set<String> s = new HashSet<>();
        s.add(chave(a.chave()));
        if (a.nome() != null) s.add(chave(a.nome()));
        if (a.nomeEn() != null) s.add(chave(a.nomeEn()));
        for (String al : a.aliasesVendedor()) s.add(chave(al));
        s.remove("");
        return s;
    }

    // ------------------------------------------------------------------ conversao de um valor com chave

    String converter(AtributoDef a, String valor) {
        if (valor == null || valor.isBlank()) return null;
        switch (a.tipo()) {
            case "texto": {
                String t = valor.trim().replaceFirst("(?i)^para\\s+", "");
                return t.isEmpty() ? null : t;
            }
            case "num": {
                Double n = numeroConvertido(a, valor, true);
                return n == null ? null : formatar(n);
            }
            default:
                return canonico(a, valor);
        }
    }

    /** Valor canonico de um atributo enumerado a partir de um texto: igual, sinonimo, numero equivalente ou unico valor contido. */
    String canonico(AtributoDef a, String valor) {
        String n = chave(valor);
        if (n.isEmpty()) return null;
        List<String> valores = a.valores();
        for (String v : valores) if (chave(v).equals(n)) return v;
        Map<String, List<String>> sin = tx.sinonimosDeValor(a.chave());
        if (!sin.isEmpty()) {
            for (var e : sin.entrySet()) if (valores.contains(e.getKey()) && e.getValue().stream().anyMatch(x -> chave(x).equals(n))) return e.getKey();
        }
        if (ehBooleano(a)) {
            for (var e : tx.sinonimosDeValor(Taxonomia.BOOLEANO).entrySet())
                if (valores.contains(e.getKey()) && e.getValue().stream().anyMatch(x -> chave(x).equals(n))) return e.getKey();
            return null;
        }
        if (numerico(a)) {
            Double num = numeroConvertido(a, valor, true);
            return num == null ? null : canonicoNumerico(a, num);
        }
        return unicoContido(a, n);
    }

    private static boolean ehBooleano(AtributoDef a) {
        return a.valores().size() == 2 && a.valores().contains("sim") && a.valores().contains("não");
    }

    private static boolean numerico(AtributoDef a) {
        return "enum".equals(a.tipo()) && !a.valores().isEmpty() && a.valores().stream().allMatch(v -> v.matches("\\d+(?:[.,]\\d+)?"));
    }

    private String canonicoNumerico(AtributoDef a, double n) {
        if (!numerico(a)) return null;
        for (String v : a.valores()) if (Math.abs(Double.parseDouble(v.replace(',', '.')) - n) < 1e-9) return v;
        return null;
    }

    /** Exatamente um valor (ou sinonimo) do atributo aparece como palavra inteira no texto normalizado; senao nulo. */
    private String unicoContido(AtributoDef a, String textoNormalizado) {
        String t = " " + textoNormalizado + " ";
        Set<String> achados = new LinkedHashSet<>();
        for (String v : a.valores()) {
            String k = chave(v);
            if (k.length() >= 3 && !k.matches("\\d+") && t.contains(" " + k + " ")) achados.add(v);
        }
        for (var e : tx.sinonimosDeValor(a.chave()).entrySet()) {
            if (!a.valores().contains(e.getKey())) continue;
            for (String s : e.getValue()) { String k = chave(s); if (k.length() >= 3 && t.contains(" " + k + " ")) achados.add(e.getKey()); }
        }
        // "aco inox" contem "aco" e "inox": o valor mais longo contido em outro vale mais; ficam so os que nao estao dentro de outro achado
        Set<String> finais = new LinkedHashSet<>(achados);
        for (String x : achados) for (String y : achados) if (!x.equals(y) && chave(y).contains(chave(x))) finais.remove(x);
        return finais.size() == 1 ? finais.iterator().next() : null;
    }

    // ------------------------------------------------------------------ numeros e unidades

    private Grandeza grandezaDe(AtributoDef a) {
        if (a.unidade() == null) return null;
        String u = chave(a.unidade()).replace("kwh mes", "kwh/mes");
        String g = GRANDEZA_DA_UNIDADE.get(u);
        if (g == null && a.unidade().equals("\"")) g = "pol";
        if (g == null && a.unidade().toLowerCase(Locale.ROOT).contains("rms")) g = "potencia_rms";
        if (g == null && a.unidade().equals("°C")) g = "c";
        if (g == null && a.unidade().equals("kWh/mês")) g = "energia";
        return g == null ? null : GRANDEZAS.get(g);
    }

    /**
     * O numero do texto na unidade do esquema. Sem unidade no texto vale na do esquema (so se {@code aceitaSemUnidade}); com unidade de outra
     * grandeza nao vale.
     */
    private Double numeroConvertido(AtributoDef a, String texto, boolean aceitaSemUnidade) {
        Matcher m = Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*([^\\d\\s][^\\d]*?)?\\s*$").matcher(texto.replace(' ', ' '));
        if (!m.matches()) return null;
        double n = Double.parseDouble(m.group(1).replace(',', '.'));
        String un = m.group(2) == null ? "" : chave(m.group(2)).replace("\"", "");
        if (m.group(2) != null && m.group(2).contains("\"")) un = "\"";
        if (un.isEmpty()) return aceitaSemUnidade ? n : null;
        Grandeza g = grandezaDe(a);
        if (g == null) return aceitaSemUnidade ? n : null;
        Double fator = g.unidades().get(un);
        if (fator == null) fator = g.unidades().get(un.replace(" ", ""));
        return fator == null ? null : n * fator;
    }

    private static String formatar(double n) {
        return new BigDecimal(n).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ texto livre

    private String livre(AtributoDef a, String texto, List<AtributoDef> todos) {
        String t = texto.replace(' ', ' ');
        if ("texto".equals(a.tipo())) return null;
        if ("num".equals(a.tipo()) || numerico(a)) {
            Grandeza g = grandezaDe(a);
            if (g == null || a.chave().equals("garantia_meses")) return null;
            if (todos.stream().filter(x -> x != a).anyMatch(x -> grandezaDe(x) == g || (g.nome().startsWith("comprimento") && grandezaDe(x) != null && grandezaDe(x).nome().startsWith("comprimento")))) return null;
            if (g.nome().startsWith("comprimento")) return null; // dimensao solta no texto nao diz qual e
            Set<String> achados = new LinkedHashSet<>();
            for (String un : g.unidades().keySet()) {
                Matcher m = Pattern.compile("(?i)(?<![\\w.,])(\\d+(?:[.,]\\d+)?)\\s*" + (un.equals("\"") ? "\"" : Pattern.quote(un) + "(?![\\p{L}\\d])")).matcher(t);
                while (m.find()) {
                    double n = Double.parseDouble(m.group(1).replace(',', '.')) * g.unidades().get(un);
                    String v = "num".equals(a.tipo()) ? formatar(n) : canonicoNumerico(a, n);
                    if (v != null) achados.add(v);
                }
            }
            return achados.size() == 1 ? achados.iterator().next() : null;
        }
        if (ehBooleano(a)) return null;
        String n = chave(t);
        if (a.chave().equals("voltagem")) return voltagem(a, t);
        return unicoContido(a, n);
    }

    private String voltagem(AtributoDef a, String texto) {
        Map<String, List<String>> sin = tx.sinonimosDeValor("voltagem");
        String t = " " + chave(texto) + " ";
        Set<String> achados = new LinkedHashSet<>();
        // as escritas mais longas primeiro ("110 220v" antes de "220v"), e o trecho achado sai do texto
        List<String[]> escritas = new ArrayList<>();
        for (var e : sin.entrySet()) if (a.valores().contains(e.getKey())) for (String s : e.getValue()) escritas.add(new String[]{chave(s), e.getKey()});
        for (String v : a.valores()) escritas.add(new String[]{chave(v), v});
        escritas.sort((x, y) -> y[0].length() - x[0].length());
        for (String[] w : escritas) {
            if (w[0].length() < 3) continue;
            String pad = " " + w[0] + " ";
            if (t.contains(pad)) { achados.add(w[1]); t = t.replace(pad, " "); }
        }
        return achados.size() == 1 ? achados.iterator().next() : null;
    }

    /** Sem acento, minusculas, parenteses removidos, so letras e numeros separados por um espaco. */
    static String chave(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", " ").replaceAll("[^a-z0-9/]+", " ").trim();
    }
}
