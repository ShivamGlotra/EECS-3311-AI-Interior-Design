package com.smartplate.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileTest {

    @Test
    void validProfileHasNoProblems() {
        UserProfile p = new UserProfile(List.of("Vegetarian"), List.of("peanuts"), List.of("mushrooms"), 30, 2);
        assertTrue(p.validate().isEmpty());
        assertTrue(p.getDietaryPreferences().contains("vegetarian"));
    }

    @Test
    void servingsAndTimeBelowOneAreRejected() {
        UserProfile p = new UserProfile(List.of(), List.of(), List.of(), 0, 0);
        List<String> problems = p.validate();
        assertEquals(2, problems.size());
    }

    @Test
    void duplicateMalformedAndUnknownEntriesAreReported() {
        UserProfile p = new UserProfile(List.of("keto"), List.of("peanuts", "Peanuts", "123"), List.of(""), 30, 2);
        List<String> problems = p.validate();
        assertTrue(problems.stream().anyMatch(s -> s.contains("Duplicate allergy")));
        assertTrue(problems.stream().anyMatch(s -> s.contains("Malformed allergy")));
        assertTrue(problems.stream().anyMatch(s -> s.contains("Empty disliked")));
        assertTrue(problems.stream().anyMatch(s -> s.contains("Unknown dietary preference 'keto'")));
    }

    @Test
    void requestConstraintsTightenWithoutChangingOriginal() {
        UserProfile p = new UserProfile(List.of(), List.of(), List.of("olives"), 45, 2);
        UserProfile q = p.withRequestConstraints(List.of("vegan"), List.of("mushrooms"), 30);
        assertEquals(30, q.getMaxCookingTime());
        assertTrue(q.getDislikedIngredients().contains("mushrooms"));
        assertTrue(q.getDietaryPreferences().contains("vegan"));
        assertTrue(p.getDietaryPreferences().isEmpty());
        assertEquals(45, p.getMaxCookingTime());
        assertEquals(1, p.getDislikedIngredients().size());
        // a looser request limit does not loosen the profile
        assertEquals(45, p.withRequestConstraints(List.of(), List.of(), 90).getMaxCookingTime());
    }

    @Test
    void entriesWithAccentedLettersAreAccepted() {
        UserProfile p = new UserProfile(List.of(), List.of(), List.of("jalapeño", "crème fraîche"), 30, 2);
        assertTrue(p.validate().isEmpty());
    }

    @Test
    void oneServingAndOneMinuteAreTheSmallestValidValues() {
        assertTrue(new UserProfile(List.of(), List.of(), List.of(), 1, 1).validate().isEmpty());
        assertEquals(2, new UserProfile(List.of(), List.of(), List.of(), 0, 0).validate().size());
    }
}
