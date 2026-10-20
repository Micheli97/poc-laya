package br.com.projeto.curadoria.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decide APLICAR ou MANTER a partir dos votos de sinais independentes. Regras, na ordem:
 *  1. qualquer sinal com FALHA mantem o produto (nada e aplicado quando um sinal quebrou);
 *  2. precisa haver uma categoria votada por pelo menos {@code minimoConcordantes} sinais;
 *  3. com {@code exigirModelo}, pelo menos um dos concordantes tem que ser modelo (o vendedor sozinho nao basta);
 *  4. VETO: algum sinal DA LISTA {@code sinaisComVeto} que votou em OUTRA categoria com confianca acima de {@code vetoConfianca}
 *     mantem o produto. So sinal com confianca calibrada deve poder vetar: por padrao llm e supervisionado, nao o Laya;
 *  5. a confianca media dos concordantes precisa chegar a {@code limiar}.
 * Na duvida o produto fica como esta (MANTER). Esta classe nao conhece Laya, LLM nem rede: so votos.
 */
public final class PoliticaDecisao {

    public static final String FALHA_SINAL = "falha-sinal";
    public static final String VETO = "veto";
    public static final String DISCORDANCIA = "discordancia";
    public static final String CONFIANCA_BAIXA = "confianca-baixa";
    public static final String SINAIS_INSUFICIENTES = "sinais-insuficientes";
    public static final String SO_VENDEDOR = "so-vendedor";

    /** Sinais que podem vetar quando nada e configurado: os de confianca calibrada. O Laya so veta se for incluido. */
    public static final java.util.Set<String> VETO_PADRAO = java.util.Set.of("llm", "supervisionado");

    /**
     * @param minimoConcordantes quantos sinais precisam votar na mesma categoria
     * @param vetoConfianca      acima disso, o voto contrario de um sinal com veto bloqueia
     * @param sinaisComVeto      quais sinais podem vetar (nulo = {@link #VETO_PADRAO})
     */
    public record Parametros(int minimoConcordantes, boolean exigirModelo, double vetoConfianca, double limiar, java.util.Set<String> sinaisComVeto,
                             java.util.Set<String> sinaisUsados) {
        public Parametros {
            if (minimoConcordantes < 1) throw new IllegalArgumentException("minimo-concordantes precisa ser pelo menos 1");
            sinaisComVeto = sinaisComVeto == null ? VETO_PADRAO : java.util.Set.copyOf(sinaisComVeto);
            sinaisUsados = sinaisUsados == null || sinaisUsados.isEmpty() ? null : java.util.Set.copyOf(sinaisUsados);
        }
        /** Forma anterior: todos os sinais configurados votam. {@code sinaisUsados} so serve para avaliar subconjuntos de sinais na varredura. */
        public Parametros(int minimoConcordantes, boolean exigirModelo, double vetoConfianca, double limiar, java.util.Set<String> sinaisComVeto) {
            this(minimoConcordantes, exigirModelo, vetoConfianca, limiar, sinaisComVeto, null);
        }
        /** Forma anterior: veto so de llm e supervisionado. */
        public Parametros(int minimoConcordantes, boolean exigirModelo, double vetoConfianca, double limiar) {
            this(minimoConcordantes, exigirModelo, vetoConfianca, limiar, null);
        }
        /** Texto curto para relatorios: "min=2 modelo=sim veto=0.80 limiar=0.70 veto-de=llm+supervisionado". */
        public String resumo() {
            return String.format(Locale.ROOT, "min=%d modelo=%s veto=%.2f limiar=%.2f veto-de=%s", minimoConcordantes, exigirModelo ? "sim" : "nao", vetoConfianca, limiar,
                    String.join("+", new java.util.TreeSet<>(sinaisComVeto))) + (sinaisUsados == null ? "" : " sinais=" + String.join("+", new java.util.TreeSet<>(sinaisUsados)));
        }
    }

    /**
     * @param aplicar        true = aplicar {@code categoria}; false = manter
     * @param categoria      a categoria escolhida (se aplicar) ou a mais votada (se manter, so informativa; pode ser nula)
     * @param motivo         sempre preenchido; quando mantem, e o motivo (falha-sinal:x, veto:x, discordancia, ...)
     * @param confiancaMedia dos concordantes (0 se nao houve candidata)
     */
    public record Decisao(boolean aplicar, String categoria, double confiancaMedia, String motivo, List<Voto> concordantes, List<Voto> votos) {
        /** Para auditoria: "laya:fogoes(0.91) vendedor:fogoes(0.70) supervisionado:cooktops(0.55)". */
        public String descreverVotos() {
            return votos.isEmpty() ? "nenhum voto" : votos.stream().map(v -> String.format(Locale.ROOT, "%s:%s(%.2f)", v.sinal(), v.categoria(), v.confianca())).collect(Collectors.joining(" "));
        }
    }

    private final Parametros par;

    public PoliticaDecisao(Parametros parametros) { this.par = parametros; }

    public Parametros parametros() { return par; }

    public Decisao decidir(List<ResultadoSinal> todos) {
        // com sinaisUsados (so na avaliacao), os outros sinais sao ignorados, como se nao estivessem configurados
        List<ResultadoSinal> resultados = par.sinaisUsados() == null ? todos : todos.stream().filter(r -> par.sinaisUsados().contains(r.sinal())).toList();
        List<Voto> votos = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (ResultadoSinal r : resultados) {
            if (r instanceof ResultadoSinal.ComVoto c) votos.add(c.voto());
            else if (r instanceof ResultadoSinal.Falha f) falhas.add(f.sinal());
            // SemVoto: nao se aplica, nao e voto nem falha
        }
        if (!falhas.isEmpty()) return manter(null, 0, FALHA_SINAL + ":" + String.join(",", falhas), List.of(), votos);
        if (votos.isEmpty()) return manter(null, 0, SINAIS_INSUFICIENTES, List.of(), votos);

        Map<String, List<Voto>> porCategoria = new LinkedHashMap<>();
        for (Voto v : votos) porCategoria.computeIfAbsent(v.categoria(), k -> new ArrayList<>()).add(v);

        // a mais votada (empate: maior confianca media)
        var melhor = porCategoria.entrySet().stream()
                .max(Comparator.<Map.Entry<String, List<Voto>>>comparingInt(e -> e.getValue().size()).thenComparingDouble(e -> media(e.getValue()))).orElseThrow();
        String categoria = melhor.getKey();
        List<Voto> concordantes = melhor.getValue();
        double media = media(concordantes);

        if (concordantes.size() < par.minimoConcordantes())
            return manter(categoria, media, votos.size() < par.minimoConcordantes() ? SINAIS_INSUFICIENTES : DISCORDANCIA, concordantes, votos);
        if (par.exigirModelo() && concordantes.stream().noneMatch(Voto::modelo)) return manter(categoria, media, SO_VENDEDOR, concordantes, votos);

        for (Voto v : votos) // veto: um sinal CALIBRADO e firme em outra categoria
            if (par.sinaisComVeto().contains(v.sinal()) && !v.categoria().equals(categoria) && v.confianca() > par.vetoConfianca()) return manter(categoria, media, VETO + ":" + v.sinal(), concordantes, votos);

        if (media < par.limiar()) return manter(categoria, media, CONFIANCA_BAIXA, concordantes, votos);
        return new Decisao(true, categoria, media, "votacao:" + concordantes.stream().map(Voto::sinal).collect(Collectors.joining("+")), concordantes, votos);
    }

    private static Decisao manter(String categoria, double media, String motivo, List<Voto> concordantes, List<Voto> votos) {
        return new Decisao(false, categoria, media, motivo, concordantes, votos);
    }

    private static double media(List<Voto> vs) { return vs.stream().mapToDouble(Voto::confianca).average().orElse(0); }
}
