package com.smartplate.presentation;

import com.smartplate.ai.MockAIModel;
import com.smartplate.facade.SmartRecipeFacade;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlannerCLITest {

    private String run(String... lines) {
        String input = String.join("\n", lines) + "\nquit\n";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MealPlannerCLI cli = new MealPlannerCLI(SmartRecipeFacade.create(new MockAIModel()),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        cli.runLoop();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void scriptedSessionCoversTheMainCommands() {
        String out = run(
                "profile set diet=vegetarian allergies=peanuts time=40",
                "pantry add olive oil 500 ml",
                "search lentil",
                "recommend 2 dinners",
                "plan weekday dinners",
                "edit swap Wednesday",
                "undo",
                "scale r09 8",
                "substitute r10 butter",
                "nutrition plan",
                "shopping",
                "trace");
        assertTrue(out.contains("Profile saved"));
        assertTrue(out.contains("olive oil: 500 ml"));
        assertTrue(out.contains("Lentil Soup"));
        assertTrue(out.contains("Recommended"));
        assertTrue(out.contains("Meal plan:"));
        assertTrue(out.contains("Undone."));
        assertTrue(out.contains("Servings: 8"));
        assertTrue(out.contains("butter -> olive oil"));
        assertTrue(out.contains("Whole plan (5 dinners)"));
        assertTrue(out.contains("Shopping list:"));
        assertTrue(out.contains("Validation"));
        assertTrue(out.contains("Bye!"));
    }

    @Test
    void errorsAreShownNotThrown() {
        String out = run("profile set servings=0", "pantry add salt 1 cup", "undo", "frobnicate", "scale r01 x");
        assertTrue(out.contains("The profile was not saved"));
        assertTrue(out.contains("Unsupported unit"));
        assertTrue(out.contains("Nothing to undo"));
        assertTrue(out.contains("Unknown command"));
        assertTrue(out.contains("expected a number"));
    }

    @Test
    void undoWithNothingToUndoDoesNotClaimSuccess() {
        String out = run("undo");
        assertTrue(out.contains("Nothing to undo"));
        assertFalse(out.contains("Undone."));
    }
}
