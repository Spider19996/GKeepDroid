# 06 – Compact mode

**Goal:** more content per screen, switchable, default off, still easy to tap.

Decisions to take with the user first:
- One global switch, or separate for editor / overview / widgets? (Proposal: one global switch in App settings; widgets follow it unless
  their own `listSpacingDp` was changed.)

Steps:
- [ ] Setting: `compact: Boolean = false` in `settings/AppSettings.kt` (load/save like `foldNotes`), `appCompact` in `SettingsState.kt`,
      a switch on the app settings page (`settings/SettingsAppPages.kt`), strings in `values/strings.xml` and every `values-*`.
- [ ] `ui/Dimens.kt`: `data class Dimens(rowGap, rowHeight, framePadding, headerInset, cardPadding, cardRowGap, widgetRowGap, …)` with
      `Normal` and `Compact`; `LocalDimens` provided in `KeepTheme` (`ui/Theme.kt`, next to `LocalOnNote`).
- [ ] Editor: `ROW_GAP` 12 → 6 dp (`EditorScreen` `spacedBy`, `categoryFrame` reach, `CategorySwipeBand` reach must use the same value),
      checklist row 56 → 44 dp (`ChecklistRow` handle strip, divider height, `handleHeightPx`), frame bottom padding 8 → 4 dp, header inset 10 → 8 dp.
- [ ] Overview cards (`notes/NoteCards.kt`): card padding 12 → 8 dp, item row gap from `listSpacingDp` 2 → 0, category frame padding,
      gap between cards in `NotesGrid.kt`.
- [ ] Widgets: `WidgetParts`/`WidgetCategories` paddings via the widget's settings (Glance has no CompositionLocal from the app theme;
      read `AppSettingsStore.load(context).compact` in `provideGlance`).
- [ ] Touch targets stay ≥ 40 dp (checkbox, handle, fold arrow, `+`): shrink padding, not hit areas.
- [ ] README screenshots (English, demo mode) normal vs. compact.

Done when: switch works live in editor, overview, widgets; frames and swipe band still seamless in compact (re-run the corner screenshots);
visible rows per screen counted before/after.
