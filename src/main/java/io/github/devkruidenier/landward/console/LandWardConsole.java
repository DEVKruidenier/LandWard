package io.github.devkruidenier.landward.console;

import io.github.devkruidenier.landward.LandWardPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LandWardConsole {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final int INNER_WIDTH = 54;
    private static final int LABEL_WIDTH = 22;
    private static final TextColor FRAME = TextColor.color(0xD6A84B);
    private static final TextColor LAND = TextColor.color(0xFFD166);
    private static final TextColor WARD = TextColor.color(0x55D6BE);
    private static final TextColor VERSION = TextColor.color(0xFFF3B0);

    private final LandWardPlugin plugin;

    public LandWardConsole(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void info(String message, Tone tone) {
        infoRaw(component(message, tone));
    }

    public void info(String template, Map<String, ?> placeholders, Tone valueTone) {
        infoRaw(template(template, placeholders, valueTone));
    }

    public void warning(String template, Map<String, ?> placeholders) {
        warningRaw(template(template, placeholders, Tone.ERROR));
    }

    public void bannerTop() {
        infoRaw(frame("╔" + "═".repeat(INNER_WIDTH) + "╗"));
    }

    public void bannerHeader(String version) {
        int spacing = Math.max(1, INNER_WIDTH - 2 - "LANDWARD".length() - version.length());
        Component line = Component.text("║ ", FRAME)
                .append(styled("LAND", LAND, true))
                .append(styled("WARD", WARD, true))
                .append(Component.text(" ".repeat(spacing)))
                .append(styled(version, VERSION, true))
                .append(Component.text(" ║", FRAME));
        infoRaw(colorsEnabled() ? line : Component.text("║ LANDWARD" + " ".repeat(spacing) + version + " ║"));
    }

    public void bannerDivider() {
        infoRaw(frame("╠" + "═".repeat(INNER_WIDTH) + "╣"));
    }

    public void bannerRow(String icon, String label, String value, Tone tone) {
        String fittedLabel = fit(label, LABEL_WIDTH);
        int used = 3 + fittedLabel.length() + value.length();
        int trailing = Math.max(1, INNER_WIDTH - used);

        if (!colorsEnabled()) {
            infoRaw(Component.text("║ " + icon + " " + fittedLabel + value + " ".repeat(trailing) + "║"));
            return;
        }

        Component line = Component.text("║ ", FRAME)
                .append(styled(icon, tone.color, true))
                .append(Component.space())
                .append(Component.text(fittedLabel, NamedTextColor.GRAY))
                .append(styled(value, tone.color, true))
                .append(Component.text(" ".repeat(trailing)))
                .append(Component.text("║", FRAME));
        infoRaw(line);
    }

    public void bannerBottom() {
        infoRaw(frame("╚" + "═".repeat(INNER_WIDTH) + "╝"));
    }

    public void ready(String message) {
        if (!colorsEnabled()) {
            infoRaw(Component.text("✔ " + message));
            return;
        }
        infoRaw(styled("✔ ", NamedTextColor.GREEN, true)
                .append(styled(message, NamedTextColor.GREEN, true)));
    }

    private Component component(String message, Tone tone) {
        if (!colorsEnabled()) {
            return Component.text(message);
        }

        Component component = Component.text(message, tone.color);
        return tone.bold ? component.decorate(TextDecoration.BOLD) : component;
    }

    private Component frame(String text) {
        return colorsEnabled() ? Component.text(text, FRAME) : Component.text(text);
    }

    private Component styled(String text, TextColor color, boolean bold) {
        Component component = Component.text(text, color);
        return bold ? component.decorate(TextDecoration.BOLD) : component;
    }

    private void infoRaw(Component component) {
        logComponent("info", component);
    }

    private void warningRaw(Component component) {
        logComponent("warn", component);
    }

    /**
     * Paper has a component-aware logger while Spigot exposes the standard
     * java.util.logging logger. Resolve the Paper method only at runtime so a
     * single LandWard JAR can run on both platforms.
     */
    private void logComponent(String level, Component component) {
        try {
            Object componentLogger = plugin.getClass().getMethod("getComponentLogger").invoke(plugin);
            componentLogger.getClass().getMethod(level, Component.class).invoke(componentLogger, component);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Spigot does not provide JavaPlugin#getComponentLogger().
        }

        String message = PlainTextComponentSerializer.plainText().serialize(component);
        if ("warn".equals(level)) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().info(message);
        }
    }

    private String fit(String value, int width) {
        String trimmed = value.strip();
        if (trimmed.length() >= width) {
            return trimmed.substring(0, width - 2) + "… ";
        }
        return trimmed + " ".repeat(width - trimmed.length());
    }

    private Component template(String template, Map<String, ?> placeholders, Tone valueTone) {
        if (placeholders.isEmpty()) {
            return component(template, valueTone);
        }

        if (!colorsEnabled()) {
            String rendered = template;
            for (Map.Entry<String, ?> placeholder : placeholders.entrySet()) {
                rendered = rendered.replace(
                        "{" + placeholder.getKey() + "}",
                        String.valueOf(placeholder.getValue())
                );
            }
            return Component.text(rendered);
        }

        TextComponent.Builder result = Component.text();
        Matcher matcher = PLACEHOLDER.matcher(template);
        int position = 0;
        while (matcher.find()) {
            result.append(Component.text(template.substring(position, matcher.start()), NamedTextColor.GRAY));
            Object value = placeholders.get(matcher.group(1));
            if (value == null) {
                result.append(Component.text(matcher.group(), NamedTextColor.GRAY));
            } else {
                result.append(component(String.valueOf(value), valueTone));
            }
            position = matcher.end();
        }
        result.append(Component.text(template.substring(position), NamedTextColor.GRAY));
        return result.build();
    }

    private boolean colorsEnabled() {
        return plugin.getConfig().getBoolean("console.colored-output", true);
    }

    public enum Tone {
        MUTED(NamedTextColor.DARK_GRAY, false),
        BRAND(NamedTextColor.GOLD, true),
        INFO(NamedTextColor.AQUA, false),
        SUCCESS(NamedTextColor.GREEN, false),
        WARNING(NamedTextColor.YELLOW, false),
        ERROR(NamedTextColor.RED, false);

        private final NamedTextColor color;
        private final boolean bold;

        Tone(NamedTextColor color, boolean bold) {
            this.color = color;
            this.bold = bold;
        }
    }
}
