package fr.equibook.core;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class Rules {
    private Rules() { }
    public static final List<String> LEVELS = List.of("BEGINNER", "GALOP_1", "GALOP_2", "GALOP_3", "GALOP_4", "GALOP_5", "GALOP_6", "GALOP_7");
    public static void require(boolean condition, String message) { if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public static String text(Document p, String key) {
        Object value = p.get(key);
        if (!(value instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " doit être une chaîne non vide");
        return (String) value;
    }
    public static String choice(Document p, String key, List<String> choices) {
        String value = text(p, key);
        if (!choices.contains(value)) throw new IllegalArgumentException(key + " invalide");
        return value;
    }
    public static int integer(Document p, String key) {
        Object value = p.get(key);
        if (!(value instanceof Number n) || n.doubleValue() != n.intValue()) throw new IllegalArgumentException(key + " doit être entier");
        return ((Number) value).intValue();
    }
    public static BigDecimal money(Document p, String key) {
        Object raw = p.get(key);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(key + " doit être numérique");
        BigDecimal result = new BigDecimal(raw.toString());
        if (result.signum() <= 0 || result.compareTo(new BigDecimal("1000000")) > 0 || result.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException(key + " doit être positif, au plus 1000000, avec au plus deux décimales");
        return result;
    }
    public static Instant instant(String value) {
        try { return Instant.parse(value); }
        catch (Exception e) { throw new IllegalArgumentException("Date ISO-8601 UTC invalide"); }
    }
    public static void fields(Document p, String... names) {
        if (!p.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Champs attendus : " + String.join(", ", names));
    }
    public static Document visible(Document source) {
        Document result = new Document(source);
        result.keySet().removeIf(k -> k.startsWith("_"));
        return result;
    }
}
