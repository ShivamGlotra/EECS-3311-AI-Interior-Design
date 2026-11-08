package com.smartplate.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chooses which AIModel implementation SmartPlate uses (Adapter pattern -
 * client side).
 *
 * <p>
 * Selected by the environment variable {@code SMARTPLATE_AI_PROVIDER}:
 * <ul>
 * <li>{@code mock} - offline MockAIModel</li>
 * <li>{@code anthropic} - AIAdapter over AIClient (needs
 * ANTHROPIC_API_KEY)</li>
 * <li>{@code gemini} - GeminiAdapter (needs GEMINI_API_KEY; GEMINI_MODEL is
 * optional)</li>
 * <li>{@code ollama} - OllamaAdapter, a local model (OLLAMA_MODEL and
 * OLLAMA_URL are optional)</li>
 * <li>{@code auto} (default) - anthropic if its key is set, else gemini if its
 * key is set, otherwise mock</li>
 * </ul>
 * Ollama is never picked automatically, so a stray local server cannot change
 * behaviour silently.
 * The GUI's model switcher uses {@link #PROVIDERS}, {@link #missingSetting} and
 * {@link #forProvider}.
 */
public final class AIModelFactory {

    public static final String PROVIDER_VARIABLE = "SMARTPLATE_AI_PROVIDER";

    public static final String MOCK = "mock";
    public static final String ANTHROPIC = "anthropic";
    public static final String GEMINI = "gemini";
    public static final String OLLAMA = "ollama";
    /** Every provider that can be chosen, in the order the GUI lists them. */
    public static final List<String> PROVIDERS = List.of(MOCK, ANTHROPIC, GEMINI, OLLAMA);

    private AIModelFactory() {
    }

    /**
     * The real environment, plus a local git-ignored .env file for secrets (see
     * EnvFile).
     */
    public static AIModel fromEnvironment() {
        return create(EnvFile.load());
    }

    /**
     * Takes the environment as a map so tests do not depend on the real
     * environment.
     */
    public static AIModel create(Map<String, String> env) {
        String provider = value(env, PROVIDER_VARIABLE, "auto").toLowerCase(Locale.ROOT);
        if (provider.equals("auto")) {
            if (missingSetting(ANTHROPIC, env) == null) {
                return forProvider(ANTHROPIC, env);
            }
            return missingSetting(GEMINI, env) == null ? forProvider(GEMINI, env) : new MockAIModel();
        }
        return forProvider(provider, env);
    }

    /**
     * Builds the model for one provider name (mock, anthropic, gemini or ollama).
     */
    public static AIModel forProvider(String provider, Map<String, String> env) {
        String name = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        switch (name) {
            case MOCK:
                return new MockAIModel();
            case ANTHROPIC:
                return new AIAdapter(new AIClient(
                        env.get(AIClient.API_KEY_VARIABLE), env.get(AIClient.MODEL_VARIABLE)));
            case GEMINI:
                return new GeminiAdapter(env.get(GeminiAdapter.API_KEY_VARIABLE),
                        value(env, GeminiAdapter.MODEL_VARIABLE, GeminiAdapter.DEFAULT_MODEL));
            case OLLAMA:
                return new OllamaAdapter(
                        value(env, "OLLAMA_URL", OllamaAdapter.DEFAULT_BASE_URL),
                        value(env, "OLLAMA_MODEL", OllamaAdapter.DEFAULT_MODEL));
            default:
                throw new IllegalArgumentException("Unknown " + PROVIDER_VARIABLE + " '" + provider
                        + "'. Use mock, anthropic, gemini, ollama or auto.");
        }
    }

    /**
     * Why a provider cannot be used yet (e.g. its API key is missing), or null if
     * it can.
     */
    public static String missingSetting(String provider, Map<String, String> env) {
        String name = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (name.equals(ANTHROPIC) && value(env, AIClient.API_KEY_VARIABLE, "").isEmpty()) {
            return "Claude needs an API key: add " + AIClient.API_KEY_VARIABLE + " to the .env file.";
        }
        if (name.equals(GEMINI) && value(env, GeminiAdapter.API_KEY_VARIABLE, "").isEmpty()) {
            return "Gemini needs an API key: add " + GeminiAdapter.API_KEY_VARIABLE + " to the .env file.";
        }
        return null;
    }

    /** The provider name of a model built by this factory. */
    public static String providerOf(AIModel model) {
        if (model instanceof AIAdapter) {
            return ANTHROPIC;
        }
        if (model instanceof GeminiAdapter) {
            return GEMINI;
        }
        if (model instanceof OllamaAdapter) {
            return OLLAMA;
        }
        return MOCK;
    }

    private static String value(Map<String, String> env, String key, String fallback) {
        String v = env.get(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
