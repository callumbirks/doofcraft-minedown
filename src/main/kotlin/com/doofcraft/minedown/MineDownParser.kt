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
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentBuilder
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.*
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.*
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class MineDownParser {

    /**
     * The character to use as a special color code. (Default: ampersand &)
     */
    private var colorChar: Char = '&'

    /**
     * All enabled options
     */
    private var enabledOptions: MutableSet<Option> = EnumSet.of(
        Option.LEGACY_COLORS,
        Option.SIMPLE_FORMATTING,
        Option.ADVANCED_FORMATTING
    )

    /**
     * All filters
     */
    private var filteredOptions: MutableSet<Option> = EnumSet.noneOf(Option::class.java)

    /**
     * Whether to accept malformed strings or not (Default: false)
     */
    private var lenient: Boolean = false

    /**
     * Detect urls in strings and add events to them? (Default: true)
     */
    private var urlDetection: Boolean = true

    /**
     * The text to display when hovering over an URL. Has a %url% placeholder.
     */
    private var urlHoverText: String = "Click to open url"

    /**
     * Automatically add http to values of open_url when there doesn't exist any? (Default: true)
     */
    private var autoAddUrlPrefix: Boolean = true

    /**
     * The max width the hover text should have.
     * Minecraft itself will wrap after 60 characters.
     * Won't apply if the text already includes new lines.
     */
    private var hoverTextWidth: Int = 60

    private var builder: ComponentBuilder<*, *>? = null
    private var value: StringBuilder = StringBuilder()
    private var translationKey: String? = null
    private var translationArgs: MutableList<Component> = ArrayList()
    private var font: String? = null
    private var insertion: String? = null
    private var rainbowPhase: Int? = null
    private var colors: MutableList<Map.Entry<TextColor, Boolean>>? = null
    private var shadow: ShadowColor? = null
    private var format: MutableMap<TextDecoration, Boolean> = HashMap()
    private var formattingIsLegacy: Boolean = false
    private var clickEvent: ClickEvent? = null
    private var hoverEvent: HoverEvent<*>? = null

    init {
        reset()
    }

    /**
     * Create a ComponentBuilder by parsing a MineDown message
     * @param message The message to parse
     * @return The parsed ComponentBuilder
     * @throws IllegalArgumentException Thrown when a parsing error occurs and lenient is set to false
     */
    @Throws(IllegalArgumentException::class)
    fun parse(message: String): ComponentBuilder<*, *> {
        val urlMatcher = if (urlDetection()) URL_PATTERN.matcher(message) else null
        var escaped = false
        var i = 0
        while (i < message.length) {
            val c = message[i]

            val isEscape = c == '\\' && i + 1 < message.length
            val isColorCode = isEnabled(Option.LEGACY_COLORS) &&
                    i + 1 < message.length && (c == '§' || c == colorChar())
            var eventEndIndex = -1
            var eventDefinition: String? = null
            if (!escaped && isEnabled(Option.ADVANCED_FORMATTING) && c == '[') {
                eventEndIndex = Util.getUnescapedEndIndex(message, '[', ']', i)
                if (eventEndIndex != -1 && message.length > eventEndIndex + 1 && message[eventEndIndex + 1] == '(') {
                    val definitionClose = Util.getUnescapedEndIndex(message, '(', ')', eventEndIndex + 1)
                    if (definitionClose != -1) {
                        eventDefinition = message.substring(eventEndIndex + 2, definitionClose)
                    }
                }
            }
            val isFormatting = isEnabled(Option.SIMPLE_FORMATTING) &&
                    (c == '_' || c == '*' || c == '~' || c == '?' || c == '#') && Util.isDouble(message, i) &&
                    message.indexOf("$c$c", i + 2) != -1

            when {
                escaped -> {
                    escaped = false
                }
                isEscape -> {
                    escaped = true
                    i++
                    continue
                }
                isColorCode -> {
                    i++
                    var code = message[i]
                    if (code in 'A'..'Z') {
                        code = code.lowercaseChar()
                    }
                    var rainbowPhase: Int? = null
                    var encoded: MutableList<Map.Entry<TextFormat, Boolean>>? = null
                    var filterOption: Option? = null
                    val colorString = StringBuilder()
                    for (j in i until message.length) {
                        val c1 = message[j]
                        if (c1 == c && colorString.length > 1) {
                            val colorStr = colorString.toString()
                            rainbowPhase = parseRainbow(colorStr, "", lenient())
                            if (rainbowPhase == null && !colorStr.contains("=")) {
                                encoded = parseFormat(colorStr, "", true)
                                if (encoded.isEmpty()) {
                                    encoded = null
                                } else {
                                    filterOption = Option.SIMPLE_FORMATTING
                                    i = j
                                }
                            } else {
                                filterOption = Option.SIMPLE_FORMATTING
                                i = j
                            }
                            break
                        }
                        if (c1 != '_' && c1 != '#' && c1 != '-' && c1 != ',' && c1 != ':' &&
                            c1 !in 'A'..'Z' && c1 !in 'a'..'z' && c1 !in '0'..'9'
                        ) {
                            break
                        }
                        colorString.append(c1)
                    }
                    if (rainbowPhase == null && encoded == null) {
                        val format = Util.getFormatFromLegacy(code)
                        if (format != null) {
                            filterOption = Option.LEGACY_COLORS
                            encoded = ArrayList()
                            encoded.add(SimpleImmutableEntry(format, true))
                        }
                    }

                    if (rainbowPhase != null || encoded != null) {
                        if (!isFiltered(filterOption)) {
                            if (encoded != null && encoded.size == 1) {
                                val single = encoded.iterator().next()
                                when {
                                    single.key == Util.TextControl.RESET -> {
                                        if (builder() == null && ((format() != null && format()!!.isNotEmpty()) || (colors() != null && colors()!!.isNotEmpty()))) {
                                            builder(Component.text())
                                        }
                                        appendValue()
                                        colors(ArrayList())
                                        rainbowPhase(null)
                                        format(HashMap())
                                    }
                                    single.key is TextColor -> {
                                        if (value().isNotEmpty()) {
                                            if (builder() == null && format() != null && format()!!.isNotEmpty()) {
                                                builder(Component.text())
                                            }
                                            appendValue()
                                        }
                                        colors(ArrayList())
                                        colors()!!.add(SimpleImmutableEntry(single.key as TextColor, single.value))
                                        rainbowPhase(null)
                                        if (formattingIsLegacy()) {
                                            format(HashMap())
                                        }
                                    }
                                    single.key is TextDecoration -> {
                                        if (value.isNotEmpty()) {
                                            appendValue()
                                        }
                                        formattingIsLegacy(true)
                                        format()[single.key as TextDecoration] = single.value
                                    }
                                }
                            } else {
                                if (value().isNotEmpty()) {
                                    appendValue()
                                }
                                rainbowPhase(rainbowPhase)
                                if (encoded != null) {
                                    val colors = ArrayList<Map.Entry<TextColor, Boolean>>()
                                    for (e in encoded) {
                                        if (e.key is TextColor) {
                                            colors.add(SimpleImmutableEntry(e.key as TextColor, e.value))
                                        }
                                    }
                                    colors(colors)
                                } else {
                                    colors(null)
                                }
                                if (formattingIsLegacy()) {
                                    format(HashMap())
                                }
                            }
                        }
                    } else {
                        value().append(c).append(code)
                    }
                    i++
                    continue
                }
                eventEndIndex != -1 && eventDefinition != null -> {
                    appendValue()
                    if (!isFiltered(Option.ADVANCED_FORMATTING) && eventDefinition.isNotEmpty()) {
                        append(parseEvent(message.substring(i + 1, eventEndIndex), eventDefinition))
                    } else {
                        append(copy(true).parse(message.substring(i + 1, eventEndIndex)))
                    }
                    i = eventEndIndex + 2 + eventDefinition.length
                    continue
                }
                isFormatting -> {
                    val endIndex = message.indexOf("$c$c", i + 2)
                    val formats = HashMap(format())
                    if (!isFiltered(Option.SIMPLE_FORMATTING)) {
                        formats[MineDown.getFormatFromChar(c)] = true
                    }
                    formattingIsLegacy(false)
                    appendValue()
                    append(copy(true).format(formats).parse(message.substring(i + 2, endIndex)))
                    i = endIndex + 2
                    continue
                }
            }

            // URL
            if (urlDetection() && urlMatcher != null) {
                var urlEnd = message.indexOf(' ', i)
                if (urlEnd == -1) {
                    urlEnd = message.length
                }
                if (urlMatcher.region(i, urlEnd).find()) {
                    appendValue()
                    value(StringBuilder(message.substring(i, urlEnd)))
                    appendValue()
                    i = urlEnd - 1
                    i++
                    continue
                }
            }

            // It's normal text, just append the character
            value().append(message[i])
            i++
        }
        if (escaped) {
            value().append('\\')
        }
        appendValue()
        if (builder() == null) {
            builder(Component.text())
        }
        return builder()!!
    }

    private fun append(builder: ComponentBuilder<*, *>) {
        if (builder() == null) {
            builder(Component.text().append(builder))
        } else {
            builder()!!.append(builder)
        }
    }

    private fun appendValue() {
        val builder: ComponentBuilder<*, *>
        val applicableColors: List<TextColor>
        var valueCodepointLength = value().length.toLong()
        // If the value is empty don't add anything
        if (valueCodepointLength == 0L) {
            return
        }
        applicableColors = if (rainbowPhase() != null) {
            // Rainbow colors
            valueCodepointLength = value().codePoints().count()
            Util.createRainbow(valueCodepointLength, rainbowPhase()!!)
        } else if (colors() != null) {
            if (colors()!!.size > 1) {
                valueCodepointLength = value().codePoints().count()
                Util.createGradient(
                    valueCodepointLength,
                    colors!!.filter { it.value }.map { it.key }
                )
            } else {
                colors!!.map { it.key }
            }
        } else {
            ArrayList()
        }

        builder = if (applicableColors.size > 1 && translationKey() == null) {
            // Colors need to have a gradient/rainbow applied
            Component.text()
        } else {
            // Single color mode
            if (translationKey() != null) {
                try {
                    Component.translatable(translationKey()!!, value().toString(), translationArgs()).toBuilder()
                } catch (e: NoSuchMethodError) {
                    // Adventure version without fallback
                    Component.translatable(translationKey()!!, translationArgs()).toBuilder()
                }
            } else {
                Component.text(value().toString()).toBuilder()
            }.also { b ->
                if (applicableColors.size == 1) {
                    b.color(applicableColors[0])
                }
            }
        }

        if (shadow() != null) {
            builder.shadowColor(shadow())
        }

        if (font() != null) {
            builder.font(Key.key(font()!!))
        }
        builder.insertion(insertion())
        Util.applyFormat(builder, format)
        if (urlDetection() && URL_PATTERN.matcher(value).matches()) {
            var v = value.toString()
            if (!v.startsWith("http://") && !v.startsWith("https://")) {
                v = "http://$v"
            }
            builder.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, v))
            if (urlHoverText() != null && urlHoverText()!!.isNotEmpty()) {
                builder.hoverEvent(
                    HoverEvent.hoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        MineDown(urlHoverText()!!).replace("url", value.toString()).toComponent()
                    )
                )
            }
        }
        if (clickEvent() != null) {
            builder.clickEvent(clickEvent())
        }
        if (hoverEvent() != null) {
            builder.hoverEvent(hoverEvent())
        }

        if (applicableColors.size > 1) {
            val stepLength = Math.round(valueCodepointLength.toDouble() / applicableColors.size).toInt()
            val component = Component.empty().toBuilder()

            val sb = StringBuilder()
            var colorIndex = 0
            var steps = 0

            val it = value().codePoints().iterator()
            while (it.hasNext()) {
                sb.appendCodePoint(it.nextInt())
                if (++steps == stepLength) {
                    steps = 0
                    component.append(Component.text(sb.toString()).color(applicableColors[colorIndex++]))
                    sb.clear()
                }
            }
            builder.append(component)
        }
        if (builder() == null) {
            builder(builder)
        } else {
            builder()!!.append(builder)
        }
        value(StringBuilder())
    }

    /**
     * Parse a MineDown event string
     * @param text        The display text
     * @param definitions The event definition string
     * @return The parsed ComponentBuilder for this string
     */
    fun parseEvent(text: String, definitions: String): ComponentBuilder<*, *> {
        val defParts = mutableListOf<String>()
        if (definitions.startsWith(" ")) {
            defParts.add("")
        }
        defParts.addAll(definitions.split(" "))
        if (definitions.endsWith(" ")) {
            defParts.add("")
        }
        var rainbowPhase: Int? = null
        var colors: MutableList<Map.Entry<TextColor, Boolean>>? = null
        var shadowColor: ShadowColor? = null
        var translationKey: String? = null
        val translationArgs = mutableListOf<Component>()
        var font: String? = null
        var insertion: String? = null
        val formats = HashMap<TextDecoration, Boolean>()
        var clickEvent: ClickEvent? = null
        var hoverEvent: HoverEvent<*>? = null

        var formatEnd = -1

        var i = 0
        while (i < defParts.size) {
            val definition = defParts[i]
            val parsedRainbowPhase = parseRainbow(definition, "", lenient())
            if (parsedRainbowPhase != null) {
                rainbowPhase = parsedRainbowPhase
                i++
                continue
            } else if (!definition.contains("=")) {
                val parsed = parseFormat(definition, "", true)
                if (parsed.isNotEmpty()) {
                    for (e in parsed) {
                        if (e.key is TextColor) {
                            if (colors == null) {
                                colors = ArrayList()
                            }
                            colors.add(SimpleImmutableEntry(e.key as TextColor, e.value))
                        } else if (e.key is TextDecoration) {
                            formats[e.key as TextDecoration] = e.value
                        }
                    }
                    formatEnd = i
                    i++
                    continue
                }
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.TRANSLATE_PREFIX)) {
                translationKey = definition.substring(MineDown.TRANSLATE_PREFIX.length)
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.WITH_PREFIX)) {
                val iRef = IntArray(1) { i }
                val args = getValue(iRef, definition.substring(MineDown.WITH_PREFIX.length), defParts, true).split("(?<!\\\\),".toRegex())
                for (arg in args) {
                    translationArgs.add(copy(false).urlDetection(false).parse(arg).build())
                }
                i = iRef[0]
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.FONT_PREFIX)) {
                font = definition.substring(MineDown.FONT_PREFIX.length)
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.INSERTION_PREFIX)) {
                val iRef = IntArray(1) { i }
                insertion = getValue(iRef, definition.substring(MineDown.INSERTION_PREFIX.length), defParts, true)
                i = iRef[0]
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.COLOR_PREFIX)) {
                val colorRainbowPhase = parseRainbow(definition, MineDown.COLOR_PREFIX, lenient())
                if (colorRainbowPhase == null) {
                    val parsed = parseFormat(definition, MineDown.COLOR_PREFIX, lenient())
                    colors = ArrayList()
                    for (e in parsed) {
                        if (e.key is TextColor) {
                            colors.add(SimpleImmutableEntry(e.key as TextColor, e.value))
                        } else if (!lenient()) {
                            throw IllegalArgumentException("${e.key} is a format and not a color!")
                        }
                    }
                } else {
                    rainbowPhase = colorRainbowPhase
                }
                formatEnd = i
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.SHADOW_PREFIX)) {
                val parsed = parseShadow(definition, MineDown.SHADOW_PREFIX, lenient())
                if (parsed != null) {
                    shadowColor = parsed
                } else if (!lenient()) {
                    throw IllegalArgumentException("Invalid shadow definition: $definition")
                }
                formatEnd = i
                i++
                continue
            }

            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.FORMAT_PREFIX)) {
                val parsed = parseFormat(definition, MineDown.FORMAT_PREFIX, lenient())
                for (e in parsed) {
                    if (e.key is TextDecoration) {
                        formats[e.key as TextDecoration] = e.value
                    } else if (!lenient()) {
                        throw IllegalArgumentException("${e.key} is a color and not a format!")
                    }
                }
                formatEnd = i
                i++
                continue
            }

            if (i == formatEnd + 1 && URL_PATTERN.matcher(definition).matches()) {
                var def = definition
                if (!def.startsWith("http://") && !def.startsWith("https://")) {
                    def = "http://$def"
                }
                clickEvent = ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, def)
                i++
                continue
            }

            var clickAction: ClickEvent.Action? = if (definition.startsWith("/")) ClickEvent.Action.RUN_COMMAND else null
            var hoverAction: HoverEvent.Action<*>? = null
            if (definition.lowercase(Locale.ROOT).startsWith(MineDown.HOVER_PREFIX)) {
                hoverAction = HoverEvent.Action.SHOW_TEXT
            }
            val parts = definition.split("=", limit = 2)
            if (hoverAction == null) {
                hoverAction = HoverEvent.Action.NAMES.value(parts[0].lowercase(Locale.ROOT))
            }
            try {
                clickAction = ClickEvent.Action.valueOf(parts[0].uppercase(Locale.ROOT))
            } catch (ignored: IllegalArgumentException) {
            }

            val iRef = IntArray(1) { i }
            val valueStr = getValue(iRef, if (parts.size > 1) parts[1] else "", defParts, clickAction != null || hoverAction != null)
            i = iRef[0]

            if (clickAction != null) {
                var finalValue = valueStr
                if (autoAddUrlPrefix() && clickAction == ClickEvent.Action.OPEN_URL && !finalValue.startsWith("http://") && !finalValue.startsWith("https://")) {
                    finalValue = "http://$finalValue"
                }
                clickEvent = ClickEvent.clickEvent(clickAction, finalValue)
            } else if (hoverAction == null) {
                hoverAction = HoverEvent.Action.SHOW_TEXT
            }
            if (hoverAction != null) {
                when (hoverAction) {
                    HoverEvent.Action.SHOW_TEXT -> {
                        hoverEvent = HoverEvent.hoverEvent(hoverAction, copy(false).urlDetection(false).parse(Util.wrap(valueStr, hoverTextWidth())).build())
                    }
                    HoverEvent.Action.SHOW_ENTITY -> {
                        val valueParts = valueStr.split(":", limit = 2)
                        try {
                            val additionalParts = valueParts[1].split(" ", limit = 2)
                            var entityType = additionalParts[0]
                            if (!entityType.contains(":")) {
                                entityType = "minecraft:$entityType"
                            }
                            hoverEvent = HoverEvent.showEntity(
                                HoverEvent.ShowEntity.showEntity(
                                    Key.key(entityType), UUID.fromString(valueParts[0]),
                                    if (additionalParts.size > 1 && additionalParts[1] != null)
                                        copy(false).urlDetection(false).parse(additionalParts[1]).build()
                                    else null
                                )
                            )
                        } catch (e: Exception) {
                            if (!lenient()) {
                                if (valueParts.size < 2) {
                                    throw IllegalArgumentException("Invalid entity definition. Needs to be of format uuid:id or uuid:namespace:id!")
                                }
                                throw IllegalArgumentException(e.message)
                            }
                        }
                    }
                    HoverEvent.Action.SHOW_ITEM -> {
                        val valueParts = valueStr.split(" ", limit = 2)
                        var id = valueParts[0]
                        if (!id.contains(":")) {
                            id = "minecraft:$id"
                        }
                        var count = 1
                        val countIndex = valueParts[0].indexOf('*')
                        if (countIndex > 0 && countIndex + 1 < valueParts[0].length) {
                            try {
                                count = valueParts[0].substring(countIndex + 1).toInt()
                                id = valueParts[0].substring(0, countIndex)
                            } catch (e: NumberFormatException) {
                                if (!lenient()) {
                                    throw IllegalArgumentException(e.message)
                                }
                            }
                        }
                        var tag: BinaryTagHolder? = null
                        if (valueParts.size > 1 && valueParts[1] != null) {
                            tag = BinaryTagHolder.binaryTagHolder(valueParts[1])
                        }

                        try {
                            hoverEvent = HoverEvent.showItem(
                                HoverEvent.ShowItem.showItem(
                                    Key.key(id), count, tag
                                )
                            )
                        } catch (e: Exception) {
                            if (!lenient()) {
                                throw IllegalArgumentException(e.message)
                            }
                        }
                    }
                }
            }
            i++
        }

        if (clickEvent != null && hoverEvent == null) {
            hoverEvent = HoverEvent.hoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Component.text()
                    .append(Component.text(clickEvent.action().toString().lowercase(Locale.ROOT).replace('_', ' ')))
                    .color(NamedTextColor.BLUE)
                    .append(Component.text(" ${clickEvent.value()}")).color(NamedTextColor.WHITE)
                    .build()
            )
        }

        return copy()
            .urlDetection(false)
            .translationKey(translationKey)
            .translationArgs(translationArgs)
            .rainbowPhase(rainbowPhase)
            .colors(colors)
            .shadow(shadowColor)
            .font(font)
            .insertion(insertion)
            .format(formats)
            .clickEvent(clickEvent)
            .hoverEvent(hoverEvent)
            .parse(text)
    }

    private fun getValue(iRef: IntArray, firstPart: String, defParts: List<String>, hasAction: Boolean): String {
        var i = iRef[0]
        var bracketDepth = if (firstPart.isNotEmpty() && firstPart.startsWith("{") && hasAction) 1 else 0

        val value = StringBuilder()
        if (firstPart.isNotEmpty() && hasAction) {
            if (bracketDepth > 0) {
                value.append(firstPart.substring(1))
            } else {
                value.append(firstPart)
            }
        } else {
            value.append(defParts[i])
        }

        i++
        while (i < defParts.size) {
            val part = defParts[i]
            if (bracketDepth == 0) {
                val equalsIndex = part.indexOf('=')
                if (equalsIndex > 0 && !Util.isEscaped(part, equalsIndex)) {
                    i--
                    break
                }
            }
            value.append(" ")
            if (bracketDepth > 0) {
                val startBracketIndex = part.indexOf("={")
                if (startBracketIndex > 0 && !Util.isEscaped(part, startBracketIndex) && !Util.isEscaped(part, startBracketIndex + 1)) {
                    bracketDepth++
                }
                if (part.endsWith("}") && !Util.isEscaped(part, part.length - 1)) {
                    bracketDepth--
                    if (bracketDepth == 0) {
                        value.append(part.substring(0, part.length - 1))
                        break
                    }
                }
            }
            value.append(part)
            i++
        }

        iRef[0] = i
        return value.toString()
    }

    protected fun builder(): ComponentBuilder<*, *>? = builder

    protected fun builder(builder: ComponentBuilder<*, *>?): MineDownParser {
        this.builder = builder
        return this
    }

    protected fun value(value: StringBuilder): MineDownParser {
        this.value = value
        return this
    }

    protected fun value(): StringBuilder = value

    fun translationKey(translationKey: String?): MineDownParser {
        this.translationKey = translationKey
        return this
    }

    fun translationKey(): String? = translationKey

    fun translationArgs(translationArgs: List<Component>): MineDownParser {
        this.translationArgs = translationArgs.toMutableList()
        return this
    }

    fun translationArgs(): List<Component> = translationArgs

    private fun font(font: String?): MineDownParser {
        this.font = font
        return this
    }

    protected fun font(): String? = font

    private fun insertion(insertion: String?): MineDownParser {
        this.insertion = insertion
        return this
    }

    protected fun insertion(): String? = insertion

    protected fun colors(colors: MutableList<Map.Entry<TextColor, Boolean>>?): MineDownParser {
        this.colors = colors
        return this
    }

    protected fun rainbowPhase(rainbowPhase: Int?): MineDownParser {
        this.rainbowPhase = rainbowPhase
        return this
    }

    protected fun rainbowPhase(): Int? = rainbowPhase

    protected fun colors(): MutableList<Map.Entry<TextColor, Boolean>>? = colors

    protected fun shadow(shadow: ShadowColor?): MineDownParser {
        this.shadow = shadow
        return this
    }

    protected fun shadow(): ShadowColor? = shadow

    protected fun format(format: MutableMap<TextDecoration, Boolean>): MineDownParser {
        this.format = format
        return this
    }

    protected fun format(): MutableMap<TextDecoration, Boolean> = format

    protected fun formattingIsLegacy(formattingIsLegacy: Boolean): MineDownParser {
        this.formattingIsLegacy = formattingIsLegacy
        return this
    }

    protected fun formattingIsLegacy(): Boolean = formattingIsLegacy

    protected fun clickEvent(clickEvent: ClickEvent?): MineDownParser {
        this.clickEvent = clickEvent
        return this
    }

    protected fun clickEvent(): ClickEvent? = clickEvent

    protected fun hoverEvent(hoverEvent: HoverEvent<*>?): MineDownParser {
        this.hoverEvent = hoverEvent
        return this
    }

    protected fun hoverEvent(): HoverEvent<*>? = hoverEvent

    /**
     * Copy all the parser's setting to a new instance
     * @return The new parser instance with all settings copied
     */
    fun copy(): MineDownParser = copy(false)

    /**
     * Copy all the parser's setting to a new instance
     * @param formatting Should the formatting be copied too?
     * @return The new parser instance with all settings copied
     */
    fun copy(formatting: Boolean): MineDownParser = MineDownParser().copy(this, formatting)

    /**
     * Copy all the parser's settings from another parser.
     * @param from The parser to copy from
     * @return This parser's instance
     */
    fun copy(from: MineDownParser): MineDownParser = copy(from, false)

    /**
     * Copy all the parser's settings from another parser.
     * @param from       The parser to copy from
     * @param formatting Should the formatting be copied too?
     * @return This parser's instance
     */
    fun copy(from: MineDownParser, formatting: Boolean): MineDownParser {
        lenient(from.lenient())
        urlDetection(from.urlDetection())
        urlHoverText(from.urlHoverText())
        autoAddUrlPrefix(from.autoAddUrlPrefix())
        hoverTextWidth(from.hoverTextWidth())
        enabledOptions(from.enabledOptions())
        filteredOptions(from.filteredOptions())
        colorChar(from.colorChar())
        if (formatting) {
            format(from.format())
            formattingIsLegacy(from.formattingIsLegacy())
            rainbowPhase(from.rainbowPhase())
            colors(from.colors())
            clickEvent(from.clickEvent())
            hoverEvent(from.hoverEvent())
        }
        return this
    }

    /**
     * Reset the parser state to the start
     * @return The parser's instance
     */
    fun reset(): MineDownParser {
        builder = null
        value = StringBuilder()
        translationKey = null
        translationArgs.clear()
        font = null
        insertion = null
        rainbowPhase = null
        colors = null
        shadow = null
        format = HashMap()
        clickEvent = null
        hoverEvent = null
        return this
    }

    /**
     * Whether or not to translate legacy color codes (Default: true)
     * @return Whether or not to translate legacy color codes (Default: true)
     * @deprecated Use [isEnabled] instead
     */
    @Deprecated("Use isEnabled(Option) instead")
    fun translateLegacyColors(): Boolean = isEnabled(Option.LEGACY_COLORS)

    /**
     * Whether or not to translate legacy color codes
     * @return The parser
     * @deprecated Use [enable] and [disable] instead
     */
    @Deprecated("Use enable(Option) and disable(Option) instead")
    fun translateLegacyColors(enabled: Boolean): MineDownParser =
        if (enabled) enable(Option.LEGACY_COLORS) else disable(Option.LEGACY_COLORS)

    /**
     * Check whether or not an option is enabled
     * @param option The option to check for
     * @return `true` if it's enabled; `false` if not
     */
    fun isEnabled(option: Option): Boolean = enabledOptions().contains(option)

    /**
     * Enable an option.
     * @param option The option to enable
     * @return The parser instance
     */
    fun enable(option: Option): MineDownParser {
        enabledOptions().add(option)
        return this
    }

    /**
     * Disable an option. Disabling an option will stop the parser from replacing
     * this option's chars in the string. Use [filter] to completely
     * remove the characters used by this option from the message instead.
     * @param option The option to disable
     * @return The parser instance
     */
    fun disable(option: Option): MineDownParser {
        enabledOptions().remove(option)
        return this
    }

    /**
     * Check whether or not an option is filtered
     * @param option The option to check for
     * @return `true` if it's enabled; `false` if not
     */
    fun isFiltered(option: Option?): Boolean = filteredOptions().contains(option)

    /**
     * Filter an option. This enables the parsing of an option and completely
     * removes the characters of this option from the string.
     * @param option The option to add to the filter
     * @return The parser instance
     */
    fun filter(option: Option): MineDownParser {
        filteredOptions().add(option)
        enabledOptions().add(option)
        return this
    }

    /**
     * Unfilter an option. Does not enable it!
     * @param option The option to remove from the filter
     * @return The parser instance
     */
    fun unfilter(option: Option): MineDownParser {
        filteredOptions().remove(option)
        return this
    }

    /**
     * Escape formatting in the string depending on this parser's options. This will escape backslashes too!
     * @param string The string to escape
     * @return The string with all formatting of this parser escaped
     */
    fun escape(string: String): String {
        val value = StringBuilder()
        for (i in string.indices) {
            val c = string[i]

            val isEscape = c == '\\'
            val isColorCode = isEnabled(Option.LEGACY_COLORS) &&
                    i + 1 < string.length && (c == '§' || c == colorChar())
            val isEvent = isEnabled(Option.ADVANCED_FORMATTING) && c == '['
            val isFormatting = isEnabled(Option.SIMPLE_FORMATTING) &&
                    (c == '_' || c == '*' || c == '~' || c == '?' || c == '#') && Util.isDouble(string, i)

            if (isEscape || isColorCode || isEvent || isFormatting) {
                value.append('\\')
            }
            value.append(c)
        }
        return value.toString()
    }

    enum class Option {
        /**
         * Translate simple, in-line MineDown formatting in strings? (Default: true)
         */
        SIMPLE_FORMATTING,

        /**
         * Translate advanced MineDown formatting (e.g. events) in strings? (Default: true)
         */
        ADVANCED_FORMATTING,

        /**
         * Whether or not to translate legacy color codes (Default: true)
         */
        LEGACY_COLORS
    }

    /**
     * Get The character to use as a special color code.
     * @return The color character (Default: ampersand &)
     */
    fun colorChar(): Char = colorChar

    /**
     * Set the character to use as a special color code.
     * @param colorChar The color char (Default: ampersand &)
     * @return The MineDownParser instance
     */
    fun colorChar(colorChar: Char): MineDownParser {
        this.colorChar = colorChar
        return this
    }

    /**
     * Get all enabled options that will be used when parsing
     * @return a modifiable set of options
     */
    fun enabledOptions(): MutableSet<Option> = enabledOptions

    /**
     * Set all enabled options that will be used when parsing at once, replaces any existing options
     * @param enabledOptions The enabled options
     * @return The MineDownParser instance
     */
    fun enabledOptions(enabledOptions: MutableSet<Option>): MineDownParser {
        this.enabledOptions = enabledOptions
        return this
    }

    /**
     * Get all filtered options that will be parsed and then removed from the string
     * @return a modifiable set of options
     */
    fun filteredOptions(): MutableSet<Option> = filteredOptions

    /**
     * Set all filtered options that will be parsed and then removed from the string at once,
     * replaces any existing options
     * @param filteredOptions The filtered options
     * @return The MineDownParser instance
     */
    fun filteredOptions(filteredOptions: MutableSet<Option>): MineDownParser {
        this.filteredOptions = filteredOptions
        return this
    }

    /**
     * Get whether to accept malformed strings or not
     * @return whether or not the accept malformed strings (Default: false)
     */
    fun lenient(): Boolean = lenient

    /**
     * Set whether to accept malformed strings or not
     * @param lenient Set whether or not to accept malformed string (Default: false)
     * @return The MineDownParser instance
     */
    fun lenient(lenient: Boolean): MineDownParser {
        this.lenient = lenient
        return this
    }

    /**
     * Get whether or not urls in strings are detected and get events added to them?
     * @return whether or not urls are detected (Default: true)
     */
    fun urlDetection(): Boolean = urlDetection

    /**
     * Set whether or not to detect urls in strings and add events to them?
     * @param urlDetection Whether or not to detect urls in strings  (Default: true)
     * @return The MineDownParser instance
     */
    fun urlDetection(urlDetection: Boolean): MineDownParser {
        this.urlDetection = urlDetection
        return this
    }

    /**
     * Get the text to display when hovering over an URL. Has a %url% placeholder.
     */
    fun urlHoverText(): String? = urlHoverText

    /**
     * Set the text to display when hovering over an URL. Has a %url% placeholder.
     * @param urlHoverText The url hover text
     * @return The MineDownParser instance
     */
    fun urlHoverText(urlHoverText: String?): MineDownParser {
        this.urlHoverText = urlHoverText ?: ""
        return this
    }

    /**
     * Get whether or not to automatically add http to values of open_url when there doesn't exist any?
     * @return whether or not to automatically add http to values of open_url when there doesn't exist any? (Default: true)
     */
    fun autoAddUrlPrefix(): Boolean = autoAddUrlPrefix

    /**
     * Set whether or not to automatically add http to values of open_url when there doesn't exist any?
     * @param autoAddUrlPrefix Whether or not automatically add http to values of open_url when there doesn't exist any? (Default: true)
     * @return The MineDownParser instance
     */
    fun autoAddUrlPrefix(autoAddUrlPrefix: Boolean): MineDownParser {
        this.autoAddUrlPrefix = autoAddUrlPrefix
        return this
    }

    /**
     * Get the max width the hover text should have.
     * Minecraft itself will wrap after 60 characters.
     * Won't apply if the text already includes new lines.
     */
    fun hoverTextWidth(): Int = hoverTextWidth

    /**
     * Set the max width the hover text should have.
     * Minecraft itself will wrap after 60 characters.
     * Won't apply if the text already includes new lines.
     * @param hoverTextWidth The url hover text length
     * @return The MineDownParser instance
     */
    fun hoverTextWidth(hoverTextWidth: Int): MineDownParser {
        this.hoverTextWidth = hoverTextWidth
        return this
    }

    companion object {
        private const val RAINBOW = "rainbow"

        val URL_PATTERN: Pattern = Pattern.compile("^(?:(https?)://)?([-\\w_\\.]+\\.[a-z]{2,18})(/\\S*)?\$")

        private fun parseRainbow(colorString: String, prefix: String, lenient: Boolean): Int? {
            if (colorString.substring(prefix.length).lowercase(Locale.ROOT).startsWith(RAINBOW)) {
                return if (colorString.length > prefix.length + RAINBOW.length + 1) {
                    try {
                        colorString.substring(prefix.length + RAINBOW.length + 1).toInt()
                    } catch (e: NumberFormatException) {
                        if (!lenient) throw e
                        null
                    }
                } else {
                    0
                }
            }
            return null
        }

        /**
         * Parse a color/format definition
         * @param colorString The string to parse
         * @param prefix      The color prefix e.g. ampersand (&)
         * @param lenient     Whether or not to accept malformed strings
         * @return The parsed color or `null` if lenient is true and no color was found
         */
        private fun parseFormat(colorString: String, prefix: String, lenient: Boolean): MutableList<Map.Entry<TextFormat, Boolean>> {
            val formats = mutableListOf<Map.Entry<TextFormat, Boolean>>()
            if (prefix.length + 1 == colorString.length) {
                val format = Util.getFormatFromLegacy(colorString[prefix.length])
                if (format == null && !lenient) {
                    throw IllegalArgumentException("${colorString[prefix.length]} is not a valid $prefix char!")
                }
                formats.add(SimpleImmutableEntry(format, true))
            } else {
                for (part in colorString.substring(prefix.length).split("[\\-,]".toRegex())) {
                    if (part.isEmpty()) {
                        continue
                    }
                    var partStr = part
                    val negated = partStr[0] == '!'
                    if (negated) {
                        partStr = partStr.substring(1)
                    }
                    try {
                        val format = Util.getFormatFromString(partStr)
                        formats.add(SimpleImmutableEntry(format, !negated))
                    } catch (e: IllegalArgumentException) {
                        if (!lenient) {
                            throw e
                        }
                    }
                }
            }
            return formats
        }

        /**
         * Parse a color/format definition
         * @param shadowString The string to parse
         * @param prefix       The shadow prefix
         * @param lenient      Whether to accept malformed strings
         * @return The parsed shadow color or `null` if lenient is true and no color was found
         */
        private fun parseShadow(shadowString: String, prefix: String, lenient: Boolean): ShadowColor? {
            if (prefix.length + 1 == shadowString.length) {
                val format = Util.getFormatFromLegacy(shadowString[prefix.length])
                if (format == null && !lenient) {
                    throw IllegalArgumentException("${shadowString[prefix.length]} is not a valid $prefix char!")
                }
                return if (format is TextColor) {
                    ShadowColor.shadowColor(format, MineDown.SHADOW_ALPHA)
                } else if (!lenient) {
                    throw IllegalArgumentException("${shadowString[prefix.length]} is not a valid shadow color!")
                } else {
                    null
                }
            }
            val shadowColor = shadowString.substring(prefix.length)
            if (shadowColor.isEmpty()) {
                return if (!lenient) {
                    throw IllegalArgumentException("No value for the shadow specified!")
                } else {
                    null
                }
            }

            try {
                val format = Util.getFormatFromString(shadowColor)
                if (format is TextColor) {
                    return ShadowColor.shadowColor(format, MineDown.SHADOW_ALPHA)
                } else if (format != null) {
                    if (!lenient) {
                        throw IllegalArgumentException("$shadowColor is not a valid shadow color!")
                    }
                }
            } catch (ignored: IllegalArgumentException) {
            }
            var modShadowColor = shadowColor
            if (shadowColor.startsWith(TextColor.HEX_PREFIX) && shadowColor.length == 5) {
                // support short form which only specifies a single hex for each channel
                modShadowColor = TextColor.HEX_PREFIX + shadowColor[1] + shadowColor[1] +
                        shadowColor[2] + shadowColor[2] +
                        shadowColor[3] + shadowColor[3] +
                        shadowColor[4] + shadowColor[4]
            }
            val shadow = ShadowColor.fromHexString(modShadowColor)
            if (shadow != null) {
                return shadow
            }
            if (!lenient) {
                throw IllegalArgumentException("$shadowColor is not a valid shadow color!")
            }
            return null
        }
    }
}
