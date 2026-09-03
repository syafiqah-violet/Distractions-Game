# Refrigerator Sorting Game — Build Plan

**Stack:** Java 21+, JavaFX 21 (LTS), Maven with `javafx-maven-plugin`, shapes + emoji for art.

**Core loop:** groceries appear on a counter → player drags each into a fridge zone → correct zone scores points, wrong zone costs points → clear all items before the timer runs out → next level.

---

## 1. Project structure

```
fridge-game/
├── pom.xml
└── src/main/
    ├── java/
    │   └── com/yourname/fridgegame/
    │       ├── Launcher.java            # main() that calls FridgeGameApp
    │       ├── FridgeGameApp.java       # extends Application, owns the Stage
    │       ├── model/
    │       │   ├── FoodCategory.java    # enum: DAIRY, PRODUCE, MEAT, DRINKS, FROZEN
    │       │   ├── StorageZone.java     # enum: DOOR, TOP_SHELF, CRISPER, FREEZER...
    │       │   ├── GroceryItem.java     # record: id, name, emoji, category
    │       │   ├── Level.java           # items to place + time limit
    │       │   └── GameState.java       # score, lives, streak, current level
    │       ├── data/
    │       │   └── ItemCatalog.java     # the master list of groceries + levels
    │       ├── view/
    │       │   ├── GroceryNode.java     # draggable visual for one item
    │       │   ├── ZoneNode.java        # a drop target (shelf/drawer/door bin)
    │       │   ├── FridgeView.java      # assembles zones into a fridge
    │       │   ├── CounterView.java     # holds the not-yet-placed items
    │       │   └── HudView.java         # score, timer, level label
    │       └── controller/
    │           ├── GameController.java  # rules, scoring, level flow
    │           └── DragHandler.java     # wires drag events to nodes/zones
    └── resources/
        └── com/yourname/fridgegame/
            └── styles.css
```

`Launcher.java` exists so the JAR has a main class that isn't an `Application` subclass — this avoids the "JavaFX runtime components are missing" error when running outside the Maven plugin.

### pom.xml essentials

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <javafx.version>21.0.4</javafx.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>${javafx.version}</version>
  </dependency>
</dependencies>

<build><plugins>
  <plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
      <mainClass>com.yourname.fridgegame.Launcher</mainClass>
    </configuration>
  </plugin>
</plugins></build>
```

To run it yourself going forward, from d:\Refrigerator Game:

PowerShell: .\mvnw.cmd javafx:run
Git Bash / this shell: ./mvnw javafx:run

---

## 2. The model (build this first — pure Java, fully testable)

```java
public enum FoodCategory { DAIRY, PRODUCE, MEAT, DRINKS, FROZEN }

public enum StorageZone {
    DOOR      ("Door",       EnumSet.of(FoodCategory.DRINKS)),
    TOP_SHELF ("Top Shelf",  EnumSet.of(FoodCategory.DAIRY)),
    MID_SHELF ("Mid Shelf",  EnumSet.of(FoodCategory.MEAT)),
    CRISPER   ("Crisper",    EnumSet.of(FoodCategory.PRODUCE)),
    FREEZER   ("Freezer",    EnumSet.of(FoodCategory.FROZEN));

    private final String label;
    private final Set<FoodCategory> accepted;

    StorageZone(String label, Set<FoodCategory> accepted) {
        this.label = label;
        this.accepted = accepted;
    }

    public String label() { return label; }

    public boolean accepts(GroceryItem item) {
        return accepted.contains(item.category());
    }
}

public record GroceryItem(String id, String name, String emoji, FoodCategory category) {}
```

`GameState` holds `IntegerProperty score`, `IntegerProperty lives`, `IntegerProperty secondsLeft`, `IntegerProperty level`.

Use JavaFX properties, not plain `int`s — then the HUD binds to them and updates itself with zero glue code:

```java
scoreLabel.textProperty().bind(state.scoreProperty().asString("Score: %d"));
timeLabel.textProperty().bind(state.secondsLeftProperty().asString("Time: %ds"));
```

That binding trick is the single biggest simplifier in the whole project. Do it early.

### Starter item catalog (~15 items)

| Emoji | Name | Category | Correct zone |
|---|---|---|---|
| 🥛 | Milk | DAIRY | Top Shelf |
| 🧀 | Cheese | DAIRY | Top Shelf |
| 🧈 | Butter | DAIRY | Top Shelf |
| 🥬 | Lettuce | PRODUCE | Crisper |
| 🥕 | Carrot | PRODUCE | Crisper |
| 🍅 | Tomato | PRODUCE | Crisper |
| 🥦 | Broccoli | PRODUCE | Crisper |
| 🍗 | Chicken | MEAT | Mid Shelf |
| 🥩 | Steak | MEAT | Mid Shelf |
| 🐟 | Fish | MEAT | Mid Shelf |
| 🧃 | Juice | DRINKS | Door |
| 🥤 | Soda | DRINKS | Door |
| 💧 | Water | DRINKS | Door |
| 🍦 | Ice Cream | FROZEN | Freezer |
| 🧊 | Ice | FROZEN | Freezer |

---

## 3. Drag and drop — the crux

Use JavaFX's **Dragboard API** (`startDragAndDrop`), not manual mouse-move dragging. It gives you drop-target enter/exit events for free, which is exactly what you need to highlight a shelf when the player hovers over it.

### On the grocery node (drag source)

```java
node.setOnDragDetected(e -> {
    Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
    ClipboardContent content = new ClipboardContent();
    content.putString(item.id());               // dragboard only carries simple data
    db.setContent(content);
    db.setDragView(node.snapshot(null, null));  // item follows the cursor
    node.setOpacity(0.3);                       // ghost the original
    e.consume();
});

node.setOnDragDone(e -> {
    node.setOpacity(1.0);
    if (e.getTransferMode() == TransferMode.MOVE) {
        counter.remove(node);   // it landed somewhere; take it off the counter
    }
    e.consume();
});
```

Because the dragboard only carries a `String`, keep a `Map<String, GroceryItem>` in `ItemCatalog` and look the id back up on drop.

### On the zone node (drop target)

```java
zone.setOnDragOver(e -> {
    if (e.getGestureSource() != zone && e.getDragboard().hasString()) {
        e.acceptTransferModes(TransferMode.MOVE);
    }
    e.consume();
});

zone.setOnDragEntered(e -> zone.getStyleClass().add("zone-hover"));
zone.setOnDragExited(e  -> zone.getStyleClass().remove("zone-hover"));

zone.setOnDragDropped(e -> {
    GroceryItem item = catalog.byId(e.getDragboard().getString());
    boolean correct = zone.getZone().accepts(item);
    controller.handleDrop(item, zone.getZone());   // scoring lives in the controller
    e.setDropCompleted(correct);                   // false → item stays on the counter
    e.consume();
});
```

### Two gotchas that will each cost you an hour

1. **A drop target must have a non-transparent background** (or `setPickOnBounds(true)`) or it won't receive drag events at all. Use a `StackPane` or `VBox` with a CSS background colour for zones — never a bare `Group`.
2. **`setDropCompleted(false)` is your bounce-back.** Since the node is only removed from the counter in `setOnDragDone` when the transfer actually succeeded, a wrong drop needs no cleanup code — the item simply never leaves the counter.

### Zone layout

Each `ZoneNode` is a `VBox` with a header `Label` and a `FlowPane` body that accepted items get added into. `FlowPane` wraps automatically as items pile up, so you never write positioning code.

---

## 4. Scoring and rules

In `GameController.handleDrop(item, zone)`:

| Outcome | Effect |
|---|---|
| Correct zone | `+10 × (1 + streak/5)`, streak++, item stays in the zone |
| Wrong zone | `−5`, streak resets to 0, lives−−, item bounces back to counter |
| Counter empties | Level complete → time bonus `secondsLeft × 2` → next level |
| Timer hits 0, or lives hit 0 | Game over screen |

Add a red flash `Timeline` on the zone for wrong-drop feedback, and a green scale-pulse for correct.

---

## 5. Timer and game flow

One `Timeline` owned by the controller:

```java
timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
    state.setSecondsLeft(state.getSecondsLeft() - 1);
    if (state.getSecondsLeft() <= 0) endGame();
}));
timer.setCycleCount(Animation.INDEFINITE);
timer.play();
```

Screens are just root nodes you swap on the `Scene`: `MenuView` → `GameView` → `GameOverView`.

Keep **one** `Scene` and call `scene.setRoot(...)` — recreating Scenes loses your stylesheet and resets the window size.

---

## 6. Build phases

| Phase | Goal | You'll know it works when | Est. |
|---|---|---|---|
| **0. Setup** | `pom.xml`, `Launcher`, blank 1000×700 window | `mvn javafx:run` opens a window | 30 min |
| **1. Model** | Enums, record, `GameState`, `ItemCatalog` with ~15 items | JUnit test: `CRISPER.accepts(lettuce) == true` | 1 h |
| **2. Static layout** | Fridge with 5 zones on the left, counter on the right, HUD on top — no interaction yet | Looks like a fridge, resizes sensibly | 2 h |
| **3. Drag & drop** | Items drag from counter into zones, zones highlight on hover | Any item can be dropped into any zone | 2–3 h |
| **4. Rules & scoring** | Correct/wrong logic, score bindings, bounce-back | Score changes correctly, wrong drops rejected | 1–2 h |
| **5. Timer & levels** | Countdown, level complete, game over, restart | Full playthrough start to finish | 2 h |
| **6. Polish** | `styles.css`, fade/scale transitions, high score saved | It feels like a game | open-ended |

Phases 0–5 are roughly a solid weekend.

**Don't start phase 3 until phase 2 renders correctly** — debugging drag events on top of a broken layout is miserable, because a mispositioned invisible node silently swallows every drop.

---

## 7. Testing

- **Unit tests (no JavaFX needed):** `StorageZone.accepts()` for every category/zone pair; scoring maths in `GameController` including the streak multiplier; the level-advance condition.
- **Manual checklist:** drag each item to a wrong zone (must bounce back); drag outside the window (must return to counter); let the timer expire mid-drag; restart after game over (score and lives must reset); resize the window mid-game.

---

## 8. Stretch ideas, once it plays

- Expiry dates: items spoil if left on the counter too long
- A "wrong zone" penalty that visually rots the item
- Difficulty curve: shorter timers and more items per level
- Sound effects via `AudioClip`
- High-score table persisted with `java.util.prefs.Preferences` (no file handling needed)
