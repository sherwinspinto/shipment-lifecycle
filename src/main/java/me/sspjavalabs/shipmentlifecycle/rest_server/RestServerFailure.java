package me.sspjavalabs.shipmentlifecycle.rest_server;

import java.util.List;

public sealed interface RestServerFailure {
  record MethodNotSupportedFailure(String method, String resourceUrl, List<String> allowedMethods)
      implements RestServerFailure {}

  record UnmatchedPathFailure(String method, String resourceUrl) implements RestServerFailure {}
}
