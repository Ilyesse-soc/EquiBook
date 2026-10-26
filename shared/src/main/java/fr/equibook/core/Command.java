package fr.equibook.core;

import org.bson.Document;

public record Command(String commandId, String commandType, String occurredAt, Document payload) { }
