package com.nuvio.app.features.plugins

import com.nuvio.app.features.plugins.runtime.cs3.Cs3Archive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * CloudStream provider ingestion: the url a user pastes, the `repo.json` indirection,
 * the plugin list shape, and the `.cs3` container the Android loader reads.
 */
class CloudStreamProviderIngestionTest {
    private val rawRepo =
        "raw.githubusercontent.com/some/repo/refs/heads/builds/repo.json"

    @Test
    fun cloudStreamDeepLinksAreUnwrappedInsteadOfTreatedAsAHost() {
        assertEquals("https://$rawRepo", normalizeCloudStreamRepoUrl("cloudstreamrepo://$rawRepo"))
        assertEquals("https://$rawRepo", normalizeCloudStreamRepoUrl("cloudstream://$rawRepo"))
        assertEquals(
            "https://foo.com/repo.json",
            normalizeCloudStreamRepoUrl("cloudstreamrepo://add?url=https%3A%2F%2Ffoo.com%2Frepo.json"),
        )
        // must never end up as https://cloudstreamrepo://…, which cannot be resolved
        assertTrue(normalizeCloudStreamRepoUrl("cloudstreamrepo://$rawRepo")!!.startsWith("https://raw"))
    }

    @Test
    fun plainUrlsKeepWorking() {
        assertEquals("https://$rawRepo", normalizeCloudStreamRepoUrl("https://$rawRepo"))
        assertEquals("https://$rawRepo", normalizeCloudStreamRepoUrl(" $rawRepo "))
        assertEquals("https://foo.com/manifest.json", normalizeCloudStreamRepoUrl("foo.com"))
        assertEquals(
            "https://foo.com/x/manifest.json",
            normalizeCloudStreamRepoUrl("https://foo.com/x/"),
        )
        assertEquals(
            "https://foo.com/plugins.json?token=1",
            normalizeCloudStreamRepoUrl("https://foo.com/plugins.json?token=1#anchor"),
        )
        assertEquals("https://localhost:8080/repo.json", normalizeCloudStreamRepoUrl("localhost:8080/repo.json"))
    }

    @Test
    fun nothingTypedInIsRejected() {
        assertNull(normalizeCloudStreamRepoUrl(""))
        assertNull(normalizeCloudStreamRepoUrl("   "))
        assertNull(normalizeCloudStreamRepoUrl("cloudstreamrepo://"))
        assertNull(normalizeCloudStreamRepoUrl("cloudstreamrepo://add?url="))
    }

    @Test
    fun percentDecodingKeepsTheQueryIntact() {
        assertEquals("https://foo.com/repo.json", percentDecode("https%3A%2F%2Ffoo.com%2Frepo.json"))
        assertEquals("plain", percentDecode("plain"))
    }

    @Test
    fun everyPluginListEntryIsFollowedAndMerged() {
        val plugins = """[{"name":"B","internalName":"B","url":"https://h/B.cs3","version":1,"tvTypes":["Movie"]}]"""
        val root =
            """{"name":"Repo","pluginLists":["https://h/plugins.json","https://h/extra.json"]}"""
        val requested = mutableListOf<String>()
        val merged = runBlocking {
            resolveCloudStreamRepoPayload(root) { url ->
                requested.add(url)
                if (url.endsWith("plugins.json")) plugins else error("unreachable")
            }
        }
        assertEquals(listOf("https://h/plugins.json", "https://h/extra.json"), requested)
        assertTrue(merged.contains("\"B\""), "merged payload should keep the live list: $merged")
    }

    @Test
    fun aRepositoryWithoutPluginListsIsPassedThrough() {
        val plugins = """[{"name":"A","url":"https://h/A.cs3"}]"""
        assertEquals(plugins, runBlocking { resolveCloudStreamRepoPayload(plugins) { error("no fetch") } })
    }

    @Test
    fun whenEveryListFailsTheOriginalPayloadIsKeptSoTheErrorStaysUseful() {
        val root = """{"name":"Repo","pluginLists":["https://h/plugins.json"]}"""
        assertEquals(root, runBlocking { resolveCloudStreamRepoPayload(root) { error("offline") } })
    }

    @Test
    fun cloudStreamPluginListsBecomeProviders() {
        val payload = """
            [
              {"name":"Movies","internalName":"Movies","url":"https://h/Movies.cs3","version":25,
               "tvTypes":["Movie","TvSeries","Cartoon"],"language":"hi","iconUrl":"https://h/i.png"},
              {"name":"Anime","internalName":"Anime","url":"https://h/Anime.cs3","version":3,"tvType":"Anime"},
              {"name":"Everything","internalName":"Everything","url":"https://h/All.cs3","tvTypes":["All"]},
              {"name":"Relative","internalName":"Relative","fileName":"Relative.cs3","tvTypes":["Movie"]}
            ]
        """.trimIndent()
        val manifest = PluginManifestParser.parse(payload)
        assertEquals(4, manifest.scrapers.size)

        val movies = manifest.scrapers.first { it.id == "Movies" }
        assertEquals("https://h/Movies.cs3", movies.filename)
        assertEquals(listOf("movie", "tv"), movies.supportedTypes)
        assertEquals("25", movies.version)
        assertEquals(listOf("cs3"), movies.formats)
        assertTrue(movies.enabled)
        assertEquals("https://h/i.png", movies.logo)

        assertEquals(listOf("tv"), manifest.scrapers.first { it.id == "Anime" }.supportedTypes)
        assertEquals(
            listOf("movie", "tv"),
            manifest.scrapers.first { it.id == "Everything" }.supportedTypes,
        )
        assertEquals("Relative.cs3", manifest.scrapers.first { it.id == "Relative" }.filename)
    }

    @Test
    fun anEntryWithoutANameIsSkippedRatherThanCrashingTheRepository() {
        val manifest = PluginManifestParser.parse("""[{"url":"https://h/x.cs3"},{"name":"Ok","url":"https://h/ok.cs3"}]""")
        assertEquals(listOf("Ok"), manifest.scrapers.map { it.name })
    }

    @Test
    fun cs3ContainersAreReadByEntryAndNonDexDataIsIgnored() {
        val dir = File.createTempFile("nuvio-cs3", ".dir").apply { delete(); mkdirs() }
        try {
            val archive = File(dir, "plugin.cs3")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("""{"pluginClassName":"com.example.SamplePlugin","requiresResources":false}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write("not a dex file".toByteArray())
                zip.closeEntry()
            }
            val bytes = archive.readBytes()
            assertEquals(
                """{"pluginClassName":"com.example.SamplePlugin","requiresResources":false}""",
                Cs3Archive.entryText(bytes, Cs3Archive.MANIFEST_ENTRY),
            )
            assertEquals(emptyList(), Cs3Archive.dexClassNames(Cs3Archive.entryBytes(bytes, Cs3Archive.DEX_ENTRY)!!))
            assertNull(Cs3Archive.entryText(bytes, "resources.zip"))
            assertTrue(Cs3Archive.contains(bytes))
            assertTrue(Cs3Archive.entryNames(bytes) == listOf("manifest.json", "classes.dex"))
            assertEquals(emptyList(), Cs3Archive.dexClassNames("{}".toByteArray()))
        } finally {
            dir.deleteRecursively()
        }
    }
}
