"""Проверка проекта без Android SDK: XML + баланс скобок в Java."""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
ok = True

for path in ROOT.rglob("*.xml"):
    try:
        ET.parse(path)
        print("XML ok:", path.relative_to(ROOT))
    except Exception as exc:  # noqa: BLE001
        ok = False
        print("XML FAIL:", path.relative_to(ROOT), exc)

string_literal = re.compile(r'"(?:\\.|[^"\\])*"', re.S)
line_comment = re.compile(r"//[^\n]*")
block_comment = re.compile(r"/\*.*?\*/", re.S)

for path in sorted(ROOT.rglob("*.java")):
    source = path.read_text(encoding="utf-8")
    code = string_literal.sub('""', source)
    code = block_comment.sub("", code)
    code = line_comment.sub("", code)
    for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
        if code.count(opener) != code.count(closer):
            ok = False
            print(f"BALANCE FAIL {path.name}: {opener}{code.count(opener)} {closer}{code.count(closer)}")
        else:
            print(f"balance ok {path.name}: {opener}")
    print(f"{path.name}: {len(source.splitlines())} lines")

sys.exit(0 if ok else 1)
