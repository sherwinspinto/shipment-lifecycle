package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto;

import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;

public sealed interface RequestHandlerResult {
  record Ok(String responseBody) implements RequestHandlerResult {
    public static Ok ok(String responseBody) {
      return new Ok(responseBody);
    }
  }

  record Created(String responseBody, String location) implements RequestHandlerResult {
    public static Created created(String responseBody, String location) {
      return new Created(responseBody, location);
    }
  }

  record Failure(RestServerFailure restServerFailure) implements RequestHandlerResult {
    public static Failure failure(RestServerFailure restServerFailure) {
      return new Failure(restServerFailure);
    }
  }
}
