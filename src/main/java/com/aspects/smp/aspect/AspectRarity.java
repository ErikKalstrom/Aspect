package com.aspects.smp.aspect;

public enum AspectRarity {
    COMMON("white"),
    UNCOMMON("green"),
    RARE("aqua"),
    EPIC("light_purple"),
    LEGENDARY("gold");

    private final String color;

    AspectRarity(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }
}
