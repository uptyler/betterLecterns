# BetterLectern

BetterLectern remembers the last-read page of each written or writable book placed on a lectern. Dispensers can also place books on lecterns.

> **Transparency:** This mod was entirely vibe-coded and created for demonstration purposes. It is not intended to be maintained.

## Demonstration

[Watch the YouTube demonstration of a 15-option randomizer](https://youtu.be/S1mxZqn6LTo).

This mod was created as a demonstration of [this feature request](https://feedback.minecraft.net/hc/en-us/community/posts/50195910233741-Book-Lectern-Memory).

## How it works

When a written or writable book is placed on a lectern, the mod checks its `CUSTOM_DATA` component for a valid UUID under `BetterLecternsBookId`. If the UUID is missing or invalid, the mod assigns a new one.

The lectern uses the UUID to look up that book's saved page in its block-entity data and restores the page when a saved entry exists. It records the current page when the page changes or the book is removed, so the page can be restored the next time that book is placed.

## Build

Run `./gradlew build`. The mod jar is created in `build/libs/`.

## License

CC0-1.0

