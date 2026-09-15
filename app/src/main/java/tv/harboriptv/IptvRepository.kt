package tv.harboriptv

import java.net.HttpURLConnection
import java.net.URL


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
        connection.setRequestProperty("User-Agent", "HarborTV/0.1")
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
        val api = "$base/player_api.php?username=${java.net.URLEncoder.encode(username, "UTF-8")}&password=${java.net.URLEncoder.encode(password, "UTF-8")}&action=get_live_streams"
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
                    number = i + 1
                ))
            }
        }
    }
}
