package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;
import br.com.projeto.curadoria.core.Modelos.ResultadoCuradoria;
import br.com.projeto.curadoria.core.Modelos.Status;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Itens novos (cadastro de hoje): para cada um, em ordem,
 * <ol>
 *   <li><b>invalido?</b> regras de codigo ({@link ValidadorDeCadastro}); se for, para aqui;</li>
 *   <li><b>duplicado?</b> EAN igual a um produto do catalogo, ou codigo de modelo + titulo/marca (com desempate opcional) ({@link IndiceDeDuplicados});</li>
 *   <li><b>classifica</b> pelo agente (votacao de sinais), como qualquer produto;</li>
 *   <li><b>fora da taxonomia?</b> pela estrategia configurada: confianca baixa do supervisionado, "nenhuma destas" do LLM, as duas juntas
 *       ou "nenhuma destas" do Laya ajustado. Se estiver fora, a subcategoria fica vazia (nao se forca uma categoria errada).</li>
 * </ol>
 * Um duplicado ou um item fora da taxonomia continua com o resultado da classificacao no retorno, para quem for revisar.
 */
public final class NovosItens {

    public static final String SEM_ESTRATEGIA = "nenhuma";

    /** @param status INVALIDO, FORA_DA_TAXONOMIA, DUPLICADO ou o status da classificacao (APLICADO, MANTIDO) */
    public record Resultado(String id, String status, boolean invalido, String motivoInvalido, String duplicadoDe, String motivoDuplicado, boolean foraDaTaxonomia,
                            String motivoFora, String subcategoria, ResultadoCuradoria classificacao) {}

    /** Configuracao: estrategias e limiares (ajustados nos novos que nao estao em novos_teste). */
    public record Config(String fora, double foraConfianca, double foraPreFiltro, int threads) {
        public Config { fora = fora == null ? SEM_ESTRATEGIA : fora.trim(); }
    }

    private final AgenteCuradoria agente;
    private final ValidadorDeCadastro validador;
    private final IndiceDeDuplicados indice;      // nulo = sem deteccao de duplicados
    private final Desempate desempate;
    private final SegundaOpiniao llm;              // para o "nenhuma destas" do LLM
    private final Config cfg;
    private final ExecutorService pool;

    private final AtomicLong total = new AtomicLong(), invalidos = new AtomicLong(), duplicados = new AtomicLong(), fora = new AtomicLong(), desempates = new AtomicLong(),
            desempatesRejeitaram = new AtomicLong(), consultasForaLlm = new AtomicLong();

    public NovosItens(AgenteCuradoria agente, ValidadorDeCadastro validador, IndiceDeDuplicados indice, Desempate desempate, SegundaOpiniao llm, Config cfg) {
        this.agente = agente; this.validador = validador; this.indice = indice; this.desempate = desempate == null ? Desempate.NENHUM : desempate;
        this.llm = llm == null ? SegundaOpiniao.DESLIGADA : llm; this.cfg = cfg;
        if (("llm".equals(cfg.fora()) || "combinada".equals(cfg.fora())) && !this.llm.ativa())
            throw new IllegalStateException("curadoria.novos.fora=" + cfg.fora() + " exige curadoria.segunda-opiniao.ativa=true");
        this.pool = Executors.newFixedThreadPool(Math.max(1, cfg.threads()));
    }

    public List<Resultado> tratar(List<ProdutoEntrada> produtos) {
        List<Future<Resultado>> fs = produtos.stream().map(p -> pool.submit(() -> tratar(p))).toList();
        List<Resultado> out = new ArrayList<>();
        for (Future<Resultado> f : fs) {
            try { out.add(f.get()); }
            catch (ExecutionException e) { throw new IllegalStateException("falha ao tratar item novo: " + e.getCause(), e.getCause()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrompido"); }
        }
        return out;
    }

    public Resultado tratar(ProdutoEntrada p) {
        total.incrementAndGet();
        var v = validador.validar(p);
        if (v.invalido()) {
            invalidos.incrementAndGet();
            return new Resultado(p.id(), "INVALIDO", true, v.motivo(), null, null, false, null, null, null);
        }
        String dup = null, motivoDup = null;
        if (indice != null) {
            var d = indice.decidir(p);
            if (d.duplicadoDe() != null) {
                dup = d.duplicadoDe(); motivoDup = d.motivo();
                if (d.precisaDesempate() && desempate != Desempate.NENHUM) {
                    desempates.incrementAndGet();
                    Optional<Boolean> mesmo = desempate.mesmoProduto(indice.resumoDoNovo(p), d.candidato());
                    if (mesmo.isPresent() && !mesmo.get()) { dup = null; motivoDup = null; desempatesRejeitaram.incrementAndGet(); }
                    else motivoDup = d.motivo() + (mesmo.isPresent() ? "+" + desempate.nome() : "");
                }
            }
        }
        ResultadoCuradoria cl = agente.classificar(p);
        String motivoFora = detectarFora(p, cl);
        if (dup != null) duplicados.incrementAndGet();
        if (motivoFora != null) {
            fora.incrementAndGet();
            return new Resultado(p.id(), "FORA_DA_TAXONOMIA", false, null, dup, motivoDup, true, motivoFora, null, cl);
        }
        String sub = cl.status() == Status.APLICADO && cl.classificacao() != null ? cl.classificacao().categoria() : null;
        return new Resultado(p.id(), dup != null ? "DUPLICADO" : cl.status().name(), false, null, dup, motivoDup, false, null, sub, cl);
    }

    /** Motivo (texto) se o item parece fora da taxonomia, ou nulo. */
    private String detectarFora(ProdutoEntrada p, ResultadoCuradoria cl) {
        Double nb = confiancaDoSupervisionado(cl);
        switch (cfg.fora()) {
            case "baixa-confianca":
                return nb != null && nb < cfg.foraConfianca() ? String.format(Locale.ROOT, "confianca-baixa(%.2f)", nb) : null;
            case "llm":
                return nb != null && nb >= cfg.foraPreFiltro() ? null : perguntarAoLlm(p, cl);
            case "combinada": {
                if (nb != null && nb < cfg.foraConfianca()) return String.format(Locale.ROOT, "confianca-baixa(%.2f)", nb);
                return nb != null && nb >= cfg.foraPreFiltro() ? null : perguntarAoLlm(p, cl);
            }
            case "laya": {
                Boolean nenhuma = agente.layaDisseNenhumaDestas(p);
                return Boolean.TRUE.equals(nenhuma) ? "laya-nenhuma-destas" : null;
            }
            default:
                return null;
        }
    }

    private String perguntarAoLlm(ProdutoEntrada p, ResultadoCuradoria cl) {
        consultasForaLlm.incrementAndGet();
        Optional<SegundaOpiniao.Opiniao> op = llm.consultarPermitindoNenhuma(new SegundaOpiniao.Pedido(p, cl.tituloLimpo(), null, null, false, false));
        return op.isPresent() && Taxonomia.NENHUMA_DESTAS.equals(op.get().categoria()) ? "llm-nenhuma-destas" : null;
    }

    private static Double confiancaDoSupervisionado(ResultadoCuradoria cl) {
        if (cl.votos() == null) return null;
        for (Voto v : cl.votos()) if (Sinais.SUPERVISIONADO.equals(v.sinal())) return v.confianca();
        return null;
    }

    /** Contadores para as metricas e para o custo. */
    public record Uso(long itens, long invalidos, long duplicados, long foraDaTaxonomia, long desempates, long desempatesQueRejeitaram, long consultasForaLlm) {}
    public Uso uso() { return new Uso(total.get(), invalidos.get(), duplicados.get(), fora.get(), desempates.get(), desempatesRejeitaram.get(), consultasForaLlm.get()); }

    public String descricao() {
        return "novos(fora=" + cfg.fora() + (indice == null ? ", sem-duplicados" : ", duplicados[" + indice.tamanho() + "], desempate=" + desempate.nome()) + ")";
    }
}
