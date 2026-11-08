package com.smartplate.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DaysTest {

    @Test
    void findsDayNamesInFreeText() {
        assertEquals("Tuesday", Days.findIn("Swap Tuesday for something with spinach"));
        assertEquals("Tuesday", Days.findIn("replace tue's dinner"));
        assertEquals("Friday", Days.findIn("change the friday-night meal"));
        assertNull(Days.findIn(null));
    }

    @Test
    void hyphenatedWordsThatStartLikeADayAreNotDays() {
        assertNull(Days.findIn("swap the sun-dried tomato pasta"));
        assertEquals("Wednesday", Days.findIn("swap the sun-dried tomato pasta on Wednesday"));
    }
}