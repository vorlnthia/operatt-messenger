#!/usr/bin/env python3
"""Reproducibly brand the GPL-3.0 Snikket iOS source for Operatt testing.

Usage: python3 ios/prepare_upstream.py /path/to/snikket-ios

Copyrights of upstream Snikket, Siskin and Tigase remain with their authors.
This script does not create Apple signing credentials or publish an iOS app.
"""
from __future__ import annotations

import json
import math
import plistlib
import re
import struct
import sys
import zlib
from pathlib import Path

DOMAIN = "chat.operatt.com"
OLD_ID = "org.snikket.ios"
APP_ID = "com.operatt.messenger.ios"


def replace_exact(path: Path, old: str, new: str) -> None:
    content = path.read_text(encoding="utf-8")
    if old not in content:
        raise RuntimeError(f"Expected upstream anchor missing in {path}: {old[:75]}")
    path.write_text(content.replace(old, new), encoding="utf-8")


def rewrite_identifiers(root: Path) -> None:
    allowed = {".swift", ".plist", ".entitlements", ".pbxproj", ".storyboard", ".strings"}
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in allowed:
            continue
        # Leave upstream's copyright and license files untouched.
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        new = text.replace(OLD_ID, APP_ID)
        new = new.replace("group.snikket.", "group.com.operatt.messenger.")
        if new != text:
            path.write_text(new, encoding="utf-8")
    pbx = root / "Snikket.xcodeproj" / "project.pbxproj"
    old = pbx.read_text(encoding="utf-8")
    updated = re.sub(r'SENTRY_DSN = "[^"]*";', 'SENTRY_DSN = "";', old)
    if updated != old:
        pbx.write_text(updated, encoding="utf-8")


def modify_login(root: Path) -> None:
    reg = root / "Snikket" / "settings" / "RegisterAccountController.swift"
    replace_exact(
        reg,
        "        passwordSuggestNew = false;",
        '        passwordSuggestNew = false;\n'
        '        domainFieldValue = "chat.operatt.com";\n'
        '        DispatchQueue.main.async { [weak self] in\n'
        '            self?.updateDomain("chat.operatt.com")\n'
        '        }',
    )
    login = root / "Snikket" / "settings" / "AddAccountController.swift"
    replace_exact(
        login,
        "    func validateAccount() {",
        '    private func operattJid(_ rawValue: String?) -> BareJID? {\n'
        '        guard let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),\n'
        '              !value.isEmpty else { return nil }\n'
        '        return BareJID(value.contains("@") ? value : value + "@chat.operatt.com")\n'
        '    }\n\n'
        "    func validateAccount() {",
    )
    replace_exact(login, "BareJID(self.jidTextField.text)", "operattJid(self.jidTextField.text)")
    replace_exact(login, "BareJID(jidTextField.text)", "operattJid(jidTextField.text)")
    replace_exact(
        login,
        "        jidTextField.keyboardType = .emailAddress;",
        '        jidTextField.keyboardType = .emailAddress;\n'
        '        jidTextField.placeholder = "Username or username@chat.operatt.com";',
    )


def modify_app_metadata(root: Path) -> None:
    info = root / "Snikket" / "Info.plist"
    with info.open("rb") as fp:
        values = plistlib.load(fp)
    values["CFBundleDisplayName"] = "Operatt Messenger"
    values["CFBundleName"] = "Operatt Messenger"
    values["Sentry DSN"] = ""
    with info.open("wb") as fp:
        plistlib.dump(values, fp, sort_keys=False)
    version = root / "Config" / "Version.xcconfig"
    replace_exact(version, "MARKETING_VERSION = 1.2.5", "MARKETING_VERSION = 0.1.0")


def png_chunk(kind: bytes, data: bytes) -> bytes:
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)


def write_icon(path: Path, n: int) -> None:
    # Render an independent blue messenger icon; do not redistribute Snikket logos.
    rows = []
    for y in range(n):
        row = bytearray([0])
        yy = (y + 0.5) / n
        for x in range(n):
            xx = (x + 0.5) / n
            glow = 0.57 * xx + 0.43 * (1 - yy)
            rgb = (int(20 + 2 * glow), int(83 + 83 * glow), int(204 + 43 * glow))
            ellipse = ((xx - .5) / .30) ** 2 + ((yy - .465) / .218) ** 2 < 1
            # The small lower-right speech-bubble tail is a distinct branded motif.
            tail = (.59 < xx < .79 and .57 < yy < .735
                    and yy < .735 - .54 * (.79 - xx))
            dot = any(((xx - cx) / .023) ** 2 + ((yy - .465) / .023) ** 2 < 1
                      for cx in (.405, .5, .595))
            if ellipse or tail:
                rgb = (24, 113, 226) if dot else (255, 255, 255)
            row.extend(rgb)
        rows.append(bytes(row))
    raw = b"".join(rows)
    png = (b"\x89PNG\r\n\x1a\n"
           + png_chunk(b"IHDR", struct.pack(">IIBBBBB", n, n, 8, 2, 0, 0, 0))
           + png_chunk(b"IDAT", zlib.compress(raw, level=7))
           + png_chunk(b"IEND", b""))
    path.write_bytes(png)


def replace_icons(root: Path) -> None:
    icon_dir = root / "Snikket" / "Assets.xcassets" / "AppIcon.appiconset"
    config = json.loads((icon_dir / "Contents.json").read_text(encoding="utf-8"))
    for item in config["images"]:
        filename = item.get("filename")
        if filename:
            size_match = re.search(r"logo-(\d+)px\.png", filename)
            if size_match is None:
                raise RuntimeError("Unknown upstream app icon name: " + filename)
            write_icon(icon_dir / filename, int(size_match.group(1)))
    logo = root / "Snikket" / "Assets.xcassets" / "appLogo.imageset" / "logo-1024px.png"
    write_icon(logo, 1024)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: prepare_upstream.py /path/to/snikket-ios")
    root = Path(sys.argv[1]).resolve()
    if not (root / "Snikket.xcodeproj" / "project.pbxproj").is_file():
        raise SystemExit("Not a Snikket iOS checkout: " + str(root))
    rewrite_identifiers(root)
    modify_login(root)
    modify_app_metadata(root)
    replace_icons(root)
    print("Operatt iOS onboarding and distinct app branding prepared.")
    print("XMPP domain:", DOMAIN, "; bundle ID:", APP_ID)
    print("iOS code signing and push notification provisioning remain separate steps.")


if __name__ == "__main__":
    main()
