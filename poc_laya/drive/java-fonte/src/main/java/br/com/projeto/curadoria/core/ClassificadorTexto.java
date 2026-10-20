package br.com.projeto.curadoria.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Classificador supervisionado em Java puro (sem dependencia nova): Naive Bayes multinomial sobre palavras e bigramas
 * do titulo limpo e dos valores dos atributos.
 *
 * REGRAS DE OURO
 *  - Treina SO com rotulos humanos (golden set e conjunto de treino). Nunca com decisoes do proprio agente: isso criaria um
 *    ciclo em que o agente aprende os proprios erros. Por isso {@link #lerTreino} recusa arquivos que o agente grava.
 *  - NAO usa a categoria do vendedor, nem como variante: ele aprenderia a copiar o vendedor e a votacao contaria o mesmo sinal duas vezes.
 *    Nao ha nenhum campo de vendedor em {@link Exemplo}.
 *  - A confianca e CALIBRADA: 20% do treino fica de fora para ajustar uma temperatura que reduz o ECE (erro de calibracao esperado).
 *    Naive Bayes sozinho e muito confiante; sem calibrar, o limiar da votacao nao significaria nada.
 */
public final class ClassificadorTexto {

    /** Um exemplo rotulado. Titulo e atributos do anuncio e a categoria certa. Nada do vendedor. */
    /** @param extra texto longo (destaques e descricao) do catalogo v2; nulo na v1, onde o modelo so usa titulo e atributos */
    public record Exemplo(String id, String titulo, Map<String, String> atributos, String categoria, String extra) {
        public Exemplo(String id, String titulo, Map<String, String> atributos, String categoria) { this(id, titulo, atributos, categoria, null); }
    }

    public record Predicao(String categoria, double confianca) {}

    /** Resultado do treino: numeros da calibracao no conjunto de calibracao (os 20% separados). */
    public record Relatorio(int exemplos, int treino, int calibracao, int vocabulario, double temperatura,
                            double eceAntes, double eceDepois, double acertoCalibracao, String versao) {}

    public record Treinado(ClassificadorTexto modelo, Relatorio relatorio) {}

    private static final double ALFA = 0.5;
    private static final String MAGIA = "CTXT2";
    /** Modelo ponderado (catalogo v2): contagens com TF-IDF em ponto fixo (x1000) e o IDF de cada caracteristica. */
    private static final String MAGIA_PONDERADO = "CTXT3";
    private static final double ALFA_PONDERADO = 0.1;
    private static final int ESCALA = 1000;
    private static final double PESO_DO_TITULO = 3.0;

    /** IDF x1000 por caracteristica; nulo nos modelos da v1. Com ele, o modelo e o ponderado (titulo x3, TF-IDF, contagem). */
    private final Map<String, Integer> idfMilesimos;
    private final List<String> categorias;
    private final Map<String, int[]> contagem;      // caracteristica -> contagem por classe
    private final long[] totalPorClasse;            // soma das contagens de cada classe
    private final int[] docsPorClasse;
    private final int totalDocs;
    private final double temperatura;
    private final Relatorio relatorio;
    private final String versao;

    private ClassificadorTexto(List<String> categorias, Map<String, int[]> contagem, long[] totalPorClasse, int[] docsPorClasse, int totalDocs,
                               double temperatura, Relatorio relatorio, String versao) {
        this(categorias, contagem, totalPorClasse, docsPorClasse, totalDocs, temperatura, relatorio, versao, null);
    }

    private ClassificadorTexto(List<String> categorias, Map<String, int[]> contagem, long[] totalPorClasse, int[] docsPorClasse, int totalDocs,
                               double temperatura, Relatorio relatorio, String versao, Map<String, Integer> idfMilesimos) {
        this.idfMilesimos = idfMilesimos;
        this.categorias = categorias;
        this.contagem = contagem;
        this.totalPorClasse = totalPorClasse;
        this.docsPorClasse = docsPorClasse;
        this.totalDocs = totalDocs;
        this.temperatura = temperatura;
        this.relatorio = relatorio;
        this.versao = versao;
    }

    public String versao() { return versao; }
    public Relatorio relatorio() { return relatorio; }
    public double temperatura() { return temperatura; }

    // =====================================================================
    // treino e calibracao
    // =====================================================================
    public static Treinado treinar(List<Exemplo> exemplos, long semente) {
        if (exemplos.size() < 10) throw new IllegalArgumentException("exemplos de treino insuficientes: " + exemplos.size());
        if (exemplos.stream().anyMatch(e -> e.extra() != null)) return treinarPonderado(exemplos, semente); // catalogo v2: texto longo
        List<Exemplo> emb = new ArrayList<>(exemplos);
        Collections.shuffle(emb, new Random(semente));
        int nCalib = Math.max(1, emb.size() / 5); // 20% para calibrar
        List<Exemplo> calib = emb.subList(0, nCalib), treino = emb.subList(nCalib, emb.size());

        List<String> cats = treino.stream().map(Exemplo::categoria).distinct().sorted().toList();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cats.size(); i++) idx.put(cats.get(i), i);
        Map<String, int[]> cont = new HashMap<>();
        long[] total = new long[cats.size()];
        int[] docs = new int[cats.size()];
        for (Exemplo e : treino) {
            int c = idx.get(e.categoria());
            docs[c]++;
            for (String f : caracteristicas(e.titulo(), e.atributos(), e.extra())) {
                cont.computeIfAbsent(f, k -> new int[cats.size()])[c]++;
                total[c]++;
            }
        }

        // calibracao: escolhe a temperatura que minimiza a log-verossimilhanca negativa no conjunto separado
        ClassificadorTexto bruto = new ClassificadorTexto(cats, cont, total, docs, treino.size(), 1.0, null, "-");
        List<double[]> logits = new ArrayList<>();
        List<Integer> certos = new ArrayList<>();
        for (Exemplo e : calib) {
            Integer c = idx.get(e.categoria());
            logits.add(bruto.pontuar(e.titulo(), e.atributos(), e.extra()));
            certos.add(c == null ? -1 : c); // categoria que nao apareceu no treino: sempre "errada"
        }
        double melhorT = 1.0, melhorNll = Double.MAX_VALUE;
        for (int i = 0; i <= 120; i++) {
            double t = Math.pow(10, i * (Math.log10(500.0) / 120.0)); // 1 a 500, escala logaritmica
            double nll = 0;
            for (int k = 0; k < logits.size(); k++) {
                double[] p = softmax(logits.get(k), t);
                int c = certos.get(k);
                nll += -Math.log(Math.max(1e-12, c < 0 ? 1e-12 : p[c]));
            }
            if (nll < melhorNll) { melhorNll = nll; melhorT = t; }
        }
        double eceAntes = ece(logits, certos, 1.0), eceDepois = ece(logits, certos, melhorT);
        int acertos = 0;
        for (int k = 0; k < logits.size(); k++) if (argmax(logits.get(k)) == certos.get(k)) acertos++;

        Relatorio rel = new Relatorio(exemplos.size(), treino.size(), calib.size(), cont.size(), melhorT, eceAntes, eceDepois, (double) acertos / logits.size(), null);
        ClassificadorTexto sem = new ClassificadorTexto(cats, cont, total, docs, treino.size(), melhorT, rel, "-");
        byte[] bytes = sem.serializar();
        String v = hash(bytes);
        Relatorio relComVersao = new Relatorio(rel.exemplos(), rel.treino(), rel.calibracao(), rel.vocabulario(), rel.temperatura(), rel.eceAntes(), rel.eceDepois(), rel.acertoCalibracao(), v);
        return new Treinado(new ClassificadorTexto(cats, cont, total, docs, treino.size(), melhorT, relComVersao, v), relComVersao);
    }

    // =====================================================================
    // modelo ponderado (catalogo v2)
    // =====================================================================
    /**
     * Naive Bayes multinomial com contagens ponderadas por TF-IDF, para anuncios com titulo curto e muito texto: o titulo conta x3, e os
     * atributos do vendedor, os destaques e a descricao entram com prefixos proprios. O ajuste (peso do titulo, TF-IDF, contagem em vez de
     * presenca, alfa 0,1) foi escolhido na VALIDACAO do catalogo v2; com a presenca binaria da v1 o acerto cai de ~89% para ~44%.
     * A temperatura sai dos 20% separados, mas as contagens finais usam TODOS os exemplos (e o que o time rotulou: nao desperdica nenhum).
     */
    private static Treinado treinarPonderado(List<Exemplo> exemplos, long semente) {
        List<Exemplo> emb = new ArrayList<>(exemplos);
        Collections.shuffle(emb, new Random(semente));
        int nCalib = Math.max(1, emb.size() / 5);
        List<Exemplo> calib = emb.subList(0, nCalib), treino = emb.subList(nCalib, emb.size());

        ClassificadorTexto parcial = contarPonderado(treino, 1.0, null);
        Map<String, Integer> idxParcial = new HashMap<>();
        for (int i = 0; i < parcial.categorias.size(); i++) idxParcial.put(parcial.categorias.get(i), i);
        List<double[]> logits = new ArrayList<>();
        List<Integer> certos = new ArrayList<>();
        for (Exemplo e : calib) {
            logits.add(parcial.pontuar(e.titulo(), e.atributos(), e.extra()));
            certos.add(idxParcial.getOrDefault(e.categoria(), -1));
        }
        double melhorT = 1.0, melhorNll = Double.MAX_VALUE;
        for (int i = 0; i <= 120; i++) {
            double t = Math.pow(10, i * (Math.log10(500.0) / 120.0));
            double nll = 0;
            for (int k = 0; k < logits.size(); k++) {
                double[] p = softmax(logits.get(k), t);
                int c = certos.get(k);
                nll += -Math.log(Math.max(1e-12, c < 0 ? 1e-12 : p[c]));
            }
            if (nll < melhorNll) { melhorNll = nll; melhorT = t; }
        }
        double eceAntes = ece(logits, certos, 1.0), eceDepois = ece(logits, certos, melhorT);
        int acertos = 0;
        for (int k = 0; k < logits.size(); k++) if (argmax(logits.get(k)) == certos.get(k)) acertos++;

        Relatorio rel = new Relatorio(exemplos.size(), exemplos.size(), calib.size(), 0, melhorT, eceAntes, eceDepois, (double) acertos / logits.size(), null);
        ClassificadorTexto sem = contarPonderado(emb, melhorT, rel);
        String v = hash(sem.serializar());
        Relatorio relComVersao = new Relatorio(rel.exemplos(), rel.treino(), rel.calibracao(), sem.contagem.size(), rel.temperatura(), rel.eceAntes(), rel.eceDepois(), rel.acertoCalibracao(), v);
        ClassificadorTexto finalM = new ClassificadorTexto(sem.categorias, sem.contagem, sem.totalPorClasse, sem.docsPorClasse, sem.totalDocs, melhorT, relComVersao, v, sem.idfMilesimos);
        return new Treinado(finalM, relComVersao);
    }

    /** Conta com TF-IDF (x1000, em ponto fixo) os exemplos dados. */
    private static ClassificadorTexto contarPonderado(List<Exemplo> docs, double temperatura, Relatorio rel) {
        List<String> cats = docs.stream().map(Exemplo::categoria).distinct().sorted().toList();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cats.size(); i++) idx.put(cats.get(i), i);
        List<Map<String, Double>> tfs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (Exemplo e : docs) {
            Map<String, Double> tf = termos(e.titulo(), e.atributos(), e.extra());
            tfs.add(tf);
            for (String f : tf.keySet()) df.merge(f, 1, Integer::sum);
        }
        Map<String, Integer> idf = new HashMap<>();
        df.forEach((f, n) -> idf.put(f, (int) Math.round((Math.log((docs.size() + 1.0) / (n + 1.0)) + 1.0) * ESCALA)));
        Map<String, int[]> cont = new HashMap<>();
        long[] total = new long[cats.size()];
        int[] ndocs = new int[cats.size()];
        for (int d = 0; d < docs.size(); d++) {
            int c = idx.get(docs.get(d).categoria());
            ndocs[c]++;
            for (var t : tfs.get(d).entrySet()) {
                int w = (int) Math.round(t.getValue() * idf.get(t.getKey())); // tf x idf, ja x1000
                cont.computeIfAbsent(t.getKey(), k -> new int[cats.size()])[c] += w;
                total[c] += w;
            }
        }
        return new ClassificadorTexto(cats, cont, total, ndocs, docs.size(), temperatura, rel, "-", idf);
    }

    /** Termos com a frequencia: titulo x3 ("t:"), valores dos atributos do vendedor ("a:") e destaques e descricao ("d:"). */
    static Map<String, Double> termos(String titulo, Map<String, String> atributos, String extra) {
        Map<String, Double> f = new LinkedHashMap<>();
        for (String p : palavras(new Preparador().limparTitulo(titulo))) f.merge("t:" + p, PESO_DO_TITULO, Double::sum);
        if (atributos != null) for (String valor : atributos.values()) for (String p : palavras(valor)) f.merge("a:" + p, 1.0, Double::sum);
        if (extra != null) for (String p : palavras(extra)) f.merge("d:" + p, 1.0, Double::sum);
        return f;
    }

    private double[] pontuarPonderado(String titulo, Map<String, String> atributos, String extra) {
        int n = categorias.size();
        double[] s = new double[n];
        double v = contagem.size();
        for (int c = 0; c < n; c++) s[c] = Math.log((docsPorClasse[c] + 0.5) / (totalDocs + 0.5 * n));
        double alfa = ALFA_PONDERADO * ESCALA;
        for (var t : termos(titulo, atributos, extra).entrySet()) {
            int[] cont = contagem.get(t.getKey());
            if (cont == null) continue; // palavra que o modelo nunca viu
            double w = t.getValue() * idfMilesimos.get(t.getKey()) / ESCALA;
            for (int c = 0; c < n; c++) s[c] += w * Math.log((cont[c] + alfa) / (totalPorClasse[c] + alfa * v));
        }
        return s;
    }

    // =====================================================================
    // predicao
    // =====================================================================
    public Predicao prever(String titulo, Map<String, String> atributos) { return prever(titulo, atributos, null); }

    public Predicao prever(String titulo, Map<String, String> atributos, String extra) {
        double[] p = softmax(pontuar(titulo, atributos, extra), temperatura);
        int i = argmax(p);
        return new Predicao(categorias.get(i), p[i]);
    }

    /** Log da probabilidade a posteriori (sem normalizar) de cada classe. */
    private double[] pontuar(String titulo, Map<String, String> atributos, String extra) {
        if (idfMilesimos != null) return pontuarPonderado(titulo, atributos, extra);
        int n = categorias.size();
        double[] s = new double[n];
        double v = contagem.size();
        for (int c = 0; c < n; c++) s[c] = Math.log((docsPorClasse[c] + ALFA) / (totalDocs + ALFA * n));
        for (String f : caracteristicas(titulo, atributos, extra)) {
            int[] cont = contagem.get(f);
            if (cont == null) continue; // palavra que o modelo nunca viu
            for (int c = 0; c < n; c++) s[c] += Math.log((cont[c] + ALFA) / (totalPorClasse[c] + ALFA * v));
        }
        return s;
    }

    static double[] softmax(double[] logits, double temperatura) {
        double max = Double.NEGATIVE_INFINITY;
        for (double l : logits) max = Math.max(max, l);
        double soma = 0;
        double[] p = new double[logits.length];
        for (int i = 0; i < p.length; i++) { p[i] = Math.exp((logits[i] - max) / temperatura); soma += p[i]; }
        for (int i = 0; i < p.length; i++) p[i] /= soma;
        return p;
    }

    private static int argmax(double[] a) {
        int m = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[m]) m = i;
        return m;
    }

    /** Erro de calibracao esperado: media, por faixa de confianca, de |acerto - confianca| ponderada pelo tamanho da faixa. */
    static double ece(List<double[]> logits, List<Integer> certos, double temperatura) {
        final int faixas = 10;
        double[] somaConf = new double[faixas], somaAcerto = new double[faixas];
        int[] n = new int[faixas];
        for (int k = 0; k < logits.size(); k++) {
            double[] p = softmax(logits.get(k), temperatura);
            int pred = argmax(p);
            int b = Math.min(faixas - 1, (int) (p[pred] * faixas));
            somaConf[b] += p[pred];
            somaAcerto[b] += pred == certos.get(k) ? 1 : 0;
            n[b]++;
        }
        double e = 0;
        for (int b = 0; b < faixas; b++) if (n[b] > 0) e += (double) n[b] / logits.size() * Math.abs(somaAcerto[b] / n[b] - somaConf[b] / n[b]);
        return e;
    }

    // =====================================================================
    // caracteristicas
    // =====================================================================
    /** Palavras e bigramas do titulo limpo (presenca, nao contagem) e palavras dos valores dos atributos (prefixo "a:"). */
    static Set<String> caracteristicas(String titulo, Map<String, String> atributos) { return caracteristicas(titulo, atributos, null); }

    /** Com {@code extra} (catalogo v2), tambem as palavras do texto longo, com prefixo "d:". */
    static Set<String> caracteristicas(String titulo, Map<String, String> atributos, String extra) {
        Set<String> f = new LinkedHashSet<>();
        List<String> pal = palavras(new Preparador().limparTitulo(titulo));
        for (int i = 0; i < pal.size(); i++) {
            f.add(pal.get(i));
            if (i + 1 < pal.size()) f.add(pal.get(i) + "_" + pal.get(i + 1));
        }
        if (atributos != null) for (String valor : atributos.values()) for (String p : palavras(valor)) f.add("a:" + p);
        if (extra != null) for (String p : palavras(extra)) f.add("d:" + p);
        return f;
    }

    private static List<String> palavras(String texto) {
        List<String> out = new ArrayList<>();
        for (String t : Texto.normalizar(texto).split("[^a-z0-9]+")) if (t.length() >= 2) out.add(t);
        return out;
    }

    // =====================================================================
    // arquivo
    // =====================================================================
    /**
     * Le o JSONL de treino (id, titulo, atributos, categoria). Linhas com "_meta" sao avisos e sao ignoradas.
     * Recusa arquivos que o proprio agente grava (decisoes, exemplos de revisao) e linhas marcadas como vindas do agente.
     *
     * @param pastaDeDadosDoAgente pasta onde o agente grava seus arquivos; nenhum arquivo dentro dela e aceito como treino
     */
    @SuppressWarnings("unchecked")
    public static List<Exemplo> lerTreino(Path arquivo, Path pastaDeDadosDoAgente, Taxonomia tx) throws IOException {
        Path abs = arquivo.toAbsolutePath().normalize();
        if (pastaDeDadosDoAgente != null && abs.startsWith(pastaDeDadosDoAgente.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("o treino nao pode vir da pasta de dados do agente (" + pastaDeDadosDoAgente + "): so rotulos humanos");
        String nome = abs.getFileName().toString();
        if (nome.startsWith("decisoes-") || nome.startsWith("exemplos-rotulados") || nome.startsWith("votos-"))
            throw new IllegalArgumentException("o arquivo " + nome + " e gravado pelo agente e nao pode ser usado como treino");
        List<Exemplo> out = new ArrayList<>();
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            if (linha.isBlank()) continue;
            Map<String, Object> m = (Map<String, Object>) Json.parse(linha);
            if (m.containsKey("_meta")) continue;
            if ("agente".equals(m.get("fonte")) || "agente".equals(m.get("rotuloPor")))
                throw new IllegalArgumentException("exemplo " + m.get("id") + " foi rotulado pelo agente: so rotulos humanos entram no treino");
            String cat = (String) m.get("categoria");
            if (tx != null && !tx.categoriaValida(cat)) continue; // categoria que nao existe na taxonomia
            Map<String, String> at = new LinkedHashMap<>();
            if (m.get("atributos") instanceof Map<?, ?> a) a.forEach((k, v) -> at.put(String.valueOf(k), String.valueOf(v)));
            out.add(new Exemplo((String) m.get("id"), String.valueOf(m.get("titulo")), at, cat, textoLongo(m)));
        }
        return out;
    }

    /** Destaques e descricao do exemplo (catalogo v2). Nulo quando a linha so tem titulo e atributos (v1). */
    public static String textoLongoDe(br.com.projeto.curadoria.core.Modelos.ProdutoEntrada p) {
        StringBuilder sb = new StringBuilder();
        if (p.bullets() != null) for (String b : p.bullets()) sb.append(b).append(' ');
        if (p.descricao() != null) sb.append(p.descricao().replaceAll("<[^>]+>", " "));
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static String textoLongo(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        if (m.get("bullets") instanceof List<?> b) for (Object x : b) sb.append(x).append(' ');
        if (m.get("descricao") instanceof String d) sb.append(d.replaceAll("<[^>]+>", " "));
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public void salvar(Path arquivo) throws IOException {
        Files.createDirectories(arquivo.toAbsolutePath().getParent());
        Files.write(arquivo, serializar());
    }

    public static ClassificadorTexto carregar(Path arquivo) throws IOException {
        byte[] bytes = Files.readAllBytes(arquivo);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String magia = in.readUTF();
            if (!MAGIA.equals(magia) && !MAGIA_PONDERADO.equals(magia)) throw new IOException("arquivo de modelo desconhecido: " + arquivo);
            boolean ponderado = MAGIA_PONDERADO.equals(magia);
            int nCat = in.readInt();
            List<String> cats = new ArrayList<>();
            for (int i = 0; i < nCat; i++) cats.add(in.readUTF());
            double t = in.readDouble();
            int totalDocs = in.readInt();
            int[] docs = new int[nCat];
            long[] total = new long[nCat];
            for (int i = 0; i < nCat; i++) { docs[i] = in.readInt(); total[i] = in.readLong(); }
            int nCaract = in.readInt();
            Map<String, int[]> cont = new HashMap<>();
            for (int i = 0; i < nCaract; i++) {
                String f = in.readUTF();
                int[] c = new int[nCat];
                int nz = in.readInt();
                for (int k = 0; k < nz; k++) c[in.readShort()] = in.readInt();
                cont.put(f, c);
            }
            Map<String, Integer> idf = null;
            if (ponderado) {
                int nIdf = in.readInt();
                idf = new HashMap<>();
                for (int i = 0; i < nIdf; i++) idf.put(in.readUTF(), in.readInt());
            }
            Relatorio r = new Relatorio(in.readInt(), in.readInt(), in.readInt(), nCaract, t, in.readDouble(), in.readDouble(), in.readDouble(), null);
            String v = hash(bytes);
            return new ClassificadorTexto(cats, cont, total, docs, totalDocs, t,
                    new Relatorio(r.exemplos(), r.treino(), r.calibracao(), r.vocabulario(), r.temperatura(), r.eceAntes(), r.eceDepois(), r.acertoCalibracao(), v), v, idf);
        }
    }

    private byte[] serializar() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bos)) {
            out.writeUTF(idfMilesimos == null ? MAGIA : MAGIA_PONDERADO);
            out.writeInt(categorias.size());
            for (String c : categorias) out.writeUTF(c);
            out.writeDouble(temperatura);
            out.writeInt(totalDocs);
            for (int i = 0; i < categorias.size(); i++) { out.writeInt(docsPorClasse[i]); out.writeLong(totalPorClasse[i]); }
            out.writeInt(contagem.size());
            for (var e : new TreeMap<>(contagem).entrySet()) { // ordem fixa: o mesmo modelo gera sempre a mesma versao
                out.writeUTF(e.getKey());
                int nz = 0;
                for (int v : e.getValue()) if (v != 0) nz++;
                out.writeInt(nz);
                for (int c = 0; c < e.getValue().length; c++) if (e.getValue()[c] != 0) { out.writeShort(c); out.writeInt(e.getValue()[c]); }
            }
            if (idfMilesimos != null) {
                out.writeInt(idfMilesimos.size());
                for (var e : new TreeMap<>(idfMilesimos).entrySet()) { out.writeUTF(e.getKey()); out.writeInt(e.getValue()); }
            }
            Relatorio r = relatorio;
            out.writeInt(r == null ? 0 : r.exemplos());
            out.writeInt(r == null ? 0 : r.treino());
            out.writeInt(r == null ? 0 : r.calibracao());
            out.writeDouble(r == null ? 0 : r.eceAntes());
            out.writeDouble(r == null ? 0 : r.eceDepois());
            out.writeDouble(r == null ? 0 : r.acertoCalibracao());
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(byte[] bytes) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hash curto de um texto, para versionar configuracao e arquivos. */
    static String hashDe(String texto) { return hash(texto.getBytes(StandardCharsets.UTF_8)); }
}
