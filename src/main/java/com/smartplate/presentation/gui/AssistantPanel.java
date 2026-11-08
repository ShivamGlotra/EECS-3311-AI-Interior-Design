package com.smartplate.presentation.gui;

import com.smartplate.domain.NutritionInfo;
import com.smartplate.domain.PantryItem;
import com.smartplate.domain.Recipe;
import com.smartplate.domain.RecipeIngredient;
import com.smartplate.domain.ShoppingItem;
import com.smartplate.domain.ShoppingList;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.domain.Units;
import com.smartplate.facade.SmartRecipeFacade;
import com.smartplate.plan.MealPlan;
import com.smartplate.plan.PlannedMeal;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * The AI Assistant tab: F04 recommendations, F05 weekly plan, F06 edit and
 * undo, and the plan's
 * F09 nutrition and F10 shopping list. Calls only SmartRecipeFacade.
 */
public class AssistantPanel extends JPanel {

    static final String DEFAULT_REQUEST = "Plan dinners for this week, use some of my pantry items, avoid mushrooms, keep meals under 30 minutes";
    static final String NOTHING_SENT = "Nothing was sent to the AI — the request was rejected before planning started.";
    static final String NO_REQUEST_YET = "No AI request yet. Type a request above and choose an action.";

    private static final String PLAN_EMPTY = "empty";
    private static final String PLAN_TABLE = "table";
    static final String SIDE_RESULTS = "results";
    static final String SIDE_SHOPPING = "shopping";
    static final String SIDE_NUTRITION = "nutrition";

    private final SmartRecipeFacade facade;
    private final TaskRunner runner;

    private final JTextField request = new JTextField(DEFAULT_REQUEST);
    private final JButton recommendButton = Ui.secondary("Recommend");
    private final JButton createButton = Ui.primary("Create weekly plan");
    private final JButton editButton = Ui.secondary("Edit plan");
    private final JButton undoButton = Ui.secondary("Undo");
    private final JButton nutritionButton = Ui.secondary("Plan nutrition");
    private final JButton shoppingButton = Ui.secondary("Shopping list");

    private final DefaultTableModel planModel = Ui.readOnlyModel("DAY", "RECIPE", "ID", "SERVINGS", "MINUTES",
            "KCAL / SERVING");
    private final JTable planTable = Ui.table(planModel);
    private final JLabel planSubtitle = Ui.muted(" ");
    private final CardLayout planCards = new CardLayout();
    private final JPanel planBody = new JPanel(planCards);

    private final CardLayout sideCards = new CardLayout();
    private final JPanel side = new JPanel(sideCards);
    private String sideView = SIDE_RESULTS;

    private final AlertBox result = new AlertBox();
    private final JPanel resultExtra = Ui.stack();
    private final JTextArea trace = new JTextArea();

    private final JPanel shoppingRows = Ui.stack();
    private final JLabel shoppingSubtitle = Ui.muted(" ");
    private final JLabel shoppingCovered = new JLabel();
    private String shoppingText = "";

    private final JPanel nutritionBody = Ui.stack();

    private String changedDay;
    private boolean busy;
    private Consumer<Boolean> busyListener = value -> {
    };

    public AssistantPanel(SmartRecipeFacade facade) {
        this(facade, TaskRunner.background());
    }

    AssistantPanel(SmartRecipeFacade facade, TaskRunner runner) {
        super(new BorderLayout(0, 16));
        this.facade = facade;
        this.runner = runner;
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        add(buildRequestBar(), BorderLayout.NORTH);
        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);
        main.add(buildPlanCard(), BorderLayout.CENTER);
        main.add(buildSide(), BorderLayout.EAST);
        add(main, BorderLayout.CENTER);

        result.show(AlertBox.Kind.INFO, null, "Type a request above, then choose Recommend or Create weekly plan.");
        trace.setText(NO_REQUEST_YET);
        refreshPlan();
    }

    // ================================================================== layout

    private JComponent buildRequestBar() {
        RoundedPanel bar = new RoundedPanel(new BorderLayout(12, 0), Theme.SURFACE, Theme.BORDER, 12, 12);
        JLabel label = Ui.fieldLabel("Request");
        label.setLabelFor(request);
        request.setFont(Theme.regular(14f));
        request.setCaretPosition(0);
        request.addActionListener(e -> createButton.doClick());
        bar.add(label, BorderLayout.WEST);
        bar.add(request, BorderLayout.CENTER);
        bar.add(Ui.row(8, recommendButton, createButton, editButton, undoButton), BorderLayout.EAST);

        recommendButton.addActionListener(e -> recommend());
        createButton.addActionListener(e -> createPlan());
        editButton.addActionListener(e -> editPlan());
        undoButton.addActionListener(e -> undo());
        recommendButton.setToolTipText("F04: recipes that fit your profile and this request");
        createButton.setToolTipText("F05: a validated plan of 5 to 7 dinners");
        editButton.setToolTipText("F06: replace one day, e.g. \"swap Tuesday for something with spinach\"");
        undoButton.setToolTipText("F06: undo the last plan change");
        return bar;
    }

    private JComponent buildPlanCard() {
        RoundedPanel card = Ui.card();
        nutritionButton.addActionListener(e -> showSide(SIDE_NUTRITION));
        shoppingButton.addActionListener(e -> showSide(SIDE_SHOPPING));
        card.add(Ui.cardHeader("Current meal plan", planSubtitle, nutritionButton, shoppingButton), BorderLayout.NORTH);

        PlanRenderer renderer = new PlanRenderer();
        for (int i = 0; i < planModel.getColumnCount(); i++) {
            planTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
        int[] widths = { 110, 320, 70, 90, 90, 130 };
        for (int i = 0; i < widths.length; i++) {
            planTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        planTable.setToolTipText("Double-click a day to ask for a replacement");
        planTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && planTable.getSelectedRow() >= 0) {
                    prefillSwap(planTable.getSelectedRow());
                }
            }
        });

        planBody.setOpaque(false);
        planBody.add(buildEmptyState(), PLAN_EMPTY);
        planBody.add(Ui.scroll(planTable), PLAN_TABLE);
        card.add(planBody, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setOpaque(false);
        JPanel content = Ui.stack();
        JLabel icon = new JLabel(Icons.calendar(56, Theme.TEXT_DISABLED));
        JLabel title = Ui.heading("No meal plan yet", 17f);
        JLabel text = new JLabel(
                "<html><div style='text-align:center; width:360px'>Describe the week you want, then press "
                        + "<b>Create weekly plan</b>. Plans have 5 to 7 dinners, one per day.</div></html>");
        text.setFont(Theme.regular(14f));
        text.setForeground(Theme.TEXT_MUTED);
        JPanel chips = Ui.row(8,
                exampleChip("Weekday dinners, under 30 minutes"),
                exampleChip("7 vegetarian dinners using my pantry"),
                exampleChip("6 dinners for 4 people"));
        for (JComponent c : new JComponent[] { icon, title, text, chips }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        content.add(icon);
        content.add(Ui.gap(12));
        content.add(title);
        content.add(Ui.gap(8));
        content.add(text);
        content.add(Ui.gap(14));
        content.add(chips);
        empty.add(content);
        return empty;
    }

    private JButton exampleChip(String text) {
        JButton chip = Ui.chip(text);
        chip.addActionListener(e -> {
            request.setText(text);
            request.requestFocusInWindow();
        });
        return chip;
    }

    private JComponent buildSide() {
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(400, 10));

        // Result + agent trace
        JPanel results = new JPanel(new BorderLayout(0, 16));
        results.setOpaque(false);
        RoundedPanel resultCard = Ui.card();
        resultCard.add(Ui.cardHeader("Result", (String) null), BorderLayout.NORTH);
        JPanel resultBody = Ui.stack();
        resultBody.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
        result.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultExtra.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultBody.add(result);
        resultBody.add(resultExtra);
        resultCard.add(resultBody, BorderLayout.CENTER);
        results.add(resultCard, BorderLayout.NORTH);

        RoundedPanel traceCard = Ui.card();
        traceCard.add(Ui.cardHeader("Agent trace", "What the agent did for the last request"), BorderLayout.NORTH);
        trace.setEditable(false);
        trace.setLineWrap(true);
        trace.setWrapStyleWord(true);
        trace.setFont(Theme.mono(12f));
        trace.setForeground(Theme.TRACE_TEXT);
        trace.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        traceCard.add(Ui.scroll(trace), BorderLayout.CENTER);
        results.add(traceCard, BorderLayout.CENTER);
        side.add(results, SIDE_RESULTS);

        // Shopping list (F10)
        RoundedPanel shopping = Ui.card();
        JButton copy = Ui.secondary("Copy");
        JButton export = Ui.primary("Export .txt");
        copy.addActionListener(e -> copyShoppingList());
        export.addActionListener(e -> exportShoppingList());
        shopping.add(Ui.cardHeader("Shopping list", shoppingSubtitle, copy, export), BorderLayout.NORTH);
        shoppingRows.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        shopping.add(Ui.scroll(shoppingRows), BorderLayout.CENTER);
        JPanel shoppingFooter = Ui.stack();
        shoppingFooter.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 16));
        shoppingCovered.setFont(Theme.regular(12f));
        shoppingCovered.setForeground(Theme.ACCENT_PRESSED);
        shoppingCovered.setAlignmentX(Component.LEFT_ALIGNMENT);
        shoppingFooter.add(shoppingCovered);
        shoppingFooter.add(backLink());
        shopping.add(shoppingFooter, BorderLayout.SOUTH);
        side.add(shopping, SIDE_SHOPPING);

        // Plan nutrition (F09)
        RoundedPanel nutrition = Ui.card();
        nutrition.add(Ui.cardHeader("Plan nutrition", "Whole week, all servings"), BorderLayout.NORTH);
        nutritionBody.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        nutrition.add(Ui.scroll(nutritionBody), BorderLayout.CENTER);
        JPanel nutritionFooter = Ui.stack();
        nutritionFooter.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 16));
        nutritionFooter.add(backLink());
        nutrition.add(nutritionFooter, BorderLayout.SOUTH);
        side.add(nutrition, SIDE_NUTRITION);
        return side;
    }

    private JButton backLink() {
        JButton back = Ui.link("← Back to result and agent trace");
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.addActionListener(e -> showSide(SIDE_RESULTS));
        return back;
    }

    // ================================================================== actions

    /** F04. */
    void recommend() {
        String text = requestText();
        if (text == null) {
            return;
        }
        runAI(text, () -> facade.recommendRecipes(text), recipes -> {
            result.show(AlertBox.Kind.SUCCESS, null,
                    recipes.size() + " recipe" + (recipes.size() == 1 ? "" : "s")
                            + " recommended and validated against your profile.");
            resultExtra.removeAll();
            for (Recipe r : recipes) {
                resultExtra.add(Ui.gap(8));
                resultExtra.add(recipeRow(r));
            }
        });
    }

    /** F05. */
    void createPlan() {
        String text = requestText();
        if (text == null) {
            return;
        }
        runAI(text, () -> facade.createMealPlan(text), plan -> {
            changedDay = null;
            refreshPlan();
            result.show(AlertBox.Kind.SUCCESS, null, "New " + plan.size() + "-day plan created and validated.");
        });
    }

    /** F06: the agent picks a replacement; the facade runs it as a command. */
    void editPlan() {
        String text = requestText();
        if (text == null) {
            return;
        }
        Map<String, String> before = snapshot();
        runAI(text, () -> facade.editMealPlan(text), plan -> {
            Map<String, String> after = snapshot();
            changedDay = firstDifference(before, after);
            refreshPlan();
            result.show(AlertBox.Kind.SUCCESS, null, "Plan updated. Press Undo to revert.");
            if (changedDay != null) {
                resultExtra.add(Ui.gap(10));
                resultExtra.add(comparison(changedDay, "BEFORE", before.get(changedDay), "NOW", after.get(changedDay)));
            }
        });
    }

    /** F06 undo: no AI involved, so it runs immediately. */
    void undo() {
        Map<String, String> before = snapshot();
        resultExtra.removeAll();
        try {
            facade.undoLastChange();
        } catch (SmartPlateException e) {
            result.show(AlertBox.Kind.ERROR, "Could not undo.", e.getMessage());
            refreshPlan();
            return;
        }
        Map<String, String> after = snapshot();
        String day = firstDifference(before, after);
        changedDay = null;
        refreshPlan();
        result.show(AlertBox.Kind.SUCCESS, null, "Last change undone.");
        if (day != null) {
            resultExtra.add(Ui.gap(10));
            resultExtra.add(comparison(day, "UNDONE", before.get(day), "RESTORED", after.get(day)));
        }
        showSide(SIDE_RESULTS);
    }

    /**
     * Runs an AI request off the Swing thread, then shows the result and the agent
     * trace.
     */
    private <T> void runAI(String text, Callable<T> task, Consumer<T> onSuccess) {
        setBusy(true);
        showSide(SIDE_RESULTS);
        resultExtra.removeAll();
        result.show(AlertBox.Kind.INFO, null, "Working… asking " + facade.getAIModelName() + ".");
        runner.run(task, value -> {
            setBusy(false);
            resultExtra.removeAll();
            onSuccess.accept(value);
            showTrace(text, false);
            resultExtra.revalidate();
            resultExtra.repaint();
        }, error -> {
            setBusy(false);
            resultExtra.removeAll();
            result.show(AlertBox.Kind.ERROR, "Could not complete the request.", error.getMessage());
            showTrace(text, true);
            refreshPlan();
        });
    }

    private String requestText() {
        String text = request.getText().trim();
        if (text.isEmpty()) {
            showSide(SIDE_RESULTS);
            resultExtra.removeAll();
            result.show(AlertBox.Kind.ERROR, "Could not complete the request.", "Please type a request.");
            trace.setText(NOTHING_SENT);
            return null;
        }
        return text;
    }

    /**
     * Shows the agent's steps, numbered. Only trusts a trace that belongs to this
     * request.
     */
    private void showTrace(String text, boolean failed) {
        List<String> steps = facade.getLastAgentTrace();
        boolean fresh = !steps.isEmpty() && steps.get(0).contains("\"" + text + "\"");
        if (!fresh) {
            trace.setText(failed ? NOTHING_SENT : NO_REQUEST_YET);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append('\n');
        }
        trace.setText(sb.toString().trim());
        trace.setCaretPosition(0);
    }

    private void prefillSwap(int row) {
        String day = String.valueOf(planModel.getValueAt(row, 0));
        request.setText("Swap " + day + " for something ");
        request.requestFocusInWindow();
        request.setCaretPosition(request.getText().length());
    }

    // ================================================================== state

    /**
     * Tells the window when an AI request starts and ends, so it can lock the model
     * and strategy boxes.
     */
    public void onBusyChange(Consumer<Boolean> listener) {
        busyListener = listener == null ? value -> {
        } : listener;
    }

    private void setBusy(boolean value) {
        busy = value;
        updateButtons();
        busyListener.accept(value);
        setCursor(value ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : null);
    }

    private void updateButtons() {
        boolean hasPlan = facade.getCurrentPlan() != null && !facade.getCurrentPlan().isEmpty();
        recommendButton.setEnabled(!busy);
        createButton.setEnabled(!busy);
        editButton.setEnabled(!busy && hasPlan);
        undoButton.setEnabled(!busy && facade.canUndo());
        nutritionButton.setEnabled(hasPlan);
        shoppingButton.setEnabled(hasPlan);
    }

    /**
     * Re-reads the current plan from the facade into the table (or the empty
     * state).
     */
    public void refreshPlan() {
        MealPlan plan = facade.getCurrentPlan();
        planModel.setRowCount(0);
        if (plan == null || plan.isEmpty()) {
            planCards.show(planBody, PLAN_EMPTY);
            planSubtitle.setText("No plan yet");
            if (!SIDE_RESULTS.equals(sideView)) {
                showSide(SIDE_RESULTS);
            }
        } else {
            for (PlannedMeal meal : plan.getMeals()) {
                Recipe r = meal.getRecipe();
                NutritionInfo n = r.getNutritionPerServing();
                planModel.addRow(new Object[] { meal.getDay(), r.getName(), r.getId(), meal.getServings(),
                        meal.getCookingTime(), n == null ? "—" : String.format(Locale.ROOT, "%.0f", n.getCalories()) });
            }
            planCards.show(planBody, PLAN_TABLE);
            int servings = plan.getMeals().get(0).getServings();
            planSubtitle.setText(plan.size() + " dinners · " + servings + " servings each");
            if (SIDE_SHOPPING.equals(sideView)) {
                loadShoppingList();
            } else if (SIDE_NUTRITION.equals(sideView)) {
                loadNutrition();
            }
        }
        updateButtons();
    }

    void showSide(String view) {
        if (SIDE_SHOPPING.equals(view) && !loadShoppingList()) {
            return;
        }
        if (SIDE_NUTRITION.equals(view) && !loadNutrition()) {
            return;
        }
        sideView = view;
        sideCards.show(side, view);
    }

    private Map<String, String> snapshot() {
        Map<String, String> names = new LinkedHashMap<>();
        MealPlan plan = facade.getCurrentPlan();
        if (plan != null) {
            for (PlannedMeal meal : plan.getMeals()) {
                names.put(meal.getDay(), meal.getRecipe().getName() + "|" + meal.getRecipe().getId());
            }
        }
        return names;
    }

    private static String firstDifference(Map<String, String> before, Map<String, String> after) {
        for (Map.Entry<String, String> e : after.entrySet()) {
            if (!e.getValue().equals(before.get(e.getKey()))) {
                return e.getKey();
            }
        }
        return null;
    }

    // ================================================================== F10
    // shopping list

    private boolean loadShoppingList() {
        ShoppingList list;
        try {
            list = facade.generateShoppingList();
        } catch (SmartPlateException e) {
            result.show(AlertBox.Kind.ERROR, "No shopping list.", e.getMessage());
            return false;
        }
        shoppingRows.removeAll();
        StringBuilder text = new StringBuilder("SmartPlate shopping list\n");
        Set<String> toBuy = new LinkedHashSet<>();
        for (ShoppingItem item : list.getItems()) {
            toBuy.add(item.getName());
            String qty = Units.format(item.getQuantity(), item.getUnit());
            shoppingRows.add(shoppingRow(capitalize(item.getName()), qty));
            text.append("[ ] ").append(item.getName()).append(" - ").append(qty).append('\n');
        }
        if (list.getItems().isEmpty()) {
            JLabel none = Ui.muted("Nothing to buy: your pantry covers the whole plan.");
            shoppingRows.add(none);
        }
        for (String note : list.getNotes()) {
            JLabel l = Ui.muted("Note: " + note);
            l.setForeground(Theme.WARN_FG);
            shoppingRows.add(l);
            text.append("Note: ").append(note).append('\n');
        }
        Set<String> covered = new LinkedHashSet<>();
        for (RecipeIngredient ri : facade.getCurrentPlan().getIngredients()) {
            PantryItem have = facade.getPantry().findItem(ri.getName());
            if (have != null && !toBuy.contains(ri.getName())) {
                covered.add(ri.getName());
            }
        }
        shoppingSubtitle.setText(list.getItems().size() + " items · pantry subtracted");
        shoppingCovered.setText(covered.isEmpty() ? " "
                : "<html><div style='width:340px'>Covered by your pantry: " + String.join(", ", covered)
                        + "</div></html>");
        shoppingText = text.toString();
        shoppingRows.revalidate();
        shoppingRows.repaint();
        return true;
    }

    private JComponent shoppingRow(String name, String qty) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.ROW_LINE));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox box = new JCheckBox(name);
        box.setOpaque(false);
        box.setFont(Theme.regular(13f));
        JLabel amount = new JLabel(qty);
        amount.setFont(Theme.mono(12f));
        amount.setForeground(Theme.TEXT_MUTED);
        row.add(box, BorderLayout.CENTER);
        row.add(amount, BorderLayout.EAST);
        return row;
    }

    private void copyShoppingList() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(shoppingText), null);
            shoppingSubtitle.setText("Copied to the clipboard");
        } catch (RuntimeException e) {
            shoppingSubtitle.setText("Could not copy: " + e.getMessage());
        }
    }

    private void exportShoppingList() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("shopping-list.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), shoppingText, StandardCharsets.UTF_8);
            shoppingSubtitle.setText("Saved to " + chooser.getSelectedFile().getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the file: " + e.getMessage(),
                    "SmartPlate", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ================================================================== F09 plan
    // nutrition

    private boolean loadNutrition() {
        NutritionInfo total;
        try {
            total = facade.calculatePlanNutrition();
        } catch (SmartPlateException e) {
            result.show(AlertBox.Kind.ERROR, "No nutrition data.", e.getMessage());
            return false;
        }
        nutritionBody.removeAll();
        JPanel stats = new JPanel(new GridLayout(2, 2, 10, 10));
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(statBox("Calories", String.format(Locale.ROOT, "%,.0f kcal", total.getCalories())));
        stats.add(statBox("Protein", String.format(Locale.ROOT, "%,.0f g", total.getProtein())));
        stats.add(statBox("Carbohydrates", String.format(Locale.ROOT, "%,.0f g", total.getCarbohydrates())));
        stats.add(statBox("Fat", String.format(Locale.ROOT, "%,.0f g", total.getFat())));
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        nutritionBody.add(stats);
        nutritionBody.add(Ui.gap(16));
        JLabel perDay = Ui.heading("Calories per day", 13.5f);
        perDay.setAlignmentX(Component.LEFT_ALIGNMENT);
        nutritionBody.add(perDay);
        nutritionBody.add(Ui.gap(6));
        List<PlannedMeal> meals = facade.getCurrentPlan().getMeals();
        double max = 1;
        List<Double> values = new ArrayList<>();
        for (PlannedMeal m : meals) {
            double kcal = m.getNutrition().getCalories();
            values.add(kcal);
            max = Math.max(max, kcal);
        }
        for (int i = 0; i < meals.size(); i++) {
            nutritionBody.add(dayBar(meals.get(i).getDay().substring(0, 3), values.get(i), max));
        }
        nutritionBody.revalidate();
        nutritionBody.repaint();
        return true;
    }

    private JComponent statBox(String label, String value) {
        RoundedPanel box = new RoundedPanel(new BorderLayout(), Theme.SURFACE, Theme.BORDER, 10, 10);
        JPanel inner = Ui.stack();
        inner.add(Ui.muted(label));
        JLabel v = Ui.heading(value, 17f);
        inner.add(v);
        box.add(inner, BorderLayout.CENTER);
        return box;
    }

    private JComponent dayBar(String day, double kcal, double max) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        JLabel d = new JLabel(day);
        d.setFont(Theme.semibold(12f));
        d.setPreferredSize(new Dimension(36, 18));
        JLabel v = new JLabel(String.format(Locale.ROOT, "%,.0f kcal", kcal), SwingConstants.RIGHT);
        v.setFont(Theme.mono(11.5f));
        v.setForeground(Theme.TRACE_TEXT);
        v.setPreferredSize(new Dimension(80, 18));
        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                int h = 8;
                int y = (getHeight() - h) / 2;
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ROW_LINE);
                g2.fillRoundRect(0, y, getWidth(), h, h, h);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, y, (int) Math.round(getWidth() * kcal / max), h, h, h);
                g2.dispose();
            }
        };
        row.add(d, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    // ================================================================== small
    // views

    private JComponent recipeRow(Recipe r) {
        JPanel row = Ui.stack();
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = Ui.heading(r.getName(), 13.5f);
        JLabel meta = Ui.muted(r.getId() + " · " + r.getCookingTime() + " min · serves " + r.getServings()
                + (r.getDietaryTags().isEmpty() ? "" : " · " + String.join(", ", r.getDietaryTags())));
        row.add(name);
        row.add(meta);
        return row;
    }

    /** Before → after card for a changed day. Values are "name|id". */
    private JComponent comparison(String day, String beforeLabel, String before, String afterLabel, String after) {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        grid.add(dayBox(day.toUpperCase(Locale.ROOT) + " · " + beforeLabel, nameOf(before), true), c);
        c.weightx = 0;
        c.insets = new Insets(0, 8, 0, 8);
        grid.add(new JLabel(Icons.arrowRight(18, Theme.TEXT_MUTED)), c);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        grid.add(dayBox(day.toUpperCase(Locale.ROOT) + " · " + afterLabel, nameOf(after), false), c);
        return grid;
    }

    private static JComponent dayBox(String caption, String name, boolean old) {
        RoundedPanel box = new RoundedPanel(new BorderLayout(), old ? Theme.SURFACE : Theme.ACCENT_TINT,
                old ? Theme.BORDER : Theme.ACCENT, 10, 8);
        JPanel inner = Ui.stack();
        JLabel cap = new JLabel(caption);
        cap.setFont(Theme.semibold(10.5f));
        cap.setForeground(old ? Theme.TEXT_MUTED : Theme.ACCENT_PRESSED);
        JLabel n = new JLabel(old ? "<html><s>" + name + "</s></html>" : "<html>" + name + "</html>");
        n.setFont(old ? Theme.regular(13f) : Theme.semibold(13f));
        n.setForeground(old ? Theme.TEXT_MUTED : Theme.TEXT);
        inner.add(cap);
        inner.add(n);
        box.add(inner, BorderLayout.CENTER);
        return box;
    }

    private static String nameOf(String nameAndId) {
        return nameAndId == null ? "—" : nameAndId.substring(0, nameAndId.indexOf('|'));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Renders the plan table: padding, monospace ids, and a tinted "CHANGED" row
     * after an edit.
     */
    private final class PlanRenderer extends DefaultTableCellRenderer {
        private final JLabel changedTag = Ui.pill("CHANGED", Theme.ACCENT, Color.WHITE);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, false, row, column);
            boolean changed = changedDay != null && changedDay.equals(planModel.getValueAt(row, 0));
            Color bg = changed || selected ? Theme.ACCENT_TINT : Theme.SURFACE;
            label.setBackground(bg);
            label.setForeground(Theme.TEXT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            label.setFont(column == 2 ? Theme.mono(12.5f) : column == 0 ? Theme.semibold(13f) : Theme.regular(13f));
            if (column == 2) {
                label.setForeground(Theme.TEXT_MUTED);
            }
            if (changed && column == 1) {
                JPanel cell = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
                cell.setBackground(bg);
                cell.setBorder(BorderFactory.createEmptyBorder(9, 6, 0, 0));
                JLabel name = new JLabel(String.valueOf(value));
                name.setFont(Theme.regular(13f));
                name.setForeground(Theme.TEXT);
                cell.add(name);
                cell.add(changedTag);
                return cell;
            }
            return label;
        }
    }

    // ================================================================== accessors
    // for tests

    JTextField requestField() {
        return request;
    }

    DefaultTableModel planModel() {
        return planModel;
    }

    AlertBox resultBox() {
        return result;
    }

    String traceText() {
        return trace.getText();
    }

    String changedDay() {
        return changedDay;
    }

    String sideView() {
        return sideView;
    }

    boolean isEditEnabled() {
        return editButton.isEnabled();
    }

    boolean isUndoEnabled() {
        return undoButton.isEnabled();
    }

    int shoppingRowCount() {
        return shoppingRows.getComponentCount();
    }

    Font requestFont() {
        return request.getFont();
    }
}
