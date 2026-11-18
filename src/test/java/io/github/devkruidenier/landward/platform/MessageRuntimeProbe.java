package io.github.devkruidenier.landward.platform;

import io.github.devkruidenier.landward.LandWardPlugin;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs in LandWard's loader against the server's actual native component codec. */
public final class MessageRuntimeProbe {
    public static int run(LandWardPlugin plugin) throws Exception {
        AtomicInteger checks=new AtomicInteger();
        String craft=plugin.getServer().getClass().getPackageName();
        Class<?> chat=Class.forName(craft+".util.CraftChatMessage",true,plugin.getServer().getClass().getClassLoader());
        var from=chat.getMethod("fromJSON",String.class);
        var to=chat.getMethod("toJSON",from.getReturnType());
        var message=MessageText.info("LandWard ","Outdated",true,"Download latest version");
        String encoded=GsonComponentSerializer.gson().serialize(message);
        Object nativeValue=from.invoke(null,encoded);
        String roundTrip=(String)to.invoke(null,nativeValue);
        check(roundTrip.contains("run_command") && roundTrip.contains("/landward:claim update"),"Native command preserved",checks);
        check(roundTrip.contains("show_text") && roundTrip.contains("Download latest version"),"Native hover preserved",checks);
        String link=GsonComponentSerializer.gson().serialize(MessageText.download("https://github.com/DEVKruidenier/LandWard/releases/latest"));
        String nativeLink=(String)to.invoke(null,from.invoke(null,link));
        check(nativeLink.contains("open_url") && nativeLink.contains("releases/latest"),"Native URL preserved",checks);
        String denied=GsonComponentSerializer.gson().serialize(MessageText.info("LandWard ","Outdated",false,"Unused"));
        check(!((String)to.invoke(null,from.invoke(null,denied))).contains("run_command"),"No unprivileged action",checks);
        String action=GsonComponentSerializer.gson().serialize(MessageText.legacy("\u00a7cProtected by Rotterdam"));
        var nativeAction=JsonParser.parseString((String)to.invoke(null,from.invoke(null,action))).toString();
        check(nativeAction.contains("Protected by Rotterdam") && nativeAction.contains("red"),"Native actionbar text/color",checks);
        var backend=PlayerMessages.class.getDeclaredField("nativeMessages");backend.setAccessible(true);
        Object transport=backend.get(plugin.messages());
        if(transport!=null) {
            var send=transport.getClass().getDeclaredField("send");send.setAccessible(true);
            var method=(java.lang.reflect.Method)send.get(transport);
            check(method.getName().equals("sendSystemMessage") && method.getParameterTypes()[1]==boolean.class,"Native chat/overlay transport available",checks);
        }else check(net.kyori.adventure.audience.Audience.class.isAssignableFrom(org.bukkit.entity.Player.class),"Native Adventure audience",checks);
        if(Bukkit.getName().equalsIgnoreCase("CraftBukkit")) {
            boolean absent=false;
            try{Class.forName("net.md_5.bungee.api.chat.BaseComponent");}catch(ClassNotFoundException expected){absent=true;}
            check(absent,"No Spigot chat classes supplied to CraftBukkit",checks);
        }
        return checks.get();
    }
    private static void check(boolean value,String reason,AtomicInteger checks){if(!value)throw new AssertionError(reason);checks.incrementAndGet();}
}
