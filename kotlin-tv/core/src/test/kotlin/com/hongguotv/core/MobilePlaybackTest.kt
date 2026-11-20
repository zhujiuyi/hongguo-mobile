package com.hongguotv.core

import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MobilePlaybackTest {
    private val device = mapOf("device_id" to "123456", "iid" to "654321", "cdid" to "test-device")
    private fun model(id: String = "123", typed: Boolean = false, stringModel: Boolean = false): String {
        val model = JSONObject("""{"video_list":[{"video_meta":{"codec_type":"bytevc2","vwidth":720,"vheight":1280},"main_url":"https://example.org/unsupported"},{"video_meta":{"codec_type":"h264","vwidth":720,"vheight":1280},"main_url":"https://example.org/video"}]}""")
        val entries = JSONObject().put(id, JSONObject().put("video_model", if (stringModel) model.toString() else model))
        return JSONObject().put("code", 0).put("data", if (typed) JSONObject().put("1004", entries) else entries).toString()
    }
    private fun http(body: String, inspect: (okhttp3.Request) -> Unit = {}) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
    private val signer = MobilePlaybackSigner { url, _, _ -> MobileSignedRequest(url, emptyMap()) }

    @Test fun supportsBothLiveResponseShapesWithoutSelectingOtherEpisodes() {
        for (typed in listOf(false, true)) for (stringModel in listOf(false, true)) {
            val rows = MobilePlayback.ladder("123", device, http(model(typed=typed, stringModel=stringModel)), signer, 1000).renditions
            assertEquals(1, rows.size)
            assertEquals(720, rows.single().quality)
            assertEquals("https://example.org/video", rows.single().url)
        }
        assertThrows(PlaybackSourceException::class.java) {
            MobilePlayback.ladder("123", device, http(model("456")), signer, 1000)
        }
    }
    @Test fun signsAndSendsIdenticalCompressedBytesWithMatchingIdentityAndDigest() {
        var signed: ByteArray? = null
        var url: String? = null
        val signing = MobilePlaybackSigner { target, bytes, identity ->
            assertEquals(device, identity)
            signed = bytes.copyOf(); url = target
            MobileSignedRequest(target, mapOf("X-Argus" to "test-signature"))
        }
        MobilePlayback.ladder("123", device, http(model()) { request ->
            val bytes = Buffer().apply { request.body!!.writeTo(this) }.readByteArray()
            assertArrayEquals(signed, bytes)
            assertEquals(url, request.url.toString())
            assertEquals("/novel/player/multi_video_model/player/v1", request.url.encodedPath)
            assertEquals("123456", request.url.queryParameter("device_id"))
            assertEquals("654321", request.url.queryParameter("iid"))
            assertEquals("gzip", request.header("Content-Encoding"))
            assertEquals("test-signature", request.header("X-Argus"))
            assertNull(request.header("Cookie"))
            assertEquals(MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02X".format(it) }, request.header("x-ss-stub"))
            val body = JSONObject(GZIPInputStream(bytes.inputStream()).reader().readText())
            assertEquals("TV must not request the app-only bytevc2 capability tier", 1, body.getJSONObject("biz_param").getInt("device_level"))
            assertEquals("123", body.getJSONObject("mixed_video_id_map").getJSONArray("1004").getString(0))
        }, signing, 1000)
    }
    @Test fun rejectsBusinessErrorsAndOversizeResponse() {
        val error = assertThrows(PlaybackSourceException::class.java) {
            MobilePlayback.ladder("123", device, http("""{"code":110001}"""), signer, 1000)
        }
        assertEquals(110001, error.upstreamCode)
        assertThrows(IOException::class.java) {
            MobilePlayback.ladder("123", device, http(" ".repeat(2 * 1024 * 1024 + 1)), signer, 1000)
        }
    }
    @Test fun cancellationBeforeOrDuringSigningNeverSendsRequest() {
        val transport = http(model()) { fail("cancelled request reached network") }
        for (before in listOf(false, true)) {
            val scope = RequestScope()
            if (before) scope.cancel()
            assertThrows(IOException::class.java) {
                scope.run { MobilePlayback.ladder("123", device, transport,
                    MobilePlaybackSigner { url, _, _ -> scope.cancel(); MobileSignedRequest(url, emptyMap()) }, 1000) }
            }
        }
    }
    @Test fun speculationCannotRegisterAnIdentity() {
        val session = PlaybackDeviceSession { throw AssertionError("background registration") }
            assertThrows(IOException::class.java) {
                ContentRepository(http = http(model()), playbackSession = session)
                    .streamLadderWithCompatibility("123", speculative = true)
            }
    }

    private fun tabModel(id: String = "123", stringModel: Boolean = true): String {
        val model = JSONObject("""{"video_list":[{"video_meta":{"codec_type":"bytevc2","vwidth":720,"vheight":1280},"main_url":"https://example.org/unsupported"},{"video_meta":{"codec_type":"h264","vwidth":720,"vheight":1280},"main_url":"https://example.org/video"}]}""")
        val entries = JSONObject().put(id, JSONObject().put("video_model", if (stringModel) model.toString() else model))
        return JSONObject().put("code", 0).put("data", JSONObject().put("video_model_data", entries)).toString()
    }

    @Test fun emptyPostFallsBackToV5VideoTabAcrossHostsAndCachesHealthyHost() {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = when {
                request.method == "POST" -> ""
                request.url.host == "api5-normal-sinfonlineb.fqnovel.com" -> ""
                request.url.host == "api5-normal-sinfonlinea.fqnovel.com" -> tabModel("123")
                else -> fail("unexpected host ${request.url.host}") as String
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val session = PlaybackDeviceSession { DeviceRegister.Registered("123456", "654321") }
        val repository = ContentRepository(http = client, playbackSession = session)

        val first = repository.streamLadderWithCompatibility("123").renditions.single()
        assertEquals(720, first.quality)
        assertEquals("https://example.org/video", first.url)
        assertEquals(3, requests.size)
        assertEquals("POST", requests[0].method)
        assertEquals("GET", requests[1].method)
        assertEquals("api5-normal-sinfonlineb.fqnovel.com", requests[1].url.host)
        assertEquals("GET", requests[2].method)
        assertEquals("api5-normal-sinfonlinea.fqnovel.com", requests[2].url.host)

        val tabRequest = requests[2]
        assertEquals("/reading/bookapi/video_tab/multi_video_model/v1/", tabRequest.url.encodedPath)
        assertEquals("123", tabRequest.url.queryParameter("video_id"))
        for (parameter in listOf("iid", "device_id", "cdid")) {
            assertEquals(requests[0].url.queryParameter(parameter), tabRequest.url.queryParameter(parameter))
        }
        assertFalse(tabRequest.header("x-khronos").isNullOrBlank())
        assertFalse(tabRequest.header("x-helios").isNullOrBlank())
        assertFalse(tabRequest.header("x-medusa").isNullOrBlank())
        assertNull(tabRequest.header("Content-Type"))
        assertNull(tabRequest.header("Content-Encoding"))
        assertNull(tabRequest.header("x-ss-stub"))
        assertNull(tabRequest.header("Cookie"))

        val second = repository.streamLadderWithCompatibility("123").renditions.single()
        assertEquals("https://example.org/video", second.url)
        assertEquals(4, requests.size)
        assertEquals("GET", requests[3].method)
        assertEquals("api5-normal-sinfonlinea.fqnovel.com", requests[3].url.host)
    }

    @Test fun allEmptyHostsRotateAnonymousIdentityAndRecover() {
        val requests = mutableListOf<Request>()
        var registerCount = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val did = request.url.queryParameter("device_id")
            val body = if (did == "333" && request.method == "GET") tabModel("123") else ""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val session = PlaybackDeviceSession {
            registerCount++
            if (registerCount == 1) DeviceRegister.Registered("111", "222")
            else DeviceRegister.Registered("333", "444")
        }
        val repository = ContentRepository(http = client, playbackSession = session)

        val recovered = repository.streamLadderWithCompatibility("123").renditions.single()
        assertEquals(720, recovered.quality)
        assertEquals("https://example.org/video", recovered.url)
        assertEquals(2, registerCount)
        assertEquals("333", requests.last().url.queryParameter("device_id"))
        assertEquals(MobilePlayback.TAB_HOSTS.first(), requests.last().url.host)

        val beforeNext = requests.size
        repository.streamLadderWithCompatibility("123")
        assertEquals(beforeNext + 1, requests.size)
        assertEquals("333", requests.last().url.queryParameter("device_id"))
        assertEquals("GET", requests.last().method)
    }

    @Test fun tabFallbackRejectsWrongEpisodeAndHonorsRetryAfter() {
        val wrongEpisodeRequests = mutableListOf<Request>()
        val wrongEpisodeClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            wrongEpisodeRequests += request
            val body = if (request.method == "POST") "" else tabModel("456")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val repo1 = ContentRepository(
            http = wrongEpisodeClient,
            playbackSession = PlaybackDeviceSession { DeviceRegister.Registered("123456", "654321") },
        )
        val wrongEpisodeError = assertThrows(PlaybackSourceException::class.java) {
            repo1.streamLadderWithCompatibility("123")
        }
        assertEquals(PlaybackSourceException.Reason.NO_PLAYABLE_SOURCE, wrongEpisodeError.reason)
        assertEquals(listOf("POST", "GET"), wrongEpisodeRequests.map { it.method })

        val backoffRequests = mutableListOf<Request>()
        var rotated = false
        val backoffClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            backoffRequests += request
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody())
            if (request.method == "GET") builder.header("Retry-After", "30")
            builder.build()
        }.build()
        val repo2 = ContentRepository(
            http = backoffClient,
            playbackSession = PlaybackDeviceSession {
                if (rotated) throw AssertionError("Retry-After backoff must not rotate device")
                rotated = true
                DeviceRegister.Registered("123456", "654321")
            },
        )
        assertThrows(PlaybackServiceBackoffException::class.java) {
            repo2.streamLadderWithCompatibility("123")
        }
        assertEquals(listOf("POST", "GET"), backoffRequests.map { it.method })
    }
}
