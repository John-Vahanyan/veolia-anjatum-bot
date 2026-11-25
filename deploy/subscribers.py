#!/usr/bin/env python3
"""List the bot's subscribers and their addresses. Read-only; messages nobody.

Run on the droplet as the bot's own service account (no root needed for the data):

    sudo -u veolia-bot /opt/veolia-bot/subscribers.py
    sudo -u veolia-bot /opt/veolia-bot/subscribers.py --district kentron
    sudo -u veolia-bot /opt/veolia-bot/subscribers.py --user @someone
    sudo -u veolia-bot /opt/veolia-bot/subscribers.py --help

Names and usernames aren't stored in the database, so they're looked up through
Telegram's getChat unless --no-names is given. Standard library only.
"""
import argparse
import json
import os
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

DEFAULT_ENV_FILE = "/opt/veolia-bot/veolia-bot.env"


def iso_date(value):
    try:
        return datetime.strptime(value, "%Y-%m-%d").strftime("%Y-%m-%d")
    except ValueError:
        raise argparse.ArgumentTypeError(f"{value!r} is not a YYYY-MM-DD date")


def parse_args():
    parser = argparse.ArgumentParser(
        description="List the bot's subscribers and their addresses.",
        epilog="Filters combine (all must match) and are case-insensitive partial matches.")
    parser.add_argument("--user", help="Telegram @username or name, or an exact numeric id")
    parser.add_argument("--region", help="region code, e.g. yerevan, lori, vayots-dzor")
    parser.add_argument("--district", help="Yerevan district code, e.g. kentron, nor-nork")
    parser.add_argument("--street", help="stored street/keyword text, in Armenian letters")
    parser.add_argument("--type", choices=["street", "area", "keyword"],
                        help="street inside an area, whole area, or unscoped keyword")
    parser.add_argument("--since", type=iso_date, metavar="YYYY-MM-DD", help="subscribed on/after this UTC date")
    parser.add_argument("--until", type=iso_date, metavar="YYYY-MM-DD", help="subscribed on/before this UTC date")
    parser.add_argument("--no-names", action="store_true", help="skip the Telegram lookup, show ids only")
    parser.add_argument("--env-file", default=DEFAULT_ENV_FILE, help="default: %(default)s")
    parser.add_argument("--db", help="SQLite file (default: BOT_DB_PATH from the env file)")
    args = parser.parse_args()
    if args.no_names and args.user and not args.user.isdigit():
        parser.error("--user needs a numeric id when --no-names is given")
    return args


def read_env(path):
    env = {}
    with open(path, encoding="utf-8") as lines:
        for line in lines:
            key, sep, value = line.strip().partition("=")
            if sep and not key.startswith("#"):
                env[key.strip()] = value.strip().strip("\"'")
    return env


def load_rows(path):
    db = sqlite3.connect(f"file:{urllib.request.pathname2url(os.path.abspath(path))}?mode=ro", uri=True)
    try:
        return db.execute(
            "SELECT user_id, region_code, district_code, keyword, subscription_type, created_at "
            "FROM subscriptions ORDER BY user_id, created_at").fetchall()
    finally:
        db.close()


def kind_of(region, subscription_type):
    if subscription_type == "SCOPED_STREET":
        return "street"
    return "area" if region else "keyword"


def contains(text, wanted):
    return wanted is None or wanted.casefold() in text.casefold()


def code_contains(code, wanted):
    return wanted is None or contains(code, wanted.strip().replace("-", "_").replace(" ", "_"))


def matches(row, args):
    _, region, district, keyword, subscription_type, created = row
    return (code_contains(region, args.region)
            and code_contains(district, args.district)
            and contains(keyword, args.street)
            and (args.type is None or kind_of(region, subscription_type) == args.type)
            and (args.since is None or created[:10] >= args.since)
            and (args.until is None or created[:10] <= args.until))


def printable(text):
    """Names and keywords are user-typed: drop control characters so they can't mess with the terminal."""
    return "".join(c for c in text if c.isprintable())


def look_up(token, user_id):
    """The user's "@username  Name" as Telegram reports it now, or why it couldn't be found."""
    url = f"https://api.telegram.org/bot{token}/getChat?chat_id={user_id}"
    for attempt in range(2):
        try:
            with urllib.request.urlopen(url, timeout=10) as response:
                chat = json.load(response)["result"]
            break
        except urllib.error.HTTPError as error:
            if error.code == 429 and attempt == 0:
                time.sleep(int(error.headers.get("Retry-After") or 5))
                continue
            return f"(lookup failed: HTTP {error.code})"
        except Exception as error:  # the message can contain the URL, hence the token: show the type only
            return f"(lookup failed: {type(error).__name__})"
    username = "@" + chat["username"] if chat.get("username") else "(no username)"
    name = " ".join(filter(None, [chat.get("first_name"), chat.get("last_name")])) or chat.get("title", "")
    return printable(f"{username}  {name}").strip()


def main():
    args = parse_args()
    try:
        env = {} if args.db and args.no_names else read_env(args.env_file)
        # A relative BOT_DB_PATH is relative to the service's working directory, next to the env file.
        db_path = args.db or os.path.join(os.path.dirname(os.path.abspath(args.env_file)),
                                          env.get("BOT_DB_PATH") or "data/bot.db")
        rows = load_rows(db_path)
    except (OSError, sqlite3.Error) as error:
        sys.exit(f"Cannot read the bot's data: {error}\n"
                 "Run it as the service account: sudo -u veolia-bot /opt/veolia-bot/subscribers.py")
    token = env.get("BOT_TOKEN")
    if not args.no_names and not token:
        sys.exit(f"No BOT_TOKEN in {args.env_file}; pass --no-names to list ids only")

    by_user = {}
    for row in rows:
        if matches(row, args) and (not (args.user and args.user.isdigit()) or row[0] == int(args.user)):
            by_user.setdefault(row[0], []).append(row)

    shown_users = shown_subscriptions = failed = 0
    for user_id, subscriptions in by_user.items():
        label = ""
        if not args.no_names:
            label = look_up(token, user_id)
            failed += label.startswith("(lookup failed")
            time.sleep(0.05)
            if args.user and not args.user.isdigit() and (
                    label.startswith("(lookup failed") or not contains(label.replace("(no username)", ""), args.user)):
                continue
        print(f"\n{label}  (id {user_id})" if label else f"\nid {user_id}")
        for _, region, district, keyword, subscription_type, created in subscriptions:
            text = printable(keyword) + (" (whole area)" if kind_of(region, subscription_type) == "area" else "")
            print("   - " + " / ".join(filter(None, [region, district, text])) + f"   [{created} UTC]")
        shown_users += 1
        shown_subscriptions += len(subscriptions)

    print(f"\n{shown_users} users, {shown_subscriptions} subscriptions")
    if failed:
        print(f"{failed} name lookups failed; such users can't be matched by --user name", file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
