package fr.equibook.core;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Comparator;

/** Une instance par service : le message reste dans processing jusqu'au commit MongoDB. */
@Component
public class EventPump {
    private static final Logger LOG = LoggerFactory.getLogger(EventPump.class);
    private final Store store;
    private final Domain domain;
    private final StringRedisTemplate redis;
    public EventPump(Store store, Domain domain, StringRedisTemplate redis) { this.store = store; this.domain = domain; this.redis = redis; }
    @Scheduled(fixedDelay = 200)
    public void publish() {
        try {
            var pending = store.all("outbox").stream().sorted(Comparator.comparing(d -> d.get("event", Document.class).getString("occurredAt"))).limit(100).toList();
            for (Document item : pending) {
                redis.opsForList().leftPush(queue(item.getString("receiver")), item.get("event", Document.class).toJson());
                store.delete("outbox", item.getString("_id"));
            }
        } catch (Exception e) { LOG.warn("Publication différée : {}", e.getMessage()); }
    }
    @Scheduled(fixedDelay = 200)
    public void consume() {
        String queue = queue(domain.service());
        String processing = queue + ":processing";
        try {
            for (int i = 0; i < 100; i++) {
                String json = redis.opsForList().index(processing, -1);
                if (json == null) json = redis.opsForList().rightPopAndLeftPush(queue, processing);
                if (json == null) return;
                domain.receive(Document.parse(json));
                redis.opsForList().remove(processing, 1, json);
            }
        } catch (Exception e) { LOG.warn("Événement conservé pour réessai : {}", e.getMessage()); }
    }
    private static String queue(String receiver) { return "events:" + receiver.replace("-service", ""); }
}
