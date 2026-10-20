#!/usr/bin/env python3
"""Fail-closed 16 KiB ELF alignment check for Plavio's bundled JNI libraries.

Checks each shared object's ELF class/machine for its Android ABI and requires
all PT_LOAD segments to have p_align >= 16 KiB and valid offset/address
congruence. This does not validate APK ZIP alignment, runtime behavior, or
libraries supplied outside app/src/main/jniLibs.
"""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

PAGE_ALIGNMENT = 16 * 1024
EXPECTED = {
    "arm64-v8a": (2, 183),       # ELFCLASS64, EM_AARCH64
    "armeabi-v7a": (1, 40),      # ELFCLASS32, EM_ARM
}
LIBRARY = "libffmpegJNI.so"
PT_LOAD = 1


class ElfError(ValueError):
    pass


def read_elf(path: Path) -> tuple[int, int, list[tuple[int, int, int]]]:
    data = path.read_bytes()
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise ElfError("not an ELF file")
    elf_class, encoding = data[4], data[5]
    if elf_class not in (1, 2) or encoding not in (1, 2):
        raise ElfError("unsupported ELF class or byte order")
    endian = "<" if encoding == 1 else ">"
    if elf_class == 1:
        header_fmt = endian + "HHIIIIIHHHHHH"
        ph_fmt = endian + "IIIIIIII"
        phoff_index, phentsize_index, phnum_index = 4, 8, 9
    else:
        header_fmt = endian + "HHIQQQIHHHHHH"
        ph_fmt = endian + "IIQQQQQQ"
        phoff_index, phentsize_index, phnum_index = 4, 8, 9
    header_size = 16 + struct.calcsize(header_fmt)
    if len(data) < header_size:
        raise ElfError("truncated ELF header")
    header = struct.unpack_from(header_fmt, data, 16)
    machine = header[1]
    phoff, phentsize, phnum = header[phoff_index], header[phentsize_index], header[phnum_index]
    expected_phsize = struct.calcsize(ph_fmt)
    if phentsize < expected_phsize:
        raise ElfError(f"program-header entry too small ({phentsize} < {expected_phsize})")
    if phoff + phentsize * phnum > len(data):
        raise ElfError("truncated program-header table")
    loads = []
    for idx in range(phnum):
        off = phoff + idx * phentsize
        entry = struct.unpack_from(ph_fmt, data, off)
        if elf_class == 1:
            p_type, p_offset, p_vaddr, _paddr, _filesz, _memsz, _flags, p_align = entry
        else:
            p_type, _flags, p_offset, p_vaddr, _paddr, _filesz, _memsz, p_align = entry
        if p_type == PT_LOAD:
            loads.append((p_offset, p_vaddr, p_align))
    if not loads:
        raise ElfError("no PT_LOAD segments found")
    return elf_class, machine, loads


def inspect(path: Path, abi: str) -> list[str]:
    errors = []
    expected = EXPECTED.get(abi)
    if expected is None:
        return [f"unsupported ABI directory: {abi}"]
    try:
        elf_class, machine, loads = read_elf(path)
    except (OSError, ElfError, struct.error) as exc:
        return [f"{path}: cannot inspect ELF: {exc}"]
    if (elf_class, machine) != expected:
        errors.append(
            f"{path}: wrong ELF ABI (class={elf_class}, machine={machine}); "
            f"expected class={expected[0]}, machine={expected[1]} for {abi}"
        )
    for index, (offset, vaddr, align) in enumerate(loads, 1):
        if align < PAGE_ALIGNMENT:
            errors.append(
                f"{path}: PT_LOAD #{index} p_align=0x{align:x}; "
                f"requires >=0x{PAGE_ALIGNMENT:x} (16 KiB)"
            )
        elif (offset - vaddr) % align != 0:
            errors.append(
                f"{path}: PT_LOAD #{index} has invalid p_offset/p_vaddr "
                f"congruence for p_align=0x{align:x}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "native_root", nargs="?",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/jniLibs",
        help="jniLibs directory (defaults to this checkout's app/src/main/jniLibs)",
    )
    args = parser.parse_args()
    root = args.native_root
    errors: list[str] = []
    if not root.is_dir():
        print(f"FAIL: JNI library directory does not exist: {root}", file=sys.stderr)
        return 2

    actual_abis = {p.name for p in root.iterdir() if p.is_dir()}
    expected_abis = set(EXPECTED)
    for abi in sorted(expected_abis - actual_abis):
        errors.append(f"missing required ABI directory: {root / abi}")
    for abi in sorted(actual_abis - expected_abis):
        errors.append(f"unexpected ABI directory (review explicitly): {root / abi}")

    for abi in sorted(expected_abis & actual_abis):
        abi_dir = root / abi
        libraries = sorted(abi_dir.glob("*.so"))
        target = abi_dir / LIBRARY
        if not target.is_file():
            errors.append(f"missing required codec library: {target}")
        for path in libraries:
            if path.name == LIBRARY:
                errors.extend(inspect(path, abi))
            else:
                errors.extend(inspect(path, abi))
        if not libraries:
            errors.append(f"no shared libraries found in {abi_dir}")

    if errors:
        print("FAIL: native 16 KiB alignment/ABI verification", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"PASS: {root}: all shared libraries have expected ABI and 16 KiB PT_LOAD alignment")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
