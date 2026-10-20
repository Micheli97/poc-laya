package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

/**
 * Um sinal independente que vota na categoria de um produto. A decisao final sai da votacao de varios sinais
 * ({@link PoliticaDecisao}), nunca de um so. Implementacoes: vendedor, Laya, LLM e classificador supervisionado.
 *
 * Contrato: {@link #votar} nunca lanca excecao; qualquer problema vira {@link ResultadoSinal.Falha}.
 */
public interface Sinal {

    /** O produto ja preparado (texto limpo para o modelo) e a categoria do vendedor mapeada na taxonomia (nula = sem sinal). */
    record Contexto(ProdutoEntrada produto, Preparador.Preparado preparado, String categoriaVendedor) {}

    String nome();

    /** true para modelos (Laya, LLM, supervisionado); false para o vendedor. */
    boolean ehModelo();

    ResultadoSinal votar(Contexto contexto);
}
