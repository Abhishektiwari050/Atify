import SwiftUI

public struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var prefs = PreferencesService.shared
    @State private var cookieText: String = ""
    @State private var showSavedAlert = false

    public init() {}

    public var body: some View {
        NavigationView {
            ZStack {
                AtifyTheme.background.ignoresSafeArea()

                Form {
                    Section(header: Text("Spotify Integration").foregroundColor(AtifyTheme.textSecondary)) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Spotify 'sp_dc' Cookie")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)

                            Text("Optional. Allows fetching personalized recommendations, private playlists, and liked songs from your Spotify account.")
                                .font(.system(size: 12))
                                .foregroundColor(AtifyTheme.textSecondary)

                            HStack {
                                SecureField("Paste sp_dc cookie...", text: $cookieText)
                                    .textFieldStyle(.plain)
                                    .padding(8)
                                    .background(AtifyTheme.surface)
                                    .cornerRadius(6)
                                    .foregroundColor(.white)

                                Button(action: {
                                    if let paste = UIPasteboard.general.string {
                                        cookieText = paste.trimmingCharacters(in: .whitespacesAndNewlines)
                                    }
                                }) {
                                    Image(systemName: "doc.on.clipboard")
                                        .foregroundColor(AtifyTheme.spotifyGreen)
                                }
                            }

                            Button(action: {
                                prefs.saveSpotifyCookie(cookieText)
                                showSavedAlert = true
                            }) {
                                Text("Save Cookie")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.black)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(AtifyTheme.spotifyGreen)
                                    .cornerRadius(6)
                            }
                            .padding(.top, 4)
                        }
                        .padding(.vertical, 4)
                        .listRowBackground(AtifyTheme.card)
                    }

                    Section(header: Text("Storage & Cache").foregroundColor(AtifyTheme.textSecondary)) {
                        Button(action: {
                            InnerTubeService.shared.clearCache()
                            let impact = UINotificationFeedbackGenerator()
                            impact.notificationOccurred(.success)
                        }) {
                            HStack {
                                Text("Clear Stream URL Cache")
                                    .foregroundColor(.white)
                                Spacer()
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                            }
                        }
                        .listRowBackground(AtifyTheme.card)
                    }

                    Section(header: Text("About").foregroundColor(AtifyTheme.textSecondary)) {
                        HStack {
                            Text("Atify iOS")
                                .foregroundColor(.white)
                            Spacer()
                            Text("v1.4.11")
                                .foregroundColor(AtifyTheme.textSecondary)
                        }
                        .listRowBackground(AtifyTheme.card)

                        HStack {
                            Text("Playback Engine")
                                .foregroundColor(.white)
                            Spacer()
                            Text("InnerTube + AVFoundation")
                                .foregroundColor(AtifyTheme.textSecondary)
                        }
                        .listRowBackground(AtifyTheme.card)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundColor(AtifyTheme.spotifyGreen)
                }
            }
            .onAppear {
                cookieText = prefs.spotifyCookie
            }
            .alert("Settings Saved", isPresented: $showSavedAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Spotify authentication cookie updated.")
            }
        }
    }
}
