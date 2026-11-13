import SwiftUI
import UIKit
import shared

/// Hosts the shared Compose Multiplatform reader. The old SwiftUI reader remains in the
/// project as a reference while the remaining screens move into shared/commonMain.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ReaderViewControllerFactory().makeReaderViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}
