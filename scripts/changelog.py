#!/usr/bin/env python3
"""Maintain CHANGELOG.md (Keep a Changelog format).

Usage:
  scripts/changelog.py release <version> [--date YYYY-MM-DD]
      Move the [Unreleased] entries into a new "## [<version>] - <date>"
      section and leave an empty [Unreleased] above it.
  scripts/changelog.py check <version>
      Exit non-zero unless <version> has a non-empty section.
  scripts/changelog.py notes <version>
      Print the body of <version>'s section (used as GitHub release notes).

A leading "v" on <version> is ignored, so tags such as v2.3.0 work as-is.
This file is identical in every Middleware mobile SDK repo; keep it that way.
"""

import argparse
import datetime
import pathlib
import re
import sys

CHANGELOG = pathlib.Path(__file__).resolve().parent.parent / "CHANGELOG.md"
UNRELEASED = "Unreleased"
HEADING = re.compile(r"^## \[([^\]]+)\]")
VERSION = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
COMMENT = re.compile(r"<!--.*?-->", re.S)


def fail(message):
    sys.exit(f"changelog: {message}")


def normalize(version):
    version = version.strip()
    if version.startswith("v"):
        version = version[1:]
    if not VERSION.match(version):
        fail(f"'{version}' is not a semantic version (X.Y.Z)")
    return version


def find_section(lines, name):
    """Return (heading index, end index) of the '## [name]' section, or None."""
    start = None
    for i, line in enumerate(lines):
        match = HEADING.match(line)
        if not match:
            continue
        if start is not None:
            return start, i
        if match.group(1) == name:
            start = i
    return (start, len(lines)) if start is not None else None


def body(lines, section):
    """The section's lines without its heading or surrounding blank lines."""
    start, end = section
    text = "\n".join(lines[start + 1:end]).strip("\n")
    return text.split("\n") if text.strip() else []


def has_content(lines):
    return bool(COMMENT.sub("", "\n".join(lines)).strip())


def read():
    if not CHANGELOG.exists():
        fail(f"{CHANGELOG} not found")
    return CHANGELOG.read_text().split("\n")


def section_body(lines, version):
    section = find_section(lines, version)
    if section is None:
        fail(f"no '## [{version}]' section in CHANGELOG.md")
    entries = body(lines, section)
    if not has_content(entries):
        fail(f"the [{version}] section in CHANGELOG.md is empty")
    return entries


def release(version, date):
    lines = read()
    if find_section(lines, version) is not None:
        fail(f"CHANGELOG.md already has a [{version}] section")
    unreleased = find_section(lines, UNRELEASED)
    if unreleased is None:
        fail(f"no '## [{UNRELEASED}]' section in CHANGELOG.md")
    entries = body(lines, unreleased)
    if not has_content(entries):
        fail(f"[{UNRELEASED}] is empty; add entries for {version} first")

    start, end = unreleased
    released = [f"## [{UNRELEASED}]", "", f"## [{version}] - {date}", "", *entries, ""]
    lines[start:end] = released
    CHANGELOG.write_text("\n".join(lines))
    print(f"CHANGELOG.md: moved [{UNRELEASED}] to [{version}] - {date}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)
    release_cmd = commands.add_parser("release", help="cut a version from [Unreleased]")
    release_cmd.add_argument("version")
    release_cmd.add_argument("--date", default=datetime.date.today().isoformat())
    commands.add_parser("check", help="require a non-empty section").add_argument("version")
    commands.add_parser("notes", help="print a version's section").add_argument("version")
    args = parser.parse_args()

    version = normalize(args.version)
    if args.command == "release":
        release(version, args.date)
    elif args.command == "check":
        section_body(read(), version)
        print(f"CHANGELOG.md has a [{version}] section")
    else:
        print("\n".join(section_body(read(), version)))


if __name__ == "__main__":
    main()
