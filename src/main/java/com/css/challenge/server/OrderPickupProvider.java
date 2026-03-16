package com.css.challenge.server;

public interface OrderPickupProvider {

    /**
     * Pick up an order specified by its order ID.
     * If the order is found and has not expired yet, a 'pickup' action is added to the action ledger.
     * If the order is already expired (exceeded its freshness limit), a 'discard' action is added.
     * If no such order exists, no action is logged.
     *
     * @param orderId the order ID
     */
    void pickup(String orderId);
}
