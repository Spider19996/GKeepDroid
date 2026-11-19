package com.gkeepdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Wraps a card so it can be swiped sideways, but only as far as the symbol behind it is fully shown. The action
 * runs when the finger lets go past the trigger point, and the card slides back.
 */
@Composable
internal fun SwipeCard(app: AppSettings, enabled: Boolean, onAction: (Int) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    if (!enabled || (app.swipeLeft == SWIPE_NONE && app.swipeRight == SWIPE_NONE)) {
        Box(modifier) { content() }
        return
    }
    val swipe = rememberSwipeState()
    val offsetX = swipe.offset
    val toRight = offsetX > 0f
    val kind = if (toRight) app.swipeRight else app.swipeLeft
    Box(modifier) {
        if (offsetX != 0f && kind != SWIPE_NONE) {
            Box(
                Modifier.matchParentSize().clip(MaterialTheme.shapes.medium)
                    .background(
                        if (kind == SWIPE_TRASH) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val (icon, name) = cardSwipeSymbol(kind) ?: return@Box
                Icon(painterResource(icon), contentDescription = stringResource(name))
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), 0) }
                .swipeDrag(swipe, canLeft = app.swipeLeft != SWIPE_NONE, canRight = app.swipeRight != SWIPE_NONE) { toRight ->
                    onAction(if (toRight) app.swipeRight else app.swipeLeft)
                }
        ) { content() }
    }
}
