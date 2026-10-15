package me.sspjavalabs.shipmentlifecycle.authentication;

import java.util.Optional;

public interface BearerTokenResolver {
  Optional<BearerTokenResolverResponse> resolve(String bearerToken);

  record UserData(String userName) {}

  record BearerTokenResolverResponse(UserData userData, String realm) {}

  class SystemPropertiesBearerTokenResolver implements BearerTokenResolver {

    @Override
    public Optional<BearerTokenResolverResponse> resolve(String bearerToken) {
      String storedToken = System.getProperty("SHIPMENT_LIFECYCLE_USER_TOKEN");
      if (bearerToken == null || bearerToken.isBlank() || !bearerToken.trim().equals(storedToken))
        return Optional.empty();
      return Optional.of(new BearerTokenResolverResponse(new UserData("api_user"), "api"));
    }
  }
}
