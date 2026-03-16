package com.css.challenge.server;

import com.css.challenge.client.Order;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class AcceptedOrder {

    /** Order ID */
    private final String id;
    /** Food name */
    private final String name;
    /** Ideal temperature */
    private final TempType temp;
    /** Price in dollars */
    private final int price;
    /** Freshness in seconds under ideal storage conditions. */
    private final int freshness;
    /** Time order was accepted/cooked. */
    private final Instant startTime;
    /** Projected expiration time. Initially,it just the start time + freshness (or freshness / 2 if not stored ideally).
     * Later in, if we move the order from the shelf to its ideal storage, we will update it using the following formula:
     * <code>s + m + (f - 2m) = s + f - m</code>
     * where <ul>
     *     <li>s : start time</li>
     *     <li>m : move time (in seconds) from shelf to ideal storage, relative to start time</li>
     *     <li>f : original freshsness time (in seconds)</li>
     * </ul>
     */
    private Instant expirationTime;

    public AcceptedOrder(Order order, Instant startTime) {
        this.id = order.getId();
        this.name = order.getName();
        this.temp = TempType.of(order.getTemp());
        this.freshness = order.getFreshness();
        this.price = order.getPrice();
        this.startTime = startTime;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TempType getTemp() {
        return temp;
    }

    public int getPrice() {
        return price;
    }

    public int getFreshness() {
        return freshness;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setExpirationTimeForInitialStorage(StorageType storageType) {
        long expirationInMSec = (storageType.isIdealForTemp(this.temp) ? 1000L : 500L) * freshness;
        this.expirationTime = this.startTime.plusMillis(expirationInMSec); // switching to millis to avoid rounding errors
    }


    public void updateExpirationTimeForMoveToIdealStorage() {
        Duration m = Duration.between(startTime, Instant.now());
        this.expirationTime = startTime.plusSeconds(freshness).minus(m);
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }


    public boolean isExpired() {
        return Instant.now().isAfter(this.expirationTime);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AcceptedOrder that = (AcceptedOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "{id: "
                + id
                + ", start time: "
                + startTime
                + ", expiration time: "
                + expirationTime
                + ", name: "
                + name
                + ", temp: "
                + temp
                + ", price: $"
                + price
                + ", freshness:"
                + freshness
                + "}";
    }
}
