package me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter;

import static me.sspjavalabs.shipmentlifecycle.rest_server.rate_limiter.FixedWindowLimiter.LimiterEntry.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class FixedWindowLimiter {
  private final Map<String, LimiterEntry> windowCache;
  private final int countLimit;
  private final Duration timeWindowDuration;

  public FixedWindowLimiter(Map<String, LimiterEntry> windowCache) {
    this.windowCache = windowCache;
    this.countLimit = 3;
    this.timeWindowDuration = Duration.of(60, ChronoUnit.SECONDS);
  }

  public FixedWindowLimiter(
      Map<String, LimiterEntry> windowCache, int countLimit, Duration timeWindowDuration) {
    this.windowCache = windowCache;
    this.countLimit = countLimit;
    this.timeWindowDuration = timeWindowDuration;
  }

  public LimiterEntry allow(String carrier, Instant now) {
    return compute(carrier, now);
  }

  private LimiterEntry compute(String carrier, Instant now) {
    return windowCache.compute(
        carrier,
        (k, entry) -> {
          if (k == null) {
            return new Allow(now, 1);
          }
          if (entry == null) {
            return new Allow(now, 1);
          }
          if (now.getEpochSecond()
              >= entry.windowStart().plus(this.timeWindowDuration).getEpochSecond()) {
            return new Allow(now, 1);
          } else {
            if (this.countLimit >= (entry.count() + 1))
              return new Allow(entry.windowStart(), entry.count() + 1);
            else return new Block(entry.windowStart(), entry.count());
          }
        });
  }

  public sealed interface LimiterEntry {
    Instant windowStart();

    int count();

    record Allow(Instant windowStart, int count) implements LimiterEntry {}

    record Block(Instant windowStart, int count) implements LimiterEntry {}
  }

  public long getRetry(LimiterEntry le, Instant now) {
    return switch (le) {
      case Block(Instant windowStart, _) -> {
        long retry = Duration.between(now, windowStart.plus(this.timeWindowDuration)).toSeconds();
        if (retry < 1) yield 1;
        yield retry;
      }
      case Allow _ -> 0L;
    };
  }
}
