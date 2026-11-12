import Foundation
import shared

private enum WikiNetworking {
    static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(
            memoryCapacity: 16 * 1024 * 1024,
            diskCapacity: 128 * 1024 * 1024,
            diskPath: "wiki-summary-cache"
        )
        configuration.requestCachePolicy = .useProtocolCachePolicy
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration)
    }()

    static let userAgent =
        "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)"

    static func fetchRandomArticle(language: String) async throws -> ArticleSummary {
        let urlString = WikiUrls.shared.randomSummaryUrl(lang: language)
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        // A random endpoint must never return an old cached article.
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()

        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(ArticleSummary.self, from: data)
    }
}

@MainActor
final class ReaderModel: ObservableObject {
    @Published private(set) var article: ArticleSummary?
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?
    @Published var language: String {
        didSet {
            guard language != oldValue else { return }
            UserDefaults.standard.set(language, forKey: Self.languageKey)
            Task { [weak self] in
                await self?.loadRandom()
            }
        }
    }

    private static let languageKey = "wikiLanguage"
    private var activeRequest: Task<ArticleSummary, Error>?
    private var requestGeneration = 0

    init() {
        language = UserDefaults.standard.string(forKey: Self.languageKey) ?? "en"
    }

    func loadRandom() async {
        activeRequest?.cancel()
        requestGeneration += 1
        let generation = requestGeneration
        let requestedLanguage = language

        isLoading = true
        errorMessage = nil

        let request = Task {
            try await WikiNetworking.fetchRandomArticle(language: requestedLanguage)
        }
        activeRequest = request

        defer {
            if generation == requestGeneration {
                isLoading = false
                activeRequest = nil
            }
        }

        do {
            let article = try await withTaskCancellationHandler {
                try await request.value
            } onCancel: {
                request.cancel()
            }

            guard generation == requestGeneration else { return }
            self.article = article
        } catch is CancellationError {
            // Superseded by a newer request, a language change, or a dismissed view.
        } catch let error as URLError where error.code == .cancelled {
            // URLSession reports task cancellation as a cancelled URLError.
        } catch {
            guard generation == requestGeneration else { return }
            errorMessage = error.localizedDescription
        }
    }

    func articleURL(for article: ArticleSummary) -> URL? {
        URL(string: WikiUrls.shared.mobileArticleUrl(lang: language, title: article.title))
    }

    func shareURL(for article: ArticleSummary) -> URL? {
        URL(string: WikiUrls.shared.articleUrl(lang: language, title: article.title))
    }

    func readingMinutes(for article: ArticleSummary) -> Int {
        Int(WikiText.shared.readingMinutes(text: article.extract, wordsPerMinute: 200))
    }
}
