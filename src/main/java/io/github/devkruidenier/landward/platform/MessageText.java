package io.github.devkruidenier.landward.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Shared message contents; platform delivery never changes their click/hover semantics. */
public final class MessageText {
    private MessageText() {}
    public static Component legacy(String text) { return LegacyComponentSerializer.legacySection().deserialize(text); }
    public static Component info(String header, String status, boolean offerUpdate, String hint) {
        Component badge=legacy(status);
        if(offerUpdate) badge=badge.clickEvent(ClickEvent.runCommand("/landward:claim update"))
                .hoverEvent(HoverEvent.showText(legacy(hint)));
        return legacy(header).append(badge);
    }
    public static Component link(String label, String url) {
        return legacy(label).clickEvent(ClickEvent.openUrl(url));
    }
    public static Component download(String url) { return Component.text(url,NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url)); }
}
