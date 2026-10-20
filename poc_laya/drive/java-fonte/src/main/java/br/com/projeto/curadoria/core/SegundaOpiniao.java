package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Segunda opiniao para os produtos em disputa (um LLM maior olha so os casos dificeis, ou todos, no modo LLM principal).
 *
 * Contrato: {@link #consultar} NUNCA lanca excecao e nunca trava o fluxo. Qualquer problema (timeout, erro HTTP,
 * JSON invalido, categoria que nao existe na taxonomia) devolve vazio e o produto segue a regra de falha:
 * revisao humana no modo antigo, MANTIDO no modo autonomo.
 */
public interface SegundaOpiniao {

    /**
     * O que o LLM recebe. As categorias sao ids da taxonomia; nulas quando nao ha sugestao valida/sinal.
     *
     * @param incluirSugestoes          false: o prompt NAO traz as sugestoes do Laya e do vendedor (voto independente na votacao)
     * @param incluirCategoriaDoVendedor false: a linha "Categoria do vendedor" tambem sai do bloco do produto
     */
    record Pedido(ProdutoEntrada produto, String tituloLimpo, String categoriaLaya, String categoriaVendedor,
                  boolean incluirSugestoes, boolean incluirCategoriaDoVendedor) {
        /** Forma da rodada 3: com sugestoes e com a categoria do vendedor no texto. */
        public Pedido(ProdutoEntrada produto, String tituloLimpo, String categoriaLaya, String categoriaVendedor) {
            this(produto, tituloLimpo, categoriaLaya, categoriaVendedor, true, true);
        }
    }

    /** @param categoria id existente na taxonomia (ja validado); @param confianca alta, media ou baixa */
    record Opiniao(String categoria, String confianca) {}

    /** Contadores de uso e custo, expostos em /curadoria/metricas. */
    final class Uso {
        public final AtomicLong chamadas = new AtomicLong(), falhas = new AtomicLong(), tokensEntrada = new AtomicLong(), tokensSaida = new AtomicLong(),
                tentativas429 = new AtomicLong();
        public final DoubleAdder custo = new DoubleAdder();
        /** Ultimo motivo de falha (sem dados do produto e nunca com a chave). Util para ver "HTTP 402" sem abrir o log. */
        public volatile String ultimoErro;
    }

    boolean ativa();

    Optional<Opiniao> consultar(Pedido pedido);

    Uso uso();

    /** v2: como {@link #consultar}, mas o LLM pode dizer que o produto nao esta na taxonomia: a categoria da opiniao e entao {@code Taxonomia.NENHUMA_DESTAS}. */
    default Optional<Opiniao> consultarPermitindoNenhuma(Pedido pedido) { return consultar(pedido); }

    /**
     * Chamada generica ao LLM: devolve o JSON da resposta (so o trecho entre a 1a { e a ultima }), ou vazio se falhou (a falha fica em {@link Uso}).
     * Nunca lanca excecao. Usada pela ficha tecnica; o padrao (provedor sem suporte, ou desligado) e vazio.
     */
    default Optional<String> completarJson(String sistema, String usuario, String idRequisicao) { return Optional.empty(); }

    /** Descricao curta do provedor para as metricas, sem chave (ex.: "ollama:gpt-oss:120b"). */
    default String descricao() { return "llm"; }

    /** Implementacao usada quando a segunda opiniao esta desligada. */
    SegundaOpiniao DESLIGADA = new SegundaOpiniao() {
        private final Uso uso = new Uso();
        public boolean ativa() { return false; }
        public Optional<Opiniao> consultar(Pedido pedido) { return Optional.empty(); }
        public Uso uso() { return uso; }
    };
}
