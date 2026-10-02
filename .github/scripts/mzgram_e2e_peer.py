"""Second account (B) for MZGramDeletedArchiveLiveTest, run on the CI host.

Works only against Telegram's public TEST servers (test DC 2, throwaway
99966 X YYYY numbers whose login code is the DC number repeated), never
production.
The API credentials come from the environment and are never printed.

With --preflight instead of a file name, only checks that B can sign in.

Phase 1: pick working phone numbers for A and B (a test number can be
locked by someone else's 2FA password, so retry), sign both in, create a
basic group and a supergroup with B and A as members, and write
"phoneA phoneB code basicChatId channelId" to the file given as argv[1].

Phase 2: follow the emulator's logcat. For each scenario the app (signed
in as A) logs "READY scenario=<name>"; B then sends a message to that
scenario's chat. When the app logs "RECEIVED scenario=<name>", B deletes
that message for everyone -- what another user's "Delete for everyone"
does on a real device.
"""

import asyncio
import os
import random
import re
import subprocess
import sys

from telethon import TelegramClient, functions, types
from telethon.errors import SessionPasswordNeededError, PhoneNumberUnoccupiedError, PhoneCodeInvalidError
from telethon.sessions import StringSession

# Telegram's published test-server addresses, by DC number.
TEST_DCS = {1: "149.154.175.10", 2: "149.154.167.40", 3: "149.154.175.117"}

API_ID = int(os.environ["API_ID"])
API_HASH = os.environ["API_HASH"]


def log(msg):
    print(f"[peer] {msg}", flush=True)


def new_client(dc):
    client = TelegramClient(StringSession(), API_ID, API_HASH)
    client.session.set_dc(dc, TEST_DCS[dc], 443)
    return client


def candidate_codes(dc, sent):
    # Test-server codes are the DC number repeated; the length the server
    # reports for this code wins, 5 and 6 digits are tried as fallbacks.
    length = getattr(sent.type, "length", None)
    lengths = [n for n in (length, 5, 6) if n]
    return list(dict.fromkeys(str(dc) * n for n in lengths))


async def sign_in(client, dc, phone, first_name):
    await client.connect()
    config = await client(functions.help.GetConfigRequest())
    log(f"{phone}: connected dc={client.session.dc_id} test_mode={config.test_mode} this_dc={config.this_dc}")
    if not config.test_mode:
        raise SystemExit("refusing to continue: not connected to Telegram test servers")
    sent = await client.send_code_request(phone)
    log(f"{phone}: code type {type(sent.type).__name__} length={getattr(sent.type, 'length', None)}")
    last_error = None
    for code in candidate_codes(dc, sent):
        try:
            try:
                await client.sign_in(phone, code, phone_code_hash=sent.phone_code_hash)
            except PhoneNumberUnoccupiedError:
                await client.sign_up(code, first_name, phone_code_hash=sent.phone_code_hash)
            return await client.get_me(), code
        except PhoneCodeInvalidError as e:
            log(f"{phone}: code of length {len(code)} rejected")
            last_error = e
    raise last_error


async def usable_account(first_name, exclude):
    for attempt in range(24):
        dc = (1, 2, 3)[attempt % 3]
        phone = f"99966{dc}" + "".join(random.choice("0123456789") for _ in range(4))
        if phone in exclude:
            continue
        client = new_client(dc)
        try:
            me, code = await sign_in(client, dc, phone, first_name)
            log(f"{first_name}: signed in as {phone} id={me.id}")
            return client, phone, me, code
        except SessionPasswordNeededError:
            log(f"{first_name}: {phone} has a 2FA password set by someone else, retrying")
        except Exception as e:  # noqa: BLE001 -- any login failure: try another number
            log(f"{first_name}: {phone} failed ({type(e).__name__}: {e}), retrying")
        await client.disconnect()
    raise SystemExit(f"no usable test number for {first_name}")


async def main(out_file):
    b, phone_b, me_b, _ = await usable_account("MZGram E2E B", set())
    if out_file == "--preflight":
        await b.disconnect()
        log("preflight: test-server login works")
        return
    a, phone_a, me_a, code_a = await usable_account("MZGram E2E A", {phone_b})
    # A is signed in again inside the app; this host session only made sure
    # the account exists and has no password.
    await a.disconnect()

    user_a = (await b(functions.contacts.ResolvePhoneRequest(phone=phone_a))).users[0]

    created = await b(functions.messages.CreateChatRequest(users=[user_a], title="MZGram E2E basic group"))
    basic_chat = getattr(created, "updates", created).chats[0]
    log(f"created basic group id={basic_chat.id}")

    channel = (await b(functions.channels.CreateChannelRequest(
        title="MZGram E2E supergroup", about="", megagroup=True))).chats[0]
    await b(functions.channels.InviteToChannelRequest(channel=channel, users=[user_a]))
    log(f"created supergroup id={channel.id} and invited A")

    targets = {
        "private": user_a,
        "nocache": user_a,
        "basic": types.InputPeerChat(basic_chat.id),
        "channel": channel,
    }

    with open(out_file, "w") as f:
        f.write(f"{phone_a} {phone_b} {code_a} {basic_chat.id} {channel.id}\n")
    log(f"wrote {out_file}: A={phone_a} B={phone_b}")

    proc = subprocess.Popen(["adb", "logcat", "-v", "brief", "MZGramE2E:I", "*:S"],
                            stdout=subprocess.PIPE, text=True)
    sent = {}
    loop = asyncio.get_running_loop()
    while True:
        line = await loop.run_in_executor(None, proc.stdout.readline)
        if not line:
            break
        line = line.strip()
        if "MZGramE2E" not in line:
            continue
        log(f"app: {line}")
        match = re.search(r"\b(READY|RECEIVED) scenario=(\w+)", line)
        if not match:
            if "DONE" in line:
                break
            continue
        event, scenario = match.groups()
        target = targets[scenario]
        if event == "READY" and scenario not in sent:
            text = f"MZGram E2E {scenario} {random.randint(100000, 999999)}"
            sent[scenario] = await b.send_message(target, text)
            log(f"B sent {scenario} id={sent[scenario].id} text='{text}'")
        elif event == "RECEIVED" and scenario in sent:
            await asyncio.sleep(2)
            await b.delete_messages(target, [sent[scenario].id], revoke=True)
            log(f"B deleted {scenario} id={sent[scenario].id} for everyone")
    proc.kill()
    await b.disconnect()


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1]))
