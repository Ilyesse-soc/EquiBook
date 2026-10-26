package fr.equibook.booking;

import fr.equibook.core.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import static fr.equibook.core.Rules.*;

@Service
public class BookingDomain extends Domain {
    public BookingDomain(Store store) { super(store); }
    public String service() { return "booking-service"; }
    public String collection() { return "bookings"; }
    protected Document handle(String type, String id, Document p) {
        if (type.equals("CreateBooking")) {
            fields(p, "riderId", "horseId", "sessionDate", "durationMinutes", "price");
            Document rider = needed("rider_replicas", text(p, "riderId"));
            Document horse = needed("horse_replicas", text(p, "horseId"));
            require("AVAILABLE".equals(horse.getString("status")) && horse.getString("_activeBooking") == null, "Cheval indisponible");
            require(LEVELS.indexOf(rider.getString("level")) >= LEVELS.indexOf(horse.getString("minimumRiderLevel")), "Niveau du cavalier insuffisant");
            require(instant(text(p, "sessionDate")).isAfter(Instant.now()), "La séance doit être dans le futur");
            require(integer(p, "durationMinutes") > 0, "Durée strictement positive requise");
            Document d = create("bookingId").append("rider", visible(rider)).append("horse", visible(horse))
                .append("sessionDate", text(p, "sessionDate")).append("durationMinutes", integer(p, "durationMinutes"))
                .append("price", money(p, "price").doubleValue()).append("status", "PENDING_PAYMENT");
            // Verrou local transactionnel : deux créations concurrentes ne peuvent affecter le même cheval.
            horse.put("_activeBooking", d.getString("bookingId"));
            store.save("horse_replicas", horse);
            return persist(d, "BookingCreated", "payment-service", "dashboard-service");
        }
        fields(p);
        Document d = needed(collection(), id);
        String status = d.getString("status");
        String event;
        switch (type) {
            case "ConfirmBooking" -> {
                require(status.equals("PENDING_PAYMENT") && Boolean.TRUE.equals(d.get("_paid")), "Paiement confirmé requis");
                Document horse = needed("horse_replicas", d.get("horse", Document.class).getString("horseId"));
                require("AVAILABLE".equals(horse.getString("status")) && id.equals(horse.getString("_activeBooking")), "Cheval indisponible");
                horse.put("status", "RESERVED");
                store.save("horse_replicas", horse);
                d.put("status", "CONFIRMED"); event = "BookingConfirmed";
            }
            case "CancelBooking" -> {
                require(List.of("PENDING_PAYMENT", "CREATED", "CONFIRMED").contains(status), "Réservation non annulable");
                d.put("status", "CANCELLED"); event = "BookingCancelled";
                release(d);
            }
            case "CompleteBooking" -> {
                require(status.equals("CONFIRMED"), "Réservation confirmée requise");
                d.put("status", "COMPLETED"); event = "BookingCompleted";
                release(d);
            }
            default -> throw new IllegalArgumentException("Commande inconnue");
        }
        return persist(d, event, "horse-service", "payment-service", "dashboard-service");
    }
    private void release(Document d) {
        Document horse = needed("horse_replicas", d.get("horse", Document.class).getString("horseId"));
        if (d.getString("bookingId").equals(horse.getString("_activeBooking"))) {
            horse.remove("_activeBooking");
            // Un cheval confirmé reste bloqué jusqu'à l'acquittement de HorseAvailabilityChanged.
            if (!"RESERVED".equals(horse.getString("status"))) horse.put("status", "AVAILABLE");
            store.save("horse_replicas", horse);
        }
    }
    protected void onEvent(Document e) {
        Document p = e.get("payload", Document.class);
        String type = e.getString("eventType");
        if (List.of("RiderCreated", "RiderUpdated", "RiderLevelChanged").contains(type)) {
            replicate("rider_replicas", e, new Document("riderId", p.getString("riderId"))
                .append("fullName", p.getString("firstName") + " " + p.getString("lastName")).append("level", p.getString("level")));
        } else if (List.of("HorseCreated", "HorseUpdated", "HorseAvailabilityChanged").contains(type)) {
            replicate("horse_replicas", e, new Document("horseId", p.getString("horseId")).append("name", p.getString("name"))
                .append("minimumRiderLevel", p.getString("minimumRiderLevel")).append("status", p.getString("status")));
        } else if (List.of("PaymentConfirmed", "PaymentRefunded").contains(type)) {
            if (!replicate("payment_replicas", e, new Document(p))) return;
            Document booking = needed(collection(), p.getString("bookingId"));
            booking.put("_paid", type.equals("PaymentConfirmed"));
            store.save(collection(), booking);
        } else throw new IllegalArgumentException("Événement Booking inconnu");
    }
}
