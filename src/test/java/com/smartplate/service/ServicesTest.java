package com.smartplate.service;

import com.smartplate.TestData;
import com.smartplate.domain.Ingredient;
import com.smartplate.domain.NutritionInfo;
import com.smartplate.domain.Pantry;
import com.smartplate.domain.PantryItem;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.ShoppingItem;
import com.smartplate.domain.ShoppingList;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.Substitution;
import com.smartplate.domain.UserProfile;
import com.smartplate.plan.MealPlan;
import com.smartplate.plan.PlannedMeal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesTest {

    private final RecipeRepository repo = RecipeRepository.withSampleRecipes();

    // ------------------------------------------------------------ F03
    @Test
    void searchMatchesNameIngredientAndTag() {
        assertEquals(30, repo.size());
        assertTrue(repo.search("Risotto").contains(repo.findById("r06")));
        assertTrue(repo.search("chickpeas").contains(repo.findById("r17")));
        assertTrue(repo.search("vegan lentil").contains(repo.findById("r30")));
        assertTrue(repo.search("no-such-dish").isEmpty());
        assertTrue(repo.search("  ").isEmpty());
        assertNull(repo.findById("nope"));
    }

    // ------------------------------------------------------------ F07
    @Test
    void scalerMultipliesQuantitiesAndReturnsACopy() {
        Recipe original = TestData.pasta();
        Recipe scaled = new ServingScaler().scale(original, 5);
        assertEquals(5, scaled.getServings());
        assertEquals(500, scaled.findIngredient("spaghetti").getQuantity(), 1e-9);
        assertEquals(200, original.findIngredient("spaghetti").getQuantity(), 1e-9);
        assertThrows(SmartPlateException.class, () -> new ServingScaler().scale(original, 0));
    }

    @Test
    void scalerRejectsInvalidIngredientQuantity() {
        Recipe broken = Recipe.builder("b1", "Broken").ingredient("salt", "spice", -1, "g").build();
        assertThrows(SmartPlateException.class, () -> new ServingScaler().scale(broken, 4));
    }

    // ------------------------------------------------------------ F08
    @Test
    void substitutionSkipsSubstitutesThatConflictWithProfile() {
        SubstitutionService service = SubstitutionService.withDefaultSubstitutions();
        // first option for sour cream is greek yogurt (dairy); a dairy-free user gets
        // coconut yogurt
        UserProfile dairyFree = TestData.profile(List.of("dairy-free"), List.of(), List.of(), 60);
        Substitution s = service.findSubstitute(new Ingredient("sour cream"), dairyFree);
        assertEquals("coconut yogurt", s.getSubstitute().getName());
    }

    @Test
    void applySubstitutionConvertsQuantityAndUnit() {
        SubstitutionService service = SubstitutionService.withDefaultSubstitutions();
        Recipe pasta = TestData.pasta();
        Substitution s = service.findSubstitute(new Ingredient("butter"), TestData.noRestrictions());
        Recipe updated = service.applySubstitution(pasta, s);
        assertNull(updated.findIngredient("butter"));
        assertEquals(32, updated.findIngredient("olive oil").getQuantity(), 1e-9);
        assertEquals("ml", updated.findIngredient("olive oil").getUnit());
        assertNotNull(pasta.findIngredient("butter")); // original unchanged
    }

    @Test
    void substitutionAlsoRenamesTheIngredientInTheSteps() {
        SubstitutionService service = SubstitutionService.withDefaultSubstitutions();
        UserProfile none = TestData.noRestrictions();
        Recipe rice = service.applySubstitution(repo.findById("r01"),
                service.findSubstitute(new Ingredient("soy sauce"), none));
        assertEquals("Fry the peas and carrot, add rice, coconut aminos, eggs and green onion.", rice.getSteps().get(2));
        Recipe omelette = service.applySubstitution(repo.findById("r13"),
                service.findSubstitute(new Ingredient("milk"), none));
        assertEquals("Whisk eggs with oat milk.", omelette.getSteps().get(0));
        assertTrue(repo.findById("r13").getSteps().get(0).endsWith("with milk."), "original unchanged");
    }

    @Test
    void stepsThatUseAShortNameAreRewrittenToo() {
        SubstitutionService service = SubstitutionService.withDefaultSubstitutions();
        UserProfile none = TestData.noRestrictions();
        Recipe stirFry = service.applySubstitution(repo.findById("r04"),
                service.findSubstitute(new Ingredient("chicken breast"), none));
        assertEquals("Stir-fry sliced tofu until cooked.", stirFry.getSteps().get(1));
        Recipe quesadillas = service.applySubstitution(repo.findById("r08"),
                service.findSubstitute(new Ingredient("flour tortilla"), none));
        assertEquals("Fill corn tortillas with beans, pepper and cheese.", quesadillas.getSteps().get(0));
        // the full name is in a step, so a separate "sauce" is left alone
        Recipe noodles = service.applySubstitution(repo.findById("r15"),
                service.findSubstitute(new Ingredient("soy sauce"), none));
        assertEquals("Whisk peanut butter, coconut aminos and lime juice.", noodles.getSteps().get(1));
        assertEquals("Toss noodles with sauce and sliced vegetables.", noodles.getSteps().get(2));
    }

    @Test
    void substitutionErrors() {
        SubstitutionService service = SubstitutionService.withDefaultSubstitutions();
        assertThrows(SmartPlateException.class,
                () -> service.findSubstitute(new Ingredient("saffron"), TestData.noRestrictions()));
        UserProfile allergic = TestData.profile(List.of(), List.of("soy", "oat"), List.of(), 60);
        assertThrows(SmartPlateException.class, () -> service.findSubstitute(new Ingredient("milk"), allergic));
    }

    // ------------------------------------------------------------ F09
    @Test
    void nutritionForRecipeAndPlan() {
        NutritionService service = new NutritionService();
        assertEquals(new NutritionInfo(1000, 30, 140, 36), service.calculateRecipe(TestData.pasta()));
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal("Monday", TestData.pasta(), 1));
        plan.addMeal(new PlannedMeal("Tuesday", TestData.pasta(), 3));
        assertEquals(2000, service.calculatePlan(plan).getCalories(), 1e-9);
        // the same method works on a single leaf (Composite)
        assertEquals(500, service.calculatePlan(plan.getMeal("Monday")).getCalories(), 1e-9);
        assertThrows(SmartPlateException.class, () -> service.calculatePlan(new MealPlan()));
        Recipe noData = Recipe.builder("n1", "No Data").ingredient("x", "other", 1, "g").build();
        assertThrows(SmartPlateException.class, () -> service.calculateRecipe(noData));
    }

    // ------------------------------------------------------------ F10
    @Test
    void shoppingListConsolidatesAndSubtractsPantry() {
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal("Monday", TestData.pasta(), 2)); // 200 g spaghetti, 2 garlic
        plan.addMeal(new PlannedMeal("Tuesday", TestData.pasta(), 4)); // 400 g spaghetti, 4 garlic
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("spaghetti", 0.5, "kg"));
        pantry.addItem(new PantryItem("garlic", 10, "pcs"));
        pantry.addItem(new PantryItem("butter", 1, "pcs")); // incompatible unit

        ShoppingList list = new ShoppingListService().generate(plan, pantry);
        ShoppingItem spaghetti = find(list, "spaghetti");
        assertEquals(100, spaghetti.getQuantity(), 1e-9);
        assertEquals("g", spaghetti.getUnit());
        assertNull(find(list, "garlic")); // covered by the pantry
        assertEquals(120, find(list, "butter").getQuantity(), 1e-9);
        assertTrue(list.getNotes().stream().anyMatch(n -> n.contains("butter")));
    }

    @Test
    void shoppingListRoundsCountedItemsUpToWholeNumbers() {
        Recipe lemonRice = Recipe.builder("t02", "Lemon Rice").time(15).servings(2)
                .ingredient("rice", "grain", 150, "g")
                .ingredient("lemon", "fruit", 3, "pcs")
                .nutrition(400, 8, 80, 5).build();
        MealPlan plan = new MealPlan();
        plan.addMeal(new PlannedMeal("Monday", lemonRice, 1)); // 1 serving: 1.5 lemons, 75 g rice

        ShoppingList list = new ShoppingListService().generate(plan, new Pantry());
        assertEquals(2, find(list, "lemon").getQuantity(), 1e-9); // 1.5 pcs -> 2 pcs
        assertEquals(75, find(list, "rice").getQuantity(), 1e-9); // grams are not rounded
        assertTrue(list.getNotes().stream().anyMatch(n -> n.contains("rounded up")));
    }

    @Test
    void shoppingListForEmptyPlanIsAnError() {
        assertThrows(SmartPlateException.class, () -> new ShoppingListService().generate(new MealPlan(), new Pantry()));
    }

    @Test
    void dislikingMilkStillAllowsPlantBasedMilks() {
        UserProfile noMilk = TestData.profile(List.of(), List.of(), List.of("milk"), 60);
        Substitution s = SubstitutionService.withDefaultSubstitutions().findSubstitute(new Ingredient("milk", "dairy"),
                noMilk);
        assertEquals("oat milk", s.getSubstitute().getName());
    }

    @Test
    void scalingDownToOneServingHalvesATwoServingRecipe() {
        Recipe one = new ServingScaler().scale(TestData.pasta(), 1); // pasta is written for 2
        assertEquals(1, one.getServings());
        assertEquals(100, one.findIngredient("spaghetti").getQuantity(), 1e-9);
        assertEquals(1, one.findIngredient("garlic").getQuantity(), 1e-9);
    }

    private static ShoppingItem find(ShoppingList list, String name) {
        for (ShoppingItem item : list.getItems()) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
