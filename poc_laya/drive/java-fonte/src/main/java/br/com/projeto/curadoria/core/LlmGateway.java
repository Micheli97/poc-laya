package br.com.projeto.curadoria.core;

import java.util.Map;

/**
 * Segunda opiniao via Vercel AI Gateway (ou qualquer servidor no formato OpenAI, como o MockLlm):
 *   POST {url}/v1/chat/completions   (Authorization: Bearer AI_GATEWAY_API_KEY)
 */
public final class LlmGateway extends LlmChat {

    public LlmGateway(String url, String chave, String modelo, int timeoutMs, double precoEntrada, double precoSaida, Taxonomia tx) {
        this(url, chave, modelo, timeoutMs, precoEntrada, precoSaida, tx, new Opcoes());
    }

    public LlmGateway(String url, String chave, String modelo, int timeoutMs, double precoEntrada, double precoSaida, Taxonomia tx, Opcoes opcoes) {
        super(url, "/v1/chat/completions", chave, modelo, timeoutMs, precoEntrada, precoSaida, tx, opcoes);
    }

    @Override public String descricao() { return "gateway:" + modelo; }

    @Override
    protected String corpoJson(String sistema, String usuario) {
        return "{\"model\":" + Json.str(modelo) + ",\"temperature\":0,\"max_tokens\":80,\"response_format\":{\"type\":\"json_object\"},\"messages\":["
                + "{\"role\":\"system\",\"content\":" + Json.str(sistema) + "},"
                + "{\"role\":\"user\",\"content\":" + Json.str(usuario) + "}]}";
    }

    @Override
    protected Extraido extrair(Map<String, Object> resposta) {
        Object choices = resposta.get("choices");
        if (!(choices instanceof java.util.List<?> l) || l.isEmpty()) throw new IllegalArgumentException("resposta sem choices[0].message.content");
        Object c = Json.mapa(Json.mapa(l.get(0)).get("message")).get("content");
        Map<String, Object> usage = Json.mapa(resposta.get("usage"));
        if (!(c instanceof String s)) {
            // mesmo sem conteudo os tokens gastos contam
            throw new IllegalArgumentException("resposta sem choices[0].message.content");
        }
        return new Extraido(s, numero(usage, "prompt_tokens", "input_tokens"), numero(usage, "completion_tokens", "output_tokens"),
                usage.get("cost") instanceof Number n ? n.doubleValue() : null);
    }
}
