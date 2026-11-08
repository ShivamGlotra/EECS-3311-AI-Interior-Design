package com.smartplate.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Identity of an ingredient (name and category). Two ingredients are equal when
 * their names are equal, ignoring case.
 *
 * <p>
 * Categories used by the sample data: vegetable, fruit, grain, legume, meat,
 * seafood, dairy, egg, nut, oil, spice, condiment, herb, sweetener, other.
 */
public final class Ingredient {

    private final String name;
    private final String category;

    public Ingredient(String name, String category) {
        if (name == null || name.isBlank()) {
            throw new SmartPlateException("Ingredient name must not be empty.");
        }
        this.name = DietRules.withoutAccents(name.trim().toLowerCase(Locale.ROOT));
        this.category = category == null || category.isBlank()
                ? "other"
                : category.trim().toLowerCase(Locale.ROOT);
    }

    public Ingredient(String name) {
        this(name, "other");
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Ingredient && ((Ingredient) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
