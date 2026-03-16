package com.css.challenge.client;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public class RandomOrderPickupDelayProvider implements OrderPickupDelayProvider {

    private final Random random = new Random();
    private final Duration min;
    private final Duration max;

    public RandomOrderPickupDelayProvider(Duration lower, Duration upperBoundSec) {
        this.min = lower;
        this.max = upperBoundSec;
    }

    @Override
    public Duration getDelay() {
        return Duration.of(random.nextLong(min.toMillis(), max.toMillis() + 1L), ChronoUnit.MILLIS);
    }
}
