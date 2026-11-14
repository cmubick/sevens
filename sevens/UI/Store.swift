//
//  Store.swift
//  sevens
//
//  In-app purchases: a one-time unlock of the full game, and optional tips.
//  Apple handles the payment; the app only learns whether the unlock is owned.
//

import Foundation
import StoreKit

final class Store {

    /// Product identifiers. These must match the products set up in App Store Connect.
    static let fullGameID = "sevens.fullgame"
    static let tipIDs = ["sevens.tip.small", "sevens.tip.medium", "sevens.tip.large"]

    enum Outcome: Equatable {
        case success
        /// Waiting on something outside the app, such as Ask to Buy approval.
        case pending
        case cancelled
        case failed
    }

    private static let unlockedKey = "fullGameUnlocked"

    private let defaults: UserDefaults
    private var updates: Task<Void, Never>?

    /// The one-time unlock, once the App Store has answered. Nil if the store is unavailable.
    private(set) var fullGame: Product?
    /// Tip products, cheapest first.
    private(set) var tips: [Product] = []
    /// True once the product list has been requested, whether or not anything came back.
    private(set) var hasLoaded = false
    /// Whether the App Store says the full game is owned. Cached so the menu is
    /// right before the store answers.
    private var owned: Bool

    var isUnlocked: Bool {
        #if DEBUG
        if let debugOverride { return debugOverride }
        #endif
        return owned
    }

    #if DEBUG
    /// For screenshots and manual testing: pretend the full game is or is not owned.
    var debugOverride: Bool?
    #endif
    /// Called whenever products or the unlock change, so the screen can redraw.
    var onChange: (() -> Void)?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        owned = defaults.bool(forKey: Store.unlockedKey)
    }

    deinit {
        updates?.cancel()
    }

    /// Starts watching for purchases made elsewhere, then loads products and ownership.
    func start() {
        updates = Task { [weak self] in
            for await update in Transaction.updates {
                await self?.handle(update)
            }
        }
        Task { await refresh() }
    }

    func refresh() async {
        if let products = try? await Product.products(for: [Store.fullGameID] + Store.tipIDs) {
            fullGame = products.first { $0.id == Store.fullGameID }
            tips = products.filter { Store.tipIDs.contains($0.id) }.sorted { $0.price < $1.price }
        }
        hasLoaded = true
        await refreshOwnership()
        onChange?()
    }

    /// Reads what the App Store says is owned. This is the source of truth for the unlock.
    private func refreshOwnership() async {
        var owned = false
        for await entitlement in Transaction.currentEntitlements {
            if case .verified(let transaction) = entitlement,
               transaction.productID == Store.fullGameID,
               transaction.revocationDate == nil {
                owned = true
            }
        }
        setUnlocked(owned)
    }

    func buy(_ product: Product) async -> Outcome {
        do {
            switch try await product.purchase() {
            case .success(let verification):
                guard case .verified(let transaction) = verification else { return .failed }
                if transaction.productID == Store.fullGameID {
                    setUnlocked(true)
                }
                await transaction.finish()
                onChange?()
                return .success
            case .pending:
                return .pending
            case .userCancelled:
                return .cancelled
            @unknown default:
                return .failed
            }
        } catch {
            return .failed
        }
    }

    /// Asks the App Store to re-send past purchases, for a new or restored device.
    func restore() async -> Bool {
        try? await AppStore.sync()
        await refreshOwnership()
        onChange?()
        return isUnlocked
    }

    private func handle(_ update: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = update else { return }
        if transaction.productID == Store.fullGameID {
            setUnlocked(transaction.revocationDate == nil)
        }
        await transaction.finish()
        onChange?()
    }

    private func setUnlocked(_ value: Bool) {
        owned = value
        defaults.set(value, forKey: Store.unlockedKey)
    }
}
