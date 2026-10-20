package br.com.projeto.curadoria.core;

import java.util.ArrayDeque;

/**
 * Limite de chamadas por janela de tempo (janela deslizante). Usado para nao estourar o limite do plano do provedor do LLM.
 * Quando o limite e atingido, quem chama espera ate abrir vaga (ou desiste se a espera passar do maximo).
 */
public final class LimitadorDeChamadas {

    private final int maximo;
    private final long janelaMs;
    private final ArrayDeque<Long> instantes = new ArrayDeque<>(); // em nanos, do mais antigo para o mais novo

    /** @param maximo chamadas por janela; zero ou negativo = sem limite */
    public LimitadorDeChamadas(int maximo, long janelaMs) {
        this.maximo = maximo;
        this.janelaMs = janelaMs;
    }

    public static LimitadorDeChamadas porMinuto(int maximo) { return new LimitadorDeChamadas(maximo, 60_000); }

    /** Bloqueia ate haver vaga. Devolve false se a espera necessaria passaria de {@code maxEsperaMs} (ou se a thread foi interrompida). */
    public boolean adquirir(long maxEsperaMs) {
        if (maximo <= 0) return true;
        long limiteNanos = System.nanoTime() + maxEsperaMs * 1_000_000L;
        while (true) {
            long esperarMs;
            synchronized (this) {
                long agora = System.nanoTime();
                while (!instantes.isEmpty() && agora - instantes.peekFirst() >= janelaMs * 1_000_000L) instantes.pollFirst();
                if (instantes.size() < maximo) { instantes.addLast(agora); return true; }
                esperarMs = (janelaMs * 1_000_000L - (agora - instantes.peekFirst())) / 1_000_000L + 1;
                if (agora + esperarMs * 1_000_000L > limiteNanos) return false;
            }
            try { Thread.sleep(esperarMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
    }
}
