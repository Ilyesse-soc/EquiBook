package fr.equibook.core;

import org.bson.Document;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Fixtures {
    private Fixtures() { }
    public static Command command(String type, Document p) { return new Command(UUID.randomUUID().toString(), type, Instant.now().toString(), p); }
    public static Document event(Domain receiver, String type, String id, long version, Document p) {
        return new Document("eventId", UUID.randomUUID().toString()).append("eventType", type).append("aggregateId", id)
            .append("aggregateVersion", version).append("receivers", List.of(receiver.service())).append("payload", p);
    }
    public static Document rider(String id) { return new Document("riderId", id).append("firstName", "Emma").append("lastName", "Martin").append("level", "GALOP_3"); }
    public static Document horse(String id) { return new Document("horseId", id).append("name", "Éclair").append("minimumRiderLevel", "GALOP_2").append("status", "AVAILABLE"); }
    public static Document booking(String id, String rider, String horse) {
        return new Document("bookingId", id).append("rider", new Document("riderId", rider)).append("horse", new Document("horseId", horse))
            .append("price", 45.50).append("sessionDate", Instant.now().plusSeconds(86400).toString()).append("status", "PENDING_PAYMENT");
    }
}
