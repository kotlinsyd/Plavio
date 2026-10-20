#!/usr/bin/env python3
"""Read-only public auth configuration check. Never signs in or logs key/body."""
import base64
import datetime
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'vpngqutsjxrdgaihogor'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def verify():
    source = (ROOT / 'app/src/main/java/com/pulsestream/app/data/cloud/CloudManager.kt').read_text()
    endpoint = re.search(r'const val SUPABASE_URL = "([^"]+)"', source)
    public_key = re.search(r'const val SUPABASE_ANON_KEY = "([^"]+)"', source)
    if not endpoint or not public_key:
        raise ValueError('Missing configuration')
    endpoint, public_key = endpoint.group(1), public_key.group(1)
    if endpoint != f'https://{PROJECT}.supabase.co':
        raise ValueError('Unexpected endpoint')
    parts = public_key.split('.')
    if len(parts) != 3:
        raise ValueError('Expected legacy public key')
    claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
    if claims.get('role') != 'anon' or claims.get('ref') != PROJECT:
        raise ValueError('Not the expected public role/project')
    request = urllib.request.Request(endpoint + '/auth/v1/settings', method='GET',
        headers={'apikey': public_key, 'Content-Type': 'application/json'})
    opener = urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(request, timeout=30) as response:
            status = response.status
            body = response.read(131073)
    except urllib.error.HTTPError as error:
        status = error.code
        error.close()
        body = b''
    valid_settings = False
    if status == 200 and len(body) <= 131072:
        settings = json.loads(body)
        valid_settings = isinstance(settings, dict) and isinstance(settings.get('external'), dict)
    evidence = {
        'checked_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'project_ref': PROJECT,
        'method': 'GET',
        'path': '/auth/v1/settings',
        'key_type': 'active public legacy anon',
        'http_status': status,
        'valid_public_settings_json': valid_settings,
        'redirects_allowed': False,
        'login_attempted': False,
        'private_data_accessed': False,
        'backend_modified': False,
        'key_or_response_body_logged': False,
    }
    if '--write-evidence' in sys.argv:
        output = ROOT / 'docs/public-auth-settings-0.11.8.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(evidence, indent=2) + '\n')
    print(f'Public auth settings: HTTP {status}; valid settings JSON: {valid_settings}. No keys or response body logged.')
    return 0 if status == 200 and valid_settings else 1

if __name__ == '__main__':
    try:
        sys.exit(verify())
    except Exception:
        # Do not print exceptions: transport/configuration errors may include sensitive values.
        print('Public auth settings verification failed; details intentionally withheld.', file=sys.stderr)
        sys.exit(1)
