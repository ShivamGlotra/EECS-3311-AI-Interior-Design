package com.smartplate.service;

import com.smartplate.domain.Pantry;
import com.smartplate.domain.PantryItem;
import com.smartplate.domain.RecipeIngredient;
import com.smartplate.domain.ShoppingItem;
import com.smartplate.domain.ShoppingList;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.Units;
import com.smartplate.plan.PlanComponent;

/**
 * F10: builds the shopping list for a plan. Collects ingredients through
 * PlanComponent.getIngredients() (Composite), merges duplicates, subtracts what
 * is in the
 * pantry, and keeps only what is missing.
 */
public class ShoppingListService {

    public ShoppingList generate(PlanComponent plan, Pantry pantry) {
        if (plan == null || plan.getIngredients().isEmpty()) {
            throw new SmartPlateException("The meal plan is empty; there is nothing to shop for.");
        }

        // 1. required ingredients, consolidated
        ShoppingList required = new ShoppingList();
        for (RecipeIngredient ri : plan.getIngredients()) {
            if (!ri.hasValidQuantity()) {
                required.addNote("Skipped " + ri.getName() + ": missing or invalid quantity.");
                continue;
            }
            required.addItem(new ShoppingItem(ri.getIngredient(), ri.getQuantity(), ri.getUnit()));
        }
        required.consolidate();

        // 2. subtract the pantry
        ShoppingList result = new ShoppingList();
        for (String note : required.getNotes()) {
            result.addNote(note);
        }
        for (ShoppingItem item : required.getItems()) {
            PantryItem have = pantry == null ? null : pantry.findItem(item.getName());
            if (have == null) {
                result.addItem(item);
            } else if (!Units.compatible(have.getUnit(), item.getUnit())) {
                result.addItem(item);
                result.addNote("Pantry has " + have + ", which cannot be compared with "
                        + item.getUnit() + "; the full amount is listed.");
            } else {
                double missing = Units.toBase(item.getQuantity(), item.getUnit())
                        - Units.toBase(have.getQuantity(), have.getUnit());
                if (missing > 1e-9) {
                    result.addItem(new ShoppingItem(item.getIngredient(), missing,
                            Units.baseUnit(Units.family(item.getUnit()))));
                }
            }
        }
        result.consolidate();
        if (result.roundUpQuantities()) {
            result.addNote("Counted items (pcs) have been rounded up to the next whole number.");
        }
        return result;
    }
}
