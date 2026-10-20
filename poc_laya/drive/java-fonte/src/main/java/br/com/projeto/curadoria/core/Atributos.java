package br.com.projeto.curadoria.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voltagem e cor resolvidas por codigo, sem IA: o que a regra resolve nao vai para o modelo.
 * Procura primeiro no titulo limpo e depois nos atributos do anuncio.
 */
public final class Atributos {

    private static final Pattern VOLTAGEM = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(bivolt|127v|220v)(?![\\p{L}\\p{N}])");

    private final Taxonomia tx;

    public Atributos(Taxonomia tx) { this.tx = tx; }

    /**
     * 127v, 220v ou bivolt quando aparece no titulo ou nos atributos (110v conta como 127v; os dois juntos, bivolt).
     * Nao achou: nao_informado se a categoria e eletrica, nao_se_aplica se nao e.
     */
    public String voltagem(String tituloLimpo, Map<String, String> atributos, String categoria) {
        String achou = voltagemEm(tituloLimpo);
        if (achou == null && atributos != null) {
            Set<String> todas = new LinkedHashSet<>();
            for (String v : atributos.values()) { String a = voltagemEm(v); if (a != null) todas.add(a); }
            achou = juntar(todas);
        }
        if (achou != null) return achou;
        return tx.eletrica(categoria) ? "nao_informado" : "nao_se_aplica";
    }

    /**
     * Cor pelos sinonimos (palavra inteira). Com varias cores no titulo, vale a primeira que aparece
     * ("preto com detalhes em rosa" -> preto). Nao achou: nao_informado, ou nao_se_aplica se a categoria nao tem cor.
     */
    public String cor(String tituloLimpo, Map<String, String> atributos, String categoria) {
        String achou = primeiraCor(Texto.normalizar(tituloLimpo));
        if (achou == null && atributos != null) {
            for (String v : atributos.values()) { achou = primeiraCor(Texto.normalizar(v)); if (achou != null) break; }
        }
        if (achou != null) return achou;
        return tx.temCor(categoria) ? "nao_informado" : "nao_se_aplica";
    }

    private static String voltagemEm(String texto) {
        if (texto == null) return null;
        Matcher m = VOLTAGEM.matcher(Preparador.normalizarVoltagem(texto));
        Set<String> achados = new LinkedHashSet<>();
        while (m.find()) achados.add(m.group(1).toLowerCase(Locale.ROOT));
        return juntar(achados);
    }

    private static String juntar(Set<String> achados) {
        if (achados.isEmpty()) return null;
        if (achados.contains("bivolt") || achados.size() > 1) return "bivolt";
        return achados.iterator().next();
    }

    private String primeiraCor(String textoNormalizado) {
        if (textoNormalizado.isBlank()) return null;
        String t = " " + textoNormalizado.replaceAll("[/\\-]", " ") + " ";
        String melhor = null;
        int melhorPos = Integer.MAX_VALUE;
        for (var e : tx.sinonimosDeCor().entrySet()) {
            for (String sin : e.getValue()) {
                int pos = t.indexOf(" " + Taxonomia.normalizarParaCasamento(sin) + " ");
                if (pos >= 0 && pos < melhorPos) { melhorPos = pos; melhor = e.getKey(); }
            }
        }
        return melhor;
    }
}
