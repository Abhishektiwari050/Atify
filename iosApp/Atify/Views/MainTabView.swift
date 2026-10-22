import SwiftUI

public struct MainTabView: View {
    @ObservedObject var player: AudioPlayer
    @State private var selectedTab = 0
    @State private var isNowPlayingExpanded = false

    public init(player: AudioPlayer) {
        self.player = player
        customizeTabBarAppearance()
    }

    private func customizeTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0.07, green: 0.07, blue: 0.07, alpha: 1.0)

        // Normal items
        appearance.stackedLayoutAppearance.normal.iconColor = UIColor(white: 0.6, alpha: 1.0)
        appearance.stackedLayoutAppearance.normal.titleTextAttributes = [
            .foregroundColor: UIColor(white: 0.6, alpha: 1.0)
        ]

        // Selected items
        appearance.stackedLayoutAppearance.selected.iconColor = .white
        appearance.stackedLayoutAppearance.selected.titleTextAttributes = [
            .foregroundColor: UIColor.white
        ]

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                HomeScreen(player: player)
                    .tabItem {
                        Image(systemName: "house.fill")
                        Text("Home")
                    }
                    .tag(0)

                SearchScreen(player: player)
                    .tabItem {
                        Image(systemName: "magnifyingglass")
                        Text("Search")
                    }
                    .tag(1)

                LibraryScreen(player: player)
                    .tabItem {
                        Image(systemName: "books.vertical.fill")
                        Text("Your Library")
                    }
                    .tag(2)
            }
            .accentColor(.white)

            // Floating MiniPlayer overlay right above the TabBar
            if player.currentTrack != nil {
                VStack(spacing: 0) {
                    MiniPlayerView(player: player) {
                        isNowPlayingExpanded = true
                    }
                    .padding(.bottom, 54) // offset above tab bar
                }
            }
        }
        .fullScreenCover(isPresented: $isNowPlayingExpanded) {
            NowPlayingView(player: player)
        }
    }
}
