package io.github.devkruidenier.landward.fixture;

import io.github.devkruidenier.landward.LandWardPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

/** Isolated server automation only; never installed on a player test server. */
public final class FoliaServerProbe extends JavaPlugin {
    @Override public void onEnable() {
        var target=(LandWardPlugin)Bukkit.getPluginManager().getPlugin("LandWard");
        getCommand("foliaprobe").setExecutor((sender,command,label,args)->{
            if(args.length==1 && args[0].equals("shutdown")) target.scheduler().global(Bukkit::shutdown);
            else target.scheduler().global(()->run(target));
            return true;
        });
    }
    private void run(LandWardPlugin target) {
        try {
            String name="io.github.devkruidenier.landward.gui.FoliaRuntimeProbe";
            Class<?> anchor=Class.forName("io.github.devkruidenier.landward.gui.LandFeaturesGui",true,target.getClass().getClassLoader());
            Class<?> probe;
            try(var input=getResource(name.replace('.','/')+".class")) {
                probe=MethodHandles.privateLookupIn(anchor,MethodHandles.lookup()).defineClass(input.readAllBytes());
            }
            @SuppressWarnings("unchecked") var result=(CompletableFuture<Integer>)probe.getMethod("run",LandWardPlugin.class).invoke(null,target);
            result.orTimeout(90,java.util.concurrent.TimeUnit.SECONDS).whenComplete((count,error)->{
                if(error==null)getLogger().info("FOLIA-PROBE-PASS assertions="+count);
                else getLogger().log(java.util.logging.Level.SEVERE,"FOLIA-PROBE-FAIL",error);
            });
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"FOLIA-PROBE-FAIL",error);}
    }
}
