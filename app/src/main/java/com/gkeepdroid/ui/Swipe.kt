package com.gkeepdroid

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * The sideways swipe of everything that has one (checklist rows, categories, note cards): how far it is moved, held back at
 * [SWIPE_MAX], and whether letting go counts as a swipe ([SWIPE_TRIGGER] of the way). What shows behind is up to the caller.
 */
class SwipeState(private val maxPx: Float) {
    /** How far the swiped thing is moved: negative to the left. */
    var offset by mutableFloatStateOf(0f)
        private set

    fun drag(delta: Float, canLeft: Boolean, canRight: Boolean) {
        offset = (offset + delta).coerceIn(if (canLeft) -maxPx else 0f, if (canRight) maxPx else 0f)
    }

    /** Lets go: glides back, and says which way it was swiped (true: right) if it was far enough, otherwise null. */
    suspend fun release(): Boolean? {
        val at = offset
        animate(at, 0f) { value, _ -> offset = value }
        return if (kotlin.math.abs(at) >= maxPx * SWIPE_TRIGGER) at > 0f else null
    }
}

@Composable
fun rememberSwipeState(): SwipeState {
    val maxPx = with(LocalDensity.current) { SWIPE_MAX.toPx() }
    return remember { SwipeState(maxPx) }
}

/** Moves with the finger sideways (only in the directions that are allowed) and calls [onSwiped] with the direction when a swipe counts. */
@Composable
fun Modifier.swipeDrag(state: SwipeState, canLeft: Boolean, canRight: Boolean, enabled: Boolean = true, onSwiped: (Boolean) -> Unit): Modifier =
    draggable(
        orientation = Orientation.Horizontal,
        enabled = enabled,
        state = rememberDraggableState { delta -> state.drag(delta, canLeft, canRight) },
        onDragStopped = { state.release()?.let(onSwiped) },
    )
