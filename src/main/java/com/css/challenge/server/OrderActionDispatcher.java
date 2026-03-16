package com.css.challenge.server;

import com.css.challenge.client.Action;

import java.util.List;

/**
 * Dispatcher interface to abstract dispatching order events (aka actions).
 * This abstraction makes it easier to replace the implementation of the dispatcher mechanism w/o
 * touching the business class(es) using it.
 */
public interface OrderActionDispatcher {

    void dispatch(Action action);

    List<Action> getActions();
}
