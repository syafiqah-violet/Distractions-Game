# Refrigerator Sorting Game

JavaFX game in two halves on one 60-second clock: play **Tetris against a local LLM** to
earn groceries, then drag each earned item into the right fridge zone. Build plan and
design decisions live in [FRIDGE_GAME_PLAN.md](FRIDGE_GAME_PLAN.md).

**Every grocery has to be earned.** Clear one row of Tetris, get one item on the counter;
clear four at once, get four. Sort the whole level quota before time runs out to advance.
Meanwhile an opponent board is driven by a local LLM — when it clears N rows it pushes
N−1 garbage rows into your stack.

## Requirements

- JDK 21+ (found: Temurin 21.0.7)
- No Maven install needed — use the bundled wrapper (`mvnw` / `mvnw.cmd`)
- Optional: a reachable OpenAI-compatible LLM endpoint. Without one the game plays
  identically against a built-in heuristic bot.

## Commands

```bash
./mvnw javafx:run     # run the game
./mvnw test           # unit tests
./mvnw clean package  # build target/fridge-game-1.0-SNAPSHOT.jar
```

On PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

## Controls

| Key | Action |
|---|---|
| `←` `→` (or `A` `D`) | move the piece |
| `↑` (or `W`) | rotate clockwise |
| `↓` (or `S`) | soft drop |
| `Space` | hard drop |
| mouse drag | move an earned grocery from the counter to a fridge zone |

Gravity pauses while you are mid-drag — JavaFX runs a nested event loop during a drag
gesture and would not deliver your key presses. The round clock keeps running, so
stalling a drag costs you time and gains you nothing.

## The LLM opponent

The second board is played by an agent behind the `TetrisAgent` interface. On startup the
game probes the configured endpoint; if it answers, each of the opponent's pieces is
placed by the model, otherwise the local heuristic bot takes over. The caption under the
opponent's board always says which is driving, and counts `N LLM · N fallback` so a
silently-degrading endpoint is visible rather than just making the opponent dull.

### Configuration

Precedence: environment variables, then a gitignored `llm.properties` in the working
directory, then built-in defaults. Copy [llm.properties.example](llm.properties.example)
to get started.

| Env var | Property | Default |
|---|---|---|
| `LLM_BASE_URL` | `llm.baseUrl` | `http://172.23.35.112:8001` |
| `LLM_MODEL` | `llm.model` | `qwen3.6-35b` |
| `LLM_API_KEY` | `llm.apiKey` | *(none — header omitted)* |

No key is compiled into the source or the jar. The endpoint currently accepts
unauthenticated requests, so an absent key is a supported configuration.

To confirm the offline path, point it at a dead port and check the caption reads
`local bot · Connection refused`:

```bash
LLM_BASE_URL=http://127.0.0.1:9 ./mvnw javafx:run
```

### Two request details that are not optional

Both were established by probing the live endpoint, and both are covered by
`LlmClientTest` so they cannot silently regress:

1. **`chat_template_kwargs: {"enable_thinking": false}`** — Qwen3.6 is a reasoning model.
   Left on, it spends its entire token budget in a separate `reasoning` field and returns
   `content: null` with `finish_reason: "length"` — about 4 seconds to say nothing. Off,
   it answers in ~650ms.
2. **HTTP/1.1** — Java's `HttpClient` defaults to HTTP/2, which over plaintext means an
   h2c upgrade attempt. vLLM's uvicorn mishandles it and *drops the request body*, so
   every POST returns `400 Field required: body`. `curl` works by default because it
   sends HTTP/1.1, which makes this a genuinely confusing failure to diagnose.

Measured against `qwen3.6-35b` on the DGX box: 10/10 legal placements, ~843ms average,
1030ms worst case — inside the tightest per-move deadline (1300ms at level 3).

## Difficulty

All levels run 60 seconds; difficulty comes from gravity and the opponent, not a shorter
clock. Tune these in `ItemCatalog.LEVELS`.

| Level | Items to earn & sort | Your gravity | Opponent cadence | Sends garbage |
|---|---|---|---|---|
| 1 | 4 | 700ms | 2000ms | no |
| 2 | 6 | 550ms | 1600ms | yes (N−1) |
| 3 | 8 | 420ms | 1300ms | yes (N−1) |

## Layout

```
src/main/java/com/fridgegame/
├── Launcher.java        # plain main() — keeps JavaFX out of the JAR manifest
├── FridgeGameApp.java   # Application; owns the Stage, the Scene and the level flow
├── model/               # FoodCategory, StorageZone, GroceryItem, Level, GameState,
│                        # Cell, Tetromino, TetrisBoard, TetrisMove, StepResult
├── data/                # ItemCatalog, HighScoreStore
├── ai/                  # TetrisAgent (interface), HeuristicTetrisAgent
├── llm/                 # LlmConfig, LlmClient, JsonChat, LlmTetrisAgent
├── view/                # GroceryNode, ZoneNode, FridgeView, CounterView, HudView,
│                        # TetrisBoardView, VersusView,
│                        # LevelCompleteView, GameOverView, ViewTransitions
└── controller/          # GameController, DragHandler, TetrisController, AgentController

src/main/resources/com/fridgegame/
├── styles.css
└── images/food_pixel/   # pixel-art icon per grocery item (rendered via ImageView)
src/test/java/com/fridgegame/
```

`model/` has no JavaFX imports at all — the entire Tetris engine and every game rule is
plain Java, which is why the suite runs headless with no display and no network.
`TetrisBoardView` draws onto a `Canvas` rather than building 200 `Region` nodes, so the
scene graph is not doing layout passes 20 times a second while you drag groceries.

## Progress

- [x] **Phase 0 — Setup:** pom, wrapper, `Launcher`, blank 1000×700 window
- [x] **Phase 1 — Model:** enums, `GroceryItem`, `Level`, `GameState`, `ItemCatalog` (15 items, 3 levels)
- [x] **Phase 2 — Static layout:** `FridgeView`, `ZoneNode`, `CounterView`, `GroceryNode`, `HudView`
- [x] **Phase 3 — Drag & drop:** `DragHandler` wires the Dragboard API onto counter items and fridge zones
- [x] **Phase 4 — Rules & scoring:** `GameController` scores correct/wrong drops, updates streak/lives, rejects wrong-zone drops
- [x] **Phase 5 — Timer & levels:** countdown `Timeline`, level-complete banner with time bonus, game over + restart
- [x] **Phase 6 — Polish:** drop feedback animations, `HighScoreStore` persisted via `Preferences`,
      pixel-art icons in place of emoji, gradient-backdrop Game Over/Level Complete screens with a
      shared fade+scale entrance animation, styles pass

### Tetris versus an LLM agent

- [x] **T1 — Engine:** `Tetromino` (7 pieces × 4 normalised rotations), `TetrisBoard`
      (10×20, 7-bag randomiser, line clears, garbage, top-out) — pure Java, 33 tests
- [x] **T2 — Player board:** `TetrisBoardView` on a `Canvas` with a landing ghost,
      `TetrisController` gravity `Timeline`, keys bound as a Scene event *filter* so the
      counter's `ScrollPane` cannot swallow the arrows
- [x] **T3 — Rows buy groceries:** counter starts empty; `awardClearedRows` releases one
      item per row, surplus rows pay points instead. Gravity pauses during a drag gesture
- [x] **T4 — Opponent:** second board, `HeuristicTetrisAgent` (lines/height/holes/bumpiness),
      N−1 garbage exchange, top-out costs a life and wipes the board
- [x] **T5 — LLM agent:** `LlmTetrisAgent` behind the same interface, guided-decoding JSON,
      startup reachability probe, per-move fallback to the bot on a late or illegal answer
- [x] **T6 — Polish:** green flash on your clears, red flash on incoming garbage, agent
      caption with LLM/fallback counts, rows-earned line on the level-complete card

## Pinned versions

| Thing | Version | Note |
|---|---|---|
| JavaFX | 21.0.12 | latest 21 LTS patch (plan said 21.0.4) |
| javafx-maven-plugin | 0.0.8 | confirmed still the newest release |
| JUnit Jupiter | 5.14.4 | |
| Jackson Databind | 2.22.2 | only for the LLM request/response JSON |
| Maven (wrapper) | 3.9.16 | |
