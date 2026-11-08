package com.smartplate.ai;

import com.smartplate.plan.Days;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline implementation of AIModel (Adapter pattern - alternative to
 * AIAdapter).
 *
 * <p>
 * Lets SmartPlate run and be tested with no external LLM and no API key. It has
 * three modes:
 * <ul>
 * <li><b>heuristic</b> (default): reads the prompt written by MealPlanningAgent
 * and answers like a
 * sensible model would - it skips candidates that clash with the listed diet /
 * avoid terms /
 * time limit / exclusions, prefers candidates that match words in the request,
 * and answers in
 * the requested RESPONSE_FORMAT.</li>
 * <li><b>scripted</b>: returns pre-written answers in order (used by tests to
 * force a bad first
 * answer and check that the agent revises once).</li>
 * <li><b>failing</b>: always throws AIServiceException (tests the "AI service
 * fails" flows).</li>
 * </ul>
 * Every prompt received is recorded so tests can inspect it.
 */
public class MockAIModel implements AIModel {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "with", "some", "something", "this", "that", "week", "dinner", "dinners",
            "meal", "meals", "plan", "make", "please", "want", "would", "like", "using", "use", "keep",
            "under", "minutes", "minute", "days", "day", "swap", "replace", "change", "recipes", "recipe",
            "recommend", "suggest", "give", "from", "into", "instead", "items", "item", "my", "me", "of",
            "avoid", "without", "less", "than", "only", "quick", "easy", "pantry"));

    private final Deque<String> scripted;
    private final boolean failing;
    private final List<String> prompts = new ArrayList<>();

    public MockAIModel() {
        this(null, false);
    }

    private MockAIModel(List<String> scripted, boolean failing) {
        this.scripted = scripted == null ? null : new ArrayDeque<>(scripted);
        this.failing = failing;
    }

    /**
     * A mock that returns these answers in order, then falls back to heuristic
     * answers.
     */
    public static MockAIModel scripted(String... answers) {
        return new MockAIModel(Arrays.asList(answers), false);
    }

    /** A mock whose service is always unavailable. */
    public static MockAIModel failing() {
        return new MockAIModel(null, true);
    }

    @Override
    public String generate(String prompt) {
        prompts.add(prompt);
        if (failing) {
            throw new AIServiceException("The AI service is unavailable (simulated failure).");
        }
        if (scripted != null && !scripted.isEmpty()) {
            return scripted.poll();
        }
        return answer(prompt);
    }

    @Override
    public String getName() {
        return "Mock AI (offline)";
    }

    public List<String> getPrompts() {
        return Collections.unmodifiableList(prompts);
    }

    // ------------------------------------------------------------------ heuristic
    // answers

    private String answer(String prompt) {
        String task = PromptFormat.field(prompt, PromptFormat.TASK).toUpperCase(Locale.ROOT);
        List<Candidate> ranked = rank(prompt);
        switch (task) {
            case PromptFormat.TASK_PLAN:
                return answerPlan(prompt, ranked);
            case PromptFormat.TASK_EDIT:
                return answerEdit(prompt, ranked);
            default:
                return answerRecommend(prompt, ranked);
        }
    }

    private String answerRecommend(String prompt, List<Candidate> ranked) {
        int count = parseInt(PromptFormat.field(prompt, PromptFormat.COUNT), 3);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < ranked.size() && ids.size() < count; i++) {
            ids.add(ranked.get(i).id);
        }
        return PromptFormat.RECIPES + ": " + String.join(", ", ids);
    }

    private String answerPlan(String prompt, List<Candidate> ranked) {
        List<String> days = splitList(PromptFormat.field(prompt, PromptFormat.DAYS));
        if (days.isEmpty()) {
            days = Days.firstDays(7);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            String id = ranked.isEmpty() ? "none" : ranked.get(i % ranked.size()).id;
            sb.append(days.get(i)).append(": ").append(id).append('\n');
        }
        return sb.toString().trim();
    }

    private String answerEdit(String prompt, List<Candidate> ranked) {
        String day = Days.normalize(PromptFormat.field(prompt, PromptFormat.TARGET_DAY));
        if (day == null) {
            day = Days.findIn(PromptFormat.field(prompt, PromptFormat.REQUEST));
        }
        Set<String> inPlan = new HashSet<>();
        for (String entry : splitList(PromptFormat.field(prompt, PromptFormat.CURRENT_PLAN).replace(';', ','))) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                inPlan.add(entry.substring(eq + 1).trim());
            }
        }
        String choice = "none";
        for (Candidate c : ranked) {
            if (!inPlan.contains(c.id)) {
                choice = c.id;
                break;
            }
        }
        if (choice.equals("none") && !ranked.isEmpty()) {
            choice = ranked.get(0).id;
        }
        return PromptFormat.DAY + ": " + (day == null ? "unknown" : day) + "\n" + PromptFormat.RECIPE + ": " + choice;
    }

    /**
     * Candidates that fit the listed constraints, best match with the request
     * first.
     */
    private List<Candidate> rank(String prompt) {
        List<String> diets = splitList(PromptFormat.field(prompt, PromptFormat.DIET));
        List<String> avoid = splitList(PromptFormat.field(prompt, PromptFormat.AVOID));
        Set<String> exclude = new HashSet<>(splitList(PromptFormat.field(prompt, PromptFormat.EXCLUDE)));
        int maxTime = parseInt(PromptFormat.field(prompt, PromptFormat.MAX_TIME), Integer.MAX_VALUE);
        List<String> requestWords = new ArrayList<>();
        for (String w : words(PromptFormat.field(prompt, PromptFormat.REQUEST))) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w) && Days.normalize(w) == null && !w.matches("\\d+")) {
                requestWords.add(w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w);
            }
        }

        Map<Candidate, Integer> scores = new LinkedHashMap<>();
        for (Candidate c : parseCandidates(prompt)) {
            if (exclude.contains(c.id) || c.time > maxTime || !c.tags.containsAll(diets)) {
                continue;
            }
            boolean clash = false;
            for (String a : avoid) {
                String stem = a.endsWith("s") && a.length() > 3 ? a.substring(0, a.length() - 1) : a;
                if (!stem.isEmpty() && c.text.contains(stem)) {
                    clash = true;
                }
            }
            if (clash) {
                continue;
            }
            int score = 0;
            for (String w : requestWords) {
                if (c.tags.contains(w)) {
                    score += 3;
                } else if (c.text.contains(w)) {
                    score += 2;
                }
            }
            scores.put(c, score);
        }
        List<Candidate> ranked = new ArrayList<>(scores.keySet());
        // stable sort keeps the strategy's order for equal scores
        ranked.sort((a, b) -> Integer.compare(scores.get(b), scores.get(a)));
        return ranked;
    }

    private static List<Candidate> parseCandidates(String prompt) {
        List<Candidate> out = new ArrayList<>();
        boolean inList = false;
        for (String line : prompt.split("\n")) {
            String t = line.trim();
            if (t.startsWith(PromptFormat.CANDIDATES + ":")) {
                inList = true;
                continue;
            }
            if (!inList) {
                continue;
            }
            if (!t.startsWith("-")) {
                if (t.contains(":")) {
                    break; // next section
                }
                continue;
            }
            // - id | name | 25 min | tags: a, b | ingredients: x, y
            String[] parts = t.substring(1).split("\\|");
            if (parts.length < 3) {
                continue;
            }
            Candidate c = new Candidate();
            c.id = parts[0].trim();
            c.text = t.toLowerCase(Locale.ROOT);
            c.time = parseInt(parts[2].replaceAll("[^0-9]", ""), 0);
            for (String p : parts) {
                String part = p.trim().toLowerCase(Locale.ROOT);
                if (part.startsWith("tags:")) {
                    c.tags.addAll(splitList(part.substring(5)));
                }
            }
            out.add(c);
        }
        return out;
    }

    private static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        for (String s : value.split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && !t.equals("none")) {
                out.add(t);
            }
        }
        // day names keep their capitalisation
        for (int i = 0; i < out.size(); i++) {
            String day = Days.normalize(out.get(i));
            if (day != null && day.equalsIgnoreCase(out.get(i))) {
                out.set(i, day);
            }
        }
        return out;
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9'-]+")) {
            String t = w.replace("'s", "");
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class Candidate {
        private String id;
        private String text;
        private int time;
        private final Set<String> tags = new HashSet<>();
    }
}
