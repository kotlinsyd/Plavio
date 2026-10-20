#!/usr/bin/env python3
"""Read-only inventory of bundled FFmpeg JNI ELF artifacts.

Requires Python 3, GNU/LLVM readelf, and nm. Decoder symbols are static build
indicators only: they do not establish successful runtime decoding or provenance.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIBRARIES = {
    "arm64-v8a": ("AArch64", "ELF64"),
    "armeabi-v7a": ("ARM", "ELF32"),
}
PAGE_SIZE = 16 * 1024
AUDIO_DECODERS = {
    "ac3": "AC-3 / Dolby Digital",
    "eac3": "E-AC-3 / Dolby Digital Plus",
    "dca": "DTS family (FFmpeg DCA decoder)",
    "truehd": "Dolby TrueHD",
    "mlp": "MLP (used by TrueHD bitstreams)",
    "aac": "AAC",
    "alac": "Apple Lossless",
    "flac": "FLAC",
    "mp3": "MPEG audio Layer III",
    "opus": "Opus",
    "vorbis": "Vorbis",
}


def run(*args: str) -> str:
    return subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE).stdout


def inspect(abi: str, expected_machine: str, expected_class: str) -> dict:
    path = ROOT / "app/src/main/jniLibs" / abi / "libffmpegJNI.so"
    if not path.is_file():
        raise FileNotFoundError(f"missing expected JNI library: {path}")

    header = run("readelf", "-h", str(path))
    machine_match = re.search(r"^\s*Machine:\s*(.+)$", header, re.M)
    class_match = re.search(r"^\s*Class:\s*(.+)$", header, re.M)
    if not machine_match or not class_match:
        raise ValueError(f"could not parse ELF identity for {path}")
    machine = machine_match.group(1).strip()
    elf_class = class_match.group(1).strip()

    loads = []
    for line in run("readelf", "-lW", str(path)).splitlines():
        parts = line.split()
        if len(parts) >= 8 and parts[0] == "LOAD":
            offset, vaddr, paddr, filesz, memsz = (int(x, 16) for x in parts[1:6])
            align = int(parts[-1], 16)
            loads.append({
                "offset": offset,
                "virtual_address": vaddr,
                "physical_address": paddr,
                "file_size": filesz,
                "memory_size": memsz,
                "flags": " ".join(parts[6:-1]),
                "alignment": align,
                "alignment_16k_ok": align >= PAGE_SIZE and offset % PAGE_SIZE == vaddr % PAGE_SIZE,
            })
    if not loads:
        raise ValueError(f"no PT_LOAD segments parsed for {path}")

    notes = run("readelf", "-nW", str(path))
    build_id_match = re.search(r"Build ID:\s*([0-9a-fA-F]+)", notes)
    symbols = run("nm", "-D", "--defined-only", str(path))
    decoder_symbols = sorted({
        match.group(1)
        for line in symbols.splitlines()
        if (match := re.search(r"\b(ff_[A-Za-z0-9_]+_decoder)$", line.split()[-1] if line.split() else ""))
    })
    audio_present = {
        codec: {"description": label, "symbol": f"ff_{codec}_decoder", "symbol_present": f"ff_{codec}_decoder" in decoder_symbols}
        for codec, label in AUDIO_DECODERS.items()
    }
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "abi": abi,
        "path": str(path.relative_to(ROOT)),
        "size_bytes": path.stat().st_size,
        "sha256": digest,
        "elf_class": elf_class,
        "machine": machine,
        "expected_elf_class": expected_class,
        "expected_machine": expected_machine,
        "abi_identity_matches": elf_class == expected_class and machine == expected_machine,
        "gnu_build_id": build_id_match.group(1).lower() if build_id_match else None,
        "pt_load_segments": loads,
        "all_pt_loads_16k_aligned_and_congruent": all(s["alignment_16k_ok"] for s in loads),
        "exported_ffmpeg_decoder_symbols": decoder_symbols,
        "selected_audio_decoder_symbol_evidence": audio_present,
        "evidence_limit": "Exported decoder registration symbols indicate compiled-in code only; not runtime decode, licensing, full feature completeness, or source/build provenance.",
    }


def main() -> int:
    try:
        result = {
            "inspection": "read-only static ELF/hash/symbol inventory",
            "project_root": str(ROOT),
            "required_16k_alignment_bytes": PAGE_SIZE,
            "artifacts": [inspect(abi, *expected) for abi, expected in LIBRARIES.items()],
        }
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print(f"native inventory failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
