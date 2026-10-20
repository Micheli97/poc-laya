package br.com.projeto.curadoria.core;

import java.util.List;
import java.util.Map;

/**
 * Tipos do dominio. Sao records simples: o Spring/Jackson os serializa direto como JSON (camelCase).
 */
public final class Modelos {
    private Modelos() {}

    /** Produto como chega do Jitterbit (vindo de seller, ERP ou da propria VTEX). */
    public record ProdutoEntrada(String id, String sku, String titulo, String descricao, String categoriaVendedor,
                                 String tags, String marcaInformada, Map<String, String> atributos,
                                 List<String> bullets, String modelo, String ean, String preco, String condicao,
                                 Map<String, String> vendedor) {
        /** Forma anterior (catalogo v1): sem bullets, modelo, EAN, preco, condicao nem vendedor. Os campos novos sao opcionais no JSON. */
        public ProdutoEntrada(String id, String sku, String titulo, String descricao, String categoriaVendedor,
                              String tags, String marcaInformada, Map<String, String> atributos) {
            this(id, sku, titulo, descricao, categoriaVendedor, tags, marcaInformada, atributos, null, null, null, null, null, null);
        }
    }

    /**
     * O que o agente decidiu sobre o produto.
     *
     * @param atributos ficha tecnica da taxonomia v2 (chave -> valor canonico, "nao_informado" ou "nao_se_aplica"); nulo na v1
     */
    public record Classificacao(String departamento, double confDepartamento, String categoria, double confCategoria,
                                String tipoItem, double confTipo, String voltagem, String cor, String marca,
                                Map<String, String> atributos) {
        /** Forma anterior (catalogo v1): voltagem e cor sao campos proprios e nao ha ficha tecnica. */
        public Classificacao(String departamento, double confDepartamento, String categoria, double confCategoria,
                             String tipoItem, double confTipo, String voltagem, String cor, String marca) {
            this(departamento, confDepartamento, categoria, confCategoria, tipoItem, confTipo, voltagem, cor, marca, null);
        }
    }

    public enum Status {
        /** confianca acima do limiar: pode ser gravado na VTEX */
        APROVADO,
        /** confianca baixa: foi para a fila de revisao humana */
        REVISAO,
        /** o modelo devolveu algo incoerente (categoria fora do departamento) */
        ERRO_VALIDACAO,
        /** revisor confirmou a sugestao do agente */
        CONFIRMADO,
        /** revisor corrigiu a sugestao do agente */
        CORRIGIDO,
        /** MODO AUTONOMO: decisao tomada por sinais independentes e aplicada sem revisao humana (ver mudouCategoria) */
        APLICADO,
        /** MODO AUTONOMO: na duvida o agente nao mexe; a categoria atual do vendedor fica e o produto aguarda reprocessamento (ver motivoMantido) */
        MANTIDO
    }

    /**
     * @param motivoMantido     so no modo autonomo e com status MANTIDO: falha-sinal:<sinal>, veto:<sinal>, discordancia, confianca-baixa,
     *                          sinais-insuficientes, so-vendedor, erro-validacao, modelo-indisponivel, fora-do-canario ou rollback
     * @param mudouCategoria    so no modo autonomo e com status APLICADO: true = a categoria curada e diferente da do vendedor
     *                          (o Jitterbit so grava na VTEX quando e true)
     * @param votos             o que cada sinal votou (sinal, categoria, confianca); vazio quando nenhum votou
     * @param versaoAgente      versao dos sinais usada na decisao (agente + modelo supervisionado + taxonomia + configuracao)
     * @param categoriaAnterior categoria que o produto tinha antes desta decisao (a do vendedor, ou a ultima curada)
     * @param categoriaProposta o que a politica queria aplicar, mesmo quando o canario ou um veto impediu
     */
    public record ResultadoCuradoria(String id, Status status, Classificacao classificacao, String categoriaVendedor,
                                     String tituloLimpo, String motivo, long latenciaMs, String modelo, boolean cache,
                                     String categoriaSugeridaVendedor, String regraDecisao,
                                     String categoriaSugeridaLlm, String categoriaSugeridaLaya,
                                     String motivoMantido, Boolean mudouCategoria, List<Voto> votos,
                                     String versaoAgente, String categoriaAnterior, String categoriaProposta) {
        /** Forma da rodada 3 (sem os campos do modo autonomo). */
        public ResultadoCuradoria(String id, Status status, Classificacao classificacao, String categoriaVendedor,
                                  String tituloLimpo, String motivo, long latenciaMs, String modelo, boolean cache,
                                  String categoriaSugeridaVendedor, String regraDecisao,
                                  String categoriaSugeridaLlm, String categoriaSugeridaLaya) {
            this(id, status, classificacao, categoriaVendedor, tituloLimpo, motivo, latenciaMs, modelo, cache,
                    categoriaSugeridaVendedor, regraDecisao, categoriaSugeridaLlm, categoriaSugeridaLaya, null, null, List.of(), null, null, null);
        }
    }

    public record LoteRequest(String loteId, List<ProdutoEntrada> produtos) {}

    public record LoteResposta(String loteId, int total, int aprovados, int revisao, int errosValidacao, int falhas,
                               long duracaoMs, List<ResultadoCuradoria> resultados, List<String> erros, int aplicados, int mantidos) {
        /** Forma da rodada 3 (sem os contadores do modo autonomo). */
        public LoteResposta(String loteId, int total, int aprovados, int revisao, int errosValidacao, int falhas,
                            long duracaoMs, List<ResultadoCuradoria> resultados, List<String> erros) {
            this(loteId, total, aprovados, revisao, errosValidacao, falhas, duracaoMs, resultados, erros, 0, 0);
        }
    }

    /** Itens novos: um resultado por produto (invalido, duplicado, fora da taxonomia ou classificado) e o uso das estrategias. */
    public record NovosResposta(String loteId, int total, long duracaoMs, List<NovosItens.Resultado> resultados, NovosItens.Uso uso) {}

    /** Busca v2: a consulta e a estrategia de entendimento (sinonimos, laya, llm ou hibrida; vazia = a configurada). */
    public record BuscaRequest(String q, String estrategia) {}

    public record ConsultaRequest(String q) {}

    /** @param origem como a busca foi entendida: sinonimo, afinidade ou modelo */
    public record ConsultaResposta(String q, String departamento, String categoria, String tipoItem, double confianca, String origem) {}

    public record ItemRevisao(String id, ProdutoEntrada produto, ResultadoCuradoria sugestao, String criadoEm) {}

    public record DecisaoRevisao(String categoria, String tipoItem, String voltagem, String cor, String revisor) {}

    public record EventoAuditoria(String quando, String evento, String detalhe) {}

    // ---- modo autonomo: rollback, reprocessamento, monitor e avaliacao

    /** Pelo menos um filtro e obrigatorio (para nao desfazer tudo sem querer). */
    public record RollbackRequest(String categoria, String departamento, String versao, String motivo) {}

    public record RollbackItem(String id, String categoriaAnterior, String categoriaDesfeita) {}

    /** A lista que o Jitterbit usa para reverter na VTEX: categoriaAnterior volta a valer. */
    public record RollbackResposta(int total, List<RollbackItem> itens) {}

    public record ReprocessamentoResposta(int candidatos, int reprocessados, int aplicados, int mantidos, int ignoradosSemMudanca,
                                          int ignoradosPorLimiteDeTentativas, String versaoSinais) {}

    /** tipo: "linha-de-base" ou "atual" */
    public record SinaisBuscaRequest(String tipo, List<MonitorQualidade.Metrica> categorias) {}

    public record SinaisBuscaResposta(String tipo, int categorias, List<MonitorQualidade.Acao> acoes) {}

    public record VarreduraRequest(List<ProdutoEntrada> produtos, List<PoliticaDecisao.Parametros> grade) {}

    public record DecisaoVarrida(boolean aplicar, String categoria, String motivo, double confiancaMedia) {}

    public record VarreduraItem(String id, String categoriaVendedor, List<Voto> votos, List<String> semVoto, List<String> falhas, List<DecisaoVarrida> decisoes) {}

    public record VarreduraResposta(String versaoDosVotos, int produtos, int votosReaproveitados, List<VarreduraItem> itens) {}

    public record Metricas(long processados, long aprovados, long revisao, long errosValidacao, long confirmados,
                           long corrigidos, long naFila, long cacheHits, long falhas, long latenciaP50Ms,
                           long latenciaP95Ms, double taxaAprovacao, String classificador, double limiar, String melhorias,
                           long chamadasLlm, long falhasLlm, long tokensEntradaLlm, long tokensSaidaLlm, double custoLlm, String ultimoErroLlm,
                           boolean modoAutonomo, boolean votacaoAtiva, long aplicados, long mudaram, long mantidos, long mantidosPorFalha,
                           Map<String, Long> mantidosPorMotivo, long reprocessamentos, long rollbacks, String versaoSinais,
                           String supervisionadoVersao, Double supervisionadoEceAntes, Double supervisionadoEceDepois,
                           String provedorLlm, long tentativas429Llm,
                           Double layaTemperatura, Double layaEceAntes, Double layaEceDepois, Integer layaCalibracaoAmostras) {}
}
