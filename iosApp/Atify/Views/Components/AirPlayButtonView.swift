import SwiftUI
import AVKit

public struct AirPlayButtonView: UIViewRepresentable {
    public init() {}

    public func makeUIView(context: Context) -> AVRoutePickerView {
        let routePickerView = AVRoutePickerView()
        routePickerView.tintColor = .white
        routePickerView.activeTintColor = UIColor(red: 0.118, green: 0.843, blue: 0.376, alpha: 1.0)
        routePickerView.prioritizesVideoDevices = false
        routePickerView.backgroundColor = .clear
        return routePickerView
    }

    public func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
