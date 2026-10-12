package me.sspjavalabs.shipmentlifecycle.helper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.sspjavalabs.shipmentlifecycle.db.adapter.ValueCaster;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;

public class EventGenerator {
  private static final String NEW_LINE = System.lineSeparator();
  private static final String EMPTY_STRING = "";
  private static final Pattern RETRIEVE_TIME_OFFSET = Pattern.compile("@T\\((NULL|\\d+)\\)");
  private static final Instant baseInstant = Instant.parse("2026-08-06T18:00:00Z");

  public static List<TrackingEvent> listOfTrackingEventsFromCsvString(String multilineCsv) {
    return Arrays.stream(multilineCsv.split(NEW_LINE))
        .filter(Predicate.not(String::isBlank))
        .map(EventGenerator::trackingEventFromCsvString)
        .toList();
  }

  public static TrackingEvent trackingEventFromCsvString(String trackingEventString) {
    String[] fieldsAsString = trackingEventString.split(",");
    Map<TrackingEventFields, String> fieldValueMap =
        Arrays.stream(fieldsAsString)
            .map(Tuple::fromKvField)
            .collect(Collectors.toMap(Tuple::fieldName, Tuple::fieldValue));
    EventType eventType =
        ValueCaster.getAsEnum(fieldValueMap.get(TrackingEventFields.eventType), EventType.class);
    Optional<String> occurredAtWithPossibleOffset =
        Optional.of(fieldValueMap.get(TrackingEventFields.occurredAt));

    Instant occurredAtInstant =
        occurredAtWithPossibleOffset
            .map(s -> extractTimeOffset(s, RETRIEVE_TIME_OFFSET))
            .filter(Predicate.not(OptionalInt::isEmpty))
            .map(OptionalInt::getAsInt)
            .map(offset -> baseInstant.plus(offset, ChronoUnit.HOURS))
            .orElse(null);

    return new TrackingEvent(
        fieldValueMap.get(TrackingEventFields.shipmentId),
        fieldValueMap.get(TrackingEventFields.eventId),
        eventType,
        occurredAtInstant);
  }

  public static OptionalInt extractTimeOffset(String eventType, Pattern pattern) {
    Matcher matcher = pattern.matcher(eventType);
    if (matcher.find()) {
      String offsetString = matcher.group(1);
      return offsetString == null || offsetString.isBlank() || "NULL".equals(offsetString)
          ? OptionalInt.empty()
          : OptionalInt.of(Integer.parseInt(offsetString));
    }
    throw new RuntimeException("Invalid occurredAt in test data");
  }

  private static Instant getBaseInstantWithOffset(int offset) {
    return baseInstant.plus(offset, ChronoUnit.HOURS);
  }

  public static String removeTimeOffsetFromEventType(String eventType) {
    return eventType.replaceAll(RETRIEVE_TIME_OFFSET.toString(), "");
  }

  record Tuple(TrackingEventFields fieldName, String fieldValue) {
    static Tuple fromKvField(String kvField) {
      String[] kvFieldArray = kvField.split("=");
      TrackingEventFields field = TrackingEventFields.valueOf(kvFieldArray[0]);
      String fieldValue = kvFieldArray[1];
      return Tuple.of(field, fieldValue);
    }

    static Tuple of(TrackingEventFields fieldName, String fieldValue) {
      return new Tuple(fieldName, fieldValue);
    }
  }

  public enum TrackingEventFields {
    eventId,
    shipmentId,
    eventType,
    occurredAt
  }

  public static class TrackingEventListAggregator implements ArgumentsAggregator {
    @Override
    public @Nullable List<TrackingEvent> aggregateArguments(
        ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
      String csvString = accessor.getString(0);
      assert csvString != null;
      return listOfTrackingEventsFromCsvString(csvString);
    }
  }
}
