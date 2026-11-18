package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.command.LandCommand;
import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;

/** Real Bukkit inventories/click handlers, isolated claims and a synthetic viewer; no teleport allowed. */
public final class ChunkMapServerProbe {
    private Inventory top;
    private final boolean[] permission = {true};
    private final UUID owner = UUID.randomUUID();
    private Location location;
    private Player player;
    private int checks;
    public static int run(LandWardPlugin plugin) throws Exception { return new ChunkMapServerProbe().verify(plugin); }
    private int verify(LandWardPlugin plugin) throws Exception {
        location = new Location(Bukkit.getWorlds().getFirst(), 8, 80, 8);
        var bottom = Bukkit.createInventory(null, 36);
        player = (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch(method.getName()) {
            case "getUniqueId" -> owner; case "getName" -> "MapProbe"; case "getWorld" -> location.getWorld();
            case "getLocation" -> location.clone(); case "getLocale" -> "en_us";
            case "hasPermission", "isOp" -> permission[0];
            case "openInventory" -> { top = (Inventory)args[0]; yield null; }
            case "getInventory" -> null;
            case "teleport", "teleportAsync" -> throw new AssertionError("Map must never teleport");
            case "closeInventory" -> null; case "hashCode" -> owner.hashCode(); case "equals" -> proxy == args[0];
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        var previous = new HashMap<String,Object>();
        for (String path : List.of("claims.disabled-worlds","claims.maximum-chunks-per-land","claims.map-navigation-limit","integrations.vanilla-spawn-protection.enabled","payments.mode")) previous.put(path, plugin.getConfig().get(path));
        Files.createDirectories(plugin.getDataFolder().toPath().resolve("probe"));
        try (var storage = new SqliteLandStorage(plugin.getDataFolder().toPath().resolve("probe").resolve(UUID.randomUUID()+".db"))) {
            plugin.getConfig().set("claims.disabled-worlds", List.of());
            plugin.getConfig().set("claims.maximum-chunks-per-land", 2);
            plugin.getConfig().set("claims.map-navigation-limit", 30);
            plugin.getConfig().set("integrations.vanilla-spawn-protection.enabled", false);
            plugin.getConfig().set("payments.mode", "free");
            storage.initialize(); var lands = new LandManager(storage); lands.load();
            var first = new ChunkPosition(location.getWorld().getUID(),0,0);
            var own = lands.create(owner,"Map Probe",first).land();
            var gui = new LandMapGui(plugin, lands);
            var listener = new LandMapListener(plugin, new LandCommand(plugin, lands), lands);
            gui.open(player);
            check(top.getItem(45).getType() == Material.ARROW && top.getItem(53).getType() == Material.BARRIER,"Footer edges");
            check(GuiLayout.action(top,49) == -1,"Offset information is not a button");
            for (int slot : new int[]{46,52}) check(top.getItem(slot).getType()==Material.BLACK_STAINED_GLASS_PANE
                    && GuiLayout.action(top,slot)==-1,"Unused footer slots are black and blocked");
            check(holder().chunksBySlot().keySet().stream().allMatch(slot->slot<45),"Bottom row contains no chunk targets");
            for (int slot : new int[]{47,48,50,51}) check(GuiLayout.action(top,slot)==slot,"Direction stays in footer");
            click(listener,bottom,51); click(listener,bottom,51);
            click(listener,bottom,47); click(listener,bottom,48); click(listener,bottom,50); click(listener,bottom,51);
            check(holder().viewport().offsetX()==2,"Right moves east twice");
            check(holder().chunksBySlot().get(21).equals(new ChunkPosition(first.worldId(),1,0)),"Shifted claim target");
            click(listener,bottom,21);
            check(lands.claimCount(own.id())==2 && holder().viewport().offsetX()==2,"Claim targets remote cell and retains viewport");
            click(listener,bottom,22);
            check(lands.claimCount(own.id())==2,"Player claim limit respected");
            click(listener,bottom,21); check(top.getHolder() instanceof UnclaimConfirmationHolder,"Remote unclaim confirmation");
            click(listener,bottom,15); check(holder().viewport().offsetX()==2,"Cancel retains viewport");
            click(listener,bottom,21); click(listener,bottom,11);
            check(lands.claimCount(own.id())==1 && holder().viewport().offsetX()==2,"Confirmed unclaim retains viewport");
            for(int i=0;i<40;i++) click(listener,bottom,51);
            check(holder().viewport().offsetX()==30 && top.getItem(51).getType()==Material.BLACK_STAINED_GLASS_PANE,"Bounded and disabled at east edge");
            check(Arrays.stream(top.getContents()).noneMatch(item -> item != null && item.getType()==Material.COMPASS),"No false remote compass");
            plugin.getConfig().set("claims.map-navigation-limit",5);
            click(listener,bottom,22); check(holder().viewport().offsetX()==5 && lands.claimCount(own.id())==1,"Reduced limit clamps stale map before edits");
            plugin.getConfig().set("claims.map-navigation-limit",30);
            var other = lands.create(UUID.randomUUID(),"Remote Land",new ChunkPosition(first.worldId(),100,100)).land();
            gui.openAdmin(player,other.id()); click(listener,bottom,51);
            check(holder().viewport().center().x()==101 && holder().restrictedLandId().equals(other.id()),"Remote admin selection retained");
            click(listener,bottom,22); check(lands.claimCount(other.id())==2,"Admin adds chunk to selected land only");
            click(listener,bottom,22);
            check(top.getHolder() instanceof UnclaimConfirmationHolder,"Admin remote confirmation");
            var disputed=new ChunkPosition(first.worldId(),101,100);
            lands.adminUnclaim(disputed);
            var replacement=lands.create(UUID.randomUUID(),"Replacement Land",disputed).land();
            click(listener,bottom,11);
            check(lands.landAt(disputed).orElseThrow().id().equals(replacement.id()),"Stale confirmation cannot remove a different land");
            permission[0]=false; var prior=top; click(listener,bottom,51); check(top==prior,"Revoked admin permission cannot move"); permission[0]=true;
            gui.openAdminClaims(player); click(listener,bottom,50); click(listener,bottom,22);
            check(lands.landAt(new ChunkPosition(first.worldId(),0,1)).isPresent(),"Safe Zone claims displayed chunk");
            check(holder().viewport().offsetZ()==1,"Safe Zone viewport retained");
            if(Bukkit.getWorlds().size()>1) {
                gui.open(player); var old=top; var oldWorld=location.getWorld(); location.setWorld(Bukkit.getWorlds().get(1));
                click(listener,bottom,51); check(top==old,"Player world change invalidates old map"); location.setWorld(oldWorld);
                gui.openAdminClaims(player);
            }
            plugin.getConfig().set("claims.disabled-worlds",List.of(location.getWorld().getName()));
            prior=top; click(listener,bottom,51); check(top==prior,"Disabled world blocks movement");
        } finally { previous.forEach((key,value)->plugin.getConfig().set(key,value)); GuiLayout.forget(top); }
        return checks;
    }
    private LandMapHolder holder() { return (LandMapHolder)top.getHolder(); }
    private void click(LandMapListener listener, Inventory bottom, int slot) {
        Inventory current=top;
        int raw = -1;
        for (int index=0;index<current.getSize();index++) if (GuiLayout.action(current,index)==slot) { raw=index; break; }
        if (raw<0) throw new AssertionError("Missing action " + slot);
        var view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,method,args)->switch(method.getName()) {
            case "getTopInventory" -> current; case "getBottomInventory" -> bottom; case "getPlayer" -> player;
            case "getType" -> InventoryType.CHEST; case "getInventory" -> (int)args[0]<current.getSize()?current:bottom;
            case "convertSlot" -> args[0]; case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "getItem" -> current.getItem((int)args[0]); default -> null;
        });
        var event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,raw,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        listener.onClick(event); check(event.isCancelled(),"Every click cancelled");
    }
    private void check(boolean condition,String detail) { checks++; if(!condition) throw new AssertionError(detail); }
}
