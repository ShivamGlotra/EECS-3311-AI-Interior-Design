package com.smartplate.presentation;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartplate.domain.SmartPlateException;
import com.smartplate.facade.SmartRecipeFacade;
import com.smartplate.presentation.gui.AssistantPanel;
import com.smartplate.presentation.gui.Icons;
import com.smartplate.presentation.gui.PantryPanel;
import com.smartplate.presentation.gui.PreferencesPanel;
import com.smartplate.presentation.gui.RecipesPanel;
import com.smartplate.presentation.gui.Theme;
import com.smartplate.presentation.gui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.util.Map;

/**
 * Swing desktop interface ("Clean Slate" design, light mode). Like the CLI it
 * calls only
 * SmartRecipeFacade; each tab is its own panel in
 * {@code com.smartplate.presentation.gui}:
 * AI Assistant (F04–F06, F09/F10 for the plan), Recipes (F03, F07–F09), Pantry
 * (F02) and
 * Preferences (F01).
 */
public class MealPlannerGUI {

    /** How each AI provider from the facade is shown in the model switcher. */
    private static final Map<String, String> PROVIDER_LABELS = Map.of(
            "mock", "Mock (offline)", "anthropic", "Claude", "gemini", "Gemini", "ollama", "Ollama (local)");

    private final SmartRecipeFacade facade;
    private boolean revertingModel;

    public MealPlannerGUI(SmartRecipeFacade facade) {
        this.facade = facade;
    }

    /** Builds and shows the window on the Swing event thread. */
    public void start() {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            JFrame frame = new JFrame("SmartPlate - AI Meal Planning Agent");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(buildContent());
            frame.setMinimumSize(new Dimension(1100, 720));
            frame.setSize(1280, 820);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /**
     * The whole window content: a tab bar with the logo, the four tabs, the
     * strategy and AI model switchers.
     */
    public JComponent buildContent() {
        Theme.install();
        AssistantPanel assistant = new AssistantPanel(facade);
        RecipesPanel recipes = new RecipesPanel(facade);
        PantryPanel pantry = new PantryPanel(facade);
        PreferencesPanel preferences = new PreferencesPanel(facade);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(Theme.SURFACE);
        tabs.addTab("AI Assistant", assistant);
        tabs.addTab("Recipes", recipes);
        tabs.addTab("Pantry", pantry);
        tabs.addTab("Preferences", preferences);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_AREA_ALIGNMENT,
                FlatClientProperties.TABBED_PANE_ALIGN_LEADING);
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_LEADING_COMPONENT, logo());
        tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, statusArea(assistant));
        tabs.addChangeListener(e -> {
            // other tabs may have changed the pantry or profile; re-read them from the
            // facade
            if (tabs.getSelectedComponent() == pantry) {
                pantry.refresh();
            } else if (tabs.getSelectedComponent() == preferences) {
                preferences.load();
            } else if (tabs.getSelectedComponent() == assistant) {
                assistant.refreshPlan();
            }
        });

        JPanel root = new JPanel(new java.awt.BorderLayout());
        root.setBackground(Theme.SURFACE);
        root.add(tabs, java.awt.BorderLayout.CENTER);
        return root;
    }

    private JComponent logo() {
        JLabel logo = new JLabel("SmartPlate", Icons.logo(26, Theme.ACCENT), JLabel.LEFT);
        logo.setIconTextGap(10);
        logo.setFont(Theme.semibold(17f));
        logo.setForeground(Theme.TEXT);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 28));
        logo.setOpaque(true);
        logo.setBackground(Theme.SURFACE);
        return logo;
    }

    /**
     * Strategy selector (Strategy pattern) and AI model selector (Adapter pattern),
     * both switchable at run time.
     */
    private JComponent statusArea(AssistantPanel assistant) {
        JLabel label = Ui.fieldLabel("Strategy");
        JComboBox<String> strategy = new JComboBox<>(new String[] { "pantry-first", "fastest" });
        strategy.setSelectedItem(facade.getStrategyName());
        strategy.setToolTipText("How candidate recipes are shortlisted before the AI chooses");
        label.setLabelFor(strategy);
        strategy.addActionListener(e -> {
            try {
                facade.setStrategy((String) strategy.getSelectedItem());
            } catch (SmartPlateException ex) {
                strategy.setSelectedItem(facade.getStrategyName());
            }
        });
        JLabel modelLabel = Ui.fieldLabel("AI model");
        JComboBox<String> model = modelSwitcher();
        modelLabel.setLabelFor(model);
        // while an AI request runs, the model and the strategy stay as they were when
        // it started
        assistant.onBusyChange(busy -> {
            strategy.setEnabled(!busy);
            model.setEnabled(!busy);
        });
        JPanel area = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
        area.add(label);
        area.add(strategy);
        area.add(Box.createHorizontalStrut(8));
        area.add(modelLabel);
        area.add(model);
        JPanel wrap = new JPanel(new java.awt.GridBagLayout());
        wrap.setBackground(Theme.SURFACE); // FlatLaf stretches this over the rest of the tab bar
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.EAST;
        c.weightx = 1;
        wrap.add(area, c); // right-aligned and vertically centred in the tab bar
        return wrap;
    }

    /**
     * Lists the providers (Mock, Claude, Gemini, Ollama). Choosing one asks the
     * facade to switch;
     * if that fails (e.g. no API key in .env) the previous choice comes back and
     * the reason is shown.
     * The closed box shows the exact model in use, e.g. "Ollama (gemma4:e4b)".
     */
    private JComboBox<String> modelSwitcher() {
        JComboBox<String> model = new JComboBox<>(facade.getAIProviders().toArray(new String[0]));
        model.setName("aiModel");
        model.setSelectedItem(facade.getAIProvider());
        model.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                // index -1 is the closed box: show the exact model; the open list shows
                // provider names
                String text = index < 0 ? modelDisplayName()
                        : PROVIDER_LABELS.getOrDefault(value, String.valueOf(value));
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        model.setToolTipText("AI in use: " + facade.getAIModelName());
        fitToModelName(model);
        model.addActionListener(e -> {
            if (revertingModel) {
                return;
            }
            String chosen = (String) model.getSelectedItem();
            try {
                facade.setAIProvider(chosen);
                model.putClientProperty(FlatClientProperties.OUTLINE, null);
                model.setToolTipText("AI in use: " + facade.getAIModelName());
            } catch (SmartPlateException ex) {
                revertingModel = true;
                model.setSelectedItem(facade.getAIProvider());
                revertingModel = false;
                model.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
                model.setToolTipText(ex.getMessage());
                if (!GraphicsEnvironment.isHeadless()) {
                    JOptionPane.showMessageDialog(model, ex.getMessage() + "\nSee .env.example in the project folder.",
                            "Cannot switch to " + PROVIDER_LABELS.getOrDefault(chosen, chosen),
                            JOptionPane.WARNING_MESSAGE);
                }
            }
            fitToModelName(model);
        });
        return model;
    }

    /**
     * Swing caches the box width; a new prototype value makes it measure the
     * current model name again.
     */
    private void fitToModelName(JComboBox<String> model) {
        model.setPrototypeDisplayValue(new String(facade.getAIModelName())); // new instance: always a change
        model.revalidate();
    }

    private String modelDisplayName() {
        String name = facade.getAIModelName();
        return name.startsWith("Mock") ? "Mock (offline)" : name;
    }
}
