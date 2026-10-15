package me.sspjavalabs.shipmentlifecycle.rest_server.requesthandler.dto;

import me.sspjavalabs.shipmentlifecycle.rest_server.RestServerFailure;

public sealed interface RequestHandlerResult {
  record Success(ResponseDto responseDto) implements RequestHandlerResult {}

  record Failure(RestServerFailure restServerFailure) implements RequestHandlerResult {}

  static Success ofSuccess(ResponseDto responseDto) {
    return new Success(responseDto);
  }

  static Failure ofFailure(RestServerFailure restServerFailure) {
    return new Failure(restServerFailure);
  }
}
