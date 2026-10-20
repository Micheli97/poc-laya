package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Etapa 1: transforma o anuncio bruto num texto curto e limpo para o modelo.
 * Tudo aqui e regra fixa (codigo), nao IA.
 */
public final class Preparador {

    /** Lixo promocional que nao ajuda a classificar (comparacao sem acento e sem caixa). */
    private static final List<Pattern> PROMO = List.of(
            "mega promo", "super oferta", "queima de estoque", "promocao", "oferta do dia", "oferta", "lancamento",
            "frete gratis", "envio imediato", "envio 24h", "pronta entrega", "garantia 1 ano", "original", "lacrado",
            "c/ nf", "\\+ brinde"
    ).stream().map(p -> Pattern.compile("(?i)(?<![\\p{L}])" + p + "(?![\\p{L}])")).toList();

    private static final int LIMITE_DESCRICAO = 280;

    public record Preparado(String state, String tituloLimpo) {}

    /** Catalogo v2: a descricao traz especificacoes no final, entao o limite e maior; modelo e bullets entram no texto. */
    private static final int LIMITE_DESCRICAO_V2 = 600;

    private final boolean incluirCategoriaDoVendedor;
    private final boolean v2;

    public Preparador() { this(true); }

    /**
     * @param incluirCategoriaDoVendedor false: a linha "Categoria do vendedor" nao entra no texto do modelo.
     *        Serve para o modelo nao copiar a categoria do vendedor, que e a pista mais enganosa do anuncio.
     */
    public Preparador(boolean incluirCategoriaDoVendedor) { this(incluirCategoriaDoVendedor, false); }

    /** @param v2 true para a taxonomia v2: o texto tambem leva modelo e bullets, e a descricao pode ser maior */
    public Preparador(boolean incluirCategoriaDoVendedor, boolean v2) { this.incluirCategoriaDoVendedor = incluirCategoriaDoVendedor; this.v2 = v2; }

    public Preparado preparar(ProdutoEntrada p) {
        String titulo = limparTitulo(p.titulo());
        StringBuilder sb = new StringBuilder("Titulo: ").append(titulo).append('\n');
        // todos os campos passam pela limpeza de entrada (metades de emoji e caracteres de controle) antes de qualquer modelo
        String categoriaVendedor = limparEntrada(p.categoriaVendedor()), marcaInformada = limparEntrada(p.marcaInformada()), modelo = limparEntrada(p.modelo());
        if (incluirCategoriaDoVendedor && temTexto(categoriaVendedor)) sb.append("Categoria do vendedor: ").append(categoriaVendedor.trim()).append('\n');
        if (temTexto(marcaInformada)) sb.append("Marca informada: ").append(marcaInformada.trim()).append('\n');
        if (v2 && temTexto(modelo)) sb.append("Modelo: ").append(modelo.trim()).append('\n');
        Map<String, String> at = p.atributos();
        if (at != null && !at.isEmpty())
            sb.append("Atributos: ").append(at.entrySet().stream().map(e -> limparEntrada(e.getKey()) + "=" + normalizarVoltagem(limparEntrada(e.getValue()))).collect(Collectors.joining(", "))).append('\n');
        if (v2 && p.bullets() != null && !p.bullets().isEmpty())
            sb.append("Destaques: ").append(p.bullets().stream().map(Preparador::limparEntrada).filter(Preparador::temTexto).map(String::trim).collect(Collectors.joining(" | "))).append('\n');
        String desc = p.descricao() == null ? "" : limparEntrada(p.descricao()).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int limite = v2 ? LIMITE_DESCRICAO_V2 : LIMITE_DESCRICAO;
        if (!desc.isEmpty()) sb.append("Descricao: ").append(desc.length() > limite ? desc.substring(0, limite) + "..." : desc);
        // tags ficam de fora: em catalogo real sao SEO ("fogao", "iphone", "black friday") e confundem o modelo
        return new Preparado(sb.toString().trim(), titulo);
    }

    /**
     * Limpeza de entrada: tira as metades de emoji (surrogates soltos, que um anuncio cortado deixa para tras), o caractere de
     * substituicao U+FFFD e os caracteres de controle, exceto quebra de linha e tabulacao. Emoji inteiro nao e tocado aqui
     * (o titulo ja o remove em limparTitulo). O erro da rodada 7 foi uma metade de emoji chegando ao modelo.
     */
    static String limparEntrada(String s) {
        if (s == null) return null;
        return s.codePoints()
                .filter(cp -> { int t = Character.getType(cp); return t != Character.SURROGATE && cp != 0xFFFD && (t != Character.CONTROL || cp == '\n' || cp == '\t'); })
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    String limparTitulo(String bruto) {
        if (bruto == null) return "";
        bruto = limparEntrada(bruto);
        // remove emojis e simbolos graficos
        String t = bruto.codePoints().filter(cp -> Character.getType(cp) != Character.OTHER_SYMBOL && cp < 0x1F000)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        // remove termos promocionais comparando sem acento
        String semAcento = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (semAcento.length() == t.length()) { // preserva o texto original, so apaga os trechos promocionais
            StringBuilder out = new StringBuilder(t);
            for (Pattern p : PROMO) {
                var m = p.matcher(semAcento);
                while (m.find()) for (int i = m.start(); i < m.end(); i++) out.setCharAt(i, ' ');
            }
            t = out.toString();
        }
        t = normalizarVoltagem(t);
        return t.replaceAll("\\s+", " ").replaceAll("^[\\s\\-]+|[\\s\\-]+$", "").trim();
    }

    /** 110v e 127v sao a mesma rede; padroniza as varias formas de escrever. */
    static String normalizarVoltagem(String s) {
        if (s == null) return null;
        return s.replaceAll("(?i)\\b(110|127)\\s?/\\s?(220)\\s?v?\\b", "Bivolt")
                .replaceAll("(?i)\\bbi-?volt\\b", "Bivolt")
                .replaceAll("(?i)\\b(110|127)\\s?(v|volts)\\b", "127V")
                .replaceAll("(?i)\\b220\\s?(v|volts)\\b", "220V");
    }

    private static boolean temTexto(String s) { return s != null && !s.isBlank(); }
}
