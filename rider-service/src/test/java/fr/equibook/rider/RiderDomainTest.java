package fr.equibook.rider;
import fr.equibook.core.*;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.equibook.core.Fixtures.*;
class RiderDomainTest {
    final MemoryStore store = new MemoryStore();
    final RiderDomain domain = new RiderDomain(store);
    Document payload() { return new Document("firstName", "Emma").append("lastName", "Martin").append("email", "emma@example.org").append("phone", "+33612345678").append("level", "BEGINNER"); }
    @Test void createAndChangeLevel() {
        Document r = domain.execute(command("CreateRider", payload()), "CreateRider", null);
        assertNotNull(r.getString("riderId"));
        assertEquals("BEGINNER", r.getString("level"));
        Document changed = domain.execute(command("ChangeRiderLevel", new Document("level", "GALOP_3")), "ChangeRiderLevel", r.getString("riderId"));
        assertEquals("GALOP_3", changed.getString("level"));
        assertEquals(4, store.all("outbox").size());
        assertFalse(changed.containsKey("_version"));
    }
    @Test void commandReplayDoesNotCreateTwice() {
        Command c = command("CreateRider", payload());
        assertEquals(domain.execute(c, "CreateRider", null), domain.execute(c, "CreateRider", null));
        assertEquals(1, store.all("riders").size());
        assertThrows(RuntimeException.class, () -> domain.execute(new Command(c.commandId(), "CreateRider", c.occurredAt(), payload().append("level", "GALOP_1")), "CreateRider", null));
    }
    @Test void invalidInputDoesNotPersist() {
        assertThrows(IllegalArgumentException.class, () -> domain.execute(command("CreateRider", payload().append("email", "incorrect")), "CreateRider", null));
        assertTrue(store.all("riders").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> domain.execute(command("CreateRider", payload().append("level", "EXPERT")), "CreateRider", null));
    }
}
