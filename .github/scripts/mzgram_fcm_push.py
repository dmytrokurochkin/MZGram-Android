#!/usr/bin/env python3
# Stands in for the UnifiedPush gateway's /fcm/ route in the push cold start
# check (mzgram_push_cold.sh): the gateway takes pushes only from Telegram's
# servers, so the CI host signs with a test VAPID key of its own and sends
# straight to Google FCM, the way the gateway forwards.
#
#   mzgram_fcm_push.py key                      -> "<private> <public>"
#   mzgram_fcm_push.py send <private> <public> <token> <body.b64>
#                                               -> FCM's HTTP status
import base64
import json
import sys
import time
import urllib.error
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

FCM_SEND_PREFIX = "https://fcm.googleapis.com/fcm/send/"


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b64url_decode(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def new_key():
    key = ec.generate_private_key(ec.SECP256R1())
    private = key.private_numbers().private_value.to_bytes(32, "big")
    public = key.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
    print(b64url(private), b64url(public))


def vapid_header(private_b64, public_b64):
    key = ec.derive_private_key(int.from_bytes(b64url_decode(private_b64), "big"), ec.SECP256R1())
    header = b64url(json.dumps({"typ": "JWT", "alg": "ES256"}, separators=(",", ":")).encode())
    claims = b64url(json.dumps({"aud": "https://fcm.googleapis.com", "exp": int(time.time()) + 12 * 3600,
                                "sub": "mailto:ci@example.org"}, separators=(",", ":")).encode())
    signing_input = f"{header}.{claims}".encode()
    r, s = decode_dss_signature(key.sign(signing_input, ec.ECDSA(hashes.SHA256())))
    signature = b64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))
    return f"vapid t={header}.{claims}.{signature},k={public_b64}"


def send(private_b64, public_b64, token, body_file):
    body = base64.b64decode(open(body_file).read().strip())
    request = urllib.request.Request(FCM_SEND_PREFIX + token, data=body, method="POST", headers={
        "TTL": "60",
        "Urgency": "high",
        "Content-Encoding": "aes128gcm",
        "Authorization": vapid_header(private_b64, public_b64),
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            print(response.status)
    except urllib.error.HTTPError as e:
        print(e.code, e.read()[:300].decode(errors="replace"))


if __name__ == "__main__":
    if sys.argv[1] == "key":
        new_key()
    else:
        send(*sys.argv[2:6])
