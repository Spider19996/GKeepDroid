"""Thin bridge between the Android app and gkeepapi.

Every public function returns the full list of active notes as a JSON string,
so the Kotlin side only has to parse text and refresh its cache.
"""
import json
import os
import re

import gkeepapi
from gkeepapi.node import BlobType, ColorValue
from gkeepapi.node import List as KeepList


def _login(email, master_token, state_path):
    """Authenticate, reusing the cached sync state for fast incremental syncs."""
    state = None
    if os.path.exists(state_path):
        try:
            with open(state_path) as f:
                state = json.load(f)
        except (OSError, ValueError):
            state = None

    keep = gkeepapi.Keep()
    try:
        keep.authenticate(email, master_token, state=state, sync=True)
    except Exception:
        if state is None:
            raise
        # The cached state may be stale or corrupt: retry with a full sync.
        keep = gkeepapi.Keep()
        keep.authenticate(email, master_token, sync=True)
    return keep


def _image_blobs(note):
    """Images and drawings attached to a note (audio is ignored)."""
    return [
        blob
        for blob in note.blobs
        if blob.blob is not None and blob.blob.type in (BlobType.Image, BlobType.Drawing)
    ]


def _safe_name(blob_id):
    return re.sub(r"[^A-Za-z0-9_-]", "_", blob_id)


def _sync_images(keep, notes, state_path):
    """Download images of the given notes next to the sync state; never fails the sync."""
    images_dir = state_path.rsplit(".json", 1)[0] + "_images"
    os.makedirs(images_dir, exist_ok=True)
    wanted = set()
    try:
        import requests

        for note in notes:
            for blob in _image_blobs(note):
                name = _safe_name(blob.id)
                wanted.add(name)
                path = os.path.join(images_dir, name)
                if os.path.exists(path):
                    continue
                try:
                    response = requests.get(keep.getMediaLink(blob), timeout=30)
                    response.raise_for_status()
                    with open(path, "wb") as f:
                        f.write(response.content)
                except Exception:
                    continue
    except Exception:
        return
    for name in os.listdir(images_dir):
        if name not in wanted:
            try:
                os.remove(os.path.join(images_dir, name))
            except OSError:
                pass


def _finish(keep, state_path):
    """Persist the sync state and return the active notes as JSON."""
    with open(state_path, "w") as f:
        json.dump(keep.dump(), f)
    everything = [n for n in keep.all() if not n.deleted]
    active = [n for n in everything if not n.trashed and not n.archived]
    # Archived and trashed notes only matter to the app's own screens, so they go into a separate
    # file and never slow the widgets down.
    other = []
    for n in everything:
        if n.trashed or n.archived:
            entry = _serialize(n)
            entry["state"] = "trashed" if n.trashed else "archived"
            other.append(entry)
    base = state_path.rsplit(".json", 1)[0]
    with open(base + "_other.json", "w") as f:
        json.dump(other, f)
    with open(base + "_labels.json", "w") as f:
        json.dump(sorted(label.name for label in keep.labels()), f)
    _sync_images(keep, active, state_path)
    # Ordering is left to the app, which can sort per widget.
    return json.dumps([_serialize(n) for n in active])


def _set_labels(keep, note, labels_json):
    """Make the note carry exactly these labels, creating labels that do not exist yet."""
    if labels_json is None:
        return
    wanted = [name.strip() for name in json.loads(labels_json) if name.strip()]
    for label in list(note.labels.all()):
        if label.name not in wanted:
            note.labels.remove(label)
    have = {label.name for label in note.labels.all()}
    for name in wanted:
        if name not in have:
            note.labels.add(keep.findLabel(name) or keep.createLabel(name))


def _serialize(note):
    is_list = isinstance(note, KeepList)
    if is_list:
        items = [
            {"id": item.id, "text": item.text, "checked": item.checked, "indent": item.indented}
            for item in note.items
        ]
        body = ""
    else:
        items = []
        body = note.text
    return {
        "id": note.id,
        "serverId": note.server_id or "",
        "title": note.title,
        "text": body,
        "isList": is_list,
        "items": items,
        "pinned": note.pinned,
        "color": note.color.name,
        "labels": [label.name for label in note.labels.all()],
        "images": [_safe_name(blob.id) for blob in _image_blobs(note)],
        "sort": int(note.sort),
        "updated": int(note.timestamps.updated.timestamp() * 1000),
        "created": int(note.timestamps.created.timestamp() * 1000),
    }


def _apply_items(note, new_items):
    """Merge the edited checklist into the note: text, ticks, order and indentation, matching items by id."""
    existing = {item.id: item for item in note.items}
    kept = set()
    ordered = []  # (item node, wants to be a sub-item), in the order of the editor
    for new in new_items:
        text = new["text"].strip()
        if not text:
            continue
        item = existing.get(new["id"])
        if item is None:
            item = note.add(text, checked=new["checked"])
        else:
            item.text = text
            item.checked = new["checked"]
            kept.add(item.id)
        ordered.append((item, bool(new.get("indent"))))
    for item_id, item in existing.items():
        if item_id not in kept:
            item.delete()

    # Keep sorts the highest value first, so the first item gets the highest one.
    size = len(ordered)
    unchanged = [i.id for i in note.items if not i.deleted] == [item.id for item, _ in ordered]
    if not unchanged:
        for position, (item, _) in enumerate(ordered):
            wanted = (size - position) * KeepList.SORT_DELTA
            if int(item.sort) != wanted:
                item.sort = wanted

    # A sub-item belongs to the nearest item above it that is not one.
    parent = None
    for item, wants_indent in ordered:
        if wants_indent and parent is not None:
            if item.parent_item is not parent:
                if item.parent_item is not None:
                    item.parent_item.dedent(item)
                parent.indent(item)
        else:
            if item.parent_item is not None:
                item.parent_item.dedent(item)
            parent = item


def exchange_master_token(email, oauth_token, android_id):
    """Turn the oauth_token cookie of Google's embedded setup page into a master token."""
    import gpsoauth

    response = gpsoauth.exchange_token(email, oauth_token, android_id)
    token = response.get("Token")
    if not token:
        raise ValueError(response.get("Error") or "Google did not return a token")
    return json.dumps({"token": token, "email": response.get("Email") or email})


def fetch_notes(email, master_token, state_path):
    keep = _login(email, master_token, state_path)
    return _finish(keep, state_path)


def _find_child(keep, note_id, child_id):
    """Find a list item or blob of a note. Keep.get() only knows top-level notes."""
    note = keep.get(note_id)
    if note is None:
        return None
    for child in note.children:
        if child.id == child_id:
            return child
    return None


def set_items_checked(email, master_token, state_path, ops_json):
    """Check, uncheck or delete several checklist items with a single login and sync."""
    keep = _login(email, master_token, state_path)
    for op in json.loads(ops_json):
        item = _find_child(keep, op["note"], op["item"])
        if item is None:
            continue
        if op.get("delete"):
            item.delete()
            item.touch()
        else:
            item.checked = op["checked"]
    keep.sync()
    return _finish(keep, state_path)


def update_note(email, master_token, state_path, note_id, title, text, items_json, color, pinned, remove_images_json="[]", labels_json=None):
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.title = title
    note.color = ColorValue[color]
    # Keep's own pin: it is synced like any other change of the note.
    note.pinned = pinned
    _set_labels(keep, note, labels_json)
    # Images the user marked for removal are deleted together with the other changes.
    removed = set(json.loads(remove_images_json))
    for blob in _image_blobs(note):
        if _safe_name(blob.id) in removed:
            blob.delete()
            blob.touch()
    if isinstance(note, KeepList):
        _apply_items(note, json.loads(items_json))
    else:
        note.text = text
    keep.sync()
    return _finish(keep, state_path)


def add_list_item(email, master_token, state_path, note_id, text):
    """Add an unchecked item to a checklist."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None or not isinstance(note, KeepList):
        raise ValueError("Checklist not found")
    note.add(text.strip(), checked=False)
    keep.sync()
    return _finish(keep, state_path)


def archive_note(email, master_token, state_path, note_id):
    """Archive a note, like the archive button in the Keep app."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.archived = True
    keep.sync()
    return _finish(keep, state_path)


def unarchive_note(email, master_token, state_path, note_id):
    """Bring an archived note back."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.archived = False
    keep.sync()
    return _finish(keep, state_path)


def untrash_note(email, master_token, state_path, note_id):
    """Restore a note from the trash."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.untrash()
    keep.sync()
    return _finish(keep, state_path)


def delete_forever(email, master_token, state_path, note_id):
    """Delete a note for good (it is not kept in the trash)."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.delete()
    keep.sync()
    return _finish(keep, state_path)


def trash_note(email, master_token, state_path, note_id):
    """Move a note to Keep's trash, like deleting it in the Keep app."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.trash()
    keep.sync()
    return _finish(keep, state_path)


def create_note(email, master_token, state_path, title, text, is_list, items_json, color, pinned, labels_json=None):
    keep = _login(email, master_token, state_path)
    if is_list:
        items = [
            (i["text"].strip(), i["checked"])
            for i in json.loads(items_json)
            if i["text"].strip()
        ]
        note = keep.createList(title, items)
    else:
        note = keep.createNote(title, text)
    note.color = ColorValue[color]
    note.pinned = pinned
    _set_labels(keep, note, labels_json)
    keep.sync()
    return _finish(keep, state_path)


def create_label(email, master_token, state_path, name):
    """Create a label without attaching it to a note."""
    keep = _login(email, master_token, state_path)
    name = name.strip()
    if name and keep.findLabel(name) is None:
        keep.createLabel(name)
    keep.sync()
    return _finish(keep, state_path)


def delete_label(email, master_token, state_path, name):
    """Delete a label; Keep removes it from all notes."""
    keep = _login(email, master_token, state_path)
    label = keep.findLabel(name)
    if label is not None:
        keep.deleteLabel(label.id)
    keep.sync()
    return _finish(keep, state_path)


def set_sort(email, master_token, state_path, note_id, sort):
    """Move a note in Keep's order: a higher sort value is further up."""
    keep = _login(email, master_token, state_path)
    note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    note.sort = int(sort)
    keep.sync()
    return _finish(keep, state_path)
