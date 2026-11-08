package com.smartplate.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The user's preferences (F01): dietary preferences, allergies, disliked
 * ingredients,
 * maximum cooking time and default servings.
 *
 * <p>
 * The raw entries are kept as typed so {@link #validate()} can report
 * duplicates and
 * malformed entries. The getters return the cleaned, de-duplicated sets.
 */
public class UserProfile {

    /** Dietary preferences the system knows how to check. */
    public static final List<String> SUPPORTED_DIETS = List.of("vegetarian", "vegan", "pescatarian", "gluten-free",
            "dairy-free");

    public static final int DEFAULT_MAX_COOKING_TIME = 60;
    public static final int DEFAULT_SERVINGS = 2;

    private final List<String> dietaryPreferences;
    private final List<String> allergies;
    private final List<String> dislikedIngredients;
    private final int maxCookingTime;
    private final int defaultServings;

    public UserProfile(Collection<String> dietaryPreferences, Collection<String> allergies,
            Collection<String> dislikedIngredients, int maxCookingTime, int defaultServings) {
        this.dietaryPreferences = clean(dietaryPreferences);
        this.allergies = clean(allergies);
        this.dislikedIngredients = clean(dislikedIngredients);
        this.maxCookingTime = maxCookingTime;
        this.defaultServings = defaultServings;
    }

    /** A profile with no restrictions, 60 minutes and 2 servings. */
    public static UserProfile defaultProfile() {
        return new UserProfile(List.of(), List.of(), List.of(), DEFAULT_MAX_COOKING_TIME, DEFAULT_SERVINGS);
    }

    /**
     * Checks the profile and returns a list of problems. An empty list means the
     * profile is valid.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (defaultServings < 1) {
            problems.add("Default servings must be at least 1.");
        }
        if (maxCookingTime < 1) {
            problems.add("Maximum cooking time must be at least 1 minute.");
        }
        checkEntries("dietary preference", dietaryPreferences, problems);
        checkEntries("allergy", allergies, problems);
        checkEntries("disliked ingredient", dislikedIngredients, problems);
        for (String diet : new LinkedHashSet<>(dietaryPreferences)) {
            if (!diet.isEmpty() && !SUPPORTED_DIETS.contains(diet)) {
                problems.add("Unknown dietary preference '" + diet + "'. Supported: " + SUPPORTED_DIETS + ".");
            }
        }
        return problems;
    }

    public Set<String> getDietaryPreferences() {
        return asSet(dietaryPreferences);
    }

    public Set<String> getAllergies() {
        return asSet(allergies);
    }

    public Set<String> getDislikedIngredients() {
        return asSet(dislikedIngredients);
    }

    public int getMaxCookingTime() {
        return maxCookingTime;
    }

    public int getDefaultServings() {
        return defaultServings;
    }

    /**
     * Copy of this profile with extra diets, extra dislikes and an optional tighter
     * time limit.
     * Used by the agent to apply one-off constraints from a request ("vegetarian,
     * avoid
     * mushrooms, under 30 minutes") without changing the stored profile.
     */
    public UserProfile withRequestConstraints(Collection<String> extraDiets, Collection<String> extraDislikes,
            Integer maxTime) {
        Set<String> diets = new LinkedHashSet<>(getDietaryPreferences());
        diets.addAll(extraDiets);
        Set<String> dislikes = new LinkedHashSet<>(getDislikedIngredients());
        dislikes.addAll(extraDislikes);
        int time = maxTime == null ? maxCookingTime : Math.min(maxCookingTime, maxTime);
        return new UserProfile(diets, getAllergies(), dislikes, time, defaultServings);
    }

    private static void checkEntries(String label, List<String> entries, List<String> problems) {
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.isEmpty()) {
                problems.add("Empty " + label + " entry.");
            } else if (!entry.matches("\\p{L}[\\p{L} \\-']*")) { // any letters, so "jalapeño" is fine
                problems.add("Malformed " + label + " '" + entry + "' (use letters, spaces or hyphens).");
            } else if (!seen.add(entry)) {
                problems.add("Duplicate " + label + " '" + entry + "'.");
            }
        }
    }

    private static List<String> clean(Collection<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                out.add(v == null ? "" : v.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Set<String> asSet(List<String> values) {
        Set<String> set = new LinkedHashSet<>();
        for (String v : values) {
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String toString() {
        return "Diet: " + listOrNone(getDietaryPreferences())
                + "\nAllergies: " + listOrNone(getAllergies())
                + "\nDislikes: " + listOrNone(getDislikedIngredients())
                + "\nMax cooking time: " + maxCookingTime + " min"
                + "\nDefault servings: " + defaultServings;
    }

    private static String listOrNone(Set<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
