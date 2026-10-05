#!/usr/bin/env python3
"""Offline check of NexusTutorial's lessons.yml (or a fragment of it).

  python3 tools/validate-lessons.py [path/to/lessons.yml] [--fragment]

Checks the step grammar (the same one the plugin parses), lengths, ids, categories and permissions.
When a lesson names a plugin whose plugin.yml can be found under $NEXUS_ROOT (default /home/claude),
every `command` step must use a command or alias that plugin really registers.
Set MCITEMS to a minecraft-data items.json to verify icon / `have` material names.
A --fragment skips the whole-file checks (categories, every-category-has-lessons).
Needs: pip install pyyaml
"""
import glob, json, os, re, sys
import yaml

args = [a for a in sys.argv[1:] if not a.startswith("--")]
fragment = "--fragment" in sys.argv
here = os.path.dirname(os.path.abspath(__file__))
path = args[0] if args else os.path.join(here, "..", "src", "main", "resources", "lessons.yml")
doc = yaml.safe_load(open(path, encoding="utf-8")) or {}
errors, warnings = [], []

KINDS = ("info", "command", "open", "have")
MAX_TITLE, MAX_SUMMARY, MAX_TEXT = 32, 90, 190

def parse_step(raw):
    """Mirror of Step.parse in the plugin. Returns (kind, arg, text) or None."""
    if not isinstance(raw, str) or "::" not in raw:
        return None
    head, text = raw.split("::", 1)
    text = text.strip()
    head = head.strip()
    parts = head.split(None, 1)
    if not parts or parts[0].lower() not in KINDS or not text:
        return None
    kind = parts[0].lower()
    arg = parts[1].strip() if len(parts) > 1 else ""
    if kind == "info" and arg:
        return None
    if kind != "info" and not arg:
        return None
    return kind, arg, text

# plugin.yml commands
root = os.environ.get("NEXUS_ROOT", "/home/claude")
registered = {}
for f in glob.glob(os.path.join(root, "Nexus*", "**", "plugin.yml"), recursive=True):
    try:
        text = open(f, encoding="utf-8").read()
        y = yaml.safe_load(text)
    except Exception:
        # a few plugin.yml files have an unquoted colon in a description; read names/commands by hand
        y = None
        text = open(f, encoding="utf-8").read()
        name = re.search(r"^name:\s*(\S+)", text, re.M)
        if not name:
            continue
        cmds = {}
        block = re.search(r"^commands:\n((?:[ \t]+.*\n?|\n)+)", text, re.M)
        if block:
            for m in re.finditer(r"^  (\w+):\s*\n((?:    .*\n)*)", block.group(1), re.M):
                al = re.search(r"aliases:\s*\[([^\]]*)\]", m.group(2))
                cmds[m.group(1)] = {"aliases": [a.strip() for a in al.group(1).split(",")] if al else []}
        y = {"name": name.group(1), "commands": cmds}
    names = set()
    for k, v in (y.get("commands") or {}).items():
        names.add(k.lower())
        al = (v or {}).get("aliases") or []
        if isinstance(al, str):
            al = [al]
        names.update(a.lower() for a in al)
    registered.setdefault(y["name"], set()).update(names)

mats = None
mc = os.environ.get("MCITEMS")
if mc and os.path.exists(mc):
    mats = {i["name"].upper() for i in json.load(open(mc))}

cats = doc.get("categories") or {}
lessons = doc.get("lessons") or {}
if not fragment:
    if not cats:
        errors.append("no categories")
    for cid, c in cats.items():
        for k in ("title", "icon", "description"):
            if not c.get(k):
                errors.append(f"category {cid}: missing {k}")
        if mats is not None and str(c.get("icon", "")).upper() not in mats:
            errors.append(f"category {cid}: unknown icon {c.get('icon')}")
if not lessons:
    errors.append("no lessons")

used_cats = set()
for lid, l in lessons.items():
    w = f"lesson {lid}"
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,31}", str(lid)):
        errors.append(f"{w}: id must be lowercase letters/digits/underscore")
    for k in ("category", "title", "icon", "summary", "steps"):
        if not l.get(k):
            errors.append(f"{w}: missing {k}")
    if not l.get("steps"):
        continue
    if not fragment and l.get("category") not in cats:
        errors.append(f"{w}: unknown category {l.get('category')}")
    used_cats.add(l.get("category"))
    if len(str(l.get("title", ""))) > MAX_TITLE:
        errors.append(f"{w}: title longer than {MAX_TITLE}")
    if len(str(l.get("summary", ""))) > MAX_SUMMARY:
        errors.append(f"{w}: summary longer than {MAX_SUMMARY}")
    if mats is not None and str(l.get("icon", "")).upper() not in mats:
        errors.append(f"{w}: unknown icon {l.get('icon')}")
    perm = l.get("permission")
    if perm is not None and not re.fullmatch(r"[a-z0-9_.]+", str(perm)):
        errors.append(f"{w}: bad permission")
    plug = l.get("plugin")
    steps = l["steps"]
    if not isinstance(steps, list) or not 2 <= len(steps) <= 8:
        errors.append(f"{w}: needs 2-8 steps")
        continue
    parsed = []
    for i, raw in enumerate(steps, 1):
        p = parse_step(raw)
        if p is None:
            errors.append(f"{w} step {i}: cannot parse {raw!r} (use '<info|command|open|have> [arg] :: text')")
            continue
        parsed.append(p)
        kind, arg, text = p
        if len(text) > MAX_TEXT:
            errors.append(f"{w} step {i}: text longer than {MAX_TEXT} characters")
        if "${" in raw or "@" in raw:
            errors.append(f"{w} step {i}: '${{' and '@' are not allowed (Maven filtering)")
        if kind == "command":
            first = arg.split()[0].lstrip("/").lower()
            if arg.startswith("/"):
                errors.append(f"{w} step {i}: write the command without the leading slash")
            if plug in registered:
                if first not in registered[plug]:
                    errors.append(f"{w} step {i}: {plug} does not register /{first} (it has: {', '.join(sorted(registered[plug]))})")
            elif plug:
                warnings.append(f"{w} step {i}: {plug} has no plugin.yml on disk, so /{first} is unchecked")
            if re.search(r"\b(reload|give|grant|revoke|reset|set|remove|disband|approve|deny|pardon|register|forget|trigger|start|stop)\b", arg.lower()):
                warnings.append(f"{w} step {i}: '/{arg}' looks like an admin or state-changing command -- is it safe for every player?")
        if kind == "have":
            toks = arg.split()
            if len(toks) > 2 or (len(toks) == 2 and not toks[1].isdigit()):
                errors.append(f"{w} step {i}: 'have' takes MATERIAL [count]")
            elif mats is not None and toks[0].upper() not in mats:
                errors.append(f"{w} step {i}: unknown material {toks[0]}")
    if parsed and parsed[0][0] != "info":
        errors.append(f"{w}: the first step should be 'info' (what this is)")
    if parsed and parsed[-1][0] != "info":
        errors.append(f"{w}: the last step should be 'info' (a recap or tip)")
    if sum(1 for p in parsed if p[0] != "info") > 3:
        warnings.append(f"{w}: more than three action steps -- keep lessons short")

if not fragment:
    for cid in cats:
        if cid not in used_cats:
            errors.append(f"category {cid} has no lessons")

for w in warnings:
    print("  warn:", w)
if errors:
    print("lesson problems:")
    for e in errors:
        print("  -", e)
    sys.exit(1)
print(f"lessons OK ({len(lessons)} lessons, {len(warnings)} warnings)")
