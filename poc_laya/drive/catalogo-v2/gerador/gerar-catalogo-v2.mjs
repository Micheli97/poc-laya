// Gerador do catálogo sintético v2.
// Uso: node gerador/gerar-catalogo-v2.mjs [--produtos 100000] [--novos 10000] [--semente 20261008] [--ruido 1] [--saida dados]
// --ruido 1.5 deixa os anúncios 50% mais bagunçados (teste de robustez); 1 reproduz a base original.
//
// Gera:
//   dados/produtos.jsonl     catálogo atual (bagunçado), cada linha com "gabarito"
//   dados/novos.jsonl        fluxo de itens novos (com marcas novas, duplicados, fora da taxonomia, inválidos)
//   dados/buscas.jsonl       consultas com a intenção correta (a relevância sai do gabarito)
//   dados/taxonomia.json     departamentos > categorias > subcategorias, critérios PT/EN, esquema de atributos
//   dados/splits.json        treino / validação / golden / teste, sem vazamento entre duplicados e variações
//   dados/estatisticas.json  números da base
//   dados/amostra.csv        300 linhas para olhar no Excel
//
// O gabarito existe só para avaliar. O agente nunca pode lê-lo.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  ATRIBUTOS, SINONIMOS_VALOR, MARCAS, MARCAS_NOVAS, MODELOS_CEL, MODELOS_IMPRESSORA, MODELOS_TV, MODELOS_ELETRO,
  BENEFICIOS, BENEFICIOS_CAT, EXTRAS_DEP, EXTRAS_CAT, EXTRAS_FOLHA, LINHAS, DEPARTAMENTOS, FORA_TAXONOMIA, CATEGORIAS_GENERICAS, CATEGORIAS_INGLES, BUSCAS_ARMADILHA,
} from './taxonomia-v2.mjs'
import { registrarR8, ajustarFichasR8, posProcessarR8, buscasR8, GRUPOS_FORA, SOBREPOSICOES, REGRA_PRINCIPAL } from './rodada8.mjs'

// ---------------------------------------------------------------------------
// Argumentos
// ---------------------------------------------------------------------------
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, arr) => {
  if (a.startsWith('--')) acc.push([a.slice(2), arr[i + 1] && !arr[i + 1].startsWith('--') ? arr[i + 1] : 'true'])
  return acc
}, []))
const N_PRODUTOS = Number(args.produtos || 100000)
const N_NOVOS = Number(args.novos || 10000)
const SEMENTE = Number(args.semente || 20261008)
const RUIDO = Number(args.ruido || 1) // multiplica a chance de ruído de todos os vendedores (1 = padrão)
const R8 = args.rodada8 === 'true' // rodada 8: subcategorias novas, ruídos F2, árvore legada... Sem a opção, a saída é idêntica à da rodada 7
const SAIDA = args.saida || path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'dados')

// ---------------------------------------------------------------------------
// Aleatoriedade determinística
// ---------------------------------------------------------------------------
function mulberry32(a) { return function () { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296 } }
const rnd = mulberry32(SEMENTE)
const chance = p => rnd() < p
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1))
const pick = arr => arr[Math.floor(rnd() * arr.length)]
const pickN = (arr, n) => shuffle([...arr]).slice(0, n)
function shuffle(a) { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1));[a[i], a[j]] = [a[j], a[i]] } return a }
function pickPeso(itens, pesoFn) { const total = itens.reduce((s, x) => s + pesoFn(x), 0); let r = rnd() * total; for (const x of itens) { r -= pesoFn(x); if (r <= 0) return x } return itens[itens.length - 1] }

// ---------------------------------------------------------------------------
// Texto
// ---------------------------------------------------------------------------
const semAcento = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '')
const cap = s => s ? s[0].toUpperCase() + s.slice(1) : s
const titleCase = s => s.split(' ').map(w => w.length > 2 ? cap(w) : w).join(' ')
const numBR = n => (Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100).replace('.', ','))
function typo(palavra) {
  if (palavra.length < 4) return palavra
  const i = int(1, palavra.length - 2)
  switch (int(0, 3)) {
    case 0: return palavra.slice(0, i) + palavra[i + 1] + palavra[i] + palavra.slice(i + 2)
    case 1: return palavra.slice(0, i) + palavra.slice(i + 1)
    case 2: return palavra.slice(0, i) + palavra[i] + palavra.slice(i)
    default: return palavra.slice(0, i) + pick('aeiourstn') + palavra.slice(i + 1)
  }
}
const ABREVIACOES = [[/geladeira/gi, 'gel'], [/m[aá]quina/gi, 'maq'], [/bivolt/gi, 'biv'], [/polegadas/gi, 'pol'], [/\binox\b/gi, 'inx'], [/\bbranco\b/gi, 'bco'], [/\bpreto\b/gi, 'pto'], [/\bcom\b/gi, 'c/'], [/\bpara\b/gi, 'p/'], [/\bsem\b/gi, 's/'], [/litros/gi, 'lts'], [/\bunidades\b/gi, 'un'], [/\bmasculino\b/gi, 'masc'], [/\bfeminino\b/gi, 'fem'], [/autom[aá]tico/gi, 'autom']]
const PROMO = ['SUPER OFERTA', 'PROMOÇÃO', 'Frete Grátis', 'Lançamento 2026', 'Original', '🔥', '⭐ Mais Vendido', 'Envio Imediato', 'Black Friday', 'Pronta Entrega', 'Com Nota Fiscal', 'Oferta Relâmpago', 'Queima de Estoque', '✅', 'Liquidação', 'Imperdível']
const ISCAS = ['tipo iPhone', 'compatível com Air Fryer', 'igual Samsung', 'estilo Nike', 'melhor que Brastemp', 'smart', 'gamer', 'premium', 'profissional', '2 em 1']
const MARCAS_GENERICAS = ['Genérico', 'Sem marca', 'Original', 'Importado', 'Diversas', 'Generic', 'OEM', 'Nacional']

// ---------------------------------------------------------------------------
// Taxonomia achatada e esquema de atributos
// ---------------------------------------------------------------------------
function parseAttrs(spec) {
  return spec.split(/\s+/).filter(Boolean).map(tok => {
    let m
    if ((m = tok.match(/^([a-z_]+)=(.+)$/))) return { chave: m[1], tipo: 'enum', valores: m[2].split('|').map(v => v.replace(/_/g, ' ')) }
    if ((m = tok.match(/^([a-z_]+):([\d.]+)-([\d.]+)(?::([\d.]+))?$/))) return { chave: m[1], tipo: 'num', min: Number(m[2]), max: Number(m[3]), passo: Number(m[4] || 1) }
    const def = ATRIBUTOS[tok]
    if (!def) throw new Error('Atributo desconhecido: ' + tok)
    if (def.valores) return { chave: tok, tipo: 'enum', valores: def.valores }
    if (tok === 'compatibilidade') return { chave: tok, tipo: 'texto' }
    throw new Error('Atributo sem domínio padrão: ' + tok)
  }).map(a => {
    // números em enumeração (ex.: capacidade_l=0.9|3.6|18) continuam como texto canônico
    if (!ATRIBUTOS[a.chave]) throw new Error('Atributo desconhecido: ' + a.chave)
    return a
  })
}

if (R8) registrarR8({ DEPARTAMENTOS, ATRIBUTOS, MARCAS, MODELOS_CEL, MODELOS_IMPRESSORA })
const FOLHAS = []
const POR_ID = {}
for (const dep of DEPARTAMENTOS) for (const cat of dep.categorias) for (const fo of cat.folhas) {
  const esquema = []
  const extrasFicha = fo.tipo === 'produto' ? (EXTRAS_CAT[cat.id] || EXTRAS_DEP[dep.id] || '') : ((EXTRAS_DEP[dep.id] || '').includes('garantia_meses') ? 'origem garantia_meses' : 'origem')
  for (const a of [...parseAttrs(fo.attrs), ...parseAttrs(EXTRAS_FOLHA[fo.id] || ''), ...parseAttrs(extrasFicha)]) if (!esquema.some(x => x.chave === a.chave)) esquema.push(a)
  const folha = { ...fo, dep, cat, esquema, caminho: `${dep.nome} > ${cat.nome} > ${fo.nome}` }
  FOLHAS.push(folha); POR_ID[fo.id] = folha
}
if (R8) ajustarFichasR8(POR_ID)
for (const fo of FOLHAS) for (const ref of [...fo.de, ...fo.conf]) if (!POR_ID[ref]) throw new Error(`Referência inválida em ${fo.id}: ${ref}`)
for (const b of BUSCAS_ARMADILHA) for (const ref of [...b.folhas, ...(b.armadilhas || [])]) if (!POR_ID[ref]) throw new Error(`Busca com folha inválida: ${b.q} -> ${ref}`)

const COMPAT = {
  capas_celular: MODELOS_CEL, peliculas: MODELOS_CEL,
  pulseiras_smartwatch: ['Galaxy Watch 6', 'Galaxy Watch 7', 'Apple Watch Series 9', 'Apple Watch SE', 'Amazfit Bip 5', 'Mi Band 8', 'Redmi Watch 4'],
  cartuchos: MODELOS_IMPRESSORA,
  suportes_tv: ['TVs de 14 a 42 polegadas', 'TVs de 32 a 55 polegadas', 'TVs de 43 a 75 polegadas'],
  controles_remotos: MODELOS_TV.map(m => m === 'universal' ? 'todas as TVs' : 'TV ' + m),
  acessorios_fogao: MODELOS_ELETRO.map(m => 'fogão ' + m), acessorios_geladeira: MODELOS_ELETRO.map(m => 'geladeira ' + m),
  acessorios_lavadora: ['lavadora Brastemp', 'lavadora Electrolux', 'lavadora Consul', 'lavadora LG', 'lavadora Samsung', 'lavadora Midea'],
  acessorios_ar: ['split 9000 a 12000 BTUs', 'split 18000 a 24000 BTUs', 'ar-condicionado LG', 'ar-condicionado Samsung', 'ar-condicionado Midea'],
  refis_purificador: ['purificador IBBL', 'purificador Electrolux', 'purificador Esmaltec', 'purificador Libell', 'purificador Consul'],
}

// ---------------------------------------------------------------------------
// Vendedores (com perfil de qualidade: bons erram pouco, ruins erram muito)
// ---------------------------------------------------------------------------
const NOMES_LOJA = ['Mega', 'Top', 'Casa', 'Shop', 'Store', 'Outlet', 'Center', 'Express', 'Mix', 'Prime', 'Total', 'Mundo', 'Point', 'Brasil', 'Digital', 'Lar', 'Fácil', 'Super', 'Max', 'Ofertas']
function criarVendedor(i, novo = false) {
  const r = rnd()
  const perfil = r < 0.4 ? 'bom' : r < 0.8 ? 'medio' : 'ruim'
  return { id: (novo ? 'loja_9' : 'loja_') + String(i).padStart(3, '0'), nome: `${pick(NOMES_LOJA)} ${pick(NOMES_LOJA)} ${int(1, 99)}`, perfil, fator: { bom: 0.5, medio: 1, ruim: 1.8 }[perfil] * RUIDO }
}
const VENDEDORES = Array.from({ length: 400 }, (_, i) => criarVendedor(i + 1))
const VENDEDORES_NOVOS = Array.from({ length: 40 }, (_, i) => criarVendedor(i + 1, true))

// ---------------------------------------------------------------------------
// Verdade do produto
// ---------------------------------------------------------------------------
function valorAtributo(a, folha, marca) {
  if (a.tipo === 'enum') return pick(a.valores)
  if (a.tipo === 'num') { const passos = Math.floor((a.max - a.min) / a.passo + 1e-9); const v = a.min + int(0, passos) * a.passo; return Math.round(v * 100) / 100 }
  if (a.chave === 'compatibilidade') return pick(COMPAT[folha.id] || MODELOS_ELETRO)
  return null
}

function codigoModelo(marca) {
  const pre = semAcento(marca).replace(/[^A-Za-z]/g, '').slice(0, int(1, 3)).toUpperCase()
  const formatos = [() => `${pre}${int(10, 999)}`, () => `${pre}-${int(100, 9999)}`, () => `${pre}${int(1, 9)}${pick('ABCDEFGHJKLMNPRSTVWX')}${int(10, 99)}`, () => `${pick('ABCDEFGHJKLMNPRSTVWX')}${pick('ABCDEFGHJKLMNPRSTVWX')}${int(100, 999)}${pick(['', 'B', 'X', 'S', 'PRO'])}`]
  return pick(formatos)()
}

function eanValido() {
  const d = [7, 8, 9, ...Array.from({ length: 9 }, () => int(0, 9))]
  const soma = d.reduce((s, x, i) => s + x * (i % 2 ? 3 : 1), 0)
  return d.join('') + ((10 - soma % 10) % 10)
}

function criarVerdade(folha, opts = {}) {
  let marca = opts.marcaNova ? pick(MARCAS_NOVAS) : pick(MARCAS[folha.marcas])
  const atributos = {}
  for (const a of folha.esquema) atributos[a.chave] = valorAtributo(a, folha, marca)
  // Coerência entre marca e atributos
  if (folha.id === 'smartphones') atributos.sistema_operacional = marca === 'Apple' ? 'iOS' : 'Android'
  if (folha.id === 'smartwatches') atributos.sistema_operacional = marca === 'Apple' ? 'iOS' : 'Android'
  if (folha.id === 'consoles') marca = { 'PS5': 'Sony', 'Xbox Series': 'Microsoft', 'Nintendo Switch': 'Nintendo' }[atributos.plataforma]
  let termo = chance(0.55) ? folha.termos[0] : pick(folha.termos)
  if (termo === 'iPad') marca = 'Apple'
  if (marca === 'Apple' && folha.id === 'tablets' && chance(0.6)) termo = 'iPad'
  if (folha.id === 'jogos_videogame' && atributos.plataforma) termo = pick([`jogo para ${atributos.plataforma}`, 'game', 'jogo mídia física'])
  const modelo = codigoModelo(marca)
  const [pmin, pmax] = folha.preco
  const preco = Math.round((pmin + (pmax - pmin) * Math.pow(rnd(), 1.6)) * 100) / 100
  const linhas = LINHAS[folha.id]?.[marca]
  const linha = linhas ? pick(linhas) : null
  return { folha, marca, atributos, termo, modelo, linha, preco, ean: eanValido(), marcaNova: !!opts.marcaNova }
}

// ---------------------------------------------------------------------------
// Formatação de valores (no título e nos atributos do vendedor)
// ---------------------------------------------------------------------------
function sinonimos(chave, v) {
  if (chave === 'voltagem' && SINONIMOS_VALOR.voltagem[v]) return SINONIMOS_VALOR.voltagem[v]
  if (chave === 'cor' && SINONIMOS_VALOR.cor[v]) return SINONIMOS_VALOR.cor[v]
  if (v === 'sim') return SINONIMOS_VALOR.sim
  if (v === 'não') return SINONIMOS_VALOR['não']
  return [v]
}

function fmtTitulo(chave, v) {
  const def = ATRIBUTOS[chave]
  if (v === null || v === undefined) return null
  if (v === 'não' || v === 'nao') return null
  if (v === 'sim') return def.nome === 'Frost free' ? 'Frost Free' : def.nome
  if (chave === 'resistencia_agua') return pick([`À Prova D'Água ${v}`, v, `Resistente à Água ${v}`])
  if (chave === 'compatibilidade') return 'para ' + v
  if (chave === 'voltagem' || chave === 'cor') { const s = pick(sinonimos(chave, v)); return chave === 'cor' ? cap(s) : s }
  const num = typeof v === 'number' ? numBR(v) : v
  if (def.prefixoTitulo !== undefined) return def.prefixoTitulo + num + (def.unidade && !/^ /.test(def.unidade) ? def.unidade : '')
  if (def.unidade) {
    const u = def.unidade
    const variantes = [num + u, num + ' ' + u.trim(), def.longo ? num + ' ' + cap(def.longo) : num + u]
    return pick(variantes) + (def.sufixoTitulo || '')
  }
  return cap(String(v))
}

function fmtVendedor(chave, v, folha) {
  const def = ATRIBUTOS[chave]
  if (v === null || v === undefined) return { texto: null, misturado: false }
  if (chave === 'voltagem' || chave === 'cor' || v === 'sim' || v === 'não') return { texto: pick(sinonimos(chave, v)), misturado: false }
  if (typeof v === 'number') {
    // unidades misturadas: 1500 W -> 1,5 kW; 1000 ml -> 1 L; 2000 g -> 2 kg; 30 cm -> 300 mm
    if (chance(0.12)) {
      if (chave === 'potencia_w' && v >= 1000) return { texto: numBR(v / 1000) + ' kW', misturado: true }
      if ((chave === 'volume_ml' || chave === 'capacidade_ml') && v >= 1000) return { texto: numBR(v / 1000) + ' L', misturado: true }
      if (chave === 'peso_g' && v >= 1000) return { texto: numBR(v / 1000) + ' kg', misturado: true }
      if (chave === 'altura_cm' || chave === 'largura_cm' || chave === 'tamanho_cm') return { texto: numBR(v * 10) + ' mm', misturado: true }
      if (chave === 'capacidade_l' && v < 10) return { texto: numBR(v * 1000) + ' ml', misturado: true }
    }
    const u = def.unidade || ''
    return { texto: pick([numBR(v) + u.trim(), numBR(v) + ' ' + u.trim(), numBR(v), String(v)]).trim(), misturado: false }
  }
  if (def.unidade && /^\d/.test(v)) return { texto: pick([v + def.unidade.trim(), v + ' ' + def.unidade.trim(), v]), misturado: false }
  return { texto: pick([v, cap(v), v.toUpperCase()]), misturado: false }
}

function errarValor(chave, v, folha) {
  const a = folha.esquema.find(x => x.chave === chave)
  if (!a) return v
  if (a.tipo === 'enum' && a.valores.length > 1) return pick(a.valores.filter(x => x !== v))
  if (a.tipo === 'num') return Math.round((v * pick([0.5, 0.8, 1.25, 2])) * 10) / 10
  return v
}

// ---------------------------------------------------------------------------
// Renderização: verdade -> anúncio bagunçado
// ---------------------------------------------------------------------------
const INTROS = [
  (t, m, mo) => `${cap(t)} ${m} ${mo}: a escolha certa para o seu dia a dia.`.replace(/\s+/g, ' '),
  (t, m) => m ? `Conheça a linha ${m} de ${t}.` : `Conheça nosso ${t}.`,
  (t, m, mo) => m ? `Produto: ${t}. Marca: ${m}. Modelo: ${mo}.` : `Produto: ${t}. Modelo: ${mo}.`,
  t => `Procurando ${t}? Você encontrou!`,
  (t, m) => `${cap(t)} ${m} com qualidade e garantia.`.replace(/\s+/g, ' '),
  t => `${cap(t)} para facilitar a sua rotina.`,
  (t, m, mo) => m ? `${cap(t)} modelo ${mo}, fabricado por ${m}.` : `${cap(t)} modelo ${mo}.`,
  () => '',
]
const RODAPES = ['Imagens meramente ilustrativas.', 'Dúvidas? Chame no chat!', 'Envio em até 24h úteis.', 'Produto novo, lacrado e com nota fiscal.', 'Confira as medidas antes de comprar.', 'Garantia contra defeitos de fabricação.', '']

function renderizar(verdade, ctx) {
  const { folha, marca, atributos, termo, modelo, linha } = verdade
  const fator = ctx.vendedor.fator
  const p = x => Math.min(0.95, x * fator)
  const ruidos = []
  const visto = new Set()           // atributos que aparecem em algum campo de texto do anúncio
  let marcaVisivel = false
  let titulo

  // ---------- título ----------
  const titulaveis = folha.esquema.filter(a => ATRIBUTOS[a.chave].titulo)
  const escolhidos = pickN(titulaveis, Math.min(titulaveis.length, int(0, 4)))
  const frags = []
  for (const a of escolhidos) { const s = fmtTitulo(a.chave, atributos[a.chave]); if (s) { frags.push(s); visto.add(a.chave) } }
  let marcaNoTitulo = chance(0.72)
  let modeloNoTitulo = chance(0.35)
  let T = cap(termo)
  if (linha && chance(0.6)) {
    // título pela linha comercial; metade das vezes sem o nome do produto
    if (chance(0.5)) { T = linha; ruidos.push('titulo_so_com_linha') } else T = `${cap(termo)} ${linha}`
    if (!linha.toLowerCase().includes(marca.toLowerCase())) marcaNoTitulo = marcaNoTitulo || chance(0.5)
  } else if (chance(p(0.05))) {
    // título sem o nome do produto: só marca, modelo e atributos
    T = ''; modeloNoTitulo = true; marcaNoTitulo = true; ruidos.push('titulo_sem_nome_do_produto')
  }
  const M = marcaNoTitulo ? marca : ''
  const MO = modeloNoTitulo ? modelo : ''
  const at = frags.join(' ')
  const padroes = [
    () => `${T} ${M} ${at} ${MO}`,
    () => `${T} ${at} - ${M}`,
    () => `${M} ${T} ${MO} ${at}`,
    () => `${M} ${T} ${frags.join(', ')}${MO ? ' - ' + MO : ''}`,
    () => `${T} | ${frags.join(' | ')} | ${M}`,
    () => `${T} ${MO} ${M} ${at}`,
    () => `${T ? titleCase(T) : ''} ${at} ${M}`,
  ]
  titulo = pick(padroes)().replace(/\s+\|\s+\|/g, ' |').replace(/\s+-\s*$/, '').replace(/\|\s*$/, '').replace(/\s+/g, ' ').trim()
  if (marcaNoTitulo) marcaVisivel = true

  // kit / combo
  let tipoItem = folha.tipo
  if (folha.tipo === 'produto' && chance(0.03)) { titulo = `Kit ${int(2, 4)} ${titulo}`; tipoItem = 'kit'; ruidos.push('kit') }
  else if (folha.tipo === 'produto' && chance(0.025)) {
    const acess = FOLHAS.filter(x => x.de.includes(folha.id))
    if (acess.length) { titulo = `${titulo} + ${cap(pick(pick(acess).termos))}`; ruidos.push('combo_com_acessorio') }
  }

  // ruído no título
  if (ctx.ingles && chance(0.9)) { titulo = `${marca} ${folha.nome_en.replace(/s$/, '')} ${frags.slice(0, 2).join(' ')}`.trim(); marcaVisivel = true; ruidos.push('titulo_em_ingles') }
  if (chance(p(0.22))) { const pr = pickN(PROMO, int(1, 2)); titulo = chance(0.5) ? `${pr.join(' ')} ${titulo}` : `${titulo} ${pr.join(' ')}`; ruidos.push('titulo_promocional') }
  if (chance(p(0.06))) { titulo = titulo + ' ' + pick(ISCAS); ruidos.push('palavra_isca') }
  if (chance(p(0.07))) { let t2 = titulo; for (const [re, s] of ABREVIACOES) if (chance(0.7)) t2 = t2.replace(re, s); if (t2 !== titulo) { titulo = t2; ruidos.push('abreviacao') } }
  if (chance(p(0.05))) { const ws = titulo.split(' '); const i = ws.findIndex(w => w.length >= 5 && !/\d/.test(w)); if (i >= 0) { ws[i] = typo(ws[i]); titulo = ws.join(' '); ruidos.push('erro_digitacao') } }
  if (chance(p(0.06))) { titulo = semAcento(titulo); ruidos.push('sem_acento') }
  if (chance(p(0.08))) { titulo = titulo.toUpperCase(); ruidos.push('titulo_maiusculo') }
  if (chance(p(0.04)) && titulo.length > 45) { titulo = titulo.slice(0, int(30, 45)); ruidos.push('titulo_cortado') }

  // ---------- atributos informados pelo vendedor ----------
  const atributosVendedor = {}
  const taxaInforma = { bom: 0.85, medio: 0.6, ruim: 0.3 }[ctx.vendedor.perfil]
  let conflito = false
  const vendedorErrou = new Set()
  for (const a of folha.esquema) {
    if (!chance(taxaInforma)) continue
    const chaveV = chance(0.6) ? ATRIBUTOS[a.chave].aliases[0] : pick(ATRIBUTOS[a.chave].aliases)
    let v = atributos[a.chave]
    if (chance(p(0.03)) && !visto.has(a.chave)) { v = errarValor(a.chave, v, folha); if (v !== atributos[a.chave]) vendedorErrou.add(a.chave) } // valor errado, sem outra pista no texto
    else if (chance(p(0.04)) && visto.has(a.chave)) { v = errarValor(a.chave, v, folha); conflito = true } // conflita com o título
    const { texto, misturado } = fmtVendedor(a.chave, v, folha)
    if (texto === null) continue
    atributosVendedor[chaveV] = texto
    if (v === atributos[a.chave]) visto.add(a.chave)
    if (misturado && !ruidos.includes('unidade_misturada')) ruidos.push('unidade_misturada')
  }
  if (conflito) ruidos.push('atributo_conflitante')
  if (Object.keys(atributosVendedor).length && chance(0.3)) { const [k, v] = pick([['Conteúdo da embalagem', `1 ${termo}`], ['Itens inclusos', `1 ${termo}, manual`], ['Código interno', `REF${int(1000, 9999)}`], ['SAC', '0800 000 0000']]); rnd(); atributosVendedor[k] = v }
  if (Object.keys(atributosVendedor).length === 0 && folha.esquema.length) ruidos.push('sem_atributos')

  // ---------- marca informada ----------
  let marcaInformada
  const r = rnd()
  if (r < 0.60) { marcaInformada = marca; marcaVisivel = true }
  else if (r < 0.68) { marcaInformada = chance(0.5) ? marca.toUpperCase() : marca.toLowerCase(); marcaVisivel = true; ruidos.push('marca_caixa_diferente') }
  else if (r < 0.73) { marcaInformada = typo(marca); marcaVisivel = true; ruidos.push('marca_com_erro') }
  else if (r < 0.85) { marcaInformada = ''; ruidos.push('marca_ausente') }
  else if (r < 0.91) { marcaInformada = pick(MARCAS_GENERICAS); ruidos.push('marca_generica') }
  else if (r < 0.95) { marcaInformada = ctx.vendedor.nome; ruidos.push('marca_nome_da_loja') }
  else { marcaInformada = pick(MARCAS[folha.marcas].filter(m => m !== marca)) || ''; ruidos.push('marca_errada') }

  // ---------- bullets ----------
  const bullets = []
  const nBullets = { bom: int(3, 5), medio: int(0, 4), ruim: int(0, 2) }[ctx.vendedor.perfil]
  const benef = BENEFICIOS_CAT[folha.cat.id] || BENEFICIOS[folha.dep.id]
  for (let i = 0; i < nBullets; i++) {
    if (chance(0.5) && folha.esquema.length) {
      const a = pick(folha.esquema); const v = atributos[a.chave]
      if (v !== null && v !== 'não') { const s = fmtTitulo(a.chave, v) || String(v); bullets.push(pick([`${ATRIBUTOS[a.chave].nome}: ${s}`, `${s}`, `${ATRIBUTOS[a.chave].nome} de ${s}`].slice(0, ATRIBUTOS[a.chave].unidade ? 3 : 2))); visto.add(a.chave); continue }
    }
    bullets.push(pick(benef))
  }

  // ---------- descrição ----------
  let descricao
  let descricaoCopiada = false
  if (ctx.descricaoDeOutro && chance(p(0.02))) { descricao = ctx.descricaoDeOutro; descricaoCopiada = true; ruidos.push('descricao_de_outro_produto') }
  else {
    const partes = []
    const mDesc = chance(0.6) ? marca : ''
    if (mDesc) marcaVisivel = true
    partes.push(pick(INTROS)(termo, mDesc, modelo))
    partes.push(...pickN(benef, int(1, 3)))
    if (chance(0.45) && folha.esquema.length) {
      const specs = pickN(folha.esquema, int(1, folha.esquema.length)).map(a => { const v = atributos[a.chave]; if (v === null) return null; visto.add(a.chave); return `${ATRIBUTOS[a.chave].nome}: ${fmtVendedor(a.chave, v, folha).texto}` }).filter(Boolean)
      if (specs.length) partes.push('Especificações técnicas: ' + specs.join(' | ') + '.')
    }
    if (folha.conf.length && chance(0.08)) { const outra = POR_ID[pick(folha.conf)]; partes.push(pick([`Alternativa a ${outra.termos[0]}.`, `Não confundir com ${outra.termos[0]}.`, `Dispensa ${outra.termos[0]}.`, `Pode ser usado junto com ${outra.termos[0]}.`])); ruidos.push('descricao_cita_outra_categoria') }
    if (atributos.garantia_meses) { partes.push(`Garantia de ${atributos.garantia_meses} meses.`); visto.add('garantia_meses') }
    partes.push(`Conteúdo da embalagem: 1 ${termo}${chance(0.5) ? ', manual de instruções' : ''}.`)
    partes.push(pick(RODAPES))
    descricao = partes.filter(Boolean).join(' ')
    if (chance(0.3)) { descricao = '<p>' + descricao.replace(/\. /g, '.</p><p>') + '</p>'; ruidos.push('descricao_html') }
    if (ctx.vendedor.perfil === 'ruim' && chance(0.25)) { descricao = descricao.slice(0, int(20, 80)); ruidos.push('descricao_curta') }
  }

  // ---------- categoria do vendedor ----------
  let categoriaVendedor, statusCategoria
  const rc = rnd() * (ctx.vendedor.perfil === 'ruim' ? 0.7 : 1) + (ctx.vendedor.perfil === 'ruim' ? 0.3 : 0)
  const irmas = folha.cat.folhas.filter(x => x.id !== folha.id)
  if (ctx.ingles && chance(0.7)) { categoriaVendedor = CATEGORIAS_INGLES[folha.dep.id]; statusCategoria = 'parcial_ingles' }
  else if (rc < 0.50) { categoriaVendedor = pick([folha.caminho, `${folha.dep.nome} > ${folha.nome}`, `${folha.dep.nome}/${folha.cat.nome}/${folha.nome}`]); statusCategoria = 'correta' }
  else if (rc < 0.56) { categoriaVendedor = pick([folha.nome, folha.nome.toLowerCase(), semAcento(folha.nome)]); statusCategoria = 'correta' }
  else if (rc < 0.64) { categoriaVendedor = `${folha.dep.nome} > ${folha.cat.nome}`; statusCategoria = 'parcial' }
  else if (rc < 0.73 && irmas.length) { const x = POR_ID[pick(irmas).id]; categoriaVendedor = x.caminho; statusCategoria = 'errada_irma' }
  else if (rc < 0.80 && folha.conf.length) { categoriaVendedor = POR_ID[pick(folha.conf)].caminho; statusCategoria = 'errada_confusao' }
  else if (rc < 0.90) { categoriaVendedor = pick(CATEGORIAS_GENERICAS); statusCategoria = 'generica' }
  else if (rc < 0.94) { categoriaVendedor = pick(FOLHAS).caminho; statusCategoria = 'errada_aleatoria' }
  else { categoriaVendedor = ''; statusCategoria = 'vazia' }
  if (statusCategoria.startsWith('errada')) ruidos.push('categoria_vendedor_errada')
  if (statusCategoria === 'generica') ruidos.push('categoria_vendedor_generica')
  if (statusCategoria === 'vazia') ruidos.push('categoria_vendedor_vazia')

  // ---------- tags ----------
  const tags = [termo, folha.nome.toLowerCase()]
  if (chance(p(0.25))) { tags.push(...pickN(['iphone', 'air fryer', 'smart tv', 'fogão', 'black friday', 'promoção', 'frete grátis', 'original', 'lançamento', 'samsung', 'nike', 'presente'], int(2, 5))); ruidos.push('tags_enganosas') }

  // ---------- embalagem ----------
  const grande = folha.grande
  let alt = grande ? int(50, 190) : int(4, 60), lar = grande ? int(40, 110) : int(4, 50), prof = grande ? int(30, 90) : int(2, 40)
  let peso = grande ? int(12, 95) : Math.round((0.05 + rnd() * 6) * 100) / 100
  let embalagem = { altura_cm: alt, largura_cm: lar, profundidade_cm: prof, peso_kg: peso }
  if (chance(0.1)) { embalagem = { altura_mm: alt * 10, largura_mm: lar * 10, profundidade_mm: prof * 10, peso_g: Math.round(peso * 1000) }; ruidos.push('embalagem_em_mm_g') }

  // ---------- preço e identificação ----------
  const preco = ctx.preco ?? verdade.preco
  const precoTxt = pick([`R$ ${preco.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`, preco.toFixed(2), preco.toLocaleString('pt-BR', { minimumFractionDigits: 2 }), String(Math.round(preco))])
  let ean = null
  const re = rnd()
  if (re < (ctx.manterEan ? 0.7 : 0.75)) ean = verdade.ean
  else if (re < 0.8) { ean = verdade.ean.slice(0, 12) + ((Number(verdade.ean[12]) + int(1, 8)) % 10); ruidos.push('ean_invalido') }
  if (ean === null && !ruidos.includes('ean_invalido')) ruidos.push('ean_ausente')

  // ---------- visibilidade da marca em outros campos ----------
  if (!marcaVisivel && (titulo.toLowerCase().includes(marca.toLowerCase()) || descricao.toLowerCase().includes(marca.toLowerCase()) || bullets.join(' ').toLowerCase().includes(marca.toLowerCase()))) marcaVisivel = true
  if (marcaInformada && semAcento(marcaInformada.toLowerCase()) === semAcento(marca.toLowerCase())) marcaVisivel = true

  const anuncio = {
    titulo,
    bullets,
    descricao,
    marca_informada: marcaInformada,
    modelo: chance(0.6) ? modelo : '',
    ean,
    categoria_vendedor: categoriaVendedor,
    tags: tags.join(','),
    atributos: atributosVendedor,
    embalagem,
    preco: precoTxt,
    condicao: chance(0.92) ? 'novo' : pick(['recondicionado', 'usado', 'seminovo']),
    vendedor: { id: ctx.vendedor.id, nome: ctx.vendedor.nome },
  }
  if (vendedorErrou.size) ruidos.push('atributo_errado_pelo_vendedor')
  return { anuncio, ruidos, visto, marcaVisivel, tipoItem, statusCategoria, vendedorErrou }
}

// ---------------------------------------------------------------------------
// Dificuldade
// ---------------------------------------------------------------------------
const PESO_RUIDO = { titulo_so_com_linha: 1.5, titulo_sem_nome_do_produto: 2, categoria_vendedor_errada: 2, categoria_vendedor_generica: 1.5, categoria_vendedor_vazia: 1.5, titulo_em_ingles: 2, descricao_de_outro_produto: 2.5, palavra_isca: 1.5, abreviacao: 1, erro_digitacao: 1, titulo_cortado: 1.5, atributo_conflitante: 1.5, combo_com_acessorio: 1.5, descricao_cita_outra_categoria: 1, marca_errada: 1, titulo_promocional: 0.5, tags_enganosas: 0.5, sem_atributos: 0.5, descricao_curta: 1 }
function dificuldade(ruidos, folha) {
  let s = ruidos.reduce((acc, r) => acc + (PESO_RUIDO[r] || 0.2), 0)
  if (folha.conf.length) s += 0.8
  if (folha.tipo !== 'produto') s += 0.5
  return s < 1.5 ? 'facil' : s < 3.5 ? 'medio' : 'dificil'
}

// ---------------------------------------------------------------------------
// Montagem de um registro
// ---------------------------------------------------------------------------
let seqProduto = 0, seqNovo = 0, seqVariacao = 0
const DATA_INI = Date.UTC(2019, 0, 1), DATA_FIM = Date.UTC(2026, 8, 30)
const dataAleatoria = (ini = DATA_INI, fim = DATA_FIM) => new Date(ini + rnd() * (fim - ini)).toISOString().slice(0, 10)

function registro(id, verdade, ctx, extraGabarito = {}) {
  const { anuncio, ruidos, visto, marcaVisivel, tipoItem, statusCategoria, vendedorErrou } = renderizar(verdade, ctx)
  const folha = verdade.folha
  const atributos = {}, inferiveis = []
  const errados = []
  for (const a of folha.esquema) { atributos[a.chave] = verdade.atributos[a.chave]; if (visto.has(a.chave)) inferiveis.push(a.chave); else if (vendedorErrou.has(a.chave)) errados.push(a.chave) }
  const gabarito = {
    departamento: folha.dep.id, categoria: folha.cat.id, subcategoria: folha.id, caminho: folha.caminho,
    tipo_item: tipoItem, acessorio_de: folha.de.length ? folha.de : undefined,
    marca: marcaVisivel ? verdade.marca : 'nao_identificada', marca_real: verdade.marca, marca_nova: verdade.marcaNova || undefined,
    modelo: verdade.modelo, linha: verdade.linha || undefined, atributos, atributos_inferiveis: inferiveis, atributos_errados_pelo_vendedor: errados.length ? errados : undefined,
    categoria_vendedor_status: statusCategoria,
    ruidos, dificuldade: dificuldade(ruidos, folha), perfil_vendedor: ctx.vendedor.perfil,
    ...extraGabarito,
  }
  return { id, sku: `SKU${int(1000000, 9999999)}`, ...anuncio, data_cadastro: ctx.data || dataAleatoria(), gabarito }
}

// ---------------------------------------------------------------------------
// Catálogo atual
// ---------------------------------------------------------------------------
fs.mkdirSync(SAIDA, { recursive: true })
const pesoFolha = fo => fo.peso
const produtos = []
const verdades = []          // índice -> verdade (para duplicados e variações)
const novoId = () => 'P' + String(++seqProduto).padStart(7, '0')
console.log(`Gerando ${N_PRODUTOS} produtos (semente ${SEMENTE})...`)

while (produtos.length < N_PRODUTOS) {
  const r = rnd()
  // duplicado de um produto anterior (outro vendedor, outro anúncio, mesmo produto)
  if (r < 0.04 && produtos.length > 500) {
    const k = int(0, produtos.length - 1)
    const orig = produtos[k]; const v = verdades[k]
    const raiz = orig.gabarito.duplicado_de || orig.id
    const vend = pick(VENDEDORES)
    produtos.push(registro(novoId(), v, { vendedor: vend, ingles: chance(0.03), preco: Math.round(v.preco * (0.85 + rnd() * 0.3) * 100) / 100, manterEan: true, descricaoDeOutro: produtos[int(0, produtos.length - 1)].descricao }, { duplicado_de: raiz, variacao_de: orig.gabarito.variacao_de }))
    verdades.push(v)
    continue
  }
  const folha = pickPeso(FOLHAS, pesoFolha)
  const v = criarVerdade(folha)
  const vend = pick(VENDEDORES)
  const base = registro(novoId(), v, { vendedor: vend, ingles: chance(0.03), descricaoDeOutro: produtos.length ? produtos[int(0, produtos.length - 1)].descricao : null })
  produtos.push(base); verdades.push(v)
  // família de variações (mesmo produto em outra cor, voltagem ou tamanho)
  const eixos = folha.esquema.filter(a => ['cor', 'voltagem', 'tamanho_roupa', 'tamanho_calcado', 'tamanho_numerico', 'armazenamento_gb', 'tamanho_cama', 'tamanho_fralda', 'tamanho_pet'].includes(a.chave) && a.tipo === 'enum' && a.valores.length > 1)
  if (eixos.length && chance(0.05)) {
    const eixo = pick(eixos)
    const grupo = 'GV' + String(++seqVariacao).padStart(5, '0')
    base.grupo_variacao = chance(0.6) ? grupo : ''
    base.gabarito.grupo_variacao = grupo
    const outros = pickN(eixo.valores.filter(x => x !== v.atributos[eixo.chave]), int(1, 3))
    for (const val of outros) {
      if (produtos.length >= N_PRODUTOS) break
      const v2 = { ...v, atributos: { ...v.atributos, [eixo.chave]: val }, ean: eanValido(), modelo: v.modelo }
      const filho = registro(novoId(), v2, { vendedor: vend, ingles: false, descricaoDeOutro: null }, { variacao_de: base.id, grupo_variacao: grupo, eixo_variacao: eixo.chave })
      filho.grupo_variacao = base.grupo_variacao
      produtos.push(filho); verdades.push(v2)
    }
  }
}

// ---------------------------------------------------------------------------
// Itens novos (fluxo de cadastro)
// ---------------------------------------------------------------------------
console.log(`Gerando ${N_NOVOS} itens novos...`)
const novos = []
const DATA_NOVOS_INI = Date.UTC(2026, 9, 1), DATA_NOVOS_FIM = Date.UTC(2026, 9, 31)
const novoIdN = () => 'N' + String(++seqNovo).padStart(6, '0')
// Item fora da taxonomia (mesmo código e mesma ordem de sorteios do bloco original; só virou função para a rodada 8 reutilizar)
function criarFora(fx, data) {
  const parecido = POR_ID[fx.parecido]
  const termo = pick(fx.termos), marca = pick(fx.marcas)
  const cor = pick(['preto', 'branco', 'cinza'])
  const titulo = pick([`${cap(termo)} ${marca} ${cap(cor)}`, `${marca} ${cap(termo)} ${codigoModelo(marca)}`, `${cap(termo)} ${pick(PROMO)}`])
  const vend = pick([...VENDEDORES, ...VENDEDORES_NOVOS])
  return {
    id: novoIdN(), sku: `SKU${int(1000000, 9999999)}`, titulo, bullets: pickN(BENEFICIOS_CAT[parecido.cat.id] || BENEFICIOS[parecido.dep.id], int(0, 3)),
    descricao: `${cap(termo)} ${marca}. ${pick(BENEFICIOS_CAT[parecido.cat.id] || BENEFICIOS[parecido.dep.id])}`, marca_informada: chance(0.7) ? marca : '', modelo: '', ean: chance(0.6) ? eanValido() : null,
    categoria_vendedor: pick([parecido.caminho, pick(CATEGORIAS_GENERICAS), '']), tags: termo, atributos: chance(0.5) ? { Cor: cor } : {},
    embalagem: { altura_cm: int(10, 60), largura_cm: int(10, 60), profundidade_cm: int(5, 40), peso_kg: Math.round(rnd() * 8 * 100) / 100 },
    preco: String(int(150, 5000)), condicao: 'novo', vendedor: { id: vend.id, nome: vend.nome }, data_cadastro: data,
    gabarito: { fora_da_taxonomia: true, departamento: null, categoria: null, subcategoria: null, sugestao_nova_subcategoria: fx.id, nome_sugerido: fx.nome, departamento_provavel: fx.dep, parecido_com: fx.parecido, marca, tipo_novo: 'fora_da_taxonomia', ruidos: [], dificuldade: 'dificil' },
  }
}
const FORA_POOL = R8 ? FORA_TAXONOMIA.filter(x => GRUPOS_FORA.teste.includes(x.id)) : FORA_TAXONOMIA // rodada 8: só os tipos do grupo de teste entram nos itens novos
while (novos.length < N_NOVOS) {
  const r = rnd()
  const data = dataAleatoria(DATA_NOVOS_INI, DATA_NOVOS_FIM)
  if (r < 0.15) {
    // duplicado de algo que já está no catálogo
    const k = int(0, produtos.length - 1); const orig = produtos[k]; const v = verdades[k]
    const reg = registro(novoIdN(), v, { vendedor: pick([...VENDEDORES, ...VENDEDORES_NOVOS]), ingles: chance(0.03), preco: Math.round(v.preco * (0.85 + rnd() * 0.3) * 100) / 100, manterEan: true, data, descricaoDeOutro: null }, { duplicado_de: orig.gabarito.duplicado_de || orig.id, tipo_novo: 'duplicado_do_catalogo' })
    novos.push(reg)
  } else if (r < 0.23) {
    // produto que não existe na taxonomia
    const fx = pick(FORA_POOL)
    const itemFora = criarFora(fx, data)
    if (R8) itemFora.gabarito.grupo_fora = 'teste'
    novos.push(itemFora)
  } else if (r < 0.25) {
    // cadastro inválido
    const vend = pick(VENDEDORES_NOVOS)
    novos.push({ id: novoIdN(), sku: `SKU${int(1000000, 9999999)}`, titulo: pick(['teste', 'produto', 'aaaa', 'Produto sem nome', 'item 123', '.', 'NÃO COMPRAR', 'kit']), bullets: [], descricao: pick(['', 'teste', 'descrição']), marca_informada: '', modelo: '', ean: null, categoria_vendedor: pick(['', 'Diversos']), tags: '', atributos: {}, embalagem: {}, preco: pick(['0', '1', '99999']), condicao: 'novo', vendedor: { id: vend.id, nome: vend.nome }, data_cadastro: data, gabarito: { invalido: true, departamento: null, categoria: null, subcategoria: null, tipo_novo: 'invalido', ruidos: ['cadastro_invalido'], dificuldade: 'facil' } })
  } else {
    const folha = pickPeso(FOLHAS, pesoFolha)
    const marcaNova = chance(0.12)
    const v = criarVerdade(folha, { marcaNova })
    const vend = chance(0.12) ? pick(VENDEDORES_NOVOS) : pick(VENDEDORES)
    novos.push(registro(novoIdN(), v, { vendedor: vend, ingles: chance(0.04), data, descricaoDeOutro: null }, { tipo_novo: marcaNova ? 'marca_nova' : (vend.id.startsWith('loja_9') ? 'vendedor_novo' : 'normal') }))
  }
}

// Rodada 8: ruídos de atributo (F2), árvore legada (F1), ambiguidade e itens novos extras (usa o gerador próprio rnd8)
const r8 = R8 ? posProcessarR8({ produtos, novos, FOLHAS, POR_ID, ATRIBUTOS, SINONIMOS_VALOR, DEPARTAMENTOS, SEMENTE, N_NOVOS, registro, criarVerdade, verdades, VENDEDORES, VENDEDORES_NOVOS, novoIdN, criarFora, eanValido, FORA_TAXONOMIA }) : null

// ---------------------------------------------------------------------------
// Buscas (a relevância é calculada pelo gabarito: subcategoria + filtros + marca)
// ---------------------------------------------------------------------------
console.log('Gerando buscas...')
const idxFolha = {}
for (const pr of produtos) (idxFolha[pr.gabarito.subcategoria] ||= []).push(pr)
function relevantes(b) {
  let n = 0, parcial = 0
  for (const fo of b.folhas) for (const pr of (idxFolha[fo] || [])) {
    const g = pr.gabarito
    if (b.marca && g.marca_real !== b.marca) continue
    const filtros = Object.entries(b.filtros || {})
    const ok = filtros.filter(([k, v]) => String(g.atributos[k]) === String(v)).length
    if (ok === filtros.length) n++; else parcial++
  }
  return { n, parcial }
}
const buscas = []
const vistas = new Set()
function addBusca(b) {
  const q = b.q.trim().toLowerCase()
  if (vistas.has(q)) return
  const { n, parcial } = relevantes(b)
  if (n === 0) return
  vistas.add(q)
  buscas.push({ id: 'Q' + String(buscas.length + 1).padStart(4, '0'), q: b.q, tipo: b.tipo, intencao: { subcategorias: b.folhas, filtros: b.filtros || {}, marca: b.marca || null }, armadilhas: b.armadilhas || [], ambigua: !!b.ambigua, ...(b.modelo ? { modelo: b.modelo } : {}), n_relevantes: n, n_parcialmente_relevantes: parcial })
}
for (const b of BUSCAS_ARMADILHA) addBusca({ ...b, tipo: b.erro ? 'erro_digitacao' : b.ingles ? 'ingles' : b.sinonimo ? 'sinonimo' : 'armadilha' })
for (const fo of FOLHAS) {
  const t0 = fo.termos[0]
  addBusca({ q: t0, folhas: [fo.id], tipo: 'cabeca', armadilhas: fo.conf })
  const enums = fo.esquema.filter(a => a.tipo === 'enum' && ATRIBUTOS[a.chave].titulo && a.chave !== 'compatibilidade')
  for (let k = 0; k < 2 && enums.length; k++) {
    const as = pickN(enums, Math.min(enums.length, int(1, 2)))
    const filtros = {}; const partes = [t0]
    for (const a of as) { const v = pick(a.valores); filtros[a.chave] = v; const s = fmtTitulo(a.chave, v); if (s) partes.push(s.toLowerCase()); else delete filtros[a.chave] }
    if (Object.keys(filtros).length) addBusca({ q: partes.join(' '), folhas: [fo.id], filtros, tipo: 'atributo', armadilhas: fo.conf })
  }
  if (chance(0.6)) {
    const marcasFolha = Object.entries((idxFolha[fo.id] || []).reduce((acc, pr) => (acc[pr.gabarito.marca_real] = (acc[pr.gabarito.marca_real] || 0) + 1, acc), {})).filter(([, n]) => n >= 5)
    if (marcasFolha.length) { const m = pick(marcasFolha)[0]; addBusca({ q: `${t0} ${m.toLowerCase()}`, folhas: [fo.id], marca: m, tipo: 'marca', armadilhas: fo.conf }) }
  }
  if (chance(0.35)) addBusca({ q: t0.split(' ').map((w, i) => i === 0 ? typo(w) : w).join(' '), folhas: [fo.id], tipo: 'erro_digitacao' })
  if (fo.termos.length > 1 && chance(0.4)) addBusca({ q: fo.termos[int(1, fo.termos.length - 1)], folhas: [fo.id], tipo: 'sinonimo', armadilhas: fo.conf })
}

if (R8) buscasR8({ addBusca, buscas, idxFolha, ATRIBUTOS })

// ---------------------------------------------------------------------------
// Taxonomia publicada (o que o agente pode ler)
// ---------------------------------------------------------------------------
function criterio(fo) {
  let pt = `${fo.nome}: ${fo.termos.join(', ')}.`
  let en = `${fo.nome_en}.`
  if (fo.tipo === 'acessorio') { pt += ` Peças e acessórios, não o produto principal (${fo.de.map(x => POR_ID[x].nome).join(', ')}).`; en += ` Parts and accessories, not the main product (${fo.de.map(x => POR_ID[x].nome_en).join(', ')}).` }
  if (fo.tipo === 'consumivel' && fo.de.length) { pt += ` Consumível para ${fo.de.map(x => POR_ID[x].nome).join(', ')}.`; en += ` Consumable for ${fo.de.map(x => POR_ID[x].nome_en).join(', ')}.` }
  if (fo.conf.length) { pt += ` Não inclui: ${fo.conf.map(x => POR_ID[x].nome).join(', ')}.`; en += ` Excludes: ${fo.conf.map(x => POR_ID[x].nome_en).join(', ')}.` }
  return { pt, en }
}
const taxonomia = {
  versao: 2,
  observacao: 'Taxonomia do catálogo sintético v2. Pode ser lida pelo agente. Os critérios em inglês servem para modelos treinados em inglês.',
  departamentos: DEPARTAMENTOS.map(dep => ({
    id: dep.id, nome: dep.nome, nome_en: dep.nome_en,
    criterio: `${dep.nome}: ${dep.categorias.map(c => c.nome).join(', ')}.`, criterio_en: `${dep.nome_en}: ${dep.categorias.map(c => c.nome_en).join(', ')}.`,
    categorias: dep.categorias.map(cat => ({
      id: cat.id, nome: cat.nome, nome_en: cat.nome_en,
      criterio: `${cat.nome}: ${cat.folhas.map(x => x.nome).join(', ')}.`, criterio_en: `${cat.nome_en}: ${cat.folhas.map(x => x.nome_en).join(', ')}.`,
      subcategorias: cat.folhas.map(fo0 => {
        const fo = POR_ID[fo0.id]; const cr = criterio(fo)
        return {
          id: fo.id, nome: fo.nome, nome_en: fo.nome_en, caminho: fo.caminho, termos: fo.termos, tipo: fo.tipo,
          acessorio_de: fo.de, confunde_com: fo.conf, criterio: cr.pt, criterio_en: cr.en,
          atributos: fo.esquema.map(a => ({ chave: a.chave, nome: ATRIBUTOS[a.chave].nome, nome_en: ATRIBUTOS[a.chave].en, tipo: a.tipo, valores: a.valores, min: a.min, max: a.max, unidade: ATRIBUTOS[a.chave].unidade?.trim(), valores_compativeis: a.chave === 'compatibilidade' ? (COMPAT[fo.id] || MODELOS_ELETRO) : undefined })),
        }
      }),
    })),
  })),
  atributos: Object.fromEntries(Object.entries(ATRIBUTOS).map(([k, v]) => [k, { nome: v.nome, nome_en: v.en, unidade: v.unidade?.trim(), aliases_vendedor: v.aliases }])),
  sinonimos_valor: SINONIMOS_VALOR,
  categorias_genericas: CATEGORIAS_GENERICAS,
  ...(R8 ? { sobreposicoes: SOBREPOSICOES.map(x => ({ subcategorias: [x.a, x.b], regra_principal: REGRA_PRINCIPAL })) } : {}),
  valores_especiais: { nao_informado: 'o anúncio não traz o valor', nao_se_aplica: 'o atributo não existe para essa subcategoria', nao_identificada: 'marca que não aparece em nenhum campo do anúncio' },
}

// ---------------------------------------------------------------------------
// Splits sem vazamento (duplicados e variações ficam no mesmo grupo)
// ---------------------------------------------------------------------------
console.log('Montando splits...')
const raiz = {}
for (const pr of produtos) {
  const g = pr.gabarito
  raiz[pr.id] = g.variacao_de || g.duplicado_de || pr.id
}
// resolve cadeias (duplicado de uma variação, etc.)
const resolver = id => { let x = id, guard = 0; while (raiz[x] && raiz[x] !== x && guard++ < 10) x = raiz[x]; return x }
const grupos = {}
for (const pr of produtos) (grupos[resolver(pr.id)] ||= []).push(pr.id)
const chavesGrupo = shuffle(Object.keys(grupos))
const porDep = {}
for (const k of chavesGrupo) { const pr = produtos[Number(k.slice(1)) - 1]; (porDep[pr.gabarito.departamento] ||= []).push(k) }
function tirarEstratificado(n, jaUsados) {
  const out = []; const deps = Object.keys(porDep)
  const total = deps.reduce((s, d) => s + porDep[d].length, 0)
  for (const d of deps) {
    const quota = Math.round(n * porDep[d].length / total)
    let tirados = 0
    for (const k of porDep[d]) { if (tirados >= quota) break; if (jaUsados.has(k)) continue; jaUsados.add(k); out.push(...grupos[k]); tirados += grupos[k].length }
  }
  return out
}
const usados = new Set()
const golden = tirarEstratificado(1000, usados)
const teste = tirarEstratificado(500, usados)
const validacao = tirarEstratificado(2000, usados)
const treino = []
for (const k of chavesGrupo) { if (usados.has(k)) continue; usados.add(k); treino.push(...grupos[k]); if (treino.length >= 20000) break }
const restante = []
for (const k of chavesGrupo) if (!usados.has(k)) restante.push(...grupos[k])
const novosPorTipo = {}
for (const n of novos) (novosPorTipo[n.gabarito.tipo_novo] ||= []).push(n.id)
const novosTeste = []
for (const [t, ids] of Object.entries(novosPorTipo)) novosTeste.push(...ids.slice(0, Math.round(1000 * ids.length / novos.length)))
const splits = {
  observacao: 'Grupos de duplicados e variações ficam inteiros no mesmo split. treino_200, treino_2000 e treino_5000 são prefixos de treino_20000.',
  golden: golden, teste_rodadas: teste, validacao,
  treino_200: treino.slice(0, 200), treino_2000: treino.slice(0, 2000), treino_5000: treino.slice(0, 5000), treino_20000: treino.slice(0, 20000),
  saneamento_restante: restante.length,
  novos_teste: novosTeste,
}

// ---------------------------------------------------------------------------
// Estatísticas e escrita
// ---------------------------------------------------------------------------
console.log('Escrevendo arquivos...')
const cont = (arr, fn) => arr.reduce((acc, x) => { for (const k of [].concat(fn(x))) if (k !== undefined) acc[k] = (acc[k] || 0) + 1; return acc }, {})
const pct = (n, t) => Math.round(n / t * 1000) / 10
const totalAtr = produtos.reduce((s, p) => s + Object.keys(p.gabarito.atributos).length, 0)
const totalInf = produtos.reduce((s, p) => s + p.gabarito.atributos_inferiveis.length, 0)
const vendCorreta = produtos.filter(p => p.gabarito.categoria_vendedor_status === 'correta').length
const estat = {
  produtos: produtos.length, novos: novos.length, buscas: buscas.length,
  departamentos: DEPARTAMENTOS.length, categorias: DEPARTAMENTOS.reduce((s, d) => s + d.categorias.length, 0), subcategorias: FOLHAS.length,
  atributos_distintos: Object.keys(ATRIBUTOS).length,
  atributos_por_produto_media: Math.round(totalAtr / produtos.length * 10) / 10,
  atributos_inferiveis_pct: pct(totalInf, totalAtr),
  atributos_informados_pelo_vendedor_media: Math.round(produtos.reduce((s, p) => s + Object.keys(p.atributos).length, 0) / produtos.length * 10) / 10,
  categoria_vendedor_correta_pct: pct(vendCorreta, produtos.length),
  categoria_vendedor_status: cont(produtos, p => p.gabarito.categoria_vendedor_status),
  marca_identificavel_pct: pct(produtos.filter(p => p.gabarito.marca !== 'nao_identificada').length, produtos.length),
  dificuldade: cont(produtos, p => p.gabarito.dificuldade),
  tipo_item: cont(produtos, p => p.gabarito.tipo_item),
  duplicados: produtos.filter(p => p.gabarito.duplicado_de).length,
  em_familias_de_variacao: produtos.filter(p => p.gabarito.grupo_variacao).length,
  perfil_vendedor: cont(produtos, p => p.gabarito.perfil_vendedor),
  ruidos: Object.fromEntries(Object.entries(cont(produtos, p => p.gabarito.ruidos)).sort((a, b) => b[1] - a[1])),
  por_departamento: cont(produtos, p => p.gabarito.departamento),
  novos_por_tipo: cont(novos, n => n.gabarito.tipo_novo),
  buscas_por_tipo: cont(buscas, b => b.tipo),
  splits: Object.fromEntries(Object.entries(splits).filter(([, v]) => Array.isArray(v)).map(([k, v]) => [k, v.length])),
  semente: SEMENTE,
  ruido: RUIDO,
  ...(R8 ? { rodada8: r8.stats } : {}),
}
const escreverJsonl = (arq, linhas) => { const fd = fs.openSync(path.join(SAIDA, arq), 'w'); for (const l of linhas) fs.writeSync(fd, JSON.stringify(l) + '\n'); fs.closeSync(fd) }
escreverJsonl('produtos.jsonl', produtos)
escreverJsonl('novos.jsonl', novos)
escreverJsonl('buscas.jsonl', buscas)
fs.writeFileSync(path.join(SAIDA, 'taxonomia.json'), JSON.stringify(taxonomia, null, 2))
fs.writeFileSync(path.join(SAIDA, 'splits.json'), JSON.stringify(splits))
fs.writeFileSync(path.join(SAIDA, 'estatisticas.json'), JSON.stringify(estat, null, 2))
if (R8) {
  // arvore_legada.json é o que o agente lê (sem gabarito); arvore_gabarito.json só o avaliador lê
  fs.writeFileSync(path.join(SAIDA, 'arvore_legada.json'), JSON.stringify(r8.arvoreLegada))
  fs.writeFileSync(path.join(SAIDA, 'arvore_gabarito.json'), JSON.stringify(r8.arvoreGabarito))
  fs.writeFileSync(path.join(SAIDA, 'arvore_splits.json'), JSON.stringify(r8.arvoreSplit))
  escreverJsonl('fora_treino.jsonl', r8.foraTreino)
}
const csvEsc = s => '"' + String(s ?? '').replace(/"/g, '""') + '"'
const amostra = pickN(produtos, 300)
const linhasCsv = ['id;titulo;categoria_vendedor;marca_informada;atributos_vendedor;subcategoria_correta;marca_correta;atributos_corretos;dificuldade;ruidos']
for (const p of amostra) linhasCsv.push([p.id, p.titulo, p.categoria_vendedor, p.marca_informada, JSON.stringify(p.atributos), p.gabarito.caminho, p.gabarito.marca, JSON.stringify(p.gabarito.atributos), p.gabarito.dificuldade, p.gabarito.ruidos.join(',')].map(csvEsc).join(';'))
fs.writeFileSync(path.join(SAIDA, 'amostra.csv'), '﻿' + linhasCsv.join('\n'))
console.log(JSON.stringify(estat, null, 2))
