// Rodada 8 do benchmark (opção --rodada8 do gerar-catalogo-v2.mjs).
// Tudo aqui só roda com a opção ligada; sem ela, o gerador não chama nada deste arquivo e a saída continua idêntica byte a byte.
// Os ruídos novos usam um gerador de números próprio (rnd8), separado do da base, para não mexer na sequência da base.
//
//   registrarR8        antes de montar as folhas: subcategorias novas, atributos novos, modelos novos, vizinhança (confunde_com)
//   ajustarFichasR8    depois de montar as folhas: tira voltagem (e outros) da ficha das não elétricas
//   posProcessarR8     depois dos produtos e dos itens novos: ruídos F2, árvore legada, ambiguidade, itens novos extras
//   buscasR8           depois das buscas normais: F2, sem resposta, com modelo

export const GRUPOS_FORA = {
  treino: ['drones', 'impressoras_3d', 'oculos_vr', 'purificadores_ar'],
  teste: ['paineis_solares', 'patinetes_eletricos', 'fechaduras_digitais', 'cameras_seguranca'],
}

export const NAO_ELETRICAS = ['churrasqueiras_carvao', 'fogoes_lenha', 'panelas_pressao', 'escovas_dentes_manuais', 'ferramentas_manuais_avulsas', 'bicicletas']
export const ELETRICAS_VIZINHAS = ['churrasqueiras_eletricas', 'fogoes', 'panelas_eletricas', 'escovas_dentes_eletricas', 'furadeiras', 'bicicletas_eletricas']

// pares de subcategorias com duas casas legítimas. A principal é a da função do produto (ver README).
export const SOBREPOSICOES = [
  { a: 'lavadoras_alta_pressao', b: 'lavadoras_auto', dica_a: 'para limpeza pesada, calçadas e carro', dica_b: 'para lavar carro e moto, também limpa quintal' },
  { a: 'aspiradores', b: 'aspiradores_auto', dica_a: 'portátil, serve para casa e para o carro', dica_b: 'portátil para o carro, serve também em casa' },
  { a: 'coolers', b: 'caixas_termicas_cozinha', dica_a: 'para camping e também para marmita e cozinha', dica_b: 'para marmita e cozinha, serve também no camping' },
]
export const REGRA_PRINCIPAL = 'A principal é a subcategoria da função declarada do produto: o que ele foi feito para fazer. Lavadora de alta pressão: Ferramentas (limpeza pesada) quando o uso geral é calçada, quintal e paredes; Automotivo quando o anúncio é para lavar veículos. Aspirador portátil: Eletroportáteis quando é de tomada e de uso doméstico; Automotivo quando é de 12 V para o veículo. Caixa térmica: Camping quando é cooler de lazer; Utilidades quando é para alimentos e marmita. No gabarito, o produto mantém a subcategoria da sua função em `subcategoria` e a outra entra em `subcategorias_aceitas`.'

function mulberry32(a) { return function () { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296 } }

const semAcento = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '')
const cap = s => s ? s[0].toUpperCase() + s.slice(1) : s

// ---------------------------------------------------------------------------
export function registrarR8({ DEPARTAMENTOS, ATRIBUTOS, MARCAS, MODELOS_CEL, MODELOS_IMPRESSORA }) {
  Object.assign(ATRIBUTOS, {
    tamanho_grelha_cm: { nome: 'Tamanho da grelha', en: 'Grill size', valores: ['40', '50', '60', '70'], unidade: ' cm', aliases: ['Grelha', 'Tamanho da grelha', 'Medida da grelha'], titulo: true },
    com_tampa: { nome: 'Com tampa', en: 'With lid', valores: ['sim', 'não'], aliases: ['Com tampa', 'Tampa', 'Acompanha tampa'], titulo: true },
    com_forno: { nome: 'Com forno', en: 'With oven', valores: ['sim', 'não'], aliases: ['Com forno', 'Forno', 'Possui forno'], titulo: true },
    tipo_cerdas: { nome: 'Cerdas', en: 'Bristles', valores: ['macia', 'média', 'dura'], aliases: ['Cerdas', 'Tipo de cerdas', 'Dureza'], titulo: true, prefixoTitulo: 'Cerdas ' },
    tipo_ferramenta_manual: { nome: 'Tipo de ferramenta', en: 'Tool type', valores: ['martelo', 'serrote', 'chave de grifo', 'espátula', 'formão', 'talhadeira', 'pé de cabra'], aliases: ['Tipo', 'Ferramenta', 'Modelo'], titulo: true },
    autonomia_km: { nome: 'Autonomia', en: 'Range', valores: ['25', '40', '60'], unidade: ' km', aliases: ['Autonomia', 'Alcance', 'Autonomia (km)'], titulo: true },
  })
  Object.assign(MARCAS, {
    churrasco: ['Tramontina', 'Mor', 'Brasa Forte', 'Weber', 'Grill Master', 'Arke', 'Jackwal', 'Bromélia'],
    fogao_lenha: ['Itumbiara', 'Lenha Forte', 'Rústico Casa', 'Tradição', 'Mor', 'Venâncio', 'Fogão do Sítio'],
    panela_pressao: ['Tramontina', 'Panex', 'Rochedo', 'Clock', 'Eirilar', 'Brinox', 'Nigro'],
    escova_dente: ['Oral-B', 'Colgate', 'Philips', 'Sensodyne', 'Curaprox', 'Oclean', 'Xiaomi', 'Condor', 'Bitufo'],
    ferr_manual: ['Tramontina', 'Stanley', 'Vonder', 'Gedore', 'Belzer', 'Irwin', 'Bremen', 'Lufkin'],
    ebike: ['Caloi', 'Sense', 'Oggi', 'Blitz', 'Xiaomi', 'Himo', 'Dafra', 'Voltz'],
    lavadora_auto: ['Karcher', 'Bosch', 'Wap', 'Lavor', 'Black+Decker', 'Intech Machine', 'Tramontina'],
    termica: ['Coleman', 'Soprano', 'Mor', 'Invicta', 'Termolar', 'Tramontina', 'Stanley'],
  })
  for (const m of ['Galaxy S23']) if (!MODELOS_CEL.includes(m)) MODELOS_CEL.push(m)
  for (const m of ['HP 664']) if (!MODELOS_IMPRESSORA.includes(m)) MODELOS_IMPRESSORA.push(m)

  const f = (id, nome, nome_en, termos, attrs, preco, marcas, o = {}) => ({ id, nome, nome_en, termos, attrs, preco, marcas, tipo: o.tipo || 'produto', de: o.de || [], conf: o.conf || [], peso: o.peso ?? 1, grande: !!o.grande })
  const novas = {
    fogoes_fornos: [f('fogoes_lenha', 'Fogões a lenha', 'Wood stoves', ['fogão a lenha', 'fogão rústico a lenha', 'fogão de lenha'], 'numero_bocas=2|3|4 material=ferro_fundido|aço_inox|esmaltado com_forno cor=preto|inox|vermelho', [900, 4800], 'fogao_lenha', { grande: true, conf: ['fogoes'] })],
    utilidades_cozinha: [
      f('panelas_pressao', 'Panelas de pressão', 'Pressure cookers', ['panela de pressão', 'panela de pressão polida', 'panela de pressão de alumínio'], 'capacidade_l=3|4.5|6|7|10 material=alumínio|inox cor=prata|vermelho|preto', [60, 420], 'panela_pressao', { conf: ['panelas_eletricas', 'panelas'] }),
      f('caixas_termicas_cozinha', 'Caixas térmicas para alimentos', 'Food cool boxes', ['caixa térmica para marmita', 'bolsa térmica para alimentos', 'marmita térmica'], 'capacidade_l:3-30 cor=azul|vermelho|preto|cinza', [25, 300], 'termica', { conf: ['coolers'] }),
    ],
    camping: [f('churrasqueiras_carvao', 'Churrasqueiras a carvão', 'Charcoal grills', ['churrasqueira a carvão', 'churrasqueira portátil a carvão', 'churrasqueira de bafo'], 'tamanho_grelha_cm com_tampa material=aço|ferro_fundido|alumínio|inox cor=preto|vermelho|inox', [90, 1800], 'churrasco', { grande: true, conf: ['churrasqueiras_eletricas'] })],
    aparelhos_beleza: [
      f('escovas_dentes_manuais', 'Escovas de dente manuais', 'Manual toothbrushes', ['escova de dente', 'escova dental manual', 'escova de dentes macia'], 'tipo_cerdas cor=azul|verde|rosa|branco|preto', [4, 45], 'escova_dente', { conf: ['escovas_dentes_eletricas'] }),
      f('escovas_dentes_eletricas', 'Escovas de dente elétricas', 'Electric toothbrushes', ['escova de dente elétrica', 'escova dental elétrica', 'escova sônica'], 'tipo_cerdas voltagem=Bivolt|127V|220V cor=branco|preto|azul|rosa', [80, 700], 'escova_dente', { conf: ['escovas_dentes_manuais'] }),
    ],
    ferramentas_manuais: [f('ferramentas_manuais_avulsas', 'Ferramentas manuais avulsas', 'Single hand tools', ['martelo', 'serrote', 'chave de grifo', 'espátula de pedreiro', 'formão'], 'tipo_ferramenta_manual material=aço|aço_cromo_vanádio|madeira', [12, 160], 'ferr_manual', { conf: ['furadeiras', 'parafusadeiras', 'alicates', 'jogos_ferramentas'] })],
    ciclismo: [f('bicicletas_eletricas', 'Bicicletas elétricas', 'Electric bikes', ['bicicleta elétrica', 'bike elétrica', 'e-bike'], 'aro_bike potencia_w:250-750:50 voltagem=Bivolt|127V|220V bateria_v=36|48 autonomia_km cor=preto|branco|azul|vermelho|cinza', [3500, 12000], 'ebike', { grande: true, conf: ['bicicletas'] })],
    acessorios_auto: [f('lavadoras_auto', 'Lavadoras para veículos', 'Vehicle washers', ['lavadora para carro', 'lavadora de alta pressão automotiva', 'lava jato para carro'], 'pressao_psi voltagem=12V|127V|220V cor=amarelo|preto|azul', [250, 1800], 'lavadora_auto', { conf: ['lavadoras_alta_pressao'] })],
  }
  const vizinhas = {
    churrasqueiras_eletricas: 'churrasqueiras_carvao', fogoes: 'fogoes_lenha', panelas_eletricas: 'panelas_pressao', panelas: 'panelas_pressao',
    furadeiras: 'ferramentas_manuais_avulsas', parafusadeiras: 'ferramentas_manuais_avulsas', alicates: 'ferramentas_manuais_avulsas', jogos_ferramentas: 'ferramentas_manuais_avulsas',
    bicicletas: 'bicicletas_eletricas', lavadoras_alta_pressao: 'lavadoras_auto', coolers: 'caixas_termicas_cozinha', aspiradores_auto: null,
  }
  const porId = {}
  for (const dep of DEPARTAMENTOS) for (const cat of dep.categorias) { for (const fo of cat.folhas) porId[fo.id] = fo; if (novas[cat.id]) { cat.folhas.push(...novas[cat.id]); for (const fo of novas[cat.id]) porId[fo.id] = fo } }
  for (const [antiga, nova] of Object.entries(vizinhas)) if (nova && porId[antiga] && !porId[antiga].conf.includes(nova)) porId[antiga].conf.push(nova)
  porId.panelas.termos = porId.panelas.termos.filter(t => t !== 'panela de pressão')
  porId.escovas_dentes_eletricas.conf = ['escovas_dentes_manuais']
}

// tira da ficha o que não existe para a subcategoria (voltagem nas não elétricas, consumo e painel no fogão a lenha)
export function ajustarFichasR8(POR_ID) {
  const tirar = { fogoes_lenha: ['voltagem', 'consumo_kwh', 'tipo_controle', 'certificacao_inmetro'] }
  for (const id of NAO_ELETRICAS) {
    const fo = POR_ID[id]; if (!fo) continue
    const sai = new Set(['voltagem', ...(tirar[id] || [])])
    fo.esquema = fo.esquema.filter(a => !sai.has(a.chave))
  }
}

// ---------------------------------------------------------------------------
const ELETRICAS_NOMES_ARVORE_LIXO = ['Ofertas', 'Black Friday 2023', 'Black Friday 2022', 'Outros', 'Promoções', 'Natal 2022', 'Liquidação', 'Teste', 'NÃO USAR', 'Novidades', 'Diversos', 'Dia das Mães 2021', 'Mais vendidos', 'Queima de estoque', 'Cyber Monday', 'Lançamentos 2020', 'Sem categoria', 'Importados', 'Marcas parceiras', 'Frete grátis', 'Kits e combos', 'Oferta relâmpago', 'Volta às aulas 2022', 'Verão 2023', 'Presentes', 'Em breve', 'Descontinuados', 'Aguardando cadastro']

export function posProcessarR8(c) {
  const { produtos, novos, FOLHAS, POR_ID, ATRIBUTOS, SINONIMOS_VALOR, DEPARTAMENTOS, SEMENTE, N_NOVOS, registro, criarVerdade, verdades, VENDEDORES, VENDEDORES_NOVOS, novoIdN, criarFora, eanValido, FORA_TAXONOMIA } = c
  const rnd8 = mulberry32((SEMENTE ^ 0x5A17C0DE) >>> 0)
  const chance = p => rnd8() < p
  const int = (a, b) => a + Math.floor(rnd8() * (b - a + 1))
  const pick = arr => arr[Math.floor(rnd8() * arr.length)]
  const shuffle = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd8() * (i + 1));[a[i], a[j]] = [a[j], a[i]] } return a }
  const stats = {}

  // texto de um valor de atributo como o anúncio escreveria (sem usar o gerador principal)
  const txt = (chave, v) => {
    const def = ATRIBUTOS[chave]
    if (chave === 'voltagem' || chave === 'cor') return chave === 'cor' ? cap((SINONIMOS_VALOR.cor?.[v] || [v])[0]) : (SINONIMOS_VALOR.voltagem?.[v] || [v])[0]
    const num = String(v).replace('.', ',')
    if (def.prefixoTitulo !== undefined) return def.prefixoTitulo + num + (def.unidade && !/^ /.test(def.unidade) ? def.unidade : '')
    if (def.unidade) return num + def.unidade.trim()
    return cap(String(v))
  }
  const chaveVendedor = (p, chave) => { for (const al of ATRIBUTOS[chave].aliases) if (al in p.atributos) return al; return null }

  // ---------- F2: erros de atributo ----------
  const contFase = { nao_se_aplica: 0, contradiz_anuncio: 0, fora_do_normal: 0, sem_pista: 0, controles_voltagem: 0, nao_eletricos: 0 }
  const naoEl = new Set(NAO_ELETRICAS), eletricas = new Set(ELETRICAS_VIZINHAS)
  for (const p of produtos) {
    const g = p.gabarito, fo = POR_ID[g.subcategoria]
    const erros = []
    // sem_pista: os que o gerador original já injetou (o vendedor errou e nada no anúncio contradiz)
    for (const chave of g.atributos_errados_pelo_vendedor || []) {
      const k = chaveVendedor(p, chave)
      erros.push({ chave, tipo: 'sem_pista', valor_informado: k ? p.atributos[k] : null, valor_certo: g.atributos[chave] })
      contFase.sem_pista++
    }
    // nao_se_aplica: voltagem em subcategoria que não tem
    if (naoEl.has(g.subcategoria)) {
      contFase.nao_eletricos++
      if (chance(0.40)) {
        const v = pick(['bivolt', '220V', '110/220', 'Bivolt', '127V', '110 volts'])
        p.atributos[pick(ATRIBUTOS.voltagem.aliases)] = v
        if (chance(0.5)) p.titulo = `${p.titulo} ${pick(['bivolt', '220V', '110/220V'])}`
        erros.push({ chave: 'voltagem', tipo: 'nao_se_aplica', valor_informado: v, valor_certo: null })
        g.ruidos.push('voltagem_nao_se_aplica'); contFase.nao_se_aplica++
      }
    }
    // controle: elétricos vizinhos com voltagem informada e certa
    if (eletricas.has(g.subcategoria) && g.atributos.voltagem && chaveVendedor(p, 'voltagem') && !erros.some(e => e.chave === 'voltagem')) { g.controle_voltagem = true; contFase.controles_voltagem++ }
    // contradiz_anuncio: o título diz o valor certo e o campo do vendedor diz outro
    if (fo && chance(0.05)) {
      const cand = fo.esquema.filter(a => a.tipo === 'enum' && ATRIBUTOS[a.chave].titulo && a.valores.length > 1 && g.atributos[a.chave] != null && !['sim', 'não'].includes(g.atributos[a.chave]) && !erros.some(e => e.chave === a.chave) && !(g.atributos_errados_pelo_vendedor || []).includes(a.chave))
      if (cand.length) {
        const a = pick(cand), certo = g.atributos[a.chave], outro = pick(a.valores.filter(x => x !== certo))
        for (const al of ATRIBUTOS[a.chave].aliases) delete p.atributos[al]
        p.atributos[ATRIBUTOS[a.chave].aliases[0]] = txt(a.chave, outro)
        if (!semAcento(p.titulo.toLowerCase()).includes(semAcento(txt(a.chave, certo).toLowerCase()))) p.titulo = `${p.titulo} ${txt(a.chave, certo)}`
        if (!g.atributos_inferiveis.includes(a.chave)) g.atributos_inferiveis.push(a.chave)
        erros.push({ chave: a.chave, tipo: 'contradiz_anuncio', valor_informado: outro, valor_certo: certo })
        g.ruidos.push('atributo_contradiz_anuncio'); contFase.contradiz_anuncio++
      }
    }
    // fora_do_normal: valor numérico impossível para a subcategoria (fora da faixa ou unidade trocada)
    if (fo && chance(0.01)) {
      const cand = fo.esquema.filter(a => (a.tipo === 'num' && g.atributos[a.chave] != null) || a.chave === 'numero_bocas').filter(a => !erros.some(e => e.chave === a.chave))
      if (cand.length) {
        const a = pick(cand); let valor
        if (a.chave === 'numero_bocas') valor = '12'
        else if (a.chave.endsWith('_kg')) valor = a.max * 1000
        else valor = Math.round(a.max * pick([4, 6, 10]) * 10) / 10
        for (const al of ATRIBUTOS[a.chave].aliases) delete p.atributos[al]
        p.atributos[ATRIBUTOS[a.chave].aliases[0]] = a.chave === 'numero_bocas' ? '12 bocas' : `${String(valor).replace('.', ',')}${(ATRIBUTOS[a.chave].unidade || '').trim()}`
        erros.push({ chave: a.chave, tipo: 'fora_do_normal', valor_informado: valor, valor_certo: g.atributos[a.chave] })
        g.ruidos.push('valor_fora_do_normal'); contFase.fora_do_normal++
      }
    }
    g.erros_de_atributo = erros
  }
  stats.erros_de_atributo = contFase

  // ---------- ambiguidade ----------
  let nAmb = 0
  for (const s of SOBREPOSICOES) for (const [propria, outra, dica] of [[s.a, s.b, s.dica_a], [s.b, s.a, s.dica_b]]) {
    for (const p of produtos) {
      if (p.gabarito.subcategoria !== propria || !chance(0.3)) continue
      p.titulo = `${p.titulo} ${dica}`
      p.gabarito.subcategorias_aceitas = [propria, outra]; p.gabarito.ruidos.push('ambiguidade_real'); nAmb++
    }
  }
  stats.ambiguos = nAmb

  // ---------- árvore legada ----------
  const nos = []
  const porCaminho = new Map()
  const novoNo = (nome, caminhoPai, paiId, o = {}) => {
    const caminho = caminhoPai ? `${caminhoPai} > ${nome}` : nome
    const id = 'L' + String(nos.length + 1).padStart(4, '0')
    const no = { id, nome, caminho, pai: paiId, tipo: o.tipo || 'categoria', destino: o.destino ?? null, atributo: o.atributo || null, ...(o.estrutural ? { estrutural: true } : {}), ...(o.vazio ? { vazio: true } : {}), ...(o.grupo ? { grupo: o.grupo } : {}) }
    nos.push(no); porCaminho.set(caminho, no); return no
  }
  const estrutural = partes => { // garante os nós de caminho (departamento, categoria...) e devolve o último
    let pai = null, caminho = ''
    for (const nome of partes) {
      const cam = caminho ? `${caminho} > ${nome}` : nome
      pai = porCaminho.get(cam) || novoNo(nome, caminho, pai?.id ?? null, { estrutural: true })
      caminho = cam
    }
    return pai
  }
  const nomesVar = fo => [fo.nome, semAcento(fo.nome), cap(fo.termos[0]), `${fo.cat.nome} - ${fo.nome}`, fo.nome.toLowerCase(), cap(fo.termos[Math.min(1, fo.termos.length - 1)])]
  const principal = {}   // folha -> nó de categoria
  const dups = {}        // folha -> [nós duplicados]
  const outrosDeps = DEPARTAMENTOS.map(d => d.nome)
  for (const fo of shuffle([...FOLHAS])) {
    const profundo = chance(0.12)
    const base = [fo.dep.nome, fo.cat.nome]
    if (profundo) base.push(pick(['Seção principal', 'Linha completa', 'Coleção antiga']), ...(chance(0.5) ? [pick(['Coleção 2021', 'Marcas selecionadas', 'Catálogo 2.0'])] : []))
    const pai = estrutural(base)
    const nomeCat = chance(0.7) ? fo.nome : pick(nomesVar(fo))
    principal[fo.id] = novoNo(nomeCat, pai.caminho, pai.id, { tipo: 'categoria', destino: fo.id, grupo: fo.id })
    dups[fo.id] = []
    if (chance(0.55)) {
      const n = int(1, 3)
      const nomes = shuffle(nomesVar(fo).filter(x => x !== nomeCat))
      for (let i = 0; i < n && i < nomes.length; i++) {
        const ramo = pick([[fo.dep.nome, fo.cat.nome], [pick(outrosDeps), 'Antigos'], ['Outros', fo.cat.nome], ['Promoções', fo.dep.nome], [fo.dep.nome, 'Sortimento antigo', fo.cat.nome]])
        const p2 = estrutural(ramo)
        dups[fo.id].push(novoNo(nomes[i], p2.caminho, p2.id, { tipo: 'duplicado', destino: fo.id, grupo: fo.id }))
      }
    }
  }
  // lixo
  const nLixo = 130
  for (let i = 0; i < nLixo; i++) {
    const nome = i < ELETRICAS_NOMES_ARVORE_LIXO.length ? ELETRICAS_NOMES_ARVORE_LIXO[i] : `${pick(ELETRICAS_NOMES_ARVORE_LIXO)} ${pick(['2019', '2020', '2021', '2022', '2023', 'v2', 'antigo', 'II'])}`
    const pai = chance(0.6) ? estrutural([pick(['Promoções', 'Outros', 'Campanhas'])]) : estrutural([pick(DEPARTAMENTOS).nome])
    novoNo(nome, pai.caminho, pai.id, { tipo: 'lixo', destino: null, vazio: i % 5 === 0 })
  }
  // atributos como nó (até chegar perto de 1.700 nós)
  const ALVO = 1700
  const candidatos = []
  for (const fo of FOLHAS) for (const a of fo.esquema) {
    if (a.tipo !== 'enum' || !ATRIBUTOS[a.chave].titulo || a.chave === 'compatibilidade') continue
    for (const v of a.valores) if (!['sim', 'não'].includes(v)) candidatos.push({ fo, chave: a.chave, valor: v })
  }
  shuffle(candidatos)
  const nosAttr = {}
  for (const cd of candidatos) {
    if (nos.length >= ALVO) break
    const paiNo = dups[cd.fo.id].length && chance(0.25) ? pick(dups[cd.fo.id]) : principal[cd.fo.id]
    const nome = `${paiNo.nome} ${txt(cd.chave, cd.valor).toLowerCase()}`
    const no = novoNo(nome, paiNo.caminho, paiNo.id, { tipo: 'atributo', destino: cd.fo.id, atributo: { chave: cd.chave, valor: cd.valor } })
    ;(nosAttr[cd.fo.id] ||= []).push(no)
  }

  // produtos penduram num nó (com ruído: parte em nó errado, parte em lixo)
  const lixos = nos.filter(n => n.tipo === 'lixo' && !n.vazio)
  const cont = { certo: 0, atributo: 0, errado: 0, lixo: 0 }
  for (const p of produtos) {
    const g = p.gabarito, fo = POR_ID[g.subcategoria]
    let no = null, status, atr = null
    const r = rnd8()
    if (r < 0.05) { no = pick(lixos); status = 'lixo' }
    else if (r < 0.12) { const irmas = fo.cat.folhas.filter(x => x.id !== fo.id && principal[x.id]); no = irmas.length ? principal[pick(irmas).id] : pick(lixos); status = irmas.length ? 'errado' : 'lixo' }
    else {
      const ofertas = (nosAttr[fo.id] || []).filter(n => String(g.atributos[n.atributo.chave]) === String(n.atributo.valor))
      if (ofertas.length && chance(0.4)) { no = pick(ofertas); status = 'atributo'; atr = { ...no.atributo } }
      else { no = chance(0.3) && dups[fo.id].length ? pick(dups[fo.id]) : principal[fo.id]; status = 'certo' }
    }
    cont[status]++
    p.categoria_legada = no.id
    g.categoria_legada_status = status
    g.atributo_do_no_legado = atr
  }
  stats.arvore = { nos: nos.length, por_tipo: nos.reduce((a, n) => (a[n.estrutural ? 'estrutural' : n.tipo] = (a[n.estrutural ? 'estrutural' : n.tipo] || 0) + 1, a), {}), profundidade_max: Math.max(...nos.map(n => n.caminho.split(' > ').length)), produtos_por_status: cont }

  // split 70/30 dos nós: categoria + duplicados de uma subcategoria ficam inteiros do mesmo lado
  const gruposFolha = shuffle(FOLHAS.map(f => f.id)), ladoFolha = {}
  gruposFolha.forEach((id, i) => { ladoFolha[id] = i < Math.round(gruposFolha.length * 0.7) ? 'treino' : 'teste' })
  const arvoreSplit = { treino: [], teste: [], observacao: 'Nós estruturais (departamentos e categorias pais) ficam fora. Categoria e duplicados da mesma subcategoria ficam do mesmo lado; nós de atributo e de lixo são sorteados 70/30.' }
  for (const n of nos) {
    if (n.estrutural) continue
    const lado = (n.tipo === 'categoria' || n.tipo === 'duplicado') ? ladoFolha[n.destino] : (chance(0.7) ? 'treino' : 'teste')
    arvoreSplit[lado].push(n.id)
  }

  // ---------- itens novos extras ----------
  const raizDe = p => p.gabarito.duplicado_de || p.id
  const porRaiz = {}
  produtos.forEach((p, i) => (porRaiz[raizDe(p)] ||= []).push(i))
  const gruposComCopias = Object.values(porRaiz).filter(a => a.length >= 2)
  const indPorId = new Map(produtos.map((p, i) => [p.id, i]))
  const vendNovo = () => pick([...VENDEDORES, ...VENDEDORES_NOVOS])
  const dataNovos = () => `2026-10-${String(int(1, 31)).padStart(2, '0')}`
  const extras = { duplicados: 0, variacoes: 0, limitrofes: 0, por_pista: {} }
  const nDup = Math.round(N_NOVOS * 0.06)
  for (let i = 0; i < nDup && gruposComCopias.length; i++) {
    const grupo = pick(gruposComCopias), k = pick(grupo)
    const orig = produtos[k], v = verdades[k], pista = pick(['ean_igual', 'sem_ean', 'titulo_reescrito', 'marca_diferente'])
    const reg = registro(novoIdN(), v, { vendedor: vendNovo(), ingles: false, preco: Math.round(v.preco * (0.85 + rnd8() * 0.3) * 100) / 100, manterEan: true, data: dataNovos(), descricaoDeOutro: null }, { duplicado_de: raizDe(orig), tipo_novo: 'duplicado_do_catalogo', pista_duplicado: pista, grupo_de_copias: grupo.length })
    if (pista === 'ean_igual') reg.ean = v.ean
    else if (pista === 'sem_ean') reg.ean = null
    else if (pista === 'titulo_reescrito') reg.titulo = shuffle(reg.titulo.split(' ')).join(' ')
    else reg.marca_informada = pick([reg.marca_informada.toUpperCase(), reg.marca_informada.toLowerCase(), 'Genérico', ''])
    novos.push(reg); extras.duplicados++; extras.por_pista[pista] = (extras.por_pista[pista] || 0) + 1
  }
  const EIXOS = ['cor', 'voltagem', 'tamanho_roupa', 'tamanho_calcado', 'tamanho_numerico', 'armazenamento_gb', 'tamanho_cama', 'tamanho_fralda', 'tamanho_pet']
  const nVar = Math.round(N_NOVOS * 0.05)
  for (let tent = 0, feitos = 0; feitos < nVar && tent < nVar * 20; tent++) {
    const k = int(0, produtos.length - 1), orig = produtos[k], v = verdades[k], fo = v.folha
    const eixos = fo.esquema.filter(a => EIXOS.includes(a.chave) && a.tipo === 'enum' && a.valores.length > 1)
    if (!eixos.length || orig.gabarito.duplicado_de) continue
    const eixo = pick(eixos), outro = pick(eixo.valores.filter(x => x !== v.atributos[eixo.chave]))
    const v2 = { ...v, atributos: { ...v.atributos, [eixo.chave]: outro }, ean: eanValido() }
    const reg = registro(novoIdN(), v2, { vendedor: vendNovo(), ingles: false, data: dataNovos(), descricaoDeOutro: null }, { variacao_de: orig.gabarito.variacao_de || orig.id, eixo_variacao: eixo.chave, tipo_novo: 'variacao_do_catalogo' })
    if (!semAcento(reg.titulo.toLowerCase()).includes(semAcento(txt(eixo.chave, outro).toLowerCase()))) reg.titulo = `${reg.titulo} ${txt(eixo.chave, outro)}`
    novos.push(reg); feitos++; extras.variacoes++
  }
  // inválidos limítrofes: cadastros curtos ou estranhos, mas legítimos
  const LIMITROFES = [
    ['Kit 3 panos', ['pano', 'limpeza'], 12], ['Adesivo R$ 1,00', ['adesivo', 'papelaria'], 1], ['Pilha AA', ['pilha', 'bateria'], 6], ['iPhone 13 128GB', ['smartphone'], 3200],
    ['Pano de prato', ['pano', 'cozinha'], 9], ['Cabo USB-C 1m', ['cabo'], 18], ['Caneta azul', ['caneta'], 2], ['Fita isolante', ['fita'], 5], ['Lâmpada LED 9W', ['lâmpada'], 8],
    ['Tomada 10A', ['tomada'], 7], ['Mouse sem fio', ['mouse'], 29], ['Case iPhone 15', ['capinha', 'capa'], 15], ['Garrafa 500ml', ['garrafa'], 14],
  ]
  const acha = palavras => FOLHAS.find(fo => palavras.some(w => fo.termos.some(t => semAcento(t.toLowerCase()).includes(semAcento(w.toLowerCase()))) || semAcento(fo.nome.toLowerCase()).includes(semAcento(w.toLowerCase()))))
  const nLim = Math.round(N_NOVOS * 0.01)
  for (let i = 0; i < nLim; i++) {
    const [titulo, palavras, preco] = LIMITROFES[i % LIMITROFES.length]
    const fo = acha(palavras), vend = pick([...VENDEDORES, ...VENDEDORES_NOVOS])
    const marca = chance(0.5) ? pick(['Nexa', 'Kivo', 'Zuni', 'Aurel', 'Vexo']) : ''
    novos.push({ id: novoIdN(), sku: `SKU${int(1000000, 9999999)}`, titulo: i < LIMITROFES.length ? titulo : `${titulo} ${marca}`.trim(), bullets: [], descricao: chance(0.5) ? '' : titulo, marca_informada: marca, modelo: '', ean: chance(0.5) ? eanValido() : null, categoria_vendedor: '', tags: '', atributos: {}, embalagem: {}, preco: String(Math.max(1, Math.round(preco * (0.8 + rnd8() * 0.5)))), condicao: 'novo', vendedor: { id: vend.id, nome: vend.nome }, data_cadastro: dataNovos(), gabarito: { invalido: false, departamento: fo?.dep.id ?? null, categoria: fo?.cat.id ?? null, subcategoria: fo?.id ?? null, tipo_novo: 'invalido_limitrofe', ruidos: ['cadastro_curto_legitimo'], dificuldade: 'dificil' } })
    extras.limitrofes++
  }
  stats.novos_extras = extras

  // fora da taxonomia do grupo de treino (para ensinar a opção "nenhuma destas"); nunca entra nos itens novos do teste
  const foraTreino = []
  const tiposTreino = FORA_TAXONOMIA.filter(x => GRUPOS_FORA.treino.includes(x.id))
  const nForaTreino = Math.round(N_NOVOS * 0.08)
  for (let i = 0; i < nForaTreino; i++) {
    const it = criarFora(tiposTreino[i % tiposTreino.length], dataNovos())
    it.id = 'F' + String(i + 1).padStart(5, '0')
    it.gabarito.grupo_fora = 'treino'
    foraTreino.push({ ...it, split: i % 2 === 0 ? 'treino' : 'validacao' })
  }
  stats.fora_da_taxonomia = { tipos_treino: GRUPOS_FORA.treino, tipos_teste: GRUPOS_FORA.teste, itens_treino_e_validacao: foraTreino.length }

  const arvoreLegada = nos.map(n => ({ id: n.id, nome: n.nome, caminho: n.caminho, pai: n.pai }))
  const arvoreGabarito = nos.map(n => ({ id: n.id, nome: n.nome, caminho: n.caminho, pai: n.pai, tipo: n.tipo, destino: n.destino, atributo: n.atributo, estrutural: !!n.estrutural, vazio: !!n.vazio }))
  return { arvoreLegada, arvoreGabarito, arvoreSplit, foraTreino, stats }
}

// ---------------------------------------------------------------------------
export function buscasR8({ addBusca, buscas, idxFolha, ATRIBUTOS }) {
  const contar = () => buscas.length
  const antes = contar()
  // F2: dado errado do vendedor muda a busca
  addBusca({ q: 'fogão bivolt', folhas: ['fogoes'], filtros: { voltagem: 'Bivolt' }, tipo: 'f2', armadilhas: ['fogoes_lenha'] })
  addBusca({ q: 'fogão 220v', folhas: ['fogoes'], filtros: { voltagem: '220V' }, tipo: 'f2', armadilhas: ['fogoes_lenha'] })
  addBusca({ q: 'churrasqueira bivolt', folhas: ['churrasqueiras_eletricas'], filtros: { voltagem: 'Bivolt' }, tipo: 'f2', armadilhas: ['churrasqueiras_carvao'] })
  addBusca({ q: 'churrasqueira a carvão', folhas: ['churrasqueiras_carvao'], tipo: 'f2', armadilhas: ['churrasqueiras_eletricas'] })
  addBusca({ q: 'fogão a lenha', folhas: ['fogoes_lenha'], tipo: 'f2', armadilhas: ['fogoes'] })
  addBusca({ q: 'furadeira 220v', folhas: ['furadeiras'], filtros: { voltagem: '220V' }, tipo: 'f2', armadilhas: ['parafusadeiras', 'ferramentas_manuais_avulsas'] })
  // com modelo: relevante só o que bate o modelo
  const MODELOS = [
    ['capinha iphone 15', 'capas_celular', 'iPhone 15'], ['película galaxy s23', 'peliculas', 'Galaxy S23'], ['cartucho hp 664', 'cartuchos', 'HP 664'],
    ['capinha galaxy a55', 'capas_celular', 'Galaxy A55'], ['película iphone 14', 'peliculas', 'iPhone 14'], ['cartucho epson l3250', 'cartuchos', 'Epson L3250'],
  ]
  for (const [q, fo, modelo] of MODELOS) addBusca({ q, folhas: [fo], filtros: { compatibilidade: modelo }, tipo: 'com_modelo', modelo })
  // sem resposta: o catálogo não tem nada; o certo é voltar vazio
  const SEM = ['xptoz', 'trator agrícola', 'geladera de praia nuclear', 'ração para dinossauro', 'submarino de brinquedo tamanho real', 'qwerty asdfgh', 'pneu de avião comercial', 'casa pré-fabricada de 3 quartos', 'smartphone holográfico dobrável', 'unicórnio inflável gigante com motor', 'fertilizante nuclear', 'helicóptero']
  const vistas = new Set(buscas.map(b => b.q.toLowerCase()))
  for (const q of SEM) {
    if (vistas.has(q)) continue
    buscas.push({ id: 'Q' + String(buscas.length + 1).padStart(4, '0'), q, tipo: 'sem_resposta', intencao: { subcategorias: [], filtros: {}, marca: null }, armadilhas: [], ambigua: false, n_relevantes: 0, n_parcialmente_relevantes: 0 })
  }
  return buscas.length - antes
}
