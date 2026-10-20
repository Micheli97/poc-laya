package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os produtos MANTIDO (e o que ja se tentou com eles) em JSONL so de acrescimo, gravado a cada mudanca (aberto, escrito e fechado:
 * nada fica em buffer). Cada linha guarda o produto inteiro, o motivo, a versao dos sinais e as tentativas; depois de um
 * reinicio, {@code /curadoria/reprocessar} le o estado daqui e consegue reavaliar os produtos.
 *
 * O arquivo so cresce: a ultima linha de cada id vale, e uma linha "removido" apaga o produto (foi aplicado ou revisado).
 * Em producao isto vira uma tabela num banco, com chave no id do produto.
 */
public final class RegistroMantidos {

    /** O estado de um produto MANTIDO. */
    public record Estado(String id, String motivo, String versao, int tentativasSemMudanca, int reprocessos, ProdutoEntrada produto) {}

    private final Path arquivo;
    private final Map<String, Estado> atual = new LinkedHashMap<>();

    public RegistroMantidos(Path arquivo) throws IOException {
        this.arquivo = arquivo;
        if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
        if (Files.exists(arquivo)) {
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                if (linha.isBlank()) continue;
                try {
                    Map<String, Object> m = Json.mapa(Json.parse(linha));
                    String id = (String) m.get("id");
                    if ("removido".equals(m.get("tipo"))) atual.remove(id);
                    else atual.put(id, new Estado(id, (String) m.get("motivo"), (String) m.get("versao"), ((Number) m.get("tentativasSemMudanca")).intValue(),
                            ((Number) m.get("reprocessos")).intValue(), lerProduto(Json.mapa(m.get("produto")))));
                } catch (RuntimeException linhaRuim) { /* linha truncada de uma queda: ignora */ }
            }
        }
    }

    public synchronized void registrar(Estado e) {
        gravar("{\"quando\":" + Json.str(Instant.now().toString()) + ",\"tipo\":\"mantido\",\"id\":" + Json.str(e.id()) + ",\"motivo\":" + Json.str(e.motivo())
                + ",\"versao\":" + Json.str(e.versao()) + ",\"tentativasSemMudanca\":" + e.tentativasSemMudanca() + ",\"reprocessos\":" + e.reprocessos()
                + ",\"produto\":" + produtoJson(e.produto()) + "}\n");
        atual.put(e.id(), e);
    }

    /** O produto deixou de ser MANTIDO (foi aplicado ou ganhou outro desfecho). */
    public synchronized void registrarRemovido(String id) {
        if (!atual.containsKey(id)) return;
        gravar("{\"quando\":" + Json.str(Instant.now().toString()) + ",\"tipo\":\"removido\",\"id\":" + Json.str(id) + "}\n");
        atual.remove(id);
    }

    public synchronized Map<String, Estado> estado() { return new LinkedHashMap<>(atual); }

    private void gravar(String linha) {
        try {
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException ex) {
            // sem o registro o reprocessamento depois de um reinicio nao acharia o produto: melhor falhar alto
            throw new IllegalStateException("nao consegui gravar o registro de mantidos: " + ex.getMessage(), ex);
        }
    }

    static String produtoJson(ProdutoEntrada p) {
        StringBuilder at = new StringBuilder("{");
        if (p.atributos() != null) for (var e : p.atributos().entrySet()) { if (at.length() > 1) at.append(','); at.append(Json.str(e.getKey())).append(':').append(Json.str(e.getValue())); }
        at.append('}');
        return "{\"id\":" + Json.str(p.id()) + ",\"sku\":" + Json.str(p.sku()) + ",\"titulo\":" + Json.str(p.titulo()) + ",\"descricao\":" + Json.str(p.descricao())
                + ",\"categoriaVendedor\":" + Json.str(p.categoriaVendedor()) + ",\"tags\":" + Json.str(p.tags()) + ",\"marcaInformada\":" + Json.str(p.marcaInformada()) + ",\"atributos\":" + at
                + ",\"bullets\":" + bulletsJson(p.bullets()) + ",\"modelo\":" + Json.str(p.modelo()) + ",\"ean\":" + Json.str(p.ean())
                + ",\"preco\":" + Json.str(p.preco()) + ",\"condicao\":" + Json.str(p.condicao()) + "}";
    }

    private static String bulletsJson(java.util.List<String> bullets) {
        if (bullets == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (String b : bullets) { if (sb.length() > 1) sb.append(','); sb.append(Json.str(b)); }
        return sb.append(']').toString();
    }

    private static ProdutoEntrada lerProduto(Map<String, Object> m) {
        Map<String, String> at = new LinkedHashMap<>();
        Json.mapa(m.get("atributos")).forEach((k, v) -> at.put(k, String.valueOf(v)));
        java.util.List<String> bullets = m.get("bullets") instanceof java.util.List<?> l ? l.stream().map(String::valueOf).toList() : null;
        return new ProdutoEntrada((String) m.get("id"), (String) m.get("sku"), (String) m.get("titulo"), (String) m.get("descricao"),
                (String) m.get("categoriaVendedor"), (String) m.get("tags"), (String) m.get("marcaInformada"), at,
                bullets, (String) m.get("modelo"), (String) m.get("ean"), (String) m.get("preco"), (String) m.get("condicao"), null);
    }
}
