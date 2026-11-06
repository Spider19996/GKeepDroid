package com.gkeepdroid

import androidx.glance.color.ColorProvider as DayNightColorProvider
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.glance.background
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider

/** Colors, picture and text palette of a note card in a widget. */

internal val DarkNoteText = Color(0xFF202124)

/** Text on the dark versions of Keep's pictures. */
internal val LightNoteText = Color(0xFFE8EAED)

/** Keep's picture (in the light or dark version) or the user's own photo behind a note in a widget; null if it has none. */
internal fun widgetPicture(context: Context, settings: WidgetSettings, account: String?, note: KeepNote?, fraction: Float): Bitmap? {
    if (!settings.showNoteColors || account == null || note == null) return null
    CustomBackgrounds.forWidget(context, account, note.id, settings.noteAlpha, fraction)?.let { return it }
    val name = RichNotes.of(context, account, note.id)?.background.orEmpty()
    if (name.isEmpty()) return null
    val night = NoteLook.of(context, account, note.id).dark ?: NoteBackgrounds.isNight(context)
    return NoteBackgrounds.forWidget(context, name, settings.noteAlpha, fraction, night)
}

/** How a note looks in a widget: its color, picture and the text colors that go with them. */
internal class CardLook(val noteColor: Int?, val picture: Bitmap?, val palette: TextPalette) {
    /** With a picture, the note's color is a frame around it. */
    val framed: Boolean get() = picture != null && noteColor != null
}

internal fun cardLook(context: Context, settings: WidgetSettings, account: String?, note: KeepNote?, picture: Bitmap?): CardLook {
    val look = if (account != null && note != null) NoteLook.of(context, account, note.id) else null
    // The note can choose the light or dark version; without a choice the phone's mode decides.
    val night = look?.dark ?: NoteBackgrounds.isNight(context)
    val noteColor = if (settings.showNoteColors && note != null) NoteColors.argb(note.color, night) else null
    // Keep's pictures come in a dark version; a photo of one's own never does.
    val onDarkPicture = night && picture != null && account != null && note != null && CustomBackgrounds.stamp(context, account, note.id) == 0L
    val palette = textPalette(
        settings, onNoteColor = noteColor != null || picture != null,
        onDarkPicture = onDarkPicture || (noteColor != null && night && picture == null), own = look?.textColor?.let { Color(it) },
    )
    return CardLook(noteColor, picture, palette)
}

/**
 * The text color of a card. With different day and night colors the widget itself switches when
 * the system theme changes, without being redrawn.
 */
internal class TextPalette(private val day: Color, private val night: Color) {
    fun provider(alpha: Float = 1f): ColorProvider =
        if (day == night) {
            ColorProvider(day.copy(alpha = alpha))
        } else {
            DayNightColorProvider(day = day.copy(alpha = alpha), night = night.copy(alpha = alpha))
        }
}

/** Notes with a color always get dark text, which suits the light note colors. */
internal fun textPalette(settings: WidgetSettings, onNoteColor: Boolean, onDarkPicture: Boolean = false, own: Color? = null): TextPalette = when {
    own != null -> TextPalette(own, own)
    onDarkPicture -> TextPalette(LightNoteText, LightNoteText)
    onNoteColor -> TextPalette(DarkNoteText, DarkNoteText)
    settings.textColorAuto -> TextPalette(DarkNoteText, Color.White)
    else -> Color(settings.textColor).let { TextPalette(it, it) }
}
