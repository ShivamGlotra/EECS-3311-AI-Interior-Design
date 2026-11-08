package com.smartplate.domain;

import org.junit.jupiter.api.Test;

import com.smartplate.TestData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DietRulesTest {

    @Test
    void pluralTermsMatchSingularIngredients() {
        assertTrue(DietRules.matches(new Ingredient("mushroom", "vegetable"), "mushrooms"));
        assertTrue(DietRules.matches(new Ingredient("peanut butter", "nut"), "peanuts"));
        assertTrue(DietRules.matches(new Ingredient("tomato", "vegetable"), "tomatoes"));
    }

    @Test
    void groupTermsMatchByCategoryOrMembers() {
        assertTrue(DietRules.matches(new Ingredient("cashew", "nut"), "nuts"));
        assertTrue(DietRules.matches(new Ingredient("spaghetti", "grain"), "gluten"));
        assertTrue(DietRules.matches(new Ingredient("shrimp", "seafood"), "shellfish"));
        assertTrue(DietRules.matches(new Ingredient("parmesan", "dairy"), "dairy"));
        assertFalse(DietRules.matches(new Ingredient("gluten-free spaghetti", "grain"), "gluten"));
        assertFalse(DietRules.matches(new Ingredient("corn tortilla", "grain"), "gluten"));
    }

    @Test
    void wholeWordMatchingAvoidsFalsePositives() {
        assertFalse(DietRules.matches(new Ingredient("nutmeg", "spice"), "nut"));
        assertFalse(DietRules.matches(new Ingredient("eggplant", "vegetable"), "egg"));
    }

    @Test
    void dietsAreCheckedFromIngredientCategories() {
        assertTrue(DietRules.violatesDiet(new Ingredient("chicken breast", "meat"), "vegetarian"));
        assertFalse(DietRules.violatesDiet(new Ingredient("salmon", "seafood"), "pescatarian"));
        assertTrue(DietRules.violatesDiet(new Ingredient("egg", "egg"), "vegan"));
        assertTrue(DietRules.violatesDiet(new Ingredient("honey", "sweetener"), "vegan"));
        assertTrue(DietRules.violatesDiet(new Ingredient("milk", "dairy"), "dairy-free"));
        assertTrue(DietRules.violatesDiet(new Ingredient("flour", "grain"), "gluten-free"));
    }

    @Test
    void conflictsListEveryReason() {
        UserProfile p = new UserProfile(List.of("vegan"), List.of("dairy"), List.of("butter"), 30, 2);
        assertEquals(3, DietRules.conflicts(new Ingredient("butter", "dairy"), p).size());
    }

    @Test
    void dislikingADairyFoodDoesNotBlockPlantBasedFoodsNamedAfterIt() {
        UserProfile noMilkOrButter = TestData.profile(List.of(), List.of(), List.of("milk", "butter"), 60);
        assertTrue(DietRules.conflicts(new Ingredient("coconut milk", "other"), noMilkOrButter).isEmpty());
        assertTrue(DietRules.conflicts(new Ingredient("oat milk", "other"), noMilkOrButter).isEmpty());
        assertTrue(DietRules.conflicts(new Ingredient("peanut butter", "nut"), noMilkOrButter).isEmpty());
        assertFalse(DietRules.conflicts(new Ingredient("milk", "dairy"), noMilkOrButter).isEmpty());
        assertFalse(DietRules.conflicts(new Ingredient("butter", "dairy"), noMilkOrButter).isEmpty());
    }

    @Test
    void allergiesStayStrictForFoodsNamedAfterTheAllergen() {
        UserProfile milkAllergy = TestData.profile(List.of(), List.of("milk"), List.of(), 60);
        assertFalse(DietRules.conflicts(new Ingredient("coconut milk", "other"), milkAllergy).isEmpty());
    }

    @Test
    void accentedAndPlainSpellingsMatchEachOther() {
        assertTrue(DietRules.matches(new Ingredient("jalapeno", "vegetable"), "jalapeños"));
        assertTrue(DietRules.matches(new Ingredient("jalapeño", "vegetable"), "jalapeno"));
    }
}
