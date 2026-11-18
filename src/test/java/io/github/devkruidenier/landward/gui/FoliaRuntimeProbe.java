package io.github.devkruidenier.landward.gui;

import io.github.devkruidenier.landward.LandWardPlugin;
import io.github.devkruidenier.landward.claim.LandManager;
import io.github.devkruidenier.landward.model.*;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.bukkit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only code injected into LandWard's loader, never shipped in the plugin. */
public final class FoliaRuntimeProbe {
    public static CompletableFuture<Integer> run(LandWardPlugin plugin) throws Exception {
        World world=Bukkit.getWorlds().getFirst();
        var scheduler=plugin.scheduler();
        var assertions=new AtomicInteger();
        var storage=new SqliteLandStorage(plugin.getDataFolder().toPath().resolve("folia-probe-"+UUID.randomUUID()+".db"));
        storage.initialize();var manager=new LandManager(storage);manager.load();
        var contested=new ChunkPosition(world.getUID(),500,500);
        var threads=ConcurrentHashMap.<String>newKeySet();
        var work=new ArrayList<CompletableFuture<Void>>();
        // Two far-apart loaded regions execute storage operations concurrently.
        for(int cx:new int[]{0,256}) {
            work.add(world.getChunkAtAsync(cx,0,true).thenCompose(chunk -> scheduler.atChunk(world,cx,0,()->{
                check(!scheduler.folia() || Bukkit.isOwnedByCurrentRegion(world,cx,0),"Chunk owner",assertions);
                threads.add(Thread.currentThread().getName());
                try {manager.create(UUID.randomUUID(),"Region "+cx,contested);}
                catch(Exception error){throw new CompletionException(error);}
                return null;
            })));
        }
        // Prepare both chunks that the player's collision box will overlap.
        for(int cx:new int[]{256,257}) work.add(world.getChunkAtAsync(cx,0,true).thenCompose(chunk -> scheduler.atChunk(world,cx,0,()->{
            for(int x=cx*16;x<cx*16+16;x++)for(int z=7;z<=9;z++){
                world.getBlockAt(x,100,z).setType(Material.STONE,false);
                for(int y=101;y<=103;y++)world.getBlockAt(x,y,z).setType(y==103?Material.STONE:Material.AIR,false);
            }
            return null;
        })));
        LandWarp warp=new LandWarp(UUID.randomUUID(),"Boundary indoor",world.getUID(),256*16+15.95,101,8.5,137.54298F,-22.513847F);
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            try {
                check(manager.totalClaimCount()==1 && storage.load().lands().size()==1,"No competing claim overwrite",assertions);
                var reloaded=new LandManager(storage);reloaded.load();
                check(reloaded.claims().equals(manager.claims()),"Restart persistence",assertions);
                check(!threads.isEmpty(),"Region callbacks ran",assertions);
            }catch(Exception error){return CompletableFuture.<Boolean>failedFuture(error);}
            return WarpSafety.check(scheduler,world,warp);
        }).thenCompose(safe -> {
            check(safe,"Indoor chunk-edge destination",assertions);
            return scheduler.atChunk(world,257,0,()->{world.getBlockAt(257*16,101,8).setType(Material.STONE,false);return true;});
        }).thenCompose(ignored -> WarpSafety.check(scheduler,world,warp)).thenCompose(safe -> {
            check(!safe,"Neighbor chunk obstruction rejects destination",assertions);
            return scheduler.atChunk(world,257,0,()->{world.getBlockAt(257*16,101,8).setType(Material.LAVA,false);return true;});
        }).thenCompose(ignored -> WarpSafety.check(scheduler,world,warp)).thenCompose(safe -> {
            check(!safe,"Neighbor chunk lava rejects destination",assertions);
            return scheduler.atChunk(world,257,0,()->{world.getBlockAt(257*16,101,8).setType(Material.AIR,false);return true;});
        }).thenCompose(ignored -> WarpSafety.check(scheduler,world,warp)).thenCompose(safe -> {
            check(safe,"Restored clear destination",assertions);
            var absent=new LandWarp(UUID.randomUUID(),"Absent",world.getUID(),1600008,101,1600008,0,0);
            return WarpSafety.check(scheduler,world,absent);
        }).thenApply(safe -> {
            check(!safe,"Missing chunks fail closed without generation",assertions);
            plugin.getLogger().info("REGION-PROBE-THREADS "+threads);
            return assertions.get();
        }).whenComplete((value,error)->{try{storage.close();}catch(Exception ignored){}});
    }
    private static void check(boolean value,String detail,AtomicInteger assertions) {
        if(!value)throw new AssertionError(detail);assertions.incrementAndGet();
    }
}
