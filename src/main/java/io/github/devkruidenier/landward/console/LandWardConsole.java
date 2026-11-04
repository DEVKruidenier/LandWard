package io.github.devkruidenier.landward.console;

import io.github.devkruidenier.landward.LandWardPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LandWardConsole {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    private final LandWardPlugin plugin;

    public LandWardConsole(LandWardPlugin plugin) {
        this.plugin = plugin;
    }

    public void info(String message, Tone tone) {
        plugin.getComponentLogger().info(component(message, tone));
    }

    public void info(String template, Map<String, ?> placeholders, Tone valueTone) {
        plugin.getComponentLogger().info(template(template, placeholders, valueTone));
    }

    public void warning(String template, Map<String, ?> placeholders) {
        plugin.getComponentLogger().warn(template(template, placeholders, Tone.ERROR));
    }

    private Component component(String message, Tone tone) {
        if (!colorsEnabled()) {
            return Component.text(message);
        }

        Component component = Component.text(message, tone.color);
        return tone.bold ? component.decorate(TextDecoration.BOLD) : component;
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
