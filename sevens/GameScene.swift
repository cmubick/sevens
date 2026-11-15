//
//  GameScene.swift
//  sevens
//
//  Created by Chris Ubick on 11/12/26.
//
//  The game table. All rules live in the Rules folder; this scene only draws
//  the current Round and turns taps into moves.
//

import SpriteKit
import StoreKit

final class GameScene: SKScene {

    // MARK: - State

    var safeInsets: UIEdgeInsets = .zero {
        didSet { if safeInsets != oldValue { relayout() } }
    }

    /// Player 0 is the person holding the phone. Play runs You → West → North → East.
    private let names = ["You", "West", "North", "East"]

    private enum Overlay {
        case menu
        case help(page: Int)
        case history
        case store
        case summary(points: [Int], winner: Int)
        case jokerWarning(Round.Move, Face)
        case gameMenu
        case quitConfirm
        case newGameConfirm
    }

    private var settings = MatchSettings()
    private var match = Match()
    private var round: Round?
    private var selectedJoker: Card?
    private var eventText = ""
    private var overlay: Overlay? = .menu
    /// Set while the scripted practice round is running.
    private var practice: Practice?
    private var history = GameHistory()
    /// The unfinished game on disk, if there is one. Offered as "Resume game" on the menu.
    private var savedGame: SavedGame?
    /// True while the cards are flying out at the start of a round.
    private var isDealing = false
    private let store = Store()
    /// The line of feedback shown on the store screen after a purchase or restore.
    private var storeMessage = ""
    private var isBuying = false
    /// The store's diagnostic line is hidden until the store title is tapped five times.
    private var storeTitleTaps = 0
    private var showsStoreDiagnostics = false
    /// Rounds the player has gone out first in during the current game.
    private var roundsWon = 0
    /// False for debug scenarios, so test positions never land in the history.
    private var recordsHistory = true
    /// Bumped whenever a game starts so stale timers do nothing.
    private var generation = 0
    private var isSetUp = false

    // MARK: - Nodes

    private var cardNodes: [Card: CardNode] = [:]
    private let slotLayer = SKNode()
    private let targetLayer = SKNode()
    private let hudLayer = SKNode()
    private let coachLayer = SKNode()
    private let dealLayer = SKNode()
    private let overlayLayer = SKNode()
    private var badges: [Int: BadgeNode] = [:]
    private var eventLabel = SKLabelNode()
    private var promptLabel = SKLabelNode()
    private var infoLabel = SKLabelNode()

    // MARK: - Layout

    private struct Metrics {
        let size: CGSize
        let u: CGFloat
        /// Wide layouts (iPad on its side) lay the suits out as rows instead of columns.
        let isLandscape: Bool
        let handScale: CGFloat
        let handBottomRowY: CGFloat
        let handRowStep: CGFloat
        let handPerRow: Int
        let infoY: CGFloat
        let promptY: CGFloat
        let eventY: CGFloat
        let badgeY: CGFloat
        let badgeSize: CGSize
        let tableScale: CGFloat
        let tableMidY: CGFloat
        /// Distance between neighboring ranks in a run.
        let step: CGFloat
        /// Distance between suits: columns when upright, rows when on its side.
        let suitSpacing: CGFloat
        let tableBottom: CGFloat

        init(size: CGSize, insets: UIEdgeInsets) {
            self.size = size
            isLandscape = size.width > size.height
            let u = isLandscape
                ? min(size.width / 844, size.height / 600)
                : min(size.width / 390, size.height / 780)
            self.u = u

            // Upright, the hand is two rows; smaller cards leave room to show most of the top row.
            handScale = isLandscape ? u : 0.92 * u
            handPerRow = isLandscape ? 14 : 7
            let handCardHeight = CardNode.size.height * handScale
            handBottomRowY = insets.bottom + 10 * u + handCardHeight / 2
            handRowStep = handCardHeight * 0.82
            let handTop = insets.bottom + 10 * u + handCardHeight * (isLandscape ? 1.0 : 1.82) + 12 * u
            infoY = handTop + 12 * u
            promptY = handTop + 38 * u
            eventY = handTop + 64 * u
            let tableBottom = handTop + 82 * u
            self.tableBottom = tableBottom

            let badgeTop = size.height - insets.top - 6 * u
            badgeSize = CGSize(width: min((size.width - 48 * u) / 3, 220 * u), height: 48 * u)
            badgeY = badgeTop - badgeSize.height / 2
            let tableTop = badgeTop - badgeSize.height - 10 * u
            tableMidY = (tableTop + tableBottom) / 2

            if isLandscape {
                suitSpacing = (tableTop - tableBottom) / 4
                let tableCardHeight = min(suitSpacing - 8 * u, 120 * u)
                tableScale = tableCardHeight / CardNode.size.height
                let tableCardWidth = CardNode.size.width * tableScale
                step = min((size.width - 40 * u - tableCardWidth) / 12, tableCardWidth * 0.78)
            } else {
                suitSpacing = min((size.width - 24 * u) / 4, 92 * u)
                let tableCardWidth = min(suitSpacing - 10 * u, 62 * u)
                tableScale = tableCardWidth / CardNode.size.width
                let tableCardHeight = CardNode.size.height * tableScale
                step = min((tableTop - tableBottom - tableCardHeight) / 12, tableCardHeight * 0.42)
            }
        }

        var tableCardSize: CGSize {
            CGSize(width: CardNode.size.width * tableScale, height: CardNode.size.height * tableScale)
        }

        func slot(_ face: Face) -> CGPoint {
            let suit = CGFloat(Suit.displayOrder.firstIndex(of: face.suit) ?? 0)
            let offset = CGFloat(face.rank - Face.startRank) * step
            if isLandscape {
                return CGPoint(x: size.width / 2 + offset, y: tableMidY + (1.5 - suit) * suitSpacing)
            }
            return CGPoint(x: size.width / 2 + (suit - 1.5) * suitSpacing, y: tableMidY + offset)
        }

        /// Stacking order on the table, so the corner of every card stays visible.
        func tableZ(_ face: Face) -> CGFloat {
            CGFloat((isLandscape ? face.rank : 20 - face.rank) * 3)
        }

        /// Where to draw the spot for placing `face`. A fresh suit gets a whole card;
        /// the next card in a run gets just the strip that sticks out past the run.
        func target(for face: Face, run: ClosedRange<Int>?) -> (position: CGPoint, size: CGSize) {
            var position = slot(face)
            var size = tableCardSize
            guard let run else { return (position, size) }
            let outward: CGFloat = face.rank > run.upperBound ? 1 : -1
            if isLandscape {
                position.x += outward * (size.width - step) / 2
                size.width = step
            } else {
                position.y += outward * (size.height - step) / 2
                size.height = step
            }
            return (position, size)
        }

        /// The right edge of the table when the suits are laid out as rows.
        var tableRight: CGFloat {
            size.width / 2 + 6 * step + tableCardSize.width / 2
        }

        func badge(_ player: Int) -> CGPoint {
            CGPoint(x: size.width / 2 + CGFloat(player - 2) * (badgeSize.width + 12 * u), y: badgeY)
        }
    }

    private var metrics: Metrics { Metrics(size: size, insets: safeInsets) }

    // MARK: - Scene life cycle

    override func didMove(to view: SKView) {
        backgroundColor = Style.felt
        slotLayer.zPosition = 0
        targetLayer.zPosition = 80
        hudLayer.zPosition = 5
        coachLayer.zPosition = 90
        dealLayer.zPosition = 150
        overlayLayer.zPosition = 1000
        addChild(slotLayer)
        addChild(targetLayer)
        addChild(hudLayer)
        addChild(coachLayer)
        addChild(dealLayer)
        addChild(overlayLayer)
        isSetUp = true
        history = HistoryStore.load()
        savedGame = GameSaver.load()
        _ = Sounds.shared
        #if DEBUG
        if let index = CommandLine.arguments.firstIndex(of: "-unlocked"),
           CommandLine.arguments.indices.contains(index + 1) {
            store.debugOverride = CommandLine.arguments[index + 1] == "YES"
        }
        #endif
        store.onChange = { [weak self] in self?.rebuildOverlay() }
        store.start()
        // First launch opens on the instructions.
        if !UserDefaults.standard.bool(forKey: HelpPage.seenKey) {
            overlay = .help(page: 0)
        }
        relayout()
        #if DEBUG
        let arguments = CommandLine.arguments
        if let index = arguments.firstIndex(of: "-scenario"), arguments.indices.contains(index + 1) {
            startScenario(arguments[index + 1])
        }
        #endif
    }

    #if DEBUG
    /// Jumps straight into a set-up position for testing, e.g. launch with `-scenario joker`.
    /// Scenarios: joker, ask, give, roundEnd, gameEnd.
    private func startScenario(_ name: String) {
        func f(_ suit: Suit, _ rank: Int) -> Card { .face(Face(suit, rank)) }
        recordsHistory = false
        if name == "history" {
            // Sample data for looking at the History screen. Never saved.
            UserDefaults.standard.set(true, forKey: HelpPage.seenKey)
            var generator = SeededGenerator(seed: 16)
            var sample = GameHistory()
            for game in 0..<24 {
                // Scores drift lower over time so the trend reads as improving.
                let mine = Int.random(in: 20...(140 - game * 4), using: &generator)
                let others = (0..<3).map { _ in Int.random(in: 30...130, using: &generator) }
                sample.add(GameRecord(
                    date: Date(), target: 100, jokers: 0,
                    scores: [mine] + others, rounds: Int.random(in: 3...6, using: &generator), roundsWon: 1
                ))
            }
            history = sample
            overlay = .history
            rebuildOverlay()
            return
        }
        if name == "store" {
            UserDefaults.standard.set(true, forKey: HelpPage.seenKey)
            overlay = .store
            rebuildOverlay()
            return
        }
        if name == "help" {
            overlay = .help(page: 2)
            rebuildOverlay()
            return
        }
        if name == "practice" {
            // Jump to the moment North's joker kills the player's 10 of spades.
            UserDefaults.standard.set(true, forKey: HelpPage.seenKey)
            startPractice()
            let stop = 11
            guard var position = round else { return }
            for step in Practice.script.prefix(stop) {
                switch step {
                case .you(let move, _), .bot(let move): try? position.apply(move)
                case .note: break
                }
            }
            generation += 1
            round = position
            practice?.index = stop
            eventText = "North played a joker as 10\(Suit.spades.glyph)"
            advance()
            return
        }
        if name == "showcase" || name == "showcaseJoker" {
            // A believable mid-game table: two bot-played rounds for the scores,
            // then a third round stopped on the player's turn.
            UserDefaults.standard.set(true, forKey: HelpPage.seenKey)
            settings = MatchSettings(length: .long, jokers: 1)
            var game = Match(settings: settings)
            var current = Round(hands: [[], [], [], []])
            for seed in UInt64(1)...200 {
                var generator = SeededGenerator(seed: seed)
                game = Match(settings: settings)
                for _ in 0..<2 {
                    var finished = game.dealRound(using: &generator)
                    while finished.winner == nil, let move = Bot.chooseMove(in: finished) {
                        try? finished.apply(move)
                    }
                    game.record(finished)
                }
                current = game.dealRound(using: &generator)
                var moves = 0
                var found = false
                while current.winner == nil, let move = Bot.chooseMove(in: current) {
                    if moves >= 20, current.actor == 0, current.phase == .play,
                       current.hands[0].count >= 8,
                       current.legalMoves().count >= 2, current.hands[0].contains(where: \.isJoker) {
                        found = true
                        break
                    }
                    try? current.apply(move)
                    moves += 1
                }
                if found { break }
            }
            generation += 1
            match = game
            cardNodes.values.forEach { $0.removeFromParent() }
            cardNodes = [:]
            round = current
            selectedJoker = name == "showcaseJoker" ? current.hands[0].first(where: \.isJoker) : nil
            overlay = nil
            eventText = "East played a card"
            rebuildOverlay()
            sync(animated: false)
            refreshHUD()
            return
        }
        var mine: [Card]
        var jokers = 0
        var turn = 0
        var length = GameLength.epic
        switch name {
        case "joker", "jokerPick":
            jokers = 1
            mine = [.joker(0), f(.spades, 8), f(.spades, 9), f(.diamonds, 13), f(.clubs, 2)]
        case "ask":
            mine = [f(.diamonds, 13), f(.clubs, 1), f(.hearts, 2)]
        case "give":
            mine = [f(.spades, 8), f(.diamonds, 13), f(.clubs, 1)]
            turn = 1
        case "roundEnd":
            mine = [f(.spades, 8)]
        case "gameEnd":
            mine = [f(.spades, 8)]
            length = .short
        default:
            return
        }
        UserDefaults.standard.set(true, forKey: HelpPage.seenKey)

        var table = Table()
        table.place(.sevenOfSpades)
        let rest = Card.deck(jokers: jokers).filter { $0 != .face(.sevenOfSpades) && !mine.contains($0) }
        // West gets nothing playable, so West has to ask on its first turn.
        let playable = rest.filter { $0.face.map(table.canPlace) ?? false }
        let blocked = rest.filter { !playable.contains($0) }
        var hands: [[Card]] = [mine, [], [], []]
        for (index, card) in blocked.enumerated() { hands[1 + index % 3].append(card) }
        for (index, card) in playable.enumerated() { hands[2 + index % 2].append(card) }

        generation += 1
        settings.jokers = jokers
        settings.length = length
        match = Match(settings: settings)
        cardNodes.values.forEach { $0.removeFromParent() }
        cardNodes = [:]
        round = Round(hands: hands, table: table, turn: turn)
        // "jokerPick" opens with the joker already selected, to show the placement spots.
        selectedJoker = name == "jokerPick" ? .joker(0) : nil
        overlay = nil
        eventText = "Test scenario: \(name)"
        rebuildOverlay()
        advance()
    }
    #endif

    override func didChangeSize(_ oldSize: CGSize) {
        relayout()
    }

    private func relayout() {
        guard isSetUp else { return }
        let m = metrics

        // Empty slots that show where each suit starts.
        slotLayer.removeAllChildren()
        for suit in Suit.allCases {
            let slot = SKShapeNode(rectOf: m.tableCardSize, cornerRadius: 7 * m.tableScale)
            slot.position = m.slot(Face(suit, Face.startRank))
            slot.fillColor = SKColor(white: 0, alpha: 0.15)
            slot.strokeColor = SKColor(white: 1, alpha: 0.25)
            slot.lineWidth = 1.5
            let label = Style.label("7" + suit.glyph, size: 18 * m.tableScale, font: Style.bold,
                                    color: SKColor(white: 1, alpha: 0.35))
            slot.addChild(label)
            slotLayer.addChild(slot)
        }

        hudLayer.removeAllChildren()
        badges = [:]
        for player in 1..<names.count {
            let badge = BadgeNode(size: m.badgeSize, unit: m.u)
            badge.position = m.badge(player)
            hudLayer.addChild(badge)
            badges[player] = badge
        }
        eventLabel = Style.label("", size: 14 * m.u, color: SKColor(white: 1, alpha: 0.8))
        eventLabel.position = CGPoint(x: size.width / 2, y: m.eventY)
        promptLabel = Style.label("", size: 18 * m.u, font: Style.bold)
        promptLabel.position = CGPoint(x: size.width / 2, y: m.promptY)
        infoLabel = Style.label("", size: 13 * m.u, color: SKColor(white: 1, alpha: 0.8))
        infoLabel.position = CGPoint(x: size.width / 2, y: m.infoY)
        hudLayer.addChild(eventLabel)
        hudLayer.addChild(promptLabel)
        hudLayer.addChild(infoLabel)

        sync(animated: false)
        refreshHUD()
        rebuildOverlay()
    }

    // MARK: - Game flow

    private func startMatch() {
        generation += 1
        limitSettingsToWhatIsOwned()
        match = Match(settings: settings)
        roundsWon = 0
        recordsHistory = true
        startRound()
    }

    /// Without the full game, only Short and Standard games with no jokers are available.
    private func limitSettingsToWhatIsOwned() {
        guard !store.isUnlocked else { return }
        if isLocked(settings.length) { settings.length = .standard }
        settings.jokers = 0
    }

    private func isLocked(_ length: GameLength) -> Bool {
        !store.isUnlocked && length.targetScore > GameLength.standard.targetScore
    }

    private func openStore() {
        storeMessage = ""
        storeTitleTaps = 0
        overlay = .store
        rebuildOverlay()
    }

    private func purchase(_ product: Product) {
        guard !isBuying else { return }
        isBuying = true
        storeMessage = "Contacting the App Store…"
        rebuildOverlay()
        Task { [weak self] in
            guard let self else { return }
            let outcome = await self.store.buy(product, in: self.view?.window?.windowScene)
            self.isBuying = false
            switch outcome {
            case .success:
                Haptics.victory(game: false)
                self.storeMessage = product.id == Store.fullGameID
                    ? "Unlocked. Enjoy the full game!"
                    : "Thank you! Your dealer appreciates it."
            case .pending:
                self.storeMessage = "Waiting for approval."
            case .cancelled:
                // Signing in to the App Store ends the first attempt, so say what to do next.
                self.storeMessage = "Not purchased. Tap again to try."
            case .failed:
                self.storeMessage = "The purchase didn't go through."
            }
            self.rebuildOverlay()
        }
    }

    private func redeemCode() {
        guard !isBuying, let scene = view?.window?.windowScene else { return }
        isBuying = true
        storeMessage = "Opening the App Store…"
        rebuildOverlay()
        Task { [weak self] in
            guard let self else { return }
            let unlocked = await self.store.redeemCode(in: scene)
            self.isBuying = false
            if unlocked {
                Haptics.victory(game: false)
                self.storeMessage = "Unlocked. Enjoy the full game!"
            } else {
                // A redeemed code can arrive a moment after the sheet closes.
                self.storeMessage = ""
            }
            self.rebuildOverlay()
        }
    }

    private func restorePurchases() {
        guard !isBuying else { return }
        isBuying = true
        storeMessage = "Checking your purchases…"
        rebuildOverlay()
        Task { [weak self] in
            guard let self else { return }
            let found = await self.store.restore()
            self.isBuying = false
            self.storeMessage = found ? "Full game restored." : "No earlier purchase found."
            self.rebuildOverlay()
        }
    }

    private func startRound() {
        cardNodes.values.forEach { $0.removeFromParent() }
        cardNodes = [:]
        var generator = SystemRandomNumberGenerator()
        let dealt = match.dealRound(using: &generator)
        round = dealt
        selectedJoker = nil
        overlay = nil
        eventText = dealt.turn == 0
            ? "You hold the 7\(Suit.spades.glyph) and start"
            : "\(names[dealt.turn]) holds the 7\(Suit.spades.glyph) and starts"
        rebuildOverlay()
        saveProgress()
        runDeal()
    }

    // MARK: - Dealing

    /// Flies the cards out from the middle of the table, then starts play. A tap skips it.
    private func runDeal() {
        sync(animated: false)
        refreshHUD()
        guard let round else { return }
        isDealing = true
        let m = metrics
        let deck = CGPoint(x: size.width / 2, y: m.tableMidY)

        // The player's cards fan out into the hand, one after another.
        for (index, card) in sortedHand(round.hands[0]).enumerated() {
            guard let node = cardNodes[card] else { continue }
            let target = node.position
            let scale = node.xScale
            node.position = deck
            node.setScale(scale * 0.6)
            node.alpha = 0
            let fly = SKAction.group([
                .fadeIn(withDuration: 0.08),
                .move(to: target, duration: 0.28),
                .scale(to: scale, duration: 0.28),
            ])
            fly.timingMode = .easeOut
            node.run(.sequence([.wait(forDuration: Double(index) * 0.035), fly]), withKey: "move")
        }

        // A few face-down cards fly to each opponent.
        let backSize = m.tableCardSize
        for player in 1..<round.playerCount {
            for copy in 0..<4 {
                let back = SKShapeNode(rectOf: backSize, cornerRadius: 7 * m.tableScale)
                back.fillColor = SKColor(red: 0.50, green: 0.09, blue: 0.11, alpha: 1)
                back.strokeColor = Style.cardFace
                back.lineWidth = 2
                back.position = deck
                back.alpha = 0
                dealLayer.addChild(back)
                back.run(.sequence([
                    .wait(forDuration: Double(copy) * 0.11 + Double(player) * 0.03),
                    .fadeIn(withDuration: 0.04),
                    .group([.move(to: m.badge(player), duration: 0.26), .scale(to: 0.5, duration: 0.26)]),
                    .fadeOut(withDuration: 0.08),
                    .removeFromParent(),
                ]))
            }
        }
        dealLayer.run(.repeat(.sequence([
            .run { Sounds.shared.play(.deal) },
            .wait(forDuration: 0.11),
        ]), count: 5), withKey: "sound")

        after(1.0) { [weak self] in self?.finishDeal() }
    }

    private func finishDeal() {
        guard isDealing else { return }
        isDealing = false
        dealLayer.removeAllChildren()
        advance()
    }

    /// Ends the deal animation at once and puts every card where it belongs.
    private func stopDealAnimation() {
        guard isDealing else { return }
        isDealing = false
        dealLayer.removeAllActions()
        dealLayer.removeAllChildren()
        cardNodes.values.forEach { $0.removeAllActions() }
        sync(animated: false)
    }

    // MARK: - Saving and resuming

    /// Writes the game in progress to disk. Practice rounds and test positions are never saved.
    /// Pass `roundRecorded` once a finished round has been added to the scores, so
    /// resuming deals the next round instead of scoring the same one twice.
    private func saveProgress(roundRecorded: Bool = false) {
        guard practice == nil, recordsHistory else { return }
        let game = SavedGame(match: match, round: roundRecorded ? nil : round, roundsWon: roundsWon)
        savedGame = game
        GameSaver.save(game)
    }

    private func clearSavedGame() {
        savedGame = nil
        GameSaver.clear()
    }

    private func resumeGame() {
        guard let saved = savedGame else { return }
        generation += 1
        practice = nil
        recordsHistory = true
        match = saved.match
        settings = saved.match.settings
        roundsWon = saved.roundsWon
        cardNodes.values.forEach { $0.removeFromParent() }
        cardNodes = [:]
        selectedJoker = nil
        overlay = nil
        if let saved = saved.round {
            round = saved
            eventText = "Game resumed"
            rebuildOverlay()
            advance()
        } else {
            // Saved between rounds: deal the next one.
            startRound()
        }
    }

    /// Adds an abandoned game to the history as a quit, if it got as far as finishing a round.
    private func recordQuit(of match: Match, roundsWon: Int) {
        guard match.roundsPlayed >= 1 else { return }
        history.add(GameRecord(
            date: Date(),
            target: match.settings.length.targetScore,
            jokers: match.settings.jokers,
            scores: match.scores,
            rounds: match.roundsPlayed,
            roundsWon: roundsWon,
            quit: true
        ))
        HistoryStore.save(history)
    }

    /// Starting a new game over a saved one counts the saved one as quit.
    private func replaceSavedGameWithNew() {
        if let saved = savedGame {
            recordQuit(of: saved.match, roundsWon: saved.roundsWon)
        }
        clearSavedGame()
        startMatch()
    }

    /// Redraws, then lets a bot move or waits for the player.
    private func advance() {
        sync(animated: true)
        refreshHUD()
        guard let round else { return }
        if round.winner != nil {
            after(1.2) { [weak self] in self?.finishRound() }
            return
        }
        if practice != nil {
            continuePractice()
            return
        }
        guard let actor = round.actor, actor != 0 else { return }
        after(0.9) { [weak self] in
            guard let self, let round = self.round, round.actor == actor,
                  let move = Bot.chooseMove(in: round) else { return }
            self.perform(move)
        }
    }

    /// A move the player made by tapping. In practice only the scripted move is accepted.
    private func playerPerform(_ move: Round.Move, from node: CardNode? = nil) {
        if let practice {
            guard case .you(let expected, _)? = practice.current, expected == move else {
                eventText = "Follow the hint for now"
                if let node { refuse(node) } else { refreshHUD() }
                return
            }
            self.practice?.index += 1
        }
        perform(move)
    }

    // MARK: - Practice round

    private func startPractice() {
        generation += 1
        practice = Practice()
        match = Match(settings: MatchSettings(length: .epic))
        cardNodes.values.forEach { $0.removeFromParent() }
        cardNodes = [:]
        round = Practice.deal()
        selectedJoker = nil
        overlay = nil
        eventText = ""
        rebuildOverlay()
        advance()
    }

    /// A quit game goes in the history once at least one round has been finished.
    private var quitWillCount: Bool {
        practice == nil && recordsHistory && match.roundsPlayed >= 1
    }

    /// Opens the in-game menu. The bots wait while it is up.
    private func openGameMenu() {
        guard round != nil, practice == nil, overlay == nil else { return }
        generation += 1
        stopDealAnimation()
        overlay = .gameMenu
        rebuildOverlay()
    }

    private func keepPlaying() {
        overlay = nil
        rebuildOverlay()
        advance()
    }

    private func quitGame() {
        if quitWillCount {
            recordQuit(of: match, roundsWon: roundsWon)
        }
        clearSavedGame()
        stopDealAnimation()
        Haptics.thump()
        generation += 1
        round = nil
        match = Match(settings: settings)
        selectedJoker = nil
        eventText = ""
        overlay = .menu
        sync(animated: false)
        refreshHUD()
        rebuildOverlay()
    }

    private func endPractice() {
        generation += 1
        practice = nil
        round = nil
        match = Match(settings: settings)
        eventText = ""
        overlay = .menu
        sync(animated: false)
        refreshHUD()
        rebuildOverlay()
    }

    /// Runs the next scripted bot move, or waits for the player on a note or a `you` step.
    private func continuePractice() {
        refreshCoach()
        guard case .bot(let move)? = practice?.current else { return }
        after(1.0) { [weak self] in
            guard let self, self.practice != nil else { return }
            self.practice?.index += 1
            self.perform(move)
        }
    }

    private func refreshCoach() {
        coachLayer.removeAllChildren()
        guard let practice, overlay == nil, round?.winner == nil else { return }
        let text: String
        let isNote: Bool
        switch practice.current {
        case .note(let note)?: (text, isNote) = (note, true)
        case .you(_, let hint)?: (text, isNote) = (hint, false)
        case .bot?, nil: return
        }

        let m = metrics
        let u = m.u
        // Upright, the bubble sits under the table. On its side the table fills
        // that space, so the bubble goes in the corner to the right of the hand.
        let beside = m.isLandscape
        let width = beside ? size.width / 2 - 200 * u : min(size.width - 28 * u, 520 * u)

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineSpacing = 2 * u
        let label = SKLabelNode()
        label.attributedText = NSAttributedString(string: text, attributes: [
            .font: UIFont(name: Style.regular, size: 14.5 * u) ?? .systemFont(ofSize: 14.5 * u),
            .foregroundColor: SKColor.white,
            .paragraphStyle: paragraph,
        ])
        label.numberOfLines = 0
        label.preferredMaxLayoutWidth = width - 28 * u
        label.verticalAlignmentMode = .center
        label.zPosition = 1

        let buttonSpace = isNote ? 40 * u : 0
        let height = label.frame.height + 28 * u + buttonSpace
        let bubble = SKShapeNode(rectOf: CGSize(width: width, height: height), cornerRadius: 14 * u)
        bubble.position = beside
            ? CGPoint(x: size.width - 14 * u - width / 2,
                      y: max(safeInsets.bottom + 10 * u + height / 2, (safeInsets.bottom + m.tableBottom) / 2))
            : CGPoint(x: size.width / 2, y: m.tableBottom + height / 2 + 2 * u)
        bubble.fillColor = Style.panel
        bubble.strokeColor = Style.gold
        bubble.lineWidth = 1.5
        coachLayer.addChild(bubble)
        label.position = CGPoint(x: 0, y: buttonSpace / 2)
        bubble.addChild(label)

        if isNote {
            let next = ButtonNode(
                text: "Next",
                size: CGSize(width: 110 * u, height: 30 * u),
                fontSize: 15 * u
            ) { [weak self] in
                self?.practice?.index += 1
                self?.continuePractice()
            }
            next.position = CGPoint(x: 0, y: -height / 2 + 26 * u)
            next.zPosition = 1
            bubble.addChild(next)
        }
    }

    private func perform(_ move: Round.Move) {
        guard var next = round, let actor = next.actor else { return }
        let phase = next.phase
        guard (try? next.apply(move)) != nil else { return }
        eventText = describe(move, by: actor, phase: phase, in: next)
        playHaptic(for: move, by: actor, phase: phase)
        round = next
        selectedJoker = nil
        saveProgress()
        advance()
    }

    /// Called just before `round` is replaced, so it still holds the position before the move.
    private func playHaptic(for move: Round.Move, by actor: Int, phase: Round.Phase) {
        guard let round else { return }
        switch move {
        case .play, .give:
            Sounds.shared.play(.card)
        case .joker(_, let face):
            Sounds.shared.play(actor != 0 && round.hands[0].contains(.face(face)) ? .warning : .joker)
        case .ask:
            Sounds.shared.play(.ask)
        }
        switch move {
        case .play:
            actor == 0 ? Haptics.tap() : Haptics.tick()
        case .joker(_, let face):
            // A joker that kills one of your cards is bad news.
            if actor != 0 && round.hands[0].contains(.face(face)) {
                Haptics.warning()
            } else {
                Haptics.thump()
            }
        case .ask:
            // Being asked for a card needs your attention.
            if actor == 0 || round.previousPlayer(before: actor) == 0 {
                Haptics.thump()
            } else {
                Haptics.tick()
            }
        case .give:
            if case .give(let from, let to) = phase, from == 0 || to == 0 {
                Haptics.tap()
            } else {
                Haptics.tick()
            }
        }
    }

    private func finishRound() {
        guard let round, let winner = round.winner else { return }
        let points = match.record(round)
        if winner == 0 { roundsWon += 1 }
        if match.isOver && practice == nil && recordsHistory {
            history.add(GameRecord(
                date: Date(),
                target: settings.length.targetScore,
                jokers: settings.jokers,
                scores: match.scores,
                rounds: match.roundsPlayed,
                roundsWon: roundsWon
            ))
            HistoryStore.save(history)
        }
        if practice == nil && recordsHistory {
            if match.isOver {
                clearSavedGame()
            } else {
                saveProgress(roundRecorded: true)
            }
        }
        overlay = .summary(points: points, winner: winner)
        if match.isOver && practice == nil {
            if match.winners.contains(0) {
                Haptics.victory(game: true)
                Sounds.shared.play(.bigWin)
            } else {
                Haptics.warning()
                Sounds.shared.play(.warning)
            }
        } else if winner == 0 {
            Haptics.victory(game: false)
            Sounds.shared.play(.win)
        } else {
            Haptics.thump()
        }
        refreshHUD()
        rebuildOverlay()
    }

    private func after(_ seconds: TimeInterval, _ body: @escaping () -> Void) {
        let token = generation
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard let self, self.generation == token else { return }
            body()
        }
    }

    private func describe(_ move: Round.Move, by actor: Int, phase: Round.Phase, in round: Round) -> String {
        let you = actor == 0
        let who = names[actor]
        switch move {
        case .play(let face):
            return "\(who) played \(face.label)"
        case .joker(_, let face):
            return "\(who) played a joker as \(face.label)"
        case .ask:
            let giver = round.previousPlayer(before: actor)
            let from = giver == 0 ? "you" : names[giver]
            return you ? "You asked \(from) for a card" : "\(who) can't play and asks \(from) for a card"
        case .give(let card):
            guard case .give(let from, let to) = phase else { return "" }
            if to == 0 { return "\(names[from]) gave you \(card.label)" }
            if from == 0 { return "You gave \(names[to]) \(card.label)" }
            return "\(names[from]) gave \(names[to]) a card"
        }
    }

    // MARK: - Drawing the cards

    private struct Placement {
        var position: CGPoint
        var scale: CGFloat
        var z: CGFloat
        var alpha: CGFloat = 1
        var standIn: Face?
        var dimmed = false
        var selected = false
    }

    private func sortedHand(_ hand: [Card]) -> [Card] {
        func key(_ card: Card) -> Int {
            switch card {
            case .face(let face):
                (Suit.displayOrder.firstIndex(of: face.suit) ?? 0) * 100 + face.rank
            case .joker(let index):
                1000 + index
            }
        }
        return hand.sorted { key($0) < key($1) }
    }

    /// The cards in the player's hand that can be tapped right now.
    private func playableCards(in round: Round) -> Set<Card> {
        guard round.actor == 0 else { return [] }
        var cards: Set<Card> = []
        for move in round.legalMoves() {
            switch move {
            case .play(let face): cards.insert(.face(face))
            case .joker(let joker, _): cards.insert(joker)
            case .give(let card): cards.insert(card)
            case .ask: break
            }
        }
        return cards
    }

    private func sync(animated: Bool) {
        guard let round else {
            cardNodes.values.forEach { $0.removeFromParent() }
            cardNodes = [:]
            return
        }
        let m = metrics
        var placements: [Card: Placement] = [:]

        // Cards on the table. A joker sits in the slot of the card it replaced.
        for (suit, run) in round.table.runs {
            for rank in run {
                let face = Face(suit, rank)
                let joker = round.table.jokers[face]
                placements[joker ?? .face(face)] = Placement(
                    position: m.slot(face),
                    scale: m.tableScale,
                    z: m.tableZ(face),
                    standIn: joker == nil ? nil : face
                )
            }
        }

        // Bot hands are hidden behind their badges.
        for player in 1..<round.playerCount {
            for card in round.hands[player] {
                placements[card] = Placement(position: m.badge(player), scale: m.tableScale * 0.5, z: 1, alpha: 0)
            }
        }

        // The player's hand, in rows of seven.
        let hand = sortedHand(round.hands[0])
        let playable = playableCards(in: round)
        let myTurn = round.actor == 0
        let perRow = m.handPerRow
        let rows = stride(from: 0, to: hand.count, by: perRow).map {
            Array(hand[$0..<min($0 + perRow, hand.count)])
        }
        let cardWidth = CardNode.size.width * m.handScale
        for (rowIndex, row) in rows.enumerated() {
            let rowsBelow = rows.count - 1 - rowIndex
            let baseY = m.handBottomRowY + CGFloat(rowsBelow) * m.handRowStep
                + (rows.count == 1 && !m.isLandscape ? m.handRowStep / 2 : 0)
            let spread = row.count > 1
                ? min(cardWidth + 4 * m.u, (size.width - 24 * m.u - cardWidth) / CGFloat(row.count - 1))
                : 0
            let startX = size.width / 2 - spread * CGFloat(row.count - 1) / 2
            for (index, card) in row.enumerated() {
                let canPlay = playable.contains(card)
                let selected = card == selectedJoker
                placements[card] = Placement(
                    position: CGPoint(
                        x: startX + spread * CGFloat(index),
                        y: baseY + (selected ? 18 * m.u : canPlay ? 9 * m.u : 0)
                    ),
                    scale: m.handScale,
                    z: CGFloat(100 + (rowIndex * 20 + index) * 3),
                    dimmed: myTurn && !canPlay,
                    selected: selected
                )
            }
        }

        for (card, placement) in placements {
            let node: CardNode
            var isNew = false
            if let existing = cardNodes[card] {
                node = existing
            } else {
                node = CardNode(card: card)
                cardNodes[card] = node
                addChild(node)
                isNew = true
            }
            node.zPosition = placement.z
            node.standIn = placement.standIn
            node.isDead = card.face.map(round.dead.contains) ?? false
            node.isDimmed = placement.dimmed
            node.isSelected = placement.selected

            node.removeAction(forKey: "move")
            if animated && !isNew {
                let move = SKAction.group([
                    .move(to: placement.position, duration: 0.28),
                    .scale(to: placement.scale, duration: 0.28),
                    .fadeAlpha(to: placement.alpha, duration: 0.2),
                ])
                move.timingMode = .easeOut
                node.run(move, withKey: "move")
            } else {
                node.position = placement.position
                node.setScale(placement.scale)
                node.alpha = placement.alpha
            }
        }
    }

    // MARK: - Labels, badges and joker targets

    private func refreshHUD() {
        let m = metrics
        let actor = round?.actor
        for (player, badge) in badges {
            badge.update(
                name: names[player],
                cards: round?.hands[player].count,
                score: match.scores[player],
                active: actor == player
            )
        }

        hudLayer.childNode(withName: "ask")?.removeFromParent()
        hudLayer.childNode(withName: "exitPractice")?.removeFromParent()
        hudLayer.childNode(withName: "quitGame")?.removeFromParent()
        refreshCoach()
        targetLayer.removeAllChildren()
        eventLabel.text = eventText
        promptLabel.text = ""
        promptLabel.fontColor = .white
        promptLabel.fontSize = 18 * m.u

        guard let round else {
            infoLabel.text = ""
            return
        }
        let count = round.hands[0].count
        if practice != nil {
            infoLabel.text = ""
            let exit = ButtonNode(
                text: "Exit practice",
                size: CGSize(width: 120 * m.u, height: 22 * m.u),
                fontSize: 12 * m.u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white
            ) { [weak self] in self?.endPractice() }
            exit.name = "exitPractice"
            exit.position = infoLabel.position
            exit.zPosition = 400
            hudLayer.addChild(exit)
        } else {
            infoLabel.text = "You · \(count) card\(count == 1 ? "" : "s") · \(match.scores[0]) pts · game to \(settings.length.targetScore)"
            let quit = ButtonNode(
                text: "Menu",
                size: CGSize(width: 48 * m.u, height: 22 * m.u),
                fontSize: 12 * m.u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white
            ) { [weak self] in self?.openGameMenu() }
            quit.name = "quitGame"
            quit.position = CGPoint(x: 12 * m.u + 24 * m.u, y: infoLabel.position.y)
            quit.zPosition = 400
            hudLayer.addChild(quit)
        }

        guard let actor else { return }
        if actor != 0 {
            promptLabel.text = "\(names[actor])'s turn"
            promptLabel.fontColor = SKColor(white: 1, alpha: 0.7)
            return
        }

        promptLabel.fontColor = Style.gold
        switch round.phase {
        case .give(_, let to):
            promptLabel.text = "\(names[to]) asks you for a card. Pick one to give."
            promptLabel.fontSize = 16 * m.u
        case .play:
            promptLabel.fontSize = 18 * m.u
            if round.legalMoves() == [.ask] {
                let giver = names[round.previousPlayer(before: 0)]
                let button = ButtonNode(
                    text: "No play. Ask \(giver) for a card",
                    size: CGSize(width: 250 * m.u, height: 30 * m.u),
                    fontSize: 15 * m.u
                ) { [weak self] in self?.playerPerform(.ask) }
                button.name = "ask"
                button.position = promptLabel.position
                button.zPosition = 400
                hudLayer.addChild(button)
            } else if selectedJoker != nil {
                promptLabel.text = "Tap a glowing spot to place the joker"
                promptLabel.fontSize = 16 * m.u
                for face in round.table.openFaces {
                    let spot = m.target(for: face, run: round.table.runs[face.suit])
                    let target = TargetNode(face: face, size: spot.size, scale: m.tableScale)
                    target.position = spot.position
                    targetLayer.addChild(target)
                }
            } else {
                promptLabel.text = "Your turn"
            }
        case .over:
            break
        }
    }

    // MARK: - Touches

    private func ancestor<T: SKNode>(of node: SKNode, as type: T.Type) -> T? {
        var current: SKNode? = node
        while let candidate = current {
            if let match = candidate as? T { return match }
            current = candidate.parent
        }
        return nil
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        if isDealing && overlay == nil {
            // Any tap skips the deal.
            generation += 1
            stopDealAnimation()
            advance()
            return
        }
        let hits = nodes(at: touch.location(in: self))

        if overlay != nil {
            let button = hits.lazy
                .compactMap { self.ancestor(of: $0, as: ButtonNode.self) }
                .first { $0.inParentHierarchy(self.overlayLayer) }
            if let button {
                Haptics.tap()
                Sounds.shared.play(.tap)
                button.action()
            }
            return
        }
        if let button = hits.lazy.compactMap({ self.ancestor(of: $0, as: ButtonNode.self) }).first {
            Haptics.tap()
            Sounds.shared.play(.tap)
            button.action()
            return
        }
        if let target = hits.lazy.compactMap({ self.ancestor(of: $0, as: TargetNode.self) }).first {
            placeJoker(as: target.face)
            return
        }

        let mine = Set(round?.hands[0] ?? [])
        let cards = hits
            .compactMap { ancestor(of: $0, as: CardNode.self) }
            .filter { mine.contains($0.card) }
        if let top = cards.max(by: { $0.zPosition < $1.zPosition }) {
            tapped(top)
        } else if selectedJoker != nil {
            selectedJoker = nil
            sync(animated: true)
            refreshHUD()
        }
    }

    private func tapped(_ node: CardNode) {
        guard let round, round.actor == 0 else { return }
        let card = node.card
        switch round.phase {
        case .give:
            playerPerform(.give(card), from: node)
        case .play:
            switch card {
            case .face(let face):
                if round.isLegal(.play(face)) {
                    playerPerform(.play(face), from: node)
                } else {
                    eventText = round.dead.contains(face)
                        ? "\(face.label) was replaced by a joker and can't be played"
                        : "\(face.label) can't be played yet"
                    refuse(node)
                }
            case .joker:
                let usable = round.legalMoves().contains { move in
                    if case .joker(let joker, _) = move { return joker == card }
                    return false
                }
                if usable {
                    selectedJoker = selectedJoker == card ? nil : card
                    Haptics.tick()
                    sync(animated: true)
                    refreshHUD()
                } else {
                    eventText = "The round has to open with the real 7\(Suit.spades.glyph)"
                    refuse(node)
                }
            }
        case .over:
            break
        }
    }

    private func refuse(_ node: CardNode) {
        Haptics.warning()
        Sounds.shared.play(.warning)
        refreshHUD()
        let nudge = 5 * metrics.u
        node.run(.sequence([
            .moveBy(x: -nudge, y: 0, duration: 0.05),
            .moveBy(x: nudge * 2, y: 0, duration: 0.08),
            .moveBy(x: -nudge, y: 0, duration: 0.05),
        ]))
    }

    private func placeJoker(as face: Face) {
        guard let round, let joker = selectedJoker else { return }
        let move = Round.Move.joker(joker, as: face)
        if round.jokerReplacesOwnCard(move) {
            overlay = .jokerWarning(move, face)
            rebuildOverlay()
        } else {
            playerPerform(move)
        }
    }

    // MARK: - Overlays

    private func rebuildOverlay() {
        overlayLayer.removeAllChildren()
        guard let overlay, isSetUp else { return }
        let m = metrics

        let dimmer = SKShapeNode(rectOf: CGSize(width: size.width * 2, height: size.height * 2))
        dimmer.position = CGPoint(x: size.width / 2, y: size.height / 2)
        dimmer.fillColor = SKColor(white: 0, alpha: 0.6)
        dimmer.strokeColor = .clear
        overlayLayer.addChild(dimmer)

        let width = min(size.width - 32 * m.u, 340 * m.u)
        let height: CGFloat
        switch overlay {
        case .menu: height = 530 * m.u
        case .history: height = 470 * m.u
        case .store: height = 500 * m.u
        case .help: height = 470 * m.u
        case .summary: height = 350 * m.u
        case .jokerWarning: height = 250 * m.u
        case .gameMenu: height = 330 * m.u
        case .quitConfirm: height = 250 * m.u
        case .newGameConfirm: height = 250 * m.u
        }
        let panel = SKShapeNode(rectOf: CGSize(width: width, height: height), cornerRadius: 18 * m.u)
        panel.position = CGPoint(x: size.width / 2, y: size.height / 2)
        panel.fillColor = Style.panel
        panel.strokeColor = Style.gold.withAlphaComponent(0.6)
        panel.lineWidth = 1.5
        panel.zPosition = 1
        overlayLayer.addChild(panel)

        switch overlay {
        case .menu:
            buildMenu(in: panel, width: width, m: m)
        case .history:
            buildHistory(in: panel, width: width, m: m)
        case .store:
            buildStore(in: panel, width: width, m: m)
        case .help(let page):
            buildHelp(in: panel, width: width, page: page, m: m)
        case .summary(let points, let winner):
            buildSummary(in: panel, width: width, points: points, winner: winner, m: m)
        case .gameMenu:
            buildGameMenu(in: panel, width: width, m: m)
        case .quitConfirm:
            buildQuitConfirm(in: panel, width: width, m: m)
        case .newGameConfirm:
            buildNewGameConfirm(in: panel, width: width, m: m)
        case .jokerWarning(let move, let face):
            buildJokerWarning(in: panel, width: width, move: move, face: face, m: m)
        }
    }

    private func add(_ node: SKNode, to panel: SKNode, x: CGFloat = 0, y: CGFloat) {
        node.position = CGPoint(x: x, y: y)
        node.zPosition = 1
        panel.addChild(node)
    }

    private func choiceButton(_ text: String, size: CGSize, selected: Bool, m: Metrics, action: @escaping () -> Void) -> ButtonNode {
        ButtonNode(
            text: text,
            size: size,
            fontSize: 15 * m.u,
            fill: selected ? Style.cardFace : SKColor(white: 1, alpha: 0.12),
            textColor: selected ? Style.ink : .white,
            action: action
        )
    }

    private func buildMenu(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        limitSettingsToWhatIsOwned()
        let lock = " \u{1F512}"
        add(Style.label("Sevens", size: 44 * u, font: Style.bold, color: Style.gold), to: panel, y: 215 * u)
        add(Style.label("Spades rule the table", size: 15 * u, color: SKColor(white: 1, alpha: 0.8)), to: panel, y: 179 * u)

        add(Style.label("Game length", size: 14 * u, font: Style.bold), to: panel, y: 139 * u)
        let half = CGSize(width: (width - 52 * u) / 2, height: 40 * u)
        for (index, length) in GameLength.allCases.enumerated() {
            let locked = isLocked(length)
            let button = choiceButton(
                "\(length.title) · \(length.targetScore)" + (locked ? lock : ""),
                size: half,
                selected: settings.length == length,
                m: m
            ) { [weak self] in
                if locked {
                    self?.openStore()
                } else {
                    self?.settings.length = length
                    self?.rebuildOverlay()
                }
            }
            let column: CGFloat = index % 2 == 0 ? -1 : 1
            add(button, to: panel,
                x: column * (half.width / 2 + 6 * u),
                y: (101 - CGFloat(index / 2) * 48) * u)
        }

        add(Style.label("Jokers", size: 14 * u, font: Style.bold), to: panel, y: 9 * u)
        let third = CGSize(width: (width - 64 * u) / 3, height: 40 * u)
        for count in 0...2 {
            let locked = !store.isUnlocked && count > 0
            let button = choiceButton(
                (count == 0 ? "None" : "\(count)") + (locked ? lock : ""),
                size: third,
                selected: settings.jokers == count,
                m: m
            ) { [weak self] in
                if locked {
                    self?.openStore()
                } else {
                    self?.settings.jokers = count
                    self?.rebuildOverlay()
                }
            }
            add(button, to: panel, x: CGFloat(count - 1) * (third.width + 12 * u), y: -29 * u)
        }

        if savedGame != nil {
            // A game is waiting: resuming is the main action, and a new game replaces it.
            let half = CGSize(width: (width - 52 * u) / 2, height: 50 * u)
            let resume = ButtonNode(text: "Resume game", size: half, fontSize: 17 * u) { [weak self] in
                self?.resumeGame()
            }
            add(resume, to: panel, x: -(half.width / 2 + 6 * u), y: -103 * u)
            let fresh = ButtonNode(
                text: "New game",
                size: half,
                fontSize: 17 * u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white
            ) { [weak self] in
                self?.overlay = .newGameConfirm
                self?.rebuildOverlay()
            }
            add(fresh, to: panel, x: half.width / 2 + 6 * u, y: -103 * u)
        } else {
            let deal = ButtonNode(
                text: "Deal",
                size: CGSize(width: width - 40 * u, height: 50 * u),
                fontSize: 20 * u
            ) { [weak self] in self?.startMatch() }
            add(deal, to: panel, y: -103 * u)
        }

        let sound = ButtonNode(
            text: Sounds.shared.isMuted ? "Sound off" : "Sound on",
            size: CGSize(width: 76 * u, height: 22 * u),
            fontSize: 11 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            Sounds.shared.isMuted.toggle()
            self?.rebuildOverlay()
        }
        add(sound, to: panel, x: width / 2 - 50 * u, y: 247 * u)

        let small = CGSize(width: (width - 52 * u) / 2, height: 44 * u)
        let help = ButtonNode(
            text: "How to play",
            size: small,
            fontSize: 16 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            self?.overlay = .help(page: 0)
            self?.rebuildOverlay()
        }
        add(help, to: panel, x: -(small.width / 2 + 6 * u), y: -163 * u)

        let practiceButton = ButtonNode(
            text: "Practice round",
            size: small,
            fontSize: 16 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in self?.startPractice() }
        add(practiceButton, to: panel, x: small.width / 2 + 6 * u, y: -163 * u)

        let unlocked = store.isUnlocked
        let historyButton = ButtonNode(
            text: "History" + (unlocked ? "" : lock),
            size: small,
            fontSize: 16 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            if unlocked {
                self?.overlay = .history
                self?.rebuildOverlay()
            } else {
                self?.openStore()
            }
        }
        add(historyButton, to: panel, x: -(small.width / 2 + 6 * u), y: -217 * u)

        let storeButton = ButtonNode(
            text: unlocked ? "Tip your dealer" : "Unlock full game",
            size: small,
            fontSize: 15 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: Style.gold
        ) { [weak self] in self?.openStore() }
        add(storeButton, to: panel, x: small.width / 2 + 6 * u, y: -217 * u)
    }

    private func buildStore(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        let faint = SKColor(white: 1, alpha: 0.7)
        let unlocked = store.isUnlocked
        add(Style.label(unlocked ? "Tip your dealer" : "Sevens: Full Game",
                        size: 24 * u, font: Style.bold, color: Style.gold), to: panel, y: 218 * u)

        if unlocked {
            let thanks = Style.label("You own the full game.\nThank you for supporting Sevens!", size: 15 * u)
            thanks.numberOfLines = 0
            thanks.preferredMaxLayoutWidth = width - 48 * u
            add(thanks, to: panel, y: 130 * u)
        } else {
            let features = [
                "Jokers: play with one or two",
                "Long and Epic games, to 200 and 500",
                "Game history and your trend",
            ]
            for (index, feature) in features.enumerated() {
                add(Style.label("•  " + feature, size: 15 * u), to: panel, y: (182 - CGFloat(index) * 24) * u)
            }
            add(Style.label("One purchase. Yours to keep. No ads, ever.", size: 12.5 * u, color: faint),
                to: panel, y: 106 * u)

            let wide = CGSize(width: width - 40 * u, height: 48 * u)
            if let product = store.fullGame {
                let buy = ButtonNode(text: "Unlock for \(product.displayPrice)", size: wide, fontSize: 19 * u) {
                    [weak self] in self?.purchase(product)
                }
                add(buy, to: panel, y: 66 * u)
            } else {
                let waiting = ButtonNode(
                    text: store.hasLoaded ? "Store unavailable" : "Loading…",
                    size: wide,
                    fontSize: 17 * u,
                    fill: SKColor(white: 1, alpha: 0.12),
                    textColor: faint
                ) {}
                add(waiting, to: panel, y: 66 * u)
            }
            let small = CGSize(width: (width - 50 * u) / 2, height: 28 * u)
            let restore = ButtonNode(
                text: "Restore purchases",
                size: small,
                fontSize: 13 * u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white
            ) { [weak self] in self?.restorePurchases() }
            add(restore, to: panel, x: -(small.width / 2 + 5 * u), y: 22 * u)
            let redeem = ButtonNode(
                text: "Redeem code",
                size: small,
                fontSize: 13 * u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white
            ) { [weak self] in self?.redeemCode() }
            add(redeem, to: panel, x: small.width / 2 + 5 * u, y: 22 * u)
        }

        // Tips are always optional and never unlock anything.
        if !unlocked {
            add(Style.label("Tip your dealer", size: 16 * u, font: Style.bold), to: panel, y: -20 * u)
        }
        add(Style.label("Tips are optional and don't unlock anything extra.", size: 12.5 * u, color: faint),
            to: panel, y: (unlocked ? 70 : -42) * u)
        let tipY: CGFloat = (unlocked ? 20 : -84) * u
        if store.tips.isEmpty {
            add(Style.label(store.hasLoaded ? "Tips are unavailable right now." : "Loading…",
                            size: 14 * u, color: faint), to: panel, y: tipY)
        } else {
            let count = CGFloat(store.tips.count)
            let size = CGSize(width: (width - 40 * u - (count - 1) * 10 * u) / count, height: 44 * u)
            for (index, tip) in store.tips.enumerated() {
                let x = (CGFloat(index) - (count - 1) / 2) * (size.width + 10 * u)
                let button = ButtonNode(
                    text: tip.displayPrice,
                    size: size,
                    fontSize: 17 * u,
                    fill: SKColor(white: 1, alpha: 0.12),
                    textColor: .white
                ) { [weak self] in self?.purchase(tip) }
                add(button, to: panel, x: x, y: tipY)
                add(Style.label(tip.displayName, size: 12 * u, color: faint), to: panel, x: x, y: tipY - 34 * u)
            }
        }

        add(Style.label(storeMessage, size: 14 * u, font: Style.bold, color: Style.gold),
            to: panel, y: (unlocked ? -70 : -150) * u)

        // Hidden support tool: five taps on the title show what the App Store told the app.
        let secret = ButtonNode(
            text: "",
            size: CGSize(width: 240 * u, height: 36 * u),
            fontSize: 1,
            fill: .clear,
            outlined: false
        ) { [weak self] in
            guard let self else { return }
            self.storeTitleTaps += 1
            if self.storeTitleTaps >= 5 {
                self.storeTitleTaps = 0
                self.showsStoreDiagnostics.toggle()
                self.rebuildOverlay()
            }
        }
        add(secret, to: panel, y: 218 * u)

        if showsStoreDiagnostics {
            let detail = Style.label(store.diagnostics, size: 9.5 * u, color: SKColor(white: 1, alpha: 0.55))
            detail.numberOfLines = 0
            detail.preferredMaxLayoutWidth = width - 32 * u
            add(detail, to: panel, y: -172 * u)
        }

        let close = ButtonNode(
            text: "Close",
            size: CGSize(width: width - 40 * u, height: 46 * u),
            fontSize: 18 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            self?.overlay = .menu
            self?.rebuildOverlay()
        }
        add(close, to: panel, y: -207 * u)
    }

    private func buildHistory(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        let faint = SKColor(white: 1, alpha: 0.7)
        add(Style.label("Your history", size: 24 * u, font: Style.bold, color: Style.gold), to: panel, y: 198 * u)

        let close = ButtonNode(
            text: "Close",
            size: CGSize(width: width - 40 * u, height: 46 * u),
            fontSize: 18 * u
        ) { [weak self] in
            self?.overlay = .menu
            self?.rebuildOverlay()
        }
        add(close, to: panel, y: -195 * u)

        guard !history.games.isEmpty else {
            let empty = Style.label("No finished games yet.\nPlay a game to the end to start your history.", size: 15 * u)
            empty.numberOfLines = 0
            empty.preferredMaxLayoutWidth = width - 48 * u
            add(empty, to: panel, y: 20 * u)
            return
        }

        let all = history.allTime
        let recent = history.recentSummary
        let earlier = history.earlierSummary
        add(Style.label(
            "\(all.games) game\(all.games == 1 ? "" : "s") played · \(all.wins) won",
            size: 14 * u, color: faint
        ), to: panel, y: 170 * u)

        // The headline: how the recent games compare with the ones before them.
        let headline: String
        let detail: String
        let color: SKColor
        switch history.trend {
        case .improving:
            (headline, color) = ("↗\u{FE0E} Trending better", Style.gold)
            detail = "Your recent games are finishing higher than before."
        case .steady:
            (headline, color) = ("→\u{FE0E} Holding steady", .white)
            detail = "Your recent games match your earlier ones."
        case .slipping:
            (headline, color) = ("↘\u{FE0E} Slipping a little", SKColor(red: 1.0, green: 0.55, blue: 0.5, alpha: 1))
            detail = "Your recent games are finishing lower than before."
        case .notEnough(let needed):
            (headline, color) = ("Trend coming soon", .white)
            detail = "Play \(needed) more game\(needed == 1 ? "" : "s") to see how you're trending."
        }
        add(Style.label(headline, size: 20 * u, font: Style.bold, color: color), to: panel, y: 132 * u)
        add(Style.label(detail, size: 12.5 * u, color: faint), to: panel, y: 108 * u)

        // Recent games against the games before them.
        let left = -width / 2 + 24 * u
        let middle = width / 2 - 112 * u
        let right = width / 2 - 24 * u
        func cell(_ text: String, x: CGFloat, y: CGFloat, bold: Bool = false, color: SKColor = .white, leading: Bool = false) {
            let label = Style.label(text, size: (bold ? 16 : 13) * u, font: bold ? Style.bold : Style.regular, color: color)
            label.horizontalAlignmentMode = leading ? .left : .right
            add(label, to: panel, x: x, y: y)
        }
        let hasEarlier = earlier.games > 0
        cell("Last \(recent.games)", x: middle, y: 74 * u, color: faint)
        cell(hasEarlier ? "Before" : "", x: right, y: 74 * u, color: faint)

        func row(_ title: String, _ recentText: String, _ earlierText: String, better: Bool?, y: CGFloat) {
            cell(title, x: left, y: y, leading: true)
            let color: SKColor = better == true ? Style.gold : .white
            cell(recentText, x: middle, y: y, bold: true, color: color)
            cell(hasEarlier ? earlierText : "–", x: right, y: y, bold: true, color: faint)
        }
        func percent(_ value: Double) -> String { "\(Int((value * 100).rounded()))%" }
        func decimal(_ value: Double) -> String { String(format: "%.1f", value) }
        row("Games won", percent(recent.winRate), percent(earlier.winRate),
            better: hasEarlier ? recent.winRate > earlier.winRate : nil, y: 46 * u)
        row("Average finish", decimal(recent.averagePlace), decimal(earlier.averagePlace),
            better: hasEarlier ? recent.averagePlace < earlier.averagePlace : nil, y: 18 * u)
        row("Points per round", decimal(recent.pointsPerRound), decimal(earlier.pointsPerRound),
            better: hasEarlier ? recent.pointsPerRound < earlier.pointsPerRound : nil, y: -10 * u)

        // Finishing place in each of the last ten games, oldest on the left.
        let strip = Array(history.games.suffix(GameHistory.recentCount))
        add(Style.label("Where you finished, oldest to newest", size: 12.5 * u, color: faint), to: panel, y: -52 * u)
        let side = 25 * u
        let gap = 5 * u
        for (index, game) in strip.enumerated() {
            let square = SKShapeNode(rectOf: CGSize(width: side, height: side), cornerRadius: 6 * u)
            let fill: SKColor
            let text: SKColor
            switch game.quit ? 0 : game.place {
            case 1: (fill, text) = (Style.gold, Style.ink)
            case 2: (fill, text) = (SKColor(white: 1, alpha: 0.85), Style.ink)
            case 3: (fill, text) = (SKColor(white: 1, alpha: 0.25), .white)
            default: (fill, text) = (Style.red, .white)
            }
            square.fillColor = fill
            square.strokeColor = .clear
            let label = Style.label(game.quit ? "Q" : "\(game.place)", size: 14 * u, font: Style.bold, color: text)
            label.zPosition = 1
            square.addChild(label)
            add(square, to: panel,
                x: (CGFloat(index) - CGFloat(strip.count - 1) / 2) * (side + gap),
                y: -84 * u)
        }
        add(Style.label("1 is a win · Q is a game you quit · fewer points is better", size: 11.5 * u, color: faint), to: panel, y: -118 * u)
    }

    private func buildHelp(in panel: SKNode, width: CGFloat, page index: Int, m: Metrics) {
        let u = m.u
        let pages = HelpPage.all
        let page = pages[min(max(index, 0), pages.count - 1)]
        let isLast = index >= pages.count - 1

        add(Style.label(page.title, size: 24 * u, font: Style.bold, color: Style.gold), to: panel, y: 195 * u)

        // Example cards in a row.
        let scale = 0.78 * u
        let spacing = (CardNode.size.width + 8) * scale
        for (position, example) in page.examples.enumerated() {
            let node = CardNode(card: example.card)
            node.standIn = example.standIn
            node.isDead = example.dead
            node.setScale(scale)
            add(node, to: panel,
                x: (CGFloat(position) - CGFloat(page.examples.count - 1) / 2) * spacing,
                y: 122 * u)
        }

        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineSpacing = 3 * u
        let body = SKLabelNode()
        body.attributedText = NSAttributedString(string: page.text, attributes: [
            .font: UIFont(name: Style.regular, size: 15 * u) ?? .systemFont(ofSize: 15 * u),
            .foregroundColor: SKColor.white,
            .paragraphStyle: paragraph,
        ])
        body.numberOfLines = 0
        body.preferredMaxLayoutWidth = width - 44 * u
        body.verticalAlignmentMode = .center
        add(body, to: panel, y: -22 * u)

        // Page dots.
        for dot in pages.indices {
            let circle = SKShapeNode(circleOfRadius: 3.5 * u)
            circle.fillColor = dot == index ? Style.gold : SKColor(white: 1, alpha: 0.3)
            circle.strokeColor = .clear
            add(circle, to: panel, x: (CGFloat(dot) - CGFloat(pages.count - 1) / 2) * 14 * u, y: -148 * u)
        }

        let size = CGSize(width: (width - 52 * u) / 2, height: 46 * u)
        let close: () -> Void = { [weak self] in
            UserDefaults.standard.set(true, forKey: HelpPage.seenKey)
            // Opened mid-game, the instructions go back to the game menu.
            self?.overlay = self?.round != nil ? .gameMenu : .menu
            self?.rebuildOverlay()
        }
        let back = ButtonNode(
            text: index == 0 ? "Skip" : "Back",
            size: size,
            fontSize: 17 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            if index == 0 {
                close()
            } else {
                self?.overlay = .help(page: index - 1)
                self?.rebuildOverlay()
            }
        }
        add(back, to: panel, x: -(size.width / 2 + 6 * u), y: -195 * u)

        let next = ButtonNode(text: isLast ? "Done" : "Next", size: size, fontSize: 17 * u) { [weak self] in
            if isLast {
                close()
            } else {
                self?.overlay = .help(page: index + 1)
                self?.rebuildOverlay()
            }
        }
        add(next, to: panel, x: size.width / 2 + 6 * u, y: -195 * u)
    }

    private func buildSummary(in panel: SKNode, width: CGFloat, points: [Int], winner: Int, m: Metrics) {
        let u = m.u
        let over = match.isOver
        let practicing = practice != nil
        let title: String
        if over {
            let winners = match.winners
            if winners == [0] {
                title = "You win the game!"
            } else if winners.contains(0) {
                title = "You tied for the win"
            } else {
                let list = winners.map { names[$0] }.joined(separator: " & ")
                title = "\(list) \(winners.count == 1 ? "wins" : "tie")"
            }
        } else {
            title = winner == 0 ? "You went out!" : "\(names[winner]) went out"
        }
        add(Style.label(title, size: 24 * u, font: Style.bold, color: Style.gold), to: panel, y: 135 * u)
        let subtitle = practicing
            ? "Practice complete"
            : over
            ? "Game over. Lowest score wins."
            : "Round \(match.roundsPlayed) · game to \(settings.length.targetScore)"
        add(Style.label(subtitle, size: 14 * u, color: SKColor(white: 1, alpha: 0.8)), to: panel, y: 106 * u)

        let left = -width / 2 + 28 * u
        let right = width / 2 - 28 * u
        let header = SKColor(white: 1, alpha: 0.6)
        let roundHeader = Style.label("Round", size: 12 * u, color: header)
        roundHeader.horizontalAlignmentMode = .right
        add(roundHeader, to: panel, x: right - 80 * u, y: 72 * u)
        let totalHeader = Style.label("Total", size: 12 * u, color: header)
        totalHeader.horizontalAlignmentMode = .right
        add(totalHeader, to: panel, x: right, y: 72 * u)

        let best = match.scores.min()
        for player in names.indices {
            let y = (42 - CGFloat(player) * 36) * u
            let leading = match.scores[player] == best
            let color: SKColor = leading ? Style.gold : .white
            let name = Style.label(names[player], size: 18 * u, font: Style.bold, color: color)
            name.horizontalAlignmentMode = .left
            add(name, to: panel, x: left, y: y)
            let gained = Style.label("+\(points[player])", size: 16 * u, color: SKColor(white: 1, alpha: 0.8))
            gained.horizontalAlignmentMode = .right
            add(gained, to: panel, x: right - 80 * u, y: y)
            let total = Style.label("\(match.scores[player])", size: 18 * u, font: Style.bold, color: color)
            total.horizontalAlignmentMode = .right
            add(total, to: panel, x: right, y: y)
        }

        let button = ButtonNode(
            text: practicing ? "Back to menu" : over ? "New game" : "Next round",
            size: CGSize(width: width - 40 * u, height: 50 * u),
            fontSize: 20 * u
        ) { [weak self] in
            guard let self else { return }
            if practicing {
                self.endPractice()
            } else if over {
                self.generation += 1
                self.round = nil
                self.match = Match(settings: self.settings)
                self.eventText = ""
                self.overlay = .menu
                self.sync(animated: false)
                self.refreshHUD()
                self.rebuildOverlay()
            } else {
                self.startRound()
            }
        }
        add(button, to: panel, y: -130 * u)
    }

    private func buildGameMenu(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        add(Style.label("Game menu", size: 24 * u, font: Style.bold, color: Style.gold), to: panel, y: 125 * u)

        let wide = CGSize(width: width - 40 * u, height: 46 * u)
        let back = ButtonNode(text: "Back to game", size: wide, fontSize: 18 * u) { [weak self] in
            self?.keepPlaying()
        }
        add(back, to: panel, y: 66 * u)

        func secondary(_ text: String, y: CGFloat, action: @escaping () -> Void) {
            let button = ButtonNode(
                text: text,
                size: wide,
                fontSize: 17 * u,
                fill: SKColor(white: 1, alpha: 0.12),
                textColor: .white,
                action: action
            )
            add(button, to: panel, y: y)
        }
        secondary(Sounds.shared.isMuted ? "Sound: off" : "Sound: on", y: 10 * u) { [weak self] in
            Sounds.shared.isMuted.toggle()
            self?.rebuildOverlay()
        }
        secondary("How to play", y: -46 * u) { [weak self] in
            self?.overlay = .help(page: 0)
            self?.rebuildOverlay()
        }
        secondary("Quit game", y: -102 * u) { [weak self] in
            self?.overlay = .quitConfirm
            self?.rebuildOverlay()
        }
    }

    private func buildNewGameConfirm(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        add(Style.label("Start a new game?", size: 22 * u, font: Style.bold, color: Style.gold), to: panel, y: 88 * u)

        let counts = (savedGame?.match.roundsPlayed ?? 0) >= 1
        let body = Style.label(
            counts
                ? "Your saved game will go in your history as a game you quit, which counts as finishing last."
                : "Your saved game will be discarded. No round has finished, so it won't count in your history.",
            size: 15 * u
        )
        body.numberOfLines = 0
        body.preferredMaxLayoutWidth = width - 48 * u
        add(body, to: panel, y: 26 * u)

        let size = CGSize(width: (width - 52 * u) / 2, height: 46 * u)
        let cancel = ButtonNode(text: "Cancel", size: size, fontSize: 16 * u) { [weak self] in
            self?.overlay = .menu
            self?.rebuildOverlay()
        }
        add(cancel, to: panel, x: -(size.width / 2 + 6 * u), y: -78 * u)

        let fresh = ButtonNode(
            text: "New game",
            size: size,
            fontSize: 16 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in self?.replaceSavedGameWithNew() }
        add(fresh, to: panel, x: size.width / 2 + 6 * u, y: -78 * u)
    }

    private func buildQuitConfirm(in panel: SKNode, width: CGFloat, m: Metrics) {
        let u = m.u
        add(Style.label("Quit this game?", size: 22 * u, font: Style.bold, color: Style.gold), to: panel, y: 88 * u)

        let body = Style.label(
            quitWillCount
                ? "It will go in your history as a game you quit, which counts as finishing last."
                : "No round has finished yet, so this game won't count in your history.",
            size: 15 * u
        )
        body.numberOfLines = 0
        body.preferredMaxLayoutWidth = width - 48 * u
        add(body, to: panel, y: 26 * u)

        let size = CGSize(width: (width - 52 * u) / 2, height: 46 * u)
        let stay = ButtonNode(text: "Keep playing", size: size, fontSize: 16 * u) { [weak self] in
            self?.keepPlaying()
        }
        add(stay, to: panel, x: -(size.width / 2 + 6 * u), y: -78 * u)

        let leave = ButtonNode(
            text: "Quit game",
            size: size,
            fontSize: 16 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in self?.quitGame() }
        add(leave, to: panel, x: size.width / 2 + 6 * u, y: -78 * u)
    }

    private func buildJokerWarning(in panel: SKNode, width: CGFloat, move: Round.Move, face: Face, m: Metrics) {
        let u = m.u
        add(Style.label("You're holding the \(face.label)", size: 20 * u, font: Style.bold, color: Style.gold),
            to: panel, y: 88 * u)

        let body = Style.label(
            "If the joker takes its place, your \(face.label) can never be played and counts 30 points.",
            size: 15 * u
        )
        body.numberOfLines = 0
        body.preferredMaxLayoutWidth = width - 48 * u
        add(body, to: panel, y: 28 * u)

        let size = CGSize(width: (width - 52 * u) / 2, height: 46 * u)
        let cancel = ButtonNode(
            text: "Cancel",
            size: size,
            fontSize: 17 * u
        ) { [weak self] in
            self?.overlay = nil
            self?.rebuildOverlay()
        }
        add(cancel, to: panel, x: -(size.width / 2 + 6 * u), y: -78 * u)

        let play = ButtonNode(
            text: "Play joker",
            size: size,
            fontSize: 17 * u,
            fill: SKColor(white: 1, alpha: 0.12),
            textColor: .white
        ) { [weak self] in
            self?.overlay = nil
            self?.rebuildOverlay()
            self?.perform(move)
        }
        add(play, to: panel, x: size.width / 2 + 6 * u, y: -78 * u)
    }
}
