//
//  Store.kt
//
//  In-app purchases: a one-time unlock of the full game, and optional tips.
//  This file is the part the game talks to and has no Android in it, so the
//  tests can stand a pretend store in its place. PlayStore.kt is the real one.
//  Ported from sevens/UI/Store.swift.
//

package io.chrisubick.sevens.store

/** Something that can be bought, as Google Play describes it. `price` is already formatted, such as "$2.99". */
data class StoreProduct(val id: String, val name: String, val price: String)

enum class PurchaseOutcome {
    Success,

    /** Waiting on something outside the app, such as a parent's approval or a cash payment. */
    Pending,
    Cancelled,
    Failed,
}

/** Everything the screen needs to know about the store at one moment. */
data class StoreState(
    /** The one-time unlock, once the store has answered. Null if the store is unavailable. */
    val fullGame: StoreProduct? = null,
    /** Tip products, cheapest first. */
    val tips: List<StoreProduct> = emptyList(),
    /** True once the product list has been requested, whether or not anything came back. */
    val hasLoaded: Boolean = false,
    val isUnlocked: Boolean = false,
    /** One line describing what the store currently knows, for the hidden support line on the store screen. */
    val diagnostics: String = "",
)

interface Store {
    val state: StoreState

    /** Called whenever products or the unlock change, so the screen can redraw. */
    var onChange: () -> Unit

    /** Connects to the store, then loads products and what is owned. */
    fun start()

    /** Asks the store again what is owned: a purchase can be made or refunded while the app is in the background. */
    fun refresh()

    /**
     * Buys a product. `host` is whatever the store needs to show its payment
     * sheet on top of; on Android that is the Activity. `done` is called once.
     */
    fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit)

    /**
     * Re-reads past purchases, for a new or restored phone. `done` is told whether
     * the full game is owned, or null if the store could not be asked.
     */
    fun restore(done: (Boolean?) -> Unit)

    /** Opens the store's page for typing in a promo code. False if it could not be opened. */
    fun redeemCode(host: Any?): Boolean

    companion object {
        /** Product identifiers. These must match the products set up in the Google Play Console. */
        const val fullGameID = "sevens.fullgame"
        val tipIDs = listOf("sevens.tip.small", "sevens.tip.medium", "sevens.tip.large")
    }
}

/** A store with nothing for sale, where the full game is either owned or not. Used by tests. */
class NoStore(unlocked: Boolean = true) : Store {
    override val state = StoreState(hasLoaded = true, isUnlocked = unlocked)
    override var onChange: () -> Unit = {}
    override fun start() {}
    override fun refresh() {}
    override fun buy(product: StoreProduct, host: Any?, done: (PurchaseOutcome) -> Unit) = done(PurchaseOutcome.Failed)
    override fun restore(done: (Boolean?) -> Unit) = done(state.isUnlocked)
    override fun redeemCode(host: Any?) = false
}
