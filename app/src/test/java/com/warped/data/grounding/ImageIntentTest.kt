package com.warped.data.grounding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Quick-task (image-grid): intent-gate word table (ES+EN).
 */
class ImageIntentTest {

    @ParameterizedTest(name = "image intent: \"{0}\"")
    @ValueSource(
        strings = [
            "muéstrame fotos de gatos",
            "Muéstrame Fotos De Gatos",
            "enséñame imágenes del mar",
            "busca una foto del Everest",
            "fotos de perros jugando",
            "imagen del sistema solar",
            "imágenes de París",
            "una fotografía antigua",
            "quiero ver el diagrama",
            "haz un dibujo de un dragón",
            "show me pictures of cats",
            "Show Me Pictures Of Cats",
            "show me the diagram",
            "a picture of a mountain",
            "an image of the moon",
            "photos from the match",
            "find a photo of Einstein",
            "diagram of the heart",
        ],
    )
    fun `image-intent queries fire`(query: String) {
        assertThat(ImageIntent.hasImageIntent(query)).isTrue()
    }

    @ParameterizedTest(name = "no intent: \"{0}\"")
    @ValueSource(
        strings = [
            "qué hora es",
            "explícame la fotosíntesis",
            "la imaginación es importante",
            "imagina un mundo mejor",
            "quién ganó el partido",
            "what is the capital of France",
            "explain photosynthesis",
            "imagination matters",
            "imagine a better world",
            "",
            "   ",
            "verbo irregular",
        ],
    )
    fun `non-image queries stay silent`(query: String) {
        assertThat(ImageIntent.hasImageIntent(query)).isFalse()
    }

    @Test
    fun `imaginacion never matches the imagen keyword`() {
        // Word-boundary contract: substring matching would fire on
        // "imaginación" — token matching must not.
        assertThat(ImageIntent.hasImageIntent("la imaginación")).isFalse()
        assertThat(ImageIntent.hasImageIntent("imaginación")).isFalse()
        assertThat(ImageIntent.hasImageIntent("imagen")).isTrue()
    }
}
