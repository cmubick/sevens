//
//  Nodes.swift
//  sevens
//
//  The SpriteKit building blocks: cards, buttons, opponent badges and joker targets.
//

import SpriteKit

enum Style {
    static let felt = SKColor(red: 0.05, green: 0.30, blue: 0.20, alpha: 1)
    static let panel = SKColor(red: 0.04, green: 0.22, blue: 0.15, alpha: 1)
    static let gold = SKColor(red: 1.00, green: 0.82, blue: 0.30, alpha: 1)
    static let red = SKColor(red: 0.80, green: 0.13, blue: 0.15, alpha: 1)
    static let ink = SKColor(red: 0.10, green: 0.10, blue: 0.12, alpha: 1)
    static let joker = SKColor(red: 0.45, green: 0.22, blue: 0.70, alpha: 1)
    static let cardFace = SKColor(red: 0.99, green: 0.98, blue: 0.95, alpha: 1)

    static let regular = "AvenirNext-Medium"
    static let bold = "AvenirNext-Bold"

    static func label(_ text: String, size: CGFloat, font: String = Style.regular, color: SKColor = .white) -> SKLabelNode {
        let label = SKLabelNode(fontNamed: font)
        label.text = text
        label.fontSize = size
        label.fontColor = color
        label.verticalAlignmentMode = .center
        label.horizontalAlignmentMode = .center
        return label
    }
}

extension Suit {
    /// The suit symbol forced to plain text so it never renders as an emoji.
    var glyph: String { symbol + "\u{FE0E}" }

    var color: SKColor {
        switch self {
        case .spades, .clubs: Style.ink
        case .hearts, .diamonds: Style.red
        }
    }

    /// Left-to-right order on the table and in the hand, alternating colors.
    static let displayOrder: [Suit] = [.spades, .hearts, .clubs, .diamonds]
}

extension Face {
    var label: String { rankName + suit.glyph }
}

extension Card {
    var label: String {
        switch self {
        case .face(let face): face.label
        case .joker: "Joker"
        }
    }
}

final class CardNode: SKNode {
    static let size = CGSize(width: 60, height: 84)

    let card: Card
    private let body = SKShapeNode(rectOf: CardNode.size, cornerRadius: 7)
    private let shade = SKShapeNode(rectOf: CardNode.size, cornerRadius: 7)
    private let corner = Style.label("", size: 17, font: Style.bold)
    private let center = Style.label("", size: 36)
    private let tag = Style.label("", size: 9.5, font: Style.bold)

    /// For a joker on the table: the card it is standing in for.
    var standIn: Face? { didSet { if standIn != oldValue { refresh() } } }
    /// A card a joker has replaced: unplayable and worth 30.
    var isDead = false { didSet { if isDead != oldValue { refresh() } } }
    var isDimmed = false { didSet { shade.isHidden = !isDimmed } }
    var isSelected = false { didSet { if isSelected != oldValue { refresh() } } }

    init(card: Card) {
        self.card = card
        super.init()

        body.fillColor = Style.cardFace
        body.lineWidth = 1
        addChild(body)

        corner.horizontalAlignmentMode = .left
        corner.verticalAlignmentMode = .top
        corner.position = CGPoint(x: -CardNode.size.width / 2 + 5, y: CardNode.size.height / 2 - 5)
        corner.zPosition = 1
        addChild(corner)

        center.position = CGPoint(x: 0, y: -10)
        center.zPosition = 1
        addChild(center)

        tag.position = CGPoint(x: 0, y: -CardNode.size.height / 2 + 10)
        tag.zPosition = 1
        addChild(tag)

        shade.fillColor = SKColor(white: 0, alpha: 0.38)
        shade.strokeColor = .clear
        shade.zPosition = 2
        shade.isHidden = true
        addChild(shade)

        refresh()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func refresh() {
        body.strokeColor = isSelected ? Style.gold : SKColor(white: 0, alpha: 0.35)
        body.lineWidth = isSelected ? 3 : 1
        tag.text = ""

        switch card {
        case .face(let face):
            let color = isDead ? SKColor(white: 0.55, alpha: 1) : face.suit.color
            corner.text = face.label
            corner.fontColor = color
            center.text = face.suit.glyph
            center.fontColor = color
            if isDead {
                tag.text = "DEAD · 30"
                tag.fontColor = Style.red
            }
        case .joker:
            corner.fontColor = Style.joker
            center.fontColor = Style.joker
            center.text = "★"
            if let standIn {
                corner.text = standIn.label + "★"
                tag.text = "JOKER"
            } else {
                corner.text = "★"
                tag.text = "JOKER · 30"
            }
            tag.fontColor = Style.joker
        }
    }
}

final class ButtonNode: SKNode {
    let action: () -> Void

    init(
        text: String,
        size: CGSize,
        fontSize: CGFloat,
        fill: SKColor = Style.gold,
        textColor: SKColor = Style.ink,
        action: @escaping () -> Void
    ) {
        self.action = action
        super.init()

        let shape = SKShapeNode(rectOf: size, cornerRadius: min(size.height / 2, 12))
        shape.fillColor = fill
        shape.strokeColor = SKColor(white: 1, alpha: 0.35)
        shape.lineWidth = 1
        addChild(shape)

        let label = Style.label(text, size: fontSize, font: Style.bold, color: textColor)
        label.zPosition = 1
        addChild(label)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

/// A highlighted spot on the table where the selected joker can go.
final class TargetNode: SKNode {
    let face: Face

    init(face: Face, size: CGSize, scale: CGFloat) {
        self.face = face
        super.init()

        let shape = SKShapeNode(rectOf: size, cornerRadius: 6 * scale)
        shape.fillColor = SKColor(red: 0.35, green: 0.33, blue: 0.10, alpha: 0.92)
        shape.strokeColor = Style.gold
        shape.lineWidth = 2.5
        addChild(shape)

        let label = Style.label(face.label, size: 16 * scale, font: Style.bold, color: .white)
        label.zPosition = 1
        addChild(label)

        run(.repeatForever(.sequence([
            .fadeAlpha(to: 0.55, duration: 0.6),
            .fadeAlpha(to: 1.0, duration: 0.6),
        ])))
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

/// An opponent's name, card count and score at the top of the table.
final class BadgeNode: SKNode {
    private let shape: SKShapeNode
    private let nameLabel: SKLabelNode
    private let detailLabel: SKLabelNode

    init(size: CGSize, unit u: CGFloat) {
        shape = SKShapeNode(rectOf: size, cornerRadius: 10 * u)
        nameLabel = Style.label("", size: 15 * u, font: Style.bold)
        detailLabel = Style.label("", size: 12 * u, color: SKColor(white: 1, alpha: 0.8))
        super.init()

        shape.fillColor = SKColor(white: 0, alpha: 0.25)
        addChild(shape)
        nameLabel.position = CGPoint(x: 0, y: 9 * u)
        detailLabel.position = CGPoint(x: 0, y: -10 * u)
        addChild(nameLabel)
        addChild(detailLabel)
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func update(name: String, cards: Int?, score: Int, active: Bool) {
        nameLabel.text = name
        nameLabel.fontColor = active ? Style.gold : .white
        if let cards {
            detailLabel.text = "\(cards) card\(cards == 1 ? "" : "s") · \(score) pts"
        } else {
            detailLabel.text = "\(score) pts"
        }
        shape.strokeColor = active ? Style.gold : SKColor(white: 1, alpha: 0.2)
        shape.lineWidth = active ? 2.5 : 1
    }
}
