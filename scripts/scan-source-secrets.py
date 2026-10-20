#!/usr/bin/env python3
"""Conservative source gate; complements, does not replace, full secret scanning."""
from pathlib import Path
import re
import json
import base64
import sys

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    re.compile(r'\b(?:ghp_[A-Za-z0-9]{30,}|glpat-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})\b'),
    re.compile(r'eyJ[A-Za-z0-9_-]{15,}\.eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{20,}'),
]
SKIP = {'.git', '.gradle', '.kotlin', 'build', 'node_modules'}
failures = []
for path in ROOT.rglob('*'):
    if not path.is_file() or any(part in SKIP for part in path.relative_to(ROOT).parts):
        continue
    if path.suffix in {'.jks', '.keystore'}:
        failures.append(f'{path.relative_to(ROOT)}: private signing material')
        continue
    if path.name == 'google-services.json' or path.name == 'scan-source-secrets.py':
        continue  # Firebase public app configuration is not an authorization secret.
    if path.stat().st_size > 2_000_000:
        continue
    try:
        text = path.read_text()
    except (UnicodeError, OSError):
        continue
    for number, line in enumerate(text.splitlines(), 1):
        # Supabase's embedded anon JWT is public client configuration, never a service-role key.
        if path.name == 'CloudManager.kt':
            match = PATTERNS[2].search(line)
            if match:
                try:
                    payload = match.group().split('.')[1]
                    claims = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
                    if claims.get('role') == 'anon':
                        line = line.replace(match.group(), '')
                except (ValueError, KeyError):
                    pass
        if any(pattern.search(line) for pattern in PATTERNS):
            failures.append(f'{path.relative_to(ROOT)}:{number}: potential credential (redacted)')
if failures:
    print('\n'.join(failures), file=sys.stderr)
    sys.exit(1)
print('No private signing files or recognized credential patterns found in source.')
