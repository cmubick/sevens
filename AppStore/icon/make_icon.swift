import AppKit
import ImageIO

// Draws the Sevens app icon: a 7 of spades on green felt.
// usage: swift make_icon.swift <out.png> <light|dark|tinted>
let args = CommandLine.arguments
let out = args[1]
let mode = args.count > 2 ? args[2] : "light"
let side = 1024

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: side, pixelsHigh: side,
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: side, height: side)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
let ctx = NSGraphicsContext.current!.cgContext
let full = CGRect(x: 0, y: 0, width: side, height: side)

func color(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) -> NSColor {
    NSColor(deviceRed: r, green: g, blue: b, alpha: a)
}

// Background.
if mode == "tinted" {
    color(0, 0, 0).setFill(); full.fill()
} else {
    let inner = mode == "dark" ? color(0.05, 0.20, 0.14) : color(0.09, 0.42, 0.28)
    let outer = mode == "dark" ? color(0.01, 0.07, 0.05) : color(0.03, 0.22, 0.15)
    let gradient = NSGradient(starting: inner, ending: outer)!
    gradient.draw(fromCenter: CGPoint(x: 512, y: 600), radius: 0,
                  toCenter: CGPoint(x: 512, y: 512), radius: 760, options: [.drawsAfterEndingLocation])
}

// The card, tilted a little.
let cardFill = mode == "tinted" ? color(1, 1, 1) : color(0.99, 0.98, 0.95)
let ink = mode == "tinted" ? color(0, 0, 0) : color(0.10, 0.10, 0.12)
let gold = mode == "tinted" ? color(0.75, 0.75, 0.75) : color(1.0, 0.82, 0.30)

ctx.saveGState()
ctx.translateBy(x: 512, y: 512)
ctx.rotate(by: -8 * .pi / 180)
let card = CGRect(x: -270, y: -370, width: 540, height: 740)
if mode != "tinted" {
    ctx.setShadow(offset: CGSize(width: 0, height: -18), blur: 50, color: color(0, 0, 0, 0.45).cgColor)
}
let path = NSBezierPath(roundedRect: card, xRadius: 64, yRadius: 64)
cardFill.setFill(); path.fill()
ctx.setShadow(offset: .zero, blur: 0, color: nil)
gold.setStroke(); path.lineWidth = 14; path.stroke()

func draw(_ text: String, font: NSFont, color: NSColor, center: CGPoint) {
    let string = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color])
    let size = string.size()
    string.draw(at: CGPoint(x: center.x - size.width / 2, y: center.y - size.height / 2))
}
let bold = NSFont(name: "AvenirNext-Heavy", size: 400) ?? NSFont.boldSystemFont(ofSize: 400)
draw("7", font: bold, color: ink, center: CGPoint(x: 0, y: 110))
draw("\u{2660}\u{FE0E}", font: NSFont.systemFont(ofSize: 300), color: ink, center: CGPoint(x: 0, y: -195))
ctx.restoreGState()
NSGraphicsContext.restoreGraphicsState()

// Flatten onto an opaque bitmap: App Store icons may not have an alpha channel.
let space = CGColorSpace(name: CGColorSpace.sRGB)!
let opaque = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
                       space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
opaque.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
opaque.fill(full)
opaque.draw(rep.cgImage!, in: full)
let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath: out) as CFURL, "public.png" as CFString, 1, nil)!
CGImageDestinationAddImage(destination, opaque.makeImage()!, nil)
CGImageDestinationFinalize(destination)
