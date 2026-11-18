package io.github.devkruidenier.landward.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageTextTest {
    @Test void privilegedOutdatedBadgeRetainsNamespacedDownloadCommandAndHover() {
        var message=MessageText.info("\u00a76LandWard ","\u00a7cOutdated",true,"Download update");
        var badge=message.children().getLast();
        assertEquals(ClickEvent.Action.RUN_COMMAND,badge.clickEvent().action());
        assertEquals(ClickEvent.runCommand("/landward:claim update"),badge.clickEvent());
        assertNotNull(badge.hoverEvent());
        assertEquals(message,GsonComponentSerializer.gson().deserialize(GsonComponentSerializer.gson().serialize(message)));
    }
    @Test void nonAdministratorBadgeHasNoPrivilegedAction() {
        var badge=MessageText.info("LandWard ","Outdated",false,"Download update").children().getLast();
        assertNull(badge.clickEvent());assertNull(badge.hoverEvent());
    }
    @Test void downloadAndAuthorLinksRemainClickableAndColorsAreParsed() {
        String url="https://github.com/DEVKruidenier/LandWard/releases/latest";
        var link=MessageText.download(url);
        assertEquals(ClickEvent.openUrl(url),link.clickEvent());
        Component legacy=MessageText.legacy("\u00a7cProtected by Rotterdam");
        assertEquals("Protected by Rotterdam",PlainTextComponentSerializer.plainText().serialize(legacy));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,legacy.color());
        var author=MessageText.link("\u00a7bDEVKruidenier","https://github.com/DEVKruidenier");
        assertEquals(ClickEvent.Action.OPEN_URL,author.clickEvent().action());
    }
}
