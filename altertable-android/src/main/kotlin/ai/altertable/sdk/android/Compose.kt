package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf

/**
 * CompositionLocal for the Altertable client.
 *
 * Throws an error if accessed without being provided, ensuring type safety.
 * Use [ProvideAltertable] to provide the client at the root of your Compose hierarchy.
 */
public val LocalAltertable = compositionLocalOf<Altertable> {
    error("Altertable client not provided. Use ProvideAltertable() to provide it.")
}

/**
 * Provides the Altertable client to the composition hierarchy.
 *
 * Use this at the root of your Compose app:
 * ```
 * ProvideAltertable(altertableClient) {
 *     MyApp()
 * }
 * ```
 *
 * @param client The Altertable client instance.
 * @param content The composable content that will have access to the client.
 */
@Suppress("FunctionName")
@Composable
public fun ProvideAltertable(
    client: Altertable,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAltertable provides client) {
        content()
    }
}

/**
 * Retrieves the Altertable client from [LocalAltertable].
 *
 * Throws an error if the client was not provided via [ProvideAltertable].
 *
 * @return The Altertable client instance.
 */
@Composable
public fun rememberAltertable(): Altertable = LocalAltertable.current

/**
 * Tracks a screen view when this composable enters the composition.
 *
 * The client is resolved from [LocalAltertable].
 *
 * @param name The screen name to track.
 * @param properties Optional additional properties.
 */
public fun Modifier.screenView(
    name: String,
    properties: Map<String, Any> = emptyMap(),
): Modifier = this.then(ScreenViewElement(name, properties))

/**
 * Tracks a screen view using [LaunchedEffect] when this composable enters the composition.
 *
 * This is an alternative to [Modifier.screenView] that uses a composable function instead of a modifier.
 * The client is resolved from [LocalAltertable].
 *
 * @param name The screen name to track.
 * @param properties Optional additional properties.
 */
@Suppress("FunctionName")
@Composable
public fun TrackScreenView(
    name: String,
    properties: Map<String, Any> = emptyMap(),
) {
    val client = rememberAltertable()
    LaunchedEffect(name) {
        client.screen(name, properties)
    }
}

private data class ScreenViewElement(
    val name: String,
    val properties: Map<String, Any>,
) : ModifierNodeElement<ScreenViewNode>() {
    override fun create() = ScreenViewNode(name, properties)

    override fun update(node: ScreenViewNode) {
        node.name = name
        node.properties = properties
    }
}

private class ScreenViewNode(
    var name: String,
    var properties: Map<String, Any>,
) : Modifier.Node(), CompositionLocalConsumerModifierNode {
    private var tracked = false

    override fun onAttach() {
        if (!tracked) {
            tracked = true
            currentValueOf(LocalAltertable).screen(name, properties)
        }
    }

    override fun onDetach() {
        tracked = false
    }
}
