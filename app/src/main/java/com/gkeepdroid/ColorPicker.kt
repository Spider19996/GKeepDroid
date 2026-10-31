package com.gkeepdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

/** HSV sliders plus a hex field; returns an opaque ARGB color through [onPick]. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = FloatArray(3).also { AndroidColor.colorToHSV(initial, it) }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    var hexText by remember { mutableStateOf(hex(initial)) }

    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    Modifier.fillMaxWidth().height(48.dp)
                        .background(Color(color), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                ) {}
                Text(stringResource(R.string.color_hue))
                Slider(
                    value = hue,
                    onValueChange = { hue = it; hexText = hex(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))) },
                    valueRange = 0f..360f,
                )
                Text(stringResource(R.string.color_saturation))
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it; hexText = hex(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))) },
                )
                Text(stringResource(R.string.color_brightness))
                Slider(
                    value = value,
                    onValueChange = { value = it; hexText = hex(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))) },
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        // Apply as soon as the text is a complete #RRGGBB value.
                        val parsed = runCatching { AndroidColor.parseColor(text) }.getOrNull()
                        if (parsed != null && text.length == 7) {
                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                        }
                    },
                    singleLine = true,
                    label = { Text("#RRGGBB") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
