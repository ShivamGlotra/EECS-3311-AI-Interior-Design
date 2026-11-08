package com.smartplate.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ingredient-level rules shared by ConstraintValidator, SubstitutionService and
 * the
 * selection strategies. Checks one ingredient against a profile's allergies,
 * dislikes
 * and dietary preferences by looking at the ingredient's name and category, not
 * at a
 * recipe's tags (report F04: "inspects each recipe's ingredient list, not just
 * its tags").
 */
public final class DietRules {

    private static final Set<String> DAIRY_WORDS = Set.of("milk", "butter", "cream", "yogurt", "cheese");

    /** Allergy/avoid terms that stand for a whole group of ingredients. */
    private static final Map<String, Set<String>> GROUP_NAMES = Map.of(
            "nut", Set.of("almond", "cashew", "walnut", "pecan", "peanut", "hazelnut", "pistachio", "pesto"),
            "gluten", Set.of("pasta", "flour", "bread", "flour tortilla", "soy sauce", "egg noodle", "couscous",
                    "breadcrumb", "pita", "spaghetti", "bulgur", "ramen", "dough", "gnocchi", "flatbread"),
            "shellfish", Set.of("shrimp", "prawn", "crab", "lobster", "mussel", "scallop"),
            "fish", Set.of("salmon", "tuna", "cod", "tilapia", "fish"),
            "soy", Set.of("soy sauce", "tofu", "edamame", "soy milk", "tempeh"),
            "egg", Set.of("egg"),
            "dairy", Set.of());

    /** Groups that also match by ingredient category. */
    private static final Map<String, String> GROUP_CATEGORY = Map.of(
            "nut", "nut",
            "dairy", "dairy",
            "egg", "egg",
            "meat", "meat",
            "seafood", "seafood");

    private DietRules() {
    }

    /**
     * "jalapeño" -> "jalapeno", "crème" -> "creme", so accented and plain spellings
     * match.
     */
    static String withoutAccents(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Returns the reasons this ingredient conflicts with the profile (empty if
     * none).
     */
    public static List<String> conflicts(Ingredient ingredient, UserProfile profile) {
        List<String> reasons = new ArrayList<>();
        for (String allergy : profile.getAllergies()) {
            if (matches(ingredient, allergy)) {
                reasons.add("contains " + ingredient.getName() + " (allergy: " + allergy + ")");
            }
        }
        for (String dislike : profile.getDislikedIngredients()) {
            if (matchesDislike(ingredient, dislike)) {
                reasons.add("contains " + ingredient.getName() + " (disliked: " + dislike + ")");
            }
        }
        for (String diet : profile.getDietaryPreferences()) {
            if (violatesDiet(ingredient, diet)) {
                reasons.add("contains " + ingredient.getName() + " (not " + diet + ")");
            }
        }
        return reasons;
    }

    /**
     * True if the ingredient falls under an allergy/dislike term such as "peanuts"
     * or "gluten".
     */
    public static boolean matches(Ingredient ingredient, String term) {
        String stem = singular(term);
        if (stem.isEmpty()) {
            return false;
        }
        String name = ingredient.getName();
        String category = ingredient.getCategory();
        if (GROUP_CATEGORY.containsKey(stem) && category.equals(GROUP_CATEGORY.get(stem))) {
            return true;
        }
        if (stem.equals("gluten") && name.contains("gluten-free")) {
            return false;
        }
        if (GROUP_NAMES.containsKey(stem)) {
            for (String member : GROUP_NAMES.get(stem)) {
                if (containsWord(name, member)) {
                    return true;
                }
            }
            return false;
        }
        return category.equals(stem) || containsWord(name, stem);
    }

    public static boolean matchesDislike(Ingredient ingredient, String term) {
        String stem = singular(term);
        if (DAIRY_WORDS.contains(stem) && !ingredient.getCategory().equals("dairy")
                && !ingredient.getName().equals(stem)) {
            return false;
        }
        return matches(ingredient, term);
    }

    /** True if the ingredient is not allowed by this dietary preference. */
    public static boolean violatesDiet(Ingredient ingredient, String diet) {
        String category = ingredient.getCategory();
        switch (diet) {
            case "vegetarian":
                return category.equals("meat") || category.equals("seafood");
            case "pescatarian":
                return category.equals("meat");
            case "vegan":
                return category.equals("meat") || category.equals("seafood") || category.equals("dairy")
                        || category.equals("egg") || ingredient.getName().contains("honey");
            case "gluten-free":
                return matches(ingredient, "gluten");
            case "dairy-free":
                return category.equals("dairy");
            default:
                return false;
        }
    }

    /**
     * Naive singular form: "mushrooms" -> "mushroom", "tomatoes" -> "tomato",
     * "berries" -> "berry".
     */
    public static String singular(String term) {
        String t = withoutAccents(term == null ? "" : term.trim().toLowerCase(Locale.ROOT));
        if (t.endsWith("ies") && t.length() > 4) {
            return t.substring(0, t.length() - 3) + "y";
        }
        if (t.endsWith("oes") && t.length() > 4) {
            return t.substring(0, t.length() - 2);
        }
        if (t.endsWith("s") && !t.endsWith("ss") && t.length() > 3) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** Whole-word match so that "nut" does not match "nutmeg" or "coconut". */
    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int i = text.indexOf(word, from);
            if (i < 0) {
                return false;
            }
            boolean startOk = i == 0 || !Character.isLetter(text.charAt(i - 1));
            int end = i + word.length();
            // allow a plural "s"/"es" after the word, e.g. "egg" in "eggs"
            boolean endOk = end == text.length() || !Character.isLetter(text.charAt(end))
                    || text.startsWith("s", end)
                            && (end + 1 == text.length() || !Character.isLetter(text.charAt(end + 1)))
                    || text.startsWith("es", end)
                            && (end + 2 == text.length() || !Character.isLetter(text.charAt(end + 2)));
            if (startOk && endOk) {
                return true;
            }
            from = i + 1;
        }
    }
}
