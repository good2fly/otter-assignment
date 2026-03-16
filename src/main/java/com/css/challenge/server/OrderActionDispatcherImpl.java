package com.css.challenge.server;

import com.css.challenge.client.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderActionDispatcherImpl implements OrderActionDispatcher {

    private final List<Action> actions = new ArrayList<>();

    @Override
    public void dispatch(Action action) {
        actions.add(action);
    }

    @Override
    public List<Action> getActions() {
        return Collections.unmodifiableList(actions);
    }
}
