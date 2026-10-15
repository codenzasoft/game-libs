# game-libs

Java libraries for a Hearts card game engine, published to Maven Central under `com.codenzasoft.games`.

## Modules

### game-common

Core abstractions shared across games: `Game`, `Player`, `Card`, `Deck`, `Suit`, `Rank`, and `GameLifecycleObserver`.

```groovy
implementation 'com.codenzasoft.games:game-common:1.0.0'
```

### game-hearts

Hearts card game engine — rules, game state, and bot strategies.

```groovy
implementation 'com.codenzasoft.games:game-hearts:1.0.0'
```

**Key classes:**
- `HeartsGame` — game lifecycle (start, pass cards, play card, quit)
- `HeartsState` / `RoundState` / `TrickState` — immutable game state
- `HeartsPlayerObserver` — implement to drive a bot or human player
- `DefensiveStrategy` — rule-based bot (avoids points, dumps dangerous cards)
- `AdaptiveStrategy` — extends `DefensiveStrategy`; attempts moon shots when viable

## Building locally

```bash
./gradlew build
./gradlew test
```

## Releasing

Tag a commit with `vX.Y.Z` to trigger the release workflow, which signs and publishes both modules to Maven Central automatically.

```bash
git tag v1.0.1
git push origin v1.0.1
```

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).
