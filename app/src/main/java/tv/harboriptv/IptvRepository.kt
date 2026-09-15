package tv.harboriptv

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val url: String,
    val logo: String = "",
    val tvgId: String = "",
    val number: Int = 0
)

data class EpgEntry(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String = ""
)

object IptvRepository {
    fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "HarborTV/0.2")
        return connection.inputStream.bufferedReader().use { it.readText() }
            .also { connection.disconnect() }
    }

    fun parseM3u(text: String): List<Channel> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val result = mutableListOf<Channel>()
        var pending = emptyMap<String, String>()
        var pendingName = ""
        var index = 1
        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = Regex("([\\w-]+)=(\\\"([^\\\"]*)\\\"|([^,\\s]*))").findAll(line)
                    .associate { it.groupValues[1] to (it.groupValues[3].ifEmpty { it.groupValues[2] }) }
                pending = attrs
                pendingName = line.substringAfterLast(",", "Channel $index").trim()
            } else if (!line.startsWith("#")) {
                result += Channel(
                    id = pending["tvg-id"].orEmpty().ifEmpty { "ch-$index" },
                    name = pendingName.ifEmpty { "Channel $index" },
                    group = pending["group-title"].orEmpty().ifEmpty { "All Channels" },
                    url = line,
                    logo = pending["tvg-logo"].orEmpty(),
                    tvgId = pending["tvg-id"].orEmpty(),
                    number = index++
                )
                pending = emptyMap()
            }
        }
        return result
    }

    fun loadXtream(baseUrl: String, username: String, password: String): List<Channel> {
        val base = baseUrl.trimEnd('/')
        val api = "$base/player_api.php?username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}&action=get_live_streams"
        val json = org.json.JSONArray(fetchText(api))
        return buildList {
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                add(Channel(
                    id = o.optString("stream_id", "xtream-$i"),
                    name = o.optString("name", "Channel ${i + 1}"),
                    group = o.optString("category_name", "Live TV"),
                    url = "$base/live/$username/$password/${o.optString("stream_id")}.ts",
                    logo = o.optString("stream_icon"),
                    tvgId = o.optString("epg_channel_id"),
                    number = i + 1
                ))
            }
        }
    }

    fun loadXtreamEpg(baseUrl: String, username: String, password: String, streamId: String): List<EpgEntry> {
        val base = baseUrl.trimEnd('/')
        val api = "$base/player_api.php?username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}&action=get_short_epg&stream_id=${URLEncoder.encode(streamId, "UTF-8")}&limit=12"
        return parseXtreamEpg(fetchText(api), streamId)
    }

    fun parseXtreamEpg(jsonText: String, channelId: String): List<EpgEntry> {
        val root = org.json.JSONObject(jsonText)
        val listings = root.optJSONArray("epg_listings") ?: return emptyList()
        return buildList {
            for (i in 0 until listings.length()) {
                val item = listings.optJSONObject(i) ?: continue
                val start = parseXtreamDate(item.optString("start"))
                val end = parseXtreamDate(item.optString("end"))
                if (start > 0L && end > start) {
                    add(EpgEntry(channelId, decodeHtml(item.optString("title")), start, end, decodeHtml(item.optString("description"))))
                }
            }
        }.sortedBy { it.start }
    }

    fun parseXmltv(xml: String): List<EpgEntry> {
        val result = mutableListOf<EpgEntry>()
        val programRegex = Regex("<programme\\b([^>]*)>(.*?)</programme>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        val descRegex = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        val channelRegex = Regex("\\bchannel\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        val startRegex = Regex("\\bstart\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        val stopRegex = Regex("\\bstop\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        for (match in programRegex.findAll(xml)) {
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val channel = channelRegex.find(attrs)?.groupValues?.get(1).orEmpty()
            val start = parseXmltvDate(startRegex.find(attrs)?.groupValues?.get(1).orEmpty())
            val end = parseXmltvDate(stopRegex.find(attrs)?.groupValues?.get(1).orEmpty())
            val title = titleRegex.find(body)?.groupValues?.get(1)?.let(::decodeHtml)?.stripTags().orEmpty()
            val desc = descRegex.find(body)?.groupValues?.get(1)?.let(::decodeHtml)?.stripTags().orEmpty()
            if (channel.isNotBlank() && start > 0L && end > start && title.isNotBlank()) result += EpgEntry(channel, title, start, end, desc)
        }
        return result.sortedBy { it.start }
    }

    private fun parseXmltvDate(value: String): Long {
        if (value.isBlank()) return 0L
        val formats = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmssZ", "yyyyMMddHHmmss")
        for (pattern in formats) try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            if (!pattern.contains('Z')) sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.isLenient = true
            return sdf.parse(value.trim())?.time ?: 0L
        } catch (_: Exception) { }
        return 0L
    }

    private fun parseXtreamDate(value: String): Long {
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss")
        for (pattern in formats) try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            return sdf.parse(value.trim())?.time ?: 0L
        } catch (_: Exception) { }
        return 0L
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun String.stripTags(): String = replace(Regex("<[^>]*>"), "").trim()
}
