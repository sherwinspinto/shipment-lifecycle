package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.RequestHandlerResult;
import me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto.ResponseDto;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.EventLog;
import me.sspjavalabs.shipmentlifecycle.statemachine.engine.model.Timeline;

public final class ShipmentsRequestHandler implements RequestHandler {
  public static final String ROOT_RESOURCE_URL = "/shipments";
  public static final String TIMELINE_GET_URL =
      ROOT_RESOURCE_URL + "/{carrier}/{shipmentId}/events";
  static final Pattern TIMELINE_GET_PATTERN =
      Pattern.compile(
          ROOT_RESOURCE_URL + "/(?<carrier>[a-zA-Z0-9-]+)/(?<shipmentId>[a-zA-Z0-9-]+)/events$");
  private final EventLog eventLog;

  public ShipmentsRequestHandler(EventLog eventLog) {
    this.eventLog = eventLog;
  }

  public RequestHandlerResult handleRequest(String requestMethod, String requestUrl)
      throws IOException {
    if (requestMethod.equals("GET")) {
      return handleGet(requestUrl);
    } else {
      return RequestHandlerResult.ofFailure(
          new RestServerFailure.MethodNotSupportedFailure(
              requestMethod, TIMELINE_GET_URL, List.of("GET")));
    }
  }

  RequestHandlerResult handleGet(String resourceUrl) throws IOException {
    Matcher matcher = TIMELINE_GET_PATTERN.matcher(resourceUrl);
    if (matcher.matches()) {
      String carrier = matcher.group("carrier");
      String shipmentId = matcher.group("shipmentId");
      Timeline timeline = eventLog.rebuild(carrier, shipmentId);
      return RequestHandlerResult.ofSuccess(ResponseDto.Events.fromDomain(timeline));
    } else
      return RequestHandlerResult.ofFailure(
          new RestServerFailure.UnmatchedPathFailure("GET", resourceUrl));
  }
}
