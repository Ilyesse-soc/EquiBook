package fr.equibook.core;

import org.bson.Document;
import java.util.*;
import java.util.function.Supplier;

/** Double de stockage uniquement pour les tests unitaires ; le scénario HTTP utilise MongoDB et Redis réels. */
public class MemoryStore implements Store {
    private Map<String, Map<String, Document>> data = new HashMap<>();
    private Document copy(Document d) { return d == null ? null : Document.parse(d.toJson()); }
    public Document get(String c, String id) { return copy(data.getOrDefault(c, Map.of()).get(id)); }
    public List<Document> all(String c) { return data.getOrDefault(c, Map.of()).values().stream().map(this::copy).toList(); }
    public void save(String c, Document d) { data.computeIfAbsent(c, k -> new HashMap<>()).put(d.getString("_id"), copy(d)); }
    public void delete(String c, String id) { data.getOrDefault(c, new HashMap<>()).remove(id); }
    public synchronized <T> T atomic(Supplier<T> work) {
        Map<String, Map<String, Document>> before = new HashMap<>();
        data.forEach((c, docs) -> { var map = new HashMap<String, Document>(); docs.forEach((id, d) -> map.put(id, copy(d))); before.put(c, map); });
        try { return work.get(); } catch (RuntimeException e) { data = before; throw e; }
    }
}
