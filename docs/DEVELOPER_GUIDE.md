# SmartPlate — Developer Guide (for beginners, written for React developers)

This guide explains **everything in the code**: what each folder and file is for, how a click travels
through the program, every important function and algorithm (with `file:line` references), the five
design patterns, how to change things, and how to find and fix bugs.

> **Where line numbers come from.** All `file:line` references are for branch
> `claude/swing-ui-direction-b` at the commit that added this guide. If you edit a file, lines move a
> little; search for the function name instead (in IntelliJ: `Ctrl+F12` / `Cmd+F12` lists every
> method in a file).
>
> **Short paths.** `agent/MealPlanningAgent.java:102` means
> `src/main/java/com/smartplate/agent/MealPlanningAgent.java`, line 95. Test files are under
> `src/test/java/com/smartplate/`.

---

## Contents

1. [The app in one page](#1-the-app-in-one-page)
2. [Java for React developers (crash course)](#2-java-for-react-developers-crash-course)
3. [Swing for React developers (the GUI library)](#3-swing-for-react-developers-the-gui-library)
4. [Tools: build, run, test, debug](#4-tools-build-run-test-debug)
5. [Repository map: every file in one line](#5-repository-map-every-file-in-one-line)
6. [Software architecture: layers and rules](#6-software-architecture-layers-and-rules)
7. [The five design patterns, in the code](#7-the-five-design-patterns-in-the-code)
8. [Follow a click: end-to-end walkthroughs](#8-follow-a-click-end-to-end-walkthroughs)
9. [File-by-file reference: functions and algorithms](#9-file-by-file-reference-functions-and-algorithms)
10. [The GUI, panel by panel](#10-the-gui-panel-by-panel)
11. [The CLI](#11-the-cli)
12. [Tests](#12-tests)
13. [Cookbook: how to make common changes](#13-cookbook-how-to-make-common-changes)
14. [Debugging guide and known issues](#14-debugging-guide-and-known-issues)
15. [Glossary](#15-glossary)

---

## 1. The app in one page

**SmartPlate** plans a week of dinners. You type a request like *"Plan dinners for this week, avoid
mushrooms, under 30 minutes"*. An AI picks recipes from a fixed list of 30, and **Java code checks
every choice** against your allergies, diet, dislikes and time limit before you see it. You can then
swap a day (and undo), scale recipes, substitute ingredients, see nutrition and get a shopping list.

**The 10 features (from the Stage 1 report):**

| ID | Feature | AI? | Main code |
|---|---|---|---|
| F01 | Preferences (diet, allergies, dislikes, time, servings) | no | `domain/UserProfile.java` |
| F02 | Pantry (add / update / remove) | no | `domain/Pantry.java` |
| F03 | Recipe search | no | `service/RecipeRepository.java` |
| F04 | AI recommendations | yes | `agent/MealPlanningAgent.recommend` |
| F05 | AI weekly plan (5–7 dinners) | yes | `agent/MealPlanningAgent.createPlan` |
| F06 | Edit a day + undo | yes | `agent/MealPlanningAgent.editPlan`, `command/*` |
| F07 | Scale servings | no | `service/ServingScaler.java` |
| F08 | Substitute an ingredient | no | `service/SubstitutionService.java` |
| F09 | Nutrition (recipe or plan) | no | `service/NutritionService.java` |
| F10 | Shopping list (plan minus pantry) | no | `service/ShoppingListService.java` |

**The big idea:** *"the AI proposes, Java decides."* The AI only ever chooses from a shortlist of
real recipes. `ConstraintValidator` checks its answer. If it's wrong, the agent asks the AI **once
more**, telling it what was wrong. If it's still wrong, the user gets an error, **never a bad plan**.

```
 You ──► GUI or CLI ──► SmartRecipeFacade ──► MealPlanningAgent ──► AI (real Claude or offline mock)
                               │                     │
                               │                     └──► ConstraintValidator (checks the AI)
                               └──► services: nutrition, scaling, substitution, shopping list
```

There is **no database and no server**. Everything lives in memory while the program runs. The
30 recipes, the starter pantry and the substitution list are written in Java in
`service/SampleData.java`.

---

## 2. Java for React developers (crash course)

You already know most of the ideas; they just have different names.

### 2.1 Project and build

| React / Node | This Java project | Notes |
|---|---|---|
| `package.json` | `pom.xml` | Dependencies and build settings |
| `npm install` | done automatically by `mvn ...` | Downloads into `~/.m2` (like a global `node_modules`) |
| `npm run build` | `mvn package` | Output in `target/` (like `dist/`) |
| `npm start` | `java -jar target/smartplate-1.0.0.jar` | or `mvn compile exec:java` |
| `npm test` (Jest) | `mvn test` (JUnit 5) | |
| `src/components/Button.jsx` | `src/main/java/com/smartplate/presentation/gui/Ui.java` | **Folder = package**, file name = class name |
| `import { X } from './x'` | `import com.smartplate.domain.Recipe;` | Imports use the package path, not a file path |
| TypeScript types | Java types (always required) | `String name`, `int count`, `List<Recipe> recipes` |

### 2.2 Language features side by side

| JavaScript / TypeScript | Java | Example in this repo |
|---|---|---|
| `const x = 5` | `final int x = 5;` | `final` = cannot be reassigned |
| `let name = "a"` | `String name = "a";` | Semicolons are required |
| `string[]`, `Array<Recipe>` | `List<Recipe>` | `facade.getAllRecipes()` |
| `Record<string, number>` / `Map` | `Map<String, Integer>` | `AssistantPanel.snapshot()` |
| `Set` | `Set<String>` | `UserProfile.getAllergies()` |
| `undefined` / `null` | only `null` | `Recipe.getNutritionPerServing()` may return `null` |
| `x => x * 2` | `x -> x * 2` | lambdas everywhere in the GUI |
| `arr.map(f).filter(g)` | `list.stream().map(f).filter(g).toList()` | `AIAdapter.extractText` (`ai/AIAdapter.java:78`) |
| `` `Hello ${name}` `` | `"Hello " + name` or `String.format("Hello %s", name)` | |
| `throw new Error("msg")` | `throw new SmartPlateException("msg");` | |
| `try { } catch (e) { }` | `try { } catch (SmartPlateException e) { }` | You catch by **type** |
| `class A extends B` | `class A extends B` | same |
| TS `interface Shape { area(): number }` | `interface PlanComponent { NutritionInfo getNutrition(); }` | `class MealPlan implements PlanComponent` |
| `export` / not exported | `public` / `private` | No modifier = "package-private" (visible in the same folder only) |
| module-level function | `static` method | `Units.format(...)`, `DietRules.matches(...)` |
| `this.x` in a class | `this.x` or just `x` | fields are declared at the top of the class |
| `===` for strings | `a.equals(b)` | **Never compare Strings with `==` in Java** |
| `switch` | `switch (x) { case "a": ...; break; }` | `MealPlannerCLI.handle` |
| decorators | annotations: `@Override`, `@Test` | `@Override` = "this replaces a parent method" |

### 2.3 Three Java things that surprise React developers

1. **Everything is a class.** There are no free functions. Utilities are classes with `static`
   methods (`Units`, `DietRules`, `Ui`, `Icons`).
2. **Objects are passed by reference** (like JS objects). `Recipe` is *immutable*: its methods return
   a new copy instead of changing it (like `{...obj, x: 1}` in React state).
3. **Exceptions are how errors flow.** Every user-facing error is a `SmartPlateException` with a
   readable message. The GUI and CLI catch it and show `e.getMessage()`.

### 2.4 Reading a Java method signature

```java
public List<Recipe> recommend(String request, UserProfile profile, Pantry pantry) { ... }
//  ^      ^            ^        ^parameters (type then name) ...
//  |      |            method name
//  |      return type (a list of recipes)
//  visibility (callable from anywhere)
```

---

## 3. Swing for React developers (the GUI library)

The desktop window uses **Swing** (built into Java), styled with **FlatLaf** (a theme library, like a
CSS framework).

| React | Swing | Where to see it |
|---|---|---|
| `<div>` | `JPanel` | everywhere |
| `<button onClick={f}>` | `JButton b; b.addActionListener(e -> f());` | `AssistantPanel.buildRequestBar` (:137) |
| `<input>` | `JTextField` | request field |
| `<textarea>` | `JTextArea` | agent trace |
| `<select>` | `JComboBox<String>` | unit, category, strategy |
| `<input type="checkbox">` | `JCheckBox` | diets, shopping list |
| `<input type="number">` | `JSpinner` | servings, cooking time |
| `<table>` + data array | `JTable` + `DefaultTableModel` | plan, results, pantry tables |
| cell render function | `TableCellRenderer` | `AssistantPanel.PlanRenderer` (:761) |
| `display:flex; flex-direction:row` | `FlowLayout` (`Ui.row`) | button rows |
| `display:flex; flex-direction:column` | `BoxLayout.Y_AXIS` (`Ui.stack`) | stacked labels |
| CSS grid areas (header / main / sidebar) | `BorderLayout` (NORTH / CENTER / EAST / SOUTH / WEST) | every panel |
| `grid-template-columns: repeat(2, 1fr)` | `GridLayout(rows, cols, gapX, gapY)` | stat boxes |
| `{cond ? <A/> : <B/>}` | `CardLayout` (`cards.show(panel, "name")`) | empty state vs table |
| Tabs component | `JTabbedPane` | `MealPlannerGUI.buildContent` (:72) |
| Theme provider / global CSS | `Theme.install()` → `UIManager.put(...)` | `gui/Theme.java:54` |
| inline style | `putClientProperty(FlatClientProperties.STYLE, "...")` | `Ui.primary` (:129) |
| `useState` + automatic re-render | **fields + you call a refresh method yourself** | `refreshPlan()`, `render()` |
| JS main thread | the **Event Dispatch Thread (EDT)** | all UI changes must happen here |
| `await fetch(...)` | `SwingWorker` (wrapped by `TaskRunner`) | `gui/TaskRunner.java:17` |

### The most important difference: no automatic re-render

In React, changing state re-renders the UI. **In Swing nothing updates by itself.** After you change
data, you must update the widgets yourself, for example `planModel.addRow(...)`,
`label.setText(...)` or `button.setEnabled(...)`. That's why each panel has a method like
`refreshPlan()` (`AssistantPanel.java:467`), `render()` (`RecipesPanel.java:341`), `refresh()`
(`PantryPanel.java:187`) or `load()` (`PreferencesPanel.java:178`) that rebuilds what is shown from
the facade.

---

## 4. Tools: build, run, test, debug

**Install:** JDK 21 (Temurin from adoptium.net), Maven 3.9+, IntelliJ IDEA Community (recommended).

```bash
mvn compile                          # compile only (like tsc)
mvn test                             # run all 134 tests
mvn package                          # build target/smartplate-1.0.0.jar (includes all libraries)
java -jar target/smartplate-1.0.0.jar            # open the desktop app
java -jar target/smartplate-1.0.0.jar --cli      # command-line version
java -jar target/smartplate-1.0.0.jar --mock     # force the offline AI
java -jar target/smartplate-1.0.0.jar --empty    # start with an empty pantry
mvn test -Dtest=MealPlanningAgentTest            # run one test class
mvn test -Dtest=GuiPanelsTest#createEditAndUndoMarkTheChangedDay   # run one test
```

**Which AI:** the setting `SMARTPLATE_AI_PROVIDER` picks the model at start-up (read by `ai/AIModelFactory.java`):
`auto` (default: Claude if `ANTHROPIC_API_KEY` is set, else Gemini if `GEMINI_API_KEY` is set, else the
offline `MockAIModel`), `mock`, `anthropic`, `gemini` (optional `GEMINI_MODEL`, default
`gemini-flash-latest`), or `ollama` (a local model; optional `OLLAMA_MODEL`, default `llama3.2`, and
`OLLAMA_URL`, default `http://localhost:11434`). `SMARTPLATE_AI_MODEL` changes the Claude model id.
In the window you can also switch model with the **AI model** box at the top right.

**API keys go in a `.env` file**, exactly like `dotenv` in Node: copy `.env.example` to `.env` in the
project folder and fill it in. `.env` is git-ignored, so it is never pushed. Real environment
variables override it:
```bash
cp .env.example .env        # then edit .env: GEMINI_API_KEY=..., OLLAMA_MODEL=gemma4:e4b, ...
SMARTPLATE_AI_PROVIDER=ollama OLLAMA_MODEL=gemma4:e4b java -jar target/smartplate-1.0.0.jar   # one-off override
```
IntelliJ runs the app from the project folder, so it finds `.env` too.

**IntelliJ:**
- File → Open → choose `pom.xml` → *Open as Project*. Set File → Project Structure → SDK = 21.
- Run the app: open `app/SmartPlateApp.java` and click the green ▶ next to `main`.
  Add `--cli` in *Run → Edit Configurations → Program arguments* for the CLI.
- **Debug:** click in the gutter next to a line number to add a red breakpoint, then use the 🐞
  button instead of ▶. The program pauses there and you can inspect every variable (like Chrome
  DevTools breakpoints). Good breakpoints: `SmartRecipeFacade.createMealPlan` (:143),
  `MealPlanningAgent.createPlan` (:164), `ConstraintValidator.validateRecipe` (:23).
- Run a test: open the test file and click ▶ next to the class or method.

**VS Code** (the closest thing to `package.json` scripts): `.vscode/tasks.json` defines
*SmartPlate: run app*, *run CLI*, *run with mock AI*, *test*, *build jar* and *clean*. Run them from
*Terminal → Run Task…*, with Ctrl/Cmd+Shift+B (build jar), or as buttons in the **Task Runner** view
at the bottom of the Explorer (install the recommended extensions VS Code suggests when you open the
folder). For breakpoints use the **Run and Debug** panel: `.vscode/launch.json` has *SmartPlate: app*,
*CLI* and *app with mock AI* (F5).

---

## 5. Repository map: every file in one line

```
pom.xml                         build file: Java 17, JUnit 5, Anthropic SDK, FlatLaf, fat-jar packaging
README.md                       project title, setup steps and links to the documents
.vscode/                        shared VS Code tasks (run, test, build), debug configs, recommended extensions
SmartPlate_Stage1_Report_FINAL.md  the Stage 1 design report
docs/
  STAGE2_REPORT.md              Stage 2 report: AI tools, collaboration log, traceability, design changes
  IMPLEMENTATION.md             shorter code explanation for the instructor
  AI_USAGE.md                   where AI helped (disclosure for the professor)
  DEVELOPER_GUIDE.md            this file
  screenshots/                  GUI screenshots
  umlet-diagrams/               final Stage 1 class, use-case and sequence diagrams (UMLet .uxf + PNG)
  plantuml-diagrams/            earlier versions of the same diagrams (PlantUML + PNG)
src/main/resources/fonts/       IBM Plex fonts used by the GUI (+ OFL.txt licence)

src/main/java/com/smartplate/
  app/SmartPlateApp.java              main(): chooses real/mock AI, seeds the pantry, starts GUI or CLI
  facade/SmartRecipeFacade.java       FACADE: one method per feature; owns profile, pantry, current plan
  agent/
    MealPlanningAgent.java            the AI agent: shortlist → ask AI → validate → revise once
    ConstraintValidator.java          checks a recipe/plan against the profile (the safety net)
    ValidationResult.java             list of violation messages
    RequestConstraints.java           reads "5 dinners", "under 30 minutes", "avoid X", "vegan" from text
  ai/
    AIModel.java                      ADAPTER target interface: generate(prompt) -> text
    AIAdapter.java                    ADAPTER: turns a prompt into an Anthropic API request and back
    AIClient.java                     ADAPTEE: the real Anthropic SDK client (API key from env var)
    OllamaAdapter.java                ADAPTER #2: a local model through Ollama's /api/chat (JDK HttpClient)
    GeminiAdapter.java                ADAPTER #3: Google Gemini's generateContent REST API (JDK HttpClient)
    AIModelFactory.java               picks mock / anthropic / gemini / ollama (SMARTPLATE_AI_PROVIDER, GUI switcher)
    EnvFile.java                      reads API keys and settings from the git-ignored .env file
    MockAIModel.java                  offline fake AI (heuristic / scripted / failing)
    PromptFormat.java                 the "KEY: value" text protocol between agent and AI
    AIServiceException.java           "the AI service failed" error
  strategy/
    RecipeSelectionStrategy.java      STRATEGY interface + shared fitsProfile() filter
    PantryFirstStrategy.java          prefer recipes using pantry items
    FastestMealStrategy.java          prefer quickest recipes
  command/
    Command.java                      COMMAND interface: execute() / undo()
    ReplaceMealCommand.java           replaces one day's meal, remembers the old one
    CommandHistory.java               undo stack
  plan/
    PlanComponent.java                COMPOSITE interface: getNutrition / getIngredients / getCookingTime
    MealPlan.java                     COMPOSITE: the 5–7 day plan
    PlannedMeal.java                  LEAF: one day = recipe × servings
    Days.java                         day-name helpers ("tue" -> "Tuesday")
  domain/                             plain data + rules, no UI, no AI
    Recipe.java, RecipeIngredient.java, Ingredient.java, NutritionInfo.java
    UserProfile.java (+ validate), Pantry.java, PantryItem.java, PantryAction.java
    ShoppingList.java, ShoppingItem.java, Substitution.java
    Units.java (g/kg/ml/l/pcs maths), DietRules.java (allergy/diet matching)
    SmartPlateException.java, InvalidProfileException.java
  service/
    RecipeRepository.java             the 30 recipes; search / findById / findAll
    SampleData.java                   the recipe, pantry and substitution DATA
    NutritionService.java, ServingScaler.java, SubstitutionService.java, ShoppingListService.java
  presentation/
    MealPlannerCLI.java               text interface
    MealPlannerGUI.java               desktop window (tabs, strategy and AI model switchers)
    gui/
      AssistantPanel.java             tab 1: AI requests, plan table, result, trace, shopping, nutrition
      RecipesPanel.java               tab 2: search, scale, substitute, recipe nutrition
      PantryPanel.java                tab 3: pantry table + form
      PreferencesPanel.java           tab 4: profile form + validation messages
      Theme.java                      colours, fonts, FlatLaf settings
      Ui.java                         factory for cards, buttons, labels, tables, pills
      AlertBox.java                   green/red/blue message box
      RoundedPanel.java               panel with rounded corners
      Icons.java                      small icons drawn in code
      TaskRunner.java                 runs AI calls in the background (or directly in tests)

src/test/java/com/smartplate/         21 test classes, 134 tests (see section 12)
```

---

## 6. Software architecture: layers and rules

### 6.1 Layers (top calls bottom, never the other way)

```
┌────────────────────────────────────────────────────────────────────┐
│ PRESENTATION   presentation/ (MealPlannerGUI + gui/*, MealPlannerCLI) │  ← what the user sees
├────────────────────────────────────────────────────────────────────┤
│ APPLICATION    facade/SmartRecipeFacade                              │  ← single entry point
├────────────────────────────────────────────────────────────────────┤
│ LOGIC          agent/, strategy/, command/, service/, ai/            │  ← how things are done
├────────────────────────────────────────────────────────────────────┤
│ DOMAIN         domain/, plan/                                        │  ← data + business rules
├────────────────────────────────────────────────────────────────────┤
│ DATA           service/RecipeRepository + SampleData                 │  ← where recipes come from
└────────────────────────────────────────────────────────────────────┘
```

### 6.2 The rules that keep the design clean (please keep them!)

1. **The GUI and CLI only call `SmartRecipeFacade`.** They must not import anything from `agent`,
   `ai`, `service` or `command`. (They may read domain objects the facade returns, like `Recipe`.)
   Check it any time with: `grep -rn "import com.smartplate.\(agent\|ai\|service\|command\)" src/main/java/com/smartplate/presentation`. It should print nothing.
2. **Domain classes know nothing about UI or AI.** `Recipe`, `Pantry` etc. never import Swing.
3. **Every expected error is a `SmartPlateException`** with a message a user can read.
   No `System.exit`, no printing inside logic classes.
4. **Recipes are immutable.** Scaling or substituting returns a new `Recipe`
   (`Recipe.withServingsAndIngredients`, `Recipe.java:99`).
5. **The AI never has the last word.** Any AI answer goes through `ConstraintValidator`.

### 6.3 Object wiring (who creates whom)

`SmartRecipeFacade.create(aiModel)` (`facade/SmartRecipeFacade.java:66`) builds the whole system once:

```java
RecipeRepository repository = RecipeRepository.withSampleRecipes();      // 30 recipes
MealPlanningAgent agent = new MealPlanningAgent(aiModel, repository,
        new ConstraintValidator(), new PantryFirstStrategy());          // default strategy
return new SmartRecipeFacade(repository, agent, new NutritionService(), new ServingScaler(),
        SubstitutionService.withDefaultSubstitutions(), new ShoppingListService(), new CommandHistory());
```

It's like creating your stores/services once in `App.jsx` and passing them down. The GUI panels
all receive **the same facade object**, so they share one profile, one pantry and one plan.

---

## 7. The five design patterns, in the code

| Pattern | Problem it solves | Classes | React analogy |
|---|---|---|---|
| **Facade** | UI shouldn't know 10 internal classes | `SmartRecipeFacade` | an `api.js` module the components call |
| **Adapter** | swap AI providers / run offline | `AIModel` ← `AIAdapter` → `AIClient`; `GeminiAdapter`; `OllamaAdapter`; `MockAIModel` | a wrapper around `fetch` / an SDK; a mock for tests |
| **Strategy** | several ways to pick candidate recipes | `RecipeSelectionStrategy`, `PantryFirstStrategy`, `FastestMealStrategy` | passing a `sortFn` prop |
| **Command** | undo plan edits | `Command`, `ReplaceMealCommand`, `CommandHistory` | Redux actions + an undo stack |
| **Composite** | one meal and a whole plan treated the same | `PlanComponent`, `MealPlan`, `PlannedMeal` | a component tree where parent = sum of children |

### 7.1 Facade — `facade/SmartRecipeFacade.java`
One public method per feature: `setupProfile` (:93), `managePantry` (:106), `searchRecipes` (:128),
`recommendRecipes` (:136), `createMealPlan` (:143), `editMealPlan` (:152), `undoLastChange` (:160),
`adjustServings` (:169), `substituteIngredient` (:178), `calculateRecipeNutrition` (:191),
`calculatePlanNutrition` (:196), `generateShoppingList` (:202). Helpers for the UI: `getProfile`,
`getPantry`, `getCurrentPlan`, `canUndo`, `getRecipe`, `getAllRecipes`, `getLastSubstitution`,
`setStrategy` (:243), `getStrategyName`, `getAIModelName`, `getLastAgentTrace` (:293).
State it owns: `profile`, `pantry`, `currentPlan`, `lastSubstitution`.

### 7.2 Adapter — `ai/`
- `AIModel` (`ai/AIModel.java:9`): `String generate(String prompt)` + `getName()`.
- `AIAdapter.generate` (`ai/AIAdapter.java:42`) → `buildRequest` (:63) makes the SDK request
  (model, `SYSTEM_PROMPT` at :26, low effort, refusal fallback), `client.sendRequest`,
  then `extractText` (:78) gets the text back, or throws `AIServiceException`.
- `AIClient` (`ai/AIClient.java:22`): reads `ANTHROPIC_API_KEY` / `SMARTPLATE_AI_MODEL`
  (`fromEnvironment` :38) and calls the Anthropic SDK (`sendRequest` :53).
- `OllamaAdapter` (`ai/OllamaAdapter.java:31`): the same job for a local model. `generate` (:73) POSTs
  `buildRequestBody` (:98: model, `stream: false`, the same `SYSTEM_PROMPT`, the prompt, temperature 0.2)
  to `<OLLAMA_URL>/api/chat`; `extractText` (:110) reads `message.content`, drops `<think>…</think>`
  (`stripThinking` :135) and maps 404 → "ollama pull <model>", other HTTP errors, connection refused
  and timeouts (180 s) to `AIServiceException`.
- `GeminiAdapter` (`ai/GeminiAdapter.java:30`): the same job for Google Gemini. `generate` (:70) POSTs
  `buildRequestBody` (:97: `systemInstruction` = `SYSTEM_PROMPT`, `contents` = the prompt) to
  `/v1beta/models/<model>:generateContent`, with the key in the `x-goog-api-key` header (never in the URL).
  `extractText` (:107) joins the answer parts (skipping "thought" parts) and maps a bad key, unknown
  model (404), rate limit (429), blocked prompt and empty answer to `AIServiceException`.
- `AIModelFactory` (`ai/AIModelFactory.java`): `create(env)` (:55) handles `auto` (Claude key → Claude,
  else Gemini key → Gemini, else Mock; never Ollama) and otherwise calls `forProvider(name, env)` (:69),
  a `switch` over mock / anthropic / gemini / ollama. `missingSetting` (:94) says which key is missing;
  `providerOf` (:106) maps a model back to its name. It takes the environment as a `Map`, so tests pass a fake one.
- `EnvFile` (`ai/EnvFile.java`): `load()` = the real environment + the `KEY=value` lines of `./.env`
  (environment wins). Like `dotenv` in Node.
- **Switching at run time:** the GUI's AI model box calls `facade.setAIProvider(name)` (`SmartRecipeFacade.java:279`),
  which re-reads the settings (so a key added to `.env` works without a restart), refuses with a
  clear message if the key is missing, and otherwise calls `MealPlanningAgent.setAIModel` (:87).
- `MockAIModel` (`ai/MockAIModel.java:40`): same interface, no network.

### 7.3 Strategy — `strategy/`
- Interface `selectRecipes(recipes, profile, pantry)` (`RecipeSelectionStrategy.java:18`) plus the
  shared filter `fitsProfile` (:30).
- `PantryFirstStrategy.selectRecipes` (:19): filter, then sort by "number of pantry ingredients"
  (descending), then cooking time, then id.
- `FastestMealStrategy.selectRecipes` (:15): filter, then sort by cooking time, then id.
- The agent is the "context": `MealPlanningAgent.setStrategy` (:74). The GUI combo box calls
  `facade.setStrategy("fastest")` (`MealPlannerGUI.statusArea`, :122).

### 7.4 Command — `command/`
- `ReplaceMealCommand` (:10) stores `plan`, `day`, `oldMeal`, `newMeal`; `execute()` (:25) puts the
  new meal in, `undo()` (:30) puts the old one back.
- `CommandHistory` (:12): `execute` (:16) runs + pushes; `undo` (:22) pops + undoes;
  `canUndo` (:31); `clear` (:36) is called when a new plan is created.
- The agent **creates** the command (`editPlan`), the facade **runs** it (`editMealPlan`, :138).

### 7.5 Composite — `plan/`
- `PlanComponent` (:14): `getNutrition()`, `getIngredients()`, `getCookingTime()`.
- `PlannedMeal` (leaf): nutrition = per-serving × servings (`getNutrition` :50); ingredients scaled by
  `servings / recipe.servings` (`getIngredients` :60).
- `MealPlan` (composite): sums its children (`getNutrition` :75, `getIngredients` :84).
- `NutritionService.calculatePlan(PlanComponent)` and `ShoppingListService.generate(PlanComponent, …)`
  work on either, without checking which.

---

## 8. Follow a click: end-to-end walkthroughs

### 8.1 GUI: the user clicks **Create weekly plan**

| # | What happens | Where |
|---|---|---|
| 1 | Button was wired to `createPlan()` | `gui/AssistantPanel.java:137` `buildRequestBar` |
| 2 | Read the text; blank → red error, stop | `AssistantPanel.java:318` `createPlan` → `:391` `requestText` |
| 3 | Disable buttons, show "Working…", run in background | `:370` `runAI` → `gui/TaskRunner.java:17` |
| 4 | **Facade** forwards to the agent | `facade/SmartRecipeFacade.java:143` `createMealPlan` |
| 5 | Read constraints from the text ("5 dinners", "avoid mushrooms"…) | `agent/RequestConstraints.java:64` `parse` |
| 6 | Number of days must be 5–7 or error | `RequestConstraints.java:173` `planDays` |
| 7 | Get all 30 recipes, strategy filters + sorts, keep 15 | `agent/MealPlanningAgent.java:298` `shortlist` |
| 8 | Build the prompt (profile, candidates, response format) | `MealPlanningAgent.java:337` `newPrompt` |
| 9 | Ask the AI | `MealPlanningAgent.java:312` `callAI` → `MockAIModel.generate` (:76) or `AIAdapter.generate` (:42) |
| 10 | Parse "Monday: r03" lines | `MealPlanningAgent.java:370` `parsePlan` |
| 11 | Unknown id → violation; known → `PlannedMeal` added | `:309` `lookup`, `plan/MealPlan.java:26` `addMeal` |
| 12 | **Validate** the plan | `agent/ConstraintValidator.java:48` `validatePlan` → `:23` `validateRecipe` |
| 13 | Problems? Ask once more with `PREVIOUS_VIOLATIONS` + `EXCLUDE`. Still bad → exception | loop in `MealPlanningAgent.createPlan` (:164) |
| 14 | Facade stores the plan, clears undo history | `SmartRecipeFacade.java:143` |
| 15 | Back on the UI thread: fill the table, show success + trace | `AssistantPanel.java:467` `refreshPlan`, `:404` `showTrace` |

### 8.2 GUI: **Edit plan** and **Undo**

1. `AssistantPanel.editPlan` (:331) takes a `snapshot()` (:506) of day → recipe first.
2. `facade.editMealPlan` (:152) → `agent.editPlan` (`MealPlanningAgent.java:229`):
   finds the day in the text (`findDay` :394), asks the AI for `DAY:` + `RECIPE:`, checks the recipe
   exists, is different, and passes `validateRecipe`. Then it returns a `ReplaceMealCommand`.
3. The facade runs it: `CommandHistory.execute` (:16) → `ReplaceMealCommand.execute` (:25) →
   `MealPlan.replaceMeal` (:43).
4. The panel compares snapshots (`firstDifference` :517) to know which day changed. It sets
   `changedDay` (tinted row + "CHANGED" tag, `PlanRenderer` :761) and shows the before → now card
   (`comparison` :715).
5. **Undo:** `AssistantPanel.undo` (:350) → `facade.undoLastChange` (:160) →
   `CommandHistory.undo` (:22) → `ReplaceMealCommand.undo` (:30).

### 8.3 GUI: **Shopping list**

`AssistantPanel.loadShoppingList` (:529) → `facade.generateShoppingList` (:202) →
`ShoppingListService.generate` (`service/ShoppingListService.java:20`):
1. `plan.getIngredients()`: every ingredient of every day, already scaled to that day's servings.
2. Invalid quantities are skipped with a note; the rest go into a `ShoppingList`.
3. `ShoppingList.consolidate()` (`domain/ShoppingList.java:30`) merges duplicates.
4. For each item, subtract the pantry amount (same unit family only). Keep what's still missing.
5. `consolidate()` again for nice units (≥1000 g → kg).

The panel also computes "Covered by your pantry" (plan ingredients you have that aren't on the list).

### 8.4 CLI: typing `plan weekday dinners`

`app/SmartPlateApp.java:35` `main` → `MealPlannerCLI.runLoop` (:76) reads a line →
`handle` (:112) → `case "plan"` (:144) → `facade.createMealPlan(...)` → `printPlan` (:324).
The same facade method as the GUI, so the behaviour is identical.

---

## 9. File-by-file reference: functions and algorithms

### 9.1 `app/SmartPlateApp.java`
- `main(args)` (:35): picks the AI (`chooseModel`, :59: `MockAIModel` if `--mock` is given, otherwise
  `AIModelFactory.fromEnvironment()`). A mistyped `SMARTPLATE_AI_PROVIDER` throws
  `IllegalArgumentException`; `main` prints its message and exits. Then it creates the facade, adds
  `SampleData.pantry()` unless `--empty`, and starts the CLI (`--cli` or no screen) or the GUI.

### 9.2 `facade/SmartRecipeFacade.java`
Every method validates its input, calls one subsystem, and returns or throws. Notes:
- `setupProfile` (:93): `profile.validate()`; problems → `InvalidProfileException` (old profile kept).
- `managePantry` (:106): `switch` on `ADD` / `UPDATE` / `REMOVE` → `Pantry`.
- `createMealPlan` (:143): stores `currentPlan` and **clears the undo history**.
- `editMealPlan` (:152): needs a plan; runs the agent's command through `CommandHistory`.
- `substituteIngredient` (:178): checks the recipe contains the ingredient, then
  `findSubstitute` + `applySubstitution`; remembers `lastSubstitution` for the UI message.
- `setStrategy(name)` (:243): `"pantry..."` → `PantryFirstStrategy`, `"fast..."` → `FastestMealStrategy`.

### 9.3 `agent/MealPlanningAgent.java` (the heart of the app)
Constants: `MAX_CANDIDATES = 15` (:52), `DEFAULT_RECOMMENDATIONS = 3` (:53),
`MAX_ATTEMPTS = 2` (:54) means first try + one revision. `PLAN_LINE` regex (:56) matches "Monday: r03".

**Algorithm used by `recommend` (:102) and `createPlan` (:164):**
```
constraints = RequestConstraints.parse(request)      // extra rules from the text
effective   = constraints.applyTo(profile)           // profile + those rules (copy)
candidates  = shortlist(effective, pantry)           // strategy, max 15; none → error
exclude = {}, violations = []
repeat up to 2 times:
    prompt = newPrompt(task, request, effective, candidates, exclude, violations)
    answer = AI.generate(prompt)
    parse ids; unknown id → violation + exclude
    validate each recipe (or the whole plan); bad recipe → violation + exclude
    if no violations → return result
return valid part (recommend) or throw SmartPlateException (plan)
```
- `editPlan` (:229) does **not** revise. It follows sequence diagram SD05: one answer, then
  validate, then a command or an error.
- `shortlist` (:298), `callAI` (:312, logs the answer), `lookup` (:328), `newPrompt` (:337),
  `candidateLine` (:361, "id | name | min | tags | ingredients"), `parsePlan` (:370),
  `parseIdList` (:379), `cleanId` (:390, removes `*`, backticks etc.), `findDay` (:394).
- `log` (:406) appends to the **trace** the GUI shows (`getLastTrace` :95).

**A prompt looks like this** (keys from `ai/PromptFormat.java`):
```
TASK: PLAN
REQUEST: weekday dinners, avoid mushrooms, under 20 minutes
DIET: vegetarian
AVOID: peanuts, mushrooms
MAX_TIME: 20
EXCLUDE: none
CANDIDATES:
- r12 | Greek Salad with Chickpeas | 10 min | tags: vegetarian, ... | ingredients: cucumber, ...
DAYS: Monday, Tuesday, Wednesday, Thursday, Friday
RESPONSE_FORMAT: one line per day, '<Day>: <id>' ...
```
On a revision it also contains `PREVIOUS_VIOLATIONS:` lines and a filled `EXCLUDE:`.

### 9.4 `agent/ConstraintValidator.java`
- `validateRecipe` (:23): for each ingredient → `DietRules.conflicts` (allergy, dislike, diet);
  invalid quantity; cooking time > limit; no ingredients. Each problem is a readable sentence.
- `validatePlan` (:48): empty plan; days not in 5–7; duplicate or invalid day; servings < 1; then
  `validateRecipe` for every meal, prefixed with the day ("Tuesday: Chicken Stir-Fry (r04) contains…").

### 9.5 `agent/RequestConstraints.java` — reading rules out of a sentence
Regular expressions (:28–31), all applied to the lower-cased request:

| Rule | Regex idea | Example → result |
|---|---|---|
| `DAYS` | number + day/dinner/night | "plan 6 dinners" → 6; "weekday" → 5 |
| `COUNT` | number + recipes/ideas/meals… | "give me 4 vegetarian recipes" → 4 |
| `TIME` | under/within/max… + number + min | "under 30 minutes" → 30 |
| `SERVINGS` | for + number + people | "for 4 people" → 4 |
| `AVOID` | avoid/no/without/skip + **one word** | "avoid mushrooms" → mushrooms |
| diets | any word from `UserProfile.SUPPORTED_DIETS` | "something vegan" → vegan |

`NOT_INGREDIENTS` (:43) stops "no more than 20 min" from adding "more" as a dislike.
`planDays` (:173) throws if not 5–7. `applyTo` (:206) returns a **copy** of the profile with the extra
diets, dislikes and a stricter time limit, so the saved profile never changes.

### 9.6 `domain/DietRules.java` — matching ingredients to allergies and diets
- `conflicts(ingredient, profile)` (:58) returns reasons, e.g. `contains peanut butter (allergy: peanuts)`.
- `matches(ingredient, term)` (:82):
  1. `singular(term)` (:139): "mushrooms" → "mushroom", "tomatoes" → "tomato", "berries" → "berry".
  2. Special case: "gluten" never matches a name containing "gluten-free".
  3. If the term is a **category group** (`GROUP_CATEGORY` :36: nut, dairy, egg, meat, seafood) and the
     ingredient's category matches → true.
  4. If the term is a **name group** (`GROUP_NAMES` :25, e.g. gluten → pasta, flour, bread…, shellfish
     → shrimp, crab…), match any member as a whole word.
  5. Otherwise: category equals term, or the name contains the term **as a whole word**
     (`containsWord` :154, so "nut" doesn't match "nutmeg" and "egg" doesn't match "eggplant";
     a plural "s"/"es" after the word is allowed).
- `violatesDiet(ingredient, diet)` (:116): vegetarian = no meat/seafood category; pescatarian = no meat;
  vegan = no meat/seafood/dairy/egg/honey; gluten-free = `matches(…, "gluten")`; dairy-free = no dairy.

### 9.7 `domain/UserProfile.java`
- Constructor (:36) lower-cases and trims entries but **keeps duplicates**, so `validate` can report them.
- `validate()` (:54): servings ≥ 1, time ≥ 1, each entry must match `[a-z][a-z \-']*`
  (`checkEntries` :110), no duplicates, diets must be in `SUPPORTED_DIETS` (:24).
- `withRequestConstraints` (:100): copy with extra diets/dislikes and `min(time, requestTime)`.

### 9.8 `domain/Pantry.java`
- `addItem` (:20): validate (quantity > 0, supported unit). If the ingredient already exists with a
  compatible unit, **add the amounts** in the existing unit (500 ml + 0.5 l = 1000 ml); incompatible
  unit → error "Use UPDATE instead".
- `updateItem` (:37) replaces; `removeItem` (:46); `findItem` (:55) is case-insensitive.

### 9.9 `domain/Units.java` — unit maths
- Families: MASS (g, kg), VOLUME (ml, l), COUNT (pcs) — `family` (:28).
- `toBase` (:48): kg → ×1000 g, l → ×1000 ml. `convert(q, from, to)` (:69) = `toBase(q, from) / toBase(1, to)`.
- `format` (:77): rounds to 2 decimals and drops ".0" ("200 g", "1.5 kg").

### 9.10 `domain/ShoppingList.java` — `consolidate()` (:30)
Key = `name|family`. Sum every quantity in the base unit (g, ml, pcs). Output one line per key; mass
or volume ≥ 1000 becomes kg / l. Different families (e.g. 2 pcs onion and 600 g onion) stay separate lines.

### 9.11 `domain/Recipe.java`
Immutable. `builder(id, name)` (:46) is used by `SampleData` (`.time().servings().tags().ingredient().nutrition().step().build()`).
`findIngredient` (:88), `withServingsAndIngredients` (:99), `withIngredients` (:105), `summary` (:110), `describe` (:116).

### 9.12 `plan/` — `MealPlan`, `PlannedMeal`, `Days`
- `MealPlan.addMeal` (:26): one meal per day, max 7, keeps Monday→Sunday order. `replaceMeal` (:43)
  only for an existing day.
- `PlannedMeal` constructor (:20) normalises the day ("tue" → "Tuesday") and rejects servings < 1.
- `Days.normalize` (:21): accepts a full name or a prefix of 3+ letters. `firstDays(n)` (:61): plans
  always start Monday.

### 9.13 `strategy/` — see 7.3
`RecipeSelectionStrategy.fitsProfile` (:30) = within the time limit and no ingredient conflicts.
`PantryFirstStrategy.pantryMatches` (:33) counts ingredients present in the pantry.

### 9.14 `service/`
- `RecipeRepository.search(query)` (:43): split the query into words; a recipe matches if **every
  word** appears in its searchable text = id + name + tags + ingredient names (`searchableText` :81).
  `findById` (:66) is case-insensitive.
- `ServingScaler.scale(recipe, servings)` (:13): factor = new / old servings; every quantity × factor;
  errors for servings < 1 or bad quantities.
- `SubstitutionService.findSubstitute` (:45): all predefined substitutions for that ingredient
  (`getSubstitutionsFor` :31), the first whose substitute has **no** `DietRules.conflicts` with the
  profile. `applySubstitution` (:63): quantity × factor, unit = substitution's unit or the original;
  `rewriteSteps` (:86) names the substitute in the steps (whole words, plurals kept, short names such as
  "chicken" for chicken breast when the full name is not in any step).
- `NutritionService.calculateRecipe` (:12) = per serving × servings; `calculatePlan` (:29) =
  `plan.getNutrition()` (Composite).
- `ShoppingListService.generate` (:20): see 8.3.
- `SampleData`: recipes (:19), starter pantry (:268), substitutions (:281, e.g. butter → olive oil ×0.8 ml at :283).

### 9.15 `ai/MockAIModel.java` — how the fake AI "thinks"
- Modes: default heuristic; `scripted(...)` (:66) returns fixed answers (tests); `failing()` (:71).
- `answer` (:99) reads `TASK:` and calls `answerRecommend` (:112), `answerPlan` (:121) or `answerEdit` (:134).
- `rank` (:163): drop candidates that are in `EXCLUDE`, slower than `MAX_TIME`, missing a `DIET` tag,
  or whose text contains an `AVOID` word. Score the rest by request words (ignoring `STOP_WORDS`
  :42): tag match +3, other text match +2. Stable sort keeps the strategy's order for ties.
- It records every prompt (`getPrompts` :92), which tests use to check revisions.

### 9.16 `ai/PromptFormat.java`
Constant keys (`TASK`, `REQUEST`, `CANDIDATES`, `RECIPES`, `DAY`, `RECIPE`…) and `field(text, key)`
(:39), which finds a `KEY: value` line (case-insensitive, ignores `*` and backticks).

---

## 10. The GUI, panel by panel

The window: `MealPlannerGUI.start()` (:55) → `Theme.install()` → `buildContent()` (:72) creates the
4 panels with the same facade and a `JTabbedPane`. The logo (`logo` :107) and the strategy selector +
AI model switcher (`statusArea` :122, `modelSwitcher` :168) live in the tab bar. While an AI request
runs, `AssistantPanel.onBusyChange` tells the window to disable both boxes. Switching tabs calls `refresh()` / `load()` /
`refreshPlan()`, because another tab may have changed the data.

### 10.1 `gui/AssistantPanel.java` (tab 1, 804 lines)
| Part | Where |
|---|---|
| Fields (widgets + state `changedDay`, `busy`, `sideView`, `busyListener`) | :64–:110 |
| Layout: request bar / plan card / empty state / right side | `buildRequestBar` :137, `buildPlanCard` :159, `buildEmptyState` :190, `buildSide` :228 |
| Actions (F04, F05, F06, undo) | `recommend` :300, `createPlan` :318, `editPlan` :331, `undo` :350 |
| Background running + errors | `runAI` :376, `requestText` :397 |
| Agent trace (numbered; "Nothing was sent…" when the trace isn't for this request) | `showTrace` :413 |
| Enable/disable buttons | `setBusy` :446, `updateButtons` :453 |
| Re-read the plan into the table / empty state | `refreshPlan` :467 |
| Switch the right panel: results / shopping / nutrition | `showSide` :495 |
| Find the changed day | `snapshot` :506, `firstDifference` :517 |
| F10 view | `loadShoppingList` :529, `copyShoppingList` :590, `exportShoppingList` :599 |
| F09 view | `loadNutrition` :617, `statBox` :656, `dayBar` :666 (draws the bar) |
| Before → now card | `comparison` :715, `dayBox` :733 |
| Table cell look + CHANGED tag | `PlanRenderer` :761 |
| Getters used only by tests | :764 onward |

**State model (React terms):** `changedDay` is like `useState<string|null>`. The table model is the
"rendered list". `refreshPlan()` plays the role of a re-render.

### 10.2 `gui/RecipesPanel.java` (tab 2)
- `search` (:258) → `facade.searchRecipes`; `select(id)` (:269) shows the working copy if one exists.
- `scale` (:277) → `facade.adjustServings`; `substitute` (:293) → `facade.substituteIngredient`
  and records a note "replaces butter" (`noteFor` :420); `reset` (:311) throws the copy away.
- `workingCopies` / `substitutionNotes` maps keep modified recipes per id (not saved anywhere).
- `render` (:341) rebuilds the detail view: header, ingredient rows, nutrition, steps.

### 10.3 `gui/PantryPanel.java` (tab 3)
- `apply(action)` (:142): parse quantity, call `facade.managePantry`. On error, show the message and
  outline the field it's about (quantity / name / unit). On success: `refresh` (:187), select the row,
  show "Added …".
- `CATEGORIES` (:31) = the choices in the Category dropdown.

### 10.4 `gui/PreferencesPanel.java` (tab 4)
- `save` (:143) builds a `UserProfile` from the form → `facade.setupProfile`. On
  `InvalidProfileException`, every problem goes in the red banner and `markField` (:191) outlines the
  matching field (allergy / disliked / servings / cooking time / dietary).
- `load` (:178) fills the form from `facade.getProfile()` (Revert button, tab switch).

### 10.5 Building blocks
- `Theme` (`gui/Theme.java`): colour constants (:21–:46), `install` (:54) sets FlatLaf + `UIManager`
  defaults (fonts, radii, table, tabs), fonts loaded from `resources/fonts` (`loadFonts` :153).
  **Change the look here.**
- `Ui`: `card` (:37), `cardHeader` (:42/:47), `row`, `stack`, `scroll`, text helpers, buttons
  `primary` (:129) / `secondary` (:138) / `danger` (:146) / `chip` / `link`, `readOnlyModel` (:173),
  `table` (:182, left-aligned headers), `leftAligned(spinner)` (:201), `pill` (:211).
- `AlertBox.show(kind, heading, message)` (:48) for SUCCESS / ERROR / INFO.
- `TaskRunner.background()` (:17) runs a task on a worker thread and calls back on the UI thread;
  `direct()` (:45) runs immediately (tests).

---

## 11. The CLI

`presentation/MealPlannerCLI.java`: `runLoop` (:76) reads lines until `quit`; `handle` (:112) is one
big `switch` on the first word. Commands are listed in `HELP` (:39): `profile`, `pantry`, `recipes`,
`search`, `show`, `recommend`, `plan`, `showplan`, `edit`, `undo`, `scale`, `substitute`, `reset`,
`nutrition`, `shopping`, `strategy`, `trace`. Parsing helpers: `profile` (:199, `key=value` pairs),
`pantry` (:249, "pantry add olive oil 500 ml" → name = everything before the last 2 words).
Errors print as `Error: <message>`; the loop never crashes on a `SmartPlateException`.

---

## 12. Tests

`mvn test` runs **134 tests** in 21 classes, all offline (no real AI is called):

| Test class | What it protects |
|---|---|
| `domain/UserProfileTest`, `DietRulesTest`, `PantryTest`, `UnitsTest`, `ShoppingListTest` | domain rules and maths |
| `plan/MealPlanTest` | Composite sums, one meal per day |
| `plan/DaysTest` | day names found in free text; "sun-dried" is not Sunday |
| `agent/ConstraintValidatorTest` | allergen found in ingredients even if tags lie |
| `agent/RequestConstraintsTest` | text → constraints |
| `agent/MealPlanningAgentTest` | the agent loop: invented ids rejected, **exactly one** revision, AI failure |
| `strategy/StrategyTest`, `command/CommandHistoryTest`, `service/ServicesTest` | patterns + services |
| `ai/AIModelTest` | adapter request building, mock modes |
| `ai/AIModelFactoryTest` | every `SMARTPLATE_AI_PROVIDER` value, defaults, `auto` order, missing keys, unknown value |
| `ai/EnvFileTest` | `.env` parsing, missing file, environment wins over the file |
| `ai/GeminiAdapterTest` | the Gemini adapter against a fake Gemini server: URL, key header, request JSON, thought parts skipped, every error, and the whole agent through it |
| `ai/OllamaAdapterTest` | the Ollama adapter against a fake Ollama server (a tiny `HttpServer` in the test): request JSON, `<think>` removal, 404/500/empty/unreachable errors, and the whole agent through it |
| `facade/SmartRecipeFacadeTest` | all 10 features end to end |
| `presentation/MealPlannerCLITest` | a scripted CLI session |
| `presentation/gui/GuiPanelsTest` | every GUI tab, headless (no window needed) |

A test looks like Jest:
```java
@Test
void scalerMultipliesQuantities() {                         // it("scaler multiplies quantities", () => {
    Recipe scaled = new ServingScaler().scale(TestData.pasta(), 5);
    assertEquals(5, scaled.getServings());                  //   expect(scaled.servings).toBe(5)
}                                                           // })
```
To test the agent with a misbehaving AI, use `MockAIModel.scripted("RECIPES: r04, r999", "RECIPES: r09")`.
The first answer is wrong, so the second is used for the revision.

---

## 13. Cookbook: how to make common changes

**Before any change:** `mvn test` should be green. **After:** `mvn test` again, then run the app.

### 13.1 Add a recipe
1. `service/SampleData.java`, in `recipes()` (:19), copy one `Recipe.builder("r30", ...)...build(),`
   block and give it a **new unique id** (e.g. `r31`).
2. Use only units g, kg, ml, l, pcs, and a sensible **category** for each ingredient (meat, seafood,
   dairy, egg, nut, grain…). Diet and allergy checks depend on categories!
3. Two tests count the recipes; update the number 30 in both:
   `ServicesTest.searchMatchesNameIngredientAndTag` (`assertEquals(30, repo.size())`) and
   `GuiPanelsTest.recipesSearchScaleSubstituteAndReset` (`assertEquals(30, ...getRowCount())`).
   Search for `30` in `src/test` to be sure.

### 13.2 Add a substitution
`SampleData.substitutions()` (:281): add `sub("from", "fromCategory", "to", "toCategory", factor, unitOrNull),`.
Order matters: the **first** one that fits the profile wins.

### 13.3 Add a dietary preference (e.g. "keto")
1. `UserProfile.SUPPORTED_DIETS` (:24): add `"keto"`.
2. `DietRules.violatesDiet` (:116): add `case "keto": return ...;`.
3. The GUI checkbox and request parsing ("something keto") pick it up automatically.
4. Add a test in `DietRulesTest`.

### 13.4 Add an allergy group (e.g. "sesame" → tahini, sesame oil)
`DietRules.GROUP_NAMES` (:25): add `"sesame", Set.of("tahini", "sesame")`. Note `Map.of` allows at most
10 entries; beyond that, switch to `Map.ofEntries(Map.entry(...), ...)`.

### 13.5 Add a recipe-selection strategy (e.g. "high protein")
1. New class `strategy/HighProteinStrategy.java` implementing `RecipeSelectionStrategy` (copy
   `FastestMealStrategy`, sort by `-recipe.getNutritionPerServing().getProtein()`).
2. `SmartRecipeFacade.setStrategy` (:243): add `else if (key.startsWith("protein")) strategy = new HighProteinStrategy();`.
3. GUI: add `"protein"` to the combo in `MealPlannerGUI.statusArea` (:122). CLI help text in `MealPlannerCLI.HELP` (:39).
4. Add to `StrategyTest`. Update the class diagram.

### 13.6 Add a new feature end to end (the pattern to follow)
1. **Domain/service:** put the logic in a domain class or a new service (no UI code!).
2. **Facade:** add one public method that validates input and calls the service.
3. **CLI:** new `case` in `MealPlannerCLI.handle` + a line in `HELP`.
4. **GUI:** a button in the right panel → a small method that calls the facade, then refresh the view;
   errors → `AlertBox` / error label.
5. **Tests:** service test + facade test + GUI or CLI test.
6. **Docs:** traceability table, class diagram, sequence diagram, `IMPLEMENTATION.md`.

### 13.7 Change the look
- Colours and fonts: `gui/Theme.java` (constants at the top, `install()` for widget defaults).
- Button styles: `Ui.primary/secondary/danger`.
- Spacing of a panel: the `BorderFactory.createEmptyBorder(top, left, bottom, right)` calls (≈ CSS padding).

### 13.8 Change what the AI is told
- Global rules: `AIAdapter.SYSTEM_PROMPT` (`ai/AIAdapter.java:26`). `OllamaAdapter` and `GeminiAdapter` send the same text.
- Per-request content: `MealPlanningAgent.newPrompt` (:337) and the `RESPONSE_FORMAT` text in
  `recommend` / `createPlan` / `editPlan`. If you change the format, update the parsers
  (`parsePlan`, `parseIdList`) **and** `MockAIModel` so offline mode still works.

### 13.9 Use another AI provider
Write a new class implementing `AIModel` (copy `GeminiAdapter`, the simplest HTTP one), then: add its name
to `AIModelFactory.PROVIDERS`, a `case` in `forProvider` (:69), its key check in `missingSetting` (:94), an
`instanceof` in `providerOf` (:106), a label in `MealPlannerGUI.PROVIDER_LABELS`, the key in `.env.example`,
and tests. The agent doesn't change. That's the point of the Adapter pattern; `OllamaAdapter` and
`GeminiAdapter` were added exactly this way.

### 13.10 Add a GUI tab
New `JPanel` class in `presentation/gui/` that takes the facade in its constructor (copy
`PantryPanel`), then `tabs.addTab("Name", new YourPanel(facade))` in `MealPlannerGUI.buildContent` (:72).

---

## 14. Debugging guide and known issues

### 14.1 How to find a bug fast
1. **Reproduce it in the CLI** (`--cli`). If the CLI has it too, the bug is below the facade;
   otherwise it's in the GUI panel.
2. **Read the agent trace** (GUI right panel or CLI `trace`). It lists the request constraints, the
   shortlist size, the AI's raw answer and every violation.
3. **Write a failing test first** (copy a similar test), then fix until it passes. Example: a recipe
   that should be rejected → a `ConstraintValidatorTest`.
4. **Breakpoints** in IntelliJ at the facade method for that feature (section 4).

### 14.2 Where to look, by symptom
| Symptom | Look in |
|---|---|
| Wrong recipe allowed / blocked for an allergy or diet | `DietRules.matches` (:82), ingredient categories in `SampleData` |
| "avoid X" / "under N minutes" in a request ignored | `RequestConstraints` patterns and word lists (:29–:45) |
| Plan has wrong number of days | `RequestConstraints.planDays` (:173), `Days.firstDays` |
| AI answer rejected unexpectedly | trace → `MealPlanningAgent.parsePlan` / `parseIdList`; `PromptFormat.field` |
| Odd shopping quantities | `PlannedMeal.getIngredients` (scaling), `ShoppingList.consolidate`, `ShoppingListService.generate` |
| Pantry merge problems | `Pantry.addItem` (:20), `Units.convert` |
| Search misses a recipe | `RecipeRepository.search` (:43): every word must appear |
| Button stays disabled / table not updated | the panel's `updateButtons` / `refreshPlan` / `render` (no auto re-render!) |
| Window looks unstyled | `Theme.install()` not called, or fonts missing from `resources/fonts` |
| Real AI errors (503 / "No API key") | env var `ANTHROPIC_API_KEY`, `AIAdapter.generate` (:42) error mapping |
| Wrong AI used / "Unknown SMARTPLATE_AI_PROVIDER" | the setting's spelling (`.env` or environment); `AIModelFactory.create` (:55). The model in use is shown in the GUI's AI model box and the CLI's first line |
| AI model box turns red / "needs an API key" | the key is missing from `.env` (see `.env.example`); `AIModelFactory.missingSetting` (:94) |
| Gemini errors ("rejected the API key", "does not know the model", "rate limited") | `GEMINI_API_KEY` / `GEMINI_MODEL` in `.env`; `GeminiAdapter.extractText` (:107) |
| "Could not reach Ollama" / "ollama pull" / timeout | `ollama serve` running? model pulled? `OLLAMA_URL`; `OllamaAdapter.generate` (:73) |
| Local model keeps failing validation | small models are weaker: try a bigger one (`OLLAMA_MODEL`), check `trace` in the CLI |

### 14.3 Known issues and limitations (good first fixes)

**Fixed during Stage 2** (each fix has a test; commits on `develop`):

| Was | Fix | Where |
|---|---|---|
| Shopping list showed "13.5 pcs garlic" | counted items are rounded up to whole numbers, with a note | `ShoppingList.roundUpQuantities` (:60), called from `ShoppingListService.generate` (commit `6bfd56c`) |
| "sun-dried" in an edit request was read as Sunday | hyphenated words stay whole; only full day names count inside them | `Days.findIn` (:38), used by the agent and the mock (`f70e701`) |
| "avoid red onion" only avoided "red"; "skip the mushrooms" avoided nothing | the whole phrase is read, lists are split on and/or | `RequestConstraints.avoidedItems` (:116) (`86b5b1e`) |
| Disliking "milk" also blocked coconut milk, oat milk, peanut butter | a dislike of a dairy word ignores non-dairy ingredients named after it; allergies stay strict | `DietRules.matchesDislike` (:106) (`db82527`) |
| The AI model could change in the middle of a request | each request keeps the model it started with; the model and strategy boxes are disabled while it runs | `MealPlanningAgent` (:104), `AssistantPanel.onBusyChange` (:441) (`9deb476`) |
| "jalapeño" was a malformed entry | any letters are accepted; accents are ignored when matching | `UserProfile.checkEntries` (:110), `DietRules.withoutAccents` (:50) (`82148d6`) |
| — | edge tests: exactly 5/7 days, 4/8 rejected, smallest profile values, scaling to 1 serving | tests (`94a8a8a`) |
| A second `undo` in the CLI printed "Undone." before "Nothing to undo." | undo first, then print | `MealPlannerCLI` undo case (`f8fe10f`) |
| "dinner for 9.5 days" was read as 5 days; decimal time limits and servings were ignored | decimals are read: days round to the nearest day (a half toward 5–7), counts and servings to nearest, time limits down | `RequestConstraints.roundTowardRange`, `number` (`c022ae4`) |

**Still open:**
1. **The mock AI is simple.** "Swap Tuesday for something with spinach" may pick a recipe without
   spinach, because the mock only re-ranks the 15 shortlisted candidates. Its own avoid filter is a
   plain word match, so with the mock a dislike of "milk" still skips coconut-milk recipes (the
   validator allows them). Real models do better. *Fix idea:* in `MockAIModel.rank` (:163) prefer
   candidates whose text contains request words.
2. **Plans always start on Monday** (`Days.firstDays` :61).
3. **Nothing is saved.** The profile, pantry, plan and working copies are lost when the app closes, and
   the CLI and GUI are separate processes. The course does not require persistence.
4. **Other tabs stay editable during a request.** The model and strategy boxes are locked while an AI
   request runs, but the pantry or preferences can still be changed in another tab during a long
   real-AI call. *Fix idea:* disable the other tabs while `busy`.
5. **Plan edits are never revised.** `editPlan` follows SD05 (one AI answer). If you want a retry,
   copy the loop from `createPlan`, and update SD05 too.
6. **Allergies match by name on purpose.** An allergy to "milk" also blocks "coconut milk". That is
   the safe choice for allergies; dislikes no longer do this.
7. **Claude was never run live.** Gemini and the local Ollama model (gemma4:e4b) were run by the author;
   the Claude path is only unit-tested (request building, error handling).

### 14.4 Common Java errors and what they mean
| Error | Meaning / fix |
|---|---|
| `cannot find symbol` | Typo, missing `import`, or calling a method that doesn't exist |
| `incompatible types: X cannot be converted to Y` | Wrong type, e.g. passing `String` where `int` is expected (`Integer.parseInt(s)`) |
| `NullPointerException` | You used something that is `null` (e.g. no plan yet → `facade.getCurrentPlan()` is `null`) |
| `X is not public in Y; cannot be accessed from outside package` | Make the method `public`, or call it from the same package |
| `HeadlessException` | Opening a window where there is no screen (servers, some test runs). Use `--cli` |
| `ConcurrentModificationException` | Changing a list while looping over it |
| Test failure `expected: <a> but was: <b>` | Your change altered behaviour; check whether the test or the code is right |

---

## 15. Glossary

| Term | Meaning |
|---|---|
| **Agent** | `MealPlanningAgent`: the code that runs the AI loop (shortlist → ask → validate → revise) |
| **Candidate / shortlist** | the ≤15 recipes the strategy lets the AI choose from |
| **Violation** | a sentence describing why an AI choice is not allowed |
| **Revision** | the one extra AI call with the violations included |
| **Trace** | the step-by-step log of the last AI request, shown in the GUI and CLI |
| **Working copy** | a scaled or substituted recipe kept by the Recipes tab / CLI until Reset |
| **Facade** | the single class the UIs talk to |
| **EDT** | Event Dispatch Thread: Swing's UI thread (like the browser's main thread) |
| **Maven** | the build tool (like npm); `pom.xml` is its config |
| **JUnit** | the test framework (like Jest) |
| **FlatLaf** | the Swing look-and-feel (theme) library |
| **Mock AI** | `MockAIModel`, an offline stand-in for Claude used by default and in tests |
