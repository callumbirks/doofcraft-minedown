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

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration

/**
 * <h2>MineDown-adventure</h2>
 * A MarkDown inspired markup for Minecraft chat components using the adventure component library.
 *
 * This lets you convert string messages into chat components by using a custom mark up syntax
 * which is loosely based on MarkDown while still supporting legacy formatting codes.
 */
class MineDown(private var message: String) {

    private val replacer: Replacer = Replacer()
    private val parser: MineDownParser = MineDownParser()
    private var components: Component? = null
    private var replaceFirst: Boolean = System.getProperty("de.themoep.minedown.adventure.replacefirst")?.toBoolean() ?: false

    /**
     * Parse and convert the message to the component
     * @return The parsed component message
     */
    fun toComponent(): Component {
        if (components() == null) {
            var msg = message()
            if (replaceFirst()) {
                msg = replacer().replaceStrings(msg)
            }
            components = replacer().replaceIn(parser().parse(msg).build())
        }
        return components()!!
    }

    /**
     * Remove a cached component and re-parse the next time [toComponent] is called
     */
    private fun reset() {
        components = null
    }

    /**
     * Set whether or not replacements should be replaced before or after the components are created.
     * When replacing first it will not replace any placeholders with component replacement values!
     * Default is after. (replaceFirst = false)
     * @param replaceFirst  Whether or not to replace first or parse first
     * @return              The MineDown instance
     */
    fun replaceFirst(replaceFirst: Boolean): MineDown {
        reset()
        this.replaceFirst = replaceFirst
        return this
    }

    /**
     * Get whether or not replacements should be replaced before or after the components are created.
     * When replacing first it will not replace any placeholders with component replacement values!
     * Default is after. (replaceFirst = false)
     * @return Whether or not to replace first or parse first
     */
    fun replaceFirst(): Boolean = replaceFirst

    /**
     * Add an array with placeholders and values that should get replaced in the message
     * @param replacements  The replacements, nth element is the placeholder, n+1th the value
     * @return              The MineDown instance
     */
    fun replace(vararg replacements: String): MineDown {
        reset()
        replacer().replace(*replacements)
        return this
    }

    /**
     * Add a map with placeholders and values that should get replaced in the message
     * @param replacements  The replacements mapped placeholder to value
     * @return              The MineDown instance
     */
    fun replace(replacements: Map<String, *>): MineDown {
        reset()
        replacer().replace(replacements)
        return this
    }

    /**
     * Add a placeholder to component mapping that should get replaced in the message
     * @param placeholder   The placeholder to replace
     * @param replacement   The replacement component
     * @return              The Replacer instance
     */
    fun replace(placeholder: String, replacement: Component): MineDown {
        reset()
        replacer().replace(placeholder, replacement)
        return this
    }

    /**
     * Set the placeholder indicator for both prefix and suffix
     * @param placeholderIndicator  The character to use as a placeholder indicator
     * @return                      The MineDown instance
     */
    fun placeholderIndicator(placeholderIndicator: String): MineDown {
        placeholderPrefix(placeholderIndicator)
        placeholderSuffix(placeholderIndicator)
        return this
    }

    /**
     * Set the placeholder indicator's prefix character
     * @param placeholderPrefix     The character to use as the placeholder indicator's prefix
     * @return                      The MineDown instance
     */
    fun placeholderPrefix(placeholderPrefix: String): MineDown {
        reset()
        replacer().placeholderPrefix(placeholderPrefix)
        return this
    }

    /**
     * Get the placeholder indicator's prefix character
     * @return The placeholder indicator's prefix character
     */
    fun placeholderPrefix(): String = replacer().placeholderPrefix()

    /**
     * Set the placeholder indicator's suffix character
     * @param placeholderSuffix     The character to use as the placeholder indicator's suffix
     * @return                      The MineDown instance
     */
    fun placeholderSuffix(placeholderSuffix: String): MineDown {
        reset()
        replacer().placeholderSuffix(placeholderSuffix)
        return this
    }

    /**
     * Get the placeholder indicator's suffix character
     * @return The placeholder indicator's suffix character
     */
    fun placeholderSuffix(): String = replacer().placeholderSuffix()

    /**
     * Set whether or not the case of the placeholder should be ignored when replacing
     * @param ignorePlaceholderCase Whether or not to ignore the case of the placeholders
     * @return                      The MineDown instance
     */
    fun ignorePlaceholderCase(ignorePlaceholderCase: Boolean): MineDown {
        reset()
        replacer().ignorePlaceholderCase(ignorePlaceholderCase)
        return this
    }

    /**
     * Get whether or not the case of the placeholder should be ignored when replacing
     * @return Whether or not to ignore the case of the placeholders
     */
    fun ignorePlaceholderCase(): Boolean = replacer().ignorePlaceholderCase()

    /**
     * Enable or disable the translation of legacy color codes
     * @param translateLegacyColors Whether or not to translate legacy color codes (Default: true)
     * @return                      The MineDown instance
     * @deprecated Use [enable] and [disable]
     */
    @Deprecated("Use enable(MineDownParser.Option) and disable(MineDownParser.Option)")
    fun translateLegacyColors(translateLegacyColors: Boolean): MineDown {
        reset()
        parser().translateLegacyColors(translateLegacyColors)
        return this
    }

    /**
     * Detect urls in strings and add events to them? (Default: true)
     * @param enabled   Whether or not to detect URLs and add events to them
     * @return          The MineDown instance
     */
    fun urlDetection(enabled: Boolean): MineDown {
        reset()
        parser().urlDetection(enabled)
        return this
    }

    /**
     * Automatically add http to values of open_url when there doesn't exist any? (Default: true)
     * @param enabled   Whether or not to automatically add http when missing
     * @return          The MineDown instance
     */
    fun autoAddUrlPrefix(enabled: Boolean): MineDown {
        reset()
        parser().autoAddUrlPrefix(enabled)
        return this
    }

    /**
     * The text to display when hovering over an URL
     * @param text  The text to display when hovering over an URL
     * @return      The MineDown instance
     */
    fun urlHoverText(text: String): MineDown {
        reset()
        parser().urlHoverText(text)
        return this
    }

    /**
     * Set the max width the hover text should have.
     * Minecraft itself will wrap after 60 characters.
     * Won't apply if the text already includes new lines.
     * @param hoverTextWidth   The url hover text length
     * @return                  The MineDown instance
     */
    fun hoverTextWidth(hoverTextWidth: Int): MineDown {
        reset()
        parser().hoverTextWidth(hoverTextWidth)
        return this
    }

    /**
     * Enable an option. Unfilter it if you filtered it before.
     * @param option    The option to enable
     * @return          The MineDown instance
     */
    fun enable(option: MineDownParser.Option): MineDown {
        reset()
        parser().enable(option)
        return this
    }

    /**
     * Disable an option. Disabling an option will stop the parser from replacing
     * this option's chars in the string. Use [filter] to completely
     * remove the characters used by this option from the message instead.
     * @param option    The option to disable
     * @return          The MineDown instance
     */
    fun disable(option: MineDownParser.Option): MineDown {
        reset()
        parser().disable(option)
        return this
    }

    /**
     * Filter an option. This completely removes the characters of this option from
     * the string ignoring whether the option is enabled or not.
     * @param option    The option to add to the filter
     * @return          The MineDown instance
     */
    fun filter(option: MineDownParser.Option): MineDown {
        reset()
        parser().filter(option)
        return this
    }

    /**
     * Unfilter an option. Does not enable it!
     * @param option    The option to remove from the filter
     * @return          The MineDown instance
     */
    fun unfilter(option: MineDownParser.Option): MineDown {
        reset()
        parser().unfilter(option)
        return this
    }

    /**
     * Set a special character to replace color codes by if translating legacy colors is enabled.
     * @param colorChar The character to use as a special color code. (Default: ampersand &)
     * @return           The MineDown instance
     */
    fun colorChar(colorChar: Char): MineDown {
        reset()
        parser().colorChar(colorChar)
        return this
    }

    /**
     * Get the set message that is to be parsed
     * @return The to be parsed message
     */
    fun message(): String = message

    /**
     * Set the message that is to be parsed
     * @param message The message to be parsed
     * @return The MineDown instance
     */
    fun message(message: String): MineDown {
        this.message = message
        reset()
        return this
    }

    /**
     * Get the replacer instance that is currently used
     * @return The currently used replacer instance
     */
    fun replacer(): Replacer = replacer

    /**
     * Get the parser instance that is currently used
     * @return The currently used parser instance
     */
    fun parser(): MineDownParser = parser

    protected fun components(): Component? = components

    /**
     * Copy all MineDown settings to a new instance
     * @return The new MineDown instance with all settings copied
     */
    fun copy(): MineDown = MineDown(message()).copy(this)

    /**
     * Copy all MineDown settings from another one
     * @param from  The MineDown to copy from
     * @return      This MineDown instance
     */
    fun copy(from: MineDown): MineDown {
        replacer().copy(from.replacer())
        parser().copy(from.parser())
        return this
    }

    companion object {
        const val FONT_PREFIX = "font="
        const val COLOR_PREFIX = "color="
        const val SHADOW_PREFIX = "shadow="
        const val FORMAT_PREFIX = "format="
        const val TRANSLATE_PREFIX = "translate="
        const val WITH_PREFIX = "with="
        const val HOVER_PREFIX = "hover="
        const val INSERTION_PREFIX = "insert="

        const val SHADOW_ALPHA = 100

        /**
         * Parse a MineDown string to components
         * @param message       The message to translate
         * @param replacements  Optional placeholder replacements
         * @return              The parsed components
         */
        fun parse(message: String, vararg replacements: String): Component {
            return MineDown(message).replace(*replacements).toComponent()
        }

        /**
         * Convert components to a MineDown string
         * @param component The components to convert
         * @return          The components represented as a MineDown string
         */
        fun stringify(component: Component): String {
            return MineDownStringifier().stringify(component)
        }

        /**
         * Get the string that represents the format in MineDown
         * @param format    The format
         * @return          The MineDown string or an empty one if it's not a format
         */
        fun getFormatString(format: TextDecoration): String {
            return when (format) {
                TextDecoration.BOLD -> "**"
                TextDecoration.ITALIC -> "##"
                TextDecoration.UNDERLINED -> "__"
                TextDecoration.STRIKETHROUGH -> "~~"
                TextDecoration.OBFUSCATED -> "??"
            }
        }

        /**
         * Get the TextColor format from a MineDown string
         * @param c The character
         * @return  The TextColor of that format or `null` it none was found
         */
        fun getFormatFromChar(c: Char): TextDecoration? {
            return when (c) {
                '~' -> TextDecoration.STRIKETHROUGH
                '_' -> TextDecoration.UNDERLINED
                '*' -> TextDecoration.BOLD
                '#' -> TextDecoration.ITALIC
                '?' -> TextDecoration.OBFUSCATED
                else -> null
            }
        }

        /**
         * Escape all MineDown formatting in a string. This will escape backslashes too!
         * @param string    The string to escape in
         * @return          The string with formatting escaped
         */
        fun escape(string: String): String {
            return MineDown(string).parser().escape(string)
        }
    }
}
