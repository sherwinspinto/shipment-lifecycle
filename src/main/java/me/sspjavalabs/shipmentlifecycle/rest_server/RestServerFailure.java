package me.sspjavalabs.shipmentlifecycle.rest_server;

import java.util.List;

public sealed interface RestServerFailure {
  int statusCode();

  record MethodNotSupportedFailure(List<String> allowedMethods) implements RestServerFailure {
    public int statusCode() {
      return 405;
    }
  }

  record UnmatchedPathFailure() implements RestServerFailure {
    private static final String responseBody =
        "{ \"error\": \"The requested resource path was not found\"}";

    public int statusCode() {
      return 404;
    }

    public String responseBody() {
      return responseBody;
    }
  }

  record BadRequestBody(String responseBody) implements RestServerFailure {
    public int statusCode() {
      return 400;
    }
  }

  record InternalServerError() implements RestServerFailure {
    public int statusCode() {
      return 500;
    }
  }
}
