package com.smartplate.agent;

import com.smartplate.domain.SmartPlateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartplate.domain.UserProfile;
import com.smartplate.service.RecipeRepository;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestConstraintsTest {

    @Test
    void parsesTheReportExampleRequest() {
        RequestConstraints c = RequestConstraints.parse(
                "Plan dinners for this week, use some of my pantry items, avoid mushrooms, keep meals under 30 minutes");
        assertEquals(30, c.getMaxTime());
        assertEquals(List.of("mushrooms"), c.getAvoid());
        assertEquals(7, c.planDays());
    }

    @Test
    void parsesDaysCountAndServings() {
        assertEquals(5, RequestConstraints.parse("weekday dinners please").planDays());
        assertEquals(6, RequestConstraints.parse("plan 6 dinners").planDays());
        assertEquals(4, RequestConstraints.parse("give me 4 vegetarian recipes").recommendationCount(3));
        assertEquals(3, RequestConstraints.parse("something quick").recommendationCount(3));
        assertEquals(4, RequestConstraints.parse("dinners for 4 people").servings(2));
        assertNull(RequestConstraints.parse("no rush").getMaxTime());
    }

    @Test
    void rejectsPlansOutsideFiveToSevenDays() {
        assertThrows(SmartPlateException.class, () -> RequestConstraints.parse("plan 3 days").planDays());
        assertThrows(SmartPlateException.class, () -> RequestConstraints.parse("plan 10 days").planDays());
    }

    @Test
    void dietsNamedInTheRequestBecomeHardConstraints() {
        RequestConstraints c = RequestConstraints.parse("swap Tuesday for something vegetarian and gluten-free");
        assertEquals(List.of("vegetarian", "gluten-free"), c.getDiets());
        assertEquals(List.of(), RequestConstraints.parse("non-vegan is fine").getDiets());
    }

    @Test
    void ignoresNonIngredientWordsAfterNo() {
        assertEquals(List.of("dairy"), RequestConstraints.parse("no more than 20 min, no dairy").getAvoid());
    }

    @Test
    void avoidReadsMultiWordIngredientsAndLists() {
        assertEquals(List.of("red onion"),
                RequestConstraints.parse("plan 5 dinners, avoid red onion, under 30 minutes").getAvoid());
        assertEquals(List.of("mushrooms", "olives"), RequestConstraints.parse("avoid mushrooms and olives").getAvoid());
        assertEquals(List.of("red onions", "garlic"),
                RequestConstraints.parse("no red onions or garlic please").getAvoid());
        assertEquals(List.of("mushrooms"), RequestConstraints.parse("skip the mushrooms").getAvoid());
        assertEquals(List.of("sun-dried tomatoes"), RequestConstraints.parse("without sun-dried tomatoes").getAvoid());
        assertEquals(List.of(), RequestConstraints.parse("quick no-cook dinners").getAvoid());
    }

    @Test
    void avoidingRedOnionDoesNotBlockOtherRedOrOnionIngredients() {
        RecipeRepository repo = RecipeRepository.withSampleRecipes();
        UserProfile effective = RequestConstraints.parse("avoid red onion").applyTo(UserProfile.defaultProfile());
        ConstraintValidator validator = new ConstraintValidator();
        assertFalse(validator.validateRecipe(repo.findById("r12"), effective).isValid()); // Greek salad: red onion
        assertTrue(validator.validateRecipe(repo.findById("r30"), effective).isValid()); // dal: red lentils, onion
    }

    @Test
    void avoidAcceptsAccentedWords() {
        assertEquals(List.of("jalapeños"), RequestConstraints.parse("no jalapeños please").getAvoid());
    }

    @Test
    void planLengthLimitsAreExactlyFiveAndSevenDays() {
        assertEquals(5, RequestConstraints.parse("plan 5 dinners").planDays());
        assertEquals(7, RequestConstraints.parse("plan 7 days").planDays());
        assertThrows(SmartPlateException.class, () -> RequestConstraints.parse("plan 4 dinners").planDays());
        assertThrows(SmartPlateException.class, () -> RequestConstraints.parse("plan 8 dinners").planDays());
    }

    @Test
    void decimalDaysRoundToTheNearestDayWithHalvesTowardFiveToSeven() {
        assertEquals(5, RequestConstraints.parse("plan 4.5 dinners").planDays());
        assertEquals(6, RequestConstraints.parse("plan 5.5 days").planDays());
        assertEquals(7, RequestConstraints.parse("plan 7.5 days").planDays());
        assertEquals(7, RequestConstraints.parse("plan 7.4 days").planDays());
        SmartPlateException e = assertThrows(SmartPlateException.class,
                () -> RequestConstraints.parse("give me dinner for 9.5 days").planDays());
        assertTrue(e.getMessage().contains("asked for 9.5"), e.getMessage()); // not quietly 5 days
    }

    @Test
    void otherDecimalNumbersAreRoundedAndHugeNumbersAreAnError() {
        RequestConstraints c = RequestConstraints.parse("2.5 quick dinners under 30.5 minutes for 2.5 people");
        assertEquals(3, c.recommendationCount(1));      // nearest, .5 up
        assertEquals(30, c.getMaxTime());                 // a time limit rounds down
        assertEquals(3, c.servings(2));                   // nearest, .5 up
        assertThrows(SmartPlateException.class, () -> RequestConstraints.parse("plan 99999999999 days"));
    }
}
