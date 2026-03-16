package com.css.challenge.server;

import com.css.challenge.client.Action;

public enum StorageType {
    HEATER,
    COOLER,
    SHELF;

    public boolean isIdealForTemp(TempType temp) {
        return (this == StorageType.HEATER && temp == TempType.HOT) ||
               (this == StorageType.COOLER && temp == TempType.COLD) ||
               (this == StorageType.SHELF && temp == TempType.ROOM);
    }

    public static StorageType of(String type) {
        return switch (type) {
            case Action.HEATER -> HEATER;
            case Action.COOLER -> COOLER;
            case Action.SHELF  -> SHELF;
            default -> throw new IllegalArgumentException("Unknown storage type: " + type);
        };
    }
}
