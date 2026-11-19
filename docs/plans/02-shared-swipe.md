# 02 – Shared swipe

**Goal:** one implementation of "swipe sideways, show colour + symbol behind, act past 75 %" instead of four.

Today:
| Where | What |
|---|---|
| `editor/ChecklistRow.kt` | item swipe (offset, draggable, bg box with `swipeLook`) |
| `editor/CategoryFrame.kt` `EditorCategoryHeader` | reports deltas; `EditorChecklist.kt` holds `categorySwipe` map, `CategorySwipeBand` draws band + symbol |
| `notes/NoteCards.kt` `SwipeCard` | whole card swipe (archive/trash/...), own colours/icons |
| `notes/NoteCards.kt` `CategorySwipe` | category frame on cards, band = frame minus moved frame |

Steps:
- [x] `ui/Swipe.kt`: `class SwipeState(maxPx, canLeft, canRight)` with `offset`, `drag(delta)`, `suspend fun release(): Boolean?`
      (animates back, returns direction if past `SWIPE_TRIGGER`), and `Modifier.swipeDrag(state, enabled, onSwiped)`.
- [x] Use it in `ChecklistRow`, `EditorCategoryHeader` (state lives in `EditorChecklist` per category instead of the raw `categorySwipe` map),
      `SwipeCard`, `CategorySwipe`.
- [ ] (decided against) `FrameGeometry` (in `CategoryFrame.kt` or own file): the frame's centre path as `fun path(size, dx, grow, top, bottom, topInset)`;
      used by `categoryFrame` (line), `CategorySwipeBand` (band = path(0) − path(offset, 0.5 dp), clipped to 14 dp past the arc), and
      the overview `CategorySwipe` (same idea with the card frame's shape).
- [x] Shared action rule: `Categories.swipeTick(items)` (tick all, untick when all ticked) used by `EditorScreen.swipeItems` and
      `NoteTile.swipeCategory`.
- [x] Overview `SwipeCard` keeps its own actions (archive, trash, pin, …) but takes colour/icon from one `cardSwipeLook(kind)` next to `swipeLook`.

Done when: all four swipes look and act as in v1.8.0 (screenshots right/left, corners zoomed), no black slivers, symbol revealed from under the frame.

Notes: `SwipeState` has `offset`, `drag`, `release`; `Modifier.swipeDrag` wraps `draggable`. The symbol/colour of a card swipe is `cardSwipeSymbol`. `FrameGeometry` was not built: the editor frame is a stroked round rect, the editor band a difference of two paths, the card band a difference of outline paths; they share no code worth extracting, and unifying them would risk the seamless corners.
