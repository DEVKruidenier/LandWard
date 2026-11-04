package io.github.devkruidenier.landward.gui;

/** Keeps the operation type for a player's native Paper anvil. */
final class RenameAnvilHolder {
    private final boolean creating;
    RenameAnvilHolder(boolean creating) { this.creating = creating; }
    boolean creating() { return creating; }
}
