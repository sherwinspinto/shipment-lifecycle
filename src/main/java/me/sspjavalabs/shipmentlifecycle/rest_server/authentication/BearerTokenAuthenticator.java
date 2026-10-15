package me.sspjavalabs.shipmentlifecycle.rest_server.authentication;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.util.List;
import java.util.Optional;
import me.sspjavalabs.shipmentlifecycle.authentication.BearerTokenResolver;

public class BearerTokenAuthenticator extends Authenticator {
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final BearerTokenResolver bearerTokenResolver;

  public BearerTokenAuthenticator(BearerTokenResolver bearerTokenResolver) {
    this.bearerTokenResolver = bearerTokenResolver;
  }

  @Override
  public Result authenticate(HttpExchange httpExchange) {
    Headers requestHeaders = httpExchange.getRequestHeaders();

    List<String> authorizationHeaders = requestHeaders.get(AUTHORIZATION_HEADER);
    String authorizationHeader =
        authorizationHeaders != null && !authorizationHeaders.isEmpty()
            ? authorizationHeaders.getFirst()
            : null;

    if (authorizationHeader == null) return new Authenticator.Failure(401);
    Optional<String> bearerTokenOpt = extractBearerTokenFromString(authorizationHeader);

    if (bearerTokenOpt.isEmpty()) return new Authenticator.Failure(401);
    Optional<BearerTokenResolver.BearerTokenResolverResponse> response =
        bearerTokenResolver.resolve(bearerTokenOpt.get());

    if (response.isPresent())
      return new Authenticator.Success(
          new HttpPrincipal(response.get().userData().userName(), response.get().realm()));
    return new Authenticator.Failure(401);
  }

  private Optional<String> extractBearerTokenFromString(String bearerTokenString) {
    return Optional.of(bearerTokenString)
        .filter(s -> s.startsWith("Bearer "))
        .map(s -> s.substring(7))
        .map(String::trim);
  }
}
