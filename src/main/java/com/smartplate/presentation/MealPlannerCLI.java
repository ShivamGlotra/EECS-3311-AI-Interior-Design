package com.smartplate.presentation;

import com.smartplate.domain.Ingredient;
import com.smartplate.domain.NutritionInfo;
import com.smartplate.domain.PantryAction;
import com.smartplate.domain.PantryItem;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.UserProfile;
import com.smartplate.facade.SmartRecipeFacade;
import com.smartplate.plan.MealPlan;
import com.smartplate.plan.PlannedMeal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line interface. Reads one command per line and calls only
 * SmartRecipeFacade.
 *
 * <p>
 * Scaling and substitution return changed copies of a recipe; the CLI keeps the
 * latest
 * copy of each recipe as a "working copy" so the two can be combined (scale,
 * then substitute).
 */
public class MealPlannerCLI {

    private static final String HELP = String.join("\n",
            "Commands:",
            "  profile                                  show your profile",
            "  profile set diet=vegetarian,gluten-free allergies=peanuts dislikes=mushrooms time=30 servings=2",
            "                                           (F01) any keys you leave out keep their current value",
            "  pantry                                   list the pantry",
            "  pantry add|update <name> <qty> <unit>    (F02) units: g, kg, ml, l, pcs",
            "  pantry remove <name>                     (F02)",
            "  recipes                                  list all recipes",
            "  search <words>                           (F03) search by name, tag or ingredient",
            "  show <recipe-id>                         show a recipe (your working copy if you changed it)",
            "  recommend <request>                      (F04) e.g. recommend 3 quick vegetarian dinners using rice",
            "  plan <request>                           (F05) e.g. plan 5 dinners, avoid mushrooms, under 30 minutes",
            "  showplan                                 show the current plan",
            "  edit <request>                           (F06) e.g. edit swap Tuesday for something vegetarian",
            "  undo                                     (F06) undo the last plan edit",
            "  scale <recipe-id> <servings>             (F07)",
            "  substitute <recipe-id> <ingredient>      (F08) e.g. substitute r10 butter",
            "  reset <recipe-id>                        forget the working copy of a recipe",
            "  nutrition <recipe-id> | nutrition plan   (F09)",
            "  shopping                                 (F10) shopping list for the plan minus the pantry",
            "  strategy [pantry-first|fastest]          show or change how candidates are shortlisted",
            "  trace                                    show what the agent did for the last AI request",
            "  help | quit");

    private final SmartRecipeFacade facade;
    private final BufferedReader in;
    private final PrintStream out;
    private final Map<String, Recipe> workingCopies = new HashMap<>();

    public MealPlannerCLI(SmartRecipeFacade facade, InputStream in, PrintStream out) {
        this.facade = facade;
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    /** Runs until "quit" or end of input. */
    public void runLoop() {
        out.println("SmartPlate - AI meal planning agent. AI model: " + facade.getAIModelName());
        out.println("Type 'help' for commands.");
        while (true) {
            out.print("> ");
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Input error: " + e.getMessage());
                return;
            }
            if (line == null) {
                out.println();
                return;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                out.println("Bye!");
                return;
            }
            try {
                handle(line);
            } catch (SmartPlateException e) {
                out.println("Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                out.println("Error: expected a number. Type 'help' for usage.");
            }
        }
    }

    /** Executes one command line. Package-private for tests. */
    void handle(String line) {
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (command) {
            case "help":
                out.println(HELP);
                break;
            case "profile":
                profile(rest);
                break;
            case "pantry":
                pantry(rest);
                break;
            case "recipes":
                for (Recipe r : facade.getAllRecipes()) {
                    out.println("  " + r.summary());
                }
                break;
            case "search":
                search(rest);
                break;
            case "show":
                out.println(recipe(rest).describe());
                break;
            case "recommend":
                List<Recipe> recs = facade.recommendRecipes(rest);
                out.println("Recommended (validated against your profile):");
                for (Recipe r : recs) {
                    out.println("  " + r.summary());
                }
                break;
            case "plan":
                printPlan(facade.createMealPlan(rest));
                break;
            case "showplan":
                if (facade.getCurrentPlan() == null) {
                    out.println("No plan yet. Use: plan <request>");
                } else {
                    printPlan(facade.getCurrentPlan());
                }
                break;
            case "edit":
                printPlan(facade.editMealPlan(rest));
                break;
            case "undo":
                MealPlan restored = facade.undoLastChange();
                out.println("Undone.");
                printPlan(restored);
                break;
            case "scale":
                scale(rest);
                break;
            case "substitute":
                substitute(rest);
                break;
            case "reset":
                workingCopies.remove(rest.toLowerCase(Locale.ROOT));
                out.println("Working copy of " + rest + " discarded.");
                break;
            case "nutrition":
                nutrition(rest);
                break;
            case "shopping":
                out.println("Shopping list:");
                out.println(facade.generateShoppingList());
                break;
            case "strategy":
                if (!rest.isEmpty()) {
                    facade.setStrategy(rest);
                }
                out.println("Strategy: " + facade.getStrategyName());
                break;
            case "trace":
                List<String> trace = facade.getLastAgentTrace();
                if (trace.isEmpty()) {
                    out.println("No AI request yet.");
                }
                for (String step : trace) {
                    out.println("  * " + step);
                }
                break;
            default:
                out.println("Unknown command '" + command + "'. Type 'help'.");
        }
    }

    private void profile(String rest) {
        if (rest.isEmpty()) {
            out.println(facade.getProfile());
            return;
        }
        if (!rest.toLowerCase(Locale.ROOT).startsWith("set")) {
            throw new SmartPlateException("Use 'profile' or 'profile set key=value ...'.");
        }
        UserProfile current = facade.getProfile();
        List<String> diet = new ArrayList<>(current.getDietaryPreferences());
        List<String> allergies = new ArrayList<>(current.getAllergies());
        List<String> dislikes = new ArrayList<>(current.getDislikedIngredients());
        int time = current.getMaxCookingTime();
        int servings = current.getDefaultServings();
        for (String pair : rest.substring(3).trim().split("\\s+(?=[a-z]+=)")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                throw new SmartPlateException("Expected key=value, got '" + pair + "'.");
            }
            String key = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = pair.substring(eq + 1).trim();
            switch (key) {
                case "diet":
                    diet = splitList(value);
                    break;
                case "allergies":
                    allergies = splitList(value);
                    break;
                case "dislikes":
                    dislikes = splitList(value);
                    break;
                case "time":
                    time = Integer.parseInt(value);
                    break;
                case "servings":
                    servings = Integer.parseInt(value);
                    break;
                default:
                    throw new SmartPlateException("Unknown profile key '" + key
                            + "'. Use diet, allergies, dislikes, time, servings.");
            }
        }
        facade.setupProfile(new UserProfile(diet, allergies, dislikes, time, servings));
        out.println("Profile saved:");
        out.println(facade.getProfile());
    }

    private void pantry(String rest) {
        if (rest.isEmpty() || rest.equalsIgnoreCase("list")) {
            out.println(facade.getPantry());
            return;
        }
        String[] words = rest.split("\\s+");
        PantryAction action;
        try {
            action = PantryAction.valueOf(words[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SmartPlateException("Use pantry add|update|remove ...");
        }
        if (action == PantryAction.REMOVE) {
            String name = String.join(" ", Arrays.copyOfRange(words, 1, words.length));
            facade.managePantry(action, new PantryItem(name, 0, "pcs"));
        } else {
            if (words.length < 4) {
                throw new SmartPlateException("Use pantry " + words[0] + " <name> <quantity> <unit>.");
            }
            String name = String.join(" ", Arrays.copyOfRange(words, 1, words.length - 2));
            double quantity = Double.parseDouble(words[words.length - 2]);
            String unit = words[words.length - 1];
            facade.managePantry(action, new PantryItem(new Ingredient(name), quantity, unit));
        }
        out.println("Pantry:");
        out.println(facade.getPantry());
    }

    private void search(String query) {
        List<Recipe> results = facade.searchRecipes(query);
        if (results.isEmpty()) {
            out.println("No recipes found.");
        }
        for (Recipe r : results) {
            out.println("  " + r.summary());
        }
    }

    private void scale(String rest) {
        String[] words = rest.split("\\s+");
        if (words.length != 2) {
            throw new SmartPlateException("Use scale <recipe-id> <servings>.");
        }
        Recipe scaled = facade.adjustServings(recipe(words[0]), Integer.parseInt(words[1]));
        workingCopies.put(scaled.getId(), scaled);
        out.println(scaled.describe());
    }

    private void substitute(String rest) {
        String[] words = rest.split("\\s+", 2);
        if (words.length != 2) {
            throw new SmartPlateException("Use substitute <recipe-id> <ingredient>.");
        }
        Recipe updated = facade.substituteIngredient(recipe(words[0]), new Ingredient(words[1]));
        workingCopies.put(updated.getId(), updated);
        out.println("Substituted " + facade.getLastSubstitution() + ":");
        out.println(updated.describe());
    }

    private void nutrition(String rest) {
        if (rest.equalsIgnoreCase("plan")) {
            NutritionInfo total = facade.calculatePlanNutrition();
            MealPlan plan = facade.getCurrentPlan();
            out.println("Whole plan (" + plan.size() + " dinners): " + total);
            for (PlannedMeal meal : plan.getMeals()) {
                out.println("  " + meal.getDay() + ": " + meal.getNutrition());
            }
            return;
        }
        Recipe r = recipe(rest);
        NutritionInfo total = facade.calculateRecipeNutrition(r);
        out.println(r.getName() + " (" + r.getServings() + " servings): " + total);
        out.println("Per serving: " + total.scale(1.0 / r.getServings()));
    }

    private void printPlan(MealPlan plan) {
        out.println("Meal plan:");
        for (PlannedMeal meal : plan.getMeals()) {
            out.println("  " + meal);
        }
        if (facade.canUndo()) {
            out.println("(type 'undo' to revert the last change)");
        }
    }

    private Recipe recipe(String id) {
        String key = id.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            throw new SmartPlateException("Give a recipe id, e.g. r01. Use 'recipes' to list them.");
        }
        Recipe copy = workingCopies.get(key);
        return copy != null ? copy : facade.getRecipe(key);
    }

    private static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        if (value.equalsIgnoreCase("none") || value.isBlank()) {
            return out;
        }
        for (String s : value.split(",")) {
            out.add(s.trim());
        }
        return out;
    }
}
