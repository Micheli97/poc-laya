package br.com.projeto.curadoria.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser/escritor JSON minimo usado SO nas chamadas ao modelo (laya-serve / Gateway),
 * para o nucleo do agente nao depender de framework. A API REST do servico usa o Jackson do Spring.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String s) { Json j = new Json(s); j.ws(); return j.valor(); }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapa(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }

    /** Serializa mapas, listas, textos, numeros, booleanos e nulo (o que o parse devolve). */
    public static String escrever(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return str(s);
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            for (var e : m.entrySet()) { if (sb.length() > 1) sb.append(','); sb.append(str(String.valueOf(e.getKey()))).append(':').append(escrever(e.getValue())); }
            return sb.append('}').toString();
        }
        if (o instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            for (Object x : it) { if (sb.length() > 1) sb.append(','); sb.append(escrever(x)); }
            return sb.append(']').toString();
        }
        if (o instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf(d.longValue());
        return String.valueOf(o);
    }

    public static String str(String v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object valor() {
        ws();
        char c = s.charAt(i);
        if (c == '{') {
            i++; Map<String, Object> m = new LinkedHashMap<>(); ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) { ws(); String k = texto(); ws(); i++; m.put(k, valor()); ws(); if (s.charAt(i++) == '}') return m; }
        }
        if (c == '[') {
            i++; List<Object> l = new ArrayList<>(); ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) { l.add(valor()); ws(); if (s.charAt(i++) == ']') return l; }
        }
        if (c == '"') return texto();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int ini = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.parseDouble(s.substring(ini, i));
    }

    private String texto() {
        StringBuilder sb = new StringBuilder(); i++;
        while (s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n'); case 't' -> sb.append('\t'); case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b'); case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e);
                }
            } else sb.append(c);
        }
        i++;
        return sb.toString();
    }
}
