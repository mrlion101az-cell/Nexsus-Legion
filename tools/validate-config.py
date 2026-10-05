#!/usr/bin/env python3
"""Offline check of NexusLegion's config.yml against what LegionConfig actually reads.

  python3 tools/validate-config.py [path/to/config.yml]

Fails if config.yml is missing a key the code reads (so a typo can never silently fall back to a
default), has a key the code never reads, or has an unusable recipe / soldier type.
Optional: set MCITEMS to a minecraft-data items.json to also verify recipe materials exist.
Needs: pip install pyyaml
"""
import json, os, re, sys
import yaml

here = os.path.dirname(os.path.abspath(__file__))
path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "src", "main", "resources", "config.yml")
src = open(os.path.join(here, "..", "src", "main", "java", "com", "nexuscraft", "nexuslegion", "LegionConfig.java"), encoding="utf-8").read()
cfg = yaml.safe_load(open(path, encoding="utf-8"))
errors = []

def flat(d, prefix=""):
    out = {}
    for k, v in d.items():
        p = prefix + str(k)
        if isinstance(v, dict) and not p.startswith("soldier-types") and not p.endswith("ingredients"):
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

mats = None
if os.environ.get("MCITEMS"):
    mats = {i["name"].upper() for i in json.load(open(os.environ["MCITEMS"]))}

for name in ("banner", "horn"):
    item = cfg["items"][name]
    shape = item["shape"]
    if len(shape) != 3 or any(len(r) > 3 for r in shape):
        errors.append(f"items.{name}.shape must be three rows of at most 3 characters")
    used = set("".join(shape)) - {" "}
    ing = item["ingredients"]
    if used - set(ing):
        errors.append(f"items.{name}: shape uses {sorted(used - set(ing))} with no ingredient")
    for k, v in ing.items():
        if k not in used:
            errors.append(f"items.{name}: ingredient '{k}' is not used in the shape")
        if mats is not None and str(v).upper() not in mats:
            errors.append(f"items.{name}: '{v}' is not a real material")
if mats is not None and str(cfg["recruit"]["cost-item"]).upper() not in mats:
    errors.append("recruit.cost-item is not a real material")

for k, t in cfg["soldier-types"].items():
    for f in ("damage", "speed", "scale", "health", "cost"):
        if f not in t:
            errors.append(f"soldier-types.{k} is missing '{f}'")
    if not (0.0625 <= t.get("scale", 1) <= 1.0):
        errors.append(f"soldier-types.{k}.scale must be between 0.0625 and 1")

print(f"{len(have)} settings, {len(cfg['soldier-types'])} soldier types, {len(errors)} problem(s)")
for e in errors:
    print("  ", e)
sys.exit(1 if errors else 0)
