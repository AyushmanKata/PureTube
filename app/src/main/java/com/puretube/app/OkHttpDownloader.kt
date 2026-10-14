package com.puretube.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

const val WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

/** Bridges NewPipeExtractor's HTTP needs to OkHttp. */
class OkHttpDownloader(private val http: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)

        val headers = request.headers()
        headers.forEach { (name, values) ->
            values.forEach { builder.addHeader(name, it) }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", WEB_USER_AGENT)
        }
        if (headers.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("Rate limited / captcha required", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.names().associateWith { response.headers.values(it) },
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
