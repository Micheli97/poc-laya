package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Classificador.Chamada;
import br.com.projeto.curadoria.core.Classificador.Resposta;
import br.com.projeto.curadoria.core.Modelos.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * O agente de curadoria. Cada metodo publico corresponde a um endpoint do servico.
 *
 * Tres jeitos de decidir, escolhidos por configuracao (com tudo desligado, o comportamento e o da rodada 3):
 *  1. EM CADEIA (padrao): preparar -> 1a chamada (departamento, ...) -> 2a chamada (categoria) -> marca por codigo -> validacao
 *     -> decisao por regras (limiar, acordo com o vendedor, segunda opiniao) -> APROVADO ou fila de REVISAO humana;
 *  2. EM CADEIA + MODO AUTONOMO: igual, mas sem revisao humana: APROVADO vira APLICADO e REVISAO vira MANTIDO;
 *  3. POR VOTACAO: sinais independentes (Laya, vendedor, supervisionado, LLM) votam e a {@link PoliticaDecisao} decide.
 * No modo autonomo, na duvida o agente nao mexe: o produto fica MANTIDO com a categoria atual do vendedor e volta no reprocessamento.
 *
 * Estado em memoria (suficiente para a simulacao), exceto o registro de decisoes aplicadas, que fica em arquivo (rollback).
 * Em producao: banco para resultados, fila, auditoria e registro.
 */
public final class AgenteCuradoria implements AutoCloseable {

    private final Taxonomia tx;
    private final Classificador clf;
    private final Preparador preparador;
    private volatile ResolvedorMarca marcas;
    private volatile FichaTecnica ficha;           // so na taxonomia v2
    private final Atributos atributos;
    private final Melhorias mel;
    private final Melhorias melCascata;           // melhorias efetivas da cascata do Laya (na votacao, tipo/voltagem/cor sempre por codigo)
    private final BuscaPorAfinidade busca;
    private final SegundaOpiniao segunda;
    private final boolean aprovarSozinho;
    private final Autonomia aut;
    private final ModeloSupervisionado supervisionado;
    private final RegistroDecisoes registro;
    private final RegistroMantidos mantidosLog;
    private final Path pastaDados;
    private final Path arquivoCalibracao;
    private final AtomicReference<CalibracaoLaya> calibracaoLaya = new AtomicReference<>();
    private final MonitorQualidade monitor;
    private final PoliticaDecisao politica;
    private final String cfgHash;
    private final double limiar;
    private final ExecutorService pool;
    private final Path arquivoExemplos;
    private final Path arquivoVotos;

    // sinais da votacao
    private final Sinal sinalVendedor;
    private final Sinal sinalLaya;
    private final Sinal sinalSupervisionado;     // nulo se desligado
    private final Sinal sinalLlm;                // nulo se a segunda opiniao esta desligada

    private final Map<String, ResultadoCuradoria> resultados = new ConcurrentHashMap<>();
    private final Map<String, String> hashPorId = new ConcurrentHashMap<>();
    private final Map<String, ItemRevisao> fila = new ConcurrentSkipListMap<>(); // ordenada por id: revisao previsivel
    private final Map<String, ProdutoEntrada> entradas = new ConcurrentHashMap<>();
    private final Map<String, List<EventoAuditoria>> auditoria = new ConcurrentHashMap<>();
    private final Map<String, EstadoMantido> mantidosPorId = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> mantidosPorMotivo = new ConcurrentSkipListMap<>();
    private final Map<String, ResultadoSinal> votosEmMemoria = new ConcurrentHashMap<>(); // chave: id|estado|sinal|versao do sinal
    private final AtomicLong processados = new AtomicLong(), aprovados = new AtomicLong(), revisao = new AtomicLong(),
            errosValidacao = new AtomicLong(), confirmados = new AtomicLong(), corrigidos = new AtomicLong(),
            cacheHits = new AtomicLong(), falhas = new AtomicLong(), aplicados = new AtomicLong(), mudaram = new AtomicLong(),
            mantidos = new AtomicLong(), reprocessamentos = new AtomicLong(), rollbacks = new AtomicLong();
    private final long[] latencias = new long[10_000];
    private final AtomicLong nLatencias = new AtomicLong();
    private volatile boolean votosCarregados;

    /** Um produto MANTIDO e o que ja se tentou com ele. */
    private static final class EstadoMantido {
        volatile String motivo;
        volatile String versaoAvaliada;
        volatile int tentativasSemMudanca;   // reprocessos desde a ultima mudanca de versao
        volatile int reprocessos;            // reprocessos no total
        EstadoMantido(String motivo, String versao, int tentativas, int reprocessos) {
            this.motivo = motivo; this.versaoAvaliada = versao; this.tentativasSemMudanca = tentativas; this.reprocessos = reprocessos;
        }
    }

    /** Comportamento original: nenhuma melhoria ligada. */
    public AgenteCuradoria(Taxonomia tx, Classificador clf, double limiar, int threads, Path pastaDados) throws IOException {
        this(tx, clf, limiar, threads, pastaDados, Melhorias.nenhuma(), new BuscaPorAfinidade(tx, List.of(), 20, 0.6));
    }

    public AgenteCuradoria(Taxonomia tx, Classificador clf, double limiar, int threads, Path pastaDados,
                           Melhorias melhorias, BuscaPorAfinidade busca) throws IOException {
        this(tx, clf, limiar, threads, pastaDados, melhorias, busca, SegundaOpiniao.DESLIGADA, false);
    }

    /**
     * @param segunda        segunda opiniao (LLM) para o que iria para a revisao; {@link SegundaOpiniao#DESLIGADA} se nao usa
     * @param aprovarSozinho true: se o LLM discordar do Laya e do vendedor mas disser confianca "alta", aprova mesmo assim
     */
    public AgenteCuradoria(Taxonomia tx, Classificador clf, double limiar, int threads, Path pastaDados,
                           Melhorias melhorias, BuscaPorAfinidade busca, SegundaOpiniao segunda, boolean aprovarSozinho) throws IOException {
        this(tx, clf, limiar, threads, pastaDados, melhorias, busca, segunda, aprovarSozinho, Autonomia.desligada(), null, new MonitorQualidade(0.10, false));
    }

    public AgenteCuradoria(Taxonomia tx, Classificador clf, double limiar, int threads, Path pastaDados,
                           Melhorias melhorias, BuscaPorAfinidade busca, SegundaOpiniao segunda, boolean aprovarSozinho,
                           Autonomia autonomia, ModeloSupervisionado supervisionado, MonitorQualidade monitor) throws IOException {
        this.segunda = segunda;
        this.aprovarSozinho = aprovarSozinho;
        this.tx = tx;
        this.clf = clf;
        this.mel = melhorias;
        this.aut = autonomia;
        this.supervisionado = supervisionado;
        this.monitor = monitor;
        this.preparador = new Preparador(melhorias.vendedorNoState(), tx.tresNiveis());
        this.busca = busca;
        this.marcas = new ResolvedorMarca(tx);
        this.ficha = tx.tresNiveis() ? FichaTecnica.naoInformado(tx) : null;
        if (tx.tresNiveis() && !autonomia.votacaoAtiva())
            throw new IllegalStateException("a taxonomia v2 (3 niveis) exige curadoria.votacao.ativa=true: o fluxo em cadeia so conhece 2 niveis");
        this.atributos = new Atributos(tx);
        this.limiar = limiar;
        this.pool = Executors.newFixedThreadPool(threads);
        Files.createDirectories(pastaDados);
        this.arquivoExemplos = pastaDados.resolve("exemplos-rotulados.jsonl");
        this.arquivoVotos = pastaDados.resolve("votos-golden.jsonl");
        this.registro = new RegistroDecisoes(pastaDados.resolve("decisoes-aplicadas.jsonl"));
        this.pastaDados = pastaDados;
        // os MANTIDO sobrevivem a reinicio: o produto inteiro, o motivo, a versao e as tentativas ficam num JSONL so de acrescimo
        this.mantidosLog = new RegistroMantidos(pastaDados.resolve("mantidos.jsonl"));
        mantidosLog.estado().forEach((id, e) -> {
            mantidosPorId.put(id, new EstadoMantido(e.motivo(), e.versao(), e.tentativasSemMudanca(), e.reprocessos()));
            entradas.put(id, e.produto());
        });
        this.arquivoCalibracao = pastaDados.resolve("calibracao-laya.json");
        if (Files.exists(arquivoCalibracao)) calibracaoLaya.set(CalibracaoLaya.carregar(arquivoCalibracao));
        this.politica = new PoliticaDecisao(autonomia.politica());
        // na votacao o Laya so deve dar a categoria: tipo, voltagem e cor saem por codigo, entao a cascata nao pergunta isso
        this.melCascata = autonomia.votacaoAtiva() ? new Melhorias(melhorias.acordoVendedor(), true, true, melhorias.top2Departamentos(), melhorias.consultaPorAfinidade(),
                melhorias.vendedorNoState(), melhorias.buscaFallbackTexto(), melhorias.limiarTop2(), melhorias.idiomaInstrucoes()) : melhorias;
        this.sinalVendedor = new Sinais.SinalVendedor(autonomia.confiancaVendedor());
        this.sinalLaya = new Sinais.SinalLaya(this::votoLaya);
        this.sinalSupervisionado = autonomia.supervisionadoAtivo() ? new Sinais.SinalSupervisionado(() -> supervisionado == null ? null : supervisionado.get(), tx.tresNiveis()) : null;
        this.sinalLlm = segunda.ativa() && autonomia.usarLlm() ? new Sinais.SinalLlm(segunda, autonomia.llmVeCategoriaDoVendedor()) : null;
        if (autonomia.llmPrincipal() && (sinalLlm == null || !autonomia.votacaoAtiva()))
            throw new IllegalStateException("curadoria.classificador=llm exige curadoria.votacao.ativa=true e curadoria.segunda-opiniao.ativa=true");
        this.cfgHash = ClassificadorTexto.hashDe(autonomia.resumo() + "|" + melhorias.resumo() + "|" + limiar + "|sozinho=" + aprovarSozinho + "|llm=" + segunda.ativa());
    }

    // =====================================================================
    // versao dos sinais: entra no cache, nas decisoes gravadas e no reprocessamento
    // =====================================================================
    /** Agente + modelo supervisionado + taxonomia + configuracao. Se qualquer um mudar, o resultado em cache deixa de valer. */
    public String versaoSinais() {
        return aut.versaoAgente() + "|sup:" + (supervisionado == null ? "-" : supervisionado.versao()) + "|tax:" + tx.versao() + "|cal:" + versaoCalibracao() + "|cfg:" + cfgHash;
    }

    private String versaoCalibracao() { CalibracaoLaya c = calibracaoLaya.get(); return c == null ? "-" : c.versao(); }

    /** Versao de UM sinal: se ela muda, so os votos desse sinal deixam de valer (os outros continuam guardados). */
    private String versaoDoSinal(String nome) {
        return switch (nome) {
            case Sinais.LAYA -> clf.nome() + "|" + melCascata.resumo() + "|cal:" + versaoCalibracao() + (opcaoNenhuma ? "|nenhuma-destas" : "");
            case Sinais.SUPERVISIONADO -> supervisionado == null ? "-" : supervisionado.versao();
            case Sinais.LLM -> segunda.descricao() + "|vendllm:" + aut.llmVeCategoriaDoVendedor();
            default -> "1";
        };
    }

    /** Descricao da versao de todos os sinais configurados, para o relatorio da varredura. */
    private String versaoDosVotos() {
        return aut.versaoAgente() + "|tax:" + tx.versao() + "|" + sinaisConfigurados().stream().map(sn -> sn.nome() + ":" + versaoDoSinal(sn.nome())).collect(Collectors.joining("|"));
    }

    // =====================================================================
    // POST /curadoria/classificar
    // =====================================================================
    public ResultadoCuradoria classificar(ProdutoEntrada p) { return classificar(p, false); }

    private ResultadoCuradoria classificar(ProdutoEntrada p, boolean forcar) {
        validarEntrada(p);
        Preparador.Preparado prep = preparador.preparar(p);
        String versao = versaoSinais();

        // idempotencia: o Jitterbit pode reenviar o mesmo produto; se nada mudou (nem o texto, nem a versao dos sinais), nao gasta modelo.
        // Resultado que foi falha de sinal nao vale como cache: a falha pode ter passado.
        String hash = Integer.toHexString((prep.state() + "\u0001" + versao).hashCode());
        ResultadoCuradoria anterior = resultados.get(p.id());
        if (!forcar && anterior != null && hash.equals(hashPorId.get(p.id())) && !falhaTransitoria(anterior.motivoMantido())) {
            cacheHits.incrementAndGet();
            return copiaCache(anterior);
        }

        long t0 = System.nanoTime();
        String catVendedor = tx.categoriaDoVendedor(p.categoriaVendedor()); // null = vendedor sem sinal
        return aut.votacaoAtiva() ? classificarPorVotacao(p, prep, catVendedor, versao, hash, t0)
                : classificarEmCadeia(p, prep, catVendedor, versao, hash, t0);
    }

    // ---------------------------------------------------------------------
    // jeito 1 e 2: em cadeia (regras), com ou sem modo autonomo
    // ---------------------------------------------------------------------
    private ResultadoCuradoria classificarEmCadeia(ProdutoEntrada p, Preparador.Preparado prep, String catVendedor, String versao, String hash, long t0) {
        Chamada c1;
        Chamada c2;
        Resposta depto;
        Resposta cat;
        Resposta tipo;
        List<String> deptos;
        try {
            // 1a chamada: so o que o codigo nao resolve (com as melhorias 2 e 3 ligadas, so o departamento)
            c1 = clf.decidir(prep.state(), tx.perguntasNivel1(mel), p.id() + "#1");
            depto = c1.respostas().get("departamento");
            if (!tx.departamentoValido(depto.valor()))
                return concluirEmCadeia(p, prep, catVendedor, versao, hash, t0, new Desfecho(Modelos.Status.ERRO_VALIDACAO, null,
                        "departamento invalido: " + depto.valor(), "validacao_departamento", null, null, List.of(), false, null));
            // 2a chamada: categoria entre as do departamento (ou dos 2 mais provaveis, se o 1o for incerto)
            deptos = departamentosParaCategoria(depto, mel);
            c2 = clf.decidir(prep.state(), tx.perguntasCategoria(deptos, mel), p.id() + "#2");
            cat = c2.respostas().get("categoria");
            tipo = mel.tipoPorCategoria() ? tipoDaCategoria(p, prep, cat.valor()) : c1.respostas().get("tipo_item");
        } catch (RuntimeException e) {
            falhas.incrementAndGet();
            if (aut.modoAutonomo()) // modelo fora do ar, timeout...: nunca aplica; o produto fica como esta e volta no reprocessamento
                return concluirEmCadeia(p, prep, catVendedor, versao, hash, t0, new Desfecho(Modelos.Status.REVISAO, null,
                        "modelo indisponivel: " + e.getClass().getSimpleName(), "modelo_indisponivel", null, null, List.of(), false, "modelo-indisponivel"));
            throw e;
        }

        boolean categoriaOk = tx.categoriaValida(cat.valor()) && deptos.contains(tx.departamentoDe(cat.valor()));
        // o departamento final e o da categoria escolhida (no modo top-2 pode ser o 2o departamento)
        String deptoFinal = categoriaOk ? tx.departamentoDe(cat.valor()) : depto.valor();
        double confDepto = deptoFinal.equals(depto.valor()) ? depto.confianca() : depto.probabilidades().getOrDefault(deptoFinal, 0.0);
        String voltagem = mel.atributosPorCodigo() ? atributos.voltagem(prep.tituloLimpo(), p.atributos(), cat.valor()) : c1.respostas().get("voltagem").valor();
        String cor = mel.atributosPorCodigo() ? atributos.cor(prep.tituloLimpo(), p.atributos(), cat.valor()) : c1.respostas().get("cor").valor();
        Classificacao cl = new Classificacao(deptoFinal, arred(confDepto), cat.valor(), arred(cat.confianca()),
                tipo.valor(), arred(tipo.confianca()), voltagem, cor, marcas.resolver(p.marcaInformada(), p.titulo()));

        // validacao de saida + decisao de negocio
        Modelos.Status status;
        String motivo;
        String regra;
        double conf = Math.min(confDepto, cat.confianca());
        if (!categoriaOk) {
            status = Modelos.Status.ERRO_VALIDACAO;
            motivo = "categoria " + cat.valor() + " nao pertence ao departamento " + depto.valor();
            regra = "validacao_categoria";
        } else if (mel.acordoVendedor()) {
            if (catVendedor != null && catVendedor.equals(cat.valor())) {
                status = Modelos.Status.APROVADO;
                motivo = "acordo vendedor + modelo (" + tx.nomeDaCategoria(cat.valor()) + ")";
                regra = "acordo_vendedor_modelo";
            } else if (catVendedor != null) {
                status = Modelos.Status.REVISAO;
                motivo = "vendedor e modelo discordam: vendedor " + tx.nomeDaCategoria(catVendedor) + ", modelo " + tx.nomeDaCategoria(cat.valor());
                regra = "discordancia_vendedor_modelo";
            } else if (conf >= limiar) {
                status = Modelos.Status.APROVADO;
                motivo = "vendedor sem sinal e confianca acima de " + limiar;
                regra = "sem_sinal_vendedor_confianca";
            } else {
                status = Modelos.Status.REVISAO;
                motivo = String.format(Locale.ROOT, "vendedor sem sinal e confianca baixa (departamento %.2f, categoria %.2f)", confDepto, cat.confianca());
                regra = "sem_sinal_vendedor_confianca_baixa";
            }
        } else if (conf >= limiar) {
            status = Modelos.Status.APROVADO;
            motivo = "confianca acima de " + limiar;
            regra = "limiar_confianca";
        } else {
            status = Modelos.Status.REVISAO;
            motivo = String.format(Locale.ROOT, "confianca baixa (departamento %.2f, categoria %.2f)", confDepto, cat.confianca());
            regra = "confianca_baixa";
        }

        List<Voto> votos = new ArrayList<>();
        if (categoriaOk) votos.add(new Voto(Sinais.LAYA, cat.valor(), arred(conf), true));
        if (catVendedor != null) votos.add(new Voto(Sinais.VENDEDOR, catVendedor, aut.confiancaVendedor(), false));

        // segunda opiniao: so para o que iria para a revisao humana
        String catLlm = null;
        boolean llmFalhou = false;
        if (segunda.ativa() && (status == Modelos.Status.REVISAO || status == Modelos.Status.ERRO_VALIDACAO)) {
            String catLaya = categoriaOk ? cat.valor() : null;
            Optional<SegundaOpiniao.Opiniao> op;
            try {
                op = segunda.consultar(new SegundaOpiniao.Pedido(p, prep.tituloLimpo(), catLaya, catVendedor));
            } catch (RuntimeException e) { // o contrato diz que nao lanca; se lancar, a segunda opiniao nunca derruba o fluxo
                op = Optional.empty();
            }
            if (op.isPresent()) { // sem resposta valida (falha, timeout, JSON ruim): segue como antes (revisao, ou MANTIDO no modo autonomo)
                catLlm = op.get().categoria();
                votos.add(new Voto(Sinais.LLM, catLlm, Sinais.confiancaDoLlm(op.get().confianca()), true));
                boolean concorda = catLlm.equals(catVendedor) || catLlm.equals(catLaya);
                boolean sozinho = !concorda && aprovarSozinho && "alta".equals(op.get().confianca());
                if (concorda || sozinho) {
                    status = Modelos.Status.APROVADO;
                    regra = concorda ? "segunda-opiniao-acordo" : "segunda-opiniao-sozinho";
                    motivo = concorda ? "segunda opiniao concorda com " + (catLlm.equals(catLaya) ? "o modelo" : "o vendedor") + " (" + tx.nomeDaCategoria(catLlm) + ")"
                            : "segunda opiniao decidiu sozinha (" + tx.nomeDaCategoria(catLlm) + ", confianca alta)";
                    if (!catLlm.equals(catLaya)) cl = recompor(cl, catLlm, op.get().confianca(), p, prep);
                } else {
                    status = Modelos.Status.REVISAO;
                    regra = "segunda-opiniao-sem-acordo";
                    motivo = "segunda opiniao (" + tx.nomeDaCategoria(catLlm) + ") nao confirma o modelo nem o vendedor";
                }
            } else {
                llmFalhou = true;
            }
        }
        return concluirEmCadeia(p, prep, catVendedor, versao, hash, t0,
                new Desfecho(status, cl, motivo, regra, catLlm, categoriaOk ? cat.valor() : null, votos, llmFalhou, null));
    }

    /** O que a cadeia decidiu, antes de virar resultado (que depende do modo autonomo). */
    private record Desfecho(Modelos.Status status, Classificacao cl, String motivo, String regra, String catLlm, String catLaya,
                            List<Voto> votos, boolean llmFalhou, String motivoMantidoForcado) {}

    private ResultadoCuradoria concluirEmCadeia(ProdutoEntrada p, Preparador.Preparado prep, String catVendedor, String versao, String hash, long t0, Desfecho d) {
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (!aut.modoAutonomo()) {
            ResultadoCuradoria r = new ResultadoCuradoria(p.id(), d.status(), d.cl(), p.categoriaVendedor(), prep.tituloLimpo(), d.motivo(), ms, clf.nome(), false,
                    catVendedor, d.regra(), d.catLlm(), d.catLaya());
            return guardar(p, prep, r, hash, d.cl());
        }
        // MODO AUTONOMO: nao ha revisao humana. Aprovado vira APLICADO; tudo o mais fica MANTIDO com a categoria do vendedor.
        boolean aprovado = d.status() == Modelos.Status.APROVADO;
        String proposta = aprovado ? d.cl().categoria() : null;
        String motivoMantido = null;
        if (aprovado && !noCanario(d.cl().departamento())) { aprovado = false; motivoMantido = "fora-do-canario"; }
        if (!aprovado && motivoMantido == null) motivoMantido = motivoMantidoDaCadeia(d);
        return resultadoAutonomo(p, prep, catVendedor, versao, hash, ms, aprovado, aprovado ? d.cl() : null, d.regra(), d.motivo(), motivoMantido,
                d.votos(), d.catLlm(), d.catLaya(), proposta);
    }

    private static String motivoMantidoDaCadeia(Desfecho d) {
        if (d.motivoMantidoForcado() != null) return d.motivoMantidoForcado();
        if (d.llmFalhou()) return "falha-sinal:llm";
        return switch (d.regra()) {
            case "validacao_departamento", "validacao_categoria" -> "erro-validacao";
            case "discordancia_vendedor_modelo", "segunda-opiniao-sem-acordo" -> "discordancia";
            case "confianca_baixa", "sem_sinal_vendedor_confianca_baixa" -> "confianca-baixa";
            default -> "sinais-insuficientes";
        };
    }

    // ---------------------------------------------------------------------
    // jeito 3: por votacao de sinais independentes
    // ---------------------------------------------------------------------
    private ResultadoCuradoria classificarPorVotacao(ProdutoEntrada p, Preparador.Preparado prep, String catVendedor, String versao, String hash, long t0) {
        Sinal.Contexto ctx = new Sinal.Contexto(p, prep, catVendedor);
        List<ResultadoSinal> rs = new ArrayList<>();
        if (!aut.llmPrincipal() && aut.usarLaya()) rs.add(sinalLaya.votar(ctx));
        rs.add(sinalVendedor.votar(ctx));
        if (sinalSupervisionado != null) rs.add(sinalSupervisionado.votar(ctx));
        PoliticaDecisao.Decisao d;
        if (aut.llmPrincipal()) { // LLM como classificador principal: sempre chamado, com as 58 categorias numa chamada
            rs.add(sinalLlm.votar(ctx));
            d = politica.decidir(rs);
        } else {
            d = politica.decidir(rs);
            // o LLM (caro) so entra se os sinais baratos nao decidiram, e nao adianta chamar se algum deles quebrou
            if (!d.aplicar() && sinalLlm != null && rs.stream().noneMatch(r -> r instanceof ResultadoSinal.Falha)) {
                rs.add(sinalLlm.votar(ctx));
                d = politica.decidir(rs);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        String catLaya = votoDe(rs, Sinais.LAYA), catLlm = votoDe(rs, Sinais.LLM);
        boolean aplicar = d.aplicar();
        String proposta = aplicar ? d.categoria() : null;
        String motivoMantido = null;
        if (aplicar && !noCanario(tx.departamentoDe(d.categoria()))) { aplicar = false; motivoMantido = "fora-do-canario"; }
        if (!aplicar && motivoMantido == null) motivoMantido = motivoMantidoDaVotacao(d, rs);
        String regra = d.aplicar() ? d.motivo() : "votacao:mantido:" + d.motivo();
        String motivo = (d.aplicar() ? "aplicar " + tx.nomeDaCategoria(d.categoria()) + " (" : "manter (" + (motivoMantido == null ? d.motivo() : motivoMantido) + "; ") + d.descreverVotos() + ")";

        if (!aut.modoAutonomo()) { // votacao sem modo autonomo: o que nao decide vai para a revisao humana, como antes
            Classificacao cl = classificacaoPara(p, prep, aplicar ? d.categoria() : (d.categoria() != null ? d.categoria() : catVendedor), aplicar ? d.confiancaMedia() : 0);
            ResultadoCuradoria r = new ResultadoCuradoria(p.id(), aplicar ? Modelos.Status.APROVADO : Modelos.Status.REVISAO, cl, p.categoriaVendedor(), prep.tituloLimpo(), motivo, ms,
                    clf.nome(), false, catVendedor, regra, catLlm, catLaya, aplicar ? null : motivoMantido, null, d.votos(), versao, catVendedor, proposta);
            return guardar(p, prep, r, hash, cl);
        }
        Classificacao cl = aplicar ? classificacaoPara(p, prep, d.categoria(), d.confiancaMedia()) : null;
        return resultadoAutonomo(p, prep, catVendedor, versao, hash, ms, aplicar, cl, regra, motivo, motivoMantido, d.votos(), catLlm, catLaya, proposta);
    }

    private static String votoDe(List<ResultadoSinal> rs, String sinal) {
        for (ResultadoSinal r : rs) if (r instanceof ResultadoSinal.ComVoto c && c.sinal().equals(sinal)) return c.voto().categoria();
        return null;
    }

    private static String motivoMantidoDaVotacao(PoliticaDecisao.Decisao d, List<ResultadoSinal> rs) {
        if (PoliticaDecisao.SINAIS_INSUFICIENTES.equals(d.motivo()))
            for (ResultadoSinal r : rs) if (r instanceof ResultadoSinal.SemVoto s && s.sinal().equals(Sinais.LAYA) && s.motivo().startsWith("erro-validacao")) return "erro-validacao";
        return d.motivo();
    }

    /** Saida da cascata do Laya: a categoria e a confianca BRUTA, ou o motivo de nao haver voto valido. */
    private record LayaBruto(String categoria, double confianca, String semVotoMotivo) {}

    private LayaBruto cascataLaya(Preparador.Preparado prep, ProdutoEntrada p) {
        if (tx.tresNiveis()) return cascataLaya3Niveis(prep, p);
        Chamada c1 = clf.decidir(prep.state(), tx.perguntasNivel1(melCascata), p.id() + "#1");
        Resposta depto = c1.respostas().get("departamento");
        if (!tx.departamentoValido(depto.valor())) return new LayaBruto(null, 0, "erro-validacao-departamento");
        List<String> deptos = departamentosParaCategoria(depto, melCascata);
        Chamada c2 = clf.decidir(prep.state(), tx.perguntasCategoria(deptos, melCascata), p.id() + "#2");
        Resposta cat = c2.respostas().get("categoria");
        if (!tx.categoriaValida(cat.valor()) || !deptos.contains(tx.departamentoDe(cat.valor()))) return new LayaBruto(null, 0, "erro-validacao-categoria");
        String deptoFinal = tx.departamentoDe(cat.valor());
        double confDepto = deptoFinal.equals(depto.valor()) ? depto.confianca() : depto.probabilidades().getOrDefault(deptoFinal, 0.0);
        return new LayaBruto(cat.valor(), arred(Math.min(confDepto, cat.confianca())), null);
    }

    /**
     * Taxonomia v2: departamento, categoria e subcategoria, uma pergunta curta por nivel (no maximo 16, 12 e 10 opcoes). O top-2 vale so
     * no departamento: quando ele e incerto, a pergunta de categoria oferece as categorias dos 2 departamentos mais provaveis.
     * A confianca e a menor das tres.
     */
    private LayaBruto cascataLaya3Niveis(Preparador.Preparado prep, ProdutoEntrada p) {
        Chamada c1 = clf.decidir(prep.state(), tx.perguntasNivel1(melCascata), p.id() + "#1");
        Resposta depto = c1.respostas().get("departamento");
        if (!tx.departamentoValido(depto.valor())) return new LayaBruto(null, 0, "erro-validacao-departamento");
        List<String> deptos = departamentosParaCategoria(depto, melCascata);
        Chamada c2 = clf.decidir(prep.state(), tx.perguntasGrupo(deptos, melCascata), p.id() + "#2");
        Resposta grupo = c2.respostas().get("categoria");
        if (!tx.grupoValido(grupo.valor()) || !deptos.contains(tx.departamentoDoGrupo(grupo.valor()))) return new LayaBruto(null, 0, "erro-validacao-categoria");
        Chamada c3 = clf.decidir(prep.state(), tx.perguntasFolha(grupo.valor(), melCascata, opcaoNenhuma, null), p.id() + "#3");
        Resposta folha = c3.respostas().get("subcategoria");
        if (opcaoNenhuma && Taxonomia.NENHUMA_DESTAS.equals(folha.valor())) return new LayaBruto(null, 0, "nenhuma-destas"); // nao e voto nem erro: o produto pode estar fora da taxonomia
        if (!tx.categoriaValida(folha.valor()) || !grupo.valor().equals(tx.grupoDe(folha.valor()))) return new LayaBruto(null, 0, "erro-validacao-subcategoria");
        String deptoFinal = tx.departamentoDoGrupo(grupo.valor());
        double confDepto = deptoFinal.equals(depto.valor()) ? depto.confianca() : depto.probabilidades().getOrDefault(deptoFinal, 0.0);
        return new LayaBruto(folha.valor(), arred(Math.min(confDepto, Math.min(grupo.confianca(), folha.confianca()))), null);
    }

    /** Descricao da estrategia de ficha tecnica e do que ela gastou, para as metricas (so na taxonomia v2). */
    private String usoDaFicha() {
        FichaTecnica f = ficha;
        if (f == null) return "";
        if (f instanceof FichaComModelo m) { var u = m.uso(); return " ficha=" + m.nome() + "(produtos=" + u.produtos() + " comPendentes=" + u.produtosComPendentes() + " chamadas=" + u.chamadas() + " pendentes=" + u.atributosPendentes() + " resolvidosPeloModelo=" + u.atributosResolvidos() + " falhas=" + u.falhas() + " descartadosSemEvidencia=" + u.descartadosSemEvidencia() + ")"; }
        return " ficha=" + f.nome();
    }

    /**
     * v2 com a opcao "nenhuma destas" ligada: o Laya ajustado, sozinho, diz que o produto nao e nenhuma das subcategorias da categoria que escolheu.
     * Nulo se a opcao esta desligada, se a cascata falhou ou se o Laya nao deu resposta valida.
     */
    public Boolean layaDisseNenhumaDestas(ProdutoEntrada p) {
        if (!opcaoNenhuma || !tx.tresNiveis()) return null;
        try {
            LayaBruto b = cascataLaya(preparador.preparar(p), p);
            if ("nenhuma-destas".equals(b.semVotoMotivo())) return true;
            return b.semVotoMotivo() == null ? Boolean.FALSE : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** v2: a pergunta de subcategoria do Laya leva a opcao "nenhuma destas" (so para checkpoints ajustados com esses exemplos). */
    private volatile boolean opcaoNenhuma;

    public void definirOpcaoNenhuma(boolean v) { this.opcaoNenhuma = v; }

    public ResolvedorMarca resolvedorDeMarca() { return marcas; }

    /** Troca o resolvedor de marca (v2: lexico montado a partir do catalogo). */
    public void definirResolvedorDeMarca(ResolvedorMarca resolvedor) { this.marcas = Objects.requireNonNull(resolvedor); }

    /** Troca a estrategia da ficha tecnica (so na taxonomia v2). */
    public void definirFichaTecnica(FichaTecnica f) { if (tx.tresNiveis()) this.ficha = Objects.requireNonNull(f); }

    /**
     * O voto do Laya: a cascata de duas chamadas, com a confianca calibrada pela temperatura (se POST /curadoria/laya/calibrar ja rodou).
     * Categoria invalida nao e voto (sem voto, "erro-validacao"); excecao e falha.
     */
    private ResultadoSinal votoLaya(Sinal.Contexto ctx) {
        LayaBruto b = cascataLaya(ctx.preparado(), ctx.produto());
        if (b.semVotoMotivo() != null) return ResultadoSinal.semVoto(Sinais.LAYA, b.semVotoMotivo());
        CalibracaoLaya cal = calibracaoLaya.get();
        double conf = cal == null ? b.confianca() : arred(cal.calibrar(b.confianca()));
        return ResultadoSinal.voto(Sinais.LAYA, b.categoria(), conf, true, b.confianca());
    }

    // ---------------------------------------------------------------------
    // montagem do resultado do modo autonomo
    // ---------------------------------------------------------------------
    private ResultadoCuradoria resultadoAutonomo(ProdutoEntrada p, Preparador.Preparado prep, String catVendedor, String versao, String hash, long ms,
                                                 boolean aplicado, Classificacao clAplicada, String regra, String motivo, String motivoMantido,
                                                 List<Voto> votos, String catLlm, String catLaya, String proposta) {
        Classificacao cl = aplicado ? clAplicada : classificacaoPara(p, prep, catVendedor, 0, false); // mantido: categoria atual do vendedor (ou nula)
        String anterior = registro.categoriaAtualAplicada(p.id()) != null && aplicado ? registro.categoriaAtualAplicada(p.id()) : catVendedor;
        Boolean mudou = aplicado ? !Objects.equals(clAplicada.categoria(), catVendedor) : null;
        ResultadoCuradoria r = new ResultadoCuradoria(p.id(), aplicado ? Modelos.Status.APLICADO : Modelos.Status.MANTIDO, cl, p.categoriaVendedor(), prep.tituloLimpo(),
                motivo, ms, clf.nome(), false, catVendedor, regra, catLlm, catLaya, aplicado ? null : motivoMantido, mudou, votos, versao, anterior, proposta);
        return guardar(p, prep, r, hash, cl);
    }

    /** Classificacao completa para uma categoria: departamento da taxonomia e tipo, voltagem e cor por codigo. Nula se nao ha categoria. */
    private Classificacao classificacaoPara(ProdutoEntrada p, Preparador.Preparado prep, String categoria, double confianca) { return classificacaoPara(p, prep, categoria, confianca, true); }

    /** @param comFicha false: nao monta a ficha tecnica (v2); o MANTIDO fica com a categoria do vendedor e a ficha dele nunca e usada, entao nao gasta modelo */
    private Classificacao classificacaoPara(ProdutoEntrada p, Preparador.Preparado prep, String categoria, double confianca, boolean comFicha) {
        if (categoria == null || !tx.categoriaValida(categoria)) // v2: sem categoria, mas a marca nao depende dela e sai do mesmo jeito
            return tx.tresNiveis() ? new Classificacao(null, 0, null, 0, null, 0, null, null, marcas.resolver(p), null) : null;
        String tipo = tx.tipoPadrao(categoria);
        if (tx.tresNiveis()) // v2: voltagem e cor sao atributos da ficha tecnica, nao campos proprios
            return new Classificacao(tx.departamentoDe(categoria), arred(confianca), categoria, arred(confianca), tipo, tipo == null ? 0 : 1.0,
                    null, null, marcas.resolver(p), comFicha ? ficha.extrair(p, prep, categoria) : null);
        return new Classificacao(tx.departamentoDe(categoria), arred(confianca), categoria, arred(confianca), tipo, tipo == null ? 0 : 1.0,
                atributos.voltagem(prep.tituloLimpo(), p.atributos(), categoria), atributos.cor(prep.tituloLimpo(), p.atributos(), categoria),
                marcas.resolver(p.marcaInformada(), p.titulo()));
    }

    private boolean noCanario(String departamento) { return aut.canario().isEmpty() || aut.canario().contains(departamento); }

    /**
     * Categoria final diferente da que o Laya escolheu (o LLM deu razao ao vendedor, ou decidiu sozinho):
     * departamento, tipo, voltagem e cor acompanham a nova categoria. A confianca vira a do LLM (alta 0.9, media 0.7, baixa 0.5).
     * Sem as melhorias de codigo ligadas, tipo/voltagem/cor ficam como o Laya respondeu.
     */
    private Classificacao recompor(Classificacao cl, String categoria, String confianca, ProdutoEntrada p, Preparador.Preparado prep) {
        double c = Sinais.confiancaDoLlm(confianca);
        boolean tipoDoCodigo = mel.tipoPorCategoria() && tx.tipoPadrao(categoria) != null;
        return new Classificacao(tx.departamentoDe(categoria), c, categoria, c,
                tipoDoCodigo ? tx.tipoPadrao(categoria) : cl.tipoItem(), tipoDoCodigo ? 1.0 : cl.confTipo(),
                mel.atributosPorCodigo() ? atributos.voltagem(prep.tituloLimpo(), p.atributos(), categoria) : cl.voltagem(),
                mel.atributosPorCodigo() ? atributos.cor(prep.tituloLimpo(), p.atributos(), categoria) : cl.cor(), cl.marca());
    }

    /** O departamento escolhido e, no modo top-2 com departamento incerto, o 2o mais provavel. */
    private List<String> departamentosParaCategoria(Resposta depto, Melhorias m) {
        List<String> l = new ArrayList<>(List.of(depto.valor()));
        if (m.top2Departamentos() && depto.confianca() < m.limiarTop2()) {
            depto.probabilidades().entrySet().stream()
                    .filter(e -> !e.getKey().equals(depto.valor()) && tx.departamentoValido(e.getKey()))
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).ifPresent(l::add);
        }
        return l;
    }

    /** Tipo vindo da categoria (codigo). So pergunta ao modelo se a categoria tem tipo misto (tipo_padrao nulo). */
    private Resposta tipoDaCategoria(ProdutoEntrada p, Preparador.Preparado prep, String categoria) {
        if (!tx.categoriaValida(categoria)) return new Resposta(null, 0);
        String padrao = tx.tipoPadrao(categoria);
        if (padrao != null) return new Resposta(padrao, 1.0);
        return clf.decidir(prep.state(), Map.of("tipo_item", tx.perguntaTipo(mel.ingles())), p.id() + "#3").respostas().get("tipo_item");
    }

    // =====================================================================
    // POST /curadoria/lote
    // =====================================================================
    public LoteResposta lote(LoteRequest req) {
        if (req == null || req.produtos() == null || req.produtos().isEmpty()) throw new IllegalArgumentException("lote vazio");
        if (req.produtos().size() > 1000) throw new IllegalArgumentException("maximo de 1000 produtos por lote");
        long t0 = System.currentTimeMillis();
        List<Future<ResultadoCuradoria>> futuros = req.produtos().stream().map(p -> pool.submit(() -> classificar(p))).toList();
        List<ResultadoCuradoria> ok = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        for (int i = 0; i < futuros.size(); i++) {
            try {
                ok.add(futuros.get(i).get());
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof Classificador.ModeloIndisponivelException mi) { futuros.forEach(f -> f.cancel(true)); throw mi; }
                erros.add(req.produtos().get(i).id() + ": " + c.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrompido");
            }
        }
        int ap = (int) ok.stream().filter(r -> r.status() == Modelos.Status.APROVADO).count();
        int rv = (int) ok.stream().filter(r -> r.status() == Modelos.Status.REVISAO).count();
        int ev = (int) ok.stream().filter(r -> r.status() == Modelos.Status.ERRO_VALIDACAO).count();
        int pl = (int) ok.stream().filter(r -> r.status() == Modelos.Status.APLICADO).count();
        int mt = (int) ok.stream().filter(r -> r.status() == Modelos.Status.MANTIDO).count();
        return new LoteResposta(req.loteId(), req.produtos().size(), ap, rv, ev, erros.size(), System.currentTimeMillis() - t0, ok, erros, pl, mt);
    }

    // =====================================================================
    // POST /curadoria/consulta  (a busca tambem usa o agente)
    // =====================================================================
    public ConsultaResposta consulta(String q) {
        if (q == null || q.isBlank()) throw new IllegalArgumentException("consulta vazia");
        if (tx.tresNiveis()) return new ConsultaResposta(q, null, null, null, 0, "texto"); // v2: o entendimento de consulta chega na etapa de busca
        // 1o sinonimo, 2o afinidade aprendida do catalogo; o modelo so entra como ultimo recurso
        if (mel.consultaPorAfinidade()) {
            Optional<BuscaPorAfinidade.Resolucao> r = busca.porSinonimo(q).or(() -> busca.porAfinidade(q));
            if (r.isPresent()) {
                var x = r.get();
                return new ConsultaResposta(q, tx.departamentoDe(x.categoria()), x.categoria(), x.tipo(), x.confianca(), x.origem());
            }
        }
        // 3o texto: o modelo sai do caminho da busca. Sem categoria, quem busca usa o casamento de palavras de sempre
        if (mel.buscaFallbackTexto()) return new ConsultaResposta(q, null, null, null, 0, "texto");
        String state = "Busca do cliente: " + q.trim();
        Chamada c1 = clf.decidir(state, tx.perguntasConsulta(mel), "consulta:" + q.trim());
        String depto = c1.respostas().get("departamento").valor();
        Resposta tipo1 = c1.respostas().get("tipo_item");
        if (!tx.departamentoValido(depto)) return new ConsultaResposta(q, depto, null, tipo1 == null ? null : tipo1.valor(), 0, "modelo");
        Chamada c2 = clf.decidir(state, tx.perguntasCategoria(List.of(depto), mel), "consulta:" + q.trim() + "#2");
        Resposta cat = c2.respostas().get("categoria");
        String tipo = mel.tipoPorCategoria() ? tx.tipoPadrao(cat.valor()) : tipo1.valor();
        return new ConsultaResposta(q, depto, cat.valor(), tipo,
                arred(Math.min(c1.respostas().get("departamento").confianca(), cat.confianca())), "modelo");
    }

    // =====================================================================
    // GET /curadoria/revisao  e  POST /curadoria/revisao/{id}
    // =====================================================================
    public List<ItemRevisao> fila(int limite) {
        return fila.values().stream().limit(Math.max(1, Math.min(limite, 500))).toList();
    }

    public ResultadoCuradoria revisar(String id, DecisaoRevisao d) {
        ItemRevisao item = fila.get(id);
        if (item == null) throw new NoSuchElementException("produto " + id + " nao esta na fila de revisao");
        if (d == null || !tx.categoriaValida(d.categoria())) throw new IllegalArgumentException("categoria invalida: " + (d == null ? null : d.categoria()));
        String tipo = tx.tipoValido(d.tipoItem()) ? d.tipoItem() : sugestao(item, Classificacao::tipoItem);
        String volt = tx.voltagemValida(d.voltagem()) ? d.voltagem() : sugestao(item, Classificacao::voltagem);
        String cor = tx.corValida(d.cor()) ? d.cor() : sugestao(item, Classificacao::cor);

        Classificacao antes = item.sugestao().classificacao();
        boolean igual = antes != null && Objects.equals(antes.categoria(), d.categoria()) && Objects.equals(antes.tipoItem(), tipo);
        Modelos.Status st = igual ? Modelos.Status.CONFIRMADO : Modelos.Status.CORRIGIDO;
        (igual ? confirmados : corrigidos).incrementAndGet();

        Classificacao nova = new Classificacao(tx.departamentoDe(d.categoria()), 1.0, d.categoria(), 1.0, tipo, 1.0, volt, cor,
                antes == null ? marcas.resolver(item.produto()) : antes.marca());
        String revisor = d.revisor() == null || d.revisor().isBlank() ? "anonimo" : d.revisor();
        ResultadoCuradoria r = new ResultadoCuradoria(id, st, nova, item.produto().categoriaVendedor(), item.sugestao().tituloLimpo(),
                (igual ? "confirmado por " : "corrigido por ") + revisor, 0, "humano", false, item.sugestao().categoriaSugeridaVendedor(), "humano",
                item.sugestao().categoriaSugeridaLlm(), item.sugestao().categoriaSugeridaLaya());
        resultados.put(id, r);
        fila.remove(id);
        auditar(id, st.name(), (igual ? "" : "de " + (antes == null ? "-" : antes.categoria()) + " para ") + d.categoria() + " por " + revisor);
        salvarExemplo(item, nova, revisor);
        busca.aprender(id, item.sugestao().tituloLimpo(), d.categoria());
        return r;
    }

    // =====================================================================
    // POST /curadoria/reprocessar
    // =====================================================================
    /**
     * Reprocessa os produtos MANTIDO. Um MANTIDO so e reavaliado quando algo mudou (versao dos sinais) ou quando o motivo foi uma
     * falha passageira (Laya ou LLM fora do ar). Falha sem mudanca de versao e tentada ate {@code reprocessamentoMaximo} vezes; depois
     * disso o agente para de insistir ate a versao mudar.
     *
     * @param limite quantos reprocessar nesta chamada (nulo = todos os elegiveis)
     * @param motivo se informado, so os MANTIDO cujo motivo comeca com ele (ex.: "falha-sinal")
     */
    public ReprocessamentoResposta reprocessar(Integer limite, String motivo) {
        String versaoAtual = versaoSinais();
        int candidatos = 0, feitos = 0, ap = 0, mt = 0, semMudanca = 0, porLimite = 0;
        int teto = limite == null ? Integer.MAX_VALUE : Math.max(0, limite);
        for (Map.Entry<String, EstadoMantido> e : new ArrayList<>(mantidosPorId.entrySet())) {
            EstadoMantido est = e.getValue();
            if (motivo != null && !motivo.isBlank() && (est.motivo == null || !est.motivo.startsWith(motivo))) continue;
            candidatos++;
            boolean versaoMudou = !versaoAtual.equals(est.versaoAvaliada);
            if (!versaoMudou) {
                if (!falhaTransitoria(est.motivo)) { semMudanca++; continue; }                 // nada mudou: reavaliar daria o mesmo resultado
                if (est.tentativasSemMudanca >= aut.reprocessamentoMaximo()) { porLimite++; continue; } // ja tentou o bastante
            }
            if (feitos >= teto) continue;
            ProdutoEntrada p = entradas.get(e.getKey());
            if (p == null) continue;
            int reproc = est.reprocessos + 1;
            int tentativas = versaoMudou ? 0 : est.tentativasSemMudanca + 1;
            reprocessamentos.incrementAndGet();
            auditar(e.getKey(), "REPROCESSADO", "tentativa " + reproc + (versaoMudou ? " (versao dos sinais mudou)" : " (motivo anterior: " + est.motivo + ", " + tentativas + " sem mudanca de versao)"));
            ResultadoCuradoria r = classificar(p, true);
            EstadoMantido novo = mantidosPorId.get(e.getKey());
            if (novo != null) {
                novo.reprocessos = reproc;
                novo.tentativasSemMudanca = tentativas;
                mantidosLog.registrar(new RegistroMantidos.Estado(e.getKey(), novo.motivo, novo.versaoAvaliada, tentativas, reproc, p)); // as tentativas sobrevivem a reinicio
            }
            feitos++;
            if (r.status() == Modelos.Status.APLICADO) ap++; else mt++;
        }
        return new ReprocessamentoResposta(candidatos, feitos, ap, mt, semMudanca, porLimite, versaoAtual);
    }

    private static boolean falhaTransitoria(String motivoMantido) {
        return motivoMantido != null && (motivoMantido.startsWith(PoliticaDecisao.FALHA_SINAL) || motivoMantido.equals("modelo-indisponivel"));
    }

    // =====================================================================
    // POST /curadoria/rollback
    // =====================================================================
    /**
     * Desfaz decisoes APLICADO que mudaram a categoria, filtradas por categoria, departamento e/ou versao do agente. Devolve a lista
     * para o Jitterbit reverter na VTEX (a categoriaAnterior volta a valer). Os produtos ficam MANTIDO com motivo "rollback".
     */
    public RollbackResposta rollback(RollbackRequest req) {
        if (req == null || (blank(req.categoria()) && blank(req.departamento()) && blank(req.versao())))
            throw new IllegalArgumentException("informe pelo menos um filtro: categoria, departamento ou versao");
        String motivo = blank(req.motivo()) ? "rollback manual" : req.motivo();
        List<RegistroDecisoes.Evento> alvo = registro.candidatosAoRollback(e ->
                (blank(req.categoria()) || req.categoria().equals(e.categoriaNova()))
                && (blank(req.departamento()) || req.departamento().equals(e.departamentoNova()))
                && (blank(req.versao()) || (e.versaoAgente() != null && e.versaoAgente().startsWith(req.versao()))));
        List<RollbackItem> itens = new ArrayList<>();
        for (RegistroDecisoes.Evento e : alvo) {
            registro.registrarRollback(e, motivo);
            rollbacks.incrementAndGet();
            itens.add(new RollbackItem(e.id(), e.categoriaAnterior(), e.categoriaNova()));
            auditar(e.id(), "ROLLBACK", "de " + e.categoriaNova() + " de volta para " + e.categoriaAnterior() + " (" + motivo + ")");
            ResultadoCuradoria atual = resultados.get(e.id());
            ProdutoEntrada p = entradas.get(e.id());
            if (atual != null && p != null) {
                Classificacao cl = classificacaoPara(p, preparador.preparar(p), e.categoriaAnterior(), 0);
                resultados.put(e.id(), new ResultadoCuradoria(e.id(), Modelos.Status.MANTIDO, cl, atual.categoriaVendedor(), atual.tituloLimpo(), "rollback: " + motivo, 0, atual.modelo(),
                        false, atual.categoriaSugeridaVendedor(), "rollback", atual.categoriaSugeridaLlm(), atual.categoriaSugeridaLaya(), "rollback", null,
                        atual.votos(), atual.versaoAgente(), e.categoriaNova(), atual.categoriaProposta()));
                mantidosPorId.put(e.id(), new EstadoMantido("rollback", versaoSinais(), 0, 0)); // so volta a ser avaliado quando a versao mudar
                mantidosLog.registrar(new RegistroMantidos.Estado(e.id(), "rollback", versaoSinais(), 0, 0, p));
            }
        }
        return new RollbackResposta(itens.size(), itens);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    // =====================================================================
    // POST /curadoria/sinais-busca  (monitor de qualidade)
    // =====================================================================
    public SinaisBuscaResposta sinaisBusca(SinaisBuscaRequest req) {
        if (req == null || req.categorias() == null || req.categorias().isEmpty()) throw new IllegalArgumentException("categorias vazias");
        String tipo = req.tipo() == null ? "" : req.tipo();
        if (tipo.equals("linha-de-base")) {
            monitor.definirLinhaDeBase(req.categorias());
            auditar("_monitor", "MONITOR", "linha de base definida para " + req.categorias().size() + " categorias");
            return new SinaisBuscaResposta(tipo, req.categorias().size(), List.of());
        }
        if (!tipo.equals("atual")) throw new IllegalArgumentException("tipo precisa ser \"linha-de-base\" ou \"atual\"");
        List<MonitorQualidade.Acao> acoes;
        try {
            acoes = monitor.avaliar(req.categorias(), cat -> rollback(new RollbackRequest(cat, null, null, "monitor de qualidade: " + cat)).total());
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        for (MonitorQualidade.Acao a : acoes)
            auditar("_monitor", "MONITOR", String.format(Locale.ROOT, "%s: %s caiu %.0f%% (%.3f -> %.3f)%s", a.categoria(), a.metrica(), a.variacao() * 100, a.base(), a.atual(),
                    a.rollbackDisparado() ? " | rollback: " + a.produtosDesfeitos() + " produtos desfeitos" : " | rollback automatico desligado"));
        return new SinaisBuscaResposta(tipo, req.categorias().size(), acoes);
    }

    // =====================================================================
    // POST /curadoria/supervisionado/treinar
    // =====================================================================
    public ClassificadorTexto.Relatorio treinarSupervisionado() throws IOException { return treinarSupervisionado(null); }

    /**
     * @param arquivo se informado, treina com ele SEM salvar o modelo no disco (so para avaliacao, ex.: treino reduzido com 200 exemplos);
     *                exige curadoria.avaliacao.ativa=true. Para voltar ao modelo oficial, chame sem arquivo.
     */
    public ClassificadorTexto.Relatorio treinarSupervisionado(String arquivo) throws IOException {
        if (supervisionado == null) throw new IllegalArgumentException("o classificador supervisionado nao esta configurado (curadoria.supervisionado.ativo)");
        boolean alternativo = arquivo != null && !arquivo.isBlank();
        if (alternativo && !aut.avaliacaoAtiva()) throw new IllegalArgumentException("treinar com outro arquivo so e permitido com curadoria.avaliacao.ativa=true");
        ClassificadorTexto.Relatorio r = alternativo ? supervisionado.treinar(Path.of(arquivo), false) : supervisionado.treinar();
        auditar("_supervisionado", "TREINO", String.format(Locale.ROOT, "versao %s: %d exemplos%s, ECE %.3f -> %.3f (T=%.1f)", r.versao(), r.exemplos(),
                alternativo ? " (arquivo alternativo, nao salvo: " + arquivo + ")" : "", r.eceAntes(), r.eceDepois(), r.temperatura()));
        return r;
    }

    // =====================================================================
    // POST /curadoria/laya/calibrar
    // =====================================================================
    /**
     * Calibra a confianca do voto do Laya com UMA temperatura, no conjunto de TREINO (rotulos humanos): roda a cascata do Laya em ate
     * {@code limite} exemplos (padrao 500, sorteados com semente fixa), ajusta a temperatura, mostra o ECE antes e depois e grava em
     * dados-agente/calibracao-laya.json. A partir dai o voto do Laya usa a confianca calibrada (e a versao dos sinais muda).
     * Os exemplos de treino nao tem a categoria do vendedor, entao a calibracao so vale com {@code vendedor-no-state=false}
     * (o Laya nao ve a categoria do vendedor); com true o relatorio traz um aviso.
     */
    public CalibracaoLaya.Relatorio calibrarLaya(Integer limite) throws IOException {
        if (supervisionado == null || supervisionado.arquivoTreino() == null) throw new IllegalArgumentException("sem arquivo de treino configurado (curadoria.supervisionado.arquivo-treino)");
        if (!aut.usarLaya() || aut.llmPrincipal()) throw new IllegalArgumentException("o Laya nao vota nesta configuracao: nada a calibrar");
        List<ClassificadorTexto.Exemplo> todos = new ArrayList<>(ClassificadorTexto.lerTreino(supervisionado.arquivoTreino(), pastaDados, tx));
        Collections.shuffle(todos, new Random(7));
        List<ClassificadorTexto.Exemplo> amostra = todos.subList(0, Math.min(todos.size(), limite == null ? 500 : Math.max(30, limite)));
        List<Future<double[]>> futuros = new ArrayList<>();
        for (ClassificadorTexto.Exemplo ex : amostra) {
            futuros.add(pool.submit(() -> {
                ProdutoEntrada p = new ProdutoEntrada(ex.id(), null, ex.titulo(), null, null, null, null, ex.atributos());
                try {
                    LayaBruto b = cascataLaya(preparador.preparar(p), p);
                    return b.semVotoMotivo() != null ? null : new double[]{b.confianca(), b.categoria().equals(ex.categoria()) ? 1 : 0};
                } catch (RuntimeException e) {
                    return null; // Laya fora do ar neste exemplo: nao entra na calibracao
                }
            }));
        }
        List<double[]> pares = new ArrayList<>();
        for (Future<double[]> f : futuros) {
            try { double[] d = f.get(); if (d != null) pares.add(d); }
            catch (ExecutionException e) { /* exemplo perdido */ }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrompido"); }
        }
        double[] confs = new double[pares.size()];
        boolean[] certos = new boolean[pares.size()];
        for (int i = 0; i < pares.size(); i++) { confs[i] = pares.get(i)[0]; certos[i] = pares.get(i)[1] == 1; }
        String aviso = mel.vendedorNoState() ? "ATENCAO: com vendedor-no-state=true o Laya ve a categoria do vendedor em producao, mas os exemplos de treino nao tem essa pista; esta calibracao so vale com vendedor-no-state=false." : null;
        CalibracaoLaya c = CalibracaoLaya.ajustar(confs, certos, amostra.size() - pares.size(), aviso);
        c.salvar(arquivoCalibracao);
        calibracaoLaya.set(c);
        auditar("_laya", "CALIBRACAO", String.format(Locale.ROOT, "T=%.2f em %d exemplos (acerto do Laya %.1f%%): ECE %.3f -> %.3f", c.temperatura(), c.relatorio().amostras(), c.relatorio().acertoLaya() * 100, c.relatorio().eceAntes(), c.relatorio().eceDepois()));
        return c.relatorio();
    }

    // =====================================================================
    // POST /curadoria/politica/varrer  (so para avaliacao: curadoria.avaliacao.ativa=true)
    // =====================================================================
    /**
     * Avalia os produtos UMA vez, com todos os sinais (inclusive o LLM, sem lazy), e aplica a politica real para cada combinacao de
     * parametros da grade. Os votos ficam em dados-agente/votos-golden.jsonl com a versao dos sinais: repetir a varredura com os
     * mesmos sinais reaproveita o arquivo e nao chama o Laya nem o LLM de novo.
     */
    public VarreduraResposta varrer(VarreduraRequest req) {
        if (!aut.avaliacaoAtiva()) throw new NoSuchElementException("a avaliacao esta desativada (curadoria.avaliacao.ativa=false)");
        if (req == null || req.produtos() == null || req.produtos().isEmpty() || req.grade() == null || req.grade().isEmpty()) throw new IllegalArgumentException("produtos e grade sao obrigatorios");
        carregarVotosGravados();
        List<PoliticaDecisao> politicas = req.grade().stream().map(PoliticaDecisao::new).toList();
        List<Sinal> sinais = sinaisConfigurados();
        AtomicLong reaproveitados = new AtomicLong();
        List<Future<VarreduraItem>> futuros = new ArrayList<>();
        for (ProdutoEntrada p : req.produtos()) {
            validarEntrada(p);
            futuros.add(pool.submit(() -> {
                Preparador.Preparado prep = preparador.preparar(p);
                String catV = tx.categoriaDoVendedor(p.categoriaVendedor());
                Sinal.Contexto ctx = new Sinal.Contexto(p, prep, catV);
                String base = p.id() + "|" + Integer.toHexString(prep.state().hashCode()) + "|";
                List<ResultadoSinal> rs = new ArrayList<>();
                boolean tudoDoArquivo = true;
                for (Sinal sn : sinais) { // cada sinal tem a sua versao: trocar o supervisionado nao refaz os votos do Laya nem do LLM
                    String versao = versaoDoSinal(sn.nome());
                    String chave = base + sn.nome() + "|" + versao;
                    ResultadoSinal r = votosEmMemoria.get(chave);
                    if (r == null) {
                        tudoDoArquivo = false;
                        r = sn.votar(ctx);
                        if (!(r instanceof ResultadoSinal.Falha)) { votosEmMemoria.put(chave, r); gravarVoto(chave, p.id(), versao, r); } // falha pode ser passageira: nao guarda
                    }
                    rs.add(r);
                }
                if (tudoDoArquivo) reaproveitados.incrementAndGet();
                List<DecisaoVarrida> ds = new ArrayList<>();
                for (PoliticaDecisao pol : politicas) {
                    PoliticaDecisao.Decisao d = pol.decidir(rs);
                    ds.add(new DecisaoVarrida(d.aplicar(), d.categoria(), d.motivo(), d.confiancaMedia()));
                }
                return new VarreduraItem(p.id(), catV,
                        rs.stream().filter(r -> r instanceof ResultadoSinal.ComVoto).map(r -> ((ResultadoSinal.ComVoto) r).voto()).toList(),
                        rs.stream().filter(r -> r instanceof ResultadoSinal.SemVoto).map(r -> r.sinal() + ":" + ((ResultadoSinal.SemVoto) r).motivo()).toList(),
                        rs.stream().filter(r -> r instanceof ResultadoSinal.Falha).map(r -> r.sinal() + ":" + ((ResultadoSinal.Falha) r).motivo()).toList(), ds);
            }));
        }
        List<VarreduraItem> itens = new ArrayList<>();
        for (Future<VarreduraItem> f : futuros) {
            try { itens.add(f.get()); }
            catch (ExecutionException e) { throw new IllegalStateException("falha na varredura: " + e.getCause(), e.getCause()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrompido"); }
        }
        return new VarreduraResposta(versaoDosVotos(), itens.size(), (int) reaproveitados.get(), itens);
    }

    /** Os sinais configurados, na ordem em que votam. A varredura chama todos, sem economizar (o LLM inclusive). */
    private List<Sinal> sinaisConfigurados() {
        List<Sinal> sinais = new ArrayList<>();
        if (!aut.llmPrincipal() && aut.usarLaya()) sinais.add(sinalLaya);
        sinais.add(sinalVendedor);
        if (sinalSupervisionado != null) sinais.add(sinalSupervisionado);
        if (sinalLlm != null) sinais.add(sinalLlm);
        return sinais;
    }

    private synchronized void carregarVotosGravados() {
        if (votosCarregados) return;
        votosCarregados = true;
        if (!Files.exists(arquivoVotos)) return;
        try {
            for (String linha : Files.readAllLines(arquivoVotos, StandardCharsets.UTF_8)) {
                if (linha.isBlank()) continue;
                try {
                    Map<String, Object> m = Json.mapa(Json.parse(linha));
                    if (m.get("sinal") == null) continue; // formato antigo (um registro por produto): ignora
                    String sinal = (String) m.get("sinal");
                    ResultadoSinal r = "voto".equals(m.get("tipo"))
                            ? ResultadoSinal.voto(sinal, (String) m.get("categoria"), ((Number) m.get("confianca")).doubleValue(), Boolean.TRUE.equals(m.get("modelo")),
                                    m.get("bruta") instanceof Number n ? n.doubleValue() : null)
                            : ResultadoSinal.semVoto(sinal, (String) m.get("motivo"));
                    votosEmMemoria.put((String) m.get("chave"), r);
                } catch (RuntimeException linhaRuim) { /* linha truncada: ignora */ }
            }
        } catch (IOException e) {
            System.err.println("Nao consegui ler " + arquivoVotos + ": " + e.getMessage());
        }
    }

    /** Um voto por linha, com a versao do sinal: so votos e sem-voto sao guardados (falha pode ter sido passageira). */
    private synchronized void gravarVoto(String chave, String id, String versao, ResultadoSinal r) {
        String linha;
        if (r instanceof ResultadoSinal.ComVoto c)
            linha = "{\"chave\":" + Json.str(chave) + ",\"id\":" + Json.str(id) + ",\"sinal\":" + Json.str(c.voto().sinal()) + ",\"versao\":" + Json.str(versao) + ",\"tipo\":\"voto\",\"categoria\":"
                    + Json.str(c.voto().categoria()) + ",\"confianca\":" + c.voto().confianca() + ",\"bruta\":" + (c.voto().confiancaBruta() == null ? "null" : c.voto().confiancaBruta())
                    + ",\"modelo\":" + c.voto().modelo() + "}\n";
        else if (r instanceof ResultadoSinal.SemVoto sv)
            linha = "{\"chave\":" + Json.str(chave) + ",\"id\":" + Json.str(id) + ",\"sinal\":" + Json.str(sv.sinal()) + ",\"versao\":" + Json.str(versao) + ",\"tipo\":\"semVoto\",\"motivo\":" + Json.str(sv.motivo()) + "}\n";
        else return;
        try { Files.writeString(arquivoVotos, linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { System.err.println("Nao consegui gravar votos: " + e.getMessage()); }
    }

    // =====================================================================
    // GET /curadoria/metricas  e  GET /curadoria/produtos/{id}/historico
    // =====================================================================
    public Metricas metricas() {
        long n = Math.min(nLatencias.get(), latencias.length);
        long[] l = Arrays.copyOf(latencias, (int) n);
        Arrays.sort(l);
        long proc = processados.get();
        Map<String, Long> porMotivo = new LinkedHashMap<>();
        mantidosPorMotivo.forEach((k, v) -> porMotivo.put(k, v.get()));
        long porFalha = porMotivo.entrySet().stream().filter(e -> falhaTransitoria(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        ClassificadorTexto.Relatorio sup = supervisionado == null ? null : supervisionado.relatorio();
        CalibracaoLaya calLaya = calibracaoLaya.get();
        return new Metricas(proc, aprovados.get(), revisao.get(), errosValidacao.get(), confirmados.get(), corrigidos.get(),
                fila.size(), cacheHits.get(), falhas.get(), n == 0 ? 0 : l[(int) (n / 2)], n == 0 ? 0 : l[(int) (n * 0.95)],
                proc == 0 ? 0 : arred((double) aprovados.get() / proc), clf.nome(), limiar,
                mel.resumo() + (segunda.ativa() ? " segunda-opiniao" + (aprovarSozinho ? "(sozinho)" : "") : "")
                        + (aut.modoAutonomo() ? " modo-autonomo" : "") + (aut.votacaoAtiva() ? " votacao(" + aut.politica().resumo() + ")" : "")
                        + (aut.llmPrincipal() ? " llm-principal" : "") + (aut.supervisionadoAtivo() ? " supervisionado" : "")
                        + (aut.canario().isEmpty() ? "" : " canario" + new TreeSet<>(aut.canario())) + usoDaFicha(),
                segunda.uso().chamadas.get(), segunda.uso().falhas.get(), segunda.uso().tokensEntrada.get(), segunda.uso().tokensSaida.get(),
                Math.round(segunda.uso().custo.sum() * 1e6) / 1e6, segunda.uso().ultimoErro,
                aut.modoAutonomo(), aut.votacaoAtiva(), aplicados.get(), mudaram.get(), mantidos.get(), porFalha, porMotivo, reprocessamentos.get(), rollbacks.get(),
                versaoSinais(), supervisionado == null ? null : supervisionado.versao(), sup == null ? null : sup.eceAntes(), sup == null ? null : sup.eceDepois(),
                segunda.ativa() ? segunda.descricao() : null, segunda.uso().tentativas429.get(),
                calLaya == null ? null : calLaya.temperatura(), calLaya == null ? null : calLaya.relatorio().eceAntes(), calLaya == null ? null : calLaya.relatorio().eceDepois(),
                calLaya == null ? null : calLaya.relatorio().amostras());
    }

    public List<EventoAuditoria> historico(String id) {
        List<EventoAuditoria> h = auditoria.get(id);
        if (h == null) throw new NoSuchElementException("produto " + id + " nunca foi processado");
        synchronized (h) { return List.copyOf(h); }
    }

    public Optional<ResultadoCuradoria> resultado(String id) { return Optional.ofNullable(resultados.get(id)); }

    @Override
    public void close() { pool.shutdownNow(); }

    // =====================================================================
    // internos
    // =====================================================================
    /** Guarda o resultado e atualiza contadores, fila, registro de decisoes e produtos MANTIDO. */
    private ResultadoCuradoria guardar(ProdutoEntrada p, Preparador.Preparado prep, ResultadoCuradoria r, String hash, Classificacao cl) {
        Modelos.Status st = r.status();
        resultados.put(p.id(), r);
        hashPorId.put(p.id(), hash);
        entradas.put(p.id(), p);
        processados.incrementAndGet();
        switch (st) {
            case APROVADO -> { aprovados.incrementAndGet(); fila.remove(p.id()); if (cl != null) busca.aprender(p.id(), prep.tituloLimpo(), cl.categoria()); }
            case REVISAO -> { revisao.incrementAndGet(); fila.put(p.id(), new ItemRevisao(p.id(), p, r, Instant.now().toString())); }
            case ERRO_VALIDACAO -> { errosValidacao.incrementAndGet(); fila.put(p.id(), new ItemRevisao(p.id(), p, r, Instant.now().toString())); }
            case APLICADO -> {
                aplicados.incrementAndGet();
                boolean mudou = Boolean.TRUE.equals(r.mudouCategoria());
                if (mudou) mudaram.incrementAndGet();
                mantidosPorId.remove(p.id());
                fila.remove(p.id());
                registro.registrarAplicado(p.id(), r.categoriaAnterior(), cl.categoria(), cl.departamento(), mudou, r.versaoAgente(), r.regraDecisao());
                busca.aprender(p.id(), prep.tituloLimpo(), cl.categoria());
            }
            case MANTIDO -> {
                mantidos.incrementAndGet();
                String motivo = r.motivoMantido() == null ? "desconhecido" : r.motivoMantido();
                mantidosPorMotivo.computeIfAbsent(motivo, k -> new AtomicLong()).incrementAndGet();
                EstadoMantido antigo = mantidosPorId.get(p.id());
                EstadoMantido novo = new EstadoMantido(motivo, r.versaoAgente(), antigo == null ? 0 : antigo.tentativasSemMudanca, antigo == null ? 0 : antigo.reprocessos);
                mantidosPorId.put(p.id(), novo);
                mantidosLog.registrar(new RegistroMantidos.Estado(p.id(), motivo, r.versaoAgente(), novo.tentativasSemMudanca, novo.reprocessos, p));
                fila.remove(p.id());
            }
            default -> { }
        }
        if (st != Modelos.Status.MANTIDO) { mantidosPorId.remove(p.id()); mantidosLog.registrarRemovido(p.id()); } // deixou de ser mantido (no log so grava se estava la)
        latencias[(int) (nLatencias.getAndIncrement() % latencias.length)] = r.latenciaMs();
        String votos = r.votos() == null || r.votos().isEmpty() ? "" : " · votos: " + r.votos().stream().map(v -> v.sinal() + ":" + v.categoria() + "(" + v.confianca() + ")").collect(Collectors.joining(" "));
        auditar(p.id(), st.name(), (cl == null ? "" : cl.categoria() + " / " + cl.tipoItem() + " · ") + r.motivo() + " · regra: " + r.regraDecisao()
                + (r.motivoMantido() == null ? "" : " · motivoMantido: " + r.motivoMantido()) + votos);
        return r;
    }

    private void auditar(String id, String evento, String detalhe) {
        List<EventoAuditoria> h = auditoria.computeIfAbsent(id, k -> new ArrayList<>());
        synchronized (h) { h.add(new EventoAuditoria(Instant.now().toString(), evento, detalhe)); }
    }

    /** Cada correcao humana vira um exemplo rotulado: material para ajustar (fine-tuning) o modelo depois. */
    private void salvarExemplo(ItemRevisao item, Classificacao c, String revisor) {
        String state = preparador.preparar(item.produto()).state();
        String linha = "{\"id\":" + Json.str(item.id()) + ",\"state\":" + Json.str(state) + ",\"rotulos\":{\"departamento\":" + Json.str(c.departamento())
                + ",\"categoria\":" + Json.str(c.categoria()) + ",\"tipo_item\":" + Json.str(c.tipoItem()) + ",\"voltagem\":" + Json.str(c.voltagem())
                + ",\"cor\":" + Json.str(c.cor()) + "},\"sugestao_do_modelo\":" + Json.str(item.sugestao().classificacao() == null ? null : item.sugestao().classificacao().categoria())
                + ",\"revisor\":" + Json.str(revisor) + ",\"quando\":" + Json.str(Instant.now().toString()) + "}\n";
        synchronized (this) {
            try { Files.writeString(arquivoExemplos, linha, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException e) { System.err.println("Nao consegui salvar exemplo rotulado: " + e.getMessage()); }
        }
    }

    private static void validarEntrada(ProdutoEntrada p) {
        if (p == null) throw new IllegalArgumentException("produto ausente");
        if (p.id() == null || p.id().isBlank()) throw new IllegalArgumentException("produto sem id");
        if (p.titulo() == null || p.titulo().isBlank()) throw new IllegalArgumentException("produto " + p.id() + " sem titulo");
    }

    private ResultadoCuradoria copiaCache(ResultadoCuradoria r) {
        return new ResultadoCuradoria(r.id(), r.status(), r.classificacao(), r.categoriaVendedor(), r.tituloLimpo(), r.motivo(), 0, r.modelo(), true,
                r.categoriaSugeridaVendedor(), r.regraDecisao(), r.categoriaSugeridaLlm(), r.categoriaSugeridaLaya(),
                r.motivoMantido(), r.mudouCategoria(), r.votos(), r.versaoAgente(), r.categoriaAnterior(), r.categoriaProposta());
    }

    private static String sugestao(ItemRevisao item, java.util.function.Function<Classificacao, String> f) {
        return item.sugestao().classificacao() == null ? null : f.apply(item.sugestao().classificacao());
    }

    private static double arred(double v) { return Math.round(v * 1000) / 1000.0; }
}
