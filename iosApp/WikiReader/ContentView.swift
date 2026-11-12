import SwiftUI

struct ContentView: View {
    @StateObject private var model = ReaderModel()

    private let languages: [(code: String, name: String)] = [
        ("en", "English"),
        ("de", "Deutsch"),
        ("es", "Español"),
        ("fr", "Français"),
        ("it", "Italiano"),
        ("ja", "日本語"),
        ("pt", "Português"),
        ("ru", "Русский"),
        ("zh", "中文"),
        ("ar", "العربية")
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let article = model.article {
                        articleView(article)
                    } else if model.isLoading {
                        ProgressView("Finding a random article…")
                            .frame(maxWidth: .infinity, minHeight: 320)
                    } else if let error = model.errorMessage {
                        VStack(spacing: 16) {
                            messageView(
                                title: "Could not load Wikipedia",
                                systemImage: "wifi.exclamationmark",
                                description: error
                            )
                            Button("Try again") {
                                Task { await model.loadRandom() }
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    } else {
                        messageView(
                            title: "No article yet",
                            systemImage: "book",
                            description: "Tap the shuffle button to start reading."
                        )
                    }
                }
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("WikiRandomizer-P")
            .overlay(alignment: .topTrailing) {
                if model.isLoading, model.article != nil {
                    ProgressView()
                        .controlSize(.small)
                        .padding(12)
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        ForEach(languages, id: \.code) { language in
                            Button {
                                model.language = language.code
                            } label: {
                                if model.language == language.code {
                                    Label(language.name, systemImage: "checkmark")
                                } else {
                                    Text(language.name)
                                }
                            }
                        }
                    } label: {
                        Label("Language", systemImage: "globe")
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task { await model.loadRandom() }
                    } label: {
                        Label("Random article", systemImage: "shuffle")
                    }
                    .disabled(model.isLoading)
                }
            }
            .refreshable {
                await model.loadRandom()
            }
            .task {
                if model.article == nil {
                    await model.loadRandom()
                }
            }
        }
    }

    private func messageView(title: String, systemImage: String, description: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 42))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.title3.bold())
            Text(description)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 320)
        .padding()
    }

    @ViewBuilder
    private func articleView(_ article: ArticleSummary) -> some View {
        Text(article.title)
            .font(.largeTitle.bold())
            .textSelection(.enabled)

        if let description = article.summaryDescription, !description.isEmpty {
            Text(description)
                .font(.title3)
                .foregroundStyle(.secondary)
        }

        let minutes = model.readingMinutes(for: article)
        if minutes > 0 {
            Label("\(minutes) min read", systemImage: "clock")
                .font(.caption)
                .foregroundStyle(.secondary)
        }

        if let thumbnail = article.thumbnail,
           let url = URL(string: thumbnail.source) {
            CachedAsyncImage(url: url) { image in
                image
                    .resizable()
                    .scaledToFill()
            } placeholder: {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 240)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 240)
            .clipped()
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }

        Text(article.extract)
            .font(.body)
            .textSelection(.enabled)

        if let url = model.articleURL(for: article) {
            NavigationLink {
                ArticleWebView(url: url)
                    .navigationTitle(article.title)
                    .navigationBarTitleDisplayMode(.inline)
            } label: {
                Label("Read the full article", systemImage: "book.pages")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }

        if let share = model.shareURL(for: article) {
            ShareLink(item: share) {
                Label("Share", systemImage: "square.and.arrow.up")
            }
            .buttonStyle(.bordered)
        }
    }
}
