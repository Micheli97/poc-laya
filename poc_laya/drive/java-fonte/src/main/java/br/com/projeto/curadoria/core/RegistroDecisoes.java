package br.com.projeto.curadoria.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Log de decisoes APLICADAS e de rollbacks, em JSONL so de acrescimo, gravado a cada decisao (arquivo aberto, escrito e fechado:
 * nada fica em buffer). E dele que o rollback le a categoria anterior e a versao do agente, e ele sobrevive a reinicio.
 *
 * Em producao isto vira uma tabela num banco (append-only, com indice por id, categoria e versao).
 */
public final class RegistroDecisoes {

    /** Uma linha do log. tipo = "aplicado" ou "rollback". */
    public record Evento(String quando, String tipo, String id, String categoriaAnterior, String categoriaNova, String departamentoNova,
                         boolean mudou, String versaoAgente, String regra, String motivo) {}

    private final Path arquivo;
    /** ultimo evento de cada produto: o estado atual segundo o log */
    private final Map<String, Evento> atual = new LinkedHashMap<>();

    public RegistroDecisoes(Path arquivo) throws IOException {
        this.arquivo = arquivo;
        if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
        if (Files.exists(arquivo)) {
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                if (linha.isBlank()) continue;
                try { Evento e = ler(linha); atual.put(e.id(), e); } catch (RuntimeException linhaRuim) { /* linha truncada de uma queda: ignora */ }
            }
        }
    }

    public synchronized void registrarAplicado(String id, String categoriaAnterior, String categoriaNova, String departamentoNova, boolean mudou,
                                               String versaoAgente, String regra) {
        gravar(new Evento(Instant.now().toString(), "aplicado", id, categoriaAnterior, categoriaNova, departamentoNova, mudou, versaoAgente, regra, null));
    }

    public synchronized void registrarRollback(Evento aplicado, String motivo) {
        gravar(new Evento(Instant.now().toString(), "rollback", aplicado.id(), aplicado.categoriaNova(), aplicado.categoriaAnterior(), null,
                false, aplicado.versaoAgente(), "rollback", motivo));
    }

    /** Decisoes aplicadas que mudaram a categoria e ainda nao foram desfeitas, filtradas. */
    public synchronized List<Evento> candidatosAoRollback(Predicate<Evento> filtro) {
        List<Evento> out = new ArrayList<>();
        for (Evento e : atual.values()) if ("aplicado".equals(e.tipo()) && e.mudou() && filtro.test(e)) out.add(e);
        return out;
    }

    /** Categoria que o produto tem agora segundo o log (a ultima curada), ou null se nunca foi aplicado. */
    public synchronized String categoriaAtualAplicada(String id) {
        Evento e = atual.get(id);
        return e != null && "aplicado".equals(e.tipo()) && e.mudou() ? e.categoriaNova() : null;
    }

    public synchronized Evento ultimo(String id) { return atual.get(id); }

    public synchronized long total() { return atual.size(); }

    private void gravar(Evento e) {
        String linha = "{\"quando\":" + Json.str(e.quando()) + ",\"tipo\":" + Json.str(e.tipo()) + ",\"id\":" + Json.str(e.id())
                + ",\"categoriaAnterior\":" + Json.str(e.categoriaAnterior()) + ",\"categoriaNova\":" + Json.str(e.categoriaNova())
                + ",\"departamentoNova\":" + Json.str(e.departamentoNova()) + ",\"mudou\":" + e.mudou() + ",\"versaoAgente\":" + Json.str(e.versaoAgente())
                + ",\"regra\":" + Json.str(e.regra()) + ",\"motivo\":" + Json.str(e.motivo()) + "}\n";
        try {
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
            atual.put(e.id(), e);
        } catch (IOException ex) {
            // se o log nao grava, o rollback depois nao teria como saber o que foi feito: melhor falhar alto
            throw new IllegalStateException("nao consegui gravar o registro de decisoes: " + ex.getMessage(), ex);
        }
    }

    private static Evento ler(String linha) {
        Map<String, Object> m = Json.mapa(Json.parse(linha));
        return new Evento((String) m.get("quando"), (String) m.get("tipo"), (String) m.get("id"), (String) m.get("categoriaAnterior"), (String) m.get("categoriaNova"),
                (String) m.get("departamentoNova"), Boolean.TRUE.equals(m.get("mudou")), (String) m.get("versaoAgente"), (String) m.get("regra"), (String) m.get("motivo"));
    }
}
