import SwiftUI
import UIKit

@MainActor
private final class ArticleImageCache {
    static let shared = ArticleImageCache()

    private let images = NSCache<NSURL, UIImage>()
    private let session: URLSession

    private init() {
        images.countLimit = 80
        images.totalCostLimit = 64 * 1024 * 1024

        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(
            memoryCapacity: 16 * 1024 * 1024,
            diskCapacity: 128 * 1024 * 1024,
            diskPath: "wiki-image-cache"
        )
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.timeoutIntervalForRequest = 20
        session = URLSession(configuration: configuration)
    }

    func cachedImage(for url: URL) -> UIImage? {
        images.object(forKey: url as NSURL)
    }

    func image(for url: URL) async throws -> UIImage {
        if let cached = cachedImage(for: url) {
            return cached
        }

        var request = URLRequest(url: url)
        request.cachePolicy = .returnCacheDataElseLoad
        request.setValue(
            "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)",
            forHTTPHeaderField: "User-Agent"
        )

        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()

        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              let image = UIImage(data: data) else {
            throw URLError(.cannotDecodeContentData)
        }

        let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
        images.setObject(image, forKey: url as NSURL, cost: cost)
        return image
    }
}

/// A small replacement for `AsyncImage` that keeps decoded thumbnails in memory while scrolling
/// and while the reader is re-created. `AsyncImage` re-downloads on view identity changes.
struct CachedAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL
    let content: (Image) -> Content
    let placeholder: () -> Placeholder

    @State private var image: UIImage?

    init(
        url: URL,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
    }

    var body: some View {
        Group {
            if let image {
                content(Image(uiImage: image))
            } else {
                placeholder()
            }
        }
        .task(id: url) {
            await load()
        }
    }

    @MainActor
    private func load() async {
        image = ArticleImageCache.shared.cachedImage(for: url)
        guard image == nil else { return }

        do {
            let loaded = try await ArticleImageCache.shared.image(for: url)
            guard !Task.isCancelled else { return }
            image = loaded
        } catch {
            // Keep the placeholder; the next appearance will retry.
        }
    }
}
