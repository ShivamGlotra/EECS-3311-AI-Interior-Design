package com.smartplate.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The shopping list produced by F10, plus notes about skipped lines. */
public class ShoppingList {

    private final List<ShoppingItem> items = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public void addItem(ShoppingItem item) {
        items.add(item);
    }

    public void addNote(String note) {
        notes.add(note);
    }

    /**
     * Merges duplicate lines: the same ingredient in compatible units becomes one
     * line
     * (in the family's base unit, shown as kg / l when large). Incompatible units,
     * for
     * example g and pcs of the same ingredient, stay as separate lines.
     */
    public void consolidate() {
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, ShoppingItem> firstSeen = new LinkedHashMap<>();
        for (ShoppingItem item : items) {
            Units.Family family = Units.family(item.getUnit());
            String key = item.getName() + "|" + family;
            totals.computeIfAbsent(key, k -> new double[1])[0] += Units.toBase(item.getQuantity(), item.getUnit());
            firstSeen.putIfAbsent(key, item);
        }
        items.clear();
        for (Map.Entry<String, double[]> e : totals.entrySet()) {
            ShoppingItem first = firstSeen.get(e.getKey());
            Units.Family family = Units.family(first.getUnit());
            double base = e.getValue()[0];
            String unit = Units.baseUnit(family);
            double quantity = base;
            if (family != Units.Family.COUNT && base >= 1000) {
                unit = family == Units.Family.MASS ? "kg" : "l";
                quantity = base / 1000.0;
            }
            items.add(new ShoppingItem(first.getIngredient(), quantity, unit));
        }
    }

    /**
     * Rounds up the quantities of all items in the shopping list that are in the
     * COUNT family.
     * 
     * @return true if any quantities were rounded up, false otherwise
     */
    public boolean roundUpQuantities() {
        boolean rounded = false;
        for (int i = 0; i < items.size(); i++) {
            ShoppingItem item = items.get(i);
            if (Units.family(item.getUnit()) == Units.Family.COUNT) {
                double quantity = item.getQuantity();
                double whole = Math.ceil(quantity - 1e-9); // the 1e-9 keeps 3.0000000001 at 3, not 4
                if (Math.abs(whole - quantity) > 1e-9) {
                    rounded = true;
                }
                items.set(i, new ShoppingItem(item.getIngredient(), whole, item.getUnit()));
            }
        }
        return rounded;
    }

    public List<ShoppingItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<String> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("Nothing to buy: the pantry covers the whole plan.\n");
        }
        for (ShoppingItem item : items) {
            sb.append("[ ] ").append(item).append('\n');
        }
        for (String note : notes) {
            sb.append("Note: ").append(note).append('\n');
        }
        return sb.toString().trim();
    }
}
