package br.com.projeto.curadoria.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A votacao entre os dois sinais baratos (vendedor e supervisionado), sem Laya nem LLM, com a mesma {@link PoliticaDecisao} do agente.
 * Serve para curar um catalogo inteiro sem chamar nenhum modelo externo (ver simulacao/CurarCatalogoV2).
 */
public final class VotacaoBarata {

    public record Decisao(boolean aplicar, String categoria, String motivo) {}

    private final PoliticaDecisao politica;

    public VotacaoBarata(PoliticaDecisao.Parametros parametros) { this.politica = new PoliticaDecisao(parametros); }

    /** @param categoriaDoVendedor nula = o vendedor nao votou; @param confVendedor o peso do voto do vendedor (padrao do agente: 0,7) */
    public Decisao decidir(String categoriaDoVendedor, double confVendedor, String categoriaDoNb, double confNb) {
        List<ResultadoSinal> rs = new ArrayList<>();
        rs.add(categoriaDoVendedor == null ? ResultadoSinal.semVoto(Sinais.VENDEDOR, "vendedor-sem-mapeamento") : ResultadoSinal.voto(Sinais.VENDEDOR, categoriaDoVendedor, confVendedor, false));
        rs.add(ResultadoSinal.voto(Sinais.SUPERVISIONADO, categoriaDoNb, confNb, true));
        var d = politica.decidir(rs);
        return new Decisao(d.aplicar(), d.categoria(), d.motivo());
    }
}
