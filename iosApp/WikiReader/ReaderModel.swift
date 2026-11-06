import Foundation
import shared

@MainActor
final class ReaderModel: ObservableObject {
    @Published var article: ArticleSummary?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var language: String {
        didSet {
            UserDefaults.standard.set(language, forKey: Self.languageKey)
            Task { await loadRandom() }
        }
    }

    private static let languageKey = "wikiLanguage"
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    init() {
        language = UserDefaults.standard.string(forKey: Self.languageKey) ?? "en"
    }

    func loadRandom() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let urlString = WikiUrls.shared.randomSummaryUrl(lang: language)
            guard let url = URL(string: urlString) else { throw URLError(.badURL) }

            var request = URLRequest(url: url)
            request.setValue(
                "WikiReader-iOS/1.0 (https://github.com/PatrickJamesDev/WikiRandomizer-P)",
                forHTTPHeaderField: "User-Agent"
            )

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode) else {
                throw URLError(.badServerResponse)
            }
            article = try decoder.decode(ArticleSummary.self, from: data)
        } catch is CancellationError {
            // A newer random request replaced this one.
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func articleURL(for article: ArticleSummary) -> URL? {
        URL(string: WikiUrls.shared.mobileArticleUrl(lang: language, title: article.title))
    }

    func shareURL(for article: ArticleSummary) -> URL? {
        URL(string: WikiUrls.shared.articleUrl(lang: language, title: article.title))
    }
}
