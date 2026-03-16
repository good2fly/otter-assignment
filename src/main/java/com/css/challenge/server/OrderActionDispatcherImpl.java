package com.css.challenge.server;

import com.css.challenge.client.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple, {@code ConcurrentLinkedQueue}-based implementation of action dispatcher.
 * The dispatching mechanism is thread-safe.
 */
public class OrderActionDispatcherImpl implements OrderActionDispatcher {

    private final Queue<Action> actions = new ConcurrentLinkedQueue<>();

    @Override
    public void dispatch(Action action) {
        // From requirements: "The actions should be output to console in a human-readable form with adequate information to follow along in real time."
        System.out.println(action);
        actions.add(action);
    }

    public List<Action> getActions() {
        return new ArrayList<>(actions);
    }
}
