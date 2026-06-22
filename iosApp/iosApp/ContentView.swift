import SwiftUI
import UIKit
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Draw edge-to-edge so Compose owns every window inset (status bar, home indicator,
            // keyboard) exactly once via WindowInsets. The shared shell's M3 TopAppBar/NavigationBar
            // already apply systemBars insets; letting SwiftUI ALSO inset the surface double-counts
            // the status bar and renders the top bar at ~2x height with its content pushed down.
            .ignoresSafeArea()
    }
}
