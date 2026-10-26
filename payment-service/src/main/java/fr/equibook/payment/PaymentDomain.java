package fr.equibook.payment;

import fr.equibook.core.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import java.util.List;
import static fr.equibook.core.Rules.*;

@Service
public class PaymentDomain extends Domain {
    public PaymentDomain(Store store) { super(store); }
    public String service() { return "payment-service"; }
    public String collection() { return "payments"; }
    protected Document handle(String type, String id, Document p) {
        if (type.equals("CreatePayment")) {
            fields(p, "bookingId");
            Document booking = needed("booking_replicas", text(p, "bookingId"));
            require(booking.getString("bookingStatus").equals("PENDING_PAYMENT"), "Réservation non payable");
            require(booking.getString("_paymentId") == null, "Paiement déjà créé pour cette réservation");
            Document d = create("paymentId").append("bookingId", booking.getString("bookingId")).append("riderId", booking.getString("riderId"))
                .append("amount", booking.get("price")).append("status", "PENDING");
            booking.put("_paymentId", d.getString("paymentId"));
            store.save("booking_replicas", booking);
            return persist(d, "PaymentCreated", "dashboard-service");
        }
        fields(p);
        Document d = needed(collection(), id);
        String status = d.getString("status");
        String event;
        switch (type) {
            case "ConfirmPayment" -> {
                require(status.equals("PENDING"), "Paiement en attente requis");
                Document b = needed("booking_replicas", d.getString("bookingId"));
                require(b.getString("bookingStatus").equals("PENDING_PAYMENT"), "Réservation non payable");
                d.put("status", "PAID"); event = "PaymentConfirmed";
            }
            case "FailPayment" -> {
                require(status.equals("PENDING"), "Paiement en attente requis");
                d.put("status", "FAILED"); event = "PaymentFailed";
            }
            case "RefundPayment" -> {
                require(status.equals("PAID"), "Paiement payé requis");
                require(!needed("booking_replicas", d.getString("bookingId")).getString("bookingStatus").equals("CONFIRMED"), "Annuler la réservation avant remboursement");
                d.put("status", "REFUNDED"); event = "PaymentRefunded";
            }
            default -> throw new IllegalArgumentException("Commande inconnue");
        }
        return type.equals("FailPayment") ? persist(d, event, "dashboard-service") : persist(d, event, "booking-service", "dashboard-service");
    }
    protected void onEvent(Document e) {
        if (!List.of("BookingCreated", "BookingConfirmed", "BookingCancelled", "BookingCompleted").contains(e.getString("eventType"))) throw new IllegalArgumentException("Événement Payment inconnu");
        Document p = e.get("payload", Document.class);
        String id = e.getString("aggregateId");
        Document previous = store.get("booking_replicas", id);
        Document replica = new Document("bookingId", id).append("riderId", p.get("rider", Document.class).getString("riderId"))
            .append("price", p.get("price")).append("bookingStatus", p.getString("status"));
        if (previous != null && previous.containsKey("_paymentId")) replica.put("_paymentId", previous.get("_paymentId"));
        replicate("booking_replicas", e, replica);
    }
}
