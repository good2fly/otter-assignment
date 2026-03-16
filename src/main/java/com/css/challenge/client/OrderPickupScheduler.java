package com.css.challenge.client;

import com.css.challenge.server.OrderPickupProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrderPickupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderPickupScheduler.class);

    private final OrderPickupProvider orderPickupProvider;
    private final OrderPickupDelayProvider orderPickupDelayProvider;
    private final ScheduledExecutorService executor;

    public OrderPickupScheduler(OrderPickupProvider pickupProvider, OrderPickupDelayProvider delayProvider) {
        this.orderPickupProvider = pickupProvider;
        this.orderPickupDelayProvider = delayProvider;
        this.executor = Executors.newScheduledThreadPool(1);
    }

    public void schedulePickup(String orderId) {
        Duration pickupDelay = orderPickupDelayProvider.getDelay();
        LOGGER.info("Scheduling order pickup for order ID={} in {}msec", orderId, pickupDelay.toMillis());
        executor.schedule(() -> orderPickupProvider.pickup(orderId), pickupDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void join() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10L, TimeUnit.MINUTES);
    }

    public void shutdownNow() {
        executor.shutdownNow();
    }

}
