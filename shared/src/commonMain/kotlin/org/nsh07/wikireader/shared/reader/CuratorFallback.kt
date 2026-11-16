package org.nsh07.wikireader.shared.reader

import com.wikirandomizerp.app.data.CuratorFeed
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Bundled copy of curators.json for the first launch and offline use on Android and iOS. */
fun fallbackCuratorLoad(): CuratorLoad = CuratorLoad(
    feed = Json { ignoreUnknownKeys = true }.decodeFromString<CuratorFeed>(FALLBACK_CURATORS),
    fromFallback = true
)

internal const val FALLBACK_CURATORS = """
{
  "version": 1,
  "curators": [
    {
      "id": "patrickjames",
      "name": "Patrick James",
      "bio": "Builds WikiRandomizer-P. Likes plants, odd biology and articles that start with a rabbit hole.",
      "github": "https://github.com/PatrickJamesDev",
      "picks": [
        {"title":"Athyriaceae","lang":"en","note":"A fern family with a surprisingly readable phylogenetic tree."},
        {"title":"Chasmogamy","lang":"en","note":"A small botanical concept that explains a lot about flowers."},
        {"title":"Solanum","lang":"en","note":"Nightshades, tomatoes and a genus that keeps surprising people."}
      ]
    },
    {
      "id": "wikirandomizer-community",
      "name": "WikiRandomizer community",
      "bio": "A rotating list of reader picks. Add yours with a pull request to curators.json.",
      "github": "https://github.com/PatrickJamesDev/WikiRandomizer-P",
      "picks": [
        {"title":"Poaceae","lang":"en","note":"Grasses, cereals and the family that feeds most of the world."},
        {"title":"History of tea","lang":"en","note":"Trade, empire and a drink that changed daily life."}
      ]
    }
  ]
}
"""
