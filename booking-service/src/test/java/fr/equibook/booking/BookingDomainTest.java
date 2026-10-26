package fr.equibook.booking;
import fr.equibook.core.*;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.equibook.core.Fixtures.*;
class BookingDomainTest {
    final MemoryStore store = new MemoryStore();
    final BookingDomain domain = new BookingDomain(store);
    @BeforeEach void replicas() {
        domain.receive(event(domain, "RiderCreated", "rider", 1, rider("rider")));
        domain.receive(event(domain, "HorseCreated", "horse", 1, horse("horse")));
    }
    Document payload() { return new Document("riderId", "rider").append("horseId", "horse").append("sessionDate", java.time.Instant.now().plusSeconds(86400).toString()).append("durationMinutes", 60).append("price", 45.50); }
    Document create() { return domain.execute(command("CreateBooking", payload()), "CreateBooking", null); }
    void paid(String id) { domain.receive(event(domain, "PaymentConfirmed", "payment", 2, new Document("bookingId", id))); }
    @Test void replicaCreationUpdateAndStaleProtection() {
        assertEquals("Emma Martin", store.get("rider_replicas", "rider").getString("fullName"));
        domain.receive(event(domain, "RiderLevelChanged", "rider", 2, rider("rider").append("level", "GALOP_5")));
        domain.receive(event(domain, "RiderUpdated", "rider", 1, rider("rider")));
        assertEquals("GALOP_5", store.get("rider_replicas", "rider").getString("level"));
        domain.receive(event(domain, "HorseUpdated", "horse", 2, horse("horse").append("name", "Orion")));
        assertEquals("Orion", store.get("horse_replicas", "horse").getString("name"));
        domain.receive(event(domain, "HorseAvailabilityChanged", "horse", 3, horse("horse").append("status", "RESTING")));
        assertEquals("RESTING", store.get("horse_replicas", "horse").getString("status"));
    }
    @Test void createPayConfirmCancel() {
        Document b = create(); String id = b.getString("bookingId");
        assertEquals("PENDING_PAYMENT", b.getString("status"));
        assertThrows(RuntimeException.class, () -> domain.execute(command("ConfirmBooking", new Document()), "ConfirmBooking", id));
        paid(id);
        assertEquals("CONFIRMED", domain.execute(command("ConfirmBooking", new Document()), "ConfirmBooking", id).getString("status"));
        assertEquals("CANCELLED", domain.execute(command("CancelBooking", new Document()), "CancelBooking", id).getString("status"));
        assertEquals(1, domain.forRider("rider").size());
    }
    @Test void completionAndInvalidTransition() {
        String id = create().getString("bookingId"); paid(id);
        domain.execute(command("ConfirmBooking", new Document()), "ConfirmBooking", id);
        assertEquals("COMPLETED", domain.execute(command("CompleteBooking", new Document()), "CompleteBooking", id).getString("status"));
        assertThrows(RuntimeException.class, () -> domain.execute(command("CancelBooking", new Document()), "CancelBooking", id));
    }
    @Test void insufficientLevelAndUnavailableAndMissingAndBadDate() {
        domain.receive(event(domain, "RiderLevelChanged", "rider", 2, rider("rider").append("level", "BEGINNER")));
        assertThrows(RuntimeException.class, this::create);
        domain.receive(event(domain, "RiderLevelChanged", "rider", 3, rider("rider")));
        assertThrows(RuntimeException.class, () -> domain.execute(command("CreateBooking", payload().append("sessionDate", "2020-01-01T00:00:00Z")), "CreateBooking", null));
        assertThrows(RuntimeException.class, () -> domain.execute(command("CreateBooking", payload().append("riderId", "unknown")), "CreateBooking", null));
        domain.receive(event(domain, "HorseAvailabilityChanged", "horse", 2, horse("horse").append("status", "RESTING")));
        assertThrows(RuntimeException.class, this::create);
        assertTrue(store.all("bookings").isEmpty());
    }
    @Test void duplicateDeliveryIsIdempotent() {
        Document e = event(domain, "RiderUpdated", "rider", 2, rider("rider").append("firstName", "Léa"));
        domain.receive(e); domain.receive(e);
        assertEquals(3, store.all("processed_events").size());
        assertEquals("Léa Martin", store.get("rider_replicas", "rider").getString("fullName"));
    }
    @Test void preventsDoubleAssignmentAndReleasesPendingCancellation() {
        String id = create().getString("bookingId");
        assertThrows(RuntimeException.class, this::create);
        domain.execute(command("CancelBooking", new Document()), "CancelBooking", id);
        assertNotNull(create());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"durationMinutes,0", "durationMinutes,-1", "price,0", "price,-5", "price,1.001", "price,1000001"})
    void invalidDurationAndPriceAreRejected(String field, double value) {
        assertThrows(RuntimeException.class, () -> domain.execute(command("CreateBooking", payload().append(field, value)), "CreateBooking", null));
        assertTrue(store.all("bookings").isEmpty());
        assertTrue(store.all("outbox").isEmpty());
        assertNull(store.get("horse_replicas", "horse").getString("_activeBooking"));
    }
}
