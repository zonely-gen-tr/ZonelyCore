from pathlib import Path
import json
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
paths = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
blocked = []
for path in filter(None, paths):
    parts = Path(path).parts
    if any(part in {"node_modules", "target", "dist", ".idea", ".codex-fix-work", "Web"} for part in parts):
        blocked.append(path)
    if Path(path).suffix.lower() in {".jar", ".class", ".zip", ".7z", ".sql", ".log"}:
        blocked.append(path)
    if Path(path).name.startswith(".env") and Path(path).name != ".env.example":
        blocked.append(path)
assert not blocked, "Private/build files must not be tracked: " + ", ".join(blocked)

pom = ET.parse(root / "pom.xml").getroot()
version = pom.find("{http://maven.apache.org/POM/4.0.0}version").text
assert re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,3}", version)
plugin = (root / "src/main/resources/plugin.yml").read_text(encoding="utf-8")
assert re.search(r"^version: \$\{project.version\}\s*$", plugin, re.M)

config = (root / "src/main/resources/config.yml").read_text(encoding="utf-8")
assert re.search(r'^web-token: ["\']{2}\s*$', config, re.M), "Example token must stay blank"
assert re.search(r'^    password: ["\']{2}\s*$', config, re.M), "Example DB password must stay blank"
assert "Copyright (c) 2025-2026 Zonely" in (root / "LICENSE").read_text(encoding="utf-8")
fixture = json.loads((root / "src/test/resources/store-fixture.json").read_text(encoding="utf-8"))
assert len(fixture["products"]) == 2
assert all(product["slug"].startswith("local-test-") for product in fixture["products"])
print("Public source package checks passed. Maven/runtime tests require the local dependencies documented in docs/GELISTIRME.md.")
