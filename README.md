# AI-Smart-Recipe-and-Meal-Planning-Agent

This project is code in java and is a customized AI agent that helps user in deciding their meals and those recipes.

**SmartPlate** is a Java desktop (Swing) and command-line application. An AI agent recommends recipes,
plans 5–7 dinners and edits the plan on request. The LLM only **chooses among existing recipes**, and
deterministic Java code checks every answer against your diet, allergies, dislikes and time limit
before you see it. It works offline with a built-in mock AI, or with Claude, Gemini or a local Ollama model.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 17 or newer (21 recommended) | `java -version` |
| Maven | 3.8 or newer | `mvn -v` |
| Optional: Ollama | any recent version, for a free local model | `ollama --version` |

## Install dependencies

Nothing to install by hand: Maven downloads every library (Anthropic Java SDK, FlatLaf, JUnit 5)
the first time you build. From the project folder:

```bash
mvn test       # downloads dependencies, compiles, runs the 134 JUnit tests (offline, no API key)
mvn package    # builds target/smartplate-1.0.0.jar (includes all dependencies)
```

## Local configuration (API keys) — optional

Without any configuration the app uses the offline **Mock AI**, so every feature works without a key.
To use a real model:

```bash
cp .env.example .env        # Windows: copy .env.example .env
```

Then edit `.env` (it is git-ignored, so your key is never committed):

| Model | Set in `.env` |
|---|---|
| Gemini | `GEMINI_API_KEY=<your key>` and `GEMINI_MODEL=gemini-flash-latest` (or another Gemini model) |
| Claude | `ANTHROPIC_API_KEY=<your key>` |
| Local Ollama | run `ollama pull gemma4:e4b` once, then `OLLAMA_MODEL=gemma4:e4b` (`OLLAMA_URL` defaults to `http://localhost:11434`) |

`SMARTPLATE_AI_PROVIDER` chooses the model at start-up: `auto` (default: Claude if its key is set,
else Gemini, else Mock), `mock`, `anthropic`, `gemini` or `ollama`. You can also switch at any time in
the **AI model** box at the top right of the window. Start the app **from the project folder** so it
finds `.env`.

## Run

```bash
java -jar target/smartplate-1.0.0.jar            # desktop GUI
java -jar target/smartplate-1.0.0.jar --cli      # command-line interface
java -jar target/smartplate-1.0.0.jar --mock     # force the offline Mock AI, whatever .env says
java -jar target/smartplate-1.0.0.jar --empty    # start with an empty pantry
```

Without building a jar: `mvn -q compile exec:java` (GUI) or `mvn -q compile exec:java -Dexec.args=--cli`.

**VS Code:** the same commands are ready-made tasks. Use *Terminal → Run Task…* → `SmartPlate: …`,
or the **Task Runner** view at the bottom of the Explorer (accept the recommended extensions when
VS Code offers them). Press F5 to run with the debugger.

## Example usage

### GUI
1. **Preferences** tab: tick *vegetarian*, type `peanuts` under allergies, set 30 minutes → **Save preferences**.
2. **Pantry** tab: add `rice`, `500`, `g` → **Add**.
3. **AI Assistant** tab: type `Plan 5 dinners, avoid red onion, under 30 minutes` → **Create weekly plan**.
   The plan appears with the agent trace (constraints, candidates, the AI's answer, validation).
4. Type `Swap Tuesday for something with spinach` → **Edit plan** (Tuesday is marked *CHANGED*) → **Undo**.
5. **Shopping list** and **Plan nutrition** buttons show F10 and F09 for the plan.
6. **Recipes** tab: search `omelette`, select a recipe, change servings → **Scale**, pick an ingredient → **Substitute**.

### CLI (real output, offline Mock AI)
```text
$ java -jar target/smartplate-1.0.0.jar --cli --mock
SmartPlate - AI meal planning agent. AI model: Mock AI (offline)
> profile set diet=vegetarian allergies=peanuts dislikes=mushrooms time=30
Profile saved:
Diet: vegetarian
Allergies: peanuts
Dislikes: mushrooms
Max cooking time: 30 min
Default servings: 2
> recommend 2 quick dinners using rice
Recommended (validated against your profile):
  r01 | Vegetable Fried Rice | 20 min | serves 2 | vegetarian, dairy-free, quick
  r02 | Chickpea Spinach Curry | 30 min | serves 2 | vegan, vegetarian, gluten-free, dairy-free
> plan 5 dinners, avoid red onion
Meal plan:
  Monday: Vegetable Fried Rice (r01, 2 servings, 20 min)
  Tuesday: Chickpea Spinach Curry (r02, 2 servings, 30 min)
  Wednesday: Spaghetti Aglio e Olio (r03, 2 servings, 15 min)
  Thursday: Tofu Veggie Stir-Fry (r11, 2 servings, 20 min)
  Friday: Spinach and Cheese Omelette (r13, 2 servings, 10 min)
> edit swap Tuesday for something with spinach
Meal plan:
  ...
  Tuesday: Quinoa Buddha Bowl (r17, 2 servings, 30 min)
  ...
(type 'undo' to revert the last change)
> undo
Undone.
> shopping
Shopping list:
[ ] frozen peas: 150 g
[ ] carrot: 2 pcs
...
```
Other commands: `search <words>`, `show <id>`, `scale <id> <servings>`, `substitute <id> <ingredient>`,
`nutrition <id>` / `nutrition plan`, `pantry add|update|remove ...`, `strategy fastest`, `trace`
(what the agent did), `help`, `quit`.

## Documents

- Stage 1 design report: [SmartPlate_Stage1_Report_FINAL.md](SmartPlate_Stage1_Report_FINAL.md) (UML in [docs/umlet-diagrams/](docs/umlet-diagrams/))
- **Stage 2 report:** [stage2-report.pdf](stage2-report.pdf) / [stage2-report.docx](stage2-report.docx) (source: [docs/STAGE2_REPORT.md](docs/STAGE2_REPORT.md))
- **Feature demonstration videos** (F01–F11 and the CLI): links in §7 *Demonstration index* of the Stage 2 report,
  and in the "Video" row of each feature in §2
- Stage 2 UML of the implemented system: [docs/umlet-diagrams/stage2/](docs/umlet-diagrams/stage2/)
- Feature demonstration script: [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)
- Code explanation: [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)
- Developer guide (structure, every function, how to change and debug): [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md)
- Where AI helped (disclosure for the instructor): [docs/AI_USAGE.md](docs/AI_USAGE.md)
