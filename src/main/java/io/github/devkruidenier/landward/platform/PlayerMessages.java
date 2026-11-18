package io.github.devkruidenier.landward.platform;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Paper/Folia use Adventure; CraftBukkit/Spigot use their native system-message transport. */
public final class PlayerMessages {
    private final NativeMessages nativeMessages;
    public PlayerMessages(Plugin plugin) {
        nativeMessages=Audience.class.isAssignableFrom(Player.class) ? null : new NativeMessages(plugin);
    }
    public void chat(Player player,Component message) { deliver(player,message,false); }
    public void actionbar(Player player,String legacy) { deliver(player,MessageText.legacy(legacy),true); }
    private void deliver(Player player,Component message,boolean overlay) {
        if(player instanceof Audience audience) {
            if(overlay)audience.sendActionBar(message);else audience.sendMessage(message);
        }else nativeMessages.send(player,message,overlay);
    }

    /** No Spigot, Paper or NMS classes appear in method signatures or constant-pool type references. */
    private static final class NativeMessages {
        private final Method fromJson;
        private final Method getHandle;
        private final Method send;
        NativeMessages(Plugin plugin) {
            try {
                String craft=plugin.getServer().getClass().getPackageName();
                ClassLoader loader=plugin.getServer().getClass().getClassLoader();
                Class<?> chat=Class.forName(craft+".util.CraftChatMessage",true,loader);
                fromJson=chat.getMethod("fromJSON",String.class);
                getHandle=Class.forName(craft+".entity.CraftPlayer",true,loader).getMethod("getHandle");
                send=getHandle.getReturnType().getMethod("sendSystemMessage",fromJson.getReturnType(),boolean.class);
            }catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported native chat transport",error);}
        }
        void send(Player player,Component message,boolean overlay) {
            try {
                Object component=fromJson.invoke(null,GsonComponentSerializer.gson().serialize(message));
                send.invoke(getHandle.invoke(player),component,overlay);
            }catch(ReflectiveOperationException error){
                Throwable cause=error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
                throw new IllegalStateException("Could not deliver player component",cause);
            }
        }
    }
}
