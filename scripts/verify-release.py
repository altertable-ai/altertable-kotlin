"""Check locally published SDK artifacts without contacting Maven Central."""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("repository", type=Path)
parser.add_argument("--signed", action="store_true")
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
version = next(
    line.split("=", 1)[1]
    for line in (root / "gradle.properties").read_text().splitlines()
    if line.startswith("sdk.version=")
)
assert json.loads((root / ".release-please-manifest.json").read_text())["."] == version
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
for artifact, extension in [("altertable-kotlin", "jar"), ("altertable-android", "aar")]:
    directory = args.repository / "ai/altertable/sdk" / artifact / version
    basename = f"{artifact}-{version}"
    filenames = [
        f"{basename}.{extension}",
        f"{basename}.pom",
        f"{basename}.module",
        f"{basename}-sources.jar",
        f"{basename}-javadoc.jar",
    ]
    for filename in filenames:
        path = directory / filename
        assert path.is_file() and path.stat().st_size, path
        if args.signed:
            assert path.with_name(path.name + ".asc").is_file(), path
    pom = ET.parse(directory / f"{basename}.pom").getroot()
    for key, expected in [
        ("groupId", "ai.altertable.sdk"),
        ("artifactId", artifact),
        ("version", version),
    ]:
        assert pom.findtext(f"m:{key}", namespaces=ns) == expected, (artifact, key)
    for key in [
        "name", "description", "url", "licenses/license/name",
        "developers/developer/id", "scm/connection",
    ]:
        element = "/".join("m:" + part for part in key.split("/"))
        assert pom.findtext(element, namespaces=ns), (artifact, key)
    with zipfile.ZipFile(directory / f"{basename}-javadoc.jar") as docs:
        assert any(name.endswith("index.html") for name in docs.namelist()), artifact
    with zipfile.ZipFile(directory / f"{basename}-sources.jar") as sources:
        assert any(name.endswith(".kt") for name in sources.namelist()), artifact
        if artifact == "altertable-kotlin":
            generated = next(name for name in sources.namelist() if name.endswith("/Version.kt"))
            assert f'SDK_VERSION = "{version}"' in sources.read(generated).decode()
    if artifact == "altertable-android":
        dependency = next(
            dep for dep in pom.findall("m:dependencies/m:dependency", ns)
            if dep.findtext("m:artifactId", namespaces=ns) == "altertable-kotlin"
        )
        assert dependency.findtext("m:version", namespaces=ns) == version
        assert pom.findtext("m:packaging", namespaces=ns) == "aar"
    print(f"Verified {artifact}:{version} ({'signed' if args.signed else 'unsigned'})")
