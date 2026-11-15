# Sevens for Android

The Android port of the iOS game in `../sevens`. Kotlin, Gradle (Kotlin DSL), Jetpack Compose.

## Layout

- `rules/` is the game itself: cards, the table, a round, a match, the bots, history and saved games, plus the scripted practice round. It is plain Kotlin with no Android in it, ported file for file from `../sevens/Rules` (and `../sevens/UI/Practice.swift`). Its tests are in `rules/src/test`.
- `app/` is the Android app: the start menu, the game table, how to play, the practice round, the history screen and resuming a saved game. In `game/`, `TableLogic.kt` works out where every card goes, `GameViewModel.kt` runs the game, the bots' turns and which panel is showing, `TableScreen.kt` draws the table, `Panels.kt` draws the menus and `Storage.kt` reads and writes the saved files. In `store/`, `Store.kt` is what the game knows about purchases and `PlayStore.kt` talks to Google Play. In `sound/`, `SoundSynth.kt` builds the sound effects from arithmetic (the app ships no audio files) and `Sounds.kt` plays them. In `haptics/`, `Haptics.kt` makes the phone tap and buzz.
- `gradle/libs.versions.toml` holds every version number in one place.

## Running it from Android Studio

1. In Android Studio choose **Open** and pick this `android` folder (not the repository root). Wait for the "Gradle sync" at the bottom of the window to finish.
2. Pick the **Medium Phone** emulator in the device list at the top and press the green **Run** arrow. The emulator shows the start menu.

The old "Pixel 3a API 30 x86" device in Device Manager is an Intel image left over from the previous install and can be deleted.

## Command line

Use Android Studio's own Java so the build does not depend on whatever Java the Mac has:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :rules:test        # the rules tests (no Android SDK needed)
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # installs it on a running emulator or plugged-in device
```

Debug builds can skip the menu from the command line:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell am start -n io.chrisubick.sevens/.MainActivity --ei jokers 2 --ei target 50   # two jokers, game to 50
$ADB shell am start -n io.chrisubick.sevens/.MainActivity --ez autoplay true            # bots play your hand too
$ADB shell am start -n io.chrisubick.sevens/.MainActivity --es scenario joker           # a joker can replace a card you hold
$ADB shell am start -n io.chrisubick.sevens/.MainActivity --ez unlocked true            # pretend the full game is owned (false: not owned)
```

Close the app first (`$ADB shell am force-stop io.chrisubick.sevens`) or the game already running carries on.

Games started with these switches are never saved and never go in the history.

## What the app saves

Up to five small files in the app's private files folder, which Android deletes when the app is uninstalled:

- `savedGame.json` is the game in progress, rewritten after every move. While it exists the start menu shows **Resume game** and **New game** instead of **Deal**. It is removed when the game ends or is quit.
- `history.json` is the list of finished and quit games behind the History screen.
- `hasSeenHowToPlay` is an empty file. Until it exists the app opens on the instructions.
- `soundMuted` is an empty file that exists only while sound is turned off.
- `fullGameUnlocked` is an empty file that exists while Google Play last said the full game is owned, so the menu is right before Google Play answers.

To look at them or start again from nothing on a debug build:

```bash
$ADB shell run-as io.chrisubick.sevens ls files
$ADB shell pm clear io.chrisubick.sevens      # wipes everything the app has saved
```

`local.properties` tells Gradle where the SDK is. It is specific to this Mac and is not committed; Android Studio recreates it if it is missing.

## Purchases

As on iOS: Short and Standard games with no jokers are free, and one purchase unlocks jokers, Long and Epic games and the History screen. Tips are optional and unlock nothing. Google takes the payment; the app only asks Google Play what is owned.

Until the products exist in the Google Play Console the store screen says **Store unavailable** and everything that needs the full game is locked. To try the unlocked game meanwhile, use the `--ez unlocked true` switch above. Force-stop the app first; the switch lasts until the app is next closed.

What has to be set up in the Play Console before a real purchase can be tested (this is Google's process and has not been tried with this app yet):

1. Create the app in the Play Console with the package name `io.chrisubick.sevens`.
2. Upload a signed release build to the **Internal testing** track. Google only lets an app sell things once a build that includes the billing library has been uploaded.
3. Under the monetisation section, create four one-time products with exactly these IDs and activate them: `sevens.fullgame`, `sevens.tip.small`, `sevens.tip.medium`, `sevens.tip.large`. The tips' names shown under their prices come from here.
4. Add your Google account as a **licence tester** (Play Console settings), so purchases are free test purchases, and as a tester on the internal track.
5. Run the app on a phone or emulator that has the Play Store and is signed in with that account.

Five quick taps on the store screen's title show a small line with what Google Play told the app, which is the first thing to read if the products do not appear.

Differences from the iOS store: the full game is "acknowledged" and tips are "consumed" as Google requires (see the top of `PlayStore.kt`); **Redeem code** opens the Play Store's own redeem page, because Google has no in-app sheet for it; and purchases are not checked against a server, which is the same level of protection as the iOS app.

## Phones and tablets

As on iOS, a phone stays upright and a tablet also works on its side, where the suits run in rows instead of columns. `MainActivity.kt` decides: Android counts a screen at least 600 wide on its short side as a tablet. Turning a tablet mid-game keeps the game going.

To try the tablet layouts without a tablet emulator, resize the phone emulator and put it back afterwards:

```bash
$ADB shell wm size 2560x1600 && $ADB shell wm density 320    # a tablet on its side
$ADB shell wm size reset && $ADB shell wm density reset
```

## Sound

The eight effects are the same recipes as on iOS. They follow the phone's media volume, and music from another app keeps playing underneath. Unlike an iPhone's silent switch, an Android phone's ringer being on silent does not mute a game; the **Sound on / Sound off** button on the start menu and in the game menu does. The emulator plays through the Mac's speakers.

## Haptics

The same six taps as on iOS: a tick for another player's move, a tap for your own, a thump for things that need your attention, a double knock for a refused move or bad news, and a buzz for going out first (longer for winning the game). They use the vibration motor, which is why `AndroidManifest.xml` asks for the `VIBRATE` permission; Android grants it without asking the player.

There is no switch for them in the game. From Android 13 they follow the phone's own **Touch feedback** setting under Sound & vibration. An emulator has no motor, so the feel can only be judged on a real phone. To see what the game asked for:

```bash
$ADB shell dumpsys vibrator_manager | grep sevens
```

## How the Kotlin differs from the Swift

The names follow the Swift code. The differences are the ones Kotlin forces:

- Swift structs are copied when assigned. The Kotlin classes are unchangeable instead, and a change returns a new value: `round = round.applying(move)`, `table.placing(face)`, `match.recording(round)`, `history.adding(record)`. In Swift these are `apply`, `place`, `record` and `add`.
- Enum cases are capitalised: `Suit.Spades`, `GameLength.Standard`.
- An ordinary card is `Card.Ordinary(face)` (Swift: `.face(face)`); a joker move is `Move.Joker(joker, asFace = face)` because `as` is a reserved word.
- `GameSaver` and `HistoryStore` are given the file to use. `Storage.kt` in the app passes files in its private files folder.
- Saved files are JSON but are not interchangeable with the iOS app's files.

`ParityTests` checks the port against numbers produced by the iOS engine: the same seed gives the same deal, and 120 seeded bot matches play out move for move the same.
