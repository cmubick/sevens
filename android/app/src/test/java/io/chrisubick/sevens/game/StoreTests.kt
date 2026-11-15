package io.chrisubick.sevens.game

import io.chrisubick.sevens.haptics.Haptic
import io.chrisubick.sevens.rules.GameLength
import io.chrisubick.sevens.rules.MatchSettings
import io.chrisubick.sevens.store.PurchaseOutcome
import io.chrisubick.sevens.store.Store
import io.chrisubick.sevens.store.StoreProduct
import io.chrisubick.sevens.store.StoreState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A pretend store that sells the unlock and three tips, and does what the test tells it. */
private class FakeStore(owned: Boolean = false) : Store {
    val fullGame = StoreProduct(Store.fullGameID, "Full Game", "$2.99")
    val tips = listOf(
        StoreProduct(Store.tipIDs[0], "Small tip", "$0.99"),
        StoreProduct(Store.tipIDs[1], "Medium tip", "$2.99"),
        StoreProduct(Store.tipIDs[2], "Large tip", "$4.99"),
    )

    /** What the next purchase does. */
    var next = PurchaseOutcome.Success

    /** A purchase made elsewhere, which only a restore finds. */
    var ownedElsewhere = false
    var started = false
    val bought = mutableListOf<String>()

    override var state = StoreState(isUnlocked = owned)
    override var onChange: () -> Unit = {}

    override fun start() {
        started = true
    }

    /** The store answers with its products, as Google Play does a moment after launch. */
    fun load() {
        state = state.copy(fullGame = fullGame, tips = tips, hasLoaded = true)
        onChange()
    }

    fun setOwned(owned: Boolean) {
        state = state.copy(isUnlocked = owned)
        onChange()
    }

    override fun refresh() {}

    override fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit) {
        if (next == PurchaseOutcome.Success) {
            bought += product.id
            if (product.id == Store.fullGameID) setOwned(true)
        }
        done(next)
    }

    /** False while the pretend store cannot be reached. */
    var reachable = true

    override fun restore(done: (Boolean?) -> Unit) {
        if (!reachable) return done(null)
        if (ownedElsewhere) setOwned(true)
        done(state.isUnlocked)
    }

    override fun redeemCode(host: Any?) = true
}

/** The locks and the store screen. Ported in spirit from StoreTests in sevensTests.swift. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoreTests {

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun withoutTheFullGameTheExtrasOpenTheStore() {
        val store = FakeStore()
        val game = GameViewModel(store = store)
        assertTrue(store.started)
        assertFalse(game.isUnlocked)
        assertTrue(game.isLocked(GameLength.Long) && game.isLocked(GameLength.Epic))
        assertFalse(game.isLocked(GameLength.Short) || game.isLocked(GameLength.Standard))
        assertTrue(game.isLocked(1) && game.isLocked(2))
        assertFalse(game.isLocked(0))

        for (locked in listOf<() -> Unit>({ game.chooseLength(GameLength.Epic) }, { game.chooseJokers(2) }, game::openHistory)) {
            locked()
            assertEquals(Overlay.Store, game.overlay)
            assertTrue(game.goBack())
            assertEquals(Overlay.Menu, game.overlay)
        }
        assertEquals(MatchSettings(), game.settings)

        // The free choices still work.
        game.chooseLength(GameLength.Short)
        game.startMatch()
        assertEquals(MatchSettings(length = GameLength.Short), game.match.settings)
    }

    @Test fun theStoreShowsItsProductsOnceTheyLoad() {
        val store = FakeStore()
        val game = GameViewModel(store = store)
        assertNull(game.shop.fullGame)
        assertFalse(game.shop.hasLoaded)
        store.load()
        assertEquals(Store.fullGameID, game.shop.fullGame?.id)
        assertEquals(Store.tipIDs, game.shop.tips.map { it.id })
    }

    @Test fun buyingTheFullGameUnlocksEverything() {
        val felt = mutableListOf<Haptic>()
        val store = FakeStore()
        val game = GameViewModel(haptics = { felt += it }, store = store)
        store.load()
        game.openStore()
        game.purchase(assertNotNull(game.shop.fullGame), host = null)
        assertTrue(game.isUnlocked)
        assertEquals("Unlocked. Enjoy the full game!", game.storeMessage)
        assertEquals(listOf(Haptic.Win), felt)

        game.closeToMenu()
        game.chooseLength(GameLength.Epic)
        game.chooseJokers(2)
        assertEquals(MatchSettings(length = GameLength.Epic, jokers = 2), game.settings)
        game.openHistory()
        assertEquals(Overlay.History, game.overlay)
    }

    @Test fun tippingDoesNotUnlockAnything() {
        val store = FakeStore()
        val game = GameViewModel(store = store)
        store.load()
        game.openStore()
        game.purchase(game.shop.tips.first(), host = null)
        assertEquals("Thank you! Your dealer appreciates it.", game.storeMessage)
        assertFalse(game.isUnlocked)
        // A tip can be given again.
        game.purchase(game.shop.tips.first(), host = null)
        assertEquals(2, store.bought.size)
    }

    @Test fun aPurchaseThatDoesNotFinishSaysWhy() {
        val store = FakeStore()
        val game = GameViewModel(store = store)
        store.load()
        game.openStore()
        val fullGame = assertNotNull(game.shop.fullGame)
        for ((outcome, message) in listOf(
            PurchaseOutcome.Cancelled to "Not purchased. Tap again to try.",
            PurchaseOutcome.Pending to "Waiting for approval.",
            PurchaseOutcome.Failed to "The purchase didn't go through.",
        )) {
            store.next = outcome
            game.purchase(fullGame, host = null)
            assertEquals(message, game.storeMessage)
            assertFalse(game.isUnlocked)
        }
    }

    @Test fun restoreFindsAnEarlierPurchase() {
        val store = FakeStore()
        val game = GameViewModel(store = store)
        game.openStore()
        store.reachable = false
        game.restorePurchases()
        assertEquals("Google Play isn't available right now.", game.storeMessage)
        store.reachable = true
        game.restorePurchases()
        assertEquals("No earlier purchase found.", game.storeMessage)
        store.ownedElsewhere = true
        game.restorePurchases()
        assertEquals("Full game restored.", game.storeMessage)
        assertTrue(game.isUnlocked)
    }

    @Test fun losingTheFullGamePutsTheMenuBackToFreeChoicesButLeavesAGameAlone() {
        val store = FakeStore(owned = true)
        val game = GameViewModel(store = store)
        game.chooseLength(GameLength.Long)
        game.chooseJokers(1)
        assertEquals(MatchSettings(length = GameLength.Long, jokers = 1), game.settings)

        // A refund arrives while the menu is up.
        store.setOwned(false)
        assertEquals(MatchSettings(), game.settings)

        // A refund arriving mid-game does not change the game being played.
        store.setOwned(true)
        game.chooseJokers(2)
        game.startMatch()
        store.setOwned(false)
        assertEquals(2, game.settings.jokers)
        assertEquals(54, assertNotNull(game.round).hands.sumOf { it.size })
    }

    @Test fun fiveTapsOnTheTitleShowTheSupportLine() {
        val game = GameViewModel(store = FakeStore())
        game.openStore()
        repeat(4) { game.storeTitleTapped() }
        assertFalse(game.showsStoreDiagnostics)
        game.storeTitleTapped()
        assertTrue(game.showsStoreDiagnostics)
    }
}
