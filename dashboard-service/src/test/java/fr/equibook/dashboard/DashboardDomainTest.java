package fr.equibook.dashboard;
import fr.equibook.core.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.equibook.core.Fixtures.*;
class DashboardDomainTest {
    final MemoryStore store = new MemoryStore();
    final DashboardDomain domain = new DashboardDomain(store);
    void send(String type, String id, int v, Document p) { domain.receive(event(domain, type, id, v, p)); }
    @Test void projectionProgressesAndRefundsWithoutDoubleCounting() {
        send("RiderCreated", "rider", 1, rider("rider"));
        Document booking = booking("booking", "rider", "horse");
        send("BookingCreated", "booking", 1, booking);
        send("BookingConfirmed", "booking", 2, new Document(booking).append("status", "CONFIRMED"));
        Document payment = new Document("paymentId", "payment").append("riderId", "rider").append("amount", 45.50).append("status", "PAID");
        Document event = event(domain, "PaymentConfirmed", "payment", 2, payment);
        domain.receive(event); domain.receive(event);
        Document d = domain.get("rider");
        assertEquals(1, ((Number)d.get("totalBookings")).intValue());
        assertEquals(1, ((Number)d.get("confirmedBookings")).intValue());
        assertEquals(45.50, ((Number)d.get("totalAmountPaid")).doubleValue());
        send("BookingCompleted", "booking", 3, new Document(booking).append("status", "COMPLETED"));
        assertEquals(1, ((Number)domain.get("rider").get("completedBookings")).intValue());
        assertEquals(0, ((Number)domain.get("rider").get("confirmedBookings")).intValue());
        send("PaymentRefunded", "payment", 3, new Document(payment).append("status", "REFUNDED"));
        send("PaymentConfirmed", "payment", 2, payment);
        assertEquals(0.0, ((Number)domain.get("rider").get("totalAmountPaid")).doubleValue());
        send("RiderLevelChanged", "rider", 2, rider("rider").append("level", "GALOP_7"));
        assertEquals("GALOP_7", domain.get("rider").getString("riderLevel"));
    }
    @Test void bookingBeforeRiderAndOutOfOrderEvents() {
        Document b = booking("booking", "rider", "horse");
        send("BookingCancelled", "booking", 3, new Document(b).append("status", "CANCELLED"));
        send("BookingCreated", "booking", 1, b);
        send("RiderCreated", "rider", 1, rider("rider"));
        assertEquals(1, ((Number)domain.get("rider").get("cancelledBookings")).intValue());
        assertEquals(1, ((Number)domain.get("rider").get("totalBookings")).intValue());
    }
}
