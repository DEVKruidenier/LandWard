package io.github.devkruidenier.landward.economy;

import org.bukkit.entity.Player;

public final class ExperiencePayments {

    private final int cost;

    public ExperiencePayments(int cost) {
        this.cost = Math.max(0, cost);
    }

    public boolean enabled(String mode) {
        return mode.equalsIgnoreCase("experience");
    }

    public boolean canAfford(Player player) {
        return player.getTotalExperience() >= cost;
    }

    public void withdraw(Player player) {
        player.giveExp(-cost);
    }

    public int cost() {
        return cost;
    }
}
