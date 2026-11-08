package com.smartplate.service;

import com.smartplate.domain.DietRules;
import com.smartplate.domain.Ingredient;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.RecipeIngredient;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.Substitution;
import com.smartplate.domain.UserProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F08: finds and applies a substitution from the predefined list, skipping substitutes that
 * clash with the user's allergies, dislikes or diet. No AI is involved.
 */
public class SubstitutionService {

    private final List<Substitution> substitutions;

    public SubstitutionService(List<Substitution> substitutions) {
        this.substitutions = List.copyOf(substitutions);
    }

    public static SubstitutionService withDefaultSubstitutions() {
        return new SubstitutionService(SampleData.substitutions());
    }

    /** All predefined substitutions for this ingredient (before profile filtering). */
    public List<Substitution> getSubstitutionsFor(Ingredient ingredient) {
        List<Substitution> result = new ArrayList<>();
        for (Substitution s : substitutions) {
            if (s.getOriginal().equals(ingredient)) {
                result.add(s);
            }
        }
        return result;
    }

    /** Returns the first substitute that does not conflict with the profile. */
    public Substitution findSubstitute(Ingredient ingredient, UserProfile profile) {
        List<Substitution> options = getSubstitutionsFor(ingredient);
        if (options.isEmpty()) {
            throw new SmartPlateException("No substitute is known for " + ingredient.getName() + ".");
        }
        for (Substitution s : options) {
            if (DietRules.conflicts(s.getSubstitute(), profile).isEmpty()) {
                return s;
            }
        }
        throw new SmartPlateException("Every known substitute for " + ingredient.getName()
                + " conflicts with your allergies or preferences.");
    }

    /**
     * Returns a copy of the recipe with the ingredient replaced, its quantity converted and the
     * steps updated to name the substitute.
     */
    public Recipe applySubstitution(Recipe recipe, Substitution substitution) {
        RecipeIngredient line = recipe.findIngredient(substitution.getOriginal().getName());
        if (line == null) {
            throw new SmartPlateException(recipe.getName() + " does not contain "
                    + substitution.getOriginal().getName() + ".");
        }
        String unit = substitution.getUnit() == null ? line.getUnit() : substitution.getUnit();
        RecipeIngredient replacement = new RecipeIngredient(substitution.getSubstitute(),
                line.getQuantity() * substitution.getConversionFactor(), unit);
        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (RecipeIngredient ri : recipe.getIngredients()) {
            ingredients.add(ri == line ? replacement : ri);
        }
        return recipe.withIngredientsAndSteps(ingredients,
                rewriteSteps(recipe.getSteps(), line.getName(), substitution.getSubstitute().getName()));
    }

    /**
     * Names the substitute in the steps. Steps often use a short name ("chicken" for chicken
     * breast, "cheese" for cheddar cheese, "tortillas" for flour tortilla), so if the full name
     * is not in any step, each longer word of the name is tried instead. A full-name match
     * comes first so that "soy sauce" does not also replace a separate "sauce".
     */
    static List<String> rewriteSteps(List<String> steps, String original, String substitute) {
        List<String> terms = new ArrayList<>();
        terms.add(original);
        if (!mentions(steps, original)) {
            for (String word : original.split("\\s+")) {
                if (word.length() >= 4 && mentions(steps, word)) {
                    terms.add(word);
                }
            }
        }
        List<String> rewritten = new ArrayList<>();
        for (String step : steps) {
            for (String term : terms) {
                step = replaceWord(step, term, substitute);
            }
            rewritten.add(step);
        }
        return rewritten;
    }

    private static boolean mentions(List<String> steps, String term) {
        for (String step : steps) {
            if (wordPattern(term).matcher(step).find()) {
                return true;
            }
        }
        return false;
    }

    /** Whole-word, case-insensitive; a plural "s"/"es" is kept when the substitute ends in the same word. */
    private static String replaceWord(String text, String term, String substitute) {
        Matcher m = wordPattern(term).matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String lastWord = term.substring(term.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
            boolean keepPlural = substitute.toLowerCase(Locale.ROOT).endsWith(lastWord);
            m.appendReplacement(out, Matcher.quoteReplacement(substitute + (keepPlural && m.group(1) != null ? m.group(1) : "")));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Pattern wordPattern(String term) {
        return Pattern.compile("(?i)(?<![\\p{L}-])" + Pattern.quote(term) + "(e?s)?(?![\\p{L}])");
    }
}
