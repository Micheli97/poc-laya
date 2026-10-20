// Avaliador de referência do catálogo v2. Mede as previsões de qualquer classificador
// (Laya, LLM, supervisionado, votação) sempre do mesmo jeito.
//
// Uso:
//   node avaliacao/avaliar.mjs saneamento --pred previsoes.jsonl [--ids golden|teste_rodadas|validacao]
//   node avaliacao/avaliar.mjs novos      --pred previsoes.jsonl [--ids novos_teste]
//   node avaliacao/avaliar.mjs busca      --pred previsoes.jsonl
//   node avaliacao/avaliar.mjs busca      --baseline-texto            (busca por palavra, como hoje)
//   node avaliacao/avaliar.mjs busca      --pred x.jsonl --incluir-novos  (catálogo + itens novos válidos)
//   node avaliacao/avaliar.mjs saneamento --baseline-vendedor --ids golden
//   node avaliacao/avaliar.mjs busca      --baseline-filtros          (navegação por categoria e filtros no catálogo como chegou)
//   node avaliacao/avaliar.mjs busca      --catalogo-previsto sane.jsonl  (mesma navegação no catálogo organizado)
//   --ids todos (todos os produtos do arquivo) ou --ids-arquivo lista.json (lista de ids)
//   --dados <pasta> para avaliar outra base gerada (ex.: --semente 20261013 --ruido 1.5)
//
// Rodada 8 (bases geradas com --rodada8; em bases antigas nada disto aparece e a saída não muda):
//   node avaliacao/avaliar.mjs arvore     --pred nos.jsonl [--ids teste|treino|todos]   (diagnóstico dos nós da árvore legada)
//   node avaliacao/avaliar.mjs arvore     --baseline-legado-nome | --baseline-legado    (mapeia o nó pelo nome | mapeamento perfeito, o teto)
//   node avaliacao/avaliar.mjs grupos     --pred grupos.jsonl [--ids ...]               (duplicados e famílias de variação no catálogo existente)
//   node avaliacao/avaliar.mjs saneamento --baseline-legado-nome | --baseline-legado    (o nó legado do produto vira a previsão; ver README)
//   O saneamento ganha o bloco "rodada8" (erros de atributo por tipo, estragados, filtro indevido, nó legado, ambiguidade);
//   o modo novos ganha variações, duplicados por grupo e motivos de erro, fora da taxonomia (departamento e nome) e limítrofes;
//   a busca ganha os tipos f2, sem_resposta e com_modelo.
//
// Formato das previsões da rodada 8:
//   arvore:      {"id":"L0042","tipo":"categoria|duplicado|atributo|lixo","destino":"fogoes"|null,"atributo":{"chave":"cor","valor":"verde"}|null}
//   grupos:      {"id":"P0000007","duplicado_de":"P0000003"|null,"variacao_de":"P0000002"|null}
//   saneamento:  além dos campos acima, "subcategoria_secundaria":"aspiradores_auto" (ambiguidade); atributo ausente, "nao_informado" ou
//                "nao_se_aplica" = fora do filtro
//   novos:       além dos campos acima, "variacao_de":"P0000002"|null, "eixo":"cor"|null, "departamento_provavel":"esporte", "nome_sugerido":"Drones"
//   busca:       além dos campos acima, "aviso":"nenhum_resultado" (consulta sem resposta), "corrigida":true (consulta com correção de digitação)
//
// Formato das previsões (uma linha JSON por item):
//   saneamento: {"id":"P0000001","status":"APLICADO|MANTIDO","subcategoria":"fogoes","marca":"Brastemp","atributos":{"voltagem":"220V",...}}
//   novos:      {"id":"N000001","subcategoria":"fogoes"|null,"fora_da_taxonomia":false,"duplicado_de":"P0000123"|null,"invalido":false}
//   busca:      {"id":"Q0001","subcategorias":["fogoes"],"filtros":{"numero_bocas":"5"},"marca":null}   (entendimento da consulta)
//          ou   {"id":"Q0001","resultados":["P0000001","P0000002",...]}                                (lista ranqueada)
// Valores de atributo: use o valor canônico da taxonomia, "nao_informado" ou "nao_se_aplica".
// Com --json, imprime o resultado em JSON (para o CompararRodadas).

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import readline from 'node:readline'

const [modo, ...resto] = process.argv.slice(2)
const args = {}
for (let i = 0; i < resto.length; i++) if (resto[i].startsWith('--')) { const k = resto[i].slice(2); const v = resto[i + 1] && !resto[i + 1].startsWith('--') ? resto[++i] : 'true'; args[k] = v }
const DADOS = args.dados || path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'dados')
if (!['saneamento', 'novos', 'busca', 'arvore', 'grupos'].includes(modo)) { console.error('Modo: saneamento | novos | busca | arvore | grupos'); process.exit(1) }

async function lerJsonl(arq, filtro) { const out = new Map(); const rl = readline.createInterface({ input: fs.createReadStream(arq) }); for await (const l of rl) { if (!l.trim()) continue; const o = JSON.parse(l); if (!filtro || filtro(o)) out.set(o.id, o) } return out }
const norm = s => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
const pct = (a, b) => b ? Math.round(a / b * 1000) / 10 : null
const splits = JSON.parse(fs.readFileSync(path.join(DADOS, 'splits.json')))
const tax = JSON.parse(fs.readFileSync(path.join(DADOS, 'taxonomia.json')))
const FOLHA = {}
for (const d of tax.departamentos) for (const c of d.categorias) for (const f of c.subcategorias) FOLHA[f.id] = { ...f, dep: d.id, cat: c.id }
function mesmoValor(prev, real) {
  if (prev === undefined || prev === null) return false
  if (typeof real === 'number') { const n = Number(String(prev).replace(',', '.')); return Number.isFinite(n) && Math.abs(n - real) <= Math.max(0.011, Math.abs(real) * 0.01) }
  return norm(prev) === norm(real)
}
const saida = r => { if (args.json) console.log(JSON.stringify(r, null, 2)); else imprimir(r) }
function imprimir(obj, pre = '') { for (const [k, v] of Object.entries(obj)) { if (v && typeof v === 'object' && !Array.isArray(v)) { console.log(pre + k + ':'); imprimir(v, pre + '  ') } else console.log(`${pre}${k}: ${Array.isArray(v) ? JSON.stringify(v) : v}`) } }

// Rodada 8: texto de um valor -> valor canônico da taxonomia (pelos sinônimos publicados)
function canon(chave, t) {
  const sin = tax.sinonimos_valor?.[chave]
  if (sin) for (const [k, lista] of Object.entries(sin)) if (lista.map(norm).includes(norm(t))) return k
  return t
}
const especial = v => v === undefined || v === null || v === 'nao_informado' || v === 'nao_se_aplica'
const lerJson = arq => JSON.parse(fs.readFileSync(path.join(DADOS, arq)))
let _arvore
function arvore() { // nós da árvore legada (sem gabarito) e o gabarito dos nós; só existem em bases geradas com --rodada8
  if (_arvore) return _arvore
  const legada = new Map(lerJson('arvore_legada.json').map(n => [n.id, n]))
  const gab = new Map(lerJson('arvore_gabarito.json').map(n => [n.id, n]))
  return (_arvore = { legada, gab })
}
// linha de base realista: o nó legado vira a subcategoria pelo nome (como um mapeamento manual de nomes), sem saber o que é duplicado, atributo ou lixo
const destinoPorNome = no => mapearVendedor(no.nome) || mapearVendedor(no.caminho)

// Mapeia a categoria informada pelo vendedor para uma subcategoria (só para a linha de base "vendedor").
function mapearVendedor(txt) {
  const t = norm(txt); if (!t) return null
  let melhor = null, tam = 0
  for (const f of Object.values(FOLHA)) for (const nome of [f.nome, f.caminho]) { const n = norm(nome); if (t.includes(n) && n.length > tam) { melhor = f.id; tam = n.length } }
  return melhor
}

// ---------------------------------------------------------------------------
if (modo === 'saneamento') {
  const todos = (args.ids || 'golden') === 'todos'
  const ids = todos ? null : new Set(args['ids-arquivo'] ? JSON.parse(fs.readFileSync(args['ids-arquivo'])) : splits[args.ids || 'golden'])
  const prods = await lerJsonl(path.join(DADOS, 'produtos.jsonl'), o => todos || ids.has(o.id))
  let prev
  if (args['baseline-vendedor']) { prev = new Map(); for (const p of prods.values()) prev.set(p.id, { id: p.id, status: 'APLICADO', subcategoria: mapearVendedor(p.categoria_vendedor), marca: p.marca_informada || null, atributos: {} }) }
  else if (args['baseline-legado-nome'] || args['baseline-legado']) {
    // o nó legado em que o produto está pendurado vira a previsão. Nome: mapeia pelo nome do nó. Legado: mapeamento perfeito (usa o gabarito dos nós; é o teto, não um agente)
    const { legada, gab } = arvore(); prev = new Map()
    for (const p of prods.values()) {
      const no = legada.get(p.categoria_legada), g = gab.get(p.categoria_legada)
      if (args['baseline-legado']) { const at = g?.atributo ? { [g.atributo.chave]: g.atributo.valor } : {}; prev.set(p.id, { id: p.id, status: g?.destino ? 'APLICADO' : 'MANTIDO', subcategoria: g?.destino || null, marca: p.marca_informada || null, atributos: at }) }
      else { const sub = no ? destinoPorNome(no) : null; prev.set(p.id, { id: p.id, status: sub ? 'APLICADO' : 'MANTIDO', subcategoria: sub, marca: p.marca_informada || null, atributos: {} }) }
    }
  }
  else prev = await lerJsonl(args.pred)
  const temR8 = [...prods.values()].some(p => p.gabarito.erros_de_atributo !== undefined)
  const R8 = { erros: {}, errosAnt: {}, estrag: { avaliados: 0, trocados: 0, removidos: 0 }, estragC: { avaliados: 0, trocados: 0, removidos: 0, ligado: false }, naoEl: { n: 0, indevido: 0 }, noLegado: { n: 0, ok: 0 }, amb: { n: 0, principal_ok: 0, aceita_qualquer: 0, sec_dadas: 0, sec_ok: 0 }, controles: { n: 0, removidos: 0 } }
  const r = { itens: prods.size, previsoes: 0, aplicados: 0, mantidos: 0, acerto_subcategoria_aplicados: 0, acerto_categoria_aplicados: 0, acerto_departamento_aplicados: 0, pioras: 0, melhoras: 0, corretos_no_catalogo: 0, por_dificuldade: {}, atributos: { avaliados: 0, corretos: 0, inventados: 0, nao_inferiveis: 0, nao_informado_correto: 0 }, marca: { avaliadas: 0, corretas: 0 } }
  for (const p of prods.values()) {
    const g = p.gabarito, x = prev.get(p.id)
    const vendedorCerto = g.categoria_vendedor_status === 'correta'
    const d = (r.por_dificuldade[g.dificuldade] ||= { n: 0, aplicados: 0, acertos: 0 })
    d.n++
    if (temR8) {
      const at = x?.atributos || {}
      const chavesErro = new Set((g.erros_de_atributo || []).map(e => e.chave))
      for (const e of g.erros_de_atributo || []) {
        const b = (R8.erros[e.tipo] ||= { n: 0, corrigidos: 0, neutralizados: 0, tirados_do_filtro: 0, passaram: 0, trocados_por_outro_valor: 0, inventados: 0 })
        b.n++
        const v = at[e.chave]
        if (e.tipo === 'nao_se_aplica') { if (especial(v)) b.neutralizados++; else b.passaram++ }
        else if (e.tipo === 'contradiz_anuncio') { if (mesmoValor(v, e.valor_certo)) b.corrigidos++; else if (especial(v)) b.tirados_do_filtro++; else if (mesmoValor(v, e.valor_informado)) b.passaram++; else b.trocados_por_outro_valor++ }
        else if (e.tipo === 'fora_do_normal' || e.tipo === 'sem_pista') {
          // DEFINICAO CORRIGIDA (pos-primeira-execucao): o ORIGINAL e o valor que o vendedor informou lido pelo proprio pipeline so do campo dele (atributos_originais, nao vem do gabarito).
          // Comparar com o texto cru do vendedor ("5 bocas", "1,5 kW") contava como troca uma simples normalizacao. Sem atributos_originais cai na definicao antiga.
          const o = x && x.atributos_originais ? x.atributos_originais[e.chave] : undefined
          const usaOriginal = o !== undefined
          const baseAntiga = e.tipo === 'sem_pista' ? canon(e.chave, e.valor_informado) : e.valor_informado
          const classif = base => especial(v) ? 'tirado' : (e.tipo === 'sem_pista' && mesmoValor(v, e.valor_certo)) ? 'corrigido' : mesmoValor(v, base) ? 'passou' : 'trocado'
          const conta = (bb, c) => { if (c === 'tirado') bb.tirados_do_filtro++; else if (c === 'passou') bb.passaram++; else if (c === 'corrigido') { bb.inventados++; bb.corrigidos++ } else { bb.trocados_por_outro_valor++; if (e.tipo === 'sem_pista') bb.inventados++ } }
          const bA = (R8.errosAnt[e.tipo] ||= { n: 0, corrigidos: 0, neutralizados: 0, tirados_do_filtro: 0, passaram: 0, trocados_por_outro_valor: 0, inventados: 0 })
          bA.n++
          conta(bA, classif(baseAntiga))
          conta(b, classif(usaOriginal ? o : baseAntiga))
        }
      }
      // DEFINICAO CORRIGIDA de "atributo certo estragado": o vendedor informou o valor CERTO (atributos_originais) e o agente o trocou por outro ou o tirou. Atributo que o vendedor nao
      // informou e o agente deixou vazio nao e estrago (a definicao antiga, abaixo, contava como removido todo nao_informado).
      if (x && x.atributos_originais) {
        R8.estragC.ligado = true
        for (const [k, o] of Object.entries(x.atributos_originais)) {
          const real = g.atributos[k]
          if (real === undefined || real === null || !mesmoValor(o, real)) continue
          R8.estragC.avaliados++
          const v = at[k]
          if (especial(v)) R8.estragC.removidos++
          else if (!mesmoValor(v, real)) R8.estragC.trocados++
        }
      }
      // atributos certos que o agente trocou ou removeu (definicao antiga; so onde a subcategoria esta certa)
      if (x && x.subcategoria === g.subcategoria) {
        const inf = new Set(g.atributos_inferiveis)
        for (const [k, real] of Object.entries(g.atributos)) {
          if (!inf.has(k) || chavesErro.has(k) || (g.atributos_errados_pelo_vendedor || []).includes(k)) continue
          R8.estrag.avaliados++
          const v = at[k]
          if (v === 'nao_informado' || v === 'nao_se_aplica') R8.estrag.removidos++
          else if (v !== undefined && !mesmoValor(v, real)) R8.estrag.trocados++
        }
      }
      // não elétricos no filtro de voltagem (a ficha publicada da subcategoria não tem voltagem)
      const ficha = FOLHA[g.subcategoria]
      if (ficha && !ficha.atributos.some(a => a.chave === 'voltagem')) { R8.naoEl.n++; if (!especial(at.voltagem)) R8.naoEl.indevido++ }
      if (g.controle_voltagem) { R8.controles.n++; if (especial(at.voltagem)) R8.controles.removidos++ }
      if (g.atributo_do_no_legado) { R8.noLegado.n++; if (x && mesmoValor(at[g.atributo_do_no_legado.chave], g.atributo_do_no_legado.valor)) R8.noLegado.ok++ }
      if (g.subcategorias_aceitas) {
        R8.amb.n++
        if (x && x.subcategoria === g.subcategoria) R8.amb.principal_ok++
        if (x && g.subcategorias_aceitas.includes(x.subcategoria)) R8.amb.aceita_qualquer++
        if (x && x.subcategoria_secundaria) { R8.amb.sec_dadas++; if (x.subcategoria_secundaria === g.subcategorias_aceitas.find(a => a !== g.subcategoria)) R8.amb.sec_ok++ }
      }
    }
    if (!x) { if (vendedorCerto) r.corretos_no_catalogo++; continue }
    r.previsoes++
    const aplicado = (x.status || 'APLICADO') === 'APLICADO' && x.subcategoria
    if (aplicado) {
      r.aplicados++; d.aplicados++
      const ok = x.subcategoria === g.subcategoria
      if (ok) { r.acerto_subcategoria_aplicados++; d.acertos++ }
      const fx = FOLHA[x.subcategoria]
      if (fx && fx.cat === g.categoria) r.acerto_categoria_aplicados++
      if (fx && fx.dep === g.departamento) r.acerto_departamento_aplicados++
      if (vendedorCerto && !ok) r.pioras++
      if (!vendedorCerto && ok) r.melhoras++
      if (ok) r.corretos_no_catalogo++
    } else { r.mantidos++; if (vendedorCerto) r.corretos_no_catalogo++ }
    // atributos: comparados só na subcategoria certa
    if (x.atributos && x.subcategoria === g.subcategoria) {
      const inf = new Set(g.atributos_inferiveis), errVend = new Set(g.atributos_errados_pelo_vendedor || [])
      for (const [k, real] of Object.entries(g.atributos)) {
        if (errVend.has(k)) continue // o vendedor informou errado e não há outra pista: fica fora da conta
        const v = x.atributos[k]
        if (inf.has(k)) { r.atributos.avaliados++; if (mesmoValor(v, real)) r.atributos.corretos++ }
        else { r.atributos.nao_inferiveis++; if (v === undefined || v === 'nao_informado') r.atributos.nao_informado_correto++; else if (!mesmoValor(v, real)) r.atributos.inventados++ }
      }
    }
    if (x.marca !== undefined) { r.marca.avaliadas++; if (norm(x.marca || 'nao_identificada') === norm(g.marca)) r.marca.corretas++ }
  }
  const out = {
    itens: r.itens, previsoes: r.previsoes,
    'aplicados_%': pct(r.aplicados, r.itens), 'mantidos_%': pct(r.mantidos, r.itens),
    'acerto_subcategoria_aplicados_%': pct(r.acerto_subcategoria_aplicados, r.aplicados),
    'acerto_categoria_aplicados_%': pct(r.acerto_categoria_aplicados, r.aplicados),
    'acerto_departamento_aplicados_%': pct(r.acerto_departamento_aplicados, r.aplicados),
    'pioras_%_do_total': pct(r.pioras, r.itens), 'melhoras_%_do_total': pct(r.melhoras, r.itens),
    'corretos_no_catalogo_%': pct(r.corretos_no_catalogo, r.itens),
    'vendedor_sozinho_%': pct([...prods.values()].filter(p => p.gabarito.categoria_vendedor_status === 'correta').length, r.itens),
    atributos: { 'acerto_inferiveis_%': pct(r.atributos.corretos, r.atributos.avaliados), avaliados: r.atributos.avaliados, 'inventados_%_dos_nao_inferiveis': pct(r.atributos.inventados, r.atributos.nao_inferiveis) },
    'marca_acerto_%': pct(r.marca.corretas, r.marca.avaliadas),
    por_dificuldade: Object.fromEntries(Object.entries(r.por_dificuldade).map(([k, v]) => [k, { n: v.n, 'aplicados_%': pct(v.aplicados, v.n), 'acerto_%': pct(v.acertos, v.aplicados) }])),
  }
  if (temR8) {
    const resolvidoDe = (t, b) => pct(t === 'nao_se_aplica' ? b.neutralizados : t === 'contradiz_anuncio' ? b.corrigidos : t === 'fora_do_normal' ? b.tirados_do_filtro : b.n - b.inventados, b.n)
    const porTipo = Object.fromEntries(Object.entries(R8.erros).map(([t, b]) => [t, {
      ...(R8.errosAnt[t] ? { 'trocados_por_outro_valor_%_definicao_antiga': pct(R8.errosAnt[t].trocados_por_outro_valor, R8.errosAnt[t].n), ...(t === 'sem_pista' ? { 'inventados_%_definicao_antiga': pct(R8.errosAnt[t].inventados, R8.errosAnt[t].n) } : {}), 'resolvidos_%_definicao_antiga': resolvidoDe(t, R8.errosAnt[t]) } : {}),
      n: b.n, 'corrigidos_%': pct(b.corrigidos, b.n), 'neutralizados_%': pct(b.neutralizados, b.n), 'tirados_do_filtro_%': pct(b.tirados_do_filtro, b.n), 'passaram_%': pct(b.passaram, b.n),
      'trocados_por_outro_valor_%': pct(b.trocados_por_outro_valor, b.n), ...(t === 'sem_pista' ? { 'inventados_%': pct(b.inventados, b.n) } : {}),
      // por tipo, a medida do critério: não se aplica = neutralizado; contradiz = corrigido; fora do normal = tirado do filtro; sem pista = não inventado
      'resolvidos_%': pct(t === 'nao_se_aplica' ? b.neutralizados : t === 'contradiz_anuncio' ? b.corrigidos : t === 'fora_do_normal' ? b.tirados_do_filtro : b.n - b.inventados, b.n),
    }]))
    out.rodada8 = {
      erros_de_atributo: porTipo,
      estragados: R8.estragC.ligado
        ? { definicao: 'o vendedor informou o valor certo e o agente o trocou ou o tirou (usa atributos_originais)', avaliados: R8.estragC.avaliados, trocados: R8.estragC.trocados, removidos: R8.estragC.removidos, '%': pct(R8.estragC.trocados + R8.estragC.removidos, R8.estragC.avaliados) }
        : { definicao: 'antiga: todo atributo certo e inferivel que ficou nao_informado ou com outro valor', avaliados: R8.estrag.avaliados, trocados: R8.estrag.trocados, removidos: R8.estrag.removidos, '%': pct(R8.estrag.trocados + R8.estrag.removidos, R8.estrag.avaliados) },
      estragados_definicao_antiga: { avaliados: R8.estrag.avaliados, trocados: R8.estrag.trocados, removidos: R8.estrag.removidos, '%': pct(R8.estrag.trocados + R8.estrag.removidos, R8.estrag.avaliados) },
      no_filtro_indevido: { nao_eletricos: R8.naoEl.n, no_filtro: R8.naoEl.indevido, '%': pct(R8.naoEl.indevido, R8.naoEl.n) },
      controles_eletricos: { n: R8.controles.n, voltagem_removida_por_engano: R8.controles.removidos, '%': pct(R8.controles.removidos, R8.controles.n) },
      atributos_do_no_legado: { n: R8.noLegado.n, recuperados: R8.noLegado.ok, '%': pct(R8.noLegado.ok, R8.noLegado.n) },
      ...(R8.amb.n ? { ambiguidade: { n: R8.amb.n, 'principal_certa_%': pct(R8.amb.principal_ok, R8.amb.n), 'aceita_qualquer_das_duas_%': pct(R8.amb.aceita_qualquer, R8.amb.n), secundarias_sugeridas: R8.amb.sec_dadas, 'secundaria_precisao_%': pct(R8.amb.sec_ok, R8.amb.sec_dadas) } } : {}),
    }
  }
  saida(out)
}

// ---------------------------------------------------------------------------
if (modo === 'novos') {
  const ids = new Set(splits[args.ids || 'novos_teste'])
  const novos = await lerJsonl(path.join(DADOS, 'novos.jsonl'), o => ids.has(o.id))
  const prev = await lerJsonl(args.pred)
  const conf = () => ({ vp: 0, fp: 0, fn: 0 })
  const fora = conf(), dup = conf(), inv = conf()
  const porTipo = {}
  let classificados = 0, acertos = 0
  // rodada 8: duplicados por grupo e motivos de erro, variações, fora da taxonomia (departamento e nome), limítrofes
  const temR8N = [...novos.values()].some(n => n.gabarito.pista_duplicado || n.gabarito.variacao_de || n.gabarito.tipo_novo === 'invalido_limitrofe' || n.gabarito.grupo_fora)
  let raizDe = null, famDe = null
  if (temR8N) {
    const prods = await lerJsonl(path.join(DADOS, 'produtos.jsonl'))
    raizDe = id => prods.get(id)?.gabarito.duplicado_de || id
    famDe = id => prods.get(id)?.gabarito.variacao_de || id
  }
  const dupG = conf(), vari = conf(), motivos = { outra_copia_do_grupo: 0, variacao_tratada_como_duplicado: 0, produto_diferente: 0 }
  const porPista = {}, foraR8 = { previstos_certos: 0, dep_ok: 0, nome_ok: 0, amostra: [] }, lim = { n: 0, barrados: 0 }, eixo = { n: 0, ok: 0 }
  const palavras = t => new Set(norm(t).split(/[^a-z0-9]+/).filter(w => w.length > 2).map(w => w.replace(/s$/, '')))
  const nomeParecido = (a, b) => { const A = palavras(a), B = palavras(b); if (!A.size || !B.size) return false; const comum = [...A].filter(w => B.has(w)).length; return comum / Math.min(A.size, B.size) >= 0.5 }
  for (const n of novos.values()) {
    const g = n.gabarito, x = prev.get(n.id) || {}
    const t = (porTipo[g.tipo_novo] ||= { n: 0, acerto: 0 }); t.n++
    const marca = (c, real, previsto) => { if (real && previsto) c.vp++; else if (!real && previsto) c.fp++; else if (real && !previsto) c.fn++ }
    marca(fora, !!g.fora_da_taxonomia, !!x.fora_da_taxonomia)
    marca(inv, !!g.invalido, !!x.invalido)
    const dupOk = g.duplicado_de && x.duplicado_de === g.duplicado_de
    if (g.duplicado_de && x.duplicado_de) { if (dupOk) dup.vp++; else { dup.fp++; dup.fn++ } } else marca(dup, !!g.duplicado_de, !!x.duplicado_de)
    if (temR8N) {
      // duplicado: o oficial acima exige o original exato; aqui, qualquer cópia do mesmo grupo e o motivo de cada erro
      const alvo = x.duplicado_de
      if (g.duplicado_de && alvo && raizDe(alvo) === raizDe(g.duplicado_de)) dupG.vp++
      else if (g.duplicado_de && alvo) { dupG.fp++; dupG.fn++ }
      else marca(dupG, !!g.duplicado_de, !!alvo)
      if (alvo && alvo !== g.duplicado_de) {
        if (g.duplicado_de && raizDe(alvo) === raizDe(g.duplicado_de)) motivos.outra_copia_do_grupo++
        else if (g.variacao_de && famDe(alvo) === famDe(g.variacao_de)) motivos.variacao_tratada_como_duplicado++
        else motivos.produto_diferente++
      }
      if (g.pista_duplicado) { const pp = (porPista[g.pista_duplicado] ||= { n: 0, exato: 0, grupo: 0 }); pp.n++; if (alvo === g.duplicado_de) pp.exato++; if (alvo && raizDe(alvo) === raizDe(g.duplicado_de)) pp.grupo++ }
      // variação: é a mesma família de um produto do catálogo em outro eixo, e não duplicado
      const pv = x.variacao_de
      if (g.variacao_de && pv) { if (famDe(pv) === famDe(g.variacao_de)) { vari.vp++; eixo.n++; if (x.eixo === g.eixo_variacao) eixo.ok++ } else { vari.fp++; vari.fn++ } } else marca(vari, !!g.variacao_de, !!pv)
      if (g.fora_da_taxonomia && x.fora_da_taxonomia) {
        foraR8.previstos_certos++
        if (x.departamento_provavel === g.departamento_provavel) foraR8.dep_ok++
        const eq = !!x.nome_sugerido && nomeParecido(x.nome_sugerido, g.nome_sugerido); if (eq) foraR8.nome_ok++
        if (foraR8.amostra.length < 12) foraR8.amostra.push({ id: n.id, sugerido: x.nome_sugerido ?? null, gabarito: g.nome_sugerido, equivalente: eq })
      }
      if (g.tipo_novo === 'invalido_limitrofe') { lim.n++; if (x.invalido) lim.barrados++ }
    }
    let ok
    if (g.fora_da_taxonomia) ok = !!x.fora_da_taxonomia && !x.subcategoria
    else if (g.invalido) ok = !!x.invalido
    else if (g.invalido === false && !g.subcategoria) ok = !x.invalido // limítrofe sem subcategoria no gabarito: basta não barrar
    else { ok = x.subcategoria === g.subcategoria; if (x.subcategoria) { classificados++; if (ok) acertos++ } }
    if (ok) t.acerto++
  }
  const pr = c => ({ 'precisao_%': pct(c.vp, c.vp + c.fp), 'cobertura_%': pct(c.vp, c.vp + c.fn) })
  const saidaN = { itens: novos.size, fora_da_taxonomia: pr(fora), duplicados: pr(dup), invalidos: pr(inv), 'acerto_subcategoria_dos_classificados_%': pct(acertos, classificados), por_tipo: Object.fromEntries(Object.entries(porTipo).map(([k, v]) => [k, { n: v.n, 'acerto_%': pct(v.acerto, v.n) }])) }
  if (temR8N) saidaN.rodada8 = {
    duplicados_oficial_original_exato: pr(dup), duplicados_por_grupo: pr(dupG), erros_de_duplicado_por_motivo: motivos,
    duplicados_por_pista: Object.fromEntries(Object.entries(porPista).map(([k, v]) => [k, { n: v.n, 'original_exato_%': pct(v.exato, v.n), 'qualquer_copia_do_grupo_%': pct(v.grupo, v.n) }])),
    variacoes: { ...pr(vari), 'eixo_certo_%': pct(eixo.ok, eixo.n) },
    fora_da_taxonomia: { ...pr(fora), 'departamento_provavel_certo_%': pct(foraR8.dep_ok, foraR8.previstos_certos), 'nome_sugerido_equivalente_%': pct(foraR8.nome_ok, foraR8.previstos_certos), amostra_para_conferir: foraR8.amostra },
    invalidos_limitrofes: { n: lim.n, barrados_por_engano: lim.barrados, '%': pct(lim.barrados, lim.n) },
  }
  saida(saidaN)
}

// ---------------------------------------------------------------------------
if (modo === 'busca') {
  const buscas = await lerJsonl(path.join(DADOS, 'buscas.jsonl'))
  const prods = await lerJsonl(path.join(DADOS, 'produtos.jsonl'))
  // --incluir-novos: o catálogo já recebeu os itens novos (os válidos, com subcategoria) e a busca passa a valer sobre ele
  if (args['incluir-novos']) { const novos = await lerJsonl(path.join(DADOS, 'novos.jsonl'), o => o.gabarito && o.gabarito.subcategoria); for (const [id, n] of novos) prods.set(id, n) }
  const rel = (b, p) => {
    const g = p.gabarito
    if (!b.intencao.subcategorias.includes(g.subcategoria)) return 0
    if (b.intencao.marca && g.marca_real !== b.intencao.marca) return 0
    const f = Object.entries(b.intencao.filtros); if (!f.length) return 2
    const ok = f.filter(([k, v]) => String(g.atributos[k]) === String(v)).length
    return ok === f.length ? 2 : 1
  }
  let prev
  if (args['baseline-texto']) {
    // busca por palavra (como a busca atual): todos os termos da consulta no título, tags ou descrição; título primeiro
    prev = new Map()
    const docs = [...prods.values()].map(p => ({ id: p.id, t: norm(p.titulo), resto: norm(p.tags + ' ' + p.descricao) }))
    for (const b of buscas.values()) {
      const termos = norm(b.q).split(/\s+/).filter(w => w.length > 1)
      const noTitulo = [], fora = []
      for (const d of docs) { const todosT = termos.every(w => d.t.includes(w)); if (todosT) noTitulo.push(d.id); else if (termos.every(w => d.t.includes(w) || d.resto.includes(w))) fora.push(d.id) }
      prev.set(b.id, { id: b.id, resultados: [...noTitulo, ...fora] })
    }
  } else if (args['baseline-filtros'] || args['catalogo-previsto']) {
    // Navegação por categoria e filtros (facetas), como o cliente faz clicando no menu e nos filtros da loja.
    // O cliente clica na categoria e nos filtros certos da consulta (a intenção); o que muda é o catálogo por baixo:
    //   --baseline-filtros: catálogo como chegou. Categoria = árvore do vendedor; cada nome de atributo cru vira uma
    //                       faceta ("Bocas", "Quantidade de bocas"...) e cada valor escrito, um valor ("5", "5 bocas"...).
    //                       O cliente clica na faceta e no valor mais comuns que correspondem ao que ele quer.
    //   --catalogo-previsto previsoes.jsonl (formato do saneamento): categoria aplicada pelo agente (os mantidos
    //                       continuam na categoria do vendedor) e ficha técnica normalizada prevista.
    //   Com --incluir-novos e --catalogo-previsto, use --novos-previstos (mesmo formato) para os itens novos aceitos.
    const crus = !args['catalogo-previsto']
    const prevCat = crus ? null : await lerJsonl(args['catalogo-previsto'])
    if (!crus && args['novos-previstos']) for (const [id, x] of await lerJsonl(args['novos-previstos'])) prevCat.set(id, x)
    const idx = []
    for (const p of prods.values()) {
      if (crus) {
        const at = {}
        for (const [k, v] of Object.entries(p.atributos || {})) at[norm(k)] = norm(v)
        idx.push({ id: p.id, sub: mapearVendedor(p.categoria_vendedor), at, marca: norm(p.marca_informada) })
      } else {
        const x = prevCat.get(p.id)
        if (!x) continue // item não aceito no catálogo (inválido, duplicado, fora da taxonomia) ou sem previsão
        const sub = (x.status || 'APLICADO') === 'APLICADO' && x.subcategoria ? x.subcategoria : mapearVendedor(p.categoria_vendedor)
        idx.push({ id: p.id, sub, at: x.atributos || {}, marca: norm(x.marca || p.marca_informada) })
      }
    }
    prev = new Map()
    for (const b of buscas.values()) {
      const subs = new Set(b.intencao.subcategorias), fs_ = Object.entries(b.intencao.filtros), m = norm(b.intencao.marca || '')
      let cand = idx.filter(d => subs.has(d.sub) && (!m || d.marca === m))
      for (const [k, v] of fs_) {
        if (!crus) { cand = cand.filter(d => mesmoValor(d.at[k], v)); continue }
        // catálogo cru: cada nome de atributo do vendedor vira uma faceta separada, e cada valor escrito, um valor de faceta.
        // O cliente clica na faceta mais comum entre os nomes possíveis do atributo e, nela, no valor mais comum que corresponde ao que quer.
        const A = tax.atributos[k] || {}, nomes = new Set([A.nome, ...(A.aliases_vendedor || [])].map(norm))
        const cont = new Map(); for (const d of cand) for (const kk of Object.keys(d.at)) if (nomes.has(kk)) cont.set(kk, (cont.get(kk) || 0) + 1)
        const K = [...cont].sort((x, y) => y[1] - x[1])[0]?.[0]
        const nv = norm(v), bate = s => s === nv || (s.startsWith(nv) && !/[0-9.,]/.test(s[nv.length]))
        const cv = new Map(); for (const d of cand) { const s = d.at[K]; if (s !== undefined && bate(s)) cv.set(s, (cv.get(s) || 0) + 1) }
        const V = [...cv].sort((x, y) => y[1] - x[1])[0]?.[0]
        cand = K && V !== undefined ? cand.filter(d => d.at[K] === V) : []
      }
      const res = cand.map(d => d.id)
      prev.set(b.id, { id: b.id, resultados: res })
    }
  } else prev = await lerJsonl(args.pred)
  const nRel = new Map()
  for (const b of buscas.values()) { let n = 0; for (const p of prods.values()) if (rel(b, p) === 2) n++; nRel.set(b.id, n) }
  const agg = {}
  const add = (grupo, k, v) => { const a = (agg[grupo] ||= {}); (a[k] ||= []).push(v) }
  for (const b of buscas.values()) {
    const x = prev.get(b.id)
    if (b.tipo === 'sem_resposta') {
      // consulta sem resposta: o certo é voltar vazia (ou com o aviso de "nenhum resultado"); fica fora das médias de 'todas'
      if (!x) { add('sem_resposta', 'sem_previsao', 1); continue }
      const n = x.resultados ? x.resultados.length : (x.subcategorias || []).length
      add('sem_resposta', 'vazia_ou_aviso', n === 0 || x.aviso === 'nenhum_resultado' ? 1 : 0)
      add('sem_resposta', 'tamanho_resultado', n)
      continue
    }
    const grupos = ['todas', b.tipo]
    if (x && x.corrigida) grupos.push('corrigidas')
    if (Object.keys(b.intencao.filtros).length) grupos.push('com_filtros')
    if (b.intencao.marca) grupos.push('com_marca')
    if (!x) { for (const gr of grupos) add(gr, 'sem_previsao', 1); continue }
    if (x.resultados) {
      const res = x.resultados.map(id => prods.get(id)).filter(Boolean)
      const rels = res.map(p => rel(b, p))
      const p10 = rels.slice(0, 10).filter(r => r === 2).length / Math.max(1, Math.min(10, rels.length || 1))
      const dcg = rels.slice(0, 10).reduce((s, r, i) => s + (2 ** r - 1) / Math.log2(i + 2), 0)
      const ideal = Array(Math.min(10, nRel.get(b.id))).fill(2).concat(Array(Math.max(0, 10 - nRel.get(b.id))).fill(0)).slice(0, 10).reduce((s, r, i) => s + (2 ** r - 1) / Math.log2(i + 2), 0)
      const precTotal = rels.length ? rels.filter(r => r === 2).length / rels.length : 0
      const cobertura = rels.filter(r => r === 2).length / Math.max(1, nRel.get(b.id))
      for (const gr of grupos) { add(gr, 'P@10', p10); add(gr, 'NDCG@10', ideal ? dcg / ideal : 0); add(gr, 'precisao_lista_inteira', precTotal); add(gr, 'cobertura', cobertura); add(gr, 'sem_resultado', rels.length ? 0 : 1); add(gr, 'tamanho_resultado', rels.length) }
    } else {
      const sub = new Set(x.subcategorias || []), real = new Set(b.intencao.subcategorias)
      const exato = sub.size === real.size && [...sub].every(s => real.has(s)) ? 1 : 0
      const contem = [...sub].some(s => real.has(s)) ? 1 : 0
      const soCertas = sub.size > 0 && [...sub].every(s => real.has(s)) ? 1 : 0
      const fr = Object.entries(b.intencao.filtros), fp = Object.entries(x.filtros || {})
      const fOk = fr.filter(([k, v]) => x.filtros && String(x.filtros[k]) === String(v)).length
      const fExtra = fp.filter(([k, v]) => String(b.intencao.filtros[k]) !== String(v)).length
      const marcaOk = norm(x.marca || '') === norm(b.intencao.marca || '') ? 1 : 0
      for (const gr of grupos) { add(gr, 'subcategorias_exatas', exato); add(gr, 'contem_alguma_certa', contem); add(gr, 'so_subcategorias_certas', soCertas); if (fr.length) add(gr, 'filtros_cobertura', fOk / fr.length); if (fp.length) add(gr, 'filtros_precisao', (fp.length - fExtra) / fp.length); add(gr, 'marca_ok', marcaOk) }
    }
  }
  const media = a => Math.round(a.reduce((s, x) => s + x, 0) / a.length * 1000) / 10
  const out = {}
  for (const [gr, ks] of Object.entries(agg)) out[gr] = Object.fromEntries(Object.entries(ks).map(([k, v]) => [k === 'tamanho_resultado' ? 'tamanho_medio_resultado' : k + '_%', k === 'tamanho_resultado' ? Math.round(v.reduce((s, x) => s + x, 0) / v.length) : media(v)]).concat([['consultas', ks[Object.keys(ks)[0]].length]]))
  saida(out)
}

// ---------------------------------------------------------------------------
// Rodada 8: diagnóstico dos nós da árvore legada (precisão e cobertura por tipo, destino, atributo, tamanho da árvore resultante)
if (modo === 'arvore') {
  const { legada, gab } = arvore()
  const sp = lerJson('arvore_splits.json')
  const lado = args.ids || 'teste'
  const sel = lado === 'todos' ? null : new Set(sp[lado])
  const nos = [...gab.values()].filter(n => !n.estrutural && (!sel || sel.has(n.id)))
  let prev
  if (args['baseline-legado']) prev = new Map(nos.map(n => [n.id, { id: n.id, tipo: n.tipo, destino: n.destino, atributo: n.atributo }])) // o teto: o gabarito
  else if (args['baseline-legado-nome']) { prev = new Map(); for (const n of nos) { const d = destinoPorNome(legada.get(n.id)); prev.set(n.id, { id: n.id, tipo: d ? 'categoria' : 'lixo', destino: d, atributo: null }) } }
  else prev = await lerJsonl(args.pred)
  const conf = () => ({ vp: 0, fp: 0, fn: 0 })
  const porTipo = { atributo: conf(), lixo: conf(), duplicado: conf(), categoria: conf() }
  const dest = { n: 0, ok: 0 }, atr = { n: 0, destino_ok: 0, ok: 0 }, semPrev = { n: 0 }
  const resultantes = new Set(), reais = new Set()
  for (const n of nos) {
    const x = prev.get(n.id)
    if (!x) semPrev.n++
    const t = x?.tipo
    for (const [tipo, c] of Object.entries(porTipo)) { if (n.tipo === tipo && t === tipo) c.vp++; else if (n.tipo !== tipo && t === tipo) c.fp++; else if (n.tipo === tipo && t !== tipo) c.fn++ }
    if (n.tipo === 'categoria' || n.tipo === 'duplicado') { dest.n++; if (x && x.destino === n.destino && (t === 'categoria' || t === 'duplicado')) dest.ok++ }
    if (n.tipo === 'atributo') {
      atr.n++
      if (x && x.destino === n.destino) atr.destino_ok++
      if (x && t === 'atributo' && x.destino === n.destino && x.atributo && x.atributo.chave === n.atributo.chave && norm(x.atributo.valor) === norm(n.atributo.valor)) atr.ok++
    }
    if (n.tipo !== 'lixo' && n.destino) reais.add(n.destino)
    if (x && ['categoria', 'duplicado', 'atributo'].includes(t) && x.destino) resultantes.add(x.destino)
  }
  const pr = c => ({ 'precisao_%': pct(c.vp, c.vp + c.fp), 'cobertura_%': pct(c.vp, c.vp + c.fn), verdadeiros: c.vp + c.fn })
  const total = Object.keys(FOLHA).length
  saida({
    nos: nos.length, conjunto: lado, nos_sem_previsao: semPrev.n,
    nos_atributo: pr(porTipo.atributo), nos_lixo: pr(porTipo.lixo), nos_duplicado: pr(porTipo.duplicado), nos_categoria: pr(porTipo.categoria),
    'destino_categoria_e_duplicado_acerto_%': pct(dest.ok, dest.n),
    atributo: { n: atr.n, 'destino_certo_%': pct(atr.destino_ok, atr.n), 'destino_e_atributo_certos_%': pct(atr.ok, atr.n) },
    arvore_resultante: { subcategorias_resultantes: resultantes.size, subcategorias_reais_no_conjunto: reais.size, subcategorias_da_taxonomia: total, 'excesso_sobre_o_conjunto_%': reais.size ? Math.round((resultantes.size - reais.size) / reais.size * 1000) / 10 : null, 'excesso_sobre_a_taxonomia_%': lado === 'todos' ? Math.round((resultantes.size - total) / total * 1000) / 10 : null },
  })
}

// ---------------------------------------------------------------------------
// Rodada 8: duplicados e famílias de variação propostos no catálogo existente (precisão por par e cobertura)
if (modo === 'grupos') {
  const prods = await lerJsonl(path.join(DADOS, 'produtos.jsonl'))
  const todos = (args.ids || 'todos') === 'todos'
  const ids = todos ? null : new Set(args['ids-arquivo'] ? JSON.parse(fs.readFileSync(args['ids-arquivo'])) : splits[args.ids])
  const prev = await lerJsonl(args.pred)
  const raiz = id => prods.get(id)?.gabarito.duplicado_de || id
  const fam = id => prods.get(id)?.gabarito.variacao_de || id
  const dup = { previstos: 0, certos: 0, verdadeiros: 0, cobertos: 0 }, vari = { previstos: 0, certos: 0, verdadeiros: 0, cobertos: 0 }
  for (const p of prods.values()) {
    if (ids && !ids.has(p.id)) continue
    const g = p.gabarito, x = prev.get(p.id) || {}
    if (g.duplicado_de) { dup.verdadeiros++; if (x.duplicado_de && x.duplicado_de !== p.id && raiz(x.duplicado_de) === raiz(p.id)) dup.cobertos++ }
    if (x.duplicado_de) { dup.previstos++; if (x.duplicado_de !== p.id && prods.has(x.duplicado_de) && raiz(x.duplicado_de) === raiz(p.id)) dup.certos++ }
    if (g.variacao_de) { vari.verdadeiros++; if (x.variacao_de && x.variacao_de !== p.id && fam(x.variacao_de) === fam(p.id)) vari.cobertos++ }
    if (x.variacao_de) { vari.previstos++; if (x.variacao_de !== p.id && prods.has(x.variacao_de) && fam(x.variacao_de) === fam(p.id)) vari.certos++ }
  }
  const pr = c => ({ pares_previstos: c.previstos, 'precisao_por_par_%': pct(c.certos, c.previstos), pares_verdadeiros: c.verdadeiros, 'cobertura_%': pct(c.cobertos, c.verdadeiros) })
  saida({ produtos: ids ? ids.size : prods.size, duplicados: pr(dup), variacoes: pr(vari) })
}
