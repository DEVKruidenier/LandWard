package io.github.devkruidenier.landward.gui;

/** Keeps native anvil text handling independent of result-item presentation. */
final class AnvilTextInput {
    private AnvilTextInput() {
    }

    /**
     * Normalises only the value reported by {@code AnvilView#getRenameText()}.
     * A null or whitespace-only field has no confirmable value.
     */
    static String capture(String renameText) {
        if (renameText == null) return null;
        String text = renameText.strip();
        return text.isEmpty() ? null : text;
    }
}
