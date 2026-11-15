//
//  SavedGame.kt
//
//  A game in progress, saved so it survives the app being closed.
//  Ported from sevens/Rules/SavedGame.swift.
//

package io.chrisubick.sevens.rules

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedGame(
    val match: Match,
    /** The round being played, or null between rounds: the next round has not been dealt yet. */
    val round: Round?,
    /** Rounds the player has gone out first in so far. */
    val roundsWon: Int,
)

/**
 * Keeps the saved game as a small JSON file. The app chooses where: on Android
 * that is a file called [fileName] in the app's private files folder.
 */
object GameSaver {
    const val fileName = "savedGame.json"

    fun load(from: File): SavedGame? =
        try {
            SevensJson.decodeFromString(SavedGame.serializer(), from.readText())
        } catch (error: Exception) {
            null
        }

    fun save(game: SavedGame, to: File) {
        try {
            to.writeTextAtomically(SevensJson.encodeToString(SavedGame.serializer(), game))
        } catch (error: Exception) {
            // A failed save only means the game can't be resumed later.
        }
    }

    fun clear(at: File) {
        at.delete()
    }
}

/**
 * The JSON format for everything the game saves. Map keys such as a card's
 * face are whole objects, and values added in later versions are ignored by
 * older ones. The files are not interchangeable with the iOS app's.
 */
val SevensJson = Json {
    allowStructuredMapKeys = true
    ignoreUnknownKeys = true
}

/** Writes to a temporary file and then swaps it in, so a crash never leaves half a file. */
internal fun File.writeTextAtomically(text: String) {
    val folder = absoluteFile.parentFile
    folder.mkdirs()
    val temporary = File.createTempFile(name, ".tmp", folder)
    try {
        temporary.writeText(text)
        Files.move(
            temporary.toPath(),
            absoluteFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } finally {
        temporary.delete()
    }
}
