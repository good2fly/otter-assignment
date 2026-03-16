package com.css.challenge.server;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * The {@code OrderProcessor} is responsible for the main domain logic:
 * <ul>
 *     <li>Maintaining invariants</li>
 *     <li>Placing an order and ensuring storage capacity</li>
 *     <li>Picking up an order and discarding if expired</li>
 * </ul>
 * This implementation is thread-safe.
 */
public class OrderProcessor implements OrderPlacer, OrderPickupProvider {

    /** Tuple to hold the removed order and the storage type it was removed from. */
    private record RemovedOrder(AcceptedOrder order, StorageType storageType) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderProcessor.class);

    /**
     * Comparator used to select which order to discard from the shelf.
     * The primary basis is the expiration time, as we discard expired orders anyway, and if that's equal, we discard the
     * cheaper order, if all equal use order ID to keep ordering stable.
     */
    private final static Comparator<AcceptedOrder> ORDER_DISCARD_COMPARATOR = Comparator.comparing(AcceptedOrder::getExpirationTime)
                                                                                        .thenComparing(AcceptedOrder::getPrice)
                                                                                        .thenComparing(AcceptedOrder::getId);

    private final OrderActionDispatcher dispatcher;
    private final int coldCapacity;
    private final int hotCapacity;
    private final int shelfCapacity;
    /** Lock to protect atomicity of top-level operations. */
    private final ReentrantLock lock = new ReentrantLock();
    /** General-purpose order ID -> order lookup map. */
    private final Map<String, AcceptedOrder> orderLookup = new HashMap<>();
    private final SortedSet<AcceptedOrder> cooler = new TreeSet<>(ORDER_DISCARD_COMPARATOR);
    private final SortedSet<AcceptedOrder> heater = new TreeSet<>(ORDER_DISCARD_COMPARATOR);
    /** Section of the shelf that holds hot orders. */
    private final SortedSet<AcceptedOrder> shelfHot    = new TreeSet<>(ORDER_DISCARD_COMPARATOR);
    /** Section of the shelf that holds cold orders. */
    private final SortedSet<AcceptedOrder> shelfCold   = new TreeSet<>(ORDER_DISCARD_COMPARATOR);
    /** Section of the shelf that holds room temperature orders. */
    private final SortedSet<AcceptedOrder> shelfRoom   = new TreeSet<>(ORDER_DISCARD_COMPARATOR);

    public OrderProcessor(OrderActionDispatcher dispatcher, int hotCapacity, int coldCapacity, int shelfCapacity) {
        if (hotCapacity <= 0 || coldCapacity <= 0 || shelfCapacity <= 0) {
            throw new IllegalArgumentException("Storage capacity must be greater than zero for all storage types");
        }
        this.dispatcher = dispatcher;
        this.coldCapacity = coldCapacity;
        this.hotCapacity = hotCapacity;
        this.shelfCapacity = shelfCapacity;
    }

    @Override
    public void place(Order order) {
        LOGGER.info("Processing order {}", order);
        validateOrder(order);
        try {
            lock.lock();
            placeOrder(order);
        } catch (Exception e) {
            LOGGER.error("Error processing order {}", order, e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void validateOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (order.getId() == null || order.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Order id cannot be empty");
        }
        if (order.getName() == null || order.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Order name cannot be empty");
        }
        if (order.getTemp() == null || order.getTemp().trim().isEmpty()) {
            throw new IllegalArgumentException("Order temp cannot be empty");
        }
        if (order.getPrice() < 0) {
            throw new IllegalArgumentException("Order price cannot be negative");
        }
        if (order.getFreshness() <= 0) {
            throw new IllegalArgumentException("Order freshness must be greater than zero");
        }
    }

    @Override
    public void pickup(String orderId) {
        LOGGER.info("Pick up order ID={}", orderId);
        try {
            lock.lock();
            removeOrder(orderId).ifPresentOrElse(
                    // assuming 'target' in pickup action means the storage the order was picked up from
                    removedOrder -> dispatcher.dispatch(new Action(Instant.now(),
                                                        orderId,
                                                        removedOrder.order.isExpired() ? Action.DISCARD : Action.PICKUP,
                                                        removedOrder.storageType.toActionString())),
                    () -> LOGGER.warn("No order found with ID={} for pickup", orderId)
            );
        } catch (Exception e) {
            LOGGER.error("Error processing pickup for order ID={}", orderId, e);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void placeOrder(Order order) {
        Instant now = Instant.now();
        AcceptedOrder acceptedOrder = new AcceptedOrder(order, now); // now is both accepted and cooked time as orders are cooked instantly
        StorageType idealStorageType = acceptedOrder.getTemp().getIdealStorageType();
        StorageType actualStorageType = findStorageForType(idealStorageType);
        acceptedOrder.setExpirationTimeForInitialStorage(actualStorageType);
        storeOrder(acceptedOrder, actualStorageType);
        dispatcher.dispatch(new Action(now, order.getId(), Action.PLACE, actualStorageType.toActionString()));
    }

    private StorageType findStorageForType(StorageType idealStorageType) {

        return switch (idealStorageType) {
            case HEATER -> makeRoomForHot();
            case COOLER -> makeRoomForCold();
            case SHELF  -> makeRoomForRoomTemp();
        };
    }

    private StorageType makeRoomForHot() {
        // Try ideal storage
        if (!isHeaterFull()) {
            return StorageType.HEATER;
        }
        // Try shelf
        if (!isShelfFull()) {
            return StorageType.SHELF;
        }
        // Since we already know heater is full, we could only possibly move cold items into the cooler
        if (tryMovingOneColdOrderToCooler()) {
            return StorageType.SHELF;
        }
        // Last resort: discard an existing order from shelf
        discardOneShelfItem();
        return StorageType.SHELF;
    }

    private StorageType makeRoomForCold() {
        if (!isCoolerFull()) {
            return StorageType.COOLER;
        }
        if (!isShelfFull()) {
            return StorageType.SHELF;
        }
        // Since we already know cooler is full, we could only possibly move hot items into the heater
        if (tryMovingOneHotOrderToHeater()) {
            return StorageType.SHELF;
        }
        discardOneShelfItem();
        return StorageType.SHELF;
    }

    private StorageType makeRoomForRoomTemp() {
        if (!isShelfFull()) {
            return StorageType.SHELF;
        }
        if (!isCoolerFull() && !shelfCold.isEmpty()) {
            moveOneOrderToIdealStorage(shelfCold, cooler, StorageType.COOLER);
            return StorageType.SHELF;
        }
        if (tryMovingOneHotOrderToHeater()) {
            return StorageType.SHELF;
        }
        discardOneShelfItem();
        return StorageType.SHELF;
    }

    private boolean tryMovingOneHotOrderToHeater() {
        if (!isHeaterFull() && !shelfHot.isEmpty()) {
            moveOneOrderToIdealStorage(shelfHot, heater, StorageType.HEATER);
            return true;
        }
        return false;
    }

    private boolean tryMovingOneColdOrderToCooler() {
        if (!isCoolerFull() && !shelfCold.isEmpty()) {
            moveOneOrderToIdealStorage(shelfCold, cooler, StorageType.COOLER);
            return true;
        }
        return false;
    }

    private void discardOneShelfItem() {
        AcceptedOrder oldestHot  = shelfHot.isEmpty() ? null : shelfHot.first();
        AcceptedOrder oldestCold = shelfCold.isEmpty() ? null : shelfCold.first();
        AcceptedOrder oldestRoom = shelfRoom.isEmpty() ? null : shelfRoom.first();
        AcceptedOrder oldestOrder = Stream.of(oldestHot, oldestCold, oldestRoom)
                .filter(Objects::nonNull)
                .sorted(ORDER_DISCARD_COMPARATOR)
                .findFirst()
                .get(); // we know at least one of these is not null, as shelf is known to be full, so get() is safe
        removeOrder(oldestOrder.getId());
        dispatcher.dispatch(new Action(Instant.now(), oldestOrder.getId(), Action.DISCARD, Action.SHELF));
    }

    private void moveOneOrderToIdealStorage(SortedSet<AcceptedOrder> from, SortedSet<AcceptedOrder> to, StorageType toStorageType) {
        // Removing the freshest one from the shelf to try to save it. Moving an old (possibly expired) order can result in waste.
        AcceptedOrder order = from.removeLast();
        LOGGER.info("Moving order {} to {}", order,  toStorageType);
        Instant expBefore = order.getExpirationTime();
        order.updateExpirationTimeForMoveToIdealStorage();
        LOGGER.info("Expiration time changed for order ID={} from {} to {}", order.getId(), expBefore, order.getExpirationTime());
        to.add(order);
        dispatcher.dispatch(new Action(Instant.now(), order.getId(), Action.MOVE, toStorageType.toActionString()));
    }

    private Optional<RemovedOrder> removeOrder(String orderId) {
        AcceptedOrder order = orderLookup.remove(orderId);
        if (order == null) {
            return Optional.empty();
        }
        // - Hot orders can only be in the heater or hot section of the shelf
        // - Cold orders can only be in the cooler or cold section of the shelf
        // - Room temp orders can only be on room-temp section of the shelf
        StorageType removedFrom = switch (order.getTemp().getIdealStorageType()) {
            case HEATER -> {
                if (heater.remove(order)) yield StorageType.HEATER;
                if (shelfHot.remove(order)) yield StorageType.SHELF;
                // Protect invariants
                throw new IllegalStateException("Hot order ID=" + orderId + " not found in either heater, or (hot) shelf.");
            }
            case COOLER -> {
                if (cooler.remove(order)) yield StorageType.COOLER;
                if (shelfCold.remove(order)) yield StorageType.SHELF;
                throw new IllegalStateException("Cold order ID=" + orderId + " not found in either cooler, or (cold) shelf.");
            }
            case SHELF -> {
                if (shelfRoom.remove(order)) yield StorageType.SHELF;
                throw new IllegalStateException("Room temp order ID=" + orderId + " not found on shelf.");
            }
        };
        return Optional.of(new RemovedOrder(order, removedFrom));
    }

    /**
     * Store an order in the specified storage type. The move is assumed to be valid (i.e. capacity already verified).
     *
     * @param order The order to be stored
     * @param storageType The type of storage
     */
    private void storeOrder(AcceptedOrder order, StorageType storageType) {
        orderLookup.put(order.getId(), order);
        switch (storageType) {
            case HEATER -> heater.add(order);
            case COOLER -> cooler.add(order);
            case SHELF -> {
                switch (order.getTemp().getIdealStorageType()) {
                    case HEATER -> shelfHot.add(order);
                    case COOLER -> shelfCold.add(order);
                    case SHELF -> shelfRoom.add(order);
                }
            }
        }
    }

    private boolean isHeaterFull() {
        return heater.size() >= hotCapacity;
    }

    private boolean isCoolerFull() {
        return cooler.size() >= coldCapacity;
    }

    private boolean isShelfFull() {
        return shelfHot.size() + shelfCold.size() + shelfRoom.size() >= shelfCapacity;
    }
}
