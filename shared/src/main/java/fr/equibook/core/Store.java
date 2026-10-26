package fr.equibook.core;

import org.bson.Document;
import java.util.List;
import java.util.function.Supplier;

public interface Store {
    Document get(String collection, String id);
    List<Document> all(String collection);
    void save(String collection, Document value);
    void delete(String collection, String id);
    <T> T atomic(Supplier<T> work);
}
