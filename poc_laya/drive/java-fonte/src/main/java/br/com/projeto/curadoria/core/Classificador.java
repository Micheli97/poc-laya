package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Taxonomia.Pergunta;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Contrato com o modelo de decisao. O resto do agente nao sabe QUAL modelo esta atras:
 * trocar Laya local por Laya/Jev na Vercel (ou regras) e so trocar a implementacao.
 */
public interface Classificador {

    /** @param probabilidades opcao -> probabilidade, quando o modelo devolve (vazio caso contrario) */
    record Resposta(String valor, double confianca, Map<String, Double> probabilidades) {
        public Resposta(String valor, double confianca) { this(valor, confianca, Map.of()); }
    }

    record Chamada(Map<String, Resposta> respostas, long latenciaMs, long tokensEntrada) {}

    /** @param requestId identificador para rastreio (vai no header X-Request-Id) */
    Chamada decidir(String state, Map<String, Pergunta> perguntas, String requestId);

    String nome();

    /** O modelo esta fora do ar ou recusou o pedido: o servico devolve 503 e o Jitterbit tenta de novo depois. */
    class ModeloIndisponivelException extends RuntimeException {
        public ModeloIndisponivelException(String msg, Throwable causa) { super(msg, causa); }
    }

    // =====================================================================
    // Implementacoes HTTP (laya-serve local e Vercel AI Gateway)
    // =====================================================================
    abstract class ModeloHttp implements Classificador {
        protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        protected Chamada enviar(HttpRequest.Builder req, Map<String, Pergunta> perguntas) {
            RuntimeException ultimo = null;
            HttpRequest r = req.build();
            for (int t = 0; t < 3; t++) {
                long t0 = System.nanoTime();
                try {
                    HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    if (resp.statusCode() == 200) {
                        conferir(resp);
                        return ler(resp.body(), perguntas, ms);
                    }
                    ultimo = new ModeloIndisponivelException("HTTP " + resp.statusCode() + " do modelo: " + resp.body().substring(0, Math.min(200, resp.body().length())), null);
                    if (resp.statusCode() != 429 && resp.statusCode() < 500) throw ultimo; // erro do pedido: nao adianta repetir
                } catch (ConnectException e) {
                    throw new ModeloIndisponivelException("Modelo fora do ar em " + r.uri().getHost() + ":" + r.uri().getPort(), e);
                } catch (IOException e) {
                    ultimo = new ModeloIndisponivelException("Falha de rede com o modelo: " + e.getMessage(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ModeloIndisponivelException("Interrompido", e);
                }
                try { Thread.sleep(300L << t); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            throw ultimo;
        }

        /** Gancho para conferir a resposta antes de usa-la (por exemplo, qual checkpoint respondeu). Lance ModeloIndisponivelException para recusar. */
        protected void conferir(HttpResponse<String> resp) { }

        /** Header HTTP so aceita ASCII com seguranca: "consulta:fogão" vira "consulta%3Afog%C3%A3o". */
        static String idCabecalho(String id) {
            return java.net.URLEncoder.encode(id == null ? "" : id, java.nio.charset.StandardCharsets.UTF_8);
        }

        static Chamada ler(String corpo, Map<String, Pergunta> perguntas, long ms) {
            Map<String, Object> r = Json.mapa(Json.parse(corpo));
            Map<String, Object> answers = Json.mapa(r.get("answers"));
            Map<String, Resposta> out = new LinkedHashMap<>();
            for (String q : perguntas.keySet()) {
                Map<String, Object> a = Json.mapa(answers.get(q));
                String valor = a.get("choice") == null ? null : a.get("choice").toString();
                double conf;
                Map<String, Double> probs = new LinkedHashMap<>();
                if (a.get("probabilities") instanceof Map<?, ?> p)
                    p.forEach((k, v) -> { if (v instanceof Number n) probs.put(String.valueOf(k), n.doubleValue()); });
                if (a.get("confidence") instanceof Number n) conf = n.doubleValue();
                else conf = probs.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                out.put(q, new Resposta(valor, conf, probs));
            }
            Map<String, Object> usage = Json.mapa(r.get("usage"));
            Object tin = usage.containsKey("input_tokens") ? usage.get("input_tokens") : usage.get("inputTokens");
            return new Chamada(out, ms, tin instanceof Number n ? n.longValue() : 0);
        }

        static String perguntasJson(Map<String, Pergunta> perguntas) {
            StringBuilder sb = new StringBuilder("{");
            for (var e : perguntas.entrySet()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(Json.str(e.getKey())).append(":{\"type\":\"choice\",\"instructions\":").append(Json.str(e.getValue().instrucoes())).append(",\"criteria\":{");
                boolean primeiro = true;
                for (var c : e.getValue().criterios().entrySet()) {
                    if (!primeiro) sb.append(',');
                    sb.append(Json.str(c.getKey())).append(':').append(Json.str(c.getValue()));
                    primeiro = false;
                }
                sb.append("}}");
            }
            return sb.append('}').toString();
        }
    }

    /** Laya rodando na sua infraestrutura (laya-serve, POST /v1/systemone). */
    final class LayaLocal extends ModeloHttp {
        /** Cabecalho em que o servidor de checkpoints locais (simulacao/laya_local_ft.py) diz qual checkpoint respondeu: "nome:sha256". */
        public static final String CABECALHO_CHECKPOINT = "X-Laya-Checkpoint";
        private final String url, modelo, chave, checkpointEsperado;
        public LayaLocal(String url, String modelo, String chave) { this(url, modelo, chave, null); }
        /** checkpointEsperado: vazio = nao confere; "nome" confere o nome; "nome:hash" confere tambem o hash (prefixo) do model.safetensors. */
        public LayaLocal(String url, String modelo, String chave, String checkpointEsperado) {
            this.url = url; this.modelo = modelo; this.chave = chave;
            this.checkpointEsperado = checkpointEsperado == null || checkpointEsperado.isBlank() ? null : checkpointEsperado.trim();
        }
        public String nome() { return "laya-local:" + modelo; }

        @Override protected void conferir(HttpResponse<String> resp) {
            if (checkpointEsperado == null) return;
            String recebido = resp.headers().firstValue(CABECALHO_CHECKPOINT).orElse("");
            if (!checkpointConfere(checkpointEsperado, recebido))
                throw new ModeloIndisponivelException("O servidor respondeu com o checkpoint '" + (recebido.isBlank() ? "(sem identificacao)" : recebido)
                        + "', mas o configurado e '" + checkpointEsperado + "': resposta recusada", null);
        }

        /** "nome" casa com "nome:hash"; "nome:hash" exige o mesmo nome e o mesmo prefixo de hash. */
        static boolean checkpointConfere(String esperado, String recebido) {
            if (recebido == null || recebido.isBlank()) return false;
            int e = esperado.indexOf(':'), r = recebido.indexOf(':');
            String nomeEsp = e < 0 ? esperado : esperado.substring(0, e), nomeRec = r < 0 ? recebido : recebido.substring(0, r);
            if (!nomeEsp.equals(nomeRec)) return false;
            if (e < 0) return true;
            return r >= 0 && !esperado.substring(e + 1).isEmpty() && recebido.substring(r + 1).startsWith(esperado.substring(e + 1));
        }

        public Chamada decidir(String state, Map<String, Pergunta> perguntas, String requestId) {
            String corpo = "{\"model\":" + Json.str(modelo) + ",\"state\":" + Json.str(state) + ",\"questions\":" + perguntasJson(perguntas) + "}";
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/v1/systemone")).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json").header("X-Request-Id", idCabecalho(requestId))
                    .POST(HttpRequest.BodyPublishers.ofString(corpo));
            if (chave != null && !chave.isBlank()) b.header("Authorization", "Bearer " + chave);
            return enviar(b, perguntas);
        }
    }

    /** Laya ou Jev hospedados no Vercel AI Gateway (POST /v1/evaluate). */
    final class Gateway extends ModeloHttp {
        private final String modelo, chave;
        public Gateway(String modelo, String chave) { this.modelo = modelo; this.chave = chave; }
        public String nome() { return "gateway:" + modelo; }

        public Chamada decidir(String state, Map<String, Pergunta> perguntas, String requestId) {
            String corpo = "{\"model\":" + Json.str(modelo) + ",\"state\":" + Json.str(state) + ",\"questions\":" + perguntasJson(perguntas) + "}";
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("https://ai-gateway.vercel.sh/v1/evaluate")).timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + chave).header("Content-Type", "application/json").header("X-Request-Id", idCabecalho(requestId))
                    .POST(HttpRequest.BodyPublishers.ofString(corpo));
            return enviar(b, perguntas);
        }
    }

    /** Linha de base sem IA: palavras-chave + confianca na categoria do vendedor. */
    final class Regras implements Classificador {
        private static final Set<String> STOP = Set.of("para", "com", "sem", "como", "outro", "outros", "produto", "inclusive", "nao", "inclui", "aparece", "anuncio", "principal");
        public String nome() { return "regras"; }

        public Chamada decidir(String state, Map<String, Pergunta> perguntas, String requestId) {
            String texto = Texto.normalizar(state).replaceAll("\\b(110|127|220)\\s?v(olts)?\\b", "$1v");
            String linhaVendedor = texto.lines().filter(l -> l.startsWith("categoria do vendedor")).findFirst().orElse("");
            Set<String> tokens = new HashSet<>(Arrays.asList(texto.split("[^a-z0-9]+")));
            Set<String> tokensVendedor = new HashSet<>(Arrays.asList(linhaVendedor.split("[^a-z0-9]+")));
            Map<String, Resposta> out = new LinkedHashMap<>();
            for (var e : perguntas.entrySet()) {
                String melhor = null; double melhorScore = -1, soma = 0;
                for (var op : e.getValue().criterios().entrySet()) {
                    double s = 0;
                    for (String t : (Texto.normalizar(op.getKey().replace('_', ' ')) + " " + Texto.normalizar(op.getValue())).split("[^a-z0-9]+")) {
                        if (t.length() < 4 || STOP.contains(t)) continue;
                        if (tokensVendedor.contains(t)) s += 3; else if (tokens.contains(t)) s += 1;
                    }
                    soma += s;
                    if (s > melhorScore) { melhorScore = s; melhor = op.getKey(); }
                }
                out.put(e.getKey(), new Resposta(melhor, soma == 0 ? 0.2 : Math.min(0.99, melhorScore / soma)));
            }
            return new Chamada(out, 0, 0);
        }
    }
}
