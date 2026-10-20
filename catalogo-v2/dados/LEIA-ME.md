# Dataset original do catálogo v2 (semente 20261008, ruído 1)

Dados **sintéticos** usados nos testes das rodadas 6 e 7. `produtos.jsonl` e `novos.jsonl` estão **compactados** (`.gz`) porque o GitHub não aceita arquivo acima de 100 MB.

## Descompactar
```bash
gunzip -k produtos.jsonl.gz novos.jsonl.gz          # Linux, macOS, Git Bash
```
PowerShell:
```powershell
foreach ($f in 'produtos','novos') { $i=[IO.File]::OpenRead("$f.jsonl.gz"); $g=New-Object IO.Compression.GzipStream($i,[IO.Compression.CompressionMode]::Decompress); $o=[IO.File]::Create("$f.jsonl"); $g.CopyTo($o); $o.Close(); $g.Close(); $i.Close() }
```
Os arquivos descompactados estão no `.gitignore` (não os versione).

## Conferir (sha256 dos arquivos DESCOMPACTADOS, os mesmos da rodada 6)
| Arquivo | sha256 |
|---|---|
| `produtos.jsonl` | `1580a317feb3e71a7afc9a698bc300581f945de7376085b28a901df4d279821d` |
| `splits.json` | `36b75a580152530ecd4a1c5dc1ec638c306a4179c7db9b7571e4196799dec8e7` |
| `taxonomia.json` | `46fff591b4b102602c32074c5cfc4c399bf744b9be5701744310569b1d49ceb0` |
| `novos.jsonl` | `c1bf5c844881f35c8dc8e92263b18e290430fb723d82fa6641fcb56e9822ef06` |
| `buscas.jsonl` | `67c3c14f7214900a2a2cbda1a08a245e95fea700fbede963b29779d6195c7f6b` |

## O que é cada arquivo
- `produtos.jsonl`: 100.000 produtos como o vendedor os cadastrou, **mais o campo `gabarito`** (a resposta certa; o agente nunca o lê, só o avaliador e a rotulagem de treino e validação).
- `novos.jsonl`: 10.000 itens novos (com gabarito). `buscas.jsonl`: 900 consultas com a intenção correta.
- `taxonomia.json`: 16 departamentos, 57 categorias, 205 subcategorias. `splits.json`: golden, teste_rodadas, validacao, treino_200/2000/5000/20000 e novos_teste.
- `estatisticas.json`: contagens e ruídos. `amostra.csv`: 300 produtos para ler no Excel.

Também dá para **regenerar** tudo: `node gerador/gerar-catalogo-v2.mjs` (Node 18+), que reproduz estes mesmos arquivos.
