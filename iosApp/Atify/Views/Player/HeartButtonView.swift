import SwiftUI

public struct HeartButtonView: View {
    public let isLiked: Bool
    public let size: CGFloat
    public let onToggle: () -> Void

    @State private var scale: CGFloat = 1.0

    public init(isLiked: Bool, size: CGFloat = 22, onToggle: @escaping () -> Void) {
        self.isLiked = isLiked
        self.size = size
        self.onToggle = onToggle
    }

    public var body: some View {
        Button(action: {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()

            withAnimation(.spring(response: 0.28, dampingFraction: 0.52)) {
                scale = isLiked ? 0.84 : 1.36
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                    scale = 1.0
                }
            }
            onToggle()
        }) {
            Image(systemName: isLiked ? "heart.fill" : "heart")
                .font(.system(size: size, weight: .medium))
                .foregroundColor(isLiked ? AtifyTheme.spotifyGreen : .white)
                .scaleEffect(scale)
        }
        .buttonStyle(.plain)
    }
}
