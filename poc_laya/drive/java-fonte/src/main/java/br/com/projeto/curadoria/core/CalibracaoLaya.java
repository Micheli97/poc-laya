package br.com.projeto.curadoria.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Calibra a confianca do voto do Laya com UMA temperatura, ajustada num conjunto rotulado (o conjunto de treino),
 * como foi feito no classificador supervisionado.
 *
 * A confianca do voto do Laya e c = min(confianca do departamento, confianca da categoria). Tratamos c como a probabilidade
 * de o voto estar certo e escalamos o seu logit: c' = sigmoide(logit(c) / T). T > 1 reduz a confianca (modelo confiante demais);
 * T < 1 aumenta (modelo tímido). A ordem dos produtos por confianca nao muda; muda o que o numero significa, que e o que o
 * limiar e o veto da votacao usam.
 *
 * LIMITE da temperatura: ela preserva o sinal do logit, entao confianca acima de 0,5 continua acima de 0,5 e abaixo continua abaixo.
 * Se o modelo acerta menos de 50% justamente onde tem confianca alta, a temperatura melhora o ECE mas nao o conserta; ai e preciso
 * outro calibrador (por exemplo Platt, com inclinacao e deslocamento).
 *
 * Mede o ECE (erro de calibracao esperado, em 10 faixas) antes e depois, no mesmo conjunto.
 */
public final class CalibracaoLaya {

    /** @param amostras produtos em que o Laya votou; @param acertoLaya acerto do voto nesses produtos */
    public record Relatorio(int amostras, int semResposta, double acertoLaya, double temperatura, double eceAntes, double eceDepois, String versao, String aviso) {}

    private static final double EPS = 1e-6;

    private final double temperatura;
    private final Relatorio relatorio;

    private CalibracaoLaya(double temperatura, Relatorio relatorio) {
        this.temperatura = temperatura;
        this.relatorio = relatorio;
    }

    public double temperatura() { return temperatura; }
    public Relatorio relatorio() { return relatorio; }
    public String versao() { return relatorio.versao(); }

    /** Confianca calibrada de uma confianca bruta do Laya. */
    public double calibrar(double bruta) { return aplicar(bruta, temperatura); }

    static double aplicar(double c, double t) {
        double x = Math.min(1 - EPS, Math.max(EPS, c));
        return 1.0 / (1.0 + Math.exp(-Math.log(x / (1 - x)) / t));
    }

    /**
     * Ajusta a temperatura que minimiza a log-verossimilhanca negativa binaria (voto certo ou errado).
     *
     * @param confs  confianca bruta de cada voto do Laya
     * @param certos se a categoria votada era a rotulada
     * @param semResposta produtos do conjunto em que o Laya falhou ou nao votou (so para o relatorio)
     */
    public static CalibracaoLaya ajustar(double[] confs, boolean[] certos, int semResposta, String aviso) {
        if (confs.length < 30) throw new IllegalArgumentException("amostras insuficientes para calibrar o Laya: " + confs.length + " (minimo 30)");
        double melhorT = 1.0, melhorNll = Double.MAX_VALUE;
        for (int i = 0; i <= 160; i++) {
            double t = Math.pow(10, -1.0 + i * (2.3 / 160.0)); // 0,1 a ~20, escala logaritmica
            double nll = 0;
            for (int k = 0; k < confs.length; k++) {
                double p = aplicar(confs[k], t);
                nll += -Math.log(Math.max(1e-12, certos[k] ? p : 1 - p));
            }
            if (nll < melhorNll) { melhorNll = nll; melhorT = t; }
        }
        double[] depois = new double[confs.length];
        int acertos = 0;
        for (int k = 0; k < confs.length; k++) { depois[k] = aplicar(confs[k], melhorT); if (certos[k]) acertos++; }
        double antes = ece(confs, certos), aposta = ece(depois, certos);
        String versao = ClassificadorTexto.hashDe(String.format(java.util.Locale.ROOT, "%.6f|%d|%.6f|%.6f", melhorT, confs.length, antes, aposta));
        return new CalibracaoLaya(melhorT, new Relatorio(confs.length, semResposta, (double) acertos / confs.length, melhorT, antes, aposta, versao, aviso));
    }

    /** ECE binario: media, por faixa de confianca, de |acerto - confianca| ponderada pelo tamanho da faixa. */
    public static double ece(double[] confs, boolean[] certos) {
        final int faixas = 10;
        double[] somaConf = new double[faixas], somaAcerto = new double[faixas];
        int[] n = new int[faixas];
        for (int k = 0; k < confs.length; k++) {
            int b = Math.min(faixas - 1, (int) (confs[k] * faixas));
            somaConf[b] += confs[k];
            somaAcerto[b] += certos[k] ? 1 : 0;
            n[b]++;
        }
        double e = 0;
        for (int b = 0; b < faixas; b++) if (n[b] > 0) e += (double) n[b] / confs.length * Math.abs(somaAcerto[b] / n[b] - somaConf[b] / n[b]);
        return e;
    }

    // ---- arquivo (dados-agente/calibracao-laya.json)
    public void salvar(Path arquivo) throws IOException {
        if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
        Relatorio r = relatorio;
        Files.writeString(arquivo, "{\"temperatura\":" + temperatura + ",\"amostras\":" + r.amostras() + ",\"semResposta\":" + r.semResposta() + ",\"acertoLaya\":" + r.acertoLaya()
                + ",\"eceAntes\":" + r.eceAntes() + ",\"eceDepois\":" + r.eceDepois() + ",\"versao\":" + Json.str(r.versao()) + ",\"aviso\":" + Json.str(r.aviso()) + "}\n", StandardCharsets.UTF_8);
    }

    public static CalibracaoLaya carregar(Path arquivo) throws IOException {
        Map<String, Object> m = Json.mapa(Json.parse(Files.readString(arquivo, StandardCharsets.UTF_8)));
        double t = ((Number) m.get("temperatura")).doubleValue();
        return new CalibracaoLaya(t, new Relatorio(((Number) m.get("amostras")).intValue(), ((Number) m.get("semResposta")).intValue(), ((Number) m.get("acertoLaya")).doubleValue(), t,
                ((Number) m.get("eceAntes")).doubleValue(), ((Number) m.get("eceDepois")).doubleValue(), (String) m.get("versao"), (String) m.get("aviso")));
    }
}
