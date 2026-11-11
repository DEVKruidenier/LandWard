package io.github.devkruidenier.landward.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnvilTextInputTest {

    @Test
    void usesTypedAmsterdamInsteadOfAConfirmationItemLabel() {
        String confirmationItemLabel = "Confirm";

        String captured = AnvilTextInput.capture("Amsterdam");

        assertEquals("Amsterdam", captured);
        assertNotEquals(confirmationItemLabel, captured);
    }

    @Test
    void trimsTypedTextAndRejectsEmptyText() {
        assertEquals("Rotterdam", AnvilTextInput.capture("  Rotterdam  "));
        assertNull(AnvilTextInput.capture(null));
        assertNull(AnvilTextInput.capture("\t \n"));
    }
}
