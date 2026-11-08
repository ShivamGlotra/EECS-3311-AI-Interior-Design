# Stage 2 UML diagrams (implemented system)

UMLet / UMLetino files describing the system **as implemented** in Stage 2. The Stage 1 design
diagrams in the parent folders (`../class`, `../use-case`, `../sequence`) are unchanged, so design and
implementation can be compared side by side.

| Diagram | UMLet file | Change from Stage 1 |
|---|---|---|
| Class diagram | [class/SmartPlate_Stage2_ClassDiagram.uxf](class/SmartPlate_Stage2_ClassDiagram.uxf) ([PNG](class/SmartPlate_Stage2_ClassDiagram.png)) | Updated. Classes added during implementation have a blue background; every pattern role is labelled (Facade, Adapter target/adapter/adaptee, Strategy, Command/Invoker, Composite/Leaf, Factory). |
| Use-case diagram | [use-case/SmartPlate_Stage2_UseCase.uxf](use-case/SmartPlate_Stage2_UseCase.uxf) ([PNG](use-case/SmartPlate_Stage2_UseCase.png)) | Updated: new UC11 Select AI Model (blue). |
| SD03 AI recommendation (F04 / UC04) | [sequence/SD03_AI_Recommendation_Stage2.uxf](sequence/SD03_AI_Recommendation_Stage2.uxf) ([PNG](sequence/SD03_AI_Recommendation_Stage2.png)) | Updated: request constraints (`RequestConstraints`), 15-candidate shortlist, "no candidates" branch, one model per request. |
| SD04 AI weekly plan (F05 / UC05) | [sequence/SD04_AI_Meal_Plan_Stage2.uxf](sequence/SD04_AI_Meal_Plan_Stage2.uxf) ([PNG](sequence/SD04_AI_Meal_Plan_Stage2.png)) | Updated: request constraints and the 5-7 day check before any AI call. |
| SD05 Edit plan and undo (F06 / UC06) | [sequence/SD05_Edit_Plan_Undo_Stage2.uxf](sequence/SD05_Edit_Plan_Undo_Stage2.uxf) ([PNG](sequence/SD05_Edit_Plan_Undo_Stage2.png)) | Updated: the day is read from the request (`Days.findIn`) or chosen by the AI. |
| SD07 Select AI model (F11 / UC11) | [sequence/SD07_Select_AI_Model.uxf](sequence/SD07_Select_AI_Model.uxf) ([PNG](sequence/SD07_Select_AI_Model.png)) | New. |

SD01 (preferences and pantry), SD02 (recipe search) and SD06 (recipe utilities) still match the
implementation, so the Stage 1 versions in `../sequence` remain current.

**About the PNGs.** Each PNG was drawn directly from its `.uxf` file (element positions, text and
lines), so picture and file always agree. After reviewing a diagram in UMLetino (umletino.com:
*File > Open* the `.uxf`), you can replace its PNG with UMLetino's own export (*File > Export as
PNG*) under the same name; the report links to these paths.
