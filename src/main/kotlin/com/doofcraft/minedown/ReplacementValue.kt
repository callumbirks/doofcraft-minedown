package com.doofcraft.minedown

import net.kyori.adventure.text.Component

sealed interface ReplacementValue {
    data class Text(val value: String) : ReplacementValue
    data class ComponentValue(val value: Component) : ReplacementValue
    data class MineDownValue(val value: String) : ReplacementValue
}
