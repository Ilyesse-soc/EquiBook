package fr.equibook.core;

import org.bson.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static fr.equibook.core.Rules.*;

public abstract class Domain {
    protected final Store store;
    protected Domain(Store store) { this.store = store; }
    public abstract String service();
    protected abstract Document handle(String type, String id, Document payload);
    protected abstract void onEvent(Document event);
    public abstract String collection();
    public Document get(String id) { return visible(needed(collection(), id)); }
    public List<Document> forRider(String riderId) {
        return store.all(collection()).stream().filter(d -> riderId.equals(d.get("rider", Document.class).getString("riderId"))).map(Rules::visible).toList();
    }
    protected Document needed(String collection, String id) {
        Document d = store.get(collection, id);
        if (d == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, collection + " : identifiant inconnu");
        return d;
    }
    public Document execute(Command c, String expected, String id) {
        if (c == null || c.payload() == null || !expected.equals(c.commandType())) throw new IllegalArgumentException("Commande incompatible avec cette route");
        try { UUID.fromString(c.commandId()); } catch (Exception e) { throw new IllegalArgumentException("commandId doit être un UUID"); }
        instant(c.occurredAt());
        String fingerprint = new Document("type", expected).append("id", id).append("payload", c.payload()).toJson();
        return store.atomic(() -> {
            Document previous = store.get("command_results", c.commandId());
            if (previous != null) {
                require(fingerprint.equals(previous.getString("fingerprint")), "commandId déjà utilisé avec une autre requête");
                return previous.get("result", Document.class);
            }
            Document result = visible(handle(expected, id, c.payload()));
            store.save("command_results", new Document("_id", c.commandId()).append("fingerprint", fingerprint).append("result", result));
            return result;
        });
    }
    public void receive(Document event) {
        store.atomic(() -> {
            String eventId = text(event, "eventId");
            if (store.get("processed_events", eventId) != null) return null;
            if (!event.getList("receivers", String.class).contains(service())) throw new IllegalArgumentException("Mauvais destinataire");
            onEvent(event);
            store.save("processed_events", new Document("_id", eventId).append("processedAt", Instant.now().toString()));
            return null;
        });
    }
    protected Document create(String idField) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        return new Document("_id", id).append(idField, id).append("createdAt", now).append("updatedAt", now).append("_version", 0L);
    }
    protected Document persist(Document d, String eventType, String... receivers) {
        d.put("updatedAt", Instant.now().toString());
        d.put("_version", ((Number) d.getOrDefault("_version", 0L)).longValue() + 1);
        store.save(collection(), d);
        String eventId = UUID.randomUUID().toString();
        Document event = new Document("eventId", eventId).append("eventType", eventType).append("occurredAt", d.getString("updatedAt"))
            .append("emitterService", service()).append("receivers", List.of(receivers)).append("aggregateId", d.getString("_id"))
            .append("aggregateVersion", d.get("_version")).append("payload", visible(d));
        for (String receiver : receivers) store.save("outbox", new Document("_id", eventId + ":" + receiver).append("receiver", receiver).append("event", event));
        return d;
    }
    protected boolean replicate(String collection, Document event, Document replica) {
        String id = event.getString("aggregateId");
        long version = ((Number) event.get("aggregateVersion")).longValue();
        Document previous = store.get(collection, id);
        if (previous != null && ((Number) previous.get("_sourceVersion")).longValue() >= version) return false;
        replica.put("_id", id);
        replica.put("_sourceVersion", version);
        if (previous != null && previous.containsKey("_activeBooking")) replica.put("_activeBooking", previous.get("_activeBooking"));
        store.save(collection, replica);
        return true;
    }
}
