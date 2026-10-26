package fr.equibook.payment;
import fr.equibook.core.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.equibook.core.Fixtures.*;
class PaymentDomainTest {
    final MemoryStore store = new MemoryStore();
    final PaymentDomain domain = new PaymentDomain(store);
    Document create() {
        domain.receive(event(domain, "BookingCreated", "booking", 1, booking("booking", "rider", "horse")));
        return domain.execute(command("CreatePayment", new Document("bookingId", "booking")), "CreatePayment", null);
    }
    @Test void replicaCreatePaymentConfirmRefundAndUpdate() {
        String id = create().getString("paymentId");
        assertEquals("rider", store.get("booking_replicas", "booking").getString("riderId"));
        assertEquals("PAID", domain.execute(command("ConfirmPayment", new Document()), "ConfirmPayment", id).getString("status"));
        domain.receive(event(domain, "BookingCancelled", "booking", 3, booking("booking", "rider", "horse").append("status", "CANCELLED")));
        assertEquals("CANCELLED", store.get("booking_replicas", "booking").getString("bookingStatus"));
        assertEquals("REFUNDED", domain.execute(command("RefundPayment", new Document()), "RefundPayment", id).getString("status"));
    }
    @Test void cancelledBookingCannotBePaid() {
        String id = create().getString("paymentId");
        domain.receive(event(domain, "BookingCancelled", "booking", 2, booking("booking", "rider", "horse").append("status", "CANCELLED")));
        assertThrows(RuntimeException.class, () -> domain.execute(command("ConfirmPayment", new Document()), "ConfirmPayment", id));
    }
    @Test void failureAndDuplicatePaymentRejected() {
        String id = create().getString("paymentId");
        assertThrows(RuntimeException.class, () -> domain.execute(command("CreatePayment", new Document("bookingId", "booking")), "CreatePayment", null));
        assertEquals("FAILED", domain.execute(command("FailPayment", new Document()), "FailPayment", id).getString("status"));
    }
}
