import Foundation

struct ArticleSummary: Decodable, Identifiable {
    let pageid: Int?
    let title: String
    let description: String?
    let extract: String
    let thumbnail: Thumbnail?
    let contentUrls: ContentUrls?

    var id: String { pageid.map(String.init) ?? title }

    struct Thumbnail: Decodable {
        let source: String
        let width: Int?
        let height: Int?
    }

    struct ContentUrls: Decodable {
        let desktop: Page?
        let mobile: Page?

        struct Page: Decodable {
            let page: String?
        }
    }
}
