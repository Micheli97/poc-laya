package br.com.projeto.curadoria.core;

/**
 * Chaves para ligar e desligar cada melhoria de assertividade, uma a uma, para medir o efeito de cada.
 * Tudo desligado reproduz o comportamento original do agente.
 *
 * @param acordoVendedor        usa a categoria do vendedor como segundo sinal na decisao
 * @param tipoPorCategoria      o tipo do item vem do tipo_padrao da categoria (sai da pergunta ao modelo)
 * @param atributosPorCodigo    voltagem e cor por regra de codigo (saem da pergunta ao modelo)
 * @param top2Departamentos     quando o departamento e incerto, a 2a chamada oferece as categorias dos 2 mais provaveis
 * @param consultaPorAfinidade  a busca resolve por sinonimo e afinidade aprendida antes de chamar o modelo
 * @param vendedorNoState       false: a linha "Categoria do vendedor" NAO vai no texto enviado ao modelo (a regra do acordo
 *                              continua usando a categoria do vendedor; so o modelo deixa de ver essa pista)
 * @param buscaFallbackTexto    a busca nunca chama o modelo: sem sinonimo nem afinidade, devolve origem "texto" e categoria nula,
 *                              e quem busca usa o casamento de palavras de sempre
 * @param limiarTop2            confianca do departamento abaixo da qual o 2o departamento tambem e considerado
 * @param idiomaInstrucoes      "pt" ou "en": idioma das instrucoes e criterios enviados ao modelo
 */
public record Melhorias(boolean acordoVendedor, boolean tipoPorCategoria, boolean atributosPorCodigo,
                        boolean top2Departamentos, boolean consultaPorAfinidade, boolean vendedorNoState, boolean buscaFallbackTexto,
                        double limiarTop2, String idiomaInstrucoes) {

    public static final int MAX_OPCOES_CATEGORIA = 20;

    /** Atalho para as 5 melhorias da rodada 2: o vendedor continua no texto e a busca ainda pode chamar o modelo. */
    public Melhorias(boolean acordoVendedor, boolean tipoPorCategoria, boolean atributosPorCodigo,
                     boolean top2Departamentos, boolean consultaPorAfinidade, double limiarTop2, String idiomaInstrucoes) {
        this(acordoVendedor, tipoPorCategoria, atributosPorCodigo, top2Departamentos, consultaPorAfinidade, true, false, limiarTop2, idiomaInstrucoes);
    }

    public Melhorias {
        idiomaInstrucoes = "en".equalsIgnoreCase(idiomaInstrucoes) ? "en" : "pt";
    }

    public static Melhorias nenhuma() {
        return new Melhorias(false, false, false, false, false, true, false, 0.7, "pt");
    }

    public boolean ingles() { return "en".equals(idiomaInstrucoes); }

    /** Resumo legivel para as metricas e para o relatorio. */
    public String resumo() {
        StringBuilder sb = new StringBuilder();
        if (acordoVendedor) sb.append("acordo-vendedor ");
        if (tipoPorCategoria) sb.append("tipo-por-categoria ");
        if (atributosPorCodigo) sb.append("atributos-por-codigo ");
        if (top2Departamentos) sb.append("top2(").append(limiarTop2).append(") ");
        if (consultaPorAfinidade) sb.append("consulta-por-afinidade ");
        if (!vendedorNoState) sb.append("sem-vendedor-no-state ");
        if (buscaFallbackTexto) sb.append("busca-fallback-texto ");
        if (ingles()) sb.append("instrucoes-en ");
        return sb.isEmpty() ? "nenhuma" : sb.toString().trim();
    }
}
