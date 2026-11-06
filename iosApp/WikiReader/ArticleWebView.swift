import SwiftUI
import UIKit
import WebKit

/// Wikipedia's mobile article page, including its tables, references and collapsible sections.
struct ArticleWebView: UIViewRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.allowsBackForwardNavigationGestures = true
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if webView.url == nil {
            webView.load(URLRequest(url: url))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            // Keep the reader inside the app; hand non-Wikipedia links to Safari.
            if let host = navigationAction.request.url?.host,
               !host.hasSuffix("wikipedia.org") {
                decisionHandler(.cancel)
                if let url = navigationAction.request.url {
                    UIApplication.shared.open(url)
                }
                return
            }
            decisionHandler(.allow)
        }
    }
}
