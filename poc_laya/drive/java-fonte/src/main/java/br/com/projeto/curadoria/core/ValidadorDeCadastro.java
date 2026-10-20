package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cadastro invalido, por regras de codigo (sem modelo): titulo que e lixo ("teste", "produto sem nome", ".", "aaaa"), titulo sem nenhuma
 * palavra de verdade, preco 0, 1 ou absurdo (99.999 ou mais) e descricao que e so enchimento ("teste", "descricao") sem EAN nem marca.
 * Uma regra basta. Os valores ajustaveis (titulos-lixo, limites de preco) vem da configuracao {@code curadoria.novos.*}.
 */
public final class ValidadorDeCadastro {

    public record Resultado(boolean invalido, String motivo) {
        static final Resultado VALIDO = new Resultado(false, null);
    }

    public static final Set<String> TITULOS_LIXO_PADRAO = Set.of("teste", "produto sem nome", "nao comprar", "item 123", "aaaa", "produto", "kit", "item", "sem nome", "xxx", "asdf");

    private final Set<String> titulosLixo;
    private final double precoMinimo, precoMaximo;

    public ValidadorDeCadastro() { this(TITULOS_LIXO_PADRAO, 1.0, 99_999.0); }

    /** @param precoMinimo preco menor ou igual a isto e invalido (0 e 1 centavo de brincadeira); @param precoMaximo preco maior ou igual e absurdo */
    public ValidadorDeCadastro(Set<String> titulosLixo, double precoMinimo, double precoMaximo) {
        this.titulosLixo = Set.copyOf(titulosLixo);
        this.precoMinimo = precoMinimo;
        this.precoMaximo = precoMaximo;
    }

    public Resultado validar(ProdutoEntrada p) {
        String titulo = FichaCodigo.chave(p.titulo());
        if (titulo.isEmpty() || titulo.replace(" ", "").length() < 2) return new Resultado(true, "titulo-vazio");
        if (titulosLixo.contains(titulo)) return new Resultado(true, "titulo-lixo");
        if (titulo.chars().filter(Character::isLetter).count() == 0) return new Resultado(true, "titulo-sem-letras");
        Double preco = preco(p.preco());
        if (preco != null && preco <= precoMinimo) return new Resultado(true, "preco-zero-ou-irrisorio");
        if (preco != null && preco >= precoMaximo) return new Resultado(true, "preco-absurdo");
        String desc = FichaCodigo.chave(p.descricao() == null ? "" : p.descricao().replaceAll("<[^>]+>", " "));
        boolean semIdentidade = (p.ean() == null || p.ean().isBlank()) && (p.marcaInformada() == null || p.marcaInformada().isBlank());
        if (semIdentidade && List.of("", "teste", "descricao").contains(desc) && titulo.split(" ").length <= 2) return new Resultado(true, "descricao-de-enchimento");
        return Resultado.VALIDO;
    }

    /** "R$ 1.234,56", "1234.56", "127,66", "736". Nulo se nao der para ler. */
    static Double preco(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[^0-9.,]", "");
        if (t.isEmpty()) return null;
        if (t.contains(",") && t.contains(".")) t = t.replace(".", "").replace(',', '.');
        else if (t.contains(",")) t = t.replace(',', '.');
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
    }

    @Override public String toString() { return "validador(preco " + precoMinimo + ".." + precoMaximo + ", " + titulosLixo.size() + " titulos-lixo)".toLowerCase(Locale.ROOT); }
}
