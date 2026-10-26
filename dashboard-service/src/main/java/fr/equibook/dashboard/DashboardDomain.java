package fr.equibook.dashboard;

import fr.equibook.core.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import static fr.equibook.core.Rules.*;

@Service
public class DashboardDomain extends Domain {
    public DashboardDomain(Store store) { super(store); }
    public String service() { return "dashboard-service"; }
    public String collection() { return "rider_dashboards"; }
    protected Document handle(String t, String id, Document p) { throw new IllegalArgumentException("Projection en lecture seule"); }
    protected void onEvent(Document e) {
        String type = e.getString("eventType");
        Document p = e.get("payload", Document.class);
        String riderId;
        if (List.of("RiderCreated", "RiderUpdated", "RiderLevelChanged").contains(type)) {
            if (!replicate("rider_replicas", e, new Document(p))) return;
            riderId = p.getString("riderId");
        } else if (List.of("BookingCreated", "BookingConfirmed", "BookingCancelled", "BookingCompleted").contains(type)) {
            if (!replicate("booking_replicas", e, new Document(p))) return;
            riderId = p.get("rider", Document.class).getString("riderId");
        } else if (List.of("PaymentCreated", "PaymentConfirmed", "PaymentFailed", "PaymentRefunded").contains(type)) {
            if (!replicate("payment_replicas", e, new Document(p))) return;
            riderId = p.getString("riderId");
        } else throw new IllegalArgumentException("Événement Dashboard inconnu");
        rebuild(riderId);
    }
    private void rebuild(String riderId) {
        Document rider = store.get("rider_replicas", riderId);
        if (rider == null) return; // Une livraison croisée peut précéder RiderCreated ; ses données restent conservées.
        var bookings = store.all("booking_replicas").stream().filter(b -> riderId.equals(b.get("rider", Document.class).getString("riderId"))).toList();
        BigDecimal total = store.all("payment_replicas").stream().filter(p -> riderId.equals(p.getString("riderId")) && "PAID".equals(p.getString("status")))
            .map(p -> new BigDecimal(p.get("amount").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Document dashboard = new Document("_id", riderId).append("riderId", riderId)
            .append("riderName", rider.getString("firstName") + " " + rider.getString("lastName")).append("riderLevel", rider.getString("level"))
            .append("totalBookings", bookings.size()).append("confirmedBookings", count(bookings, "CONFIRMED"))
            .append("completedBookings", count(bookings, "COMPLETED")).append("cancelledBookings", count(bookings, "CANCELLED"))
            .append("totalAmountPaid", total.doubleValue()).append("lastBookingDate", bookings.stream().map(b -> b.getString("sessionDate")).max(java.util.Comparator.comparing(java.time.Instant::parse)).orElse(null));
        store.save(collection(), dashboard);
    }
    private long count(List<Document> bookings, String status) { return bookings.stream().filter(b -> status.equals(b.getString("status"))).count(); }
}
