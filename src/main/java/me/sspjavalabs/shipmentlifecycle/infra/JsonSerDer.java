package me.sspjavalabs.shipmentlifecycle.infra;

public interface JsonSerDer {
  <I> String toJsonString(I input);

  <O> O toObject(String input, Class<O> type);
}
