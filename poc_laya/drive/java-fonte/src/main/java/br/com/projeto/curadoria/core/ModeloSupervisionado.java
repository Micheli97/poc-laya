package br.com.projeto.curadoria.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Guarda o classificador supervisionado em uso: carrega o modelo salvo na subida, treina a partir do arquivo de treino
 * (so rotulos humanos) e troca o modelo sem reiniciar o servico.
 */
public final class ModeloSupervisionado {

    private final Taxonomia tx;
    private final Path arquivoTreino;     // pode ser nulo
    private final Path arquivoModelo;     // dados-agente/modelo-supervisionado.bin
    private final Path pastaDadosDoAgente;
    private final long semente;
    private final AtomicReference<ClassificadorTexto> atual = new AtomicReference<>();

    public ModeloSupervisionado(Taxonomia tx, Path arquivoTreino, Path pastaDadosDoAgente, long semente) {
        this.tx = tx;
        this.arquivoTreino = arquivoTreino;
        this.pastaDadosDoAgente = pastaDadosDoAgente;
        this.arquivoModelo = pastaDadosDoAgente.resolve("modelo-supervisionado.bin");
        this.semente = semente;
    }

    /** Na subida: usa o modelo salvo; se nao houver e existir arquivo de treino, treina; senao fica sem modelo (o sinal falha, e o produto fica MANTIDO). */
    public void carregarOuTreinar() throws IOException {
        if (Files.exists(arquivoModelo)) { atual.set(ClassificadorTexto.carregar(arquivoModelo)); return; }
        if (arquivoTreino != null && Files.exists(arquivoTreino)) treinar();
    }

    /** Retreina com o arquivo de treino, salva e passa a usar o modelo novo. */
    public ClassificadorTexto.Relatorio treinar() throws IOException { return treinar(arquivoTreino, true); }

    /**
     * Treina com um arquivo, passa a usar o modelo e, se {@code salvar}, grava em dados-agente/modelo-supervisionado.bin.
     * Treinar com outro arquivo SEM salvar serve para avaliacao (ex.: so 200 exemplos) sem trocar o modelo oficial no disco.
     */
    public ClassificadorTexto.Relatorio treinar(Path arquivo, boolean salvar) throws IOException {
        if (arquivo == null || !Files.exists(arquivo))
            throw new IllegalArgumentException("arquivo de treino nao encontrado: " + arquivo + " (curadoria.supervisionado.arquivo-treino)");
        List<ClassificadorTexto.Exemplo> exemplos = ClassificadorTexto.lerTreino(arquivo, pastaDadosDoAgente, tx);
        ClassificadorTexto.Treinado t = ClassificadorTexto.treinar(exemplos, semente);
        if (salvar) t.modelo().salvar(arquivoModelo);
        atual.set(t.modelo());
        return t.relatorio();
    }

    public Path arquivoTreino() { return arquivoTreino; }

    public ClassificadorTexto get() { return atual.get(); }

    public String versao() { ClassificadorTexto m = atual.get(); return m == null ? "-" : m.versao(); }

    public ClassificadorTexto.Relatorio relatorio() { ClassificadorTexto m = atual.get(); return m == null ? null : m.relatorio(); }
}
