package com.nexo.app.ui.designsystem

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/** Text a ViewModel wants shown. The UI resolves it against the current resources, so ViewModels never hold a Context. */
sealed interface UiText {
    data class Raw(val value: String) : UiText

    /** [args] may themselves be [UiText]; they are resolved first. */
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Several texts shown as one, e.g. "Bank → Cash (3 months)". */
    data class Joined(val parts: List<UiText>) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

        fun join(vararg parts: UiText): UiText = Joined(parts.toList())

        val Empty: UiText = Raw("")
    }
}

/** These texts shown one after another with [separator] between them, e.g. "2 approved · 1 skipped". */
fun List<UiText>.joinToUiText(separator: String): UiText =
    UiText.Joined(flatMapIndexed { i, part -> if (i == 0) listOf(part) else listOf(UiText.Raw(separator), part) })

fun UiText.asString(resources: Resources): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolve(args, resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *resolve(args, resources))
    is UiText.Joined -> parts.joinToString("") { it.asString(resources) }
}

@Composable
fun UiText.asString(): String = asString(LocalResources.current)

private fun resolve(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.asString(resources) else it }.toTypedArray()

/** A message shown once (a toast or snackbar). [id] tells two identical messages apart. */
data class UserMessage(val text: UiText, val id: Long = System.nanoTime())
