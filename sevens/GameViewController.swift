//
//  GameViewController.swift
//  sevens
//
//  Created by Chris Ubick on 11/12/26.
//

import UIKit
import SpriteKit

class GameViewController: UIViewController {

    private var gameScene: GameScene?

    override func viewDidLoad() {
        super.viewDidLoad()

        guard let view = self.view as? SKView else { return }
        let scene = GameScene(size: view.bounds.size)
        scene.scaleMode = .resizeFill
        scene.safeInsets = view.safeAreaInsets
        view.presentScene(scene)
        gameScene = scene
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        gameScene?.safeInsets = view.safeAreaInsets
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        if UIDevice.current.userInterfaceIdiom == .phone {
            return .portrait
        } else {
            return .all
        }
    }

    override var prefersStatusBarHidden: Bool {
        return true
    }
}
