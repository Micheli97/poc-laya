package br.com.projeto.curadoria.core;

/**
 * Resultado de um sinal. Ha tres desfechos, e a diferenca importa:
 *  - {@link ComVoto}: o sinal votou numa categoria;
 *  - {@link SemVoto}: o sinal nao se aplica a este produto (vendedor generico, sinal desligado). NAO e falha;
 *  - {@link Falha}: o sinal quebrou (modelo fora do ar, timeout, JSON invalido). Qualquer falha deixa o produto MANTIDO.
 */
public sealed interface ResultadoSinal permits ResultadoSinal.ComVoto, ResultadoSinal.SemVoto, ResultadoSinal.Falha {

    String sinal();

    record ComVoto(Voto voto) implements ResultadoSinal {
        public String sinal() { return voto.sinal(); }
    }

    record SemVoto(String sinal, String motivo) implements ResultadoSinal {}

    record Falha(String sinal, String motivo) implements ResultadoSinal {}

    static ResultadoSinal voto(String sinal, String categoria, double confianca, boolean modelo) { return new ComVoto(new Voto(sinal, categoria, confianca, modelo)); }
    static ResultadoSinal voto(String sinal, String categoria, double confianca, boolean modelo, Double confiancaBruta) { return new ComVoto(new Voto(sinal, categoria, confianca, modelo, confiancaBruta)); }
    static ResultadoSinal semVoto(String sinal, String motivo) { return new SemVoto(sinal, motivo); }
    static ResultadoSinal falha(String sinal, String motivo) { return new Falha(sinal, motivo); }
}
