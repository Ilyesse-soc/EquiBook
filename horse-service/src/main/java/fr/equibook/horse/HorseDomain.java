package fr.equibook.horse;

import fr.equibook.core.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import java.time.Year;
import java.util.List;
import static fr.equibook.core.Rules.*;

@Service
public class HorseDomain extends Domain {
    public HorseDomain(Store store) { super(store); }
    public String service() { return "horse-service"; }
    public String collection() { return "horses"; }
    protected Document handle(String type, String id, Document p) {
        Document d;
        if (type.equals("ChangeHorseAvailability")) {
            fields(p, "status");
            d = needed(collection(), id);
            require(d.getString("_activeBooking") == null, "Cheval affecté à une réservation confirmée");
            d.put("status", choice(p, "status", List.of("AVAILABLE", "RESERVED", "RESTING", "UNAVAILABLE")));
            return persist(d, "HorseAvailabilityChanged", "booking-service");
        }
        fields(p, "name", "breed", "birthYear", "minimumRiderLevel");
        d = type.equals("CreateHorse") ? create("horseId").append("status", "AVAILABLE") : needed(collection(), id);
        int year = integer(p, "birthYear");
        if (year < 1950 || year > Year.now().getValue()) throw new IllegalArgumentException("birthYear invalide");
        d.put("name", text(p, "name"));
        d.put("breed", text(p, "breed"));
        d.put("birthYear", year);
        d.put("minimumRiderLevel", choice(p, "minimumRiderLevel", LEVELS));
        return persist(d, type.equals("CreateHorse") ? "HorseCreated" : "HorseUpdated", "booking-service");
    }
    protected void onEvent(Document e) {
        String type = e.getString("eventType");
        if (!List.of("BookingConfirmed", "BookingCancelled", "BookingCompleted").contains(type)) throw new IllegalArgumentException("Événement Horse inconnu");
        Document p = e.get("payload", Document.class);
        if (!replicate("booking_replicas", e, new Document(p))) return;
        Document d = needed(collection(), p.get("horse", Document.class).getString("horseId"));
        String booking = p.getString("bookingId");
        if (type.equals("BookingConfirmed")) {
            require(d.getString("_activeBooking") == null || booking.equals(d.getString("_activeBooking")), "Cheval déjà réservé");
            d.put("_activeBooking", booking);
            d.put("status", "RESERVED");
        } else if (booking.equals(d.getString("_activeBooking"))) {
            d.remove("_activeBooking");
            d.put("status", "AVAILABLE");
        } else return;
        persist(d, "HorseAvailabilityChanged", "booking-service");
    }
}
