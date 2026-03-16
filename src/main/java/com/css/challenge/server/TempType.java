package com.css.challenge.server;

/**
 * Represent the possible temperature values for any given order.
 */
public enum TempType {

    HOT,
    COLD,
    ROOM;

    public static TempType of(String heatType) {
        return switch (heatType.toLowerCase()) {
            case "hot" -> HOT;
            case "cold" -> COLD;
            case "room" -> ROOM;
            default -> throw new IllegalArgumentException("Unknown heat type: " + heatType);
        };
    }

    public StorageType getIdealStorageType() {
        return switch (this) {
            case HOT -> StorageType.HEATER;
            case COLD -> StorageType.COOLER;
            case ROOM -> StorageType.SHELF;
        };
    }
}
