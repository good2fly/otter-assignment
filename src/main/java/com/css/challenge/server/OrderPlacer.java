package com.css.challenge.server;

import com.css.challenge.client.Order;

public interface OrderPlacer {

    /**
     * Place a new order. New orders get cooked instantly and always made room for in the storage,
     * according to the following business rules:
     * To ensure the food remains as fresh as possible, the system should try to store a cooked order
     * at its ideal temperature. If the ideal option is full, the order must be placed on the shelf. If the
     * shelf is also full, the system should first move an existing, cold or hot order on the shelf to either
     * the cooler or heater, respectively, if there is room. If no such move is possible, an order stored
     * on the shelf must be selected and discarded first to make room for the new order.
     *
     * @param order The order to be placed.
     *
     */
    void place(Order order);
}
