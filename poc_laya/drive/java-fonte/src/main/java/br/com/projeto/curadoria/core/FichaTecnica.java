package br.com.projeto.curadoria.core;

import br.com.projeto.curadoria.core.Modelos.ProdutoEntrada;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A ficha tecnica (atributos) de um produto, guiada pelo esquema da taxonomia v2: os atributos de cada subcategoria vem de
 * {@link Taxonomia#atributosDe}. Valores aceitos: um valor canonico da taxonomia, {@link #NAO_INFORMADO} (o anuncio nao traz o valor)
 * ou {@link #NAO_SE_APLICA} (o atributo nao existe para a subcategoria). Inventar um valor e pior do que dizer nao informado.
 */
public interface FichaTecnica {
    String NAO_INFORMADO = "nao_informado", NAO_SE_APLICA = "nao_se_aplica";

    /** Um valor por atributo da subcategoria. */
    Map<String, String> extrair(ProdutoEntrada produto, Preparador.Preparado preparado, String subcategoria);

    String nome();

    /** Linha de base: todo atributo da subcategoria como nao informado (acerto 0, inventados 0). */
    static FichaTecnica naoInformado(Taxonomia tx) {
        return new FichaTecnica() {
            public Map<String, String> extrair(ProdutoEntrada p, Preparador.Preparado prep, String subcategoria) {
                Map<String, String> out = new LinkedHashMap<>();
                for (Taxonomia.AtributoDef a : tx.atributosDe(subcategoria)) out.put(a.chave(), NAO_INFORMADO);
                return out;
            }
            public String nome() { return "nenhuma"; }
        };
    }
}
