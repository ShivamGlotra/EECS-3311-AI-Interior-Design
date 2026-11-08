package com.smartplate.presentation.gui;

import com.smartplate.ai.MockAIModel;
import com.smartplate.domain.PantryAction;
import com.smartplate.domain.PantryItem;
import com.smartplate.facade.SmartRecipeFacade;
import com.smartplate.presentation.MealPlannerGUI;
import com.smartplate.service.SampleData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests of the redesigned Swing panels. AI requests run in place
 * (TaskRunner.direct())
 * with the offline Mock AI, so every check is deterministic.
 */
class GuiPanelsTest {

    private SmartRecipeFacade facade;

    @BeforeAll
    static void theme() {
        Theme.install();
    }

    @BeforeEach
    void setUp() {
        facade = SmartRecipeFacade.create(new MockAIModel());
        for (PantryItem item : SampleData.pantry()) {
            facade.managePantry(PantryAction.ADD, item);
        }
    }

    // ------------------------------------------------------------------ AI
    // Assistant (F04–F06, F09, F10)

    @Test
    void assistantStartsWithTheEmptyStateAndDisabledPlanActions() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        assertEquals(0, panel.planModel().getRowCount());
        assertFalse(panel.isEditEnabled());
        assertFalse(panel.isUndoEnabled());
        assertEquals(AssistantPanel.NO_REQUEST_YET, panel.traceText());
    }

    @Test
    void rejectedRequestShowsTheErrorAndSaysNothingWasSentToTheAI() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        panel.requestField().setText("Plan 9 days of dinners");
        panel.createPlan();
        assertEquals(AlertBox.Kind.ERROR, panel.resultBox().getKind());
        assertTrue(panel.resultBox().getMessage().contains("5 to 7 days"));
        assertEquals(AssistantPanel.NOTHING_SENT, panel.traceText());
        assertEquals(0, panel.planModel().getRowCount());
    }

    @Test
    void blankRequestIsRejectedBeforeCallingTheFacade() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        panel.requestField().setText("   ");
        panel.recommend();
        assertEquals(AlertBox.Kind.ERROR, panel.resultBox().getKind());
        assertEquals("Please type a request.", panel.resultBox().getMessage());
    }

    @Test
    void recommendShowsValidatedRecipesAndTheAgentTrace() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        panel.requestField().setText("3 quick dinners using rice");
        panel.recommend();
        assertEquals(AlertBox.Kind.SUCCESS, panel.resultBox().getKind());
        assertTrue(panel.resultBox().getMessage().startsWith("3 recipes recommended"));
        assertTrue(panel.traceText().startsWith("1. Request: \"3 quick dinners using rice\""));
    }

    @Test
    void createEditAndUndoMarkTheChangedDay() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        panel.requestField().setText("weekday dinners");
        panel.createPlan();
        assertEquals(5, panel.planModel().getRowCount());
        assertTrue(panel.isEditEnabled());
        assertFalse(panel.isUndoEnabled());

        String mondayBefore = String.valueOf(panel.planModel().getValueAt(0, 1));
        panel.requestField().setText("swap Monday");
        panel.editPlan();
        assertEquals("Monday", panel.changedDay());
        assertTrue(panel.isUndoEnabled());
        assertTrue(panel.traceText().contains("Created command: Monday:"));
        assertFalse(mondayBefore.equals(panel.planModel().getValueAt(0, 1)));

        panel.undo();
        assertNull(panel.changedDay());
        assertFalse(panel.isUndoEnabled());
        assertEquals("Last change undone.", panel.resultBox().getMessage());
        assertEquals(mondayBefore, panel.planModel().getValueAt(0, 1));
    }

    @Test
    void shoppingListAndNutritionViewsNeedAPlan() {
        AssistantPanel panel = new AssistantPanel(facade, TaskRunner.direct());
        panel.showSide(AssistantPanel.SIDE_SHOPPING);
        assertEquals(AssistantPanel.SIDE_RESULTS, panel.sideView()); // no plan yet: stays on results

        panel.requestField().setText("weekday dinners");
        panel.createPlan();
        panel.showSide(AssistantPanel.SIDE_SHOPPING);
        assertEquals(AssistantPanel.SIDE_SHOPPING, panel.sideView());
        assertTrue(panel.shoppingRowCount() > 0);
        panel.showSide(AssistantPanel.SIDE_NUTRITION);
        assertEquals(AssistantPanel.SIDE_NUTRITION, panel.sideView());
    }

    // ------------------------------------------------------------------ Recipes
    // (F03, F07–F09)

    @Test
    void theWindowIsToldWhenAnAIRequestStartsAndEnds() {
        List<Runnable> pending = new ArrayList<>();
        TaskRunner later = new TaskRunner() {
            @Override
            public <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<RuntimeException> onFailure) {
                pending.add(() -> TaskRunner.direct().run(task, onSuccess, onFailure));
            }
        };

        AssistantPanel panel = new AssistantPanel(facade, later);
        List<Boolean> busyStates = new ArrayList<>();
        panel.onBusyChange(busyStates::add);

        panel.requestField().setText("Weekly dinners");
        panel.createPlan();
        assertEquals(List.of(true), busyStates); // request running but windows locks the two combo boxes
        pending.get(0).run(); // run the AI answers
        assertEquals(List.of(true, false), busyStates); // request done, window unlocks the combo boxes
    }

    @Test
    void recipesSearchScaleSubstituteAndReset() {
        RecipesPanel panel = new RecipesPanel(facade);
        assertEquals(30, panel.resultsModel().getRowCount());

        panel.queryField().setText("spinach");
        panel.search();
        assertEquals(4, panel.resultsModel().getRowCount());

        panel.select("r13");
        assertFalse(panel.isWorkingCopy());
        panel.servingsSpinner().setValue(4);
        panel.scale();
        assertTrue(panel.isWorkingCopy());
        assertEquals(4, panel.currentRecipe().getServings());

        panel.ingredientBox().setSelectedItem("butter");
        panel.substitute();
        assertEquals(AlertBox.Kind.SUCCESS, panel.status().getKind());
        assertNotNull(panel.currentRecipe().findIngredient("olive oil"));
        assertEquals(16, panel.currentRecipe().findIngredient("olive oil").getQuantity(), 1e-9);
        assertEquals("replaces butter", panel.substitutionNote("olive oil"));

        panel.reset();
        assertFalse(panel.isWorkingCopy());
        assertEquals(2, panel.currentRecipe().getServings());
    }

    @Test
    void emptySearchShowsTheFacadeMessage() {
        RecipesPanel panel = new RecipesPanel(facade);
        panel.queryField().setText("");
        panel.search();
        assertEquals("Please enter a search query.", panel.resultsSubtitle());
    }

    @Test
    void substitutionErrorsAreShownInline() {
        RecipesPanel panel = new RecipesPanel(facade);
        panel.select("r02"); // chickpea curry: no substitute is known for curry powder
        panel.ingredientBox().setSelectedItem("curry powder");
        panel.substitute();
        assertEquals(AlertBox.Kind.ERROR, panel.status().getKind());
        assertTrue(panel.status().getMessage().contains("No substitute"));
    }

    // ------------------------------------------------------------------ Pantry
    // (F02)

    @Test
    void pantryErrorsGoNextToTheRightField() {
        PantryPanel panel = new PantryPanel(facade);
        assertEquals(8, panel.model().getRowCount());

        panel.nameField().setText("olive oil");
        panel.quantityField().setText("0");
        panel.unitBox().setSelectedItem("ml");
        panel.apply(PantryAction.UPDATE);
        assertTrue(panel.messageText().contains("greater than zero"));
        assertTrue(panel.hasErrorOutline(panel.quantityField()));

        panel.nameField().setText("salt");
        panel.apply(PantryAction.REMOVE);
        assertTrue(panel.messageText().contains("not in the pantry"));
        assertTrue(panel.hasErrorOutline(panel.nameField()));
        assertFalse(panel.hasErrorOutline(panel.quantityField()));
    }

    @Test
    void pantryAddUpdatesTheTable() {
        PantryPanel panel = new PantryPanel(facade);
        panel.nameField().setText("Chickpeas");
        panel.quantityField().setText("400");
        panel.unitBox().setSelectedItem("g");
        panel.apply(PantryAction.ADD);
        assertEquals(9, panel.model().getRowCount());
        assertEquals("Added chickpeas.", panel.messageText());
        assertNotNull(facade.getPantry().findItem("chickpeas"));
    }

    // ------------------------------------------------------------------
    // Preferences (F01)

    @Test
    void invalidProfileIsReportedAndThePreviousProfileStays() {
        PreferencesPanel panel = new PreferencesPanel(facade);
        panel.allergiesField().setText("peanuts, 123");
        panel.servingsSpinner().setValue(0);
        panel.save();
        assertEquals(AlertBox.Kind.ERROR, panel.banner().getKind());
        assertTrue(panel.banner().getMessage().contains("Default servings must be at least 1."));
        assertTrue(panel.banner().getMessage().contains("Malformed allergy '123'"));
        assertTrue(panel.hasErrorOutline(panel.allergiesField()));
        assertTrue(panel.hasErrorOutline(panel.servingsSpinner()));
        assertEquals(2, facade.getProfile().getDefaultServings());
        assertTrue(facade.getProfile().getAllergies().isEmpty());
    }

    @Test
    void validProfileIsSaved() {
        PreferencesPanel panel = new PreferencesPanel(facade);
        panel.dietBox("vegetarian").setSelected(true);
        panel.allergiesField().setText("peanuts");
        panel.servingsSpinner().setValue(3);
        panel.save();
        assertEquals(AlertBox.Kind.SUCCESS, panel.banner().getKind());
        assertTrue(facade.getProfile().getDietaryPreferences().contains("vegetarian"));
        assertEquals(3, facade.getProfile().getDefaultServings());
        assertFalse(panel.hasErrorOutline(panel.allergiesField()));
    }

    // ------------------------------------------------------------------ window

    @Test
    void windowHasTheFourTabsFromTheDesign() {
        JComponent content = new MealPlannerGUI(facade).buildContent();
        JTabbedPane tabs = find(content, JTabbedPane.class);
        assertNotNull(tabs);
        assertEquals(4, tabs.getTabCount());
        assertEquals("AI Assistant", tabs.getTitleAt(0));
        assertEquals("Recipes", tabs.getTitleAt(1));
        assertEquals("Pantry", tabs.getTitleAt(2));
        assertEquals("Preferences", tabs.getTitleAt(3));
    }

    @Test
    void theAiModelSwitcherChangesModelOrExplainsWhyNot() {
        SmartRecipeFacade switchable = SmartRecipeFacade.create(new MockAIModel(),
                () -> Map.of("OLLAMA_MODEL", "gemma4:e4b"));
        JComponent content = new MealPlannerGUI(switchable).buildContent();
        JTabbedPane tabs = find(content, JTabbedPane.class);
        JComboBox<?> model = findNamed((Component) tabs.getClientProperty(
                FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT), "aiModel");
        assertNotNull(model, "the AI model switcher is in the top-right of the tab bar");
        assertEquals(List.of("mock", "anthropic", "gemini", "ollama"), switchable.getAIProviders());
        assertEquals("mock", model.getSelectedItem());

        model.setSelectedItem("ollama");
        assertEquals("Ollama (gemma4:e4b)", switchable.getAIModelName());
        assertNull(model.getClientProperty(FlatClientProperties.OUTLINE));

        model.setSelectedItem("gemini"); // no GEMINI_API_KEY in these settings
        assertEquals("ollama", model.getSelectedItem(), "the previous choice comes back");
        assertEquals("Ollama (gemma4:e4b)", switchable.getAIModelName());
        assertEquals(FlatClientProperties.OUTLINE_ERROR, model.getClientProperty(FlatClientProperties.OUTLINE));
        assertTrue(model.getToolTipText().contains("GEMINI_API_KEY"));
    }

    private static JComboBox<?> findNamed(Component c, String name) {
        if (c instanceof JComboBox && name.equals(c.getName())) {
            return (JComboBox<?>) c;
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                JComboBox<?> found = findNamed(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T find(Component c, Class<T> type) {
        if (type.isInstance(c)) {
            return type.cast(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
