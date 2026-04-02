package eu.kanade.tachiyomi.animeextension.all.slothanime

import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SlothAnime : AnimeHttpSource() {
    override val name = "slothanime"
    override val baseUrl = "https://graphql.anilist.co"
    override val lang = "all"
    override val supportsLatest = true

    override fun popularAnimeRequest(page: Int): Request = aniListPost("""query { Page(page: $page, perPage: 24) { pageInfo { hasNextPage } media(type: ANIME, sort: TRENDING_DESC) { id title { romaji english } coverImage { large } } } }""")
    override fun popularAnimeParse(response: Response): AnimesPage = parseAniListPage(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = aniListPost("""query { Page(page: $page, perPage: 50) { pageInfo { hasNextPage } media(search: "$query", type: ANIME) { id title { romaji english } coverImage { large } } } }""")
    override fun searchAnimeParse(response: Response): AnimesPage = parseAniListPage(response)

    override fun episodeListRequest(anime: SAnime): Request = aniListPost("""query { Media(id: ${anime.url}) { episodes nextAiringEpisode { episode } } }""")
    override fun episodeListParse(response: Response): List<SEpisode> {
        val media = JSONObject(response.body.string()).getJSONObject("data").getJSONObject("Media")
        val total = if (media.isNull("nextAiringEpisode")) media.optInt("episodes", 1) else media.getJSONObject("nextAiringEpisode").getInt("episode") - 1
        return (1..total).map { i -> SEpisode.create().apply { url = "${media.getInt("id")}|$i"; name = "Episode $i"; episode_number = i.toFloat() } }.reversed()
    }

    override fun videoListRequest(episode: SEpisode): Request {
        val (id, ep) = episode.url.split("|")
        return Request.Builder().url("https://vidsrc.cc/v2/embed/anime/ani$id/$ep/sub").build()
    }
    override fun videoListParse(response: Response): List<Video> = listOf(Video(response.request.url.toString(), "VidSrc", response.request.url.toString()))

    private fun aniListPost(query: String): Request = Request.Builder().url(baseUrl).post(JSONObject().put("query", query).toString().toRequestBody("application/json".toMediaType())).build()

    private fun parseAniListPage(response: Response): AnimesPage {
        val data = JSONObject(response.body.string()).getJSONObject("data").getJSONObject("Page")
        val media = data.getJSONArray("media")
        val list = (0 until media.length()).map { i ->
            val item = media.getJSONObject(i)
            SAnime.create().apply {
                title = item.getJSONObject("title").let { it.optString("english").ifBlank { it.getString("romaji") } }
                thumbnail_url = item.getJSONObject("coverImage").getString("large")
                url = item.getInt("id").toString()
            }
        }
        return AnimesPage(list, data.getJSONObject("pageInfo").getBoolean("hasNextPage"))
    }

    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)
    override fun animeDetailsParse(response: Response) = SAnime.create()
}
