"""Thin bridge between the Android app and gkeepapi.

Every public function returns the full list of active notes as a JSON string,
so the Kotlin side only has to parse text and refresh its cache.
"""
import json
import os
import random
import re

import gkeepapi
from gkeepapi.node import BlobType, ColorValue
from gkeepapi.node import List as KeepList


def _login(email, master_token, state_path, pull=True):
    """Authenticate, reusing the cached sync state for fast incremental syncs.

    With pull=False and a cached state the first sync is skipped: a change is applied to the cached
    notes and the single sync that sends it also fetches what is new.
    """
    state = None
    if os.path.exists(state_path):
        try:
            with open(state_path) as f:
                state = json.load(f)
        except (OSError, ValueError):
            state = None

    keep = gkeepapi.Keep()
    try:
        keep.authenticate(email, master_token, state=state, sync=pull or state is None)
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


def _finish(keep, state_path, images=True):
    """Persist the sync state and return the active notes as JSON; images=False skips their download."""
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
    if images:
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
    result = _finish(keep, state_path)
    # Formatting and backgrounds come on top of the notes; a failure here must not spoil the sync.
    try:
        _fetch_rich(keep, state_path)
    except Exception:
        pass
    return result


# Text styles of Keep's rich text, as bits of the flags that go to the app.
_STYLE_BITS = {"ts_bd": 1, "ts_it": 2, "ts_un": 4, "ts_st": 8}
_HEADING_BIT = 16


def _utf16(text):
    raw = text.encode("utf-16-le", "surrogatepass")
    return [raw[i] | (raw[i + 1] << 8) for i in range(0, len(raw), 2)]


def _apply_ops(docs, ops):
    """Apply Keep's change log to the texts of a note, given as {document id: {"u": UTF-16 units, "f": flags per unit}}.

    Positions in the log are 1-based UTF-16 positions, like the strings of the app.
    """
    for op in ops:
        if op[0] == "cbx-rm":
            docs.pop(op[2], None)
            continue
        if op[0] != "docs-nestedModel":
            continue
        body = op[2]
        doc = docs.setdefault(op[1][-1], {"u": [], "f": []})
        # Where the document sits in Keep's model; needed to address it when formatting is sent.
        doc["p"] = op[1]
        kind = body.get("ty")
        units, flags = doc["u"], doc["f"]
        if kind == "is":
            new = _utf16(body["s"])
            at = body["ibi"] - 1
            units[at:at] = new
            flags[at:at] = [0] * len(new)
        elif kind == "ds":
            del units[body["si"] - 1:body["ei"]]
            del flags[body["si"] - 1:body["ei"]]
        elif kind == "as":
            marks = body.get("sm") or {}
            if body.get("st") == "paragraph":
                if "ps_hd" not in marks:
                    continue
                # The position is that of the paragraph's line break; the heading covers the line before it.
                end = min(body["si"] - 1, len(units))
                start = end
                while start > 0 and units[start - 1] != 10:
                    start -= 1
                for i in range(start, end):
                    flags[i] = flags[i] | _HEADING_BIT if marks["ps_hd"] else flags[i] & ~_HEADING_BIT
            else:
                for i in range(body["si"] - 1, min(body["ei"], len(units))):
                    for key, bit in _STYLE_BITS.items():
                        if key in marks:
                            flags[i] = flags[i] | bit if marks[key] else flags[i] & ~bit


def _text(units):
    return b"".join(u.to_bytes(2, "little") for u in units).decode("utf-16-le", "surrogatepass")


def _runs(flags):
    """The styled stretches [start, end, flags] of a document."""
    runs, start = [], 0
    for i in range(1, len(flags) + 1):
        if i == len(flags) or flags[i] != flags[start]:
            if flags[start]:
                runs.append([start, i, flags[start]])
            start = i
    return runs


def _pack(docs):
    """Documents as they are kept between syncs: the text and the flags in run-length form."""
    packed = {}
    for key, doc in docs.items():
        counts = []
        for flag in doc["f"]:
            if counts and counts[-1][1] == flag:
                counts[-1][0] += 1
            else:
                counts.append([1, flag])
        packed[key] = {"t": _text(doc["u"]), "c": counts, "p": doc.get("p")}
    return packed


def _unpack(packed):
    docs = {}
    for key, doc in packed.items():
        docs[key] = {"u": _utf16(doc["t"]), "f": [flag for count, flag in doc["c"] for _ in range(count)], "p": doc.get("p")}
    return docs


def _send_background(keep, state_path, note, name):
    """Have the next sync carry the note's background picture ("" leaves it alone, "default" removes it).

    gkeepapi does not know backgrounds, so the node that it sends is completed on its way out.
    """
    if not name:
        return
    api = keep._keep_api
    changes = api.changes

    def with_background(target_version=None, nodes=None, labels=None):
        for node in nodes or []:
            if node.get("id") == note.id:
                node["background"] = {"name": name, "origin": "none" if name == "default" else "gallery"}
        return changes(target_version=target_version, nodes=nodes, labels=labels)

    api.changes = with_background
    # A note that is otherwise unchanged is not sent at all.
    note.touch()
    # The app shows the new picture at once; the next sync confirms it.
    path = state_path.rsplit(".json", 1)[0] + "_rich.json"
    try:
        with open(path) as f:
            rich = json.load(f)
        entry = rich["n"].setdefault(note.id, {"bg": "", "d": {}})
        entry["bg"] = "" if name == "default" else name
        with open(path, "w") as f:
            json.dump(rich, f)
    except (OSError, ValueError, KeyError):
        pass


def _fetch_rich(keep, state_path):
    """Formatting and background of the notes, which gkeepapi does not know, into files of their own.

    Keep only sends them to clients that call themselves collaborative ("CL"), and then it sends the text of
    a note as a change log: the whole log the first time, afterwards only what is new. So this is a second,
    separate request with its own version, and the model of every note is kept between syncs.
    """
    base = state_path.rsplit(".json", 1)[0]
    model = {"v": None, "n": {}}
    if os.path.exists(base + "_richmodel.json"):
        try:
            with open(base + "_richmodel.json") as f:
                model = json.load(f)
        except (OSError, ValueError):
            pass
    api = keep._keep_api
    send = api.send

    def with_capability(**kwargs):
        body = kwargs.get("json")
        if body and "requestHeader" in body:
            body["requestHeader"]["capabilities"].append({"type": "CL"})
        return send(**kwargs)

    api.send = with_capability
    try:
        version = model["v"]
        while True:
            changes = api.changes(target_version=version)
            if changes.get("forceFullResync"):
                model = {"v": None, "n": {}}
            for node in changes.get("nodes", []):
                if node.get("parentId") != "root":
                    continue
                entry = model["n"].get(node["id"], {"bg": "", "m": {}})
                server = node.get("serverChanges", {})
                snapshot = server.get("snapshot")
                if snapshot is not None:
                    docs = {}
                    ops = [op for chunk in snapshot.get("serializedChunks", []) for op in json.loads(chunk)]
                else:
                    docs = _unpack(entry["m"])
                    ops = [json.loads(change["serializedCommand"]) for change in server.get("changes", [])]
                _apply_ops(docs, ops)
                entry["m"] = _pack(docs)
                # The revision of the note's model, which Keep wants to hear back when formatting is sent.
                if snapshot is not None and snapshot.get("revision"):
                    entry["rev"] = snapshot["revision"]
                revisions = [change.get("revision") for change in server.get("changes", []) if change.get("revision")]
                if revisions:
                    entry["rev"] = revisions[-1]
                if "background" in node or snapshot is not None:
                    entry["bg"] = (node.get("background") or {}).get("name") or ""
                model["n"][node["id"]] = entry
            version = changes["toVersion"]
            if not changes.get("truncated"):
                break
        model["v"] = version
    finally:
        api.send = send
    with open(base + "_richmodel.json", "w") as f:
        json.dump(model, f)
    # What the app reads: only the notes that have a background or any styled text.
    rich = {}
    for node_id, entry in model["n"].items():
        docs = {}
        for key, doc in _unpack(entry["m"]).items():
            runs = _runs(doc["f"])
            if runs:
                docs[key] = {"t": _text(doc["u"]), "r": runs}
        background = entry["bg"] if entry["bg"] != "default" else ""
        if background or docs:
            rich[node_id] = {"bg": background, "d": docs}
    with open(base + "_rich.json", "w") as f:
        json.dump({"n": rich}, f)


_STYLE_KEYS = {1: "ts_bd", 2: "ts_it", 4: "ts_un", 8: "ts_st"}


def format_note(email, master_token, state_path, note_id, docs_json):
    """Sends text formatting for a note: the styles the app wants, compared with the ones Keep has.

    docs_json: {"text" or an item id: {"t": the text, "r": [[start, end, flags], ...]}}. Only the differences are sent, as
    commands of Keep's text model, together with the note.
    """
    keep = _login(email, master_token, state_path, pull=False)
    note = keep.get(note_id)
    if note is None:
        keep.sync()
        note = keep.get(note_id)
    if note is None:
        raise ValueError("Note not found")
    # The model and its revision must be up to date, as an edit of the text may have been sent just before.
    _fetch_rich(keep, state_path)
    base = state_path.rsplit(".json", 1)[0]
    with open(base + "_richmodel.json") as f:
        entry = json.load(f)["n"].get(note.id)
    if not entry or not entry.get("rev"):
        raise ValueError("The formatting of this note is not known yet")
    docs = _unpack(entry["m"])
    commands = []
    for key, want in json.loads(docs_json).items():
        if key == "text":
            # The body of a text note is its only document.
            keys = [k for k, d in docs.items() if d.get("p") and len(d["p"]) == 3]
            key = keys[0] if len(keys) == 1 else None
        doc = docs.get(key) if key else None
        if doc is None or not doc.get("p"):
            continue
        desired = _utf16(want["t"])
        # Styles only make sense on the text Keep has.
        if desired != doc["u"]:
            continue
        flags = [0] * len(desired)
        for start, end, value in want["r"]:
            for i in range(start, min(end, len(flags))):
                flags[i] = value
        i = 0
        while i < len(flags):
            diff = (flags[i] ^ doc["f"][i]) & 15
            if not diff:
                i += 1
                continue
            j = i
            while j < len(flags) and ((flags[j] ^ doc["f"][j]) & 15) == diff and (flags[j] & diff) == (flags[i] & diff):
                j += 1
            marks = {}
            for bit, name in _STYLE_KEYS.items():
                if diff & bit:
                    marks[name] = bool(flags[i] & bit)
                    marks[name + "_i"] = False
            commands.append(["docs-nestedModel", doc["p"], {"ty": "as", "st": "text", "si": i + 1, "ei": j, "sm": marks}])
            i = j
    if commands:
        api = keep._keep_api
        changes = api.changes
        send = api.send
        bundle = {
            "sessionId": str(random.getrandbits(62)), "requestId": "1",
            "serializedCommands": json.dumps(commands, separators=(",", ":")),
        }

        def with_changes(target_version=None, nodes=None, labels=None):
            for node in nodes or []:
                if node.get("id") == note.id:
                    node["clientChanges"] = {"clientRevision": str(entry["rev"]), "commandBundles": [bundle]}
            return changes(target_version=target_version, nodes=nodes, labels=labels)

        def with_capability(**kwargs):
            body = kwargs.get("json")
            if body and "requestHeader" in body:
                body["requestHeader"]["capabilities"].append({"type": "CL"})
            return send(**kwargs)

        api.changes = with_changes
        api.send = with_capability
        try:
            note.touch()
            keep.sync()
        finally:
            api.changes = changes
            api.send = send
        _fetch_rich(keep, state_path)
    return _finish(keep, state_path, images=False)


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


def update_note(email, master_token, state_path, note_id, title, text, items_json, color, pinned, remove_images_json="[]", labels_json=None, background=""):
    keep = _login(email, master_token, state_path, pull=False)
    note = keep.get(note_id)
    if note is None:
        # Not in the cached state: fetch first.
        keep.sync()
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
    _send_background(keep, state_path, note, background)
    keep.sync()
    # Saving never adds images, so only the deletions matter; the next sync cleans up the files.
    return _finish(keep, state_path, images=bool(removed))


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


def create_note(email, master_token, state_path, title, text, is_list, items_json, color, pinned, labels_json=None, background=""):
    keep = _login(email, master_token, state_path, pull=False)
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
    _send_background(keep, state_path, note, background)
    keep.sync()
    return _finish(keep, state_path, images=False)


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
