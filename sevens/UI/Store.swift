//
//  Store.swift
//  sevens
//
//  In-app purchases: a one-time unlock of the full game, and optional tips.
//  Apple handles the payment; the app only learns whether the unlock is owned.
//

import Foundation
import StoreKit
import UIKit

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
    /// Detail of the most recent failure, for the diagnostic line on the store screen.
    private(set) var lastProblem: String?
    private var lastOwnershipCheck = "not checked"

    /// True for TestFlight and development builds, which use Apple's sandbox store.
    static var isSandbox: Bool {
        #if DEBUG
        return true
        #else
        return Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        #endif
    }

    /// One line describing what the store currently knows. Shown only in sandbox builds.
    var diagnostics: String {
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        var parts = [
            "build \(build)",
            "products \((fullGame == nil ? 0 : 1) + tips.count)/4",
            "full game \(owned ? "owned" : "not owned")",
            lastOwnershipCheck,
        ]
        if let lastProblem { parts.append(lastProblem) }
        return parts.joined(separator: " · ")
    }

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
        var seen = 0
        for await entitlement in Transaction.currentEntitlements {
            seen += 1
            if owns(entitlement) { owned = true }
        }
        // Belt and braces: also ask for the latest transaction for the unlock itself.
        if !owned, let latest = await Transaction.latest(for: Store.fullGameID), owns(latest) {
            owned = true
        }
        lastOwnershipCheck = "entitlements \(seen)"
        setUnlocked(owned)
    }

    /// Whether this transaction is a live purchase of the full game.
    private func owns(_ result: VerificationResult<Transaction>) -> Bool {
        switch result {
        case .verified(let transaction):
            return transaction.productID == Store.fullGameID && transaction.revocationDate == nil
        case .unverified(let transaction, let error):
            if transaction.productID == Store.fullGameID {
                lastProblem = "unverified: \(error)"
            }
            return false
        }
    }

    /// Buys a product. Pass the window's scene so the App Store knows where to
    /// show its payment sheet; without it the sheet can fail to appear.
    func buy(_ product: Product, in scene: UIScene? = nil) async -> Outcome {
        lastProblem = nil
        do {
            let result: Product.PurchaseResult
            if #available(iOS 17.0, *), let scene {
                result = try await product.purchase(confirmIn: scene)
            } else {
                result = try await product.purchase()
            }
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    if transaction.productID == Store.fullGameID {
                        setUnlocked(true)
                    }
                    await transaction.finish()
                    onChange?()
                    return .success
                case .unverified(_, let error):
                    lastProblem = "unverified: \(error)"
                    onChange?()
                    return .failed
                }
            case .pending:
                lastProblem = "pending"
                return .pending
            case .userCancelled:
                lastProblem = "cancelled"
                return .cancelled
            @unknown default:
                lastProblem = "unknown purchase result"
                return .failed
            }
        } catch {
            lastProblem = "error: \(error)"
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

    /// Shows Apple's sheet for typing in an offer code. A redeemed code arrives as
    /// an ordinary purchase, so the unlock follows from the usual ownership check.
    func redeemCode(in scene: UIWindowScene) async -> Bool {
        lastProblem = nil
        do {
            try await AppStore.presentOfferCodeRedeemSheet(in: scene)
        } catch {
            lastProblem = "redeem error: \(error)"
            onChange?()
            return false
        }
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
