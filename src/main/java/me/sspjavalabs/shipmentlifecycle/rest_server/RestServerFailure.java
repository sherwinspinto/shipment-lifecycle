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

  record TooManyRequests(long retryInSeconds) implements RestServerFailure {
    private static final String error = "{ \"error\" : \"retry in {retrySeconds} second(s)\"}";

    public int statusCode() {
      return 429;
    }

    public String responseBody() {
      return error.replace("{retrySeconds}", (retryInSeconds + ""));
    }
  }
}
