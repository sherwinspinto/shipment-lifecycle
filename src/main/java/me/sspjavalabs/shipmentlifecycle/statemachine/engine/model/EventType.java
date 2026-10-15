package me.sspjavalabs.shipmentlifecycle.statemachine.engine.model;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum EventType {
  LABEL_CREATED,
  PICKED_UP,
  IN_TRANSIT,
  OUT_FOR_DELIVERY,
  DELIVERED,
  EXCEPTION,
  CANCELLED;

  static final Map<String, EventType> CACHED_VALUE_MAP =
      Arrays.stream(EventType.values())
          .collect(Collectors.toUnmodifiableMap(Enum::name, val -> val));

  public static EventType parseFromString(String eventypeString) {
    return CACHED_VALUE_MAP.getOrDefault(eventypeString, null);
  }
}
