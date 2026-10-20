package br.com.projeto.curadoria.core;

/**
 * O voto de um sinal independente: em qual categoria ele acha que o produto fica e com que confianca (0 a 1).
 *
 * @param modelo         true para sinais que sao modelos (Laya, LLM, supervisionado); false para o vendedor.
 *                       A politica pode exigir que pelo menos um dos concordantes seja modelo.
 * @param confiancaBruta antes da calibracao (so o Laya tem; nula nos demais). Serve para medir o ECE antes e depois de calibrar.
 */
public record Voto(String sinal, String categoria, double confianca, boolean modelo, Double confiancaBruta) {
    public Voto(String sinal, String categoria, double confianca, boolean modelo) { this(sinal, categoria, confianca, modelo, null); }
}
