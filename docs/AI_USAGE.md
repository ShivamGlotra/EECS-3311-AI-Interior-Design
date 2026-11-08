# AI Assistance Disclosure — SmartPlate (EECS 3311)

This document states where an AI tool helped produce the SmartPlate implementation. It keeps two
different uses of AI apart:

1. **AI as a development tool:** an AI coding assistant helped write this project's code and documents.
2. **AI as a component of the product:** SmartPlate itself calls an LLM at run time (the `AIModel`
   abstraction from the design report). That is part of the required design, not help with the coursework.

---

## 1. Tool used

| Item | Detail |
|---|---|
| Tool | **Claude Code**, Anthropic's AI coding assistant, used through claude.ai/code |
| LLM model | as shown in Claude Code's session details; recorded in [`STAGE2_REPORT.md` §5](STAGE2_REPORT.md#5-ai-tools-and-collaboration-log) |
| Other AI tools | none (SmartPlate is an individual project by Shivam Glotra; only Claude Code was used) |
| Used for | Generating the Java implementation, the unit tests, the sample data and the documentation in this branch |
| Input given to the tool | The author's **Stage 1 design report and UML diagrams** (branch `stage-1Fix`: `README.md`, `docs/uml-diagrams/**`, since moved to `docs/plantuml-diagrams/`; the final UMLet versions are in `docs/umlet-diagrams/`), plus the instruction to implement every feature as described in the report, explain the code, and document the AI assistance |
| Where its output lives | Commits authored by Claude Code (on the `claude/*` and `docs/stage2-deliverables` branches, plus `c022ae4`): `pom.xml`, `src/**`, `.env.example`, `docs/IMPLEMENTATION.md`, `docs/DEVELOPER_GUIDE.md`, `docs/STAGE2_REPORT.md`, `docs/DEMO_SCRIPT.md`, `docs/AI_USAGE.md`, `docs/screenshots/**`, `docs/umlet-diagrams/stage2/**`, `stage2-report.pdf`/`.docx` and the setup and documents sections of `README.md`. Code the author wrote or applied is listed in §2. |

The Stage 1 report (requirements, features F01–F10, use cases, class and sequence diagrams, and the
choice of the five design patterns) was the **input** to the tool. This disclosure does not cover
how that report was written.

---

## 2. What the AI produced

| Area | Files | Produced by AI? | Notes |
|---|---|---|---|
| Build setup | `pom.xml` | Yes | Maven, Java 17, JUnit 5, Anthropic Java SDK, runnable jar |
| Domain layer | `domain/*` | Yes | Follows the report's class diagram. Added helpers: `Units`, `DietRules`, exceptions |
| Composite | `plan/*` | Yes | `PlanComponent`, `MealPlan`, `PlannedMeal` as in the diagram |
| Adapter / AI | `ai/*` | Yes | `AIModel`, `AIAdapter`, `AIClient` (official SDK), `MockAIModel`, `PromptFormat` |
| Agent | `agent/*` | Yes | Retrieve → strategy → AI → validate → revise once → return/report loop, from report §1.4 and SD03–SD05 |
| Strategy | `strategy/*` | Yes | Pantry-first and Fastest strategies |
| Command | `command/*` | Yes | `ReplaceMealCommand`, `CommandHistory` |
| Services and data | `service/*` | Yes | Includes the 30 sample recipes, starter pantry and 33 substitutions |
| Facade | `facade/SmartRecipeFacade.java` | Yes | One method per feature, as in the diagram |
| User interfaces | `presentation/*`, `app/*` | Yes | Swing GUI and CLI; both use only the facade |
| GUI redesign ("Clean Slate", light mode) | `presentation/MealPlannerGUI.java`, `presentation/gui/**`, FlatLaf in `pom.xml` | Yes | Claude Code proposed three design directions as mockups; **the author chose Direction B**, and Claude Code then implemented it in Swing. The IBM Plex font files are IBM's (SIL Open Font License), not AI-generated. |
| AI provider choice | `ai/AIModelFactory.java`, `app/SmartPlateApp.java` (`chooseModel` and the error handling in `main`) | **Partly** | The author wrote the factory that picks mock / anthropic / ollama from `SMARTPLATE_AI_PROVIDER`, and `SmartPlateApp`'s use of it. At the author's request Claude Code later extended the factory with the `gemini` provider, `.env` loading and the helpers the GUI switcher needs (`PROVIDERS`, `forProvider`, `missingSetting`, `providerOf`) |
| Gemini adapter, `.env` support, AI model switcher | `ai/GeminiAdapter.java`, `ai/EnvFile.java`, `.env.example`, `.gitignore`, `SmartRecipeFacade.setAIProvider`, `MealPlanningAgent.setAIModel`, `MealPlannerGUI.modelSwitcher` | Yes | Requested by the author; written by Claude Code |
| Local model adapter | `ai/OllamaAdapter.java` | Yes | Written by Claude Code to the constructor and constants the author's factory expects |
| Stage 2 defect fixes A1–A9 (shopping-list rounding, "sun-dried", multi-word avoid, dairy dislikes, one model per request, accents, edge tests, CLI undo message, decimal numbers, substitution steps) | `ShoppingList`, `ShoppingListService`, `SubstitutionService`, `Recipe`, `Days`, `MealPlanningAgent`, `MockAIModel`, `RequestConstraints`, `DietRules`, `Ingredient`, `UserProfile`, `AssistantPanel`, `MealPlannerGUI` and their tests | **Partly** | Claude Code reproduced each defect, explained the cause and wrote the proposed code, tests and test steps (for A4–A6 also patch files). The author chose which defects to fix, applied the changes on its own `fix/*` branches, ran the tests and the GUI/CLI checks, and committed and merged them (commits `6bfd56c` … `94a8a8a`). A8 and A9 were committed by Claude Code on their own branches at the author's request; the author reviewed and merged them (PRs #10, #13). |
| Tests | `src/test/**` | Yes (most); the A1–A7 tests were applied by the author | 134 JUnit 5 tests in 21 classes (16 of them for the GUI; 24 for the AI model factory, `.env`, Ollama and Gemini adapters) |
| Documentation | `docs/IMPLEMENTATION.md`, `docs/DEVELOPER_GUIDE.md`, this file | Yes | Code explanation, developer guide and this disclosure |
| Stage 2 report | `docs/STAGE2_REPORT.md` | Drafted by AI | Tools table, collaboration log, traceability and design changes, drafted from the session history; confirmed and completed by the author |

**Sample data caveat:** the recipes, quantities and nutrition values in `SampleData.java` were made
up by the AI for demonstration. They were **not** checked against a nutrition database and should
not be treated as accurate dietary information.

---

## 3. How the AI's output was checked

These checks were run during generation and can be repeated by anyone:

| Check | How to repeat | Result at time of writing |
|---|---|---|
| Compiles | `mvn compile` | Success |
| Unit tests | `mvn test` | 134 tests, 0 failures, 0 errors |
| Runnable jar | `mvn package` then `java -jar target/smartplate-1.0.0.jar --cli` | Starts; commands work |
| Scripted CLI session covering F01–F10 | See `MealPlannerCLITest` and the session in `docs/IMPLEMENTATION.md` §1 | Expected output |
| GUI | Launched on a virtual display and driven through every state (empty, error, plan, edit, shopping list, nutrition, recipes, pantry error, preferences error) | Works; screenshots in `docs/screenshots/`. One bug was found by the new GUI tests and fixed (the pantry "Added …" message was cleared by the row selection). |
| Agent revision behavior | `MealPlanningAgentTest` (scripted bad AI answers) | Invented ids and constraint violations are rejected; exactly one revision; failure reported after that |

**Ollama adapter:** tested against a fake Ollama server that answers `/api/chat` in Ollama's JSON
format (`OllamaAdapterTest`), and the packaged jar was run against such a fake server. It was **not**
run against a real local model while generating the code. **The author then ran the application with the
local model `gemma4:e4b` and it worked.**

**Gemini adapter:** tested against a fake Gemini server (`GeminiAdapterTest`). It was **not** run
against the real Gemini API while generating the code (no key was available). **The author then ran the
application with a Gemini key (free API) and it worked.**

**Not verified:** the live call to the real Claude API (`AIAdapter` → `AIClient`). No API key was
available while generating the code. The request-building code is unit tested
(`AIModelTest.adapterTranslatesThePromptIntoAProviderRequest`), and the no-key error path is
tested, but no real network call was made. Every test and the default run use the offline `MockAIModel`.

---

## 4. Author review record

The AI generated most of the code, but the author is responsible for understanding and defending it.
SmartPlate is an individual project, so there is one reviewer.

| Reviewer | Files / features reviewed | Changes made after review | Date |
|---|---|---|---|
| Shivam Glotra | The whole application through the GUI and CLI; the AI layer (`AIModelFactory`, which the author wrote, and the Ollama/Gemini adapters); the agent, domain and GUI code touched by fixes A1–A9 | Designed and wrote `AIModelFactory`; asked for the GUI redesign, `.env` key handling, the Gemini adapter and the model switcher; applied and tested fixes A1–A7 on separate branches; found bug A8 and chose its rounding rule; found bug A9 while recording and reviewed its fix; ran the app with Gemini and `gemma4:e4b` | 15 Oct – 6 Nov 2026 |

Questions worth answering during review (the TA may ask them):

* Can the author explain the agent loop in `MealPlanningAgent` and where each of the five patterns lives?
* Do the added helper classes (`Units`, `DietRules`, `RequestConstraints`, `PromptFormat`) need to be
  added to the class diagram for the final submission?
* Are the sample recipes and nutrition values acceptable for the demo?

---

## 5. AI as a component of SmartPlate (run time)

As the design report requires, SmartPlate uses an LLM only for **choosing among existing
recipes** (F04, F05, F06). The LLM never invents recipes or substitutions, and deterministic Java
code (`ConstraintValidator`) checks every answer before the user sees it.

| Mode | When | Network / cost |
|---|---|---|
| `MockAIModel` | Default; always used in tests | None |
| `AIAdapter` → `AIClient` → Claude (Anthropic API) | `SMARTPLATE_AI_PROVIDER=anthropic`, or the default `auto` when `ANTHROPIC_API_KEY` is set | Network call; billed to the key's owner |
| `GeminiAdapter` → Gemini (Google API) | `SMARTPLATE_AI_PROVIDER=gemini`, `auto` when only `GEMINI_API_KEY` is set, or the GUI's AI model box | Network call; billed to (or counted against the free quota of) the key's owner |
| `OllamaAdapter` → a local model through Ollama | `SMARTPLATE_AI_PROVIDER=ollama` or the GUI's AI model box | Runs on the user's machine; no cost, nothing leaves the machine with the default URL |

No API key is stored in the repository: keys live in a local `.env` file that `.gitignore` excludes.
