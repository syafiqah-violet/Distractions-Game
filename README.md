# Refrigerator Sorting Game

JavaFX drag-and-drop sorting game. Build plan and design decisions live in
[FRIDGE_GAME_PLAN.md](FRIDGE_GAME_PLAN.md).

## Requirements

- JDK 21+ (found: Temurin 21.0.7)
- No Maven install needed — use the bundled wrapper (`mvnw` / `mvnw.cmd`)

## Commands

```bash
./mvnw javafx:run     # run the game
./mvnw test           # unit tests
./mvnw clean package  # build target/fridge-game-1.0-SNAPSHOT.jar
```

On PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

## Layout

```
src/main/java/com/fridgegame/
├── Launcher.java        # plain main() — keeps JavaFX out of the JAR manifest
├── FridgeGameApp.java   # Application; owns the Stage and the single Scene
├── model/               # FoodCategory, StorageZone, GroceryItem, Level, GameState
├── data/                # ItemCatalog
├── view/                # GroceryNode, ZoneNode, FridgeView, CounterView, HudView
└── controller/          # GameController, DragHandler

src/main/resources/com/fridgegame/styles.css
src/test/java/com/fridgegame/
```

## Progress

- [x] **Phase 0 — Setup:** pom, wrapper, `Launcher`, blank 1000×700 window
- [x] **Phase 1 — Model:** enums, `GroceryItem`, `Level`, `GameState`, `ItemCatalog` (15 items, 3 levels)
- [x] **Phase 2 — Static layout:** `FridgeView`, `ZoneNode`, `CounterView`, `GroceryNode`, `HudView`
- [ ] Phase 3 — Drag & drop (Dragboard API)
- [ ] Phase 4 — Rules & scoring
- [ ] Phase 5 — Timer & levels
- [ ] Phase 6 — Polish

## Pinned versions

| Thing | Version | Note |
|---|---|---|
| JavaFX | 21.0.12 | latest 21 LTS patch (plan said 21.0.4) |
| javafx-maven-plugin | 0.0.8 | confirmed still the newest release |
| JUnit Jupiter | 5.14.4 | |
| Maven (wrapper) | 3.9.16 | |
