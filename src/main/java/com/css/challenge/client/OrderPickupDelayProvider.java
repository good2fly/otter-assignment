package com.css.challenge.client;

import java.time.Duration;

@FunctionalInterface
public interface OrderPickupDelayProvider {

    Duration getDelay();
}
