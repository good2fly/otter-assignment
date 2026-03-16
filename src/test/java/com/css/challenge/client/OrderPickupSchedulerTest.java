package com.css.challenge.client;

import com.css.challenge.server.OrderPickupProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OrderPickupSchedulerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderPickupSchedulerTest.class);

    private static class FixedDelayProvider implements OrderPickupDelayProvider {

        private final long[] delaysInMsec;
        private int index = 0;

        public FixedDelayProvider(long[] delaysInMsec) {
            this.delaysInMsec = Arrays.copyOf(delaysInMsec, delaysInMsec.length);
        }

        @Override
        public Duration getDelay() {
            return Duration.ofMillis(this.delaysInMsec[index++]);
        }
    }

    private static class FakeOrderProvider implements OrderPickupProvider {

        List<String> pickedupOrderIds = new ArrayList<>();

        @Override
        public void pickup(String orderId) {
            pickedupOrderIds.add(orderId);
            LOGGER.info("Order ID={} being picked up", orderId);
        }

        public List<String> getPickedupOrderIds() {
            return pickedupOrderIds;
        }
    }

    @Test
    public void testOrderPickup() throws InterruptedException {

        long[] delays = new long[] { 300L, 100L, 200L, 400L };
        FixedDelayProvider delayProvider = new FixedDelayProvider(delays);
        FakeOrderProvider orderProvider = new FakeOrderProvider();
        OrderPickupScheduler scheduler = new OrderPickupScheduler(orderProvider, delayProvider);

        for (int i = 0; i < delays.length; i++) {
            scheduler.schedulePickup("order#" + i);
        }
        scheduler.join();
        Assertions.assertIterableEquals(List.of("order#1", "order#2", "order#0", "order#3"), orderProvider.pickedupOrderIds);
    }
}
