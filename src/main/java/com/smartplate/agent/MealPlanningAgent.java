package com.smartplate.agent;

import com.smartplate.ai.AIModel;
import com.smartplate.ai.PromptFormat;
import com.smartplate.command.Command;
import com.smartplate.command.ReplaceMealCommand;
import com.smartplate.domain.Pantry;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.RecipeIngredient;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.UserProfile;
import com.smartplate.plan.Days;
import com.smartplate.plan.MealPlan;
import com.smartplate.plan.PlannedMeal;
import com.smartplate.service.RecipeRepository;
import com.smartplate.strategy.RecipeSelectionStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AI agent behind F04 (recommend), F05 (weekly plan) and F06 (edit plan).
 *
 * <p>
 * Every hybrid feature follows the same loop (report section 1.4):
 * <ol>
 * <li>retrieve recipes from RecipeRepository;</li>
 * <li>the RecipeSelectionStrategy shortlists candidates (Strategy pattern, this
 * class is the Context);</li>
 * <li>the AIModel chooses among the candidates (Adapter pattern, this class is
 * the Client);</li>
 * <li>ConstraintValidator checks the choice with deterministic code;</li>
 * <li>if there are violations, ask the AI once more with the violation
 * messages;</li>
 * <li>return a valid result, or report the failure. An invalid result is never
 * returned.</li>
 * </ol>
 * Each step is written to a trace that the CLI and GUI can show ("agent
 * trace").
 */
public class MealPlanningAgent {

    /** Most candidates offered to the AI in one prompt. */
    public static final int MAX_CANDIDATES = 15;
    public static final int DEFAULT_RECOMMENDATIONS = 3;
    private static final int MAX_ATTEMPTS = 2; // first answer + one revision

    private static final Pattern PLAN_LINE = Pattern.compile(
            "(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b\\s*[:=\\-]+\\s*([A-Za-z0-9_-]+)");

    private volatile AIModel aiModel; // switchable at run time from the GUI
    private final RecipeRepository repository;
    private final ConstraintValidator validator;
    private RecipeSelectionStrategy strategy;
    private final List<String> trace = new ArrayList<>();

    public MealPlanningAgent(AIModel aiModel, RecipeRepository repository, ConstraintValidator validator,
            RecipeSelectionStrategy strategy) {
        this.aiModel = aiModel;
        this.repository = repository;
        this.validator = validator;
        this.strategy = strategy;
    }

    /** Switches the recipe-selection strategy at run time. */
    public void setStrategy(RecipeSelectionStrategy strategy) {
        this.strategy = strategy;
    }

    public RecipeSelectionStrategy getStrategy() {
        return strategy;
    }

    public AIModel getAIModel() {
        return aiModel;
    }

    /** Switches the AI model at run time; the next request uses the new one. */
    public void setAIModel(AIModel aiModel) {
        if (aiModel == null) {
            throw new IllegalArgumentException("aiModel is required");
        }
        this.aiModel = aiModel;
    }

    /** The steps the agent took during the last request. */
    public List<String> getLastTrace() {
        return Collections.unmodifiableList(trace);
    }

    // ================================================================== F04
    // recommend

    public List<Recipe> recommend(String request, UserProfile profile, Pantry pantry) {
        trace.clear();
        AIModel model = aiModel; // avoid race conditions if the GUI switches the model during a request - only
                                 // the first model or one model per request is used for the whole request
        RequestConstraints constraints = RequestConstraints.parse(request);
        UserProfile effective = constraints.applyTo(profile);
        log("Request: \"" + request + "\" (constraints from request: " + constraints + ")");
        List<Recipe> candidates = shortlist(effective, pantry);
        int count = Math.min(constraints.recommendationCount(DEFAULT_RECOMMENDATIONS), candidates.size());

        Set<String> exclude = new LinkedHashSet<>();
        List<String> violations = new ArrayList<>();
        List<Recipe> valid = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = newPrompt(PromptFormat.TASK_RECOMMEND, request, effective, candidates, exclude, violations)
                    .field(PromptFormat.COUNT, String.valueOf(count))
                    .field(PromptFormat.RESPONSE_FORMAT, PromptFormat.RECIPES + ": <id>, <id>, ... (exactly "
                            + count + " different ids)")
                    .build();
            String answer = callAI(model, prompt, attempt);

            valid = new ArrayList<>();
            violations = new ArrayList<>();
            List<String> ids = parseIdList(PromptFormat.field(answer, PromptFormat.RECIPES));
            if (ids.isEmpty()) {
                violations.add("The answer did not contain any recipe ids in the format '"
                        + PromptFormat.RECIPES + ": id, id'");
            }
            for (String id : ids) {
                Recipe recipe = lookup(id, violations, exclude);
                if (recipe == null || valid.contains(recipe)) {
                    continue;
                }
                ValidationResult result = validator.validateRecipe(recipe, effective);
                if (result.isValid()) {
                    valid.add(recipe);
                } else {
                    violations.addAll(result.getViolations());
                    exclude.add(recipe.getId());
                }
            }
            log("Validation (attempt " + attempt + "): " + valid.size() + " valid, "
                    + violations.size() + " violation(s)" + describe(violations));
            if (violations.isEmpty() && !valid.isEmpty()) {
                break;
            }
            if (attempt < MAX_ATTEMPTS) {
                log("Revising: asking the AI again with the violation messages.");
            }
        }
        if (valid.isEmpty()) {
            log("Failure: no valid recommendation.");
            throw new SmartPlateException("No valid recommendation could be found. "
                    + (violations.isEmpty() ? "" : "Problems: " + String.join("; ", violations)));
        }
        log("Returning " + valid.size() + " validated recommendation(s).");
        return valid;
    }

    // ================================================================== F05 create
    // plan

    public MealPlan createPlan(String request, UserProfile profile, Pantry pantry) {
        trace.clear();
        AIModel model = aiModel;
        RequestConstraints constraints = RequestConstraints.parse(request);
        UserProfile effective = constraints.applyTo(profile);
        int dayCount = constraints.planDays();
        int servings = constraints.servings(profile.getDefaultServings());
        List<String> days = Days.firstDays(dayCount);
        log("Request: \"" + request + "\" (constraints from request: " + constraints + ")");
        log("Planning " + dayCount + " dinners (" + String.join(", ", days) + "), " + servings + " servings each.");
        List<Recipe> candidates = shortlist(effective, pantry);

        Set<String> exclude = new LinkedHashSet<>();
        List<String> violations = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = newPrompt(PromptFormat.TASK_PLAN, request, effective, candidates, exclude, violations)
                    .field(PromptFormat.DAYS, String.join(", ", days))
                    .field(PromptFormat.RESPONSE_FORMAT, "one line per day, '<Day>: <id>', for exactly these days: "
                            + String.join(", ", days) + ". Prefer a different recipe each day.")
                    .build();
            String answer = callAI(model, prompt, attempt);

            violations = new ArrayList<>();
            Map<String, String> chosen = parsePlan(answer);
            MealPlan plan = new MealPlan();
            for (String day : days) {
                String id = chosen.get(day);
                if (id == null) {
                    violations.add("No recipe was given for " + day);
                    continue;
                }
                Recipe recipe = lookup(id, violations, exclude);
                if (recipe != null) {
                    plan.addMeal(new PlannedMeal(day, recipe, servings));
                    log("  " + day + " -> " + recipe.getName() + " (" + recipe.getId() + ")");
                }
            }
            ValidationResult result = validator.validatePlan(plan, effective);
            violations.addAll(result.getViolations());
            for (PlannedMeal meal : plan.getMeals()) {
                if (!validator.validateRecipe(meal.getRecipe(), effective).isValid()) {
                    exclude.add(meal.getRecipe().getId());
                }
            }
            log("Validation (attempt " + attempt + "): " + (violations.isEmpty() ? "plan is valid"
                    : violations.size() + " violation(s)" + describe(violations)));
            if (violations.isEmpty()) {
                log("Returning the validated " + plan.size() + "-day plan.");
                return plan;
            }
            if (attempt < MAX_ATTEMPTS) {
                log("Revising: asking the AI again with the violation messages.");
            }
        }
        log("Failure: no valid plan after one revision.");
        throw new SmartPlateException("Could not build a valid meal plan. Problems: " + String.join("; ", violations));
    }

    // ================================================================== F06 edit
    // plan

    /**
     * Chooses a replacement meal and returns (but does not run) the command that
     * applies it.
     */
    public Command editPlan(String request, MealPlan plan, UserProfile profile, Pantry pantry) {
        trace.clear();
        AIModel model = aiModel;
        if (plan == null || plan.isEmpty()) {
            throw new SmartPlateException("There is no meal plan to edit. Create a plan first.");
        }
        RequestConstraints constraints = RequestConstraints.parse(request);
        UserProfile effective = constraints.applyTo(profile);
        String targetDay = findDay(request);
        log("Request: \"" + request + "\" (day in request: " + (targetDay == null ? "not given" : targetDay)
                + "; constraints: " + constraints + ")");
        if (targetDay != null && plan.getMeal(targetDay) == null) {
            throw new SmartPlateException("The plan has no meal on " + targetDay + ".");
        }

        List<Recipe> all = repository.findAll();
        log("Retrieved " + all.size() + " recipes from the repository.");
        List<Recipe> candidates = strategy.selectRecipes(all, effective, pantry);
        candidates = candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size()));
        log("Strategy '" + strategy.getName() + "' shortlisted " + candidates.size() + " candidate(s).");

        StringBuilder current = new StringBuilder();
        for (PlannedMeal meal : plan.getMeals()) {
            if (current.length() > 0) {
                current.append("; ");
            }
            current.append(meal.getDay()).append('=').append(meal.getRecipe().getId());
        }
        PromptBuilder builder = newPrompt(PromptFormat.TASK_EDIT, request, effective, candidates,
                Set.of(), List.of())
                .field(PromptFormat.CURRENT_PLAN, current.toString());
        if (targetDay != null) {
            builder.field(PromptFormat.TARGET_DAY, targetDay);
        }
        builder.field(PromptFormat.RESPONSE_FORMAT, "two lines: '" + PromptFormat.DAY + ": <day to change>' and '"
                + PromptFormat.RECIPE + ": <replacement id>'. Choose a recipe that is not already in the plan.");
        String answer = callAI(model, builder.build(), 1);

        String day = targetDay != null ? targetDay : Days.normalize(PromptFormat.field(answer, PromptFormat.DAY));
        PlannedMeal oldMeal = day == null ? null : plan.getMeal(day);
        if (oldMeal == null) {
            log("Failure: the day to change does not exist in the plan.");
            throw new SmartPlateException("Please name a day that is in the plan (for example \"swap Tuesday\").");
        }
        String id = cleanId(PromptFormat.field(answer, PromptFormat.RECIPE));
        Recipe recipe = repository.findById(id);
        if (recipe == null) {
            log("Rejected: '" + id + "' is not a recipe in the repository.");
            throw new SmartPlateException("No suitable replacement was found (the AI suggested '" + id
                    + "', which is not in the recipe repository).");
        }
        if (recipe.getId().equals(oldMeal.getRecipe().getId())) {
            log("Rejected: the AI proposed the same recipe.");
            throw new SmartPlateException("No suitable replacement was found for " + day + ".");
        }
        ValidationResult result = validator.validateRecipe(recipe, effective);
        log("Validation: " + (result.isValid() ? "replacement is valid" : result));
        if (!result.isValid()) {
            throw new SmartPlateException("The proposed replacement violates your constraints: "
                    + String.join("; ", result.getViolations()));
        }
        PlannedMeal newMeal = new PlannedMeal(day, recipe, oldMeal.getServings());
        ReplaceMealCommand command = new ReplaceMealCommand(plan, day, oldMeal, newMeal);
        log("Created command: " + command.getDescription());
        return command;
    }

    // ================================================================== helpers

    private List<Recipe> shortlist(UserProfile profile, Pantry pantry) {
        List<Recipe> all = repository.findAll();
        log("Retrieved " + all.size() + " recipes from the repository.");
        List<Recipe> candidates = strategy.selectRecipes(all, profile, pantry);
        if (candidates.isEmpty()) {
            log("Failure: no recipe fits the profile and request.");
            throw new SmartPlateException("No recipe in the repository satisfies your preferences and request. "
                    + "Try relaxing a constraint (for example the cooking time).");
        }
        candidates = candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size()));
        log("Strategy '" + strategy.getName() + "' shortlisted " + candidates.size() + " candidate(s).");
        return candidates;
    }

    private String callAI(AIModel model, String prompt, int attempt) {
        log("Asking " + model.getName() + (attempt > 1 ? " (revision)" : "") + ".");
        try {
            String answer = model.generate(prompt);
            log("AI answered: " + answer.replace('\n', ' '));
            return answer;
        } catch (RuntimeException e) {
            log("Failure: AI service error - " + e.getMessage());
            throw e;
        }
    }

    /**
     * Finds the recipe for an id, recording a violation if the AI named an unknown
     * recipe.
     */
    private Recipe lookup(String id, List<String> violations, Set<String> exclude) {
        Recipe recipe = repository.findById(id);
        if (recipe == null) {
            violations.add("'" + id + "' is not a recipe in the repository; choose only from CANDIDATES");
            exclude.add(id);
        }
        return recipe;
    }

    private PromptBuilder newPrompt(String task, String request, UserProfile profile, List<Recipe> candidates,
            Set<String> exclude, List<String> violations) {
        Set<String> avoid = new LinkedHashSet<>(profile.getAllergies());
        avoid.addAll(profile.getDislikedIngredients());
        PromptBuilder b = new PromptBuilder()
                .field(PromptFormat.TASK, task)
                .field(PromptFormat.REQUEST, request.replace('\n', ' '))
                .field(PromptFormat.DIET, joinOrNone(profile.getDietaryPreferences()))
                .field(PromptFormat.AVOID, joinOrNone(avoid))
                .field(PromptFormat.MAX_TIME, String.valueOf(profile.getMaxCookingTime()))
                .field(PromptFormat.EXCLUDE, joinOrNone(exclude));
        if (!violations.isEmpty()) {
            b.line(PromptFormat.PREVIOUS_VIOLATIONS + ":");
            for (String v : violations) {
                b.line("- " + v);
            }
        }
        b.line(PromptFormat.CANDIDATES + ":");
        for (Recipe r : candidates) {
            b.line("- " + candidateLine(r));
        }
        return b;
    }

    private static String candidateLine(Recipe r) {
        List<String> names = new ArrayList<>();
        for (RecipeIngredient ri : r.getIngredients()) {
            names.add(ri.getName());
        }
        return r.getId() + " | " + r.getName() + " | " + r.getCookingTime() + " min | tags: "
                + joinOrNone(r.getDietaryTags()) + " | ingredients: " + String.join(", ", names);
    }

    private static Map<String, String> parsePlan(String answer) {
        Map<String, String> chosen = new LinkedHashMap<>();
        Matcher m = PLAN_LINE.matcher(answer.replace("*", "").replace("`", ""));
        while (m.find()) {
            chosen.putIfAbsent(Days.normalize(m.group(1)), cleanId(m.group(2)));
        }
        return chosen;
    }

    private static List<String> parseIdList(String value) {
        List<String> ids = new ArrayList<>();
        for (String part : value.split("[,;\\s]+")) {
            String id = cleanId(part);
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String cleanId(String raw) {
        return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
    }

    private static String findDay(String request) {
        return Days.findIn(request);
    }

    private static String joinOrNone(Set<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String describe(List<String> violations) {
        return violations.isEmpty() ? "" : " - " + String.join("; ", violations);
    }

    private void log(String step) {
        trace.add(step);
    }

    /** Small helper that writes the "KEY: value" prompt format. */
    private static final class PromptBuilder {
        private final StringBuilder sb = new StringBuilder();

        PromptBuilder field(String key, String value) {
            sb.append(key).append(": ").append(value).append('\n');
            return this;
        }

        PromptBuilder line(String text) {
            sb.append(text).append('\n');
            return this;
        }

        String build() {
            return sb.toString().trim();
        }
    }
}
