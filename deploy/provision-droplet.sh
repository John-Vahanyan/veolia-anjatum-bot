#!/usr/bin/env bash
# Run ONCE, as root, on a fresh droplet, before the first deploy.
#
# Sets up two separate accounts (least privilege):
#   - veolia-bot  the runtime service account. No login shell. Owns the app
#                 dir and the SQLite data/ subdir. Never touched by CI.
#   - deploy      what GitHub Actions SSHes in as. Can write the jar into
#                 /opt/veolia-bot/ (group-writable) but cannot read data/,
#                 and can run exactly one command as root via sudo:
#                 `systemctl restart|is-active veolia-bot` — nothing else.
#
# Usage: sudo bash provision-droplet.sh
set -euo pipefail

SERVICE_USER=veolia-bot
DEPLOY_USER=deploy
APP_DIR=/opt/veolia-bot

if [[ $EUID -ne 0 ]]; then
  echo "Must be run as root (sudo bash provision-droplet.sh)" >&2
  exit 1
fi

echo "==> Runtime service account: $SERVICE_USER"
id -u "$SERVICE_USER" &>/dev/null || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
mkdir -p "$APP_DIR/data"
chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"
chmod 750 "$APP_DIR"
chmod 700 "$APP_DIR/data"

echo "==> Deploy account: $DEPLOY_USER"
id -u "$DEPLOY_USER" &>/dev/null || useradd --create-home --shell /bin/bash "$DEPLOY_USER"
usermod -aG "$SERVICE_USER" "$DEPLOY_USER"
chmod g+w "$APP_DIR"   # deploy can write the jar; data/ stays 700, veolia-bot-only

mkdir -p "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 700 "/home/$DEPLOY_USER/.ssh"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown -R "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"

echo "==> Restart-only sudo rule for $DEPLOY_USER"
# Cover both /usr/bin/systemctl and /bin/systemctl explicitly: sudoers matches
# commands as literal strings, and on Debian/Ubuntu one is typically a symlink
# to the other, but which path a given shell resolves via PATH can differ
# between an interactive root login (used here) and the non-interactive SSH
# command GitHub Actions runs — a single resolved path can silently mismatch
# and fall through to an (impossible, non-interactive) password prompt. The
# `is-active *` wildcard likewise covers callers that pass extra flags
# (e.g. `--quiet`), which a fixed-argument rule would otherwise reject.
cat > /etc/sudoers.d/veolia-bot-deploy <<EOF
$DEPLOY_USER ALL=(root) NOPASSWD: /usr/bin/systemctl restart veolia-bot
$DEPLOY_USER ALL=(root) NOPASSWD: /bin/systemctl restart veolia-bot
$DEPLOY_USER ALL=(root) NOPASSWD: /usr/bin/systemctl is-active * veolia-bot
$DEPLOY_USER ALL=(root) NOPASSWD: /bin/systemctl is-active * veolia-bot
EOF
chmod 440 /etc/sudoers.d/veolia-bot-deploy
visudo -cf /etc/sudoers.d/veolia-bot-deploy

cat <<EOF

Done. Remaining manual steps:
  1. Append the CI deploy public key to:
       /home/$DEPLOY_USER/.ssh/authorized_keys
  2. Install the systemd unit:
       cp deploy/veolia-bot.service /etc/systemd/system/veolia-bot.service
       systemctl daemon-reload
  3. Create /opt/veolia-bot/veolia-bot.env with BOT_TOKEN, CHANNEL_USERNAME,
     BOT_DB_PATH=/opt/veolia-bot/data/bot.db (see .env.example).
  4. Copy an initial jar into place, then:
       systemctl enable --now veolia-bot
  From here on, GitHub Actions handles every future deploy on push to main.
EOF
