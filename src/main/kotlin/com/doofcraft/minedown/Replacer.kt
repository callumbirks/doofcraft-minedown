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
import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * This class offers the ability to replace placeholders with values in strings and components.
 * It also lets you define which placeholders indicators (prefix and suffix) should be used.
 * By default these are the % character.
 */
class Replacer {

    /**
     * The map of placeholders with their typed replacements
     */
    private val replacements: MutableMap<String, ReplacementValue> = LinkedHashMap()

    /**
     * The placeholder indicator's prefix character
     */
    private var placeholderPrefix: String = "%"

    /**
     * The placeholder indicator's suffix character
     */
    private var placeholderSuffix: String = "%"

    /**
     * Replace the placeholder no matter what the case of it is
     */
    private var ignorePlaceholderCase: Boolean = true

    /**
     * Add an array with placeholders and values that should get replaced in the message
     * @param replacements The replacements, nth element is the placeholder, n+1th the placeholder's value
     * @return The Replacer instance
     */
    fun replace(vararg replacements: String): Replacer {
        Util.validate(
            replacements.size % 2 == 0,
            "The replacement length has to be even, mapping i % 2 == 0 to the placeholder and i % 2 = 1 to the placeholder's value"
        )
        var i = 0
        while (i + 1 < replacements.size) {
            replaceText(replacements[i], replacements[i + 1])
            i += 2
        }
        return this
    }

    /**
     * Add a map with placeholders and values that should get replaced in the message
     * @param replacements The replacements mapped placeholder to value
     * @return The Replacer instance
     */
    fun replace(replacements: Map<String, *>?): Replacer {
        if (replacements == null || replacements.isEmpty()) {
            return this
        }

        for ((key, value) in replacements) {
            when (value) {
                null -> replaceText(key, "null")
                is ReplacementValue -> this.replacements[key] = value
                is String -> replaceText(key, value)
                is Component -> replaceComponent(key, value)
                else -> replaceText(key, value.toString())
            }
        }
        return this
    }

    /**
     * Add a placeholder to string mapping that should get replaced in the message
     * @param placeholder The placeholder to replace
     * @param replacement The replacement string
     * @return The Replacer instance
     */
    fun replace(placeholder: String, replacement: String): Replacer = replaceText(placeholder, replacement)

    /**
     * Add a placeholder to component mapping that should get replaced in the message
     * @param placeholder The placeholder to replace
     * @param replacement The replacement component
     * @return The Replacer instance
     */
    fun replace(placeholder: String, replacement: Component): Replacer = replaceComponent(placeholder, replacement)

    /**
     * Add a map with placeholders and typed values that should get replaced in the message
     * @param replacements The replacements mapped placeholder to value
     * @return The Replacer instance
     */
    fun replaceTyped(replacements: Map<String, ReplacementValue>): Replacer {
        this.replacements.putAll(replacements)
        return this
    }

    /**
     * Add a placeholder to typed value mapping that should get replaced in the message
     * @param placeholder The placeholder to replace
     * @param replacement The replacement value
     * @return The Replacer instance
     */
    fun replace(placeholder: String, replacement: ReplacementValue): Replacer {
        this.replacements[placeholder] = replacement
        return this
    }

    /**
     * Add a placeholder to string mapping that should get replaced in the message
     * @param placeholder The placeholder to replace
     * @param text The replacement string
     * @return The Replacer instance
     */
    fun replaceText(placeholder: String, text: String): Replacer {
        this.replacements[placeholder] = ReplacementValue.Text(text)
        return this
    }

    /**
     * Add a placeholder to component mapping that should get replaced in the message
     * @param placeholder The placeholder to replace
     * @param component The replacement component
     * @return The Replacer instance
     */
    fun replaceComponent(placeholder: String, component: Component): Replacer {
        this.replacements[placeholder] = ReplacementValue.ComponentValue(component)
        return this
    }

    /**
     * Add a placeholder to MineDown string mapping that should get replaced in the message.
     * The MineDown string will be parsed into a component before replacement.
     * @param placeholder The placeholder to replace
     * @param mineDown The replacement MineDown string
     * @return The Replacer instance
     */
    fun replaceMinedown(placeholder: String, mineDown: String): Replacer {
        this.replacements[placeholder] = ReplacementValue.MineDownValue(mineDown)
        return this
    }

    /**
     * Set the placeholder indicator for both prefix and suffix
     * @param placeholderIndicator The character to use as a placeholder indicator
     * @return The Replacer instance
     */
    fun placeholderIndicator(placeholderIndicator: String): Replacer {
        placeholderPrefix(placeholderIndicator)
        placeholderSuffix(placeholderIndicator)
        return this
    }

    /**
     * Replace the placeholders in a component list
     * @param components The Component list to replace in
     * @return A copy of the array with the placeholders replaced
     */
    fun replaceIn(components: List<Component?>?): List<Component?>? {
        if (components == null) {
            return null
        }

        return components.map { replaceIn(it) }
    }

    /**
     * Replace the placeholders in a component
     * @param component The Component to replace in
     * @return A copy of the component with the placeholders replaced
     */
    fun replaceIn(component: Component?): Component? {
        if (component == null) {
            return null
        }

        val builder = Component.text()
        var modifiedComponent = component

        if (modifiedComponent is KeybindComponent) {
            modifiedComponent =
                modifiedComponent.keybind(replaceIn(modifiedComponent.keybind()) ?: modifiedComponent.keybind())
        }
        if (modifiedComponent is TextComponent) {
            val replaced = replaceStringValues(modifiedComponent.content())
            val sectionIndex = replaced.indexOf('§')
            if (sectionIndex > -1 && replaced.length > sectionIndex + 1 && Util.getFormatFromLegacy(
                    replaced.lowercase(Locale.ROOT)[sectionIndex + 1]
                ) != null
            ) {
                // String replacement contains legacy code, parse to components and append them as children.
                val replacedComponent = LegacyComponentSerializer.legacySection().deserialize(replaced)
                modifiedComponent = modifiedComponent.content("")
                val children = mutableListOf<Component>()
                children.add(replacedComponent)
                val replacedChildren = replaceIn(modifiedComponent.children())
                if (replacedChildren != null) {
                    children.addAll(replacedChildren.filterNotNull())
                }
                modifiedComponent = modifiedComponent.children(children)
            } else {
                val replacedChildren = replaceIn(modifiedComponent.children())
                modifiedComponent =
                    modifiedComponent.content(replaced).children(replacedChildren?.filterNotNull() ?: emptyList())
            }
        } else if (modifiedComponent.children().isNotEmpty()) {
            val replacedChildren = replaceIn(modifiedComponent.children())
            modifiedComponent = modifiedComponent.children(replacedChildren?.filterNotNull() ?: emptyList())
        }
        if (modifiedComponent is TranslatableComponent) {
            modifiedComponent = modifiedComponent.key(replaceIn(modifiedComponent.key()) ?: modifiedComponent.key())
            val replacedArgs = replaceIn(modifiedComponent.args())
            modifiedComponent = modifiedComponent.args(replacedArgs?.filterNotNull() ?: emptyList())
        }
        if (modifiedComponent.insertion() != null) {
            modifiedComponent = modifiedComponent.insertion(replaceIn(modifiedComponent.insertion()))
        }
        if (modifiedComponent.clickEvent() != null) {
            modifiedComponent = modifiedComponent.clickEvent(
                ClickEvent.clickEvent(
                    modifiedComponent.clickEvent()!!.action(),
                    replaceIn(modifiedComponent.clickEvent()!!.value()) ?: modifiedComponent.clickEvent()!!.value()
                )
            )
        }
        if (modifiedComponent.hoverEvent() != null) {
            val hoverEvent = modifiedComponent.hoverEvent()!!
            when (hoverEvent.action()) {
                HoverEvent.Action.SHOW_TEXT -> {
                    modifiedComponent = modifiedComponent.hoverEvent(
                        HoverEvent.showText(
                            replaceIn(hoverEvent.value() as Component) ?: (hoverEvent.value() as Component)
                        )
                    )
                }

                HoverEvent.Action.SHOW_ENTITY -> {
                    val showEntity = hoverEvent.value() as HoverEvent.ShowEntity
                    modifiedComponent = modifiedComponent.hoverEvent(
                        HoverEvent.showEntity(
                            HoverEvent.ShowEntity.showEntity(
                                Key.key(replaceIn(showEntity.type().asString()) ?: showEntity.type().asString()),
                                showEntity.id(),
                                replaceIn(showEntity.name())
                            )
                        )
                    )
                }

                HoverEvent.Action.SHOW_ITEM -> {
                    val showItem = hoverEvent.value() as HoverEvent.ShowItem
                    modifiedComponent = modifiedComponent.hoverEvent(
                        HoverEvent.showItem(
                            HoverEvent.ShowItem.showItem(
                                Key.key(replaceIn(showItem.item().asString()) ?: showItem.item().asString()),
                                showItem.count(),
                                if (showItem.nbt() != null) BinaryTagHolder.binaryTagHolder(
                                    replaceIn(showItem.nbt()!!.string()) ?: showItem.nbt()!!.string()
                                ) else null
                            )
                        )
                    )
                }

                else -> {}
            }
        }

        // Typed replacements that need the parsed component tree.
        var replacedComponents = mutableListOf<Component>(modifiedComponent)
        for ((key, value) in replacements()) {
            if (value is ReplacementValue.Text) {
                continue
            }

            val newReplacedComponents = mutableListOf<Component>()
            for (replaceComponent in replacedComponents) {
                if (replaceComponent is TextComponent) {
                    val placeholder = normalizedPlaceholder(key)
                    val searchText = textForMatch(replaceComponent.content())
                    var index = searchText.indexOf(placeholder)
                    if (index > -1) {
                        var textComponent: TextComponent = replaceComponent
                        while (true) {
                            val replacementComponent = when (value) {
                                is ReplacementValue.ComponentValue -> value.value
                                is ReplacementValue.MineDownValue -> MineDown(value.value).toComponent()
                                is ReplacementValue.Text -> Component.text(value.value)
                            }
                            newReplacedComponents += buildReplacementSegment(textComponent, index, replacementComponent)

                            textComponent = textComponent.content(
                                textComponent.content().substring(index + placeholder.length)
                            ) as TextComponent
                            val remainingText = textForMatch(textComponent.content())
                            index = remainingText.indexOf(placeholder)
                            if (remainingText.isEmpty() || index < 0) {
                                newReplacedComponents += textComponent
                                break
                            }
                        }
                        continue
                    }
                }

                newReplacedComponents += replaceComponent
            }
            replacedComponents = newReplacedComponents
        }

        builder.append(replacedComponents)
        return builder.build()
    }

    /**
     * Replace the placeholders in a string.
     * @param string The String to replace in
     * @return The string with the placeholders replaced
     */
    fun replaceIn(string: String?): String? {
        if (string == null) {
            return null
        }
        return replaceStrings(string)
    }

    /**
     * Replace the placeholders in a string.
     * Component values are converted to legacy-text equivalents in this path.
     * @param string The String to replace in
     * @return The string with the placeholders replaced
     */
    internal fun replaceStrings(string: String): String {
        return replaceStringValues(string)
    }

    /**
     * Create a copy of this Replacer
     * @return A copy of this Replacer
     */
    fun copy(): Replacer = Replacer().copy(this)

    /**
     * Copy all the values of another Replacer
     * @param from The replacer to copy
     * @return The Replacer instance
     */
    fun copy(from: Replacer): Replacer {
        replacements().clear()
        replacements().putAll(from.replacements())
        placeholderPrefix(from.placeholderPrefix())
        placeholderSuffix(from.placeholderSuffix())
        ignorePlaceholderCase(from.ignorePlaceholderCase())
        return this
    }

    /**
     * Get the map of placeholders with their typed replacements
     * @return the replacement map
     */
    fun replacements(): MutableMap<String, ReplacementValue> = this.replacements

    /**
     * Get the placeholder indicator's prefix string
     * @return the prefix characters
     */
    fun placeholderPrefix(): String = this.placeholderPrefix

    /**
     * Set the placeholder indicator's prefix string
     * @param placeholderPrefix The placeholder prefix string
     * @return the instance of this Replacer
     */
    fun placeholderPrefix(placeholderPrefix: String): Replacer {
        this.placeholderPrefix = placeholderPrefix
        return this
    }

    /**
     * Get the placeholder indicator's suffix string
     * @return the suffix characters
     */
    fun placeholderSuffix(): String = this.placeholderSuffix

    /**
     * Set the placeholder indicator's suffix string
     * @param placeholderSuffix The placeholder suffix string
     * @return the instance of this Replacer
     */
    fun placeholderSuffix(placeholderSuffix: String): Replacer {
        this.placeholderSuffix = placeholderSuffix
        return this
    }

    /**
     * Replace the placeholder no matter what the case of it is
     * @return whether or not to ignore the placeholder case (Default: true)
     */
    fun ignorePlaceholderCase(): Boolean = this.ignorePlaceholderCase

    /**
     * Set whether or not the placeholder should be replaced no matter what the case of it is
     * @param ignorePlaceholderCase Whether or not to ignore the case in placeholders (Default: true)
     * @return the instance of this Replacer
     */
    fun ignorePlaceholderCase(ignorePlaceholderCase: Boolean): Replacer {
        this.ignorePlaceholderCase = ignorePlaceholderCase
        return this
    }

    private fun buildReplacementSegment(
        textComponent: TextComponent,
        index: Int,
        replacement: Component
    ): Component {
        return if (index > 0) {
            Component.text()
                .mergeStyle(textComponent)
                .content(textComponent.content().substring(0, index))
                .append(replacement)
                .build()
        } else if (replacement is BuildableComponent<*, *>) {
            replacement.toBuilder().style(
                Style.style().merge(textComponent.style()).merge(replacement.style()).build()
            ).build()
        } else {
            Component.text().mergeStyle(textComponent).append(replacement).build()
        }
    }

    private fun normalizedPlaceholder(key: String): String {
        val placeholderKey = if (ignorePlaceholderCase()) key.lowercase(Locale.ROOT) else key
        return placeholderPrefix() + placeholderKey + placeholderSuffix()
    }

    private fun textForMatch(text: String): String {
        return if (ignorePlaceholderCase()) text.lowercase(Locale.ROOT) else text
    }

    private fun replaceStringValues(string: String): String {
        var result = string
        for ((key, replacementValue) in replacements()) {
            val replValue = replacementToString(replacementValue)
            if (ignorePlaceholderCase()) {
                val placeholder = normalizedPlaceholder(key)
                var nextStart = 0
                while (nextStart < result.length) {
                    val startIndex = result.lowercase(Locale.ROOT).indexOf(placeholder, nextStart)
                    if (startIndex <= -1) break
                    nextStart = startIndex + replValue.length
                    result = result.take(startIndex) + replValue + result.substring(startIndex + placeholder.length)
                }
            } else {
                val placeholder = normalizedPlaceholder(key)
                val pattern = PATTERN_CACHE.computeIfAbsent(placeholder, PATTERN_CREATOR)
                result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(replValue))
            }
        }
        return result
    }

    private fun replacementToString(value: ReplacementValue): String = when (value) {
        is ReplacementValue.Text -> value.value
        is ReplacementValue.ComponentValue -> LegacyComponentSerializer.legacySection().serialize(value.value)
        is ReplacementValue.MineDownValue -> value.value
    }

    companion object {
        /**
         * A cache of compiled replacement patterns
         */
        private val PATTERN_CACHE: MutableMap<String, Pattern> = ConcurrentHashMap()

        /**
         * The creator of the patterns for the pattern cache
         */
        private val PATTERN_CREATOR: (String) -> Pattern = { Pattern.compile(it, Pattern.LITERAL) }

        /**
         * Replace certain placeholders with values in string.
         * This uses the % character as placeholder indicators (suffix and prefix)
         * @param message The string to replace in
         * @param replacements The replacements, nth element is the placeholder, n+1th the value
         * @return The string with all the placeholders replaced
         */
        fun replaceIn(message: String?, vararg replacements: String): String? {
            return Replacer().replace(*replacements).replaceStrings(message ?: return null)
        }

        /**
         * Replace certain placeholders with values in a component array.
         * This uses the % character as placeholder indicators (suffix and prefix)
         * @param message The Component to replace in
         * @param replacements The replacements, nth element is the placeholder, n+1th the value
         * @return A copy of the Component array with all the placeholders replaced
         */
        fun replaceIn(message: Component?, vararg replacements: String): Component? {
            return Replacer().replace(*replacements).replaceIn(message)
        }

        /**
         * Replace a certain placeholder with a component in a component array.
         * This uses the % character as placeholder indicators (suffix and prefix)
         * @param message The Component to replace in
         * @param placeholder The placeholder to replace
         * @param replacement The replacement component
         * @return A copy of the Component array with all the placeholders replaced
         */
        fun replaceIn(message: Component?, placeholder: String, replacement: Component): Component? {
            return Replacer().replace(placeholder, replacement).replaceIn(message)
        }
    }
}
