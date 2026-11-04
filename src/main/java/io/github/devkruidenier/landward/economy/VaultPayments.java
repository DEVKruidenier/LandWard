package io.github.devkruidenier.landward.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Accesses whichever economy provider Vault has registered (for example EssentialsX Economy). */
public final class VaultPayments {

    private final double cost;

    public VaultPayments(double cost) {
        this.cost = Math.max(0.0D, cost);
    }

    public boolean isAvailable() {
        return provider() != null;
    }

    public boolean canAfford(Player player) {
        Economy provider = provider();
        return provider != null && provider.has(player, cost);
    }

    public boolean withdraw(Player player) {
        Economy provider = provider();
        if (provider == null) {
            return false;
        }
        EconomyResponse response = provider.withdrawPlayer(player, cost);
        return response.transactionSuccess();
    }

    public void refund(Player player) {
        Economy provider = provider();
        if (provider != null) {
            provider.depositPlayer(player, cost);
        }
    }

    public double cost() {
        return cost;
    }

    private Economy provider() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }
}
