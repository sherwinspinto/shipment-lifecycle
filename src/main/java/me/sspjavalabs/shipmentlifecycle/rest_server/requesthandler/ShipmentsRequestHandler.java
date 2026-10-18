package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler;

import static me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestDto.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult.*;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult.Created;
import static me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.ResponseDto.*;
import static me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendResult.*;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.sspjavalabs.shipmentlifecycle.infra.JsonSerDer;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure.UnmatchedPathFailure;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult.Failure;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.EventType;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.Timeline;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.TrackingEvent;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.outgoing.port.AppendPort;

public final class ShipmentsRequestHandler implements RequestHandler {
  public static final String ROOT_RESOURCE_URL = "/shipments";
  static final Pattern TIMELINE_GET_PATTERN =
      Pattern.compile(
          ROOT_RESOURCE_URL + "/(?<carrier>[a-zA-Z0-9-]+)/(?<shipmentId>[a-zA-Z0-9-]+)/events$");
  static final Pattern CREATE_EVENT_POST_PATTERN =
      Pattern.compile(
          ROOT_RESOURCE_URL + "/(?<carrier>[a-zA-Z0-9-]+)/(?<shipmentId>[a-zA-Z0-9-]+)/events$");
  private final EventLog eventLog;
  private final AppendPort appendPort;
  private final JsonSerDer jsonSerDer;

  public ShipmentsRequestHandler(EventLog eventLog, AppendPort appendPort, JsonSerDer jsonSerDer) {
    this.eventLog = eventLog;
    this.appendPort = appendPort;
    this.jsonSerDer = jsonSerDer;
  }

  public RequestHandlerResult handleRequest(
      String requestMethod, String requestUrl, String requestBody) throws IOException {
    if (requestMethod.equals("GET")) {
      return handleGet(requestUrl);
    } else if (requestMethod.equals("POST")) {
      return handlePost(requestUrl, requestBody);
    } else {
      return Failure.failure(new MethodNotSupportedFailure(List.of("GET")));
    }
  }

  RequestHandlerResult handleGet(String resourceUrl) throws IOException {
    Matcher matcher = TIMELINE_GET_PATTERN.matcher(resourceUrl);
    if (matcher.matches()) {
      String carrier = matcher.group("carrier");
      String shipmentId = matcher.group("shipmentId");
      Timeline timeline = eventLog.rebuild(carrier, shipmentId);
      return Ok.ok(jsonSerDer.toJsonString(Events.fromDomain(timeline)));
    } else return Failure.failure(new UnmatchedPathFailure());
  }

  RequestHandlerResult handlePost(String url, String requestBody) throws IOException {
    Matcher matcher = CREATE_EVENT_POST_PATTERN.matcher(url);
    if (matcher.matches()) {
      CreateEvent requestDto = jsonSerDer.toObject(requestBody, CreateEvent.class);
      String carrier = matcher.group("carrier");
      String shipmentId = matcher.group("shipmentId");

      if (requestDto.eventId() == null || requestDto.eventId().isBlank())
        return RequestHandlerResult.Failure.failure(
            new BadRequestBody(
                jsonSerDer.toJsonString(new ValidationError("event_id cannot be NULL or BLANK"))));
      if (requestDto.eventType() == null
          || EventType.parseFromString(requestDto.eventType()) == null)
        return RequestHandlerResult.Failure.failure(
            new BadRequestBody(
                jsonSerDer.toJsonString(new ValidationError("Incorrect event_type"))));
      if (requestDto.occurredAt() != null
          && !requestDto.occurredAt().isBlank()
          && TrackingEvent.tryParseOccurredAt(requestDto.occurredAt()).isEmpty())
        return RequestHandlerResult.Failure.failure(
            new BadRequestBody(
                jsonSerDer.toJsonString(new ValidationError("Unparseable occurred_at"))));

      TrackingEvent trackingEvent = requestDto.toDomain(shipmentId);

      return switch (appendPort.append(carrier, trackingEvent)) {
        case Inserted(String insertedEventId) ->
            Created.created(
                jsonSerDer.toJsonString(new CreateEventView("inserted", insertedEventId)), url);
        case Duplicate(String duplicateEventId) ->
            Ok.ok(jsonSerDer.toJsonString(new CreateEventView("duplicate", duplicateEventId)));
      };
    } else return Failure.failure(new UnmatchedPathFailure());
  }
}
