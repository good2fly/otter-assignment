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

    private OrderActionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OrderActionDispatcherImpl();
    }

    @Test
    void places_order_in_ideal_storage_when_there_is_capacity() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.place(createOrder("hot-1", "hot", 12, 30));

        List<Action> actions = dispatcher.getActions();
        assertEquals(1, actions.size());
        assertAction(actions.getFirst(), "hot-1", Action.PLACE, Action.HEATER);
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
    void placing_cold_order_moves_hot_shelf_order_back_to_heater_when_possible() {
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
    void pickup_discards_expired_order_from_its_current_storage() throws InterruptedException {
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
    void pickup_of_missing_order_does_not_emit_any_action() {
        OrderProcessor processor = new OrderProcessor(dispatcher, 1, 1, 1);

        processor.pickup("missing");

        assertEquals(List.of(), dispatcher.getActions());
    }
}
