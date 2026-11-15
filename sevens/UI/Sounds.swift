//
//  Sounds.swift
//  sevens
//
//  Short sound effects, generated in code so the app ships no audio files.
//

import AVFoundation

nonisolated enum Sound: CaseIterable, Sendable {
    /// A card landing on the table.
    case card
    /// One card flicked out during the deal.
    case deal
    /// A button press.
    case tap
    /// A player asking for a card.
    case ask
    case joker
    /// A move that isn't allowed, or bad news.
    case warning
    /// Going out first in a round.
    case win
    /// Winning the whole game.
    case bigWin
}

/// Builds each sound as 16-bit mono WAV data.
nonisolated enum SoundSynth {
    static let sampleRate = 44_100.0

    /// A sine tone that fades in quickly and dies away. `decay` is the time to fall to about a third.
    static func tone(_ frequency: Double, seconds: Double, decay: Double, level: Double) -> [Double] {
        let count = Int(seconds * sampleRate)
        return (0..<count).map { index in
            let t = Double(index) / sampleRate
            let attack = min(t / 0.004, 1)
            return sin(2 * .pi * frequency * t) * attack * exp(-t / decay) * level
        }
    }

    /// A tone that glides from one pitch to another.
    static func sweep(from start: Double, to end: Double, seconds: Double, level: Double) -> [Double] {
        let count = Int(seconds * sampleRate)
        var phase = 0.0
        return (0..<count).map { index in
            let progress = Double(index) / Double(count)
            let t = Double(index) / sampleRate
            phase += 2 * .pi * (start + (end - start) * progress) / sampleRate
            return sin(phase) * min(t / 0.004, 1) * (1 - progress) * level
        }
    }

    /// A soft burst of filtered noise: the papery part of a card sound.
    static func noise(seconds: Double, decay: Double, smoothing: Double, level: Double, seed: UInt64) -> [Double] {
        var generator = SeededGenerator(seed: seed)
        let count = Int(seconds * sampleRate)
        var previous = 0.0
        return (0..<count).map { index in
            let t = Double(index) / sampleRate
            let raw = Double.random(in: -1...1, using: &generator)
            previous += smoothing * (raw - previous)
            return previous * min(t / 0.002, 1) * exp(-t / decay) * level
        }
    }

    /// Adds two sounds together, sample by sample.
    static func mix(_ a: [Double], _ b: [Double]) -> [Double] {
        let count = max(a.count, b.count)
        return (0..<count).map { (index: Int) -> Double in
            (index < a.count ? a[index] : 0) + (index < b.count ? b[index] : 0)
        }
    }

    static func samples(for sound: Sound) -> [Double] {
        switch sound {
        case .card:
            return mix(noise(seconds: 0.08, decay: 0.016, smoothing: 0.35, level: 0.9, seed: 1),
                       tone(170, seconds: 0.06, decay: 0.02, level: 0.35))
        case .deal:
            return noise(seconds: 0.05, decay: 0.010, smoothing: 0.5, level: 0.6, seed: 2)
        case .tap:
            return tone(880, seconds: 0.035, decay: 0.009, level: 0.35)
        case .ask:
            return tone(523, seconds: 0.09, decay: 0.05, level: 0.35)
                + tone(392, seconds: 0.14, decay: 0.06, level: 0.35)
        case .joker:
            return mix(sweep(from: 420, to: 940, seconds: 0.18, level: 0.4),
                       [Double](repeating: 0, count: Int(0.1 * sampleRate))
                           + tone(1320, seconds: 0.14, decay: 0.05, level: 0.25))
        case .warning:
            return mix(tone(150, seconds: 0.2, decay: 0.06, level: 0.5),
                       tone(112, seconds: 0.2, decay: 0.06, level: 0.35))
        case .win:
            return tone(523, seconds: 0.11, decay: 0.07, level: 0.4)
                + tone(659, seconds: 0.11, decay: 0.07, level: 0.4)
                + tone(784, seconds: 0.30, decay: 0.12, level: 0.4)
        case .bigWin:
            let run = tone(523, seconds: 0.12, decay: 0.08, level: 0.38)
                + tone(659, seconds: 0.12, decay: 0.08, level: 0.38)
                + tone(784, seconds: 0.12, decay: 0.08, level: 0.38)
            let chord = mix(mix(tone(523, seconds: 0.7, decay: 0.25, level: 0.22),
                                tone(784, seconds: 0.7, decay: 0.25, level: 0.22)),
                            tone(1047, seconds: 0.7, decay: 0.25, level: 0.26))
            return run + chord
        }
    }

    /// Wraps samples in a WAV file header.
    static func wav(for sound: Sound) -> Data {
        let pcm = samples(for: sound).map { Int16(max(-1, min(1, $0)) * 32_000) }
        let byteCount = pcm.count * 2
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) {
            withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
        }
        data.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36 + byteCount))
        data.append(contentsOf: Array("WAVE".utf8))
        data.append(contentsOf: Array("fmt ".utf8))
        append(UInt32(16))                    // size of the format block
        append(UInt16(1))                     // PCM
        append(UInt16(1))                     // mono
        append(UInt32(sampleRate))
        append(UInt32(sampleRate) * 2)        // bytes per second
        append(UInt16(2))                     // bytes per sample
        append(UInt16(16))                    // bits per sample
        data.append(contentsOf: Array("data".utf8))
        append(UInt32(byteCount))
        for sample in pcm { append(sample) }
        return data
    }
}

/// Plays the sound effects. Muting is remembered between launches.
final class Sounds {
    static let shared = Sounds()
    private static let mutedKey = "soundMuted"

    private var players: [Sound: AVAudioPlayer] = [:]

    var isMuted: Bool {
        didSet { UserDefaults.standard.set(isMuted, forKey: Sounds.mutedKey) }
    }

    private init() {
        isMuted = UserDefaults.standard.bool(forKey: Sounds.mutedKey)
        // Ambient: the silent switch mutes the game, and the player's own music keeps playing.
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
        for sound in Sound.allCases {
            guard let player = try? AVAudioPlayer(data: SoundSynth.wav(for: sound)) else { continue }
            player.volume = 0.7
            player.prepareToPlay()
            players[sound] = player
        }
    }

    func play(_ sound: Sound) {
        guard !isMuted, let player = players[sound] else { return }
        player.currentTime = 0
        player.play()
    }
}
