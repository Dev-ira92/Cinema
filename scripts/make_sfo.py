#!/usr/bin/env python3
"""
Minimal PARAM.SFO (PSF) builder - standard PlayStation homebrew param file format.
Pure stdlib, no external deps. Produces a valid PSF binary with the handful of
keys PS4/PS5 jailbroken loaders read to list an app on the home screen.
"""
import struct
import sys
import argparse

MAGIC = 0x46535000  # "\0PSF"

# order matters: PSF requires entries sorted by key name (ascending, byte order)
def build_sfo(entries):
    """entries: list of (key, type, value, max_len)
       type: 'utf8' (string) or 'uint32'
    """
    entries = sorted(entries, key=lambda e: e[0])

    key_table = b""
    key_offsets = []
    for key, _, _, _ in entries:
        key_offsets.append(len(key_table))
        key_table += key.encode("ascii") + b"\x00"
    # pad key table to 4-byte alignment
    while len(key_table) % 4 != 0:
        key_table += b"\x00"

    data_table = b""
    data_offsets = []
    index_entries = []
    for (key, typ, value, max_len), koff in zip(entries, key_offsets):
        data_offsets.append(len(data_table))
        if typ == "utf8":
            raw = value.encode("utf-8") + b"\x00"
            fmt = 0x0204  # UTF-8 special (null-terminated) string
            data_len = len(raw)
            buf_len = max_len
            if buf_len < data_len:
                buf_len = data_len
            raw = raw + b"\x00" * (buf_len - data_len)
        elif typ == "uint32":
            raw = struct.pack("<I", value)
            fmt = 0x0404
            data_len = 4
            buf_len = 4
        else:
            raise ValueError("bad type")
        data_table += raw
        index_entries.append((koff, fmt, data_len, buf_len))

    header = struct.pack("<IIIIII",
        MAGIC,
        0x0101,              # version 1.1
        0,                   # key_table_offset placeholder (set below)
        0,                   # data_table_offset placeholder
        len(entries),
        0)  # unused field padding; real header is 5 x u32 after magic+version, fix below

    # Real PSF header layout (20 bytes):
    # u32 magic, u32 version, u32 key_table_start, u32 data_table_start, u32 table_entries
    index_table_size = 16 * len(entries)
    key_table_start = 20 + index_table_size
    data_table_start = key_table_start + len(key_table)

    header = struct.pack("<IIIII",
        MAGIC, 0x0101, key_table_start, data_table_start, len(entries))

    index_table = b""
    for koff, fmt, data_len, buf_len in index_entries:
        index_table += struct.pack("<HHIII", koff, fmt, data_len, buf_len, 0)
    # fix: index entry is key_offset(u16) fmt(u16) len(u32) max_len(u32) data_offset(u32) = 16 bytes
    index_table = b""
    for (koff, fmt, data_len, buf_len), doff in zip(index_entries, data_offsets):
        index_table += struct.pack("<HHIII", koff, fmt, data_len, buf_len, doff)

    return header + index_table + key_table + data_table


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--title", required=True)
    ap.add_argument("--title-id", required=True, help="9-char id, e.g. PPSA12345")
    ap.add_argument("--content-id", required=True, help="e.g. IV0000-PPSA12345_00-CINEHUBPS5000000")
    ap.add_argument("--app-ver", default="01.00")
    ap.add_argument("--version", default="01.000")
    ap.add_argument("--category", default="gd")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    entries = [
        ("APP_VER",       "utf8",   args.app_ver,     8),
        ("CATEGORY",      "utf8",   args.category,     4),
        ("CONTENT_ID",    "utf8",   args.content_id,  48),
        ("TITLE",         "utf8",   args.title,       128),
        ("TITLE_ID",      "utf8",   args.title_id,    12),
        ("VERSION",       "utf8",   args.version,      8),
        ("ATTRIBUTE",     "uint32", 0,                 0),
        ("PUBTOOLINFO",   "utf8",   "",                4),
        ("FORMAT",        "utf8",   "obs",              8),
    ]

    sfo = build_sfo(entries)
    with open(args.out, "wb") as f:
        f.write(sfo)
    print(f"wrote {args.out} ({len(sfo)} bytes)")

if __name__ == "__main__":
    main()
