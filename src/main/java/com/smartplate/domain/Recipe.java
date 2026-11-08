package com.smartplate.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A recipe in the repository. Immutable: serving adjustment (F07) and substitution (F08)
 * return a changed copy, so the repository's recipes are never modified.
 */
public final class Recipe {

    private final String id;
    private final String name;
    private final int cookingTime;
    private final int servings;
    private final Set<String> dietaryTags;
    private final List<RecipeIngredient> ingredients;
    private final NutritionInfo nutritionPerServing;
    private final List<String> steps;

    public Recipe(String id, String name, int cookingTime, int servings, Set<String> dietaryTags,
                  List<RecipeIngredient> ingredients, NutritionInfo nutritionPerServing, List<String> steps) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new SmartPlateException("A recipe needs an id and a name.");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.cookingTime = cookingTime;
        this.servings = servings;
        Set<String> tags = new LinkedHashSet<>();
        if (dietaryTags != null) {
            for (String tag : dietaryTags) {
                tags.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.dietaryTags = Collections.unmodifiableSet(tags);
        this.ingredients = List.copyOf(ingredients == null ? List.of() : ingredients);
        this.nutritionPerServing = nutritionPerServing; // null means "nutrition data missing"
        this.steps = List.copyOf(steps == null ? List.of() : steps);
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCookingTime() {
        return cookingTime;
    }

    public int getServings() {
        return servings;
    }

    public Set<String> getDietaryTags() {
        return dietaryTags;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    /** Nutrition for one serving, or null if the data is missing. */
    public NutritionInfo getNutritionPerServing() {
        return nutritionPerServing;
    }

    public List<String> getSteps() {
        return steps;
    }

    public boolean hasTag(String tag) {
        return dietaryTags.contains(tag.trim().toLowerCase(Locale.ROOT));
    }

    /** Returns the ingredient line for this ingredient name, or null. */
    public RecipeIngredient findIngredient(String ingredientName) {
        String key = ingredientName.trim().toLowerCase(Locale.ROOT);
        for (RecipeIngredient ri : ingredients) {
            if (ri.getName().equals(key)) {
                return ri;
            }
        }
        return null;
    }

    /** Copy of this recipe with different servings and ingredient lines. */
    public Recipe withServingsAndIngredients(int newServings, List<RecipeIngredient> newIngredients) {
        return new Recipe(id, name, cookingTime, newServings, dietaryTags, newIngredients,
                nutritionPerServing, steps);
    }

    /** Copy of this recipe with different ingredient lines. */
    public Recipe withIngredients(List<RecipeIngredient> newIngredients) {
        return withServingsAndIngredients(servings, newIngredients);
    }

    /** Copy of this recipe with different ingredient lines and steps (used by substitution). */
    public Recipe withIngredientsAndSteps(List<RecipeIngredient> newIngredients, List<String> newSteps) {
        return new Recipe(id, name, cookingTime, servings, dietaryTags, newIngredients, nutritionPerServing,
                newSteps);
    }

    /** One-line summary used in lists and AI prompts. */
    public String summary() {
        return id + " | " + name + " | " + cookingTime + " min | serves " + servings
                + (dietaryTags.isEmpty() ? "" : " | " + String.join(", ", dietaryTags));
    }

    /** Full multi-line description used by the CLI and GUI. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" [").append(id).append("]\n");
        sb.append("Cooking time: ").append(cookingTime).append(" min | Servings: ").append(servings).append('\n');
        if (!dietaryTags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", dietaryTags)).append('\n');
        }
        sb.append("Ingredients:\n");
        for (RecipeIngredient ri : ingredients) {
            sb.append("  - ").append(ri).append('\n');
        }
        if (!steps.isEmpty()) {
            sb.append("Steps:\n");
            for (int i = 0; i < steps.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(steps.get(i)).append('\n');
            }
        }
        if (nutritionPerServing != null) {
            sb.append("Per serving: ").append(nutritionPerServing).append('\n');
        }
        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Recipe && ((Recipe) o).id.equals(id)
                && ((Recipe) o).servings == servings && ((Recipe) o).ingredients.equals(ingredients);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }

    /** Builder used by the sample data and tests to keep recipe definitions readable. */
    public static final class Builder {
        private final String id;
        private final String name;
        private int cookingTime = 30;
        private int servings = 2;
        private final Set<String> tags = new LinkedHashSet<>();
        private final List<RecipeIngredient> ingredients = new ArrayList<>();
        private NutritionInfo nutrition;
        private final List<String> steps = new ArrayList<>();

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder time(int minutes) {
            this.cookingTime = minutes;
            return this;
        }

        public Builder servings(int servings) {
            this.servings = servings;
            return this;
        }

        public Builder tags(String... tags) {
            Collections.addAll(this.tags, tags);
            return this;
        }

        public Builder ingredient(String name, String category, double quantity, String unit) {
            ingredients.add(new RecipeIngredient(new Ingredient(name, category), quantity, unit));
            return this;
        }

        public Builder nutrition(double calories, double protein, double carbs, double fat) {
            this.nutrition = new NutritionInfo(calories, protein, carbs, fat);
            return this;
        }

        public Builder step(String step) {
            steps.add(step);
            return this;
        }

        public Recipe build() {
            return new Recipe(id, name, cookingTime, servings, tags, ingredients, nutrition, steps);
        }
    }
}
