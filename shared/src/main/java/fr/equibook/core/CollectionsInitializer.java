package fr.equibook.core;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CollectionsInitializer implements ApplicationRunner {
    private final MongoTemplate mongo;
    private final Domain domain;
    public CollectionsInitializer(MongoTemplate mongo, Domain domain) { this.mongo = mongo; this.domain = domain; }
    public void run(ApplicationArguments args) {
        for (String name : List.of(domain.collection(), "outbox", "command_results", "processed_events", "rider_replicas", "horse_replicas", "booking_replicas", "payment_replicas")) {
            if (!mongo.collectionExists(name)) mongo.createCollection(name);
        }
    }
}
