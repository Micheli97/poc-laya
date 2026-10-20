package br.com.projeto.curadoria.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Vigia a qualidade da busca por categoria depois que o agente aplicou mudancas. Recebe metricas por categoria
 * (cliques por busca, conversao, buscas sem resultado), compara com uma linha de base e, se uma categoria piorar mais que
 * {@code quedaMaxima}, pode disparar o rollback daquela categoria.
 *
 * Toda acao vai para a lista de acoes (e o agente a leva para a auditoria). A linha de base e enviada uma vez
 * (tipo "linha-de-base") e as medicoes seguintes (tipo "atual") sao comparadas com ela.
 */
public final class MonitorQualidade {

    /** Metricas de busca de uma categoria. Cliques e conversao: quanto maior melhor. Buscas sem resultado: quanto menor melhor. */
    public record Metrica(String categoria, double cliquesPorBusca, double conversao, double buscasSemResultado) {}

    /** O que o monitor viu e fez. variacao > 0 = piora relativa (queda de cliques ou conversao, alta de buscas sem resultado). */
    public record Acao(String categoria, String metrica, double base, double atual, double variacao, boolean rollbackDisparado, int produtosDesfeitos) {}

    private final double quedaMaxima;
    private final boolean rollbackAutomatico;
    private final Map<String, Metrica> linhaDeBase = new LinkedHashMap<>();

    public MonitorQualidade(double quedaMaxima, boolean rollbackAutomatico) {
        this.quedaMaxima = quedaMaxima;
        this.rollbackAutomatico = rollbackAutomatico;
    }

    public synchronized void definirLinhaDeBase(List<Metrica> metricas) {
        linhaDeBase.clear();
        for (Metrica m : metricas) linhaDeBase.put(m.categoria(), m);
    }

    public synchronized boolean temLinhaDeBase() { return !linhaDeBase.isEmpty(); }

    /**
     * @param rollbackDaCategoria desfaz as mudancas da categoria e devolve quantos produtos foram desfeitos
     * @return as categorias que pioraram alem do limite (uma acao por metrica que estourou)
     */
    public synchronized List<Acao> avaliar(List<Metrica> atuais, Function<String, Integer> rollbackDaCategoria) {
        if (linhaDeBase.isEmpty()) throw new IllegalStateException("envie primeiro a linha de base (tipo linha-de-base)");
        List<Acao> acoes = new ArrayList<>();
        for (Metrica atual : atuais) {
            Metrica base = linhaDeBase.get(atual.categoria());
            if (base == null) continue; // categoria sem linha de base: nao ha com o que comparar
            List<Acao> estouros = new ArrayList<>();
            double queda = quedaRelativa(base.cliquesPorBusca(), atual.cliquesPorBusca());
            if (queda > quedaMaxima) estouros.add(new Acao(atual.categoria(), "cliquesPorBusca", base.cliquesPorBusca(), atual.cliquesPorBusca(), queda, false, 0));
            queda = quedaRelativa(base.conversao(), atual.conversao());
            if (queda > quedaMaxima) estouros.add(new Acao(atual.categoria(), "conversao", base.conversao(), atual.conversao(), queda, false, 0));
            double alta = base.buscasSemResultado() > 0 ? (atual.buscasSemResultado() - base.buscasSemResultado()) / base.buscasSemResultado() : 0;
            if (alta > quedaMaxima) estouros.add(new Acao(atual.categoria(), "buscasSemResultado", base.buscasSemResultado(), atual.buscasSemResultado(), alta, false, 0));
            if (estouros.isEmpty()) continue;
            int desfeitos = rollbackAutomatico ? rollbackDaCategoria.apply(atual.categoria()) : 0; // uma vez por categoria, mesmo com varias metricas
            for (int i = 0; i < estouros.size(); i++) {
                Acao a = estouros.get(i);
                acoes.add(new Acao(a.categoria(), a.metrica(), a.base(), a.atual(), a.variacao(), rollbackAutomatico, i == 0 ? desfeitos : 0));
            }
        }
        return acoes;
    }

    private static double quedaRelativa(double base, double atual) { return base > 0 ? (base - atual) / base : 0; }
}
