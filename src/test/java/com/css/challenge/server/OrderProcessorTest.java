package com.css.challenge.server;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderProcessorTest {

    private static Order createOrder(String id, String temp, int price, int freshness) {
        return new Order(id, id, temp, price, freshness);
    }

    private static void assertAction(Action action, String id, String verb, String target) {
        assertEquals(id, action.getId());
        assertEquals(verb, action.getAction());
        assertEquals(target, action.getTarget());
    }

    private OrderActionDispatcherImpl dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OrderActionDispatcherImpl();
    }

    @Test
    void places_order_in_ideal_hot_storage_when_there_is_capacity() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("hot-1", "hot", 12, 30));
        processor.pickup("hot-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(1), "hot-1", Action.PICKUP, Action.HEATER);
    }

    @Test
    void places_order_in_ideal_cold_storage_when_there_is_capacity() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 12, 30));
        processor.pickup("cold-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-1", Action.PICKUP, Action.COOLER);
    }

    @Test
    void order_cannot_be_picked_up_multiple_times() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 12, 30));
        processor.pickup("cold-1");
        processor.pickup("cold-1");
        processor.pickup("cold-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-1", Action.PICKUP, Action.COOLER);
    }

    @Test
    void places_order_in_ideal_room_storage_when_there_is_capacity() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("room-1", "room", 12, 30));
        processor.pickup("room-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "room-1", Action.PLACE, Action.SHELF);
        assertAction(actions.get(1), "room-1", Action.PICKUP, Action.SHELF);
    }

    @Test
    void falls_back_to_shelf_when_ideal_storage_is_full() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 2);

        processor.place(createOrder("hot-1", "hot", 12, 30));
        processor.place(createOrder("hot-2", "hot", 14, 30));

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(1), "hot-2", Action.PLACE, Action.SHELF);
    }

    @Test
    void placing_cold_order_moves_hot_shelf_hot_order_back_to_heater_when_possible() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("hot-1", "hot", 12, 30));
        processor.place(createOrder("hot-2", "hot", 14, 30));
        processor.pickup("hot-1");
        processor.place(createOrder("cold-1", "cold", 11, 30));
        processor.place(createOrder("cold-2", "cold", 13, 30));

        List<Action> actions = dispatcher.getActions();
        assertEquals(6, actions.size());
        assertAction(actions.get(0), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(1), "hot-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(2), "hot-1", Action.PICKUP, Action.HEATER);
        assertAction(actions.get(3), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(4), "hot-2", Action.MOVE, Action.HEATER);
        assertAction(actions.get(5), "cold-2", Action.PLACE, Action.SHELF);
    }

    @Test
    void placing_room_temperature_order_moves_shelf_cold_order_when_cooler_has_space() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 10, 30));
        processor.place(createOrder("cold-2", "cold", 11, 30));
        processor.pickup("cold-1");
        processor.place(createOrder("room-1", "room", 9, 30));

        List<Action> actions = dispatcher.getActions();
        assertEquals(5, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(2), "cold-1", Action.PICKUP, Action.COOLER);
        assertAction(actions.get(3), "cold-2", Action.MOVE, Action.COOLER);
        assertAction(actions.get(4), "room-1", Action.PLACE, Action.SHELF);
    }

    @Test
    void placing_room_temperature_order_moves_shelf_cold_order_when_both_heater_and_cooler_has_space() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 2);

        processor.place(createOrder("cold-1", "cold", 10, 30));
        processor.place(createOrder("hot-1", "hot", 20, 30));
        processor.place(createOrder("cold-2", "cold", 11, 30));
        processor.place(createOrder("hot-2", "hot", 21, 30));
        processor.pickup("cold-1");
        processor.pickup("hot-1");
        processor.place(createOrder("room-1", "room", 9, 30));

        List<Action> actions = dispatcher.getActions();
        assertEquals(8, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(2), "cold-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(3), "hot-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(4), "cold-1", Action.PICKUP, Action.COOLER);
        assertAction(actions.get(5), "hot-1", Action.PICKUP, Action.HEATER);
        assertAction(actions.get(6), "cold-2", Action.MOVE, Action.COOLER);
        assertAction(actions.get(7), "room-1", Action.PLACE, Action.SHELF);
    }

    @Test
    void discards_oldest_shelf_order_when_no_room_can_be_made() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 3);

        processor.place(createOrder("hot-1", "hot", 10, 100));
        processor.place(createOrder("hot-2", "hot", 20, 50));
        processor.place(createOrder("cold-1", "cold", 10, 100));
        processor.place(createOrder("cold-2", "cold", 20, 40));
        processor.place(createOrder("room-1", "room", 5, 5));
        processor.place(createOrder("room-2", "room", 7, 60));

        List<Action> actions = dispatcher.getActions();
        assertEquals(7, actions.size());
        assertAction(actions.get(5), "room-1", Action.DISCARD, Action.SHELF);
        assertAction(actions.get(6), "room-2", Action.PLACE, Action.SHELF);
    }

    @Test
    void discards_oldest_cold_shelf_order_when_no_room_can_be_made() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 3);

        processor.place(createOrder("hot-1", "hot", 10, 100));
        processor.place(createOrder("hot-2", "hot", 11, 15));
        processor.place(createOrder("cold-1", "cold", 10, 100));
        processor.place(createOrder("cold-2", "cold", 20, 4));
        processor.place(createOrder("room-1", "room", 5, 6));
        processor.place(createOrder("room-2", "room", 7, 60));

        List<Action> actions = dispatcher.getActions();
        assertEquals(7, actions.size());
        assertAction(actions.get(5), "cold-2", Action.DISCARD, Action.SHELF);
        assertAction(actions.get(6), "room-2", Action.PLACE, Action.SHELF);
    }

    @Test
    void pickup_discards_expired_hot_order_from_heater() throws InterruptedException {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("hot-1", "hot", 12, 1));
        Thread.sleep(1200L);
        processor.pickup("hot-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(1), "hot-1", Action.DISCARD, Action.HEATER);
    }

    @Test
    void pickup_discards_expired_cold_order_from_cooler() throws InterruptedException {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 12, 1));
        Thread.sleep(1200L);
        processor.pickup("cold-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(2, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-1", Action.DISCARD, Action.COOLER);
    }

    @Test
    void pickup_discards_expired_cold_order_from_shelf() throws InterruptedException {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 11, 10));
        processor.place(createOrder("cold-2", "cold", 22, 1));
        Thread.sleep(1200L);
        processor.pickup("cold-2");
        processor.place(createOrder("cold-3", "cold", 33, 20));

        List<Action> actions = dispatcher.getActions();
        assertEquals(4, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(2), "cold-2", Action.DISCARD, Action.SHELF);
        assertAction(actions.get(3), "cold-3", Action.PLACE, Action.SHELF);
    }

    @Test
    void pickup_of_missing_order_does_not_emit_any_action() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.pickup("missing");

        assertEquals(List.of(), dispatcher.getActions());
    }

    @Test
    void mixed_pickup_of_existing_and_missing_orders() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("cold-1", "cold", 11, 10));
        processor.place(createOrder("cold-2", "cold", 22, 1));
        processor.place(createOrder("hot-1", "hot", 33, 13));
        processor.pickup("missing1");
        processor.pickup("cold-2");
        processor.pickup("missing2");
        processor.pickup("cold-1");
        processor.pickup("missing3");
        processor.pickup("hot-1");

        List<Action> actions = dispatcher.getActions();
        assertEquals(6, actions.size());
        assertAction(actions.get(0), "cold-1", Action.PLACE, Action.COOLER);
        assertAction(actions.get(1), "cold-2", Action.PLACE, Action.SHELF);
        assertAction(actions.get(2), "hot-1", Action.PLACE, Action.HEATER);
        assertAction(actions.get(3), "cold-2", Action.PICKUP, Action.SHELF);
        assertAction(actions.get(4), "cold-1", Action.PICKUP, Action.COOLER);
        assertAction(actions.get(5), "hot-1", Action.PICKUP, Action.HEATER);
    }
}
