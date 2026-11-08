package com.smartplate.agent;

import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.UserProfile;
import com.smartplate.plan.MealPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hard constraints stated inside one natural-language request, for example
 * "plan 5 vegetarian dinners, avoid mushrooms, under 30 minutes, for 4 people".
 *
 * <p>
 * The AI model also reads the request, but these few constraints are extracted
 * with plain
 * rules so that ConstraintValidator can enforce them deterministically for this
 * request only
 * (the stored profile is not changed).
 */
public final class RequestConstraints {

    /** A number in the request, decimals included, so "9.5 days" is never read as "5 days". */
    private static final String NUMBER = "(?<![\\d.])(\\d+(?:\\.\\d+)?)";
    private static final Pattern DAYS = Pattern.compile(NUMBER + "\\s*-?\\s*(?:days?|dinners?|nights?)\\b");
    private static final Pattern COUNT = Pattern.compile(
            NUMBER + "\\s+(?:[a-z-]+\\s+)?(?:recipes?|ideas?|options?|suggestions?|dishes|dinners?|meals?)\\b");
    private static final Pattern TIME = Pattern.compile(
            "\\b(?:under|below|less than|within|max(?:imum)?|at most|no more than|in)\\s+" + NUMBER + "\\s*(?:min|mins|minutes)\\b");
    private static final Pattern SERVINGS = Pattern
            .compile("\\bfor\\s+" + NUMBER + "\\s*(?:people|persons|servings|guests)\\b");
    private static final Pattern AVOID = Pattern.compile("\\b(?:avoid|avoiding|no|without|skip)\\b");
    private static final Set<String> AVOID_STOP = Set.of(
            "under", "below", "less", "within", "in", "for", "with", "please", "because", "but",
            "keep", "make", "use", "using", "than", "this", "that", "today", "tonight");
    private static final Set<String> ARTICLES = Set.of("the", "a", "an", "any");
    private static final int MAX_AVOID_WORDS = 3;
    private static final Set<String> NOT_INGREDIENTS = Set.of(
            "more", "longer", "than", "repeats", "repeat", "repeated", "cooking", "oven", "time", "fuss",
            "need", "thanks", "the", "a", "an", "any", "spicy", "leftovers");

    private final Double days; // as written, e.g. 9.5; planDays() rounds it
    private final Integer count;
    private final Integer maxTime;
    private final Integer servings;
    private final List<String> avoid;
    private final List<String> diets;

    private RequestConstraints(Double days, Integer count, Integer maxTime, Integer servings, List<String> avoid,
            List<String> diets) {
        this.days = days;
        this.count = count;
        this.maxTime = maxTime;
        this.servings = servings;
        this.avoid = Collections.unmodifiableList(avoid);
        this.diets = Collections.unmodifiableList(diets);
    }

    public static RequestConstraints parse(String request) {
        String text = request == null ? "" : request.toLowerCase(Locale.ROOT);
        Double days = null;
        if (text.contains("weekday") || text.contains("work week") || text.contains("workweek")) {
            days = 5.0;
        }
        Matcher m = DAYS.matcher(text);
        if (m.find()) {
            days = number(m.group(1), "days");
        }
        Integer count = null;
        m = COUNT.matcher(text);
        if (m.find()) {
            count = (int) Math.round(number(m.group(1), "recipes")); // nearest, .5 up
        }
        Integer maxTime = null;
        m = TIME.matcher(text);
        if (m.find()) {
            maxTime = (int) Math.floor(number(m.group(1), "minutes")); // a limit: round down
        }
        Integer servings = null;
        m = SERVINGS.matcher(text);
        if (m.find()) {
            servings = (int) Math.round(number(m.group(1), "people")); // nearest, .5 up
        }
        List<String> avoid = new ArrayList<>();
        m = AVOID.matcher(text);
        while (m.find()) {
            for (String item : avoidedItems(text.substring(m.end()))) {
                if (!avoid.contains(item)) {
                    avoid.add(item);
                }
            }
        }
        List<String> diets = new ArrayList<>();
        for (String diet : UserProfile.SUPPORTED_DIETS) {
            if (Pattern.compile("(?<![a-z-])" + Pattern.quote(diet) + "(?![a-z-])").matcher(text).find()) {
                diets.add(diet);
            }
        }
        return new RequestConstraints(days, count, maxTime, servings, avoid, diets);
    }

    /**
     * Reads what follows "avoid" / "no" / "without" / "skip" up to the end of that
     * part of the
     * request: "red onion, under 30 min" gives [red onion], "mushrooms and olives"
     * gives
     * [mushrooms, olives], "the mushrooms" gives [mushrooms]. An item that starts
     * with a
     * non-ingredient word ("more than 20 min") is dropped.
     */
    private static List<String> avoidedItems(String rest) {
        List<String> items = new ArrayList<>();
        List<String> words = new ArrayList<>();
        String clause = rest.split("[,.;:!?()]", 2)[0];
        for (String word : clause.trim().split("\\s+")) {
            if (word.equals("and") || word.equals("or") || word.equals("nor")) {
                addAvoidItem(items, words);
                continue;
            }
            if (word.isEmpty() || AVOID_STOP.contains(word) || !word.matches("\\p{L}[\\p{L}'-]*")) {
                break;
            }
            if (words.isEmpty() && ARTICLES.contains(word)) {
                continue;
            }
            if (words.size() < MAX_AVOID_WORDS) {
                words.add(word);
            }
        }
        addAvoidItem(items, words);
        return items;
    }

    private static void addAvoidItem(List<String> items, List<String> words) {
        if (!words.isEmpty() && !NOT_INGREDIENTS.contains(words.get(0))) {
            items.add(String.join(" ", words));
        }
        words.clear();
    }

    /** A number from the request; a huge one is a clear error instead of an overflow. */
    private static double number(String text, String what) {
        double value = Double.parseDouble(text);
        if (value > Integer.MAX_VALUE) {
            throw new SmartPlateException("The number of " + what + " (" + text + ") is too large.");
        }
        return value;
    }

    /** Nearest whole number; an exact half goes toward the allowed range (4.5 -> 5, 7.5 -> 7, 5.5 -> 6). */
    static int roundTowardRange(double value, int min, int max) {
        double down = Math.floor(value);
        double up = Math.ceil(value);
        if (value - down < 0.5) {
            return (int) down;
        }
        if (value - down > 0.5) {
            return (int) up;
        }
        return up <= min ? (int) up : down >= max ? (int) down : (int) up;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** Number of plan days: the request's number, or 7. Must be 5 to 7. */
    public int planDays() {
        double asked = days == null ? MealPlan.MAX_DAYS : days;
        int n = roundTowardRange(asked, MealPlan.MIN_DAYS, MealPlan.MAX_DAYS);
        if (n < MealPlan.MIN_DAYS || n > MealPlan.MAX_DAYS) {
            throw new SmartPlateException("A plan must have " + MealPlan.MIN_DAYS + " to " + MealPlan.MAX_DAYS
                    + " days (one dinner per day); the request asked for " + format(asked) + ".");
        }
        return n;
    }

    /** Number of recommendations: the request's number, or the default. */
    public int recommendationCount(int defaultCount) {
        return count == null || count < 1 ? defaultCount : count;
    }

    public int servings(int defaultServings) {
        return servings == null || servings < 1 ? defaultServings : servings;
    }

    public Integer getMaxTime() {
        return maxTime;
    }

    public List<String> getAvoid() {
        return avoid;
    }

    /** Dietary preferences named in the request, e.g. "vegetarian". */
    public List<String> getDiets() {
        return diets;
    }

    /** The profile plus this request's diets, extra dislikes and time limit. */
    public UserProfile applyTo(UserProfile profile) {
        return profile.withRequestConstraints(diets, avoid, maxTime);
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        if (days != null) {
            parts.add(format(days) + " days");
        }
        if (count != null) {
            parts.add(count + " items");
        }
        if (maxTime != null) {
            parts.add("max " + maxTime + " min");
        }
        if (servings != null) {
            parts.add(servings + " servings");
        }
        if (!diets.isEmpty()) {
            parts.add(String.join(", ", diets));
        }
        if (!avoid.isEmpty()) {
            parts.add("avoid " + String.join(", ", avoid));
        }
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }
}
