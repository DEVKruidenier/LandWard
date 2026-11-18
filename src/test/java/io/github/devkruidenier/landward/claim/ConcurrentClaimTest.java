package io.github.devkruidenier.landward.claim;

import io.github.devkruidenier.landward.model.ChunkPosition;
import io.github.devkruidenier.landward.storage.sqlite.SqliteLandStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentClaimTest {
    @TempDir Path directory;
    @Test void concurrentEnvironmentChangesPersistAndFormerOwnerCannotChangeSettings() throws Exception {
        try(var storage=new SqliteLandStorage(directory.resolve("settings.db"));var pool=Executors.newFixedThreadPool(8)) {
            storage.initialize();var manager=new LandManager(storage);manager.load();
            UUID owner=UUID.randomUUID();
            var land=manager.create(owner,"Settings",new ChunkPosition(UUID.randomUUID(),0,0)).land();
            var initial=manager.features(land.id()).environment();
            var work=new ArrayList<Future<?>>();
            for(var rule:io.github.devkruidenier.landward.model.EnvironmentSettings.Rule.values()) {
                work.add(pool.submit(()->{manager.toggleEnvironment(land.id(),owner,false,rule);return null;}));
            }
            for(var done:work)done.get(10,TimeUnit.SECONDS);
            for(var rule:io.github.devkruidenier.landward.model.EnvironmentSettings.Rule.values())
                assertEquals(!initial.allows(rule),manager.features(land.id()).environment().allows(rule));
            var persisted=new LandManager(storage);persisted.load();
            assertEquals(manager.features(land.id()),persisted.features(land.id()));
            manager.transferOwnership(land.id(),UUID.randomUUID());
            var before=manager.features(land.id());
            manager.toggleEnvironment(land.id(),owner,false,io.github.devkruidenier.landward.model.EnvironmentSettings.Rule.FIRE_SPREAD);
            assertEquals(before,manager.features(land.id()));
        }
    }
    @Test void twoRegionsCannotCreateOverlappingClaimsOrLoseDurableUpdates() throws Exception {
        try(var storage=new SqliteLandStorage(directory.resolve("concurrent.db"));var pool=Executors.newFixedThreadPool(8)) {
            storage.initialize();var manager=new LandManager(storage);manager.load();
            var world=UUID.randomUUID();var contested=new ChunkPosition(world,10,10);
            CountDownLatch start=new CountDownLatch(1);
            var work=new ArrayList<Future<LandManager.CreateResult>>();
            for(int i=0;i<16;i++) {final int n=i;work.add(pool.submit(()->{start.await();return manager.create(UUID.randomUUID(),"Race "+n,contested);}));}
            start.countDown();int successes=0;
            for(var result:work)if(result.get(10,TimeUnit.SECONDS).status()==LandManager.CreateStatus.CREATED)successes++;
            assertEquals(1,successes);assertEquals(1,storage.load().lands().size());assertEquals(1,storage.load().claims().size());
            var first=manager.landAt(contested).orElseThrow();
            var edits=new ArrayList<Future<?>>();
            for(int i=1;i<=8;i++){final int offset=i;edits.add(pool.submit(()->{manager.claim(first.ownerId(),new ChunkPosition(world,10+offset,10),-1,false);return null;}));}
            for(var edit:edits)edit.get(10,TimeUnit.SECONDS);
            assertEquals(9,manager.claimCount(first.id()));
            var restarted=new LandManager(storage);restarted.load();assertEquals(manager.claims(),restarted.claims());
        }
    }
}
