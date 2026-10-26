package fr.equibook.horse;
import fr.equibook.core.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.equibook.core.Fixtures.*;
class HorseDomainTest {
    final MemoryStore store = new MemoryStore();
    final HorseDomain domain = new HorseDomain(store);
    Document create() { return domain.execute(command("CreateHorse", new Document("name", "Éclair").append("breed", "Selle français").append("birthYear", 2016).append("minimumRiderLevel", "GALOP_2")), "CreateHorse", null); }
    @Test void createAndAvailability() {
        Document horse = create();
        assertEquals("AVAILABLE", horse.getString("status"));
        assertEquals("RESTING", domain.execute(command("ChangeHorseAvailability", new Document("status", "RESTING")), "ChangeHorseAvailability", horse.getString("horseId")).getString("status"));
    }
    @Test void bookingEventsReserveAndReleaseAndIgnoreStale() {
        String id = create().getString("horseId");
        Document booking = booking("booking", "rider", id);
        domain.receive(event(domain, "BookingConfirmed", "booking", 2, booking));
        assertEquals("RESERVED", domain.get(id).getString("status"));
        domain.receive(event(domain, "BookingCancelled", "booking", 3, booking));
        domain.receive(event(domain, "BookingConfirmed", "booking", 2, booking));
        assertEquals("AVAILABLE", domain.get(id).getString("status"));
    }
}
