import SwiftUI

@main
struct AtifyApp: App {
    @StateObject private var player = AudioPlayer.shared

    var body: some Scene {
        WindowGroup {
            MainTabView(player: player)
                .preferredColorScheme(.dark)
                .environmentObject(player)
        }
    }
}
