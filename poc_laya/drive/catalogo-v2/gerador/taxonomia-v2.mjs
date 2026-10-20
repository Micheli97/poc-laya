// Taxonomia v2 do catálogo sintético: 3 níveis (departamento > categoria > subcategoria),
// esquema de atributos por subcategoria e as armadilhas entre subcategorias.
//
// Formato do esquema de atributos de cada subcategoria (string separada por espaço):
//   chave                 -> usa o domínio padrão de ATRIBUTOS[chave]
//   chave=a|b|c           -> enumeração (use _ no lugar de espaço dentro de um valor)
//   chave:min-max[:passo] -> número (passo padrão 1)

// ---------------------------------------------------------------------------
// Atributos: nome de exibição, unidade, domínio padrão, chaves que vendedores usam
// ---------------------------------------------------------------------------
export const ATRIBUTOS = {
  voltagem: { nome: 'Voltagem', en: 'Voltage', valores: ['127V', '220V', 'Bivolt'], aliases: ['Voltagem', 'Tensão', 'Volts', 'voltagem', 'Tensão (V)', 'Alimentação'], titulo: true },
  potencia_w: { nome: 'Potência', en: 'Power', unidade: 'W', longo: 'watts', aliases: ['Potência', 'Potencia', 'Potência (W)', 'Watts', 'potencia'], titulo: true },
  capacidade_l: { nome: 'Capacidade', en: 'Capacity', unidade: 'L', longo: 'litros', aliases: ['Capacidade', 'Capacidade (L)', 'Volume', 'Litros', 'capacidade'], titulo: true },
  capacidade_kg: { nome: 'Capacidade de lavagem', en: 'Load capacity', unidade: 'kg', longo: 'quilos', aliases: ['Capacidade', 'Capacidade (kg)', 'Carga', 'Kg de roupa'], titulo: true },
  capacidade_ml: { nome: 'Capacidade', en: 'Capacity', unidade: 'ml', longo: 'mililitros', aliases: ['Capacidade', 'Volume', 'Capacidade (ml)'], titulo: true },
  capacidade_mah: { nome: 'Capacidade da bateria', en: 'Battery capacity', unidade: 'mAh', longo: 'mAh', aliases: ['Capacidade', 'Bateria', 'mAh'], titulo: true },
  capacidade_pessoas: { nome: 'Capacidade de pessoas', en: 'Persons', unidade: ' pessoas', aliases: ['Capacidade', 'Pessoas', 'Lugares'], titulo: true },
  capacidade_garrafas: { nome: 'Capacidade de garrafas', en: 'Bottles', unidade: ' garrafas', aliases: ['Capacidade', 'Garrafas'], titulo: true },
  capacidade_servicos: { nome: 'Capacidade de serviços', en: 'Place settings', valores: ['8', '10', '14'], unidade: ' serviços', aliases: ['Serviços', 'Capacidade', 'Nº de serviços'], titulo: true },
  cor: { nome: 'Cor', en: 'Color', valores: ['preto', 'branco', 'cinza', 'prata', 'azul', 'vermelho', 'rosa', 'verde'], aliases: ['Cor', 'cor', 'Cor principal', 'Color', 'Cores'], titulo: true },
  material: { nome: 'Material', en: 'Material', valores: ['plástico', 'aço inox', 'alumínio', 'vidro', 'madeira', 'metal'], aliases: ['Material', 'material', 'Composição', 'Matéria-prima'], titulo: false },
  numero_bocas: { nome: 'Número de bocas', en: 'Burners', valores: ['4', '5', '6'], unidade: ' bocas', aliases: ['Bocas', 'Nº de bocas', 'Quantidade de bocas', 'Queimadores'], titulo: true },
  numero_portas: { nome: 'Número de portas', en: 'Doors', valores: ['1', '2', '3', '4'], unidade: ' portas', aliases: ['Portas', 'Nº de portas', 'Quantidade de portas'], titulo: true },
  eficiencia_energetica: { nome: 'Eficiência energética', en: 'Energy rating', valores: ['A', 'B', 'C', 'D'], aliases: ['Eficiência energética', 'Selo Procel', 'Classificação energética', 'Procel'], titulo: false },
  frost_free: { nome: 'Frost free', en: 'Frost free', valores: ['sim', 'não'], aliases: ['Frost Free', 'Degelo', 'Tipo de degelo'], titulo: true },
  tipo_acendimento: { nome: 'Acendimento', en: 'Ignition', valores: ['automático', 'manual'], aliases: ['Acendimento', 'Tipo de acendimento'], titulo: false },
  tipo_energia: { nome: 'Tipo de energia', en: 'Energy type', valores: ['gás', 'indução', 'elétrico'], aliases: ['Tipo', 'Energia', 'Tipo de energia', 'Alimentação'], titulo: true },
  largura_cm: { nome: 'Largura', en: 'Width', unidade: 'cm', longo: 'centímetros', aliases: ['Largura', 'Largura (cm)', 'Medida'], titulo: true },
  btus: { nome: 'Capacidade de refrigeração', en: 'BTU', valores: ['9000', '12000', '18000', '24000'], unidade: ' BTUs', aliases: ['BTUs', 'Capacidade', 'Potência de refrigeração', 'BTU/h'], titulo: true },
  ciclo: { nome: 'Ciclo', en: 'Cycle', valores: ['frio', 'quente e frio'], aliases: ['Ciclo', 'Função'], titulo: true },
  inverter: { nome: 'Inverter', en: 'Inverter', valores: ['sim', 'não'], aliases: ['Inverter', 'Tecnologia inverter'], titulo: true },
  diametro_cm: { nome: 'Diâmetro', en: 'Diameter', unidade: 'cm', longo: 'centímetros', aliases: ['Diâmetro', 'Tamanho', 'Diâmetro (cm)'], titulo: true },
  tipo_agua: { nome: 'Tipo de água', en: 'Water type', valores: ['natural', 'gelada', 'natural e gelada'], aliases: ['Água', 'Temperatura da água'], titulo: false },
  velocidades: { nome: 'Velocidades', en: 'Speeds', valores: ['2', '3', '5', '10', '12'], unidade: ' velocidades', aliases: ['Velocidades', 'Nº de velocidades'], titulo: true },
  tipo_cafeteira: { nome: 'Tipo', en: 'Type', valores: ['filtro', 'expresso', 'cápsulas'], aliases: ['Tipo', 'Tipo de cafeteira', 'Sistema'], titulo: true },
  tipo_aspirador: { nome: 'Tipo', en: 'Type', valores: ['vertical', 'portátil', 'robô', 'com saco', 'sem saco'], aliases: ['Tipo', 'Modelo'], titulo: true },
  armazenamento_gb: { nome: 'Armazenamento', en: 'Storage', valores: ['64', '128', '256', '512'], unidade: 'GB', aliases: ['Armazenamento', 'Memória interna', 'Capacidade', 'ROM', 'Armazenamento interno'], titulo: true },
  memoria_ram_gb: { nome: 'Memória RAM', en: 'RAM', valores: ['4', '6', '8', '12', '16'], unidade: 'GB', aliases: ['Memória RAM', 'RAM', 'Memória'], titulo: true, sufixoTitulo: ' RAM' },
  tela_pol: { nome: 'Tamanho da tela', en: 'Screen size', unidade: '"', longo: 'polegadas', aliases: ['Tela', 'Tamanho da tela', 'Polegadas', 'Display'], titulo: true },
  sistema_operacional: { nome: 'Sistema operacional', en: 'OS', valores: ['Android', 'iOS'], aliases: ['Sistema', 'Sistema operacional', 'SO', 'OS'], titulo: false },
  rede: { nome: 'Rede', en: 'Network', valores: ['4G', '5G'], aliases: ['Rede', 'Conectividade', 'Tecnologia'], titulo: true },
  processador: { nome: 'Processador', en: 'CPU', valores: ['Intel Core i3', 'Intel Core i5', 'Intel Core i7', 'AMD Ryzen 5', 'AMD Ryzen 7'], aliases: ['Processador', 'CPU', 'Processor'], titulo: true },
  resolucao: { nome: 'Resolução', en: 'Resolution', valores: ['HD', 'Full HD', '4K'], aliases: ['Resolução', 'Resolucao', 'Definição'], titulo: true },
  taxa_hz: { nome: 'Taxa de atualização', en: 'Refresh rate', valores: ['60', '75', '144', '165', '240'], unidade: 'Hz', aliases: ['Taxa de atualização', 'Frequência', 'Hz'], titulo: true },
  conectividade: { nome: 'Conectividade', en: 'Connectivity', valores: ['USB', 'sem fio', 'bluetooth'], aliases: ['Conectividade', 'Conexão', 'Tipo de conexão', 'Interface'], titulo: true },
  compatibilidade: { nome: 'Compatibilidade', en: 'Compatible with', aliases: ['Compatibilidade', 'Compatível com', 'Modelo compatível', 'Serve em'], titulo: true, prefixoTitulo: 'para ' },
  conector: { nome: 'Conector', en: 'Connector', valores: ['USB-C', 'Lightning', 'Micro USB'], aliases: ['Conector', 'Entrada', 'Tipo de conector', 'Saída'], titulo: true },
  comprimento_m: { nome: 'Comprimento', en: 'Length', unidade: 'm', longo: 'metros', aliases: ['Comprimento', 'Tamanho', 'Metragem'], titulo: true },
  autonomia_h: { nome: 'Autonomia da bateria', en: 'Battery life', unidade: 'h', longo: 'horas', aliases: ['Autonomia', 'Bateria', 'Duração da bateria'], titulo: false },
  tipo_impressora: { nome: 'Tecnologia de impressão', en: 'Print technology', valores: ['jato de tinta', 'laser', 'tanque de tinta'], aliases: ['Tecnologia', 'Tipo de impressão', 'Tipo'], titulo: true },
  layout_teclado: { nome: 'Layout', en: 'Layout', valores: ['ABNT2', 'US'], aliases: ['Layout', 'Padrão', 'Idioma do teclado'], titulo: true },
  interface_ssd: { nome: 'Interface', en: 'Interface', valores: ['SATA', 'NVMe'], aliases: ['Interface', 'Conexão', 'Padrão'], titulo: true },
  padrao_wifi: { nome: 'Padrão Wi-Fi', en: 'Wi-Fi standard', valores: ['Wi-Fi 5', 'Wi-Fi 6', 'Wi-Fi 6E'], aliases: ['Padrão', 'Wi-Fi', 'Tecnologia'], titulo: true },
  tecnologia_tela: { nome: 'Tecnologia da tela', en: 'Panel', valores: ['LED', 'QLED', 'OLED'], aliases: ['Tecnologia', 'Tipo de tela', 'Painel'], titulo: true },
  potencia_rms_w: { nome: 'Potência RMS', en: 'RMS power', unidade: 'W RMS', longo: 'watts RMS', aliases: ['Potência', 'Potência RMS', 'Watts RMS', 'RMS'], titulo: true },
  canais: { nome: 'Canais', en: 'Channels', valores: ['2.0', '2.1', '3.1', '5.1'], unidade: ' canais', aliases: ['Canais', 'Configuração de canais'], titulo: true },
  resistencia_agua: { nome: 'Resistência à água', en: 'Water resistance', valores: ['IPX4', 'IPX5', 'IPX7', 'não'], aliases: ['Resistência à água', 'Proteção', 'À prova d\'água'], titulo: true },
  lumens: { nome: 'Brilho', en: 'Brightness', unidade: ' lumens', aliases: ['Lumens', 'Brilho', 'Luminosidade'], titulo: true },
  plataforma: { nome: 'Plataforma', en: 'Platform', valores: ['PS5', 'PS4', 'Xbox Series', 'Nintendo Switch', 'PC'], aliases: ['Plataforma', 'Console', 'Compatível com', 'Sistema'], titulo: true },
  tamanho_roupa: { nome: 'Tamanho', en: 'Size', valores: ['PP', 'P', 'M', 'G', 'GG', 'XG'], aliases: ['Tamanho', 'Tam', 'Size', 'Numeração', 'Grade'], titulo: true, prefixoTitulo: 'Tam ' },
  tamanho_numerico: { nome: 'Tamanho', en: 'Size', valores: ['36', '38', '40', '42', '44', '46', '48'], aliases: ['Tamanho', 'Numeração', 'Tam'], titulo: true, prefixoTitulo: 'Tam ' },
  tamanho_calcado: { nome: 'Numeração', en: 'Shoe size', valores: ['34', '35', '36', '37', '38', '39', '40', '41', '42', '43', '44'], aliases: ['Numeração', 'Tamanho', 'Número', 'Tam'], titulo: true, prefixoTitulo: 'Nº ' },
  genero: { nome: 'Gênero', en: 'Gender', valores: ['masculino', 'feminino', 'unissex'], aliases: ['Gênero', 'Genero', 'Público', 'Sexo', 'Departamento'], titulo: true },
  modelagem: { nome: 'Modelagem', en: 'Fit', valores: ['skinny', 'reta', 'slim', 'wide leg'], aliases: ['Modelagem', 'Modelo', 'Corte'], titulo: true },
  comprimento_roupa: { nome: 'Comprimento', en: 'Length', valores: ['curto', 'midi', 'longo'], aliases: ['Comprimento', 'Modelo'], titulo: true },
  tamanho_cama: { nome: 'Tamanho da cama', en: 'Bed size', valores: ['solteiro', 'casal', 'queen', 'king'], aliases: ['Tamanho', 'Medida', 'Tamanho da cama', 'Tipo de cama'], titulo: true },
  numero_fios: { nome: 'Número de fios', en: 'Thread count', valores: ['150', '180', '200', '300', '400'], unidade: ' fios', aliases: ['Fios', 'Número de fios', 'Nº de fios'], titulo: true },
  numero_pecas: { nome: 'Número de peças', en: 'Pieces', unidade: ' peças', aliases: ['Peças', 'Nº de peças', 'Quantidade de peças', 'Itens inclusos'], titulo: true },
  lugares: { nome: 'Lugares', en: 'Seats', valores: ['2', '3', '4', '5'], unidade: ' lugares', aliases: ['Lugares', 'Assentos', 'Capacidade'], titulo: true },
  altura_cm: { nome: 'Altura', en: 'Height', unidade: 'cm', longo: 'centímetros', aliases: ['Altura', 'Altura (cm)', 'Espessura'], titulo: true },
  tecnologia_colchao: { nome: 'Tecnologia', en: 'Technology', valores: ['molas ensacadas', 'espuma', 'híbrido'], aliases: ['Tecnologia', 'Tipo', 'Estrutura'], titulo: true },
  antiaderente: { nome: 'Antiaderente', en: 'Non-stick', valores: ['sim', 'não'], aliases: ['Antiaderente', 'Revestimento'], titulo: true },
  tipo_lampada: { nome: 'Tipo de lâmpada', en: 'Bulb', valores: ['LED', 'E27', 'G9'], aliases: ['Lâmpada', 'Tipo de lâmpada', 'Soquete'], titulo: false },
  dimensao_tapete: { nome: 'Dimensões', en: 'Size', valores: ['1,00 x 1,40 m', '1,50 x 2,00 m', '2,00 x 2,50 m'], aliases: ['Medidas', 'Dimensões', 'Tamanho'], titulo: true },
  bateria_v: { nome: 'Tensão da bateria', en: 'Battery voltage', valores: ['12V', '18V', '20V'], aliases: ['Bateria', 'Voltagem da bateria', 'Tensão'], titulo: true },
  alimentacao: { nome: 'Alimentação', en: 'Power source', valores: ['elétrica', 'bateria', 'manual'], aliases: ['Alimentação', 'Fonte de energia', 'Tipo'], titulo: true },
  pressao_psi: { nome: 'Pressão', en: 'Pressure', valores: ['1300', '1600', '1800', '2000'], unidade: ' PSI', aliases: ['Pressão', 'Pressão máxima', 'PSI', 'Libras'], titulo: true },
  degraus: { nome: 'Degraus', en: 'Steps', valores: ['3', '4', '5', '6', '7'], unidade: ' degraus', aliases: ['Degraus', 'Nº de degraus'], titulo: true },
  acabamento: { nome: 'Acabamento', en: 'Finish', valores: ['fosco', 'acetinado', 'semibrilho'], aliases: ['Acabamento', 'Brilho'], titulo: true },
  medida_pneu: { nome: 'Medida', en: 'Tire size', valores: ['175/70 R13', '185/65 R15', '205/55 R16', '225/45 R17'], aliases: ['Medida', 'Medida do pneu', 'Tamanho'], titulo: true },
  aro: { nome: 'Aro', en: 'Rim', valores: ['13', '14', '15', '16', '17'], aliases: ['Aro', 'Tamanho do aro'], titulo: true, prefixoTitulo: 'Aro ' },
  amperagem_ah: { nome: 'Amperagem', en: 'Amp-hours', valores: ['40', '45', '50', '60', '70'], unidade: 'Ah', aliases: ['Amperagem', 'Capacidade', 'Ah', 'Amperes'], titulo: true },
  viscosidade: { nome: 'Viscosidade', en: 'Viscosity', valores: ['5W30', '5W40', '10W40', '15W40', '20W50'], aliases: ['Viscosidade', 'Especificação', 'SAE'], titulo: true },
  tipo_oleo: { nome: 'Tipo de óleo', en: 'Oil type', valores: ['sintético', 'semissintético', 'mineral'], aliases: ['Tipo', 'Base'], titulo: true },
  tamanho_pol: { nome: 'Tamanho', en: 'Size', valores: ['14', '16', '18', '20', '22', '24'], unidade: '"', aliases: ['Tamanho', 'Medida', 'Polegadas'], titulo: true },
  modelo_lampada: { nome: 'Encaixe', en: 'Socket', valores: ['H1', 'H4', 'H7', 'H11', 'HB3'], aliases: ['Modelo', 'Encaixe', 'Tipo'], titulo: true },
  compatibilidade_veiculo: { nome: 'Veículo compatível', en: 'Vehicle', valores: ['Onix', 'HB20', 'Gol', 'Corolla', 'Civic', 'Compass', 'Strada', 'Argo', 'Polo', 'T-Cross'], aliases: ['Veículo', 'Compatível com', 'Modelo do carro', 'Aplicação'], titulo: true, prefixoTitulo: '' },
  idade_recomendada: { nome: 'Idade recomendada', en: 'Age', valores: ['0 a 2 anos', '3 a 5 anos', '6 a 8 anos', '9 a 12 anos', '12+ anos'], aliases: ['Idade', 'Idade recomendada', 'Faixa etária', 'Idade mínima'], titulo: true },
  requer_pilhas: { nome: 'Requer pilhas', en: 'Batteries required', valores: ['sim', 'não'], aliases: ['Pilhas', 'Requer pilhas', 'Funciona com pilha'], titulo: false },
  escala: { nome: 'Escala', en: 'Scale', valores: ['1:10', '1:16', '1:24', '1:43', '1:64'], aliases: ['Escala'], titulo: true },
  tamanho_cm: { nome: 'Tamanho', en: 'Size', unidade: 'cm', longo: 'centímetros', aliases: ['Tamanho', 'Altura', 'Medida'], titulo: true },
  numero_jogadores: { nome: 'Jogadores', en: 'Players', valores: ['2 a 4', '2 a 6', '3 a 8'], unidade: ' jogadores', aliases: ['Jogadores', 'Nº de jogadores', 'Participantes'], titulo: false },
  volume_ml: { nome: 'Volume', en: 'Volume', unidade: 'ml', longo: 'ml', aliases: ['Volume', 'Conteúdo', 'Quantidade', 'ml'], titulo: true },
  peso_g: { nome: 'Peso', en: 'Weight', unidade: 'g', longo: 'gramas', aliases: ['Peso', 'Peso líquido', 'Conteúdo', 'Gramas'], titulo: true },
  peso_kg: { nome: 'Peso', en: 'Weight', unidade: 'kg', longo: 'quilos', aliases: ['Peso', 'Peso líquido', 'Kg', 'Conteúdo'], titulo: true },
  tipo_cabelo: { nome: 'Tipo de cabelo', en: 'Hair type', valores: ['todos', 'cacheados', 'lisos', 'oleosos', 'danificados'], aliases: ['Tipo de cabelo', 'Indicação', 'Cabelos'], titulo: true, prefixoTitulo: 'cabelos ' },
  tipo_pele: { nome: 'Tipo de pele', en: 'Skin type', valores: ['seca', 'normal', 'oleosa', 'mista'], aliases: ['Tipo de pele', 'Indicação', 'Pele'], titulo: true, prefixoTitulo: 'pele ' },
  fragrancia: { nome: 'Fragrância', en: 'Scent', valores: ['amadeirado', 'floral', 'cítrico', 'oriental'], aliases: ['Fragrância', 'Família olfativa', 'Aroma', 'Perfume'], titulo: true },
  fps: { nome: 'FPS', en: 'SPF', valores: ['15', '30', '50', '70'], aliases: ['FPS', 'Fator de proteção', 'Proteção'], titulo: true, prefixoTitulo: 'FPS ' },
  cor_tom: { nome: 'Tom', en: 'Shade', valores: ['claro', 'médio', 'escuro'], aliases: ['Tom', 'Cor', 'Tonalidade'], titulo: true },
  acabamento_make: { nome: 'Acabamento', en: 'Finish', valores: ['matte', 'natural', 'luminoso'], aliases: ['Acabamento', 'Efeito'], titulo: true },
  temperatura_c: { nome: 'Temperatura máxima', en: 'Max temperature', valores: ['200', '210', '230'], unidade: '°C', aliases: ['Temperatura', 'Temperatura máxima'], titulo: true },
  fragrancia_limpeza: { nome: 'Fragrância', en: 'Scent', valores: ['neutro', 'limão', 'lavanda', 'floral', 'eucalipto'], aliases: ['Fragrância', 'Aroma', 'Perfume', 'Cheiro'], titulo: true },
  quantidade_unidades: { nome: 'Quantidade', en: 'Units', unidade: ' unidades', aliases: ['Quantidade', 'Unidades', 'Conteúdo', 'Qtd'], titulo: true },
  folhas_papel: { nome: 'Folha', en: 'Ply', valores: ['simples', 'dupla', 'tripla'], aliases: ['Folha', 'Tipo de folha'], titulo: true, prefixoTitulo: 'folha ' },
  tipo_cafe: { nome: 'Tipo', en: 'Type', valores: ['tradicional', 'extraforte', 'gourmet'], aliases: ['Tipo', 'Torra', 'Intensidade'], titulo: true },
  compat_capsula: { nome: 'Compatível com', en: 'Compatible with', valores: ['Nespresso', 'Dolce Gusto', 'Três Corações'], aliases: ['Compatível com', 'Máquina', 'Sistema'], titulo: true, prefixoTitulo: 'compatível ' },
  tipo_arroz: { nome: 'Tipo', en: 'Type', valores: ['tipo 1', 'integral', 'parboilizado'], aliases: ['Tipo', 'Classificação'], titulo: true },
  marchas: { nome: 'Marchas', en: 'Gears', valores: ['1', '18', '21', '24'], unidade: ' marchas', aliases: ['Marchas', 'Velocidades', 'Câmbio'], titulo: true },
  aro_bike: { nome: 'Aro', en: 'Wheel size', valores: ['26', '29'], aliases: ['Aro', 'Tamanho do aro', 'Roda'], titulo: true, prefixoTitulo: 'Aro ' },
  material_quadro: { nome: 'Material do quadro', en: 'Frame', valores: ['aço', 'alumínio', 'carbono'], aliases: ['Quadro', 'Material do quadro', 'Material'], titulo: true, prefixoTitulo: 'quadro ' },
  velocidade_max_kmh: { nome: 'Velocidade máxima', en: 'Max speed', valores: ['12', '14', '16', '18'], unidade: ' km/h', aliases: ['Velocidade máxima', 'Velocidade'], titulo: true },
  espessura_mm: { nome: 'Espessura', en: 'Thickness', valores: ['4', '6', '8', '10'], unidade: 'mm', aliases: ['Espessura', 'Altura'], titulo: true },
  sabor: { nome: 'Sabor', en: 'Flavor', valores: ['chocolate', 'baunilha', 'morango', 'sem sabor'], aliases: ['Sabor', 'Sabores'], titulo: true },
  tamanho_bola: { nome: 'Tamanho', en: 'Ball size', valores: ['4', '5', '7'], aliases: ['Tamanho', 'Número', 'Tam'], titulo: true, prefixoTitulo: 'Tamanho ' },
  porte: { nome: 'Porte', en: 'Breed size', valores: ['pequeno', 'médio', 'grande'], aliases: ['Porte', 'Tamanho do animal', 'Raça'], titulo: true, prefixoTitulo: 'porte ' },
  fase_vida: { nome: 'Fase da vida', en: 'Life stage', valores: ['filhote', 'adulto', 'sênior'], aliases: ['Idade', 'Fase', 'Fase da vida'], titulo: true },
  sabor_pet: { nome: 'Sabor', en: 'Flavor', valores: ['carne', 'frango', 'cordeiro', 'salmão'], aliases: ['Sabor'], titulo: true },
  especie: { nome: 'Espécie', en: 'Species', valores: ['cão', 'gato'], aliases: ['Espécie', 'Animal', 'Indicado para'], titulo: true },
  tamanho_pet: { nome: 'Tamanho', en: 'Size', valores: ['PP', 'P', 'M', 'G'], aliases: ['Tamanho', 'Tam'], titulo: true, prefixoTitulo: 'Tam ' },
  tipo_areia: { nome: 'Tipo', en: 'Type', valores: ['bentonita', 'sílica', 'biodegradável'], aliases: ['Tipo', 'Material', 'Composição'], titulo: true },
  formato_livro: { nome: 'Formato', en: 'Format', valores: ['capa comum', 'capa dura', 'livro de bolso'], aliases: ['Formato', 'Encadernação', 'Acabamento'], titulo: false },
  idioma: { nome: 'Idioma', en: 'Language', valores: ['português', 'inglês'], aliases: ['Idioma', 'Língua'], titulo: false },
  numero_paginas: { nome: 'Número de páginas', en: 'Pages', unidade: ' páginas', aliases: ['Páginas', 'Nº de páginas', 'Número de páginas'], titulo: false },
  materias: { nome: 'Matérias', en: 'Subjects', valores: ['1', '10', '15', '20'], unidade: ' matérias', aliases: ['Matérias', 'Divisórias'], titulo: true },
  folhas_caderno: { nome: 'Folhas', en: 'Sheets', valores: ['96', '160', '200', '300'], unidade: ' folhas', aliases: ['Folhas', 'Nº de folhas'], titulo: true },
  peso_suportado_kg: { nome: 'Peso suportado', en: 'Max weight', valores: ['13', '15', '18', '22', '36'], unidade: 'kg', aliases: ['Peso suportado', 'Peso máximo', 'Suporta até'], titulo: true, prefixoTitulo: 'até ' },
  grupo_cadeirinha: { nome: 'Grupo', en: 'Group', valores: ['0+', '0-1', '1-2-3', '2-3'], aliases: ['Grupo', 'Categoria'], titulo: true, prefixoTitulo: 'Grupo ' },
  tamanho_fralda: { nome: 'Tamanho', en: 'Size', valores: ['RN', 'P', 'M', 'G', 'XG', 'XXG'], aliases: ['Tamanho', 'Tam'], titulo: true, prefixoTitulo: 'Tam ' },
  video: { nome: 'Vídeo', en: 'Video', valores: ['sim', 'não'], aliases: ['Vídeo', 'Câmera', 'Com vídeo'], titulo: false },
  garantia_meses: { nome: 'Garantia', en: 'Warranty', valores: ['3', '6', '12', '24'], unidade: ' meses', aliases: ['Garantia', 'Garantia do fornecedor', 'Garantia (meses)'], titulo: false },
  // atributos de ficha técnica (estilo marketplace grande)
  origem: { nome: 'Origem', en: 'Origin', valores: ['nacional', 'importado'], aliases: ['Origem', 'Procedência', 'País de origem'], titulo: false },
  tipo_controle: { nome: 'Tipo de controle', en: 'Controls', valores: ['digital', 'mecânico', 'touch'], aliases: ['Painel', 'Controle', 'Tipo de painel', 'Tipo de controle'], titulo: true, prefixoTitulo: 'Painel ' },
  peso_produto_kg: { nome: 'Peso do produto', en: 'Item weight', unidade: 'kg', longo: 'quilos', aliases: ['Peso do produto', 'Peso líquido', 'Peso (kg)', 'Peso'], titulo: false },
  altura_produto_cm: { nome: 'Altura do produto', en: 'Height', unidade: 'cm', longo: 'centímetros', aliases: ['Altura', 'Altura do produto', 'Altura (cm)', 'A'], titulo: false },
  largura_produto_cm: { nome: 'Largura do produto', en: 'Width', unidade: 'cm', longo: 'centímetros', aliases: ['Largura', 'Largura do produto', 'Largura (cm)', 'L'], titulo: false },
  profundidade_produto_cm: { nome: 'Profundidade do produto', en: 'Depth', unidade: 'cm', longo: 'centímetros', aliases: ['Profundidade', 'Profundidade do produto', 'Profundidade (cm)', 'P'], titulo: false },
  certificacao_inmetro: { nome: 'Certificação Inmetro', en: 'Inmetro certified', valores: ['sim', 'não'], aliases: ['Inmetro', 'Certificação', 'Selo Inmetro'], titulo: false },
  consumo_kwh: { nome: 'Consumo de energia', en: 'Energy use', unidade: ' kWh/mês', aliases: ['Consumo', 'Consumo de energia', 'kWh/mês', 'Consumo mensal'], titulo: false },
  dispenser_agua: { nome: 'Dispenser de Água', en: 'Water dispenser', valores: ['sim', 'não'], aliases: ['Dispenser de água', 'Dispenser', 'Água na porta'], titulo: true },
  capacidade_freezer_l: { nome: 'Capacidade do freezer', en: 'Freezer capacity', unidade: 'L', longo: 'litros', aliases: ['Capacidade do freezer', 'Freezer', 'Volume do freezer'], titulo: false },
  tipo_abertura: { nome: 'Abertura', en: 'Loading', valores: ['frontal', 'superior'], aliases: ['Abertura', 'Tipo de abertura', 'Carregamento'], titulo: true, prefixoTitulo: 'Abertura ' },
  programas: { nome: 'Programas de lavagem', en: 'Programs', valores: ['10', '12', '15', '17'], unidade: ' programas', aliases: ['Programas', 'Nº de programas', 'Ciclos de lavagem'], titulo: false },
  camera_mp: { nome: 'Câmera principal', en: 'Main camera', valores: ['12', '48', '50', '64', '108', '200'], unidade: 'MP', aliases: ['Câmera', 'Câmera traseira', 'Resolução da câmera', 'Câmera principal'], titulo: true, sufixoTitulo: '' },
  bateria_mah: { nome: 'Bateria', en: 'Battery', valores: ['4000', '5000', '6000'], unidade: 'mAh', aliases: ['Bateria', 'Capacidade da bateria', 'mAh'], titulo: true },
  nfc: { nome: 'NFC', en: 'NFC', valores: ['sim', 'não'], aliases: ['NFC', 'Pagamento por aproximação'], titulo: true },
  dual_chip: { nome: 'Dual Chip', en: 'Dual SIM', valores: ['sim', 'não'], aliases: ['Dual Chip', 'Dual SIM', 'Chips'], titulo: true },
  processador_cel: { nome: 'Processador', en: 'Chipset', valores: ['Snapdragon', 'MediaTek Dimensity', 'MediaTek Helio', 'Exynos', 'Apple A16', 'Apple A17'], aliases: ['Processador', 'Chipset', 'CPU'], titulo: false },
  tecnologia_tela_cel: { nome: 'Tecnologia da tela', en: 'Display type', valores: ['LCD', 'AMOLED', 'OLED'], aliases: ['Tela', 'Tipo de tela', 'Tecnologia da tela', 'Display'], titulo: true },
  hdr: { nome: 'HDR', en: 'HDR', valores: ['sim', 'não'], aliases: ['HDR', 'Suporte a HDR'], titulo: true },
  portas_hdmi: { nome: 'Entradas HDMI', en: 'HDMI ports', valores: ['2', '3', '4'], unidade: ' HDMI', aliases: ['HDMI', 'Entradas HDMI', 'Portas HDMI'], titulo: false },
  assistente_voz: { nome: 'Assistente de voz', en: 'Voice assistant', valores: ['Alexa', 'Google Assistente', 'Bixby', 'não'], aliases: ['Assistente de voz', 'Comando de voz', 'Assistente virtual'], titulo: false },
  wifi: { nome: 'Wi-Fi', en: 'Wi-Fi', valores: ['sim', 'não'], aliases: ['Wi-Fi', 'Wifi', 'Conexão Wi-Fi'], titulo: true },
  bluetooth: { nome: 'Bluetooth', en: 'Bluetooth', valores: ['sim', 'não'], aliases: ['Bluetooth', 'Conexão Bluetooth'], titulo: true },
  placa_video: { nome: 'Placa de vídeo', en: 'Graphics', valores: ['integrada', 'dedicada'], aliases: ['Placa de vídeo', 'GPU', 'Vídeo', 'Gráficos'], titulo: true, prefixoTitulo: 'Placa de vídeo ' },
  sistema_pc: { nome: 'Sistema operacional', en: 'OS', valores: ['Windows 11', 'Linux', 'macOS', 'Chrome OS'], aliases: ['Sistema operacional', 'Sistema', 'SO'], titulo: true },
  teclado_numerico: { nome: 'Teclado numérico', en: 'Numeric keypad', valores: ['sim', 'não'], aliases: ['Teclado numérico', 'Numpad'], titulo: false },
  gola: { nome: 'Gola', en: 'Neckline', valores: ['redonda', 'V', 'polo'], aliases: ['Gola', 'Tipo de gola', 'Decote'], titulo: true, prefixoTitulo: 'Gola ' },
  manga: { nome: 'Manga', en: 'Sleeve', valores: ['curta', 'longa', 'regata'], aliases: ['Manga', 'Comprimento da manga'], titulo: true, prefixoTitulo: 'Manga ' },
  estampa: { nome: 'Estampa', en: 'Pattern', valores: ['lisa', 'estampada', 'listrada'], aliases: ['Estampa', 'Padronagem', 'Desenho'], titulo: true },
  indicado_para: { nome: 'Indicado para', en: 'Activity', valores: ['corrida', 'casual', 'academia', 'caminhada'], aliases: ['Indicado para', 'Esporte', 'Uso', 'Atividade'], titulo: true, prefixoTitulo: 'para ' },
  fechamento: { nome: 'Fechamento', en: 'Closure', valores: ['cadarço', 'velcro', 'slip-on'], aliases: ['Fechamento', 'Tipo de fechamento', 'Ajuste'], titulo: false },
  tipo_solado: { nome: 'Solado', en: 'Sole', valores: ['borracha', 'EVA', 'TPU'], aliases: ['Solado', 'Material do solado', 'Sola'], titulo: false },
  elastano: { nome: 'Com elastano', en: 'Stretch', valores: ['sim', 'não'], aliases: ['Elastano', 'Com elastano', 'Stretch'], titulo: true },
  densidade: { nome: 'Densidade', en: 'Density', valores: ['D28', 'D33', 'D45'], aliases: ['Densidade', 'Espuma'], titulo: true },
  firmeza: { nome: 'Firmeza', en: 'Firmness', valores: ['macio', 'médio', 'firme'], aliases: ['Firmeza', 'Conforto', 'Sensação'], titulo: true },
  antialergico: { nome: 'Antialérgico', en: 'Hypoallergenic', valores: ['sim', 'não'], aliases: ['Antialérgico', 'Hipoalergênico', 'Antiácaro'], titulo: true },
  retratil: { nome: 'Retrátil', en: 'Retractable', valores: ['sim', 'não'], aliases: ['Retrátil', 'Assento retrátil'], titulo: true },
  reclinavel: { nome: 'Reclinável', en: 'Reclining', valores: ['sim', 'não'], aliases: ['Reclinável', 'Encosto reclinável'], titulo: true },
  mandril_mm: { nome: 'Mandril', en: 'Chuck', valores: ['10', '13'], unidade: 'mm', aliases: ['Mandril', 'Capacidade do mandril', 'Mandril (mm)'], titulo: true, prefixoTitulo: 'Mandril ' },
  velocidade_rpm: { nome: 'Rotação', en: 'Speed', unidade: ' rpm', aliases: ['Rotação', 'RPM', 'Velocidade sem carga', 'Rotação máxima'], titulo: false },
  tipo_motor: { nome: 'Motor', en: 'Motor', valores: ['com escovas', 'brushless'], aliases: ['Motor', 'Tipo de motor'], titulo: true },
  maleta: { nome: 'Com Maleta', en: 'Carry case', valores: ['sim', 'não'], aliases: ['Maleta', 'Acompanha maleta', 'Estojo'], titulo: true },
  indice_carga: { nome: 'Índice de carga', en: 'Load index', valores: ['82', '88', '91', '94'], aliases: ['Índice de carga', 'Carga'], titulo: false },
  indice_velocidade: { nome: 'Índice de velocidade', en: 'Speed rating', valores: ['H', 'T', 'V'], aliases: ['Índice de velocidade', 'Velocidade'], titulo: false },
  suspensao: { nome: 'Suspensão', en: 'Suspension', valores: ['dianteira', 'full', 'sem suspensão'], aliases: ['Suspensão', 'Amortecedor'], titulo: true, prefixoTitulo: 'Suspensão ' },
  freio: { nome: 'Freio', en: 'Brakes', valores: ['V-brake', 'disco mecânico', 'disco hidráulico'], aliases: ['Freio', 'Tipo de freio', 'Freios'], titulo: true, prefixoTitulo: 'Freio ' },
  dobravel: { nome: 'Dobrável', en: 'Foldable', valores: ['sim', 'não'], aliases: ['Dobrável', 'Fechamento'], titulo: true },
  inclinacao: { nome: 'Inclinação', en: 'Incline', valores: ['manual', 'elétrica'], aliases: ['Inclinação', 'Ajuste de inclinação'], titulo: true, prefixoTitulo: 'Inclinação ' },
  isofix: { nome: 'Isofix', en: 'ISOFIX', valores: ['sim', 'não'], aliases: ['Isofix', 'Sistema Isofix', 'Fixação'], titulo: true },
  castrado: { nome: 'Castrados', en: 'Neutered', valores: ['sim', 'não'], aliases: ['Castrados', 'Para castrados', 'Indicação'], titulo: true },
  livre_bpa: { nome: 'Livre de BPA', en: 'BPA free', valores: ['sim', 'não'], aliases: ['Livre de BPA', 'BPA free', 'Sem BPA'], titulo: true },
  vegano: { nome: 'Vegano', en: 'Vegan', valores: ['sim', 'não'], aliases: ['Vegano', 'Fórmula vegana'], titulo: true },
  livre_crueldade: { nome: 'Cruelty Free', en: 'Cruelty free', valores: ['sim', 'não'], aliases: ['Cruelty free', 'Não testado em animais'], titulo: false },
  validade_meses: { nome: 'Validade', en: 'Shelf life', valores: ['6', '12', '18', '24'], unidade: ' meses', aliases: ['Validade', 'Prazo de validade', 'Validade (meses)'], titulo: false },
  sem_gluten: { nome: 'Sem Glúten', en: 'Gluten free', valores: ['sim', 'não'], aliases: ['Glúten', 'Contém glúten', 'Sem glúten'], titulo: true },
  organico: { nome: 'Orgânico', en: 'Organic', valores: ['sim', 'não'], aliases: ['Orgânico', 'Certificação orgânica'], titulo: true },
  sem_corantes: { nome: 'Sem Corantes', en: 'No artificial colors', valores: ['sim', 'não'], aliases: ['Corantes', 'Sem corantes'], titulo: true },
  genero_literario: { nome: 'Gênero literário', en: 'Genre', valores: ['romance', 'suspense', 'fantasia', 'ficção científica', 'drama'], aliases: ['Gênero', 'Gênero literário', 'Categoria'], titulo: true },
}

// Ficha técnica extra por departamento e por categoria (vale para subcategorias do tipo "produto";
// acessórios e consumíveis recebem só "origem" e "garantia_meses" quando o departamento tiver garantia).
export const EXTRAS_DEP = {
  eletrodomesticos: 'tipo_controle origem garantia_meses consumo_kwh:15-90 altura_produto_cm:40-190 largura_produto_cm:40-95 profundidade_produto_cm:30-80 peso_produto_kg:5-95 certificacao_inmetro',
  eletroportateis: 'tipo_controle origem garantia_meses peso_produto_kg:0.5-12:0.1 altura_produto_cm:10-60 largura_produto_cm:10-50 profundidade_produto_cm:10-50 certificacao_inmetro',
  celulares: 'garantia_meses origem peso_produto_kg:0.02-0.3:0.01',
  informatica: 'garantia_meses origem peso_produto_kg:0.05-15:0.05',
  tv_audio: 'garantia_meses origem peso_produto_kg:0.1-30:0.1',
  moda: 'origem',
  casa: 'origem garantia_meses',
  ferramentas: 'garantia_meses origem peso_produto_kg:0.2-25:0.1 maleta',
  automotivo: 'garantia_meses origem',
  brinquedos: 'certificacao_inmetro origem material=plástico|tecido|madeira|metal',
  beleza: 'vegano livre_crueldade origem validade_meses',
  mercado: 'validade_meses origem',
  esporte: 'garantia_meses origem',
  pet: 'origem',
  livros_papelaria: 'origem',
  bebes: 'certificacao_inmetro origem',
}
export const EXTRAS_CAT = {
  aparelhos_beleza: 'garantia_meses origem peso_produto_kg:0.2-1.5:0.05',
  alimentos: 'validade_meses sem_gluten organico origem',
  alimentacao_pet: 'sem_corantes validade_meses origem',
  alimentacao_bebe: 'certificacao_inmetro livre_bpa origem',
  livros: 'peso_produto_kg:0.1-1.5:0.05',
  tvs: 'garantia_meses origem peso_produto_kg:3-40:0.5 altura_produto_cm:40-110 largura_produto_cm:70-170',
}
export const EXTRAS_FOLHA = {
  geladeiras: 'dispenser_agua capacidade_freezer_l:40-150:5 inverter',
  fogoes: 'tipo_energia=gás capacidade_l:50-110:2',
  maquinas_lavar: 'tipo_abertura inverter programas',
  lava_secas: 'tipo_abertura=frontal inverter programas',
  ar_condicionado: 'wifi',
  smartphones: 'camera_mp bateria_mah nfc dual_chip processador_cel tecnologia_tela_cel taxa_hz=60|90|120',
  notebooks: 'placa_video sistema_pc teclado_numerico tecnologia_tela_cel=IPS|OLED|TN',
  tablets: 'camera_mp=8|12|13 wifi bateria_mah=5000|7000|8000|10000',
  smart_tvs: 'hdr portas_hdmi assistente_voz wifi bluetooth taxa_hz=60|120',
  soundbars: 'wifi',
  caixas_som: 'bluetooth=sim autonomia_h:4-24',
  monitores: 'hdr portas_hdmi=1|2',
  tenis: 'indicado_para fechamento tipo_solado material=tecido|couro|sintético|camurça',
  camisetas: 'gola manga estampa',
  calcas: 'material=jeans|sarja|moletom elastano',
  vestidos: 'estampa',
  colchoes: 'densidade firmeza antialergico',
  travesseiros: 'antialergico firmeza',
  sofas: 'retratil reclinavel',
  furadeiras: 'mandril_mm velocidade_rpm:2500-3500:100 tipo_motor',
  parafusadeiras: 'mandril_mm velocidade_rpm:1200-2000:100 tipo_motor',
  pneus: 'indice_carga indice_velocidade',
  bicicletas: 'suspensao freio',
  esteiras: 'dobravel inclinacao',
  carrinhos_bebe: 'reclinavel dobravel',
  cadeirinhas_auto: 'isofix',
  racoes_gatos: 'castrado',
  livros_ficcao: 'genero_literario',
}

// Linhas comerciais: nomes que o mercado usa no lugar do nome do produto ("Galaxy A55", "Air Max 90").
// Quando o título traz só a linha, a subcategoria depende de conhecimento de mundo.
export const LINHAS = {
  smartphones: { Samsung: ['Galaxy A15', 'Galaxy A35', 'Galaxy A55', 'Galaxy S24', 'Galaxy S24 Ultra'], Apple: ['iPhone 13', 'iPhone 14', 'iPhone 15', 'iPhone 15 Pro', 'iPhone 16'], Motorola: ['Moto G24', 'Moto G54', 'Moto G84', 'Edge 50'], Xiaomi: ['Redmi Note 13', 'Redmi 13C', 'Poco X6'], Realme: ['Realme C67', 'Realme 12'], Asus: ['Zenfone 10', 'ROG Phone 8'], Nokia: ['Nokia G42'], Positivo: ['Twist 5'] },
  notebooks: { Dell: ['Inspiron 15', 'Vostro 3520', 'XPS 13', 'G15'], Lenovo: ['IdeaPad 3', 'IdeaPad Slim 5', 'ThinkPad E14', 'Legion 5'], HP: ['HP 250 G9', 'Pavilion 15', 'Victus 15'], Acer: ['Aspire 5', 'Nitro 5', 'Swift Go'], Asus: ['Vivobook 15', 'Zenbook 14', 'TUF Gaming F15'], Apple: ['MacBook Air M2', 'MacBook Air M3', 'MacBook Pro 14'], Samsung: ['Galaxy Book4'], Positivo: ['Motion Q464C', 'Vaio FE15'] },
  tablets: { Apple: ['iPad 10ª geração', 'iPad Air'], Samsung: ['Galaxy Tab A9', 'Galaxy Tab S9 FE'], Lenovo: ['Tab M10', 'Tab P12'], Xiaomi: ['Redmi Pad SE', 'Xiaomi Pad 6'] },
  smart_tvs: { Samsung: ['Crystal UHD', 'Neo QLED', 'The Frame'], LG: ['NanoCell', 'OLED evo', 'UHD ThinQ AI'], TCL: ['P755', 'C755'], Philco: ['Roku TV'], Sony: ['Bravia'] },
  consoles: { Sony: ['PlayStation 5', 'PS5 Slim'], Microsoft: ['Xbox Series S', 'Xbox Series X'], Nintendo: ['Nintendo Switch OLED', 'Nintendo Switch Lite'] },
  smartwatches: { Apple: ['Apple Watch Series 9', 'Apple Watch SE'], Samsung: ['Galaxy Watch 6', 'Galaxy Watch 7', 'Galaxy Fit 3'], Xiaomi: ['Redmi Watch 4', 'Mi Band 8', 'Smart Band 8'], Amazfit: ['Amazfit Bip 5', 'Amazfit GTR 4'], Huawei: ['Watch Fit 3'] },
  fones_bluetooth: { Apple: ['AirPods Pro', 'AirPods 3'], Samsung: ['Galaxy Buds2 Pro', 'Galaxy Buds FE'], JBL: ['Tune 520BT', 'Wave Buds'], Sony: ['WH-1000XM5', 'WF-C700N'], QCY: ['QCY T13'], Edifier: ['W820NB'] },
  caixas_som: { JBL: ['JBL Flip 6', 'JBL Charge 5', 'JBL Boombox 3', 'JBL Go 3', 'JBL PartyBox 110'], Sony: ['SRS-XB100'], 'Harman Kardon': ['Onyx Studio 8'] },
  air_fryers: { Arno: ['Easy Fry Max', 'Air Fry Super'], Philips: ['Airfryer Essential XL', 'Airfryer XXL'], Mondial: ['Family IV', 'Grand Family'], Britânia: ['BFR38', 'BFR11'] },
  geladeiras: { Brastemp: ['Inverse', 'Frost Free Duplex'], Electrolux: ['Inverter Top Freezer', 'Multidoor'], Samsung: ['Bespoke', 'French Door'], LG: ['InstaView', 'Door Cooling'] },
  tenis: { Nike: ['Air Max 90', 'Revolution 7', 'Air Force 1'], Adidas: ['Ultraboost', 'Grand Court', 'Runfalcon'], Mizuno: ['Wave Prophecy', 'Wave Rider'], Olympikus: ['Corre 3', 'Venus'], Vans: ['Old Skool'], Converse: ['Chuck Taylor All Star'], Puma: ['Suede Classic'] },
  bicicletas: { Caloi: ['Caloi Elite', 'Caloi Explorer'], Houston: ['Foxer'], Sense: ['Rock Evo'] },
  perfumes: { 'O Boticário': ['Malbec', 'Lily', 'Egeo'], Natura: ['Kaiak', 'Essencial', 'Luna'], Avon: ['Far Away'] },
  blocos_montar: { Lego: ['Lego City', 'Lego Classic', 'Lego Technic'] },
  racoes_caes: { Golden: ['Golden Fórmula', 'Golden Seleção Natural'], Premier: ['Premier Fórmula'], 'Royal Canin': ['Mini Adult', 'Maxi Adult'], Pedigree: ['Nutrição Essencial'] },
  racoes_gatos: { Golden: ['Golden Gatos Castrados'], Whiskas: ['Whiskas Adulto'], Friskies: ['Friskies Mix'], 'Royal Canin': ['Indoor 27'] },
  cafeteiras: { Arno: ['Dolce Gusto Genio S', 'Dolce Gusto Mini Me'], Oster: ['PrimaLatte'], Philips: ['LatteGo 3200'] },
  parafusadeiras: { Bosch: ['GSR 120-LI', 'GSB 18V'], Makita: ['DF333D'], DeWalt: ['DCD7781'] },
}

// Frases por categoria quando o departamento mistura assuntos diferentes.
export const BENEFICIOS_CAT = {
  smartphones: ['Câmera de alta resolução para fotos incríveis.', 'Bateria de longa duração.', 'Tela com cores vivas.', 'Carregamento rápido.', 'Desempenho para jogos e aplicativos.', 'Desbloqueio por biometria.'],
  acessorios_celular: ['Proteção contra quedas e arranhões.', 'Encaixe perfeito.', 'Acesso fácil a todos os botões.', 'Material resistente.', 'Carregamento rápido.', 'Compatível com os principais modelos.'],
  wearables: ['Monitoramento de batimentos e sono.', 'Bateria de longa duração.', 'Resistente a suor.', 'Conexão bluetooth estável.', 'Leve e confortável.', 'Notificações do celular no pulso.'],
  limpeza: ['Remove a sujeira com facilidade.', 'Perfume duradouro.', 'Rende mais.', 'Embalagem econômica.', 'Biodegradável.', 'Ação antibacteriana.'],
  alimentos: ['Sabor tradicional.', 'Produto selecionado.', 'Ideal para toda a família.', 'Embalagem econômica.', 'Qualidade garantida.', 'Rico em sabor.'],
  alimentacao_pet: ['Nutrição completa e balanceada.', 'Seu pet vai adorar.', 'Ingredientes selecionados.', 'Ajuda na digestão.', 'Pelos mais bonitos.'],
  acessorios_pet: ['Material resistente a mordidas.', 'Fácil de limpar.', 'Conforto para o seu pet.', 'Ajustável.', 'Controla odores.'],
  livros: ['Leitura envolvente.', 'Best-seller.', 'Edição especial.', 'Do autor mais vendido do ano.', 'Ótimo para presentear.'],
  papelaria: ['Papel de alta qualidade.', 'Capa resistente.', 'Ideal para a volta às aulas.', 'Escrita macia.', 'Cores vibrantes.'],
  aparelhos_beleza: ['Aquecimento rápido.', 'Cabo giratório.', 'Diversas temperaturas.', 'Leve e fácil de manusear.', 'Uso profissional.'],
}

// Sinônimos que vendedores usam para valores canônicos (usados no ruído e conferidos na avaliação).
export const SINONIMOS_VALOR = {
  voltagem: { '127V': ['127V', '110V', '127 v', '110 volts', '110v', '127 Volts'], '220V': ['220V', '220 v', '220 volts', '220v', '220 Volts'], 'Bivolt': ['Bivolt', 'bivolt', '110/220V', '127/220V', 'Bivolt automático', 'bi-volt'] },
  cor: {
    preto: ['preto', 'preta', 'black', 'ônix', 'PRETO', 'pto'], branco: ['branco', 'branca', 'white', 'BRANCO', 'off white'],
    cinza: ['cinza', 'grafite', 'chumbo', 'gray', 'cinza escuro'], prata: ['prata', 'silver', 'prateado', 'inox', 'aço escovado'],
    azul: ['azul', 'azul marinho', 'blue', 'navy'], vermelho: ['vermelho', 'vermelha', 'red', 'bordô'], rosa: ['rosa', 'pink', 'rosé', 'rose gold'], verde: ['verde', 'green', 'verde militar'],
    inox: ['inox', 'aço inox', 'inoxidável', 'stainless', 'Inox'], bege: ['bege', 'areia', 'nude', 'off white'], marrom: ['marrom', 'café', 'chocolate', 'castanho'],
    amarelo: ['amarelo', 'yellow', 'mostarda'], dourado: ['dourado', 'gold', 'ouro'], transparente: ['transparente', 'cristal', 'incolor'], colorido: ['colorido', 'multicolor', 'estampado', 'sortido'],
  },
  sim: ['sim', 'Sim', 'S', 'yes', 'possui', 'com'], 'não': ['não', 'Não', 'N', 'no', 'sem', 'não possui'],
}

// ---------------------------------------------------------------------------
// Marcas por grupo
// ---------------------------------------------------------------------------
export const MARCAS = {
  eletro: ['Brastemp', 'Electrolux', 'Consul', 'Samsung', 'LG', 'Midea', 'Philco', 'Mueller', 'Esmaltec', 'Atlas', 'Fischer', 'Panasonic'],
  portateis: ['Mondial', 'Britânia', 'Philco', 'Arno', 'Oster', 'Black+Decker', 'Electrolux', 'Cadence', 'Walita', 'WAP', 'Tramontina', 'Philips'],
  celular: ['Samsung', 'Apple', 'Motorola', 'Xiaomi', 'Realme', 'Asus', 'Nokia', 'Positivo'],
  acess_cel: ['Gshield', 'Spigen', 'Baseus', 'Ugreen', 'Geonav', 'Multilaser', 'i2GO', 'Kingo', 'Anker', 'Samsung', 'Apple'],
  wearable: ['Samsung', 'Apple', 'Xiaomi', 'Amazfit', 'Huawei', 'JBL', 'Sony', 'Edifier', 'QCY', 'Multilaser'],
  informatica: ['Dell', 'Lenovo', 'HP', 'Acer', 'Asus', 'Apple', 'Samsung', 'LG', 'Positivo', 'Logitech', 'Multilaser', 'HyperX', 'Kingston', 'Redragon', 'Epson', 'Brother', 'Canon', 'TP-Link', 'Intelbras', 'SanDisk', 'Seagate', 'WD', 'AOC'],
  tv: ['Samsung', 'LG', 'TCL', 'Philco', 'Sony', 'Philips', 'AOC', 'Multilaser', 'JBL', 'Harman Kardon', 'Epson', 'BenQ', 'ELG', 'Brasforma', 'Microsoft', 'Nintendo'],
  moda: ['Nike', 'Adidas', 'Puma', 'Hering', 'Renner', 'Levi\'s', 'Colcci', 'Olympikus', 'Mizuno', 'Vans', 'Converse', 'Havaianas', 'Grendene', 'Arezzo', 'Schutz', 'Lacoste', 'Reserva', 'Farm', 'Calvin Klein', 'Ray-Ban', 'Chilli Beans', 'Technos', 'Casio', 'Mormaii', 'Lupo', 'Track&Field'],
  casa: ['Tramontina', 'Rochedo', 'Camesa', 'Santista', 'Buddemeyer', 'Altenburg', 'Ortobom', 'Castor', 'Probel', 'Mobly', 'Madesa', 'Taschibra', 'Avant', 'Brinox', 'Ordene', 'Oxford', 'Panelux', 'Electrolux', 'Kacyumara'],
  ferramentas: ['Bosch', 'Makita', 'DeWalt', 'Black+Decker', 'Vonder', 'Tramontina', 'Stanley', 'Einhell', 'WAP', 'Kärcher', 'Schulz', 'Worker', 'Skil', 'Suvinil', 'Coral', 'Trapp', 'Mor'],
  auto: ['Pirelli', 'Michelin', 'Goodyear', 'Bridgestone', 'Continental', 'Moura', 'Heliar', 'Zetta', 'Mobil', 'Castrol', 'Shell', 'Lubrax', 'Bosch', 'Dyna', 'Multilaser', 'Pioneer', 'Positron', 'Philips', 'Osram', 'Kenwood'],
  brinquedos: ['Mattel', 'Hasbro', 'Estrela', 'Lego', 'Grow', 'Toyng', 'Candide', 'Xalingo', 'Bandeirante', 'Fisher-Price', 'Cotiplás', 'Elka', 'Hot Wheels', 'Nerf', 'Zippy Toys', 'Bel Fix'],
  beleza: ['L\'Oréal', 'Natura', 'O Boticário', 'Nivea', 'Dove', 'Pantene', 'Elseve', 'Seda', 'Eudora', 'Maybelline', 'Avon', 'Vult', 'La Roche-Posay', 'Sundown', 'Taiff', 'Philips', 'Mondial', 'Gama', 'Gillette', 'Wahl', 'Salon Line', 'Lola', 'Ruby Rose'],
  mercado: ['Ypê', 'Omo', 'Comfort', 'Veja', 'Pinho Sol', 'Melitta', 'Pilão', '3 Corações', 'Tio João', 'Camil', 'Gallo', 'Andorinha', 'Lacta', 'Nestlé', 'Garoto', 'Neve', 'Personal', 'Brilhante', 'Downy', 'Qualy'],
  esporte: ['Caloi', 'Houston', 'Sense', 'Oxer', 'Kikos', 'Movement', 'Polimet', 'Nike', 'Adidas', 'Penalty', 'Umbro', 'Stanley', 'Contigo', 'Nautika', 'Coleman', 'Max Titanium', 'Growth', 'Integralmédica', 'Probiótica', 'Mor', 'Termolar'],
  pet: ['Pedigree', 'Golden', 'Premier', 'Royal Canin', 'Whiskas', 'Friskies', 'Special Dog', 'Pipicat', 'Kelco', 'Chalesco', 'Pet Games', 'Furacão Pet', 'Zee.Dog', 'Max'],
  livros: ['Companhia das Letras', 'Intrínseca', 'Rocco', 'Sextante', 'Record', 'Arqueiro', 'HarperCollins', 'Panda Books', 'Todolivro', 'Tilibra', 'Faber-Castell', 'BIC', 'Compactor', 'Foroni', 'Jandaia', 'Stabilo', 'Sestini', 'Pacific', 'Dermiwil'],
  bebe: ['Burigotto', 'Galzerano', 'Chicco', 'Fisher-Price', 'Infanti', 'Cosco', 'Safety 1st', 'MAM', 'Philips Avent', 'Lillo', 'Kiddo', 'Tutti Baby', 'Pampers', 'Huggies', 'MamyPoko', 'Johnson\'s', 'Multikids', 'Motorola'],
}

// Marcas que só aparecem nos itens novos (drift de marca).
export const MARCAS_NOVAS = ['Kaori', 'Voltix', 'Brisa Home', 'Nexa', 'Trivok', 'Lumora', 'Ondra', 'Vexo', 'Pratika', 'Zuni', 'Aurel', 'Kivo', 'Domus Pro', 'Fenix Tech', 'Mirabel', 'Tecna', 'Solare', 'Ventura', 'Orbix', 'Calibre']

// Modelos de celular e impressora usados em compatibilidade
export const MODELOS_CEL = ['Galaxy A15', 'Galaxy A35', 'Galaxy A55', 'Galaxy S24', 'Galaxy S24 Ultra', 'iPhone 13', 'iPhone 14', 'iPhone 15', 'iPhone 15 Pro', 'iPhone 16', 'Moto G24', 'Moto G54', 'Moto G84', 'Edge 50', 'Redmi Note 13', 'Redmi 13C', 'Poco X6', 'Realme C67']
export const MODELOS_IMPRESSORA = ['Epson L3250', 'Epson L4260', 'HP DeskJet 2774', 'HP Smart Tank 581', 'Canon G3110', 'Brother DCP-T420W', 'HP LaserJet M111w']
export const MODELOS_TV = ['Samsung', 'LG', 'TCL', 'Philco', 'Sony', 'Philips', 'AOC', 'universal']
export const MODELOS_ELETRO = ['Brastemp', 'Electrolux', 'Consul', 'Atlas', 'Esmaltec', 'Mueller', 'Dako', 'Fischer']

// ---------------------------------------------------------------------------
// Frases por departamento para bullets e descrição
// ---------------------------------------------------------------------------
export const BENEFICIOS = {
  eletrodomesticos: ['Design moderno que combina com qualquer cozinha.', 'Baixo consumo de energia.', 'Fácil de limpar.', 'Mais espaço interno para organizar seus alimentos.', 'Painel com controles intuitivos.', 'Acabamento resistente e durável.', 'Funcionamento silencioso.', 'Ideal para famílias grandes.'],
  eletroportateis: ['Prático para o dia a dia.', 'Peças removíveis que vão à lava-louças.', 'Pés antiderrapantes.', 'Compacto, ocupa pouco espaço na bancada.', 'Prepara receitas mais saudáveis.', 'Trava de segurança.', 'Aquecimento rápido.', 'Cabo com enrolador.'],
  celulares: ['Câmera de alta resolução para fotos incríveis.', 'Bateria de longa duração.', 'Tela com cores vivas.', 'Proteção contra quedas e arranhões.', 'Carregamento rápido.', 'Encaixe perfeito.', 'Acesso fácil a todos os botões.', 'Material resistente.'],
  informatica: ['Desempenho para trabalho e estudo.', 'Ideal para jogos.', 'Conexão estável.', 'Design ergonômico.', 'Plug and play.', 'Leve e fácil de transportar.', 'Alta velocidade de transferência.', 'Compatível com Windows e macOS.'],
  tv_audio: ['Imagem com cores mais realistas.', 'Acesso aos principais aplicativos de streaming.', 'Som envolvente.', 'Conexão bluetooth com o celular.', 'Controle por voz.', 'Experiência de cinema em casa.', 'Instalação simples.', 'Graves potentes.'],
  moda: ['Tecido macio e confortável.', 'Caimento perfeito.', 'Ideal para o dia a dia.', 'Peça versátil para várias ocasiões.', 'Costura reforçada.', 'Solado antiderrapante.', 'Leve e respirável.', 'Não desbota na lavagem.'],
  casa: ['Deixa o ambiente mais aconchegante.', 'Material de alta qualidade.', 'Fácil de montar.', 'Toque macio.', 'Combina com diferentes estilos de decoração.', 'Resistente ao uso diário.', 'Não desbota.', 'Ótimo custo-benefício.'],
  ferramentas: ['Uso profissional e doméstico.', 'Empunhadura emborrachada.', 'Alta durabilidade.', 'Motor potente.', 'Maleta para transporte.', 'Leve e fácil de manusear.', 'Trava de segurança.', 'Ideal para reformas.'],
  automotivo: ['Instalação fácil.', 'Maior segurança ao dirigir.', 'Alta durabilidade.', 'Encaixe sob medida.', 'Ótima aderência.', 'Protege o interior do veículo.', 'Resistente ao calor.', 'Produto homologado.'],
  brinquedos: ['Estimula a criatividade.', 'Diversão garantida para as crianças.', 'Material atóxico.', 'Desenvolve a coordenação motora.', 'Peças grandes e seguras.', 'Ótimo para presentear.', 'Com luzes e sons.', 'Certificado pelo Inmetro.'],
  beleza: ['Resultado visível desde a primeira aplicação.', 'Dermatologicamente testado.', 'Fórmula vegana.', 'Fragrância marcante.', 'Hidratação profunda.', 'Longa duração.', 'Não testado em animais.', 'Protege contra o frizz.'],
  mercado: ['Rende mais.', 'Sabor tradicional.', 'Embalagem econômica.', 'Remove a sujeira com facilidade.', 'Perfume duradouro.', 'Ideal para toda a família.', 'Qualidade garantida.', 'Produto selecionado.'],
  esporte: ['Ideal para treinos em casa.', 'Material resistente.', 'Leve e confortável.', 'Mantém a temperatura por horas.', 'Alta performance.', 'Ajuda a ganhar massa muscular.', 'Fácil de transportar.', 'Uso profissional e amador.'],
  pet: ['Nutrição completa e balanceada.', 'Seu pet vai adorar.', 'Material resistente a mordidas.', 'Fácil de limpar.', 'Conforto para o seu pet.', 'Controla odores.', 'Ingredientes selecionados.', 'Ajustável.'],
  livros_papelaria: ['Leitura envolvente.', 'Best-seller.', 'Edição especial.', 'Papel de alta qualidade.', 'Capa resistente.', 'Ideal para a volta às aulas.', 'Escrita macia.', 'Ótimo para presentear.'],
  bebes: ['Segurança para o seu bebê.', 'Leve e fácil de dobrar.', 'Certificado pelo Inmetro.', 'Livre de BPA.', 'Confortável e macio.', 'Proteção contra vazamentos.', 'Fácil de limpar.', 'Acompanha o crescimento da criança.'],
}

// ---------------------------------------------------------------------------
// Subcategorias. f(id, nome, nome_en, termos, atributos, [preçoMin, preçoMax], grupoDeMarcas, opções)
// opções: tipo ('produto' | 'acessorio' | 'consumivel'), de (subcategorias de que é acessório),
// conf (subcategorias com que vendedores confundem), peso (frequência relativa), grande (embalagem grande)
// ---------------------------------------------------------------------------
const f = (id, nome, nome_en, termos, attrs, preco, marcas, o = {}) => ({ id, nome, nome_en, termos, attrs, preco, marcas, tipo: o.tipo || 'produto', de: o.de || [], conf: o.conf || [], peso: o.peso ?? 1, grande: !!o.grande })
const c = (id, nome, nome_en, folhas) => ({ id, nome, nome_en, folhas })
const d = (id, nome, nome_en, categorias) => ({ id, nome, nome_en, categorias })

const COR_ELETRO = 'cor=branco|inox|preto|prata'
const COR_GERAL = 'cor=preto|branco|cinza|azul|vermelho|rosa|verde|bege|marrom|amarelo'

export const DEPARTAMENTOS = [
  d('eletrodomesticos', 'Eletrodomésticos', 'Home appliances', [
    c('refrigeracao', 'Refrigeração', 'Refrigeration', [
      f('geladeiras', 'Geladeiras', 'Refrigerators', ['geladeira', 'refrigerador', 'geladeira frost free', 'geladeira duplex'], `voltagem ${COR_ELETRO} capacidade_l:250-600:5 frost_free numero_portas=1|2|3|4 eficiencia_energetica garantia_meses`, [1800, 9500], 'eletro', { peso: 2, grande: true, conf: ['frigobares', 'freezers'] }),
      f('freezers', 'Freezers', 'Freezers', ['freezer', 'freezer horizontal', 'freezer vertical', 'congelador'], `voltagem cor=branco|inox capacidade_l:100-500:5 numero_portas=1|2 eficiencia_energetica`, [1500, 5500], 'eletro', { grande: true, conf: ['geladeiras', 'coolers'] }),
      f('frigobares', 'Frigobares', 'Mini fridges', ['frigobar', 'minibar', 'mini geladeira'], `voltagem ${COR_ELETRO} capacidade_l:45-120:5`, [900, 2500], 'eletro', { conf: ['geladeiras', 'coolers'] }),
      f('adegas', 'Adegas climatizadas', 'Wine coolers', ['adega climatizada', 'adega de vinhos', 'adega'], 'voltagem cor=preto|inox capacidade_garrafas:8-50:4', [700, 4500], 'eletro'),
    ]),
    c('fogoes_fornos', 'Fogões e fornos', 'Stoves and ovens', [
      f('fogoes', 'Fogões', 'Stoves', ['fogão', 'fogão de piso', 'fogão a gás'], `numero_bocas ${COR_ELETRO} tipo_acendimento voltagem garantia_meses`, [700, 4500], 'eletro', { peso: 2, grande: true, conf: ['cooktops', 'cozinhas_brinquedo', 'acessorios_fogao'] }),
      f('cooktops', 'Cooktops', 'Cooktops', ['cooktop', 'cooktop a gás', 'cooktop de indução', 'fogão cooktop'], 'numero_bocas=1|2|4|5 tipo_energia cor=preto|inox voltagem', [350, 4000], 'eletro', { conf: ['fogoes'] }),
      f('fornos_embutir', 'Fornos de embutir', 'Built-in ovens', ['forno de embutir', 'forno elétrico de embutir', 'forno a gás de embutir'], 'capacidade_l:50-85 voltagem cor=preto|inox', [1200, 5000], 'eletro', { grande: true, conf: ['fornos_eletricos', 'micro_ondas'] }),
      f('coifas', 'Coifas e depuradores', 'Range hoods', ['coifa', 'depurador de ar', 'coifa de parede', 'coifa de ilha'], 'largura_cm=60|75|90 voltagem cor=preto|inox', [400, 3500], 'eletro'),
    ]),
    c('lavanderia', 'Lavanderia', 'Laundry', [
      f('maquinas_lavar', 'Máquinas de lavar', 'Washing machines', ['máquina de lavar', 'lavadora de roupas', 'máquina de lavar roupas'], `capacidade_kg:8-17 voltagem cor=branco|prata|cinza eficiencia_energetica`, [1400, 5500], 'eletro', { peso: 1.5, grande: true, conf: ['lava_secas', 'tanquinhos', 'lavadoras_alta_pressao'] }),
      f('lava_secas', 'Lava e seca', 'Washer-dryers', ['lava e seca', 'lavadora e secadora'], 'capacidade_kg:9-14 voltagem cor=branco|prata|cinza|inox eficiencia_energetica', [2800, 8000], 'eletro', { grande: true, conf: ['maquinas_lavar', 'secadoras'] }),
      f('secadoras', 'Secadoras', 'Dryers', ['secadora de roupas', 'secadora'], 'capacidade_kg:8-12 voltagem cor=branco|prata', [1200, 4500], 'eletro', { grande: true, conf: ['secadores_cabelo'] }),
      f('tanquinhos', 'Tanquinhos', 'Semi-automatic washers', ['tanquinho', 'lavadora semiautomática'], 'capacidade_kg:5-15 voltagem cor=branco|azul', [350, 900], 'eletro', { conf: ['maquinas_lavar'] }),
    ]),
    c('climatizacao', 'Climatização', 'Climate control', [
      f('ar_condicionado', 'Ar-condicionado', 'Air conditioners', ['ar-condicionado', 'ar condicionado split', 'split inverter', 'ar-condicionado portátil'], 'btus voltagem=127V|220V ciclo inverter cor=branco', [1500, 5500], 'eletro', { grande: true, conf: ['climatizadores'] }),
      f('ventiladores', 'Ventiladores', 'Fans', ['ventilador', 'ventilador de coluna', 'ventilador de mesa', 'ventilador de teto', 'circulador de ar'], 'potencia_w:40-200:5 voltagem cor=preto|branco|cinza diametro_cm=30|40|50', [90, 600], 'portateis', { peso: 1.5 }),
      f('climatizadores', 'Climatizadores', 'Evaporative coolers', ['climatizador', 'climatizador de ar', 'umidificador e climatizador'], 'capacidade_l:3-45 voltagem cor=branco|preto', [250, 1500], 'portateis', { conf: ['ar_condicionado', 'purificadores_agua'] }),
      f('aquecedores', 'Aquecedores', 'Heaters', ['aquecedor elétrico', 'aquecedor a óleo', 'aquecedor halógeno'], 'potencia_w:800-2500:50 voltagem cor=branco|preto|cinza', [120, 900], 'portateis'),
    ]),
    c('lava_loucas_agua', 'Lava-louças e purificadores', 'Dishwashers and water purifiers', [
      f('lava_loucas', 'Lava-louças', 'Dishwashers', ['lava-louças', 'lava louças', 'máquina de lavar louça'], 'capacidade_servicos voltagem cor=branco|inox|preto', [1800, 6500], 'eletro', { grande: true, conf: ['detergentes'] }),
      f('purificadores_agua', 'Purificadores de água', 'Water purifiers', ['purificador de água', 'filtro de água elétrico', 'bebedouro'], 'voltagem cor=branco|preto|prata tipo_agua', [350, 1500], 'eletro', { conf: ['refis_purificador', 'comedouros_pet'] }),
    ]),
    c('pecas_eletro', 'Peças e acessórios para eletrodomésticos', 'Appliance parts and accessories', [
      f('acessorios_fogao', 'Peças para fogão', 'Stove parts', ['grelha para fogão', 'queimador para fogão', 'acendedor para fogão', 'tampa de vidro para fogão', 'mangueira de gás para fogão', 'botão para fogão'], 'compatibilidade material=ferro|aço_inox|vidro|alumínio', [20, 250], 'eletro', { tipo: 'acessorio', de: ['fogoes', 'cooktops'], conf: ['fogoes'] }),
      f('acessorios_geladeira', 'Peças para geladeira', 'Refrigerator parts', ['prateleira para geladeira', 'borracha de vedação para geladeira', 'gaveta para geladeira', 'filtro para geladeira', 'termostato para geladeira'], 'compatibilidade material=plástico|vidro|borracha', [25, 350], 'eletro', { tipo: 'acessorio', de: ['geladeiras'], conf: ['geladeiras'] }),
      f('acessorios_lavadora', 'Peças para máquina de lavar', 'Washer parts', ['mangueira para máquina de lavar', 'capa para máquina de lavar', 'pés niveladores para máquina de lavar', 'filtro para lavadora', 'bomba de drenagem para lavadora'], 'compatibilidade material=plástico|borracha|tecido', [20, 300], 'eletro', { tipo: 'acessorio', de: ['maquinas_lavar', 'lava_secas'], conf: ['maquinas_lavar'] }),
      f('acessorios_ar', 'Acessórios para ar-condicionado', 'AC accessories', ['suporte para ar-condicionado', 'controle remoto para ar-condicionado', 'kit instalação ar-condicionado', 'tubulação de cobre para ar-condicionado'], 'compatibilidade material=aço|cobre|plástico', [30, 450], 'eletro', { tipo: 'acessorio', de: ['ar_condicionado'], conf: ['ar_condicionado', 'controles_remotos'] }),
      f('refis_purificador', 'Refis para purificador', 'Purifier filters', ['refil para purificador', 'vela para filtro', 'refil filtro de água'], 'compatibilidade', [25, 180], 'eletro', { tipo: 'consumivel', de: ['purificadores_agua'], conf: ['purificadores_agua'] }),
    ]),
  ]),

  d('eletroportateis', 'Eletroportáteis', 'Small appliances', [
    c('preparo_alimentos', 'Preparo de alimentos', 'Food preparation', [
      f('air_fryers', 'Air fryers', 'Air fryers', ['air fryer', 'fritadeira elétrica sem óleo', 'fritadeira air fryer', 'airfryer'], 'capacidade_l:3-12 potencia_w:1200-2000:50 voltagem cor=preto|branco|inox|vermelho', [250, 1200], 'portateis', { peso: 2.5, conf: ['fornos_eletricos', 'livros_nao_ficcao'] }),
      f('liquidificadores', 'Liquidificadores', 'Blenders', ['liquidificador', 'liquidificador de alta potência', 'blender'], 'potencia_w:300-1400:50 velocidades capacidade_l:1-3 voltagem cor=preto|branco|vermelho|inox', [90, 700], 'portateis', { peso: 1.5, conf: ['mixers', 'processadores'] }),
      f('batedeiras', 'Batedeiras', 'Mixers', ['batedeira', 'batedeira planetária', 'batedeira de mão'], 'potencia_w:250-1000:50 velocidades voltagem cor=preto|branco|vermelho|rosa', [100, 1500], 'portateis', { conf: ['mixers'] }),
      f('processadores', 'Processadores de alimentos', 'Food processors', ['processador de alimentos', 'multiprocessador', 'mini processador'], 'potencia_w:250-1000:50 capacidade_l:1-3 voltagem cor=preto|branco', [120, 900], 'portateis', { conf: ['liquidificadores'] }),
      f('mixers', 'Mixers', 'Hand blenders', ['mixer', 'mixer de mão', 'mixer 3 em 1'], 'potencia_w:200-800:50 voltagem cor=preto|branco|inox', [70, 400], 'portateis', { conf: ['liquidificadores', 'batedeiras'] }),
      f('micro_ondas', 'Micro-ondas', 'Microwaves', ['micro-ondas', 'forno micro-ondas', 'microondas'], 'capacidade_l:20-35 potencia_w:700-1400:50 voltagem cor=branco|preto|inox|prata', [450, 1600], 'eletro', { peso: 1.5, conf: ['fornos_eletricos', 'fornos_embutir'] }),
      f('fornos_eletricos', 'Fornos elétricos', 'Countertop ovens', ['forno elétrico', 'forno elétrico de bancada', 'forno elétrico com air fryer'], 'capacidade_l:20-60 potencia_w:1200-2000:50 voltagem cor=preto|inox', [300, 1300], 'portateis', { conf: ['air_fryers', 'micro_ondas', 'fornos_embutir'] }),
      f('sanduicheiras', 'Sanduicheiras e grills', 'Sandwich makers and grills', ['sanduicheira', 'grill elétrico', 'sanduicheira e grill', 'máquina de waffle'], 'potencia_w:750-2000:50 voltagem cor=preto|inox|vermelho', [70, 450], 'portateis', { conf: ['churrasqueiras_eletricas'] }),
      f('churrasqueiras_eletricas', 'Churrasqueiras elétricas', 'Electric grills', ['churrasqueira elétrica', 'grill elétrico de mesa', 'churrasqueira de mesa'], 'potencia_w:1500-2200:50 voltagem cor=preto|inox', [120, 700], 'portateis', { conf: ['fogoes', 'sanduicheiras'] }),
      f('panelas_eletricas', 'Panelas elétricas', 'Electric cookers', ['panela elétrica de arroz', 'panela de pressão elétrica', 'multicooker', 'panela elétrica'], 'capacidade_l:1-6 potencia_w:400-1100:50 voltagem cor=preto|inox|branco', [150, 800], 'portateis', { conf: ['panelas'] }),
    ]),
    c('bebidas', 'Bebidas', 'Beverages', [
      f('cafeteiras', 'Cafeteiras', 'Coffee makers', ['cafeteira elétrica', 'cafeteira expresso', 'máquina de café', 'cafeteira de cápsulas'], 'tipo_cafeteira voltagem cor=preto|inox|vermelho|branco', [100, 3500], 'portateis', { peso: 1.5, conf: ['cafes', 'capsulas_cafe'] }),
      f('chaleiras', 'Chaleiras elétricas', 'Electric kettles', ['chaleira elétrica', 'jarra elétrica'], 'capacidade_l:1-2 voltagem cor=preto|inox|branco', [80, 350], 'portateis'),
      f('espremedores', 'Espremedores e extratores', 'Juicers', ['espremedor de frutas', 'extrator de suco', 'centrífuga de suco'], 'potencia_w:30-800:10 voltagem cor=branco|preto', [60, 600], 'portateis'),
    ]),
    c('limpeza_casa', 'Limpeza', 'Cleaning', [
      f('aspiradores', 'Aspiradores de pó', 'Vacuum cleaners', ['aspirador de pó', 'aspirador vertical', 'robô aspirador', 'aspirador portátil'], 'potencia_w:600-2000:50 tipo_aspirador voltagem cor=preto|cinza|vermelho|branco', [150, 2500], 'portateis', { peso: 1.5, conf: ['aspiradores_auto'] }),
      f('ferros_passar', 'Ferros de passar', 'Irons', ['ferro de passar', 'ferro a vapor', 'ferro de passar roupa', 'vaporizador de roupas'], 'potencia_w:1000-2400:50 voltagem cor=azul|branco|roxo|preto', [70, 450], 'portateis'),
    ]),
  ]),

  d('celulares', 'Celulares e telefonia', 'Phones', [
    c('smartphones', 'Smartphones', 'Smartphones', [
      f('smartphones', 'Smartphones', 'Smartphones', ['smartphone', 'celular', 'telefone celular'], 'armazenamento_gb memoria_ram_gb tela_pol:6-6.9:0.1 cor=preto|branco|azul|verde|rosa|prata sistema_operacional rede', [700, 9500], 'celular', { peso: 3, conf: ['capas_celular', 'celulares_basicos'] }),
      f('celulares_basicos', 'Celulares básicos', 'Feature phones', ['celular básico', 'celular para idoso', 'celular flip', 'celular com teclas grandes'], 'cor=preto|vermelho|azul', [120, 450], 'celular', { conf: ['smartphones'] }),
    ]),
    c('acessorios_celular', 'Acessórios para celular', 'Phone accessories', [
      f('capas_celular', 'Capas para celular', 'Phone cases', ['capa para celular', 'capinha', 'case', 'capa anti impacto'], 'compatibilidade cor=preto|transparente|azul|rosa|vermelho|verde material=silicone|TPU|couro|policarbonato', [15, 180], 'acess_cel', { tipo: 'acessorio', de: ['smartphones'], peso: 3, conf: ['smartphones'] }),
      f('peliculas', 'Películas', 'Screen protectors', ['película de vidro', 'película 3D', 'película de hidrogel', 'película privacidade'], 'compatibilidade', [10, 90], 'acess_cel', { tipo: 'acessorio', de: ['smartphones'], peso: 2, conf: ['smartphones', 'capas_celular'] }),
      f('carregadores', 'Carregadores', 'Chargers', ['carregador', 'carregador turbo', 'fonte carregadora', 'carregador de parede'], 'potencia_w:10-65:5 conector', [25, 300], 'acess_cel', { tipo: 'acessorio', de: ['smartphones'], peso: 1.5, conf: ['carregadores_veiculares', 'power_banks', 'cabos_usb'] }),
      f('cabos_usb', 'Cabos', 'Cables', ['cabo USB-C', 'cabo lightning', 'cabo de dados', 'cabo carregador'], 'comprimento_m=1|1.5|2|3 conector cor=preto|branco', [10, 150], 'acess_cel', { tipo: 'acessorio', de: ['smartphones'], peso: 1.5, conf: ['carregadores'] }),
      f('power_banks', 'Carregadores portáteis', 'Power banks', ['carregador portátil', 'power bank', 'bateria externa'], 'capacidade_mah=5000|10000|20000 conector cor=preto|branco', [60, 400], 'acess_cel', { conf: ['carregadores', 'baterias_auto'] }),
      f('suportes_celular', 'Suportes e tripés', 'Phone stands', ['suporte para celular', 'tripé para celular', 'ring light com tripé', 'suporte de mesa para celular'], 'cor=preto|branco', [20, 200], 'acess_cel', { tipo: 'acessorio', de: ['smartphones'], conf: ['suportes_veiculares'] }),
    ]),
    c('wearables', 'Smartwatches e fones', 'Wearables and earbuds', [
      f('smartwatches', 'Smartwatches', 'Smartwatches', ['smartwatch', 'relógio inteligente', 'smartband'], 'sistema_operacional cor=preto|prata|rosa|azul resistencia_agua', [150, 4500], 'wearable', { peso: 1.5, conf: ['relogios', 'pulseiras_smartwatch'] }),
      f('pulseiras_smartwatch', 'Pulseiras para smartwatch', 'Watch bands', ['pulseira para smartwatch', 'pulseira para relógio inteligente', 'pulseira de silicone para smartwatch'], 'compatibilidade cor=preto|branco|azul|rosa|verde material=silicone|couro|metal|nylon', [15, 120], 'acess_cel', { tipo: 'acessorio', de: ['smartwatches'], conf: ['smartwatches', 'relogios'] }),
      f('fones_bluetooth', 'Fones de ouvido', 'Earbuds and headphones', ['fone de ouvido bluetooth', 'fone sem fio', 'earbuds', 'headphone'], 'conectividade=bluetooth autonomia_h:4-60 cor=preto|branco|azul|rosa resistencia_agua', [60, 2500], 'wearable', { peso: 2, conf: ['headsets'] }),
    ]),
  ]),

  d('informatica', 'Informática', 'Computers', [
    c('computadores', 'Computadores e tablets', 'Computers and tablets', [
      f('notebooks', 'Notebooks', 'Laptops', ['notebook', 'laptop', 'notebook gamer', 'ultrabook'], 'processador memoria_ram_gb=4|8|16|32 armazenamento_gb=256|512|1024 tela_pol=14|15.6|16 cor=preto|cinza|prata', [1800, 12000], 'informatica', { peso: 2, conf: ['mochilas', 'desktops'] }),
      f('desktops', 'Computadores', 'Desktops', ['computador', 'PC gamer', 'desktop', 'computador completo'], 'processador memoria_ram_gb=8|16|32 armazenamento_gb=256|512|1024 cor=preto', [1500, 9000], 'informatica', { conf: ['notebooks', 'monitores'] }),
      f('tablets', 'Tablets', 'Tablets', ['tablet', 'tablet infantil', 'iPad'], 'armazenamento_gb=32|64|128|256 tela_pol=8|10.1|11|12.9 cor=preto|cinza|prata|azul|rosa', [450, 7500], 'informatica', { conf: ['smartphones'] }),
    ]),
    c('perifericos', 'Periféricos', 'Peripherals', [
      f('monitores', 'Monitores', 'Monitors', ['monitor', 'monitor gamer', 'monitor LED'], 'tela_pol=21.5|24|27|32|34 resolucao=Full_HD|QHD|4K taxa_hz cor=preto|branco', [550, 4500], 'informatica', { conf: ['smart_tvs'] }),
      f('mouses', 'Mouses', 'Mice', ['mouse', 'mouse sem fio', 'mouse gamer', 'mouse óptico'], 'conectividade cor=preto|branco|cinza|rosa', [25, 600], 'informatica', { peso: 1.5, conf: ['teclados', 'tapetes_yoga'] }),
      f('teclados', 'Teclados', 'Keyboards', ['teclado', 'teclado mecânico', 'teclado sem fio', 'kit teclado e mouse'], 'layout_teclado conectividade cor=preto|branco|cinza', [40, 900], 'informatica', { conf: ['mouses'] }),
      f('headsets', 'Headsets', 'Headsets', ['headset', 'headset gamer', 'fone com microfone'], 'conectividade=USB|P2|bluetooth|sem_fio cor=preto|branco|vermelho', [60, 1500], 'informatica', { conf: ['fones_bluetooth'] }),
      f('webcams', 'Webcams', 'Webcams', ['webcam', 'câmera para computador', 'webcam full hd'], 'resolucao cor=preto', [60, 700], 'informatica', { conf: ['cameras_re'] }),
      f('impressoras', 'Impressoras', 'Printers', ['impressora', 'multifuncional', 'impressora tanque de tinta', 'impressora laser'], 'tipo_impressora conectividade=USB|Wi-Fi voltagem cor=preto|branco', [450, 3500], 'informatica', { conf: ['cartuchos'] }),
    ]),
    c('armazenamento', 'Armazenamento', 'Storage', [
      f('ssds', 'SSDs', 'SSDs', ['SSD', 'SSD NVMe', 'SSD M.2', 'SSD SATA'], 'armazenamento_gb=240|480|512|1024|2048 interface_ssd', [120, 1500], 'informatica', { conf: ['hds_externos'] }),
      f('pendrives', 'Pendrives e cartões de memória', 'USB drives and memory cards', ['pendrive', 'pen drive', 'cartão de memória', 'cartão micro SD'], 'armazenamento_gb=16|32|64|128|256', [20, 300], 'informatica'),
      f('hds_externos', 'HDs externos', 'External drives', ['HD externo', 'disco rígido externo', 'SSD externo portátil'], 'armazenamento_gb=1000|2000|4000 conectividade=USB cor=preto|azul|prata', [300, 1500], 'informatica', { conf: ['ssds'] }),
    ]),
    c('redes_suprimentos', 'Redes e suprimentos', 'Networking and supplies', [
      f('roteadores', 'Roteadores', 'Routers', ['roteador', 'roteador wi-fi', 'sistema mesh', 'repetidor de sinal'], 'padrao_wifi cor=preto|branco', [90, 1500], 'informatica'),
      f('cartuchos', 'Cartuchos e toners', 'Ink and toner', ['cartucho de tinta', 'toner', 'refil de tinta', 'garrafa de tinta para impressora'], 'compatibilidade cor=preto|colorido', [30, 400], 'informatica', { tipo: 'consumivel', de: ['impressoras'], conf: ['impressoras', 'tintas_parede'] }),
    ]),
  ]),

  d('tv_audio', 'TV, áudio e games', 'TV, audio and games', [
    c('tvs', 'TVs', 'TVs', [
      f('smart_tvs', 'Smart TVs', 'Smart TVs', ['smart TV', 'TV', 'televisão', 'TV LED'], 'tela_pol=32|43|50|55|65|75 resolucao=HD|Full_HD|4K tecnologia_tela voltagem=Bivolt', [900, 12000], 'tv', { peso: 2, grande: true, conf: ['monitores', 'suportes_tv', 'controles_remotos'] }),
      f('suportes_tv', 'Suportes para TV', 'TV mounts', ['suporte para TV', 'suporte articulado para TV', 'suporte de parede para TV'], 'compatibilidade cor=preto', [40, 400], 'tv', { tipo: 'acessorio', de: ['smart_tvs'], conf: ['smart_tvs', 'acessorios_ar'] }),
      f('controles_remotos', 'Controles remotos', 'Remote controls', ['controle remoto para TV', 'controle remoto universal', 'controle remoto'], 'compatibilidade cor=preto', [15, 150], 'tv', { tipo: 'acessorio', de: ['smart_tvs'], conf: ['controles_videogame', 'carrinhos_controle', 'acessorios_ar'] }),
    ]),
    c('audio', 'Áudio', 'Audio', [
      f('soundbars', 'Soundbars', 'Soundbars', ['soundbar', 'barra de som'], 'potencia_rms_w:100-600:20 canais conectividade=bluetooth cor=preto', [400, 4500], 'tv', { conf: ['home_theaters', 'caixas_som'] }),
      f('home_theaters', 'Home theaters', 'Home theaters', ['home theater', 'sistema de home theater'], 'potencia_rms_w:300-1200:50 canais=5.1|7.1', [700, 4000], 'tv', { conf: ['soundbars'] }),
      f('caixas_som', 'Caixas de som', 'Speakers', ['caixa de som bluetooth', 'caixa de som portátil', 'caixa amplificada', 'speaker'], 'potencia_rms_w:5-500:5 resistencia_agua cor=preto|azul|vermelho|verde|cinza', [80, 3500], 'tv', { peso: 1.5, conf: ['soundbars'] }),
      f('projetores', 'Projetores', 'Projectors', ['projetor', 'projetor portátil', 'projetor 4K'], 'lumens:1500-5000:100 resolucao', [500, 6000], 'tv'),
    ]),
    c('games', 'Games', 'Games', [
      f('consoles', 'Consoles', 'Consoles', ['console', 'videogame', 'console de videogame'], 'plataforma=PS5|Xbox_Series|Nintendo_Switch armazenamento_gb=512|825|1000', [2000, 5500], 'tv', { conf: ['controles_videogame', 'jogos_videogame'] }),
      f('controles_videogame', 'Controles para videogame', 'Game controllers', ['controle para videogame', 'joystick', 'controle sem fio', 'gamepad'], 'plataforma cor=preto|branco|azul|vermelho', [90, 900], 'tv', { tipo: 'acessorio', de: ['consoles'], conf: ['controles_remotos', 'consoles'] }),
      f('jogos_videogame', 'Jogos', 'Video games', ['jogo para videogame', 'game', 'jogo mídia física'], 'plataforma', [80, 450], 'tv', { conf: ['consoles', 'jogos_tabuleiro'] }),
    ]),
  ]),

  d('moda', 'Moda', 'Fashion', [
    c('roupas', 'Roupas', 'Clothing', [
      f('camisetas', 'Camisetas', 'T-shirts', ['camiseta', 'camisa básica', 't-shirt', 'blusa', 'regata'], `tamanho_roupa genero ${COR_GERAL} material=algodão|poliéster|viscose|linho`, [30, 350], 'moda', { peso: 3 }),
      f('calcas', 'Calças', 'Pants', ['calça jeans', 'calça', 'calça de sarja', 'calça jogger'], 'tamanho_numerico genero cor=azul|preto|cinza|bege modelagem', [70, 500], 'moda', { peso: 2 }),
      f('vestidos', 'Vestidos', 'Dresses', ['vestido', 'vestido midi', 'vestido longo', 'vestido de festa'], `tamanho_roupa ${COR_GERAL} comprimento_roupa material=viscose|algodão|linho|crepe`, [70, 900], 'moda', { peso: 1.5 }),
      f('jaquetas', 'Jaquetas e moletons', 'Jackets and hoodies', ['jaqueta', 'jaqueta corta-vento', 'jaqueta jeans', 'moletom', 'casaco'], `tamanho_roupa genero ${COR_GERAL}`, [90, 900], 'moda', { peso: 1.5 }),
      f('moda_intima', 'Moda íntima', 'Underwear', ['cueca', 'calcinha', 'sutiã', 'kit cuecas', 'kit calcinhas'], 'tamanho_roupa genero cor=preto|branco|bege|colorido', [20, 200], 'moda', { conf: ['fraldas'] }),
      f('moda_praia', 'Moda praia', 'Swimwear', ['biquíni', 'maiô', 'sunga', 'bermuda de praia', 'saída de praia'], `tamanho_roupa genero ${COR_GERAL}`, [40, 400], 'moda'),
    ]),
    c('calcados', 'Calçados', 'Shoes', [
      f('tenis', 'Tênis', 'Sneakers', ['tênis', 'tênis de corrida', 'sneaker', 'tênis casual'], `tamanho_calcado genero ${COR_GERAL}`, [120, 1500], 'moda', { peso: 2.5, conf: ['chuteiras'] }),
      f('sandalias', 'Sandálias e chinelos', 'Sandals', ['sandália', 'chinelo', 'rasteirinha', 'papete'], `tamanho_calcado genero ${COR_GERAL}`, [25, 450], 'moda', { peso: 1.5 }),
      f('botas', 'Botas', 'Boots', ['bota', 'coturno', 'bota cano curto', 'bota de couro'], 'tamanho_calcado genero cor=preto|marrom|bege|caramelo', [120, 900], 'moda'),
      f('sapatos', 'Sapatos', 'Shoes', ['sapato social', 'scarpin', 'mocassim', 'sapatilha'], 'tamanho_calcado genero cor=preto|marrom|bege|nude', [90, 900], 'moda'),
    ]),
    c('acessorios_moda', 'Acessórios de moda', 'Fashion accessories', [
      f('mochilas', 'Mochilas', 'Backpacks', ['mochila', 'mochila para notebook', 'mochila de viagem', 'mochila casual'], 'capacidade_l:10-40 cor=preto|cinza|azul|verde material=poliéster|nylon|couro_sintético', [60, 600], 'moda', { conf: ['mochilas_escolares', 'notebooks'] }),
      f('bolsas', 'Bolsas', 'Handbags', ['bolsa', 'bolsa transversal', 'bolsa tote', 'bolsa de ombro'], 'cor=preto|caramelo|bege|marrom|vermelho material=couro|couro_sintético|palha|tecido', [60, 1500], 'moda', { peso: 1.5 }),
      f('relogios', 'Relógios', 'Watches', ['relógio', 'relógio de pulso', 'relógio analógico', 'relógio digital'], 'genero cor=prata|dourado|preto|marrom material=aço|couro|silicone resistencia_agua', [80, 2500], 'moda', { conf: ['smartwatches', 'pulseiras_smartwatch'] }),
      f('oculos_sol', 'Óculos de sol', 'Sunglasses', ['óculos de sol', 'óculos escuros', 'óculos aviador'], 'genero cor=preto|marrom|dourado|prata', [60, 1200], 'moda'),
      f('carteiras', 'Carteiras', 'Wallets', ['carteira', 'carteira masculina', 'porta-cartões'], 'genero cor=preto|marrom|caramelo material=couro|couro_sintético', [40, 400], 'moda'),
    ]),
  ]),

  d('casa', 'Casa e decoração', 'Home and decor', [
    c('cama_banho', 'Cama, mesa e banho', 'Bed and bath', [
      f('jogos_cama', 'Jogos de cama', 'Bed sheets', ['jogo de cama', 'lençol', 'jogo de lençol', 'lençol com elástico'], `tamanho_cama numero_fios ${COR_GERAL} material=algodão|microfibra|percal`, [60, 600], 'casa', { peso: 1.5, conf: ['camas_box', 'edredons'] }),
      f('edredons', 'Edredons e cobertores', 'Comforters and blankets', ['edredom', 'cobertor', 'manta', 'colcha'], `tamanho_cama ${COR_GERAL}`, [80, 700], 'casa', { conf: ['jogos_cama'] }),
      f('travesseiros', 'Travesseiros', 'Pillows', ['travesseiro', 'travesseiro viscoelástico', 'travesseiro de pluma'], 'material=fibra|viscoelástico|pluma|látex altura_cm:10-20', [30, 400], 'casa'),
      f('toalhas', 'Toalhas', 'Towels', ['toalha de banho', 'jogo de toalhas', 'toalha de rosto', 'toalhão'], `numero_pecas:1-6 ${COR_GERAL}`, [25, 350], 'casa'),
    ]),
    c('moveis', 'Móveis', 'Furniture', [
      f('sofas', 'Sofás', 'Sofas', ['sofá', 'sofá retrátil', 'sofá-cama', 'sofá reclinável'], 'lugares cor=cinza|bege|azul|verde|marrom material=veludo|linho|suede|couro_sintético', [900, 7000], 'casa', { grande: true }),
      f('camas_box', 'Camas box', 'Box beds', ['cama box', 'box baú', 'base box', 'cama box conjugada'], 'tamanho_cama cor=bege|cinza|preto|branco', [500, 4500], 'casa', { grande: true, conf: ['colchoes', 'camas_pet'] }),
      f('colchoes', 'Colchões', 'Mattresses', ['colchão', 'colchão de molas', 'colchão de espuma', 'colchão ortopédico'], 'tamanho_cama tecnologia_colchao altura_cm:15-35', [350, 5500], 'casa', { grande: true, conf: ['camas_box', 'tapetes_yoga'] }),
      f('cadeiras', 'Cadeiras', 'Chairs', ['cadeira', 'cadeira de escritório', 'cadeira gamer', 'cadeira de jantar'], 'cor=preto|branco|cinza|azul|vermelho material=plástico|madeira|metal|tecido', [90, 2500], 'casa', { peso: 1.5, conf: ['cadeiras_alimentacao', 'cadeirinhas_auto'] }),
      f('mesas', 'Mesas', 'Tables', ['mesa de jantar', 'mesa de escritório', 'escrivaninha', 'mesa de centro'], 'material=madeira|vidro|metal|MDF lugares', [150, 4000], 'casa', { grande: true }),
      f('guarda_roupas', 'Guarda-roupas', 'Wardrobes', ['guarda-roupa', 'roupeiro', 'guarda-roupa casal'], 'numero_portas=2|3|4|6 cor=branco|preto|bege|marrom', [500, 3500], 'casa', { grande: true }),
    ]),
    c('utilidades_cozinha', 'Utilidades de cozinha', 'Kitchenware', [
      f('panelas', 'Panelas e frigideiras', 'Pots and pans', ['panela', 'frigideira', 'caçarola', 'panela de pressão'], 'material=alumínio|inox|cerâmica|ferro_fundido antiaderente diametro_cm=16|20|24|28 cor=preto|vermelho|inox|cinza', [40, 600], 'casa', { peso: 1.5, conf: ['panelas_eletricas', 'jogos_panelas', 'cozinhas_brinquedo'] }),
      f('jogos_panelas', 'Jogos de panelas', 'Cookware sets', ['jogo de panelas', 'conjunto de panelas', 'kit panelas'], 'numero_pecas=5|7|10 material=alumínio|inox|cerâmica antiaderente cor=preto|vermelho|inox|cinza', [150, 1500], 'casa', { conf: ['panelas', 'cozinhas_brinquedo'] }),
      f('faqueiros', 'Faqueiros e talheres', 'Cutlery', ['faqueiro', 'jogo de talheres', 'kit facas', 'faca de churrasco'], 'numero_pecas=12|24|42|72|101 material=aço_inox', [40, 900], 'casa'),
      f('potes', 'Potes e organizadores', 'Food containers', ['pote hermético', 'conjunto de potes', 'marmita', 'porta mantimentos'], 'numero_pecas:1-12 material=plástico|vidro', [20, 250], 'casa'),
    ]),
    c('decoracao', 'Decoração e iluminação', 'Decor and lighting', [
      f('luminarias', 'Luminárias', 'Lamps', ['luminária', 'abajur', 'luminária de mesa', 'pendente', 'luminária de chão'], 'voltagem tipo_lampada cor=preto|branco|dourado|cobre', [40, 900], 'casa', { conf: ['lampadas_auto'] }),
      f('tapetes', 'Tapetes', 'Rugs', ['tapete', 'passadeira', 'tapete para sala', 'tapete felpudo'], `dimensao_tapete ${COR_GERAL}`, [50, 1200], 'casa', { conf: ['tapetes_auto', 'tapetes_yoga'] }),
      f('cortinas', 'Cortinas e persianas', 'Curtains', ['cortina', 'persiana', 'cortina blackout'], `${COR_GERAL} largura_cm=140|200|280|300`, [50, 600], 'casa'),
      f('quadros', 'Quadros decorativos', 'Wall art', ['quadro decorativo', 'kit quadros', 'quadro para sala'], 'numero_pecas:1-5', [40, 500], 'casa'),
      f('espelhos', 'Espelhos', 'Mirrors', ['espelho', 'espelho de parede', 'espelho de chão'], 'material=madeira|metal|MDF', [60, 900], 'casa'),
    ]),
  ]),

  d('ferramentas', 'Ferramentas e construção', 'Tools and home improvement', [
    c('ferramentas_eletricas', 'Ferramentas elétricas', 'Power tools', [
      f('furadeiras', 'Furadeiras', 'Drills', ['furadeira', 'furadeira de impacto', 'martelete'], 'potencia_w:400-1100:50 voltagem alimentacao=elétrica', [150, 1200], 'ferramentas', { peso: 1.5, conf: ['parafusadeiras'] }),
      f('parafusadeiras', 'Parafusadeiras', 'Screwdrivers', ['parafusadeira', 'parafusadeira sem fio', 'furadeira e parafusadeira'], 'bateria_v alimentacao=bateria', [180, 1500], 'ferramentas', { conf: ['furadeiras', 'ferramentas_brinquedo'] }),
      f('serras', 'Serras', 'Saws', ['serra circular', 'serra tico-tico', 'serra mármore'], 'potencia_w:500-1800:50 voltagem', [200, 1500], 'ferramentas'),
      f('esmerilhadeiras', 'Esmerilhadeiras e lixadeiras', 'Grinders and sanders', ['esmerilhadeira', 'lixadeira angular', 'lixadeira orbital'], 'potencia_w:200-2200:50 voltagem', [150, 1200], 'ferramentas'),
    ]),
    c('ferramentas_manuais', 'Ferramentas manuais', 'Hand tools', [
      f('jogos_ferramentas', 'Jogos de ferramentas', 'Tool sets', ['jogo de ferramentas', 'maleta de ferramentas', 'kit de ferramentas', 'jogo de chaves'], 'numero_pecas=40|110|129|200', [60, 1200], 'ferramentas', { conf: ['ferramentas_brinquedo'] }),
      f('trenas', 'Trenas e medidores', 'Measuring tools', ['trena', 'trena a laser', 'nível a laser', 'medidor de distância'], 'comprimento_m=3|5|8|10|40', [15, 900], 'ferramentas'),
      f('alicates', 'Alicates e chaves', 'Pliers and wrenches', ['alicate', 'alicate universal', 'chave de fenda', 'chave inglesa'], 'material=aço|aço_cromo_vanádio', [15, 200], 'ferramentas'),
    ]),
    c('jardim_limpeza', 'Jardim e limpeza pesada', 'Garden and outdoor cleaning', [
      f('lavadoras_alta_pressao', 'Lavadoras de alta pressão', 'Pressure washers', ['lavadora de alta pressão', 'lava jato', 'lavadora de pressão'], 'pressao_psi voltagem cor=amarelo|preto|azul', [300, 2500], 'ferramentas', { conf: ['maquinas_lavar'] }),
      f('cortadores_grama', 'Cortadores de grama', 'Lawn mowers', ['cortador de grama', 'aparador de grama', 'roçadeira'], 'potencia_w:500-1800:50 voltagem', [200, 2500], 'ferramentas'),
      f('mangueiras_jardim', 'Mangueiras de jardim', 'Garden hoses', ['mangueira de jardim', 'kit mangueira', 'mangueira expansível'], 'comprimento_m=10|15|20|30', [30, 300], 'ferramentas', { conf: ['acessorios_lavadora', 'acessorios_fogao'] }),
    ]),
    c('construcao', 'Construção e pintura', 'Building and paint', [
      f('escadas', 'Escadas', 'Ladders', ['escada', 'escada doméstica', 'escada de alumínio'], 'degraus material=alumínio|aço', [90, 900], 'ferramentas'),
      f('tintas_parede', 'Tintas', 'Paints', ['tinta acrílica', 'tinta para parede', 'tinta látex', 'esmalte sintético'], 'capacidade_l=0.9|3.6|18 acabamento cor=branco|cinza|bege|azul|verde', [40, 600], 'ferramentas', { conf: ['coloracoes', 'cartuchos'] }),
    ]),
  ]),

  d('automotivo', 'Automotivo', 'Automotive', [
    c('pneus_rodas', 'Pneus e rodas', 'Tires and wheels', [
      f('pneus', 'Pneus', 'Tires', ['pneu', 'pneu aro', 'pneu de carro'], 'medida_pneu aro', [250, 1200], 'auto', { peso: 1.5 }),
      f('calotas', 'Calotas', 'Hubcaps', ['calota', 'jogo de calotas', 'calota aro'], 'aro cor=prata|preto', [60, 350], 'auto'),
    ]),
    c('pecas_auto', 'Peças e manutenção', 'Parts and maintenance', [
      f('baterias_auto', 'Baterias automotivas', 'Car batteries', ['bateria automotiva', 'bateria de carro', 'bateria 60Ah'], 'amperagem_ah', [350, 900], 'auto', { conf: ['power_banks', 'parafusadeiras'] }),
      f('oleos_motor', 'Óleos de motor', 'Motor oil', ['óleo de motor', 'óleo lubrificante', 'óleo sintético'], 'viscosidade tipo_oleo capacidade_l=1|4|5', [30, 300], 'auto', { conf: ['azeites'] }),
      f('palhetas', 'Palhetas', 'Wiper blades', ['palheta', 'limpador de para-brisa', 'par de palhetas'], 'tamanho_pol', [25, 150], 'auto'),
      f('lampadas_auto', 'Lâmpadas automotivas', 'Car bulbs', ['lâmpada automotiva', 'lâmpada de farol', 'super LED farol'], 'modelo_lampada', [20, 300], 'auto', { conf: ['luminarias'] }),
    ]),
    c('acessorios_auto', 'Acessórios automotivos', 'Car accessories', [
      f('tapetes_auto', 'Tapetes automotivos', 'Car mats', ['tapete automotivo', 'tapete de carro', 'jogo de tapetes para carro'], 'compatibilidade_veiculo cor=preto|cinza material=borracha|carpete|PVC', [40, 300], 'auto', { conf: ['tapetes'] }),
      f('capas_banco', 'Capas de banco', 'Seat covers', ['capa de banco', 'capa para banco de carro', 'capa de couro para banco'], 'compatibilidade_veiculo cor=preto|cinza|bege material=couro_sintético|tecido|neoprene', [80, 600], 'auto', { conf: ['capas_celular'] }),
      f('som_auto', 'Som automotivo', 'Car audio', ['central multimídia', 'som automotivo', 'rádio para carro', 'kit multimídia'], 'tela_pol=7|9|10 conectividade=bluetooth', [200, 2500], 'auto', { conf: ['caixas_som'] }),
      f('cameras_re', 'Câmeras de ré', 'Backup cameras', ['câmera de ré', 'câmera de estacionamento', 'sensor de estacionamento'], 'compatibilidade_veiculo', [50, 400], 'auto', { conf: ['webcams'] }),
      f('suportes_veiculares', 'Suportes veiculares', 'Car phone mounts', ['suporte veicular para celular', 'suporte de celular para carro', 'suporte magnético veicular'], 'cor=preto', [20, 200], 'auto', { tipo: 'acessorio', de: ['smartphones'], conf: ['suportes_celular'] }),
      f('carregadores_veiculares', 'Carregadores veiculares', 'Car chargers', ['carregador veicular', 'carregador de carro USB', 'carregador veicular turbo'], 'conector potencia_w:12-65:3', [25, 200], 'auto', { tipo: 'acessorio', de: ['smartphones'], conf: ['carregadores'] }),
      f('aspiradores_auto', 'Aspiradores automotivos', 'Car vacuums', ['aspirador automotivo', 'aspirador de pó portátil para carro', 'aspirador veicular'], 'voltagem=12V potencia_w:60-150:10', [60, 350], 'auto', { conf: ['aspiradores'] }),
    ]),
  ]),

  d('brinquedos', 'Brinquedos', 'Toys', [
    c('bonecos', 'Bonecas e bonecos', 'Dolls and figures', [
      f('bonecas', 'Bonecas', 'Dolls', ['boneca', 'boneca bebê', 'boneca reborn', 'boneca fashion'], 'idade_recomendada cor=rosa|colorido|branco', [40, 600], 'brinquedos', { peso: 1.5 }),
      f('bonecos_acao', 'Bonecos de ação', 'Action figures', ['boneco de ação', 'action figure', 'boneco articulado', 'boneco colecionável'], 'idade_recomendada tamanho_cm:10-60:5', [40, 500], 'brinquedos'),
      f('pelucias', 'Pelúcias', 'Plush toys', ['pelúcia', 'urso de pelúcia', 'bicho de pelúcia'], 'tamanho_cm:20-100:10 cor=marrom|rosa|branco|bege|azul', [30, 350], 'brinquedos'),
    ]),
    c('veiculos_brinquedo', 'Veículos de brinquedo', 'Toy vehicles', [
      f('carrinhos_controle', 'Carrinhos de controle remoto', 'RC cars', ['carrinho de controle remoto', 'carro de controle remoto', 'carrinho RC', 'caminhão de controle remoto'], 'escala=1:10|1:16|1:24 requer_pilhas=sim idade_recomendada cor=vermelho|azul|preto|amarelo|verde', [70, 900], 'brinquedos', { conf: ['controles_remotos', 'carrinhos_bebe'] }),
      f('carrinhos_miniatura', 'Carrinhos e miniaturas', 'Die-cast cars', ['carrinho miniatura', 'miniatura de carro', 'pista com carrinhos', 'kit carrinhos'], 'escala=1:24|1:43|1:64 idade_recomendada', [15, 400], 'brinquedos', { conf: ['carrinhos_bebe'] }),
      f('carros_eletricos_infantis', 'Carros elétricos infantis', 'Ride-on cars', ['carro elétrico infantil', 'mini veículo elétrico', 'moto elétrica infantil'], 'bateria_v=6V|12V idade_recomendada cor=vermelho|preto|rosa|branco', [900, 4000], 'brinquedos', { grande: true, conf: ['carrinhos_controle'] }),
    ]),
    c('jogos_montar', 'Jogos e montar', 'Games and building', [
      f('blocos_montar', 'Blocos de montar', 'Building blocks', ['blocos de montar', 'kit de construção', 'blocos de encaixe'], 'numero_pecas:50-1500:50 idade_recomendada', [40, 1500], 'brinquedos'),
      f('quebra_cabecas', 'Quebra-cabeças', 'Puzzles', ['quebra-cabeça', 'quebra-cabeça 3D', 'puzzle'], 'numero_pecas=100|500|1000|2000 idade_recomendada', [25, 250], 'brinquedos'),
      f('jogos_tabuleiro', 'Jogos de tabuleiro', 'Board games', ['jogo de tabuleiro', 'jogo de cartas', 'jogo educativo'], 'numero_jogadores idade_recomendada', [40, 450], 'brinquedos', { conf: ['jogos_videogame'] }),
    ]),
    c('faz_de_conta', 'Faz de conta', 'Pretend play', [
      f('cozinhas_brinquedo', 'Cozinhas de brinquedo', 'Play kitchens', ['fogãozinho infantil', 'cozinha de brinquedo', 'kit cozinha infantil', 'panelinhas de brinquedo', 'mini cozinha infantil'], 'idade_recomendada requer_pilhas cor=rosa|branco|colorido|azul', [40, 900], 'brinquedos', { conf: ['fogoes', 'panelas', 'jogos_panelas'] }),
      f('ferramentas_brinquedo', 'Ferramentas de brinquedo', 'Toy tools', ['kit ferramentas de brinquedo', 'maleta de ferramentas infantil', 'bancada de ferramentas infantil'], 'idade_recomendada requer_pilhas', [40, 400], 'brinquedos', { conf: ['jogos_ferramentas', 'parafusadeiras'] }),
    ]),
    c('ar_livre_infantil', 'Brincar ao ar livre', 'Outdoor play', [
      f('bicicletas_infantis', 'Bicicletas infantis', 'Kids bikes', ['bicicleta infantil', 'bike infantil', 'bicicleta com rodinhas', 'bicicleta de equilíbrio'], 'aro_bike=12|16|20 idade_recomendada cor=rosa|azul|vermelho|verde', [250, 1200], 'esporte', { conf: ['bicicletas'] }),
      f('patinetes', 'Patinetes e skates', 'Scooters and skateboards', ['patinete', 'patinete infantil', 'skate infantil', 'patinete 3 rodas'], 'idade_recomendada cor=rosa|azul|preto|verde', [80, 600], 'brinquedos'),
      f('piscinas_infantis', 'Piscinas infantis', 'Kiddie pools', ['piscina inflável', 'piscina infantil', 'piscina de armação'], 'capacidade_l:100-3000:100', [60, 900], 'brinquedos'),
    ]),
  ]),

  d('beleza', 'Beleza e perfumaria', 'Beauty', [
    c('cabelos', 'Cabelos', 'Hair care', [
      f('shampoos', 'Shampoos', 'Shampoos', ['shampoo', 'xampu', 'shampoo antiqueda'], 'volume_ml=200|300|400|1000 tipo_cabelo', [15, 150], 'beleza', { peso: 1.5, conf: ['condicionadores'] }),
      f('condicionadores', 'Condicionadores', 'Conditioners', ['condicionador', 'creme para pentear', 'leave-in'], 'volume_ml=200|300|400|1000 tipo_cabelo', [15, 150], 'beleza', { conf: ['shampoos', 'mascaras_capilares'] }),
      f('mascaras_capilares', 'Máscaras capilares', 'Hair masks', ['máscara capilar', 'máscara de hidratação', 'máscara de reconstrução'], 'peso_g=250|300|500|1000 tipo_cabelo', [20, 200], 'beleza', { conf: ['mascaras_cilios', 'condicionadores'] }),
      f('coloracoes', 'Colorações', 'Hair color', ['coloração', 'tinta para cabelo', 'tonalizante'], 'cor=castanho|loiro|preto|ruivo|marrom', [15, 80], 'beleza', { conf: ['tintas_parede'] }),
    ]),
    c('perfumaria', 'Perfumes', 'Fragrances', [
      f('perfumes', 'Perfumes', 'Perfumes', ['perfume', 'eau de parfum', 'eau de toilette', 'deo colônia'], 'volume_ml=30|50|75|100|200 genero fragrancia', [60, 900], 'beleza', { peso: 2 }),
    ]),
    c('cuidados_pele', 'Cuidados com a pele', 'Skin care', [
      f('hidratantes', 'Hidratantes', 'Moisturizers', ['hidratante corporal', 'loção hidratante', 'creme hidratante', 'hidratante facial'], 'volume_ml=200|400|1000 tipo_pele', [15, 200], 'beleza'),
      f('protetores_solares', 'Protetores solares', 'Sunscreens', ['protetor solar', 'filtro solar', 'protetor solar facial'], 'fps volume_ml=50|120|200', [30, 150], 'beleza'),
    ]),
    c('maquiagem', 'Maquiagem', 'Makeup', [
      f('bases', 'Bases', 'Foundations', ['base líquida', 'base', 'base matte'], 'cor_tom acabamento_make volume_ml=30', [25, 300], 'beleza'),
      f('batons', 'Batons', 'Lipsticks', ['batom', 'batom líquido', 'batom matte', 'gloss'], 'cor=nude|vermelho|rosa|vinho|marrom', [15, 150], 'beleza'),
      f('mascaras_cilios', 'Máscaras de cílios', 'Mascaras', ['máscara de cílios', 'rímel', 'máscara para cílios à prova d\'água'], 'cor=preto|marrom', [20, 200], 'beleza', { conf: ['mascaras_capilares'] }),
    ]),
    c('aparelhos_beleza', 'Aparelhos de beleza', 'Beauty appliances', [
      f('secadores_cabelo', 'Secadores de cabelo', 'Hair dryers', ['secador de cabelo', 'secador profissional', 'secador'], 'potencia_w:1000-2600:100 voltagem cor=preto|rosa|branco|vermelho', [80, 900], 'beleza', { conf: ['secadoras'] }),
      f('chapinhas', 'Chapinhas e modeladores', 'Hair straighteners', ['chapinha', 'prancha alisadora', 'babyliss', 'modelador de cachos', 'escova secadora'], 'temperatura_c voltagem=Bivolt|127V|220V cor=preto|rosa|branco', [70, 900], 'beleza'),
      f('barbeadores', 'Barbeadores e aparadores', 'Shavers and trimmers', ['barbeador elétrico', 'aparador de pelos', 'máquina de cortar cabelo', 'aparador de barba'], 'voltagem alimentacao=bateria|elétrica', [60, 900], 'beleza'),
    ]),
  ]),

  d('mercado', 'Mercado', 'Grocery', [
    c('limpeza', 'Limpeza', 'Cleaning supplies', [
      f('detergentes', 'Detergentes', 'Dish soap', ['detergente', 'detergente líquido', 'lava-louças líquido'], 'volume_ml=500|5000 fragrancia_limpeza', [3, 40], 'mercado', { tipo: 'consumivel', conf: ['lava_loucas'] }),
      f('sabao_roupas', 'Sabão para roupas', 'Laundry detergent', ['sabão em pó', 'sabão líquido', 'lava-roupas líquido', 'sabão em barra'], 'peso_g=800|1600|2200|4000 fragrancia_limpeza', [10, 90], 'mercado', { tipo: 'consumivel', conf: ['maquinas_lavar'] }),
      f('amaciantes', 'Amaciantes', 'Fabric softeners', ['amaciante', 'amaciante concentrado'], 'volume_ml=500|2000|5000 fragrancia_limpeza', [8, 60], 'mercado', { tipo: 'consumivel' }),
      f('desinfetantes', 'Desinfetantes e multiuso', 'Disinfectants', ['desinfetante', 'limpador multiuso', 'água sanitária', 'limpa vidros'], 'volume_ml=500|1000|2000|5000 fragrancia_limpeza', [5, 50], 'mercado', { tipo: 'consumivel' }),
      f('papeis_higienicos', 'Papel higiênico', 'Toilet paper', ['papel higiênico', 'papel higiênico folha dupla'], 'quantidade_unidades=4|12|16|24|30 folhas_papel', [8, 70], 'mercado', { tipo: 'consumivel' }),
    ]),
    c('alimentos', 'Alimentos', 'Food', [
      f('cafes', 'Cafés', 'Coffee', ['café em pó', 'café em grãos', 'café torrado e moído', 'café gourmet'], 'peso_g=250|500|1000 tipo_cafe', [12, 120], 'mercado', { tipo: 'consumivel', conf: ['cafeteiras', 'capsulas_cafe'] }),
      f('capsulas_cafe', 'Cápsulas de café', 'Coffee capsules', ['cápsulas de café', 'cápsula compatível', 'cápsulas para cafeteira'], 'quantidade_unidades=10|16|30|50 compat_capsula', [20, 150], 'mercado', { tipo: 'consumivel', de: ['cafeteiras'], conf: ['cafeteiras', 'cafes'] }),
      f('arroz', 'Arroz', 'Rice', ['arroz', 'arroz branco', 'arroz integral', 'arroz parboilizado'], 'peso_kg=1|2|5 tipo_arroz', [6, 40], 'mercado', { tipo: 'consumivel', conf: ['panelas_eletricas'] }),
      f('azeites', 'Azeites', 'Olive oil', ['azeite de oliva', 'azeite extravirgem', 'azeite'], 'volume_ml=250|500|1000', [20, 120], 'mercado', { tipo: 'consumivel', conf: ['oleos_motor'] }),
      f('chocolates', 'Chocolates', 'Chocolate', ['chocolate', 'barra de chocolate', 'caixa de bombons', 'ovo de páscoa'], 'peso_g=90|200|250|1000', [5, 150], 'mercado', { tipo: 'consumivel', conf: ['suplementos'] }),
    ]),
  ]),

  d('esporte', 'Esporte e lazer', 'Sports', [
    c('ciclismo', 'Ciclismo', 'Cycling', [
      f('bicicletas', 'Bicicletas', 'Bikes', ['bicicleta', 'bike', 'mountain bike', 'bicicleta aro 29'], 'aro_bike marchas material_quadro cor=preto|branco|azul|vermelho|cinza', [700, 6000], 'esporte', { peso: 1.5, grande: true, conf: ['bicicletas_infantis'] }),
      f('capacetes_ciclismo', 'Capacetes de ciclismo', 'Bike helmets', ['capacete de ciclismo', 'capacete bike', 'capacete para bicicleta'], 'tamanho_pet=P|M|G cor=preto|branco|azul|vermelho', [60, 500], 'esporte'),
    ]),
    c('fitness', 'Fitness', 'Fitness', [
      f('esteiras', 'Esteiras', 'Treadmills', ['esteira ergométrica', 'esteira elétrica', 'esteira dobrável'], 'velocidade_max_kmh voltagem', [1500, 7000], 'esporte', { grande: true }),
      f('halteres', 'Halteres e anilhas', 'Dumbbells', ['halter', 'kit halteres', 'anilha', 'halteres emborrachados'], 'peso_kg=1|2|3|5|10|20', [30, 600], 'esporte'),
      f('tapetes_yoga', 'Tapetes de yoga', 'Yoga mats', ['tapete de yoga', 'colchonete', 'tapete de ioga', 'tapete para exercícios'], 'espessura_mm cor=preto|roxo|azul|rosa|verde', [40, 300], 'esporte', { conf: ['tapetes', 'colchoes'] }),
      f('suplementos', 'Suplementos', 'Supplements', ['whey protein', 'creatina', 'suplemento', 'pré-treino'], 'peso_g=300|900|1000|2000 sabor', [50, 350], 'esporte', { tipo: 'consumivel', conf: ['chocolates'] }),
    ]),
    c('esportes_coletivos', 'Futebol e esportes coletivos', 'Team sports', [
      f('bolas', 'Bolas', 'Balls', ['bola de futebol', 'bola de vôlei', 'bola de basquete', 'bola society'], 'tamanho_bola material=PU|PVC|borracha', [40, 400], 'esporte'),
      f('chuteiras', 'Chuteiras', 'Football boots', ['chuteira', 'chuteira society', 'chuteira de campo', 'chuteira futsal'], 'tamanho_calcado cor=preto|branco|azul|vermelho|amarelo', [120, 1200], 'esporte', { conf: ['tenis'] }),
    ]),
    c('camping', 'Camping e aventura', 'Camping', [
      f('barracas', 'Barracas', 'Tents', ['barraca de camping', 'barraca', 'barraca iglu'], 'capacidade_pessoas=2|3|4|6', [120, 1500], 'esporte'),
      f('garrafas_termicas', 'Garrafas e copos térmicos', 'Thermal bottles', ['garrafa térmica', 'copo térmico', 'squeeze', 'caneca térmica'], 'capacidade_ml=350|500|750|1000|1200 cor=preto|branco|verde|azul|rosa|inox', [30, 400], 'esporte', { peso: 1.5, conf: ['coolers'] }),
      f('coolers', 'Coolers e caixas térmicas', 'Coolers', ['cooler', 'caixa térmica', 'bolsa térmica'], 'capacidade_l:12-60 cor=azul|vermelho|branco|preto', [50, 900], 'esporte', { conf: ['frigobares', 'freezers', 'garrafas_termicas'] }),
    ]),
  ]),

  d('pet', 'Pet shop', 'Pet supplies', [
    c('alimentacao_pet', 'Alimentação pet', 'Pet food', [
      f('racoes_caes', 'Rações para cães', 'Dog food', ['ração para cães', 'ração para cachorro', 'ração cães adultos'], 'peso_kg=1|3|10.1|15|20 porte fase_vida sabor_pet', [30, 400], 'pet', { tipo: 'consumivel', peso: 1.5, conf: ['racoes_gatos', 'petiscos'] }),
      f('racoes_gatos', 'Rações para gatos', 'Cat food', ['ração para gatos', 'ração gato', 'ração gatos castrados'], 'peso_kg=1|3|10.1 fase_vida sabor_pet', [25, 350], 'pet', { tipo: 'consumivel', conf: ['racoes_caes', 'petiscos'] }),
      f('petiscos', 'Petiscos', 'Treats', ['petisco para cães', 'bifinho', 'sachê para gatos', 'osso para cachorro'], 'especie peso_g=60|85|250|500', [5, 80], 'pet', { tipo: 'consumivel', conf: ['brinquedos_pet'] }),
    ]),
    c('acessorios_pet', 'Acessórios pet', 'Pet accessories', [
      f('coleiras', 'Coleiras e guias', 'Collars and leashes', ['coleira', 'guia para cachorro', 'peitoral', 'coleira antipulgas'], 'tamanho_pet especie cor=preto|azul|vermelho|rosa', [15, 200], 'pet'),
      f('camas_pet', 'Camas pet', 'Pet beds', ['cama para cachorro', 'caminha pet', 'cama para gato', 'almofada pet'], 'tamanho_pet especie cor=cinza|bege|azul|rosa|marrom', [40, 400], 'pet', { conf: ['camas_box'] }),
      f('arranhadores', 'Arranhadores', 'Cat scratchers', ['arranhador para gatos', 'arranhador', 'torre para gatos'], 'cor=bege|cinza|marrom', [40, 600], 'pet'),
      f('areias_gato', 'Areias sanitárias', 'Cat litter', ['areia sanitária', 'areia para gatos', 'granulado sanitário'], 'peso_kg=2|4|12 tipo_areia', [15, 120], 'pet', { tipo: 'consumivel' }),
      f('brinquedos_pet', 'Brinquedos para pets', 'Pet toys', ['brinquedo para cachorro', 'mordedor', 'bolinha para cães', 'varinha para gatos'], 'especie', [10, 120], 'pet', { conf: ['pelucias', 'bolas'] }),
      f('comedouros_pet', 'Comedouros e bebedouros', 'Bowls and fountains', ['comedouro', 'bebedouro pet', 'fonte para gatos', 'comedouro automático'], 'especie material=plástico|inox|cerâmica', [15, 450], 'pet', { conf: ['purificadores_agua'] }),
    ]),
  ]),

  d('livros_papelaria', 'Livros e papelaria', 'Books and stationery', [
    c('livros', 'Livros', 'Books', [
      f('livros_ficcao', 'Livros de ficção', 'Fiction', ['livro', 'romance', 'livro de ficção', 'livro de suspense'], 'formato_livro idioma numero_paginas:120-800:10', [25, 150], 'livros', { peso: 1.5 }),
      f('livros_nao_ficcao', 'Livros de não ficção', 'Non-fiction', ['livro de autoajuda', 'livro de negócios', 'biografia', 'livro de receitas'], 'formato_livro idioma numero_paginas:120-600:10', [25, 180], 'livros', { conf: ['air_fryers', 'livros_ficcao'] }),
      f('livros_infantis', 'Livros infantis', 'Children\'s books', ['livro infantil', 'livro de colorir', 'livro ilustrado', 'livro de atividades'], 'idade_recomendada formato_livro', [15, 90], 'livros', { conf: ['livros_ficcao'] }),
    ]),
    c('papelaria', 'Papelaria', 'Stationery', [
      f('cadernos', 'Cadernos', 'Notebooks (paper)', ['caderno', 'caderno universitário', 'caderno inteligente', 'agenda'], 'materias folhas_caderno', [10, 150], 'livros', { conf: ['notebooks'] }),
      f('canetas', 'Canetas e marcadores', 'Pens and markers', ['caneta', 'kit canetas', 'caneta esferográfica', 'marca-texto', 'lápis de cor'], 'quantidade_unidades=1|4|12|24|36 cor=azul|preto|vermelho|colorido', [3, 120], 'livros'),
      f('mochilas_escolares', 'Mochilas escolares', 'School backpacks', ['mochila escolar', 'mochila infantil de rodinhas', 'kit mochila escolar com lancheira'], 'cor=rosa|azul|preto|roxo idade_recomendada', [60, 450], 'livros', { conf: ['mochilas'] }),
      f('estojos', 'Estojos', 'Pencil cases', ['estojo escolar', 'estojo', 'estojo de silicone'], 'cor=rosa|azul|preto|roxo|colorido', [10, 90], 'livros'),
    ]),
  ]),

  d('bebes', 'Bebês', 'Baby', [
    c('passeio', 'Passeio', 'Strollers and car seats', [
      f('carrinhos_bebe', 'Carrinhos de bebê', 'Strollers', ['carrinho de bebê', 'carrinho travel system', 'carrinho de passeio'], 'peso_suportado_kg=15|22 cor=preto|cinza|azul|rosa|bege', [350, 4000], 'bebe', { grande: true, conf: ['carrinhos_controle', 'carrinhos_miniatura'] }),
      f('cadeirinhas_auto', 'Cadeirinhas para carro', 'Car seats', ['cadeirinha para carro', 'bebê conforto', 'assento de elevação', 'cadeirinha automotiva'], 'grupo_cadeirinha peso_suportado_kg cor=preto|cinza|azul|rosa', [200, 3000], 'bebe', { conf: ['capas_banco', 'cadeiras'] }),
    ]),
    c('alimentacao_bebe', 'Alimentação do bebê', 'Feeding', [
      f('mamadeiras', 'Mamadeiras', 'Baby bottles', ['mamadeira', 'kit mamadeiras', 'mamadeira anticólica'], 'capacidade_ml=150|240|300 cor=transparente|azul|rosa', [25, 200], 'bebe', { conf: ['garrafas_termicas'] }),
      f('cadeiras_alimentacao', 'Cadeiras de alimentação', 'High chairs', ['cadeira de alimentação', 'cadeirão', 'cadeira de refeição infantil'], 'cor=cinza|branco|azul|rosa', [150, 1500], 'bebe', { conf: ['cadeiras'] }),
    ]),
    c('higiene_bebe', 'Higiene do bebê', 'Baby care', [
      f('fraldas', 'Fraldas', 'Diapers', ['fralda descartável', 'fralda', 'fralda calça', 'fralda noturna'], 'tamanho_fralda quantidade_unidades=20|40|60|80', [30, 160], 'bebe', { tipo: 'consumivel', peso: 1.5, conf: ['moda_intima'] }),
      f('lencos_umedecidos', 'Lenços umedecidos', 'Baby wipes', ['lenço umedecido', 'lenço umedecido sem perfume'], 'quantidade_unidades=48|100|200', [6, 50], 'bebe', { tipo: 'consumivel' }),
      f('banheiras_bebe', 'Banheiras', 'Baby baths', ['banheira para bebê', 'banheira com suporte', 'banheira dobrável'], 'cor=azul|rosa|branco|cinza', [70, 600], 'bebe'),
    ]),
    c('quarto_bebe', 'Quarto do bebê', 'Nursery', [
      f('bercos', 'Berços', 'Cribs', ['berço', 'mini berço', 'berço portátil', 'berço desmontável'], 'cor=branco|cinza|madeira material=MDF|madeira|tecido', [300, 3000], 'bebe', { grande: true }),
      f('babas_eletronicas', 'Babás eletrônicas', 'Baby monitors', ['babá eletrônica', 'babá eletrônica com câmera', 'monitor de bebê'], 'video', [120, 1500], 'bebe', { conf: ['cameras_re', 'webcams'] }),
    ]),
  ]),
]

// Produtos fora da taxonomia (aparecem só entre os itens novos).
export const FORA_TAXONOMIA = [
  { id: 'drones', nome: 'Drones', termos: ['drone', 'drone com câmera', 'quadricóptero'], parecido: 'carrinhos_controle', dep: 'tv_audio', marcas: ['DJI', 'Xiaomi', 'Holy Stone'] },
  { id: 'impressoras_3d', nome: 'Impressoras 3D', termos: ['impressora 3D', 'impressora 3D FDM'], parecido: 'impressoras', dep: 'informatica', marcas: ['Creality', 'Anycubic', 'Bambu Lab'] },
  { id: 'paineis_solares', nome: 'Painéis solares', termos: ['painel solar', 'placa solar fotovoltaica', 'kit energia solar'], parecido: 'baterias_auto', dep: 'ferramentas', marcas: ['Canadian Solar', 'Resun', 'Intelbras'] },
  { id: 'oculos_vr', nome: 'Óculos de realidade virtual', termos: ['óculos VR', 'óculos de realidade virtual', 'headset VR'], parecido: 'oculos_sol', dep: 'tv_audio', marcas: ['Meta', 'Pico', 'Sony'] },
  { id: 'purificadores_ar', nome: 'Purificadores de ar', termos: ['purificador de ar', 'purificador de ar com filtro HEPA'], parecido: 'purificadores_agua', dep: 'eletrodomesticos', marcas: ['Philips', 'Xiaomi', 'Electrolux'] },
  { id: 'patinetes_eletricos', nome: 'Patinetes elétricos', termos: ['patinete elétrico', 'scooter elétrica adulto'], parecido: 'patinetes', dep: 'esporte', marcas: ['Xiaomi', 'Segway', 'Atrio'] },
  { id: 'fechaduras_digitais', nome: 'Fechaduras digitais', termos: ['fechadura digital', 'fechadura eletrônica com biometria'], parecido: 'alicates', dep: 'ferramentas', marcas: ['Intelbras', 'Yale', 'Papaiz'] },
  { id: 'cameras_seguranca', nome: 'Câmeras de segurança', termos: ['câmera de segurança wi-fi', 'câmera IP', 'câmera de monitoramento 360'], parecido: 'webcams', dep: 'informatica', marcas: ['Intelbras', 'TP-Link', 'Positivo'] },
]

// Termos genéricos de categoria do vendedor (não informam nada).
export const CATEGORIAS_GENERICAS = ['Diversos', 'Outros', 'Promoções', 'Ofertas', 'Novidades', 'Geral', 'Sem categoria', 'Black Friday', 'Mais vendidos', 'Lançamentos', 'Utilidades', 'Variedades']

// Caminhos em inglês que alguns vendedores (importadores) usam.
export const CATEGORIAS_INGLES = { eletrodomesticos: 'Home & Kitchen > Appliances', eletroportateis: 'Home & Kitchen > Small Appliances', celulares: 'Electronics > Cell Phones', informatica: 'Electronics > Computers', tv_audio: 'Electronics > TV & Audio', moda: 'Clothing, Shoes & Jewelry', casa: 'Home & Kitchen > Furniture', ferramentas: 'Tools & Home Improvement', automotivo: 'Automotive', brinquedos: 'Toys & Games', beleza: 'Beauty & Personal Care', mercado: 'Grocery', esporte: 'Sports & Outdoors', pet: 'Pet Supplies', livros_papelaria: 'Books', bebes: 'Baby' }

// Buscas montadas à mão: as armadilhas clássicas. A relevância é calculada pelo gabarito.
export const BUSCAS_ARMADILHA = [
  { q: 'fogão', folhas: ['fogoes'], armadilhas: ['cozinhas_brinquedo', 'acessorios_fogao', 'churrasqueiras_eletricas', 'cooktops'] },
  { q: 'fogão 5 bocas inox', folhas: ['fogoes'], filtros: { numero_bocas: '5', cor: 'inox' }, armadilhas: ['cozinhas_brinquedo', 'acessorios_fogao'] },
  { q: 'carro', folhas: ['carrinhos_controle', 'carrinhos_miniatura', 'carros_eletricos_infantis'], armadilhas: ['carrinhos_bebe', 'tapetes_auto', 'capas_banco'], ambigua: true },
  { q: 'carrinho', folhas: ['carrinhos_bebe', 'carrinhos_controle', 'carrinhos_miniatura'], ambigua: true },
  { q: 'controle', folhas: ['controles_remotos', 'controles_videogame'], armadilhas: ['carrinhos_controle', 'acessorios_ar'], ambigua: true },
  { q: 'controle ps5', folhas: ['controles_videogame'], filtros: { plataforma: 'PS5' }, armadilhas: ['consoles', 'controles_remotos'] },
  { q: 'capa', folhas: ['capas_celular'], armadilhas: ['capas_banco', 'acessorios_lavadora'], ambigua: true },
  { q: 'capinha iphone 15', folhas: ['capas_celular'], filtros: { compatibilidade: 'iPhone 15' }, armadilhas: ['smartphones', 'peliculas'] },
  { q: 'iphone', folhas: ['smartphones'], marca: 'Apple', armadilhas: ['capas_celular', 'peliculas', 'carregadores', 'cabos_usb'] },
  { q: 'tapete', folhas: ['tapetes'], armadilhas: ['tapetes_auto', 'tapetes_yoga'], ambigua: true },
  { q: 'bateria', folhas: ['baterias_auto', 'power_banks'], armadilhas: ['parafusadeiras'], ambigua: true },
  { q: 'tinta', folhas: ['tintas_parede'], armadilhas: ['coloracoes', 'cartuchos'], ambigua: true },
  { q: 'mochila', folhas: ['mochilas', 'mochilas_escolares'], ambigua: true },
  { q: 'cadeira', folhas: ['cadeiras'], armadilhas: ['cadeiras_alimentacao', 'cadeirinhas_auto'], ambigua: true },
  { q: 'cama', folhas: ['camas_box'], armadilhas: ['camas_pet', 'jogos_cama'], ambigua: true },
  { q: 'panela', folhas: ['panelas', 'jogos_panelas'], armadilhas: ['panelas_eletricas', 'cozinhas_brinquedo'] },
  { q: 'panela elétrica de arroz', folhas: ['panelas_eletricas'], armadilhas: ['arroz', 'panelas'] },
  { q: 'lava louças', folhas: ['lava_loucas'], armadilhas: ['detergentes'] },
  { q: 'máquina de lavar 12kg', folhas: ['maquinas_lavar'], filtros: { capacidade_kg: '12' }, armadilhas: ['lavadoras_alta_pressao', 'acessorios_lavadora', 'sabao_roupas'] },
  { q: 'secador', folhas: ['secadores_cabelo'], armadilhas: ['secadoras'], ambigua: true },
  { q: 'máscara', folhas: ['mascaras_capilares', 'mascaras_cilios'], ambigua: true },
  { q: 'relógio', folhas: ['relogios'], armadilhas: ['smartwatches', 'pulseiras_smartwatch'] },
  { q: 'relógio inteligente', folhas: ['smartwatches'], armadilhas: ['relogios', 'pulseiras_smartwatch'] },
  { q: 'air fryer', folhas: ['air_fryers'], armadilhas: ['livros_nao_ficcao', 'fornos_eletricos'] },
  { q: 'fritadeira sem óleo', folhas: ['air_fryers'], armadilhas: ['livros_nao_ficcao'] },
  { q: 'café', folhas: ['cafes'], armadilhas: ['cafeteiras', 'capsulas_cafe'], ambigua: true },
  { q: 'cafeteira expresso', folhas: ['cafeteiras'], filtros: { tipo_cafeteira: 'expresso' }, armadilhas: ['capsulas_cafe', 'cafes'] },
  { q: 'chocolate', folhas: ['chocolates'], armadilhas: ['suplementos'] },
  { q: 'whey chocolate', folhas: ['suplementos'], filtros: { sabor: 'chocolate' }, armadilhas: ['chocolates'] },
  { q: 'óleo', folhas: ['oleos_motor', 'azeites'], ambigua: true },
  { q: 'carregador', folhas: ['carregadores'], armadilhas: ['carregadores_veiculares', 'power_banks', 'cabos_usb'] },
  { q: 'suporte celular carro', folhas: ['suportes_veiculares'], armadilhas: ['suportes_celular'] },
  { q: 'aspirador', folhas: ['aspiradores'], armadilhas: ['aspiradores_auto'] },
  { q: 'geladeira inox frost free', folhas: ['geladeiras'], filtros: { cor: 'inox', frost_free: 'sim' }, armadilhas: ['acessorios_geladeira', 'frigobares'] },
  { q: 'geladera', folhas: ['geladeiras'], erro: true },
  { q: 'notbook', folhas: ['notebooks'], erro: true, armadilhas: ['cadernos', 'mochilas'] },
  { q: 'notebook', folhas: ['notebooks'], armadilhas: ['cadernos', 'mochilas'] },
  { q: 'tv 55 4k', folhas: ['smart_tvs'], filtros: { tela_pol: '55', resolucao: '4K' }, armadilhas: ['suportes_tv', 'controles_remotos', 'monitores'] },
  { q: 'ventilador 220v', folhas: ['ventiladores'], filtros: { voltagem: '220V' } },
  { q: 'bicicleta aro 29', folhas: ['bicicletas'], filtros: { aro_bike: '29' }, armadilhas: ['bicicletas_infantis'] },
  { q: 'tênis nike masculino 42', folhas: ['tenis'], marca: 'Nike', filtros: { genero: 'masculino', tamanho_calcado: '42' }, armadilhas: ['chuteiras'] },
  { q: 'ração golden 15kg', folhas: ['racoes_caes'], marca: 'Golden', filtros: { peso_kg: '15' } },
  { q: 'fralda pampers g', folhas: ['fraldas'], marca: 'Pampers', filtros: { tamanho_fralda: 'G' } },
  { q: 'jogo de ferramentas', folhas: ['jogos_ferramentas'], armadilhas: ['ferramentas_brinquedo'] },
  { q: 'liquidificador 1200w', folhas: ['liquidificadores'], filtros: { potencia_w: '1200' } },
  { q: 'perfume feminino', folhas: ['perfumes'], filtros: { genero: 'feminino' } },
  { q: 'pneu aro 15', folhas: ['pneus'], filtros: { aro: '15' }, armadilhas: ['calotas'] },
  { q: 'blender', folhas: ['liquidificadores'], ingles: true },
  { q: 'washing machine', folhas: ['maquinas_lavar'], ingles: true },
  { q: 'refrigerador', folhas: ['geladeiras'], sinonimo: true },
]
