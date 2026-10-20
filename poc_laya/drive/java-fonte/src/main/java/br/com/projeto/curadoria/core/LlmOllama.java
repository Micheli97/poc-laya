package br.com.projeto.curadoria.core;

import java.util.Map;

/**
 * Segunda opiniao via Ollama (Cloud ou local), pelo endpoint NATIVO:
 *   POST {url}/api/chat   (Authorization: Bearer OLLAMA_API_KEY; sem chave no Ollama local)
 *   corpo: model, messages, stream:false, format:"json", options:{temperature:0} e, se configurado, think
 *   resposta: message.content; tokens em prompt_eval_count (entrada) e eval_count (saida)
 *
 * O Ollama nao informa custo: vale o preco por milhao de tokens da configuracao (padrao 0 no plano gratis).
 *
 * think: no gpt-oss aceita so "low", "medium" ou "high" (nao da para desligar; o padrao do Ollama e "medium").
 * "low" gasta menos tokens de saida. Modelos que nao tem raciocinio recusam o parametro (HTTP 400): deixe vazio neles.
 * eval_count inclui os tokens de raciocinio.
 */
public final class LlmOllama extends LlmChat {

    public LlmOllama(String url, String chave, String modelo, int timeoutMs, double precoEntrada, double precoSaida, Taxonomia tx, Opcoes opcoes) {
        super(url, "/api/chat", chave, modelo, timeoutMs, precoEntrada, precoSaida, tx, opcoes);
    }

    @Override public String descricao() { return "ollama:" + modelo + (opcoes.think.isEmpty() ? "" : " think=" + opcoes.think); }

    @Override
    protected String corpoJson(String sistema, String usuario) {
        return "{\"model\":" + Json.str(modelo) + ",\"stream\":false,\"format\":\"json\",\"options\":{\"temperature\":0}"
                + (opcoes.think.isEmpty() ? "" : ",\"think\":" + Json.str(opcoes.think))
                + ",\"messages\":[{\"role\":\"system\",\"content\":" + Json.str(sistema) + "},{\"role\":\"user\",\"content\":" + Json.str(usuario) + "}]}";
    }

    @Override
    protected Extraido extrair(Map<String, Object> resposta) {
        Object c = Json.mapa(resposta.get("message")).get("content");
        if (!(c instanceof String s)) throw new IllegalArgumentException("resposta sem message.content");
        return new Extraido(s, numero(resposta, "prompt_eval_count"), numero(resposta, "eval_count"), null);
    }
}
