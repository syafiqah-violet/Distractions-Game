# Distractions Game

JavaFX game in two halves: play **Tetris** to earn groceries, then drag each earned item
into the right fridge zone, then find matching pairs from memory. Four levels, each
teaching a different thing — and a local LLM plays the rival commentator and the difficulty
director rather than playing Tetris.

| Level | What you do | Clock | Pass | Fail |
|---|---|---|---|---|
| 1 | Sort only — the shopping is already on the counter, no board | 60s | every item in the right zone | clock expires with items left |
| 2 | Tetris only — nothing to sort, score as much as you can | 60s | survive the clock, having cleared ≥ 1 row | 0 rows when it expires |
| 3 | Both — clear a row to earn a grocery, then sort it | 3 min | every item sorted | clock expires with items left |
| 4 | Mix and match — 12 grocery cards face down, flip two at a time | 60s | all 6 pairs matched | clock expires with pairs left |

Losing all three lives ends the run on any level. A wrong drop, a top-out and a mismatched
pair each cost one.

## Download and play

Grab the archive for your platform from
[Releases](https://github.com/syafiqah-violet/Distractions-Game/releases), unpack
it, and run `DistractionsGame.exe` (Windows) or `bin/DistractionsGame` (Linux). A Java
runtime is bundled, which is both why nothing needs installing and why the download is
~95MB.

The build is unsigned, so Windows shows "unknown publisher" on first launch — **More info
→ Run anyway**.

A downloaded copy plays the **offline path**. The rival's commentary and the adaptive
difficulty both want an LLM endpoint on your own machine, and the default address is a LAN
box that is not yours; all four levels play through regardless, just without those two.
[Configuration](#configuration) covers pointing it at your own. To watch the model think —
the `[llm]` and `LLM thinking:` lines — run from source: the packaged launcher opens no
console of its own.

## Requirements

Only for building or running from source. The release download needs none of it.

- JDK 21+ (found: Temurin 21.0.7)
- No Maven install needed — use the bundled wrapper (`mvnw` / `mvnw.cmd`)
- Optional: a reachable OpenAI-compatible LLM endpoint. Without one every level plays
  identically — you just lose the rival's commentary and the adaptive difficulty.

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

## What the LLM actually does

It used to play the opponent's Tetris board, one `{"rotation":R,"column":C}` per piece.
That worked — 10/10 legal placements at ~843ms — and it was invisible: nobody can watch a
half-scale board and infer that a model chose those placements. So it now has two jobs it
is genuinely better at.

**Rival commentator** (`LlmCommentator`, level 3 only, bottom-right card). One dry line
reacting to the specific thing that just happened: milk in the freezer, a placement that
buried two cells, a top-out, a five-item streak, thirty seconds left with four unsorted.
A caption is read in half a second, which is the attention a player has spare mid-game.

`CommentaryController` is the part that makes it playable. Events fire faster than anyone
reads — one four-row clear plus two drops is five in a second — so: one request in flight
at a time, events folded into a single pending slot where the highest priority wins, a
five-second floor between displayed lines, and replies older than eight seconds thrown
away. A stale taunt reads as the rival being confused, which is worse than silence.

**Difficulty director** (`LlmLevelDirector`, between levels). Reads what you actually did
— rows per piece, drop accuracy, top-outs, how much clock was left — and sets the next
level's gravity, quota size and item mix, instead of the fixed numbers in
`ItemCatalog.LEVELS`. It runs while you read the level-complete card, so its latency is
free; if the answer has not landed when you click Next Level, the authored level is used
rather than waiting.

Every value it returns is **clamped in Java**. Guided decoding constrains the reply's
shape, not its judgement: a 40ms gravity is schema-valid and unplayable. Mode, clock,
level number and required rows are never asked for at all — those are the level design,
and a model rewriting them could turn level 2 into something other than a Tetris tutorial.

### Watching it think

Every model utterance prints to stdout, so `javafx:run` shows what it is doing:

```
[llm] probing qwen3.6-35b @ http://172.23.35.112:8001
[llm] online: qwen3.6-35b @ http://172.23.35.112:8001
[commentary] WRONG_DROP - they put Milk in the Freezer - wrong zone  (score=35 lives=3 time=142s unsorted=5 rows=2)
LLM thinking: "Milk in the freezer. Bold. Wrong, but bold."
[commentary] NEW_HOLES - that placement buried 2 cell(s) they can no longer reach  (...)
LLM thinking: "You just buried two cells. I would apologise to them."
[llm] commentary dropped as stale after 8400ms - "Too slow again."
[director] level 3 stats: mode=TETRIS_ONLY pieces=30 rows=4 clear=0.13 accuracy=n/a topouts=1 margin=0.00 (0s of 60s left)
LLM thinking: "They topped out once and cleared slowly, so ease the gravity and keep the quota small."
[director] level 3 <- gravity=620ms items=5 emphasis=[DAIRY, PRODUCE]
```

`LLM thinking:` is reserved for the model's own words, verbatim. Anything the game decided
— throttle drops, clamps, connection state — goes through `[llm]`, `[commentary]` or
`[director]`, so the two are never confused.

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

To confirm the offline path, point it at a dead port. All four levels must still play
through, with no commentary card and `[llm] offline (endpoint unreachable)` on the console:

```bash
LLM_BASE_URL=http://127.0.0.1:9 ./mvnw javafx:run
```

## The rival's voice

The commentary captions are also spoken aloud, by [Piper](https://github.com/OHF-Voice/piper1-gpl)
— a neural engine that runs on this machine, for free, with no account and no per-minute
meter. That combination is the requirement: the Windows SAPI stack this replaced was free
but sounded like a robot, and the hosted services that sound better bill by the minute,
which does not survive a development loop that speaks a line every few seconds.

One-time setup. Without it the game is unchanged and every caption still appears as text,
with one console line explaining why nothing is being said:

```bash
python -m pip install "piper-tts[http]"
python -m piper.download_voices en_US-lessac-medium --data-dir "$HOME/.piper-voices"
```

Run `download_voices` with no voice name to list every available voice.

Nothing else is required. Every `tts.*` setting below is optional, and **leaving one
blank is what selects its default** — in particular `tts.spawn=false` does not mean
"no thanks", it means the game will never start the speech server and will only
connect to one you are running yourself.

**A server, not a command.** Piper's CLI reloads the voice model on every invocation; its
HTTP server loads once and keeps it resident. Measured here: ~1.9s for the first synthesis
including warm-up, then **~160ms** per line. The game starts that server itself, waits for
the model to load, and destroys it on exit — so playing is still one command.

| Env var | Property | Default |
|---|---|---|
| `TTS_ENABLED` | `tts.enabled` | on *(only a literal `false` disables)* |
| `TTS_VOICE` | `tts.voice` | `en_US-lessac-medium` |
| `TTS_DATA_DIR` | `tts.dataDir` | `~/.piper-voices` |
| `TTS_PYTHON` | `tts.python` | `python` |
| `TTS_SPAWN` | `tts.spawn` | on *(`false` to run the server yourself)* |
| `TTS_BASE_URL` | `tts.baseUrl` | `http://127.0.0.1:5000` |
| `TTS_LENGTH_SCALE` | `tts.lengthScale` | *(Piper's own)* |

Two defaults are deliberate rather than arbitrary. The server binds **loopback only**,
because it is an unauthenticated synthesis endpoint and putting one on the network buys
nothing. And voice models default to `~/.piper-voices` rather than Piper's own default of
the working directory, which would drop a 60MB `.onnx` into this repository.

The voice never gets in the way: it goes quiet on pause, on level end and on game over, a
new caption cuts off the one still being spoken, and a server that fails to start costs one
console line and nothing else.

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

Commentary gets a 3s budget and a 48-token cap; the director gets 6s and 140 tokens,
since it runs behind an overlay where latency costs nothing.

## Difficulty

`ItemCatalog.LEVELS` holds **templates** — mode, clock and required rows are authored and
fixed. Gravity and the quota are starting points the director may retune for levels 2 and
3 from measured performance.

| Level | Mode | Clock | Quota | Gravity | Rows required |
|---|---|---|---|---|---|
| 1 | `SORT_ONLY` | 60s | 4 (given) | — | — |
| 2 | `TETRIS_ONLY` | 60s | — | 550ms | 1 |
| 3 | `COMBINED` | 180s | 6 (earned) | 480ms | — |
| 4 | `MEMORY` | 60s | 6 pairs (12 cards) | — | — |

On level 4 the "quota" is the list of items to pair up, not a list to sort — six items
means twelve cards. The director leaves it alone: it has neither gravity nor a sorting
quota to retune.

Director bounds: gravity 300–800ms, quota 3–10 items. A quota is never drawn from a single
food category, so the fridge stays a sorting problem.

Scoring: a correct drop or a matched pair pays 10 × (1 + streak/5); a wrong drop or a
mismatch costs 5 and a life. Cleared rows pay 20/60/150/400 for 1/2/3/4 at once — steeply
superlinear, because on level 2 there are no groceries to earn, so stacking has to be worth
the risk. Finishing a level early pays 2 points per second left.

**Level 4 is not tuned yet.** Six pairs costs even a player with a perfect memory four to
six mismatches to solve, lives carry over between levels, and there are only three of them.
`GameController.MISMATCH_COSTS_LIFE` and `FREE_MISMATCHES` are the dials; play-test before
trusting the current settings.

## Layout

The game ships as **Distractions Game**, but the code underneath still says `fridgegame` —
`com.fridgegame.*`, `FridgeGameApp`, the `fridge-game` artifact. The rename was to the
product, not the package: renaming the latter would rewrite every file for no behavioural
gain, so only the user-facing name moved.

```
src/main/java/com/fridgegame/
├── Launcher.java        # plain main() — keeps JavaFX out of the JAR manifest
├── FridgeGameApp.java   # Application; owns the Stage, the Scene and the level flow
├── model/               # FoodCategory, StorageZone, GroceryItem, Level, LevelMode,
│                        # GameState, Cell, Tetromino, TetrisBoard, TetrisMove,
│                        # StepResult, BoardMetrics, MemoryBoard
├── data/                # ItemCatalog (items + level templates), HighScoreStore
├── director/            # LevelDirector (interface), FixedLevelDirector, LevelStats
├── audio/               # Sfx (sound effects), RivalVoice (interface), PiperVoice
├── llm/                 # LlmConfig, TtsConfig, LlmClient, JsonChat, LlmLog,
│                        # LlmCommentator, CommentaryEvent, LlmLevelDirector
├── view/                # GroceryNode, ZoneNode, FridgeView, CounterView, HudView,
│                        # TetrisBoardView, TetrisPanel, CommentaryView,
│                        # LevelCompleteView, GameOverView, ViewTransitions,
│                        # MemoryCardNode, MemoryBoardView
└── controller/          # GameController, DragHandler, TetrisController,
                         # CommentaryController, MemoryController

src/main/resources/com/fridgegame/
├── styles.css
├── sound/               # short effects: piece lock, sort, fail, applause
└── images/food_pixel/   # pixel-art icon per grocery item (rendered via ImageView)
src/test/java/com/fridgegame/
```

The level's `LevelMode` decides what exists on screen: a sorting level builds no board and
never starts gravity, a Tetris level builds no counter or fridge at all, a memory level has
neither — just its grid of cards — and `HudView` hides the stats that level cannot change:
"Rows: 0" on a boardless level reads as a goal you are failing rather than one that does
not exist.

`hasTetris()` and `hasSorting()` are spelled as positive lists rather than as `!= SORT_ONLY`
and `!= TETRIS_ONLY`. A negation would hand every mode added later both mechanics by
default, silently — `MEMORY` would have booted with a Tetris board beside its cards.

`CommentaryView` floats in a `StackPane` over the game so it takes no space from the
board or the fridge. It is `mouseTransparent`, which is load-bearing: it overlaps the
Freezer zone, and a card that intercepted events would silently swallow drops in the
bottom-right corner.

`model/` has no JavaFX imports at all — the entire Tetris engine and every game rule is
plain Java, which is why the suite runs headless with no display and no network.
`TetrisBoardView` draws onto a `Canvas` rather than building 200 `Region` nodes, so the
scene graph is not doing layout passes 20 times a second while you drag groceries.

## Releasing

`git tag v1.0.0 && git push origin v1.0.0` is the whole release process.
[`.github/workflows/release.yml`](.github/workflows/release.yml) then runs the suite,
builds a self-contained app image on `windows-latest` and `ubuntu-latest`, and attaches
both archives to a GitHub Release named after the tag. Running it from the Actions tab
instead (`workflow_dispatch`) builds and uploads the same archives as run artifacts
without publishing anything, which is how to test a packaging change.

Two constraints are why the build looks the way it does. The JavaFX artifacts carry a
platform classifier, so an image can only be built on the OS it targets — hence a matrix
rather than one job. And `jpackage --app-version` rejects `1.0-SNAPSHOT`, so the version
comes from the tag (`v1.0.0` → `1.0.0`) and never from the pom.

`--add-modules` is spelled out rather than left to `jdeps`, because three of the modules
this game needs cannot be inferred from a classpath scan: `java.net.http` (`LlmClient`),
`java.prefs` (`HighScoreStore`) and `jdk.unsupported` (JavaFX's use of `sun.misc.Unsafe`).
A runtime missing any of them builds fine and fails at launch.

No icon is bundled yet — `jpackage` wants a `.ico`/`.icns` and this repository has only
PNGs, so the image ships with the stock Java icon.

## Progress

- [x] **Phase 0 — Setup:** pom, wrapper, `Launcher`, blank window
- [x] **Phase 1 — Model:** enums, `GroceryItem`, `Level`, `GameState`, `ItemCatalog` (15 items)
- [x] **Phase 2 — Static layout:** `FridgeView`, `ZoneNode`, `CounterView`, `GroceryNode`, `HudView`
- [x] **Phase 3 — Drag & drop:** `DragHandler` wires the Dragboard API onto counter items and fridge zones
- [x] **Phase 4 — Rules & scoring:** `GameController` scores correct/wrong drops, updates streak/lives
- [x] **Phase 5 — Timer & levels:** countdown `Timeline`, level-complete banner, game over + restart
- [x] **Phase 6 — Polish:** drop feedback animations, `HighScoreStore` via `Preferences`,
      pixel-art icons, gradient overlay screens with a shared fade+scale entrance

### Tetris

- [x] **T1 — Engine:** `Tetromino` (7 pieces × 4 normalised rotations), `TetrisBoard`
      (10×20, 7-bag randomiser, line clears, top-out) — pure Java
- [x] **T2 — Player board:** `TetrisBoardView` on a `Canvas` with a landing ghost,
      `TetrisController` gravity `Timeline`, keys bound as a Scene event *filter* so the
      counter's `ScrollPane` cannot swallow the arrows
- [x] **T3 — Rows buy groceries:** counter starts empty; `awardClearedRows` releases one
      item per row and pays Tetris-style points. Gravity pauses during a drag gesture

### Level structure and the LLM's real job

- [x] **L1 — One mechanic per level:** `LevelMode` splits sorting practice, Tetris practice
      and the combined round; the clock expiring is a pass on level 2 and a fail elsewhere
- [x] **L2 — Rival commentator:** `LlmCommentator` behind the existing `JsonChat` seam,
      guided-decoding JSON, `CommentaryController` throttle (one in flight, priority
      folding, five-second floor, stale replies dropped)
- [x] **L3 — Difficulty director:** `LlmLevelDirector` sets the next level's gravity, quota
      and item mix from measured `LevelStats`; every field clamped, `FixedLevelDirector`
      as the offline fallback
- [x] **L4 — Observability:** `LlmLog` prints `LLM thinking: "…"` for the model's own words
      and `[llm]`/`[commentary]`/`[director]` for everything the game decided
- [x] **L5 — Opponent removed:** the second board, `ai/` package, `AgentController`,
      `LlmTetrisAgent` and the garbage exchange are gone; the stack-shape maths from the
      heuristic bot survives as `BoardMetrics`, which is how the commentator notices a
      placement that buried cells

## Pinned versions

| Thing | Version | Note |
|---|---|---|
| JavaFX | 21.0.12 | latest 21 LTS patch (plan said 21.0.4) |
| javafx-maven-plugin | 0.0.8 | confirmed still the newest release |
| maven-jar-plugin | 3.4.2 | manifest: `Launcher` + `libs/` classpath |
| maven-dependency-plugin | 3.8.1 | fills `target/libs/` for `jpackage` |
| JUnit Jupiter | 5.14.4 | |
| Jackson Databind | 2.22.2 | only for the LLM request/response JSON |
| Maven (wrapper) | 3.9.16 | |
