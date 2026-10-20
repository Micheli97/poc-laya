package br.com.projeto.curadoria.core;

import java.util.function.Function;
import java.util.function.Supplier;

/** Os sinais independentes que votam na categoria: vendedor, Laya, LLM e classificador supervisionado. */
public final class Sinais {
    private Sinais() {}

    public static final String VENDEDOR = "vendedor", LAYA = "laya", LLM = "llm", SUPERVISIONADO = "supervisionado";

    /** Converte a confianca em palavras do LLM (alta, media, baixa) em numero. */
    public static double confiancaDoLlm(String palavra) {
        return switch (palavra == null ? "" : palavra) { case "alta" -> 0.9; case "media" -> 0.7; default -> 0.5; };
    }

    /** A categoria do vendedor mapeada na taxonomia. Sem voto quando o texto e generico, vazio ou ambiguo. NAO e modelo. */
    public static final class SinalVendedor implements Sinal {
        private final double confianca;
        /** @param confianca peso do voto do vendedor; o padrao (0.7) e aproximadamente o acerto historico do vendedor no catalogo */
        public SinalVendedor(double confianca) { this.confianca = confianca; }
        public String nome() { return VENDEDOR; }
        public boolean ehModelo() { return false; }
        public ResultadoSinal votar(Contexto c) {
            return c.categoriaVendedor() == null ? ResultadoSinal.semVoto(VENDEDOR, "vendedor-sem-mapeamento") : ResultadoSinal.voto(VENDEDOR, c.categoriaVendedor(), confianca, false);
        }
    }

    /** O classificador supervisionado (Naive Bayes calibrado). Sem modelo treinado e uma FALHA: o produto fica MANTIDO. */
    public static final class SinalSupervisionado implements Sinal {
        private final Supplier<ClassificadorTexto> modelo;
        private final boolean textoLongo;
        public SinalSupervisionado(Supplier<ClassificadorTexto> modelo) { this(modelo, false); }
        /** @param textoLongo true no catalogo v2: o modelo tambem le os destaques e a descricao do anuncio */
        public SinalSupervisionado(Supplier<ClassificadorTexto> modelo, boolean textoLongo) { this.modelo = modelo; this.textoLongo = textoLongo; }
        public String nome() { return SUPERVISIONADO; }
        public boolean ehModelo() { return true; }
        public ResultadoSinal votar(Contexto c) {
            try {
                ClassificadorTexto m = modelo.get();
                if (m == null) return ResultadoSinal.falha(SUPERVISIONADO, "modelo-nao-treinado");
                var p = m.prever(c.produto().titulo(), c.produto().atributos(), textoLongo ? ClassificadorTexto.textoLongoDe(c.produto()) : null);
                return ResultadoSinal.voto(SUPERVISIONADO, p.categoria(), p.confianca(), true);
            } catch (RuntimeException e) {
                return ResultadoSinal.falha(SUPERVISIONADO, e.getClass().getSimpleName());
            }
        }
    }

    /**
     * O Laya: a cascata de duas chamadas (departamento e categoria) que o agente ja tinha. A cascata e passada de fora
     * porque ela usa a taxonomia, as melhorias e o classificador do agente.
     */
    public static final class SinalLaya implements Sinal {
        private final Function<Contexto, ResultadoSinal> cascata;
        public SinalLaya(Function<Contexto, ResultadoSinal> cascata) { this.cascata = cascata; }
        public String nome() { return LAYA; }
        public boolean ehModelo() { return true; }
        public ResultadoSinal votar(Contexto c) {
            try { return cascata.apply(c); } catch (RuntimeException e) { return ResultadoSinal.falha(LAYA, e.getClass().getSimpleName()); }
        }
    }

    /**
     * O LLM com TODAS as categorias numa chamada, sem as sugestoes do Laya nem do vendedor (voto independente).
     * Sem resposta valida (timeout, HTTP, JSON ruim, id inexistente, 429 persistente) e uma FALHA.
     */
    public static final class SinalLlm implements Sinal {
        private final SegundaOpiniao llm;
        private final boolean verCategoriaDoVendedor;
        /** @param verCategoriaDoVendedor false: o LLM tambem nao ve a categoria do vendedor no texto (independencia total) */
        public SinalLlm(SegundaOpiniao llm, boolean verCategoriaDoVendedor) { this.llm = llm; this.verCategoriaDoVendedor = verCategoriaDoVendedor; }
        public String nome() { return LLM; }
        public boolean ehModelo() { return true; }
        public ResultadoSinal votar(Contexto c) {
            try {
                var r = llm.consultar(new SegundaOpiniao.Pedido(c.produto(), c.preparado().tituloLimpo(), null, null, false, verCategoriaDoVendedor));
                if (r.isEmpty()) return ResultadoSinal.falha(LLM, llm.uso().ultimoErro == null ? "sem-resposta" : llm.uso().ultimoErro);
                return ResultadoSinal.voto(LLM, r.get().categoria(), confiancaDoLlm(r.get().confianca()), true);
            } catch (RuntimeException e) {
                return ResultadoSinal.falha(LLM, e.getClass().getSimpleName());
            }
        }
    }
}
