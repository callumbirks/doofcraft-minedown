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

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.*

class MineDownStringifier {

    /**
     * Whether or not to use legacy color codes (Default: false)
     */
    private var useLegacyColors: Boolean = false

    /**
     * Whether or not to translate legacy formatting codes over Minedown ones (Default: false)
     */
    private var useLegacyFormatting: Boolean = false

    /**
     * Whether or not to use simple event definitions or specific ones (Default: true)
     */
    private var preferSimpleEvents: Boolean = true

    /**
     * Whether or not to put formatting in event definitions (Default: false)
     */
    private var formattingInEventDefinition: Boolean = false

    /**
     * Whether or not to put colors in event definitions (Default: false)
     */
    private var colorInEventDefinition: Boolean = false

    /**
     * The character to use as a special color code. (Default: ampersand &)
     */
    private var colorChar: Char = '&'

    private val value: StringBuilder = StringBuilder()

    private var color: TextColor? = null
    private var clickEvent: ClickEvent? = null
    private var hoverEvent: HoverEvent<*>? = null
    private val formats: MutableSet<TextDecoration> = LinkedHashSet()

    /**
     * Create a MineDown string from a component message
     * @param components The components to generate a MineDown string from
     * @return The MineDown string
     */
    fun stringify(components: List<Component>): String {
        return buildString {
            for (component in components) {
                append(stringify(component))
            }
        }
    }

    /**
     * Create a MineDown string from a component message
     * @param component The component to generate a MineDown string from
     * @return The MineDown string
     */
    fun stringify(component: Component): String {
        val sb = StringBuilder()
        if (!component.hasStyling() && component.children().isEmpty() && component is TextComponent) {
            appendText(sb, component)
            return sb.toString()
        }
        val hasEvent = (component.style().font() != null && component.style().font() != Style.DEFAULT_FONT) ||
                (component.shadowColor() != null && component.shadowColor()!!.alpha() != 0) ||
                component is TranslatableComponent || component.insertion() != null ||
                component.clickEvent() != clickEvent || component.hoverEvent() != hoverEvent
        if (hasEvent) {
            sb.append('[')
            if (!formattingInEventDefinition()) {
                appendFormat(sb, component)
            }
            if (!colorInEventDefinition()) {
                appendColor(sb, component.color())
            }
        } else if (component.color() != null) {
            appendFormat(sb, component)
            appendColor(sb, component.color())
        } else {
            appendFormat(sb, component)
        }

        appendText(sb, component)

        if (component.children().isNotEmpty()) {
            sb.append(copy().stringify(component.children()))
        }

        if (hasEvent) {
            clickEvent = component.clickEvent()
            hoverEvent = component.hoverEvent()
            if (!formattingInEventDefinition()) {
                appendFormatSuffix(sb, component)
            }
            sb.append("](")
            val definitions = mutableListOf<String>()
            if (component is TranslatableComponent) {
                definitions.add(MineDown.TRANSLATE_PREFIX + component.key())
                if (component.args().isNotEmpty()) {
                    definitions.add(buildString {
                        append(MineDown.WITH_PREFIX)
                        append("{")
                        append(component.args().joinToString(",") { stringify(it) })
                        append("}")
                    })
                }
            }
            if (colorInEventDefinition() && component.color() != null) {
                val sbi = StringBuilder()
                if (!preferSimpleEvents()) {
                    sbi.append(MineDown.COLOR_PREFIX)
                }
                if (component.color() is NamedTextColor) {
                    sbi.append(component.color())
                } else {
                    sbi.append(component.color()!!.asHexString().lowercase(Locale.ROOT))
                }
                definitions.add(sbi.toString())
            }
            if (formattingInEventDefinition()) {
                val sbi = StringBuilder()
                if (!preferSimpleEvents) {
                    sbi.append(MineDown.FORMAT_PREFIX)
                }
                sbi.append(
                    component.decorations()
                        .filterValues { it == TextDecoration.State.TRUE }
                        .keys
                        .joinToString(" ") { it.name.lowercase(Locale.ROOT) }
                )
                definitions.add(sbi.toString())
            }
            if (component.shadowColor() != null && component.shadowColor()!!.alpha() != 0) {
                val hexString = component.shadowColor()!!.asHexString().lowercase(Locale.ROOT)
                if (component.shadowColor()!!.alpha() == MineDown.SHADOW_ALPHA) {
                    val shortHex = hexString.substring(0, 7)
                    val color = TextColor.fromHexString(shortHex)
                    val namedColor = color?.let { NamedTextColor.namedColor(it.value()) }
                    if (namedColor != null) {
                        definitions.add(MineDown.SHADOW_PREFIX + namedColor)
                    } else {
                        definitions.add(MineDown.SHADOW_PREFIX + shortHex)
                    }
                } else {
                    definitions.add(MineDown.SHADOW_PREFIX + hexString)
                }
            }
            if (component.style().font() != null && component.style().font() != Style.DEFAULT_FONT) {
                val font = component.style().font()!!
                if (font.namespace() == "minecraft") {
                    definitions.add(MineDown.FONT_PREFIX + font.value())
                } else {
                    definitions.add(MineDown.FONT_PREFIX + font)
                }
            }
            if (component.insertion() != null) {
                if (component.insertion()!!.contains(" ")) {
                    definitions.add(MineDown.INSERTION_PREFIX + "{" + component.insertion() + "}")
                } else {
                    definitions.add(MineDown.INSERTION_PREFIX + component.insertion())
                }
            }
            if (component.clickEvent() != null) {
                if (preferSimpleEvents() && component.clickEvent()!!.action() == ClickEvent.Action.OPEN_URL) {
                    definitions.add(component.clickEvent()!!.value())
                } else {
                    definitions.add(component.clickEvent()!!.action().toString().lowercase(Locale.ROOT) + "=" + component.clickEvent()!!.value())
                }
            }
            if (component.hoverEvent() != null) {
                val sbi = StringBuilder()
                if (preferSimpleEvents()) {
                    if (component.hoverEvent()!!.action() == HoverEvent.Action.SHOW_TEXT &&
                        (component.clickEvent() == null || component.clickEvent()!!.action() != ClickEvent.Action.OPEN_URL)
                    ) {
                        sbi.append(MineDown.HOVER_PREFIX)
                    }
                } else {
                    sbi.append(component.hoverEvent()!!.action().toString().lowercase(Locale.ROOT)).append('=')
                }
                val hoverEvent = component.hoverEvent()!!
                when (val value = hoverEvent.value()) {
                    is Component -> {
                        sbi.append(copy().stringify(value))
                    }
                    is HoverEvent.ShowEntity -> {
                        sb.append(value.id()).append(":").append(value.type())
                        if (value.name() != null) {
                            sb.append(" ").append(stringify(value.name()!!))
                        }
                    }
                    is HoverEvent.ShowItem -> {
                        sb.append(value.item())
                        if (value.count() > 0) {
                            sb.append("*").append(value.count())
                        }
                        if (value.nbt() != null) {
                            sb.append(" ").append(value.nbt()!!.string())
                        }
                    }
                }
                definitions.add(sbi.toString())
            }
            sb.append(definitions.joinToString(" "))
            sb.append(')')
        } else {
            appendFormatSuffix(sb, component)
        }
        return sb.toString()
    }

    private fun appendText(sb: StringBuilder, component: Component) {
        when (component) {
            is TextComponent -> {
                sb.append(component.content())
                return
            }
            is TranslatableComponent -> {
                try {
                    sb.append(component.fallback())
                } catch (ignored: NoSuchMethodError) {
                    // version without fallback
                }
            }
            else -> {
                throw UnsupportedOperationException("Cannot stringify ${component.javaClass.typeName} yet! Only TextComponents are supported right now. Sorry. :(")
            }
        }
    }

    private fun appendColor(sb: StringBuilder, color: TextColor?) {
        if (this.color != color) {
            this.color = color
            if (useLegacyColors()) {
                if (color == null) {
                    sb.append(colorChar()).append(Util.TextControl.RESET.getChar())
                } else {
                    try {
                        val colorChar = Util.getLegacyFormatChar(color)
                        sb.append(colorChar()).append(colorChar)
                    } catch (e: IllegalArgumentException) {
                        println(e.message)
                    }
                }
            } else if (color is NamedTextColor) {
                sb.append(colorChar()).append(color.toString()).append(colorChar())
            } else if (color != null) {
                sb.append(colorChar()).append(color.asHexString()).append(colorChar())
            } else {
                sb.append(colorChar()).append(Util.TextControl.RESET.name).append(colorChar())
            }
        }
    }

    private fun appendFormat(sb: StringBuilder, component: Component) {
        val formats = Util.getFormats(component, true).toMutableSet()
        if (!formats.containsAll(this.formats)) {
            if (useLegacyFormatting()) {
                sb.append(colorChar()).append(Util.TextControl.RESET.getChar())
            } else {
                val formatDeque = ArrayDeque(this.formats)
                while (formatDeque.isNotEmpty()) {
                    val format = formatDeque.pollLast()
                    if (!formats.contains(format)) {
                        sb.append(MineDown.getFormatString(format))
                    }
                }
            }
        } else {
            formats.removeAll(this.formats)
        }
        for (format in formats) {
            if (useLegacyFormatting()) {
                try {
                    val colorChar = Util.getLegacyFormatChar(format)
                    sb.append(colorChar()).append(colorChar)
                } catch (e: IllegalArgumentException) {
                    println(e.message)
                }
            } else {
                sb.append(MineDown.getFormatString(format))
            }
        }
        this.formats.clear()
        this.formats.addAll(formats)
    }

    private fun appendFormatSuffix(sb: StringBuilder, component: Component) {
        if (!useLegacyFormatting()) {
            val formats = Util.getFormats(component, true)
            for (format in formats) {
                sb.append(MineDown.getFormatString(format))
            }
            this.formats.removeAll(formats)
        }
    }

    /**
     * Copy all the parser's setting to a new instance
     * @return The new parser instance with all settings copied
     */
    fun copy(): MineDownStringifier = MineDownStringifier().copy(this)

    /**
     * Copy all the parser's settings from another parser
     * @param from The stringifier to copy from
     * @return This stringifier's instance
     */
    fun copy(from: MineDownStringifier): MineDownStringifier {
        useLegacyColors(from.useLegacyColors())
        useLegacyFormatting(from.useLegacyFormatting())
        preferSimpleEvents(from.preferSimpleEvents())
        formattingInEventDefinition(from.formattingInEventDefinition())
        colorInEventDefinition(from.colorInEventDefinition())
        colorChar(from.colorChar())
        return this
    }

    /**
     * Get whether or not to use legacy color codes
     * @return whether or not to use legacy color codes when possible (Default: true)
     */
    fun useLegacyColors(): Boolean = useLegacyColors

    /**
     * Set whether or not to use legacy color codes
     * @param useLegacyColors Whether or not to use legacy colors (Default: true)
     * @return The MineDownStringifier instance
     */
    fun useLegacyColors(useLegacyColors: Boolean): MineDownStringifier {
        this.useLegacyColors = useLegacyColors
        return this
    }

    /**
     * Get whether or not to translate legacy formatting codes over MineDown ones
     * @return whether or not to use legacy formatting codes (Default: false)
     */
    fun useLegacyFormatting(): Boolean = useLegacyFormatting

    /**
     * Set whether or not to translate legacy formatting codes over MineDown ones
     * @param useLegacyFormatting Whether or not to translate legacy formatting codes (Default: false)
     * @return The MineDownStringifier instance
     */
    fun useLegacyFormatting(useLegacyFormatting: Boolean): MineDownStringifier {
        this.useLegacyFormatting = useLegacyFormatting
        return this
    }

    /**
     * Get whether or not to use simple event definitions or specific ones (Default: true)
     * @return whether or not to use simple events
     */
    fun preferSimpleEvents(): Boolean = preferSimpleEvents

    /**
     * Set whether or not to use simple event definitions or specific ones
     * @param preferSimpleEvents Whether or not to prefer simple events (Default: true)
     * @return The MineDownStringifier instance
     */
    fun preferSimpleEvents(preferSimpleEvents: Boolean): MineDownStringifier {
        this.preferSimpleEvents = preferSimpleEvents
        return this
    }

    /**
     * Get whether or not to put colors in event definitions or use inline color definitions
     * @return whether or not to put colors in event definitions (Default: false)
     */
    fun colorInEventDefinition(): Boolean = colorInEventDefinition

    /**
     * Set whether or not to put colors in event definitions or use inline color definitions
     * @param colorInEventDefinition Whether or not to put colors in event definitions (Default: false)
     * @return The MineDownStringifier instance
     */
    fun colorInEventDefinition(colorInEventDefinition: Boolean): MineDownStringifier {
        this.colorInEventDefinition = colorInEventDefinition
        return this
    }

    /**
     * Get whether or not to put formatting in event definitions or use inline formatting definitions
     * @return whether or not to put formatting in event definitions (Default: false)
     */
    fun formattingInEventDefinition(): Boolean = formattingInEventDefinition

    /**
     * Set whether or not to put formatting in event definitions or use inline formatting definitions
     * @param formattingInEventDefinition Whether or not to put formatting in event definitions (Default: false)
     * @return The MineDownStringifier instance
     */
    fun formattingInEventDefinition(formattingInEventDefinition: Boolean): MineDownStringifier {
        this.formattingInEventDefinition = formattingInEventDefinition
        return this
    }

    /**
     * Get the character to use as a special color code. (Default: ampersand &)
     * @return the color character
     */
    fun colorChar(): Char = colorChar

    /**
     * Set the character to use as a special color code.
     * @param colorChar The character to be used as the color char (for legacy and MineDown colors, default: ampersand &)
     * @return The MineDownStringifier instance
     */
    fun colorChar(colorChar: Char): MineDownStringifier {
        this.colorChar = colorChar
        return this
    }
}
