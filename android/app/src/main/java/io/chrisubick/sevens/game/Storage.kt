//
//  Storage.kt
//
//  What the app keeps between launches: the game in progress, the history of
//  finished games, whether the instructions have been seen, and whether sound
//  is off. Everything is a small file in the app's private files folder,
//  which Android removes when the app is uninstalled.
//

package io.chrisubick.sevens.game

import io.chrisubick.sevens.rules.GameHistory
import io.chrisubick.sevens.rules.GameSaver
import io.chrisubick.sevens.rules.HistoryStore
import io.chrisubick.sevens.rules.SavedGame
import java.io.File

/**
 * With no folder nothing is kept and the first-launch instructions are
 * skipped. The tests use that, and so does a game started by a test switch.
 */
class Storage(private val folder: File? = null) {

    private fun file(name: String): File? = folder?.let { File(it, name) }

    fun loadHistory(): GameHistory = file(HistoryStore.fileName)?.let(HistoryStore::load) ?: GameHistory()

    fun save(history: GameHistory) {
        file(HistoryStore.fileName)?.let { HistoryStore.save(history, it) }
    }

    fun loadGame(): SavedGame? = file(GameSaver.fileName)?.let(GameSaver::load)

    fun save(game: SavedGame) {
        file(GameSaver.fileName)?.let { GameSaver.save(game, it) }
    }

    fun clearGame() {
        file(GameSaver.fileName)?.let(GameSaver::clear)
    }

    /** The app opens on the instructions until they have been closed once. An empty file marks that. */
    var seenHowToPlay: Boolean
        get() = file(seenFileName)?.exists() ?: true
        set(seen) = mark(seenFileName, seen)

    /** Sound is on unless the player has turned it off. An empty file marks that too. */
    var soundMuted: Boolean
        get() = file(mutedFileName)?.exists() ?: false
        set(muted) = mark(mutedFileName, muted)

    /** What the store last said about owning the full game, so the menu is right before the store answers. */
    var fullGameUnlocked: Boolean
        get() = file(unlockedFileName)?.exists() ?: false
        set(unlocked) = mark(unlockedFileName, unlocked)

    /** A yes-or-no setting is kept as an empty file that is either there or not. */
    private fun mark(name: String, on: Boolean) {
        val marker = file(name) ?: return
        try {
            if (on) {
                marker.parentFile?.mkdirs()
                marker.createNewFile()
            } else {
                marker.delete()
            }
        } catch (error: Exception) {
            // A setting not sticking is not worth a crash.
        }
    }

    private companion object {
        const val seenFileName = "hasSeenHowToPlay"
        const val mutedFileName = "soundMuted"
        const val unlockedFileName = "fullGameUnlocked"
    }
}
