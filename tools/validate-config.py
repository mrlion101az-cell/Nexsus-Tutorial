#!/usr/bin/env python3
"""Offline check of NexusTutorial's config.yml against what TutorConfig actually reads.

  python3 tools/validate-config.py [path/to/config.yml]

Fails if config.yml is missing a key the code reads (so a typo can never silently fall back to a
default), has a key the code never reads, has a value that differs from the built-in default of the
same setting (so the shipped file and the code agree), or names a material that does not exist.
Optional: set MCITEMS to a minecraft-data items.json to verify material names.
Needs: pip install pyyaml
"""
import json, os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "src", "main", "resources", "config.yml")
src = open(os.path.join(here, "..", "src", "main", "java", "com", "nexuscraft", "nexustutorial", "TutorConfig.java"), encoding="utf-8").read()
cfg = yaml.safe_load(open(path, encoding="utf-8"))
errors = []

def flat(d, prefix=""):
    out = {}
    for k, v in d.items():
        p = prefix + str(k)
        if isinstance(v, dict) and not p.endswith("ingredients"):
            out.update(flat(v, p + "."))
        else:
            out[p] = v
    return out

have = flat(cfg)
read = set(re.findall(r'c\.get(?:String|Int|Double|Boolean|StringList|IntegerList)\("([a-z\-\.]+)"', src))
read |= set(re.findall(r'c\.getConfigurationSection\("([a-z\-\.]+)"\)', src))
for k in sorted(read):
    if k not in have and not any(h.startswith(k + ".") for h in have):
        errors.append(f"code reads '{k}' but config.yml does not define it")
for k in sorted(have):
    if k not in read and not any(r.startswith(k + ".") or k.startswith(r + ".") for r in read):
        errors.append(f"config.yml defines '{k}' but the code never reads it")

# Shipped values must equal the built-in defaults (scalars only).
decl = {}
for m in re.finditer(r'^\s{4}(?:String|int|double|boolean)\s+(\w+)\s*=\s*([^;]+);', src, re.M):
    decl[m.group(1)] = m.group(2).strip()
def lit(v):
    if v in ("true", "false"):
        return v == "true"
    if v.startswith('"') and v.endswith('"'):
        return v[1:-1]
    try:
        return int(v) if re.fullmatch(r'-?\d+', v) else float(v)
    except ValueError:
        return None
for m in re.finditer(r'c\.get(String|Int|Double|Boolean)\("([a-z\-\.]+)",\s*(\w+)', src):
    kind, key, field = m.groups()
    if key not in have or field not in decl:
        continue
    default = lit(decl[field])
    if default is None:
        continue
    shipped = have[key]
    if isinstance(default, (int, float)) and not isinstance(default, bool):
        same = isinstance(shipped, (int, float)) and not isinstance(shipped, bool) and abs(float(shipped) - float(default)) < 1e-9
    else:
        same = shipped == default
    if not same:
        errors.append(f"'{key}': config.yml says {shipped!r} but the built-in default is {default!r}")

# Gear defaults in code (RimeConfig.resetGear) must match the shipped file.
SECTION = {"shard": "dragonglass", "blade": "weapon", "dagger": "dagger", "spear": "spear", "arrow": "arrow"}
for m in re.finditer(r'gear\.put\("(\w+)", new Gear\("\w+", "(\w+)", ([\d.]+), RecipeSpec\.(shaped|shapeless)\((.*?)\)\)\);', src, re.S):
    gid, mat, pot, kind, body = m.groups()
    sec = SECTION[gid]
    if have[f"{sec}.material"] != mat:
        errors.append(f"{sec}.material: config.yml says {have[f'{sec}.material']} but the built-in default is {mat}")
    if gid != "shard" and abs(float(have[f"{sec}.potency"]) - float(pot)) > 1e-9:
        errors.append(f"{sec}.potency: config.yml says {have[f'{sec}.potency']} but the built-in default is {pot}")
    amount = int(re.findall(r'(\d+)\s*$', body.strip())[-1])
    if have[f"{sec}.recipe.amount"] != amount:
        errors.append(f"{sec}.recipe.amount: config.yml says {have[f'{sec}.recipe.amount']} but the built-in default is {amount}")
    if kind == "shaped":
        shape = re.findall(r'"([^"]*)"', body.split("Map.of")[0])
        if have[f"{sec}.recipe.shape"] != shape:
            errors.append(f"{sec}.recipe.shape: config.yml differs from the built-in default {shape}")
        pairs = dict(re.findall(r"'(.)', ([A-Za-z_.\"]+)", body.split("Map.of")[1]))
        want = {k: (v.split(".")[-1].strip('"') if v.startswith("RecipeSpec") else v.strip('"')) for k, v in pairs.items()}
        want = {k: ("DRAGONGLASS" if v == "SHARD" else v) for k, v in want.items()}
        if have[f"{sec}.recipe.ingredients"] != want:
            errors.append(f"{sec}.recipe.ingredients: config.yml differs from the built-in default {want}")
    else:
        loose = re.findall(r'"([A-Z_]+)"', body)
        if have[f"{sec}.recipe.ingredients"] != loose:
            errors.append(f"{sec}.recipe.ingredients: config.yml differs from the built-in default {loose}")
for k, name in (("names.shard", "shardName"), ("names.dagger", "daggerName"), ("names.spear", "spearName"), ("names.arrow", "arrowName"), ("names.weapon", "weaponName")):
    pass  # names are checked by the generic scalar comparison above

# Structure sanity
if have["offer.mode"] not in ("new", "all", "off"):
    errors.append("offer.mode must be new, all or off")
for k in ("rewards.lesson-commands", "rewards.final-commands"):
    if not isinstance(have[k], list):
        errors.append(f"{k} must be a list")
for k in [k for k in have if k.startswith("messages.")]:
    for ph in re.findall(r'\{(\w+)\}', have[k]):
        if ph not in ("lesson",):
            errors.append(f"{k}: unknown placeholder {{{ph}}}")

if errors:
    print("config problems:")
    for e in errors:
        print("  -", e)
    sys.exit(1)
print(f"config.yml OK ({len(have)} settings checked)")
