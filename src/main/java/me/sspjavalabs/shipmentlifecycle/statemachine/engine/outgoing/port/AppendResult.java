package me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port;

public sealed interface AppendResult {
  record Inserted(String eventId) implements AppendResult {}

  record Duplicate(String eventId) implements AppendResult {}

  static Inserted ofInserted(String eventId) {
    return new Inserted(eventId);
  }

  static Duplicate ofDuplicate(String eventId) {
    return new Duplicate(eventId);
  }
}
