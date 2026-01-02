package com.doofcraft.minedown

/*
 * Copyright (c) 2020 Max Lee (https://github.com/Phoenix616)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import net.kyori.adventure.text.BuildableComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentBuilder
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextFormat
import java.awt.Color
import java.util.*
import java.util.regex.Pattern
import kotlin.math.*

object Util {

    private val WRAP_PATTERN = Pattern.compile(" ", Pattern.LITERAL)

    /**
     * Utility method to throw an IllegalArgumentException if the value is false
     * @param value   The value to validate
     * @param message The message for the exception
     * @throws IllegalArgumentException Thrown if the value is false
     */
    @Throws(IllegalArgumentException::class)
    fun validate(value: Boolean, message: String) {
        if (!value) {
            throw IllegalArgumentException(message)
        }
    }

    /**
     * Apply a collection of colors/formats to a component
     * @param component The BaseComponent
     * @param formats   The collection of TextColor formats to apply
     * @return The component that was modified
     */
    fun applyFormat(component: Component, formats: Collection<TextDecoration>): Component {
        var result = component
        for (format in formats) {
            result = result.decoration(format, true)
        }
        if (component.children().isNotEmpty()) {
            for (extra in component.children()) {
                applyFormat(extra, formats)
            }
        }
        return result
    }

    /**
     * Apply a collection of colors/formats to a component builder
     * @param builder The ComponentBuilder
     * @param formats The collection of TextColor formats to apply
     * @return The component builder that was modified
     * @deprecated Use [applyFormat] with Map parameter
     */
    @Deprecated("Use applyFormat(ComponentBuilder, Map)")
    fun applyFormat(builder: ComponentBuilder<*, *>, formats: Collection<TextDecoration>): ComponentBuilder<*, *> {
        var result = builder
        for (format in formats) {
            result = result.decoration(format, true)
        }
        return result
    }

    /**
     * Apply a collection of colors/formats to a component builder
     * @param builder The ComponentBuilder
     * @param formats The collection of TextColor formats to apply
     * @return The component builder that was modified
     */
    fun applyFormat(builder: ComponentBuilder<*, *>, formats: Map<TextDecoration, Boolean>): ComponentBuilder<*, *> {
        var result = builder
        for ((key, value) in formats) {
            result = result.decoration(key, value)
        }
        return result
    }

    /**
     * Check whether or not a character at a certain index of a string repeats itself
     * @param string The string to check
     * @param index  The index at which to check the character
     * @return Whether or not the character at that index repeated itself
     */
    fun isDouble(string: String, index: Int): Boolean {
        return index + 1 < string.length && string[index] == string[index + 1]
    }

    /**
     * Check whether a certain TextColor is formatting or not
     * @param format The TextColor to check
     * @return `true` if it's a format, `false` if it's a color
     */
    fun isFormat(format: TextColor): Boolean {
        return false
    }

    /**
     * Get a set of TextColor formats all formats that a component includes
     * @param component    The component to get the formats from
     * @param ignoreParent Whether or not to include the parent's format (TODO: Does kyori-text not handle this?)
     * @return A set of all the format TextColors that the component includes
     */
    fun getFormats(component: Component, ignoreParent: Boolean): Set<TextDecoration> {
        return component.decorations()
            .filterValues { it == TextDecoration.State.TRUE }
            .keys
    }

    /**
     * Get the index of the first occurrences of a not escaped character
     * @param string The string to search
     * @param chars  The characters to search for
     * @return The first unescaped index or -1 if not found
     */
    fun indexOfNotEscaped(string: String, chars: String): Int {
        return indexOfNotEscaped(string, chars, 0)
    }

    /**
     * Get the index of the first occurrences of a not escaped character
     * @param string    The string to search
     * @param chars     The characters to search for
     * @param fromIndex Start searching from that index
     * @return The first unescaped index or -1 if not found
     */
    fun indexOfNotEscaped(string: String, chars: String, fromIndex: Int): Int {
        var i = fromIndex
        while (i < string.length) {
            val index = string.indexOf(chars, i)
            if (index == -1) {
                return -1
            }
            if (!isEscaped(string, index)) {
                return index
            }
            i++
        }
        return -1
    }

    /**
     * Check if a character at a certain index is escaped
     * @param string The string to check
     * @param index  The index of the character in the string to check
     * @return Whether or not the character is escaped (uneven number of backslashes in front of char means it is escaped)
     * @throws IndexOutOfBoundsException if the index argument is not less than the length of this string.
     */
    fun isEscaped(string: String, index: Int): Boolean {
        if (index - 1 > string.length) {
            return false
        }
        var e = 0
        while (index > e && string[index - e - 1] == '\\') {
            e++
        }
        return e % 2 != 0
    }

    /**
     * Gets the proper end index of a certain definition on the same depth while ignoring escaped chars.
     * @param string    The string to search
     * @param startChar The start character of the definition
     * @param endChar   The end character of the definition
     * @param fromIndex The index to start searching from (should be at the start char)
     * @return The first end index of that group or -1 if not found
     */
    fun getUnescapedEndIndex(string: String, startChar: Char, endChar: Char, fromIndex: Int): Int {
        var depth = 0
        var innerEscaped = false
        for (i in fromIndex until string.length) {
            if (innerEscaped) {
                innerEscaped = false
            } else if (string[i] == '\\') {
                innerEscaped = true
            } else if (string[i] == startChar) {
                depth++
            } else if (string[i] == endChar) {
                depth--
                if (depth == 0) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * Wrap a string if it is longer than the line length and contains no new line.
     * Will try to wrap at spaces between words.
     * @param string        The string to wrap
     * @param lineLength    The max length of a line
     * @return The wrapped string
     */
    fun wrap(string: String, lineLength: Int): String {
        if (string.length <= lineLength || string.contains("\n")) {
            return string
        }

        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        for (s in WRAP_PATTERN.split(string)) {
            if (currentLine.length + s.length + 1 > lineLength) {
                var rest = lineLength - currentLine.length - 1
                if (rest > lineLength / 4 && s.length > min(rest * 2, lineLength / 4)) {
                    currentLine.append(" ").append(s.substring(0, rest))
                } else {
                    rest = 0
                }
                lines.add(currentLine.toString())
                var restString = s.substring(rest)
                while (restString.length >= lineLength) {
                    lines.add(restString.substring(0, lineLength))
                    restString = restString.substring(lineLength)
                }
                currentLine.clear()
                currentLine.append(restString)
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(s)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.joinToString("\n")
    }

    /**
     * Utility method to remove RGB colors from components. This modifies the input array!
     * @param components    The components to remove the rgb colors from
     * @return The modified components (same as input).
     */
    fun rgbColorsToLegacy(components: Component): Component {
        return Component.text().append(components).mapChildrenDeep { buildableComponent ->
            val color = buildableComponent.color()
            if (color != null)
                buildableComponent.color(NamedTextColor.nearestTo(color)) as BuildableComponent<*, *>
            else
                buildableComponent
        }.build()
    }

    /**
     * Get the legacy color closest to a certain RGB color
     * @param textColor The color to get the closest legacy color for
     * @return The closest legacy color
     * @deprecated Use [NamedTextColor.nearestTo]
     */
    @Deprecated("Use NamedTextColor.nearestTo(TextColor)")
    fun getClosestLegacy(textColor: TextColor?): NamedTextColor? {
        return if (textColor != null) NamedTextColor.nearestTo(textColor) else null
    }

    /**
     * Get the distance between two colors
     * @param c1 Color A
     * @param c2 Color B
     * @return The distance or 0 if they are equal
     * @deprecated Doesn't use perceived brightness (HSV) but simply takes the distance between RGB. Do not rely on this, it will look ugly!
     */
    @Deprecated("Doesn't use perceived brightness")
    fun distance(c1: Color, c2: Color): Double {
        if (c1.rgb == c2.rgb) {
            return 0.0
        }
        return sqrt(
            (c1.red - c2.red).toDouble().pow(2) +
            (c1.green - c2.green).toDouble().pow(2) +
            (c1.blue - c2.blue).toDouble().pow(2)
        )
    }

    /**
     * Get the text format from a string, either its name or hex code
     * @param formatString The string to get the format from
     * @return The TextFormat
     * @throws IllegalArgumentException if the format could not be found from the string
     */
    @Throws(IllegalArgumentException::class)
    fun getFormatFromString(formatString: String): TextFormat {
        val format: TextFormat? = if (formatString[0] == '#') {
            TextColor.fromCSSHexString(formatString)
        } else {
            var result: TextFormat? = NamedTextColor.NAMES.value(formatString.lowercase(Locale.ROOT))
            if (result == null) {
                result = TextDecoration.NAMES.value(formatString.lowercase(Locale.ROOT))
            }
            if (result == null) {
                // Handle legacy formatting names
                result = when (formatString.lowercase(Locale.ROOT)) {
                    "underline" -> TextDecoration.UNDERLINED
                    "magic" -> TextDecoration.OBFUSCATED
                    else -> null
                }
            }
            result
        }

        return format ?: throw IllegalArgumentException("Unknown format: $formatString")
    }

    /**
     * Get a TextFormat from its legacy color code as kyori-text-api does not support that
     * @param code  The legacy char
     * @return      The TextFormat or null if none found with that char
     */
    fun getFormatFromLegacy(code: Char): TextFormat? {
        return when (code) {
            '0' -> NamedTextColor.BLACK
            '1' -> NamedTextColor.DARK_BLUE
            '2' -> NamedTextColor.DARK_GREEN
            '3' -> NamedTextColor.DARK_AQUA
            '4' -> NamedTextColor.DARK_RED
            '5' -> NamedTextColor.DARK_PURPLE
            '6' -> NamedTextColor.GOLD
            '7' -> NamedTextColor.GRAY
            '8' -> NamedTextColor.DARK_GRAY
            '9' -> NamedTextColor.BLUE
            'a' -> NamedTextColor.GREEN
            'b' -> NamedTextColor.AQUA
            'c' -> NamedTextColor.RED
            'd' -> NamedTextColor.LIGHT_PURPLE
            'e' -> NamedTextColor.YELLOW
            'f' -> NamedTextColor.WHITE
            'k' -> TextDecoration.OBFUSCATED
            'l' -> TextDecoration.BOLD
            'm' -> TextDecoration.STRIKETHROUGH
            'n' -> TextDecoration.UNDERLINED
            'o' -> TextDecoration.ITALIC
            'r' -> TextControl.RESET
            else -> null
        }
    }

    /**
     * Get the legacy color code from its format as kyori-text-api does not support that
     * @param format    The format
     * @return          The legacy color code or null if none found with that char
     */
    fun getLegacyFormatChar(format: TextFormat): Char {
        return when (format) {
            TextControl.RESET -> 'r'
            is NamedTextColor -> when (format) {
                NamedTextColor.BLACK -> '0'
                NamedTextColor.DARK_BLUE -> '1'
                NamedTextColor.DARK_GREEN -> '2'
                NamedTextColor.DARK_AQUA -> '3'
                NamedTextColor.DARK_RED -> '4'
                NamedTextColor.DARK_PURPLE -> '5'
                NamedTextColor.GOLD -> '6'
                NamedTextColor.GRAY -> '7'
                NamedTextColor.DARK_GRAY -> '8'
                NamedTextColor.BLUE -> '9'
                NamedTextColor.GREEN -> 'a'
                NamedTextColor.AQUA -> 'b'
                NamedTextColor.RED -> 'c'
                NamedTextColor.LIGHT_PURPLE -> 'd'
                NamedTextColor.YELLOW -> 'e'
                NamedTextColor.WHITE -> 'f'
                else -> throw IllegalArgumentException("$format is not supported!")
            }
            is TextDecoration -> when (format) {
                TextDecoration.OBFUSCATED -> 'k'
                TextDecoration.BOLD -> 'l'
                TextDecoration.STRIKETHROUGH -> 'm'
                TextDecoration.UNDERLINED -> 'n'
                TextDecoration.ITALIC -> 'o'
            }
            is TextColor -> getLegacyFormatChar(NamedTextColor.nearestTo(format))
            else -> throw IllegalArgumentException("$format is not supported!")
        }
    }

    /*
     * createRainbow is adapted from the net.kyori.adventure.text.minimessage.fancy.Rainbow class
     * in adventure-text-minimessage, licensed under the MIT License.
     *
     * Copyright (c) 2018-2020 KyoriPowered
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to deal
     * in the Software without restriction, including without limitation the rights
     * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
     * copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all
     * copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
     * SOFTWARE.
     */
    /**
     * Generate a rainbow with a certain length and phase
     * @param length    The length of the rainbow
     * @param phase     The phase of the rainbow.
     * @return the colors in the rainbow
     * @deprecated Use [createRainbow] with Long parameter
     */
    @Deprecated("Use createRainbow(Long, Int)")
    fun createRainbow(length: Int, phase: Int): List<TextColor> {
        return createRainbow(length.toLong(), phase)
    }

    /**
     * Generate a rainbow with a certain length and phase
     * @param length    The length of the rainbow
     * @param phase     The phase of the rainbow.
     * @return the colors in the rainbow
     */
    fun createRainbow(length: Long, phase: Int): List<TextColor> {
        val colors = mutableListOf<TextColor>()

        val fPhase = phase / 10f

        val center = 128f
        val width = 127f
        val frequency = Math.PI * 2 / length

        for (i in 0 until length) {
            colors.add(
                TextColor.color(
                    (sin(frequency * i + 2 + fPhase) * width + center).toInt(),
                    (sin(frequency * i + 0 + fPhase) * width + center).toInt(),
                    (sin(frequency * i + 4 + fPhase) * width + center).toInt()
                )
            )
        }
        return colors
    }

    /*
     * createGradient is adapted from the net.kyori.adventure.text.minimessage.fancy.Gradient class
     * in adventure-text-minimessage, licensed under the MIT License.
     *
     * Copyright (c) 2018-2020 KyoriPowered
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to deal
     * in the Software without restriction, including without limitation the rights
     * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
     * copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all
     * copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
     * SOFTWARE.
     */
    /**
     * Generate a gradient with certain colors
     * @param length    The length of the gradient
     * @param gradient    The colors of the gradient.
     * @return the colors in the gradient
     * @deprecated Use [createGradient] with Long parameter
     */
    @Deprecated("Use createGradient(Long, List)")
    fun createGradient(length: Int, gradient: List<TextColor>): List<TextColor> {
        return createGradient(length.toLong(), gradient)
    }

    /**
     * Generate a gradient with certain colors
     * @param length    The length of the gradient
     * @param gradient    The colors of the gradient.
     * @return the colors in the gradient
     */
    fun createGradient(length: Long, gradient: List<TextColor>): List<TextColor> {
        val colors = mutableListOf<TextColor>()
        if (gradient.size < 2 || length < 2) {
            return if (gradient.isEmpty()) {
                gradient
            } else {
                listOf(gradient[0])
            }
        }

        val fPhase = 0f

        val sectorLength = (length - 1).toFloat() / (gradient.size - 1)
        val factorStep = 1.0f / sectorLength

        var index = 0L

        var colorIndex = 0

        for (i in 0 until length) {
            if (factorStep * index > 1) {
                colorIndex++
                index = 0
            }

            var factor = factorStep * (index++ + fPhase)
            // loop around if needed
            if (factor > 1) {
                factor = 1 - (factor - 1)
            }

            colors.add(
                TextColor.lerp(
                    factor,
                    gradient[colorIndex],
                    gradient[min(gradient.size - 1, colorIndex + 1)]
                )
            )
        }

        return colors
    }

    enum class TextControl(private val c: Char) : TextFormat {
        RESET('r');

        fun getChar(): Char {
            return c
        }
    }
}
