package com.smartplate.agent;

import com.smartplate.TestData;
import com.smartplate.ai.AIModel;
import com.smartplate.ai.AIServiceException;
import com.smartplate.ai.MockAIModel;
import com.smartplate.command.Command;
import com.smartplate.domain.Pantry;
import com.smartplate.domain.PantryItem;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.UserProfile;
import com.smartplate.plan.MealPlan;
import com.smartplate.plan.PlannedMeal;
import com.smartplate.service.RecipeRepository;
import com.smartplate.strategy.FastestMealStrategy;
import com.smartplate.strategy.PantryFirstStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MealPlanningAgentTest {

    private final RecipeRepository repo = RecipeRepository.withSampleRecipes();
    private final UserProfile vegetarian = TestData.profile(List.of("vegetarian"), List.of("peanuts"), List.of(), 45);

    private MealPlanningAgent agent(MockAIModel ai) {
        return new MealPlanningAgent(ai, repo, new ConstraintValidator(), new PantryFirstStrategy());
    }

    // ------------------------------------------------------------ F04

    @Test
    void recommendReturnsOnlyValidRecipes() {
        MockAIModel ai = new MockAIModel();
        List<Recipe> recs = agent(ai).recommend("3 quick dinners", vegetarian, new Pantry());
        assertEquals(3, recs.size());
        ConstraintValidator v = new ConstraintValidator();
        for (Recipe r : recs) {
            assertTrue(v.validateRecipe(r, vegetarian).isValid(), r.getName());
        }
        assertEquals(1, ai.getPrompts().size()); // no revision needed
    }

    @Test
    void aRequestKeepsTheAIModelItStartedWithIfTheUserSwitchesMidway() {
        MockAIModel second = MockAIModel.scripted("RECIPES: r11, r17");
        MockAIModel answers = MockAIModel.scripted("RECIPES: r04, r999", "RECIPES: r09, r02");
        MealPlanningAgent[] agent = new MealPlanningAgent[1];
        AIModel first = new AIModel() {
            @Override
            public String generate(String prompt) {
                agent[0].setAIModel(second); // the user picks another model while the request runs
                return answers.generate(prompt);
            }

            @Override
            public String getName() {
                return "first model";
            }
        };
        agent[0] = new MealPlanningAgent(first, repo, new ConstraintValidator(), new PantryFirstStrategy());

        List<Recipe> recs = agent[0].recommend("2 dinners", vegetarian, new Pantry());
        assertEquals(List.of(repo.findById("r09"), repo.findById("r02")), recs); // revision came from the first model
        assertTrue(second.getPrompts().isEmpty());
        assertEquals(second, agent[0].getAIModel()); // the switch applies to the next request
    }

    @Test
    void recommendRevisesOnceWithViolationMessages() {
        // first answer contains a meat dish and an invented id
        MockAIModel ai = MockAIModel.scripted("RECIPES: r04, r999", "RECIPES: r09, r02");
        MealPlanningAgent agent = agent(ai);
        List<Recipe> recs = agent.recommend("2 dinners", vegetarian, new Pantry());
        assertEquals(List.of(repo.findById("r09"), repo.findById("r02")), recs);
        assertEquals(2, ai.getPrompts().size());
        String revision = ai.getPrompts().get(1);
        assertTrue(revision.contains("PREVIOUS_VIOLATIONS"));
        assertTrue(revision.contains("chicken breast (not vegetarian)"));
        assertTrue(revision.contains("'r999' is not a recipe in the repository"));
        assertTrue(revision.contains("EXCLUDE: r999, r04") || revision.contains("EXCLUDE: r04, r999"));
        assertTrue(agent.getLastTrace().stream().anyMatch(s -> s.startsWith("Revising")));
    }

    @Test
    void recommendReportsFailureWhenRevisionIsStillInvalid() {
        MockAIModel ai = MockAIModel.scripted("RECIPES: r04", "RECIPES: r05");
        assertThrows(SmartPlateException.class, () -> agent(ai).recommend("dinner", vegetarian, new Pantry()));
        assertEquals(2, ai.getPrompts().size()); // revised exactly once
    }

    @Test
    void dietInTheRequestIsEnforcedEvenIfNotInTheProfile() {
        // the AI suggests a non-vegan dish twice; the request said vegan, so it must be
        // rejected
        MockAIModel ai = MockAIModel.scripted("RECIPES: r13", "RECIPES: r13");
        assertThrows(SmartPlateException.class,
                () -> agent(ai).recommend("1 vegan dinner", TestData.noRestrictions(), new Pantry()));
        for (Recipe r : agent(new MockAIModel()).recommend("2 vegan dinners", TestData.noRestrictions(),
                new Pantry())) {
            assertTrue(r.hasTag("vegan"), r.getName());
        }
    }

    @Test
    void aiServiceFailureIsReported() {
        assertThrows(AIServiceException.class,
                () -> agent(MockAIModel.failing()).recommend("dinner", vegetarian, new Pantry()));
    }

    @Test
    void impossibleConstraintsAreReportedBeforeCallingTheAI() {
        MockAIModel ai = new MockAIModel();
        UserProfile impossible = TestData.profile(List.of("vegan"), List.of(), List.of(), 5);
        assertThrows(SmartPlateException.class, () -> agent(ai).recommend("dinner", impossible, new Pantry()));
        assertTrue(ai.getPrompts().isEmpty());
    }

    @Test
    void promptOnlyOffersCandidatesThatFitTheProfile() {
        MockAIModel ai = new MockAIModel();
        agent(ai).recommend("dinner", vegetarian, new Pantry());
        String prompt = ai.getPrompts().get(0);
        assertFalse(prompt.contains("r04 |")); // chicken
        assertFalse(prompt.contains("r15 |")); // peanut noodles (allergy)
        assertTrue(prompt.contains("AVOID: peanuts"));
    }

    // ------------------------------------------------------------ F05

    @Test
    void createPlanBuildsAValidSevenDayPlan() {
        MealPlan plan = agent(new MockAIModel()).createPlan("plan dinners for this week", vegetarian, new Pantry());
        assertEquals(7, plan.size());
        assertTrue(new ConstraintValidator().validatePlan(plan, vegetarian).isValid());
        for (PlannedMeal meal : plan.getMeals()) {
            assertEquals(2, meal.getServings());
        }
    }

    @Test
    void createPlanAppliesRequestConstraints() {
        MealPlan plan = agent(new MockAIModel()).createPlan(
                "5 dinners for 3 people, avoid spinach, under 20 minutes", vegetarian, new Pantry());
        assertEquals(5, plan.size());
        for (PlannedMeal meal : plan.getMeals()) {
            assertTrue(meal.getCookingTime() <= 20);
            assertEquals(3, meal.getServings());
            assertTrue(meal.getRecipe().findIngredient("spinach") == null);
        }
    }

    @Test
    void createPlanRevisesWhenADayIsMissingOrInvalid() {
        MockAIModel ai = MockAIModel.scripted(
                "Monday: r09\nTuesday: r04\nWednesday: r02\nThursday: r11\n", // Tuesday meat, Friday missing
                "Monday: r09\nTuesday: r03\nWednesday: r02\nThursday: r11\nFriday: r17");
        MealPlan plan = agent(ai).createPlan("weekday dinners", vegetarian, new Pantry());
        assertEquals(5, plan.size());
        assertEquals("r03", plan.getMeal("Tuesday").getRecipe().getId());
        String revision = ai.getPrompts().get(1);
        assertTrue(revision.contains("No recipe was given for Friday"));
        assertTrue(revision.contains("Tuesday: Chicken Stir-Fry"));
    }

    @Test
    void createPlanFailsAfterOneRevision() {
        MockAIModel ai = MockAIModel.scripted("nonsense", "still nonsense");
        assertThrows(SmartPlateException.class,
                () -> agent(ai).createPlan("weekday dinners", vegetarian, new Pantry()));
        assertEquals(2, ai.getPrompts().size());
    }

    @Test
    void strategyChangesTheCandidateOrder() {
        Pantry pantry = new Pantry();
        pantry.addItem(new PantryItem("chickpeas", 1, "kg"));
        pantry.addItem(new PantryItem("spinach", 500, "g"));
        pantry.addItem(new PantryItem("coconut milk", 1, "l"));
        MockAIModel ai = new MockAIModel();
        MealPlanningAgent agent = agent(ai);
        assertEquals("r02", agent.recommend("1 dinner", vegetarian, pantry).get(0).getId());
        agent.setStrategy(new FastestMealStrategy());
        assertEquals(10, agent.recommend("1 dinner", vegetarian, pantry).get(0).getCookingTime());
    }

    // ------------------------------------------------------------ F06

    private MealPlan fiveDayPlan() {
        MealPlan plan = new MealPlan();
        String[] ids = { "r09", "r01", "r02", "r11", "r17" };
        String[] days = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" };
        for (int i = 0; i < 5; i++) {
            plan.addMeal(new PlannedMeal(days[i], repo.findById(ids[i]), 2));
        }
        return plan;
    }

    @Test
    void editPlanReturnsACommandThatCanBeUndone() {
        MealPlan plan = fiveDayPlan();
        Command command = agent(new MockAIModel()).editPlan("swap Tuesday for something with spinach",
                plan, vegetarian, new Pantry());
        assertEquals("r01", plan.getMeal("Tuesday").getRecipe().getId()); // not applied yet
        command.execute();
        Recipe replacement = plan.getMeal("Tuesday").getRecipe();
        assertFalse(replacement.getId().equals("r01"));
        assertTrue(new ConstraintValidator().validateRecipe(replacement, vegetarian).isValid());
        command.undo();
        assertEquals("r01", plan.getMeal("Tuesday").getRecipe().getId());
    }

    @Test
    void editPlanRejectsUnknownDayInvalidReplacementAndInventedRecipe() {
        MealPlan plan = fiveDayPlan();
        assertThrows(SmartPlateException.class, () -> agent(new MockAIModel())
                .editPlan("swap Sunday", plan, vegetarian, new Pantry()));
        assertThrows(SmartPlateException.class, () -> agent(MockAIModel.scripted("DAY: Monday\nRECIPE: r05"))
                .editPlan("change monday", plan, vegetarian, new Pantry()));
        assertThrows(SmartPlateException.class, () -> agent(MockAIModel.scripted("DAY: Monday\nRECIPE: r777"))
                .editPlan("change monday", plan, vegetarian, new Pantry()));
        assertThrows(SmartPlateException.class, () -> agent(new MockAIModel())
                .editPlan("change monday", new MealPlan(), vegetarian, new Pantry()));
    }

    @Test
    void editPlanLetsTheAIPickTheDayWhenNotGiven() {
        MealPlan plan = fiveDayPlan();
        Command command = agent(MockAIModel.scripted("DAY: Wednesday\nRECIPE: r03"))
                .editPlan("replace the curry with pasta", plan, vegetarian, new Pantry());
        command.execute();
        assertEquals("r03", plan.getMeal("Wednesday").getRecipe().getId());
    }

    @Test
    void editPlanDoesNotMistakeSundriedForSunday() {
        MealPlan plan = fiveDayPlan(); // Monday - Friday so a wrong "Sunday" should throw an exception
        Command command = agent(MockAIModel.scripted("DAY: Wednesday\nRECIPE: r03"))
                .editPlan("replace the sun-dried tomato pasta with something else", plan, vegetarian, new Pantry());
        command.execute();
        assertEquals("r03", plan.getMeal("Wednesday").getRecipe().getId());
    }
}
