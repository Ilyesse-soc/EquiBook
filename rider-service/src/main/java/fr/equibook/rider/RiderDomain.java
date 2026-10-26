package fr.equibook.rider;

import fr.equibook.core.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import static fr.equibook.core.Rules.*;

@Service
public class RiderDomain extends Domain {
    public RiderDomain(Store store) { super(store); }
    public String service() { return "rider-service"; }
    public String collection() { return "riders"; }
    protected Document handle(String type, String id, Document p) {
        Document d;
        String event;
        if (type.equals("ChangeRiderLevel")) {
            fields(p, "level");
            d = needed(collection(), id);
            d.put("level", choice(p, "level", LEVELS));
            event = "RiderLevelChanged";
        } else {
            fields(p, "firstName", "lastName", "email", "phone", "level");
            d = type.equals("CreateRider") ? create("riderId") : needed(collection(), id);
            String email = text(p, "email");
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("email invalide");
            d.put("firstName", text(p, "firstName"));
            d.put("lastName", text(p, "lastName"));
            d.put("email", email);
            d.put("phone", text(p, "phone"));
            d.put("level", choice(p, "level", LEVELS));
            event = type.equals("CreateRider") ? "RiderCreated" : "RiderUpdated";
        }
        return persist(d, event, "booking-service", "dashboard-service");
    }
    protected void onEvent(Document e) { throw new IllegalArgumentException("Rider ne consomme aucun événement métier"); }
}
