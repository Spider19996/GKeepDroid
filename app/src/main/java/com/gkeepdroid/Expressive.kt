package com.gkeepdroid

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/*
 * Building blocks of the Material 3 Expressive look that several screens share.
 */

/** A slider whose value lives in the caller's state (a setting), so a change from elsewhere moves it too. */
@Composable
internal fun ValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    Slider(state = state, onValueChange = onValueChange, modifier = modifier)
}
