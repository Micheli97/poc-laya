package br.com.projeto.curadoria.core;

import java.util.Set;

/**
 * Configuracao do modo autonomo: nenhum produto depende de aprovacao humana. Na duvida o agente nao mexe (MANTIDO).
 *
 * @param modoAutonomo         status APLICADO/MANTIDO no lugar de APROVADO/REVISAO; nunca devolve REVISAO
 * @param votacaoAtiva         a decisao sai da votacao de sinais independentes ({@link PoliticaDecisao}) em vez da regra em cadeia
 * @param politica             parametros da votacao
 * @param confiancaVendedor    peso do voto do vendedor (padrao 0.7, perto do acerto historico do vendedor)
 * @param llmPrincipal         curadoria.classificador=llm: o LLM, com as 58 categorias numa chamada, e o classificador principal (sem Laya);
 *                             com false, o LLM so e consultado quando os sinais baratos nao decidem
 * @param llmVeCategoriaDoVendedor se o prompt do LLM mostra a categoria do vendedor (false = voto totalmente independente)
 * @param supervisionadoAtivo  inclui o classificador supervisionado como sinal
 * @param canario              departamentos liberados para aplicar; vazio = todos. Fora dele a decisao e calculada mas fica MANTIDO (modo sombra)
 * @param reprocessamentoMaximo tentativas de reprocessar um MANTIDO por falha sem mudanca de versao antes de desistir
 * @param versaoAgente         versao do agente: entra na versao dos sinais e nas decisoes gravadas (o rollback filtra por ela)
 * @param avaliacaoAtiva       habilita /curadoria/politica/varrer (so para avaliacao; desligado em producao)
 * @param usarLlm              false: o LLM nao vota na categoria (a segunda opiniao fica ativa para outros usos, como a ficha tecnica)
 * @param usarLaya             false: o Laya nao vota (nem e chamado). Serve para medir quanto o Laya agrega ao vendedor + supervisionado (+ LLM)
 */
public record Autonomia(boolean modoAutonomo, boolean votacaoAtiva, PoliticaDecisao.Parametros politica, double confiancaVendedor,
                        boolean llmPrincipal, boolean llmVeCategoriaDoVendedor, boolean supervisionadoAtivo, Set<String> canario,
                        int reprocessamentoMaximo, String versaoAgente, boolean avaliacaoAtiva, boolean usarLaya, boolean usarLlm) {

    /** Forma anterior (o Laya e o LLM votam). */
    public Autonomia(boolean modoAutonomo, boolean votacaoAtiva, PoliticaDecisao.Parametros politica, double confiancaVendedor,
                     boolean llmPrincipal, boolean llmVeCategoriaDoVendedor, boolean supervisionadoAtivo, Set<String> canario,
                     int reprocessamentoMaximo, String versaoAgente, boolean avaliacaoAtiva) {
        this(modoAutonomo, votacaoAtiva, politica, confiancaVendedor, llmPrincipal, llmVeCategoriaDoVendedor, supervisionadoAtivo, canario,
                reprocessamentoMaximo, versaoAgente, avaliacaoAtiva, true, true);
    }

    /** Forma da rodada 5 (o LLM vota quando a segunda opiniao esta ativa). */
    public Autonomia(boolean modoAutonomo, boolean votacaoAtiva, PoliticaDecisao.Parametros politica, double confiancaVendedor,
                     boolean llmPrincipal, boolean llmVeCategoriaDoVendedor, boolean supervisionadoAtivo, Set<String> canario,
                     int reprocessamentoMaximo, String versaoAgente, boolean avaliacaoAtiva, boolean usarLaya) {
        this(modoAutonomo, votacaoAtiva, politica, confiancaVendedor, llmPrincipal, llmVeCategoriaDoVendedor, supervisionadoAtivo, canario,
                reprocessamentoMaximo, versaoAgente, avaliacaoAtiva, usarLaya, true);
    }

    /** Tudo desligado: o agente responde exatamente como na rodada 3. */
    public static Autonomia desligada() {
        return new Autonomia(false, false, new PoliticaDecisao.Parametros(2, true, 0.8, 0.7), 0.7, false, true, false, Set.of(), 3, "0.4.0", false, true, true);
    }

    public Autonomia {
        canario = canario == null ? Set.of() : Set.copyOf(canario);
    }

    /** Resumo para o log e para a versao dos sinais. */
    public String resumo() {
        return "autonomo=" + modoAutonomo + " votacao=" + votacaoAtiva + " " + politica.resumo() + " vendedor=" + confiancaVendedor
                + " llmPrincipal=" + llmPrincipal + " llmVeVendedor=" + llmVeCategoriaDoVendedor + " sup=" + supervisionadoAtivo + " laya=" + usarLaya + (usarLlm ? "" : " llm-fora-da-votacao") + " canario=" + new java.util.TreeSet<>(canario);
    }
}
