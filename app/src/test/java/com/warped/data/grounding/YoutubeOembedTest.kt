package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Quick-task (YouTube oEmbed) exit gates for [YoutubeOembed].
 *
 * Pure helper — no sockets, JVM-safe (kotlinx.serialization, java.net.URI).
 */
class YoutubeOembedTest {

    @Test
    fun `locked host set matches`() {
        assertThat(YoutubeOembed.isYouTubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue()
        assertThat(YoutubeOembed.isYouTubeUrl("https://youtube.com/watch?v=dQw4w9WgXcQ")).isTrue()
        assertThat(YoutubeOembed.isYouTubeUrl("https://youtu.be/dQw4w9WgXcQ")).isTrue()
        assertThat(YoutubeOembed.isYouTubeUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ")).isTrue()
        assertThat(YoutubeOembed.isYouTubeUrl("http://youtu.be/dQw4w9WgXcQ")).isTrue()
    }

    @Test
    fun `non-family hosts do not match`() {
        assertThat(YoutubeOembed.isYouTubeUrl("https://music.youtube.com/watch?v=x")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("https://www.youtube-nocookie.com/embed/x")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("https://notyoutube.com/watch?v=x")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("https://example.com/watch?v=x")).isFalse()
    }

    @Test
    fun `non-http schemes and garbage do not match`() {
        assertThat(YoutubeOembed.isYouTubeUrl("ftp://youtu.be/dQw4w9WgXcQ")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("data:text/plain,hello")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("not a url at all")).isFalse()
        assertThat(YoutubeOembed.isYouTubeUrl("")).isFalse()
    }

    @Test
    fun `request url uses constant base with encoded video url`() {
        val video = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL x"
        val request = YoutubeOembed.oembedRequestUrl(video)

        assertThat(request).startsWith("https://www.youtube.com/oembed?url=")
        assertThat(request).endsWith("&format=json")
        assertThat(request).contains("watch%3Fv%3D")
        assertThat(request).doesNotContain(" ")
    }

    @Test
    fun `parse success returns title and thumbnail`() {
        val (title, thumb) = YoutubeOembed.parseOembed(
            """{"title":"Never Gonna Give You Up","author_name":"Rick Astley","thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"}""",
        )

        assertThat(title).isEqualTo("Never Gonna Give You Up")
        assertThat(thumb).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
    }

    @Test
    fun `blank strings become null`() {
        val (title, thumb) = YoutubeOembed.parseOembed(
            """{"title":"  ","thumbnail_url":""}""",
        )

        assertThat(title).isNull()
        assertThat(thumb).isNull()
    }

    @Test
    fun `author-name-only json parses to nulls`() {
        val (title, thumb) = YoutubeOembed.parseOembed(
            """{"author_name":"Rick Astley","type":"video"}""",
        )

        assertThat(title).isNull()
        assertThat(thumb).isNull()
    }

    @Test
    fun `malformed json parses to nulls`() {
        val (title, thumb) = YoutubeOembed.parseOembed("this is not json{")

        assertThat(title).isNull()
        assertThat(thumb).isNull()
    }
}
