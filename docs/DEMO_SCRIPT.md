# SmartPlate — feature demonstration script (Stage 2 videos)

A step-by-step plan for recording the feature videos the Stage 2 instructions require: every feature
(F01–F11) gets a labelled segment showing how it is invoked, the input, the running application, the
result, where AI is involved, and an error or alternative case. Together the recordings show both the
GUI and the CLI.

Every error message quoted below was checked against the current application.

## Before recording

1. **Build:** `mvn package` (all tests must pass).
2. **Model:** use a **real model** for the AI features (F04–F06, F11), so the video does not look like
   hard-coded answers. In `.env` set either `GEMINI_API_KEY` + `GEMINI_MODEL`, or `OLLAMA_MODEL=gemma4:e4b`
   with Ollama running. Local models can take a few seconds per answer; that is fine.
3. **Never show secrets:** do not open `.env` on screen, close terminals that printed a key, and do not
   type a key while recording. If one is ever shown, revoke it and record again.
4. **Window:** start the GUI from the project folder (`java -jar target/smartplate-1.0.0.jar`, or the
   VS Code task *SmartPlate: run app*). Use a large font in the terminal for the CLI parts.
5. **Format:** one video with chapters, or one short video per feature. On YouTube (unlisted is fine)
   or OneDrive/Google Drive, make sure the link opens **without an access request**. For each segment,
   note the start time; a timestamped link looks like `https://youtu.be/VIDEO_ID?t=125`.
6. **Say at the start of each segment:** "F0x — feature name", and in one sentence what it does and
   whether AI is involved.

Suggested order (about 12–15 minutes in total): F01 → F02 → F03 → F11 → F04 → F05 → F06 → F09 → F10 →
F07 → F08, then the CLI segment.

## GUI segments

### F01 — Preference Profile Management (deterministic)
1. **Preferences** tab. Tick *vegetarian*; allergies `peanuts`; disliked `mushrooms`; max time `30`;
   servings `2` → **Save preferences** → green "saved" message.
2. Error case: allergies `peanuts, 123` and servings `0` → **Save** → red banner "The profile was not
   saved…", both fields outlined; say that the previous profile stays active.
3. Optional: disliked `jalapeño` is accepted (accented letters work).

### F02 — Pantry Inventory Management (deterministic)
1. **Pantry** tab. Add `rice`, `500`, `g` → rice goes from 1 kg to **1.5 kg** (compatible units merge).
2. Update `olive oil` to `0` → error next to the quantity field.
3. Remove an item; then try to remove `saffron` → "'saffron' is not in the pantry."

### F03 — Recipe Search (deterministic)
1. **Recipes** tab. Search `spinach` → 4 recipes (name, tag or ingredient match).
2. Search with an empty box → "Please enter a search query."

### F11 — AI Model Selection (new)
1. Point to the **AI model** box at the top right; open it: Mock, Claude, Gemini, Ollama.
2. Choose **Gemini** or **Ollama** → the box shows the exact model, e.g. "Ollama (gemma4:e4b)".
3. Error case: choose a provider without a key (e.g. **Claude** if no Anthropic key) → the box turns
   red, the previous model stays, and the message says which variable to add to `.env`.
4. Later, during F05, point out that the **Strategy** and **AI model** boxes are greyed out while the
   request runs.

### F04 — AI Recipe Recommendation (hybrid: AI chooses, Java validates)
1. **AI Assistant** tab. Request `3 quick vegetarian dinners using rice` → **Recommend**.
2. Walk through the **Agent trace**: constraints read from the request, recipes retrieved, candidates
   shortlisted by the strategy, the AI's answer, validation. Say: the AI only chooses among these
   recipes; Java checks each one.
3. Alternative: switch **Strategy** to *fastest* and recommend again (Strategy pattern at run time).
4. Error case: `vegan dinners under 5 minutes` → "No recipe in the repository satisfies your preferences
   and request…", reported **before** the AI is asked.

### F05 — AI Weekly Meal Planning (hybrid)
1. Request `Plan 5 dinners, avoid red onion and mushrooms, under 40 minutes` → **Create weekly plan**.
2. Show the plan table and the trace line `avoid red onion, mushrooms`; mention that every day was
   validated, and that a bad AI answer would be revised once.
3. Error case: `Plan 8 dinners` → "A plan must have 5 to 7 days (one dinner per day)…".

### F06 — Meal Plan Editing and Undo (hybrid + Command pattern)
1. With the plan from F05: `Swap Tuesday for something with spinach` → **Edit plan** → Tuesday is
   tagged **CHANGED**, the Result card shows *before → now*.
2. **Undo** → Tuesday is restored.
3. Alternative: `swap the sun-dried tomato dish for pasta on Wednesday` → Wednesday changes (the word
   "sun-dried" is not read as Sunday).
4. Error case (5-day plan): `Swap Sunday for pasta` → "The plan has no meal on Sunday."

### F09 — Nutrition Information (deterministic, Composite)
1. **Plan nutrition** → totals and per-day bars for the whole plan.
2. **Recipes** tab: the selected recipe's total and per-serving nutrition.
3. Error case (CLI or a fresh start): plan nutrition without a plan → "There is no meal plan yet."

### F10 — Shopping List Generation (deterministic, Composite)
1. **Shopping list** → items for the plan **minus the pantry**, "Covered by your pantry" line, whole
   pieces (note "Counted items (pcs) have been rounded up…" when it applies).
2. Copy or export the list.
3. Error case: before any plan, the button is disabled / the CLI says "There is no meal plan yet."

### F07 — Serving-Size Adjustment (deterministic)
1. **Recipes** tab: select *Vegetable Fried Rice*, servings `4` → **Scale** → quantities double; the
   recipe is shown as a working copy until **Reset**.
2. Error case (CLI): `scale r01 0` → "Servings must be at least 1."

### F08 — Ingredient Substitution (deterministic)
1. In **Preferences**, add the dislike `milk` and save. In **Recipes**, select *Spinach and Cheese
   Omelette*, ingredient `milk` → **Substitute** → **oat milk** (allowed even though milk is disliked).
2. Alternative: in a recipe with butter, substitute `butter` → olive oil, quantity converted.
3. Error case (CLI): `substitute r01 carrot` → "No substitute is known for carrot."

## CLI segment (shows the CLI working; about 2 minutes)

```text
java -jar target/smartplate-1.0.0.jar --cli
help
profile set diet=vegetarian allergies=peanuts dislikes=mushrooms time=30
pantry add rice 500 g
search spinach
recommend 2 quick dinners using rice
trace
plan 5 dinners, avoid red onion
edit swap Tuesday for something with spinach
undo
undo                      (error: "Nothing to undo.")
scale r01 4
substitute r01 soy sauce
nutrition plan
shopping
plan 9 dinners            (error: 5 to 7 days)
quit
```
The first line of the CLI shows which AI model is in use (from `.env`); start with `--mock` to show
the offline mode.

## After recording
Paste each link with its timestamp into **§7 Demonstration index** of `docs/STAGE2_REPORT.md` (and the
"Video" row of each feature in §2 if you like), then regenerate `stage2-report.pdf` / `.docx`.
