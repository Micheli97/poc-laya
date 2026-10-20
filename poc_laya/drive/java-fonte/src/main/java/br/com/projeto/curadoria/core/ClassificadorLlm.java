package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Taxonomia.Pergunta;

import java.util.Map;

/**
 * curadoria.classificador=llm: o LLM e o classificador PRINCIPAL, no lugar do Laya.
 *
 * Diferente do Laya, o LLM nao responde a perguntas em cascata (departamento, depois categoria): ele recebe as 58 categorias
 * numa unica chamada e devolve a categoria. Quem faz essa chamada e o sinal {@link Sinais.SinalLlm}, que entra na votacao junto
 * com o vendedor e o supervisionado. Por isso este objeto so identifica o classificador (nome nas metricas) e recusa ser usado
 * como cascata: se alguem tentar, o erro e claro em vez de uma resposta inventada.
 *
 * Exige curadoria.votacao.ativa=true e curadoria.segunda-opiniao.ativa=true.
 */
public final class ClassificadorLlm implements Classificador {

    private final SegundaOpiniao llm;

    public ClassificadorLlm(SegundaOpiniao llm) { this.llm = llm; }

    @Override
    public Chamada decidir(String state, Map<String, Pergunta> perguntas, String requestId) {
        throw new UnsupportedOperationException("classificador=llm decide por votacao (SinalLlm), nao pela cascata de perguntas");
    }

    @Override
    public String nome() { return "llm:" + (llm.ativa() ? llm.descricao() : "desligado"); }
}
