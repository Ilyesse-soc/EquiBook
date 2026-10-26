package fr.equibook.core;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import com.mongodb.MongoException;
import java.util.List;
import java.util.function.Supplier;

@Component
public class MongoStore implements Store {
    private final MongoTemplate mongo;
    private final TransactionTemplate transaction;
    public MongoStore(MongoTemplate mongo, MongoDatabaseFactory factory) {
        this.mongo = mongo;
        this.transaction = new TransactionTemplate(new MongoTransactionManager(factory));
    }
    public Document get(String collection, String id) { return mongo.findById(id, Document.class, collection); }
    public List<Document> all(String collection) { return mongo.findAll(Document.class, collection); }
    public void save(String collection, Document value) { mongo.save(value, collection); }
    public void delete(String collection, String id) { mongo.remove(Query.query(Criteria.where("_id").is(id)), collection); }
    public <T> T atomic(Supplier<T> work) {
        for (int attempt = 0; ; attempt++) {
            try { return transaction.execute(status -> work.get()); }
            catch (RuntimeException e) {
                Throwable cause = e;
                while (cause != null && !(cause instanceof MongoException)) cause = cause.getCause();
                if (attempt >= 4 || !(cause instanceof MongoException m) ||
                        !m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) throw e;
            }
        }
    }
}
