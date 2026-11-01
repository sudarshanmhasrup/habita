#!/usr/bin/env bash
# Hardens a fresh Ubuntu 24.04 server and installs what Habita needs. Run it once, as root, from
# your machine:
#
#   ssh root@<server-ip> 'bash -s' < deploy/setup-server.sh
#
# Afterwards root login and password login are off. Log in as the admin user instead:
#
#   ssh habita@<server-ip>
set -euo pipefail

ADMIN_USER="${HABITA_ADMIN_USER:-habita}"
APP_DIR=/opt/habita
# Matches the user inside the server image, so the container can write its database.
APP_UID=10001

if [ "$(id -u)" -ne 0 ]; then
    echo "Run this as root." >&2
    exit 1
fi
if [ ! -s /root/.ssh/authorized_keys ]; then
    echo "Root has no SSH key. Add one first, or turning off password login would lock you out." >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "==> Updating packages"
apt-get update
apt-get -y upgrade
apt-get -y install ufw fail2ban unattended-upgrades sqlite3 ca-certificates curl

echo "==> Creating the admin user $ADMIN_USER with root's SSH keys"
if ! id "$ADMIN_USER" >/dev/null 2>&1; then
    adduser --disabled-password --gecos "" "$ADMIN_USER"
fi
usermod -aG sudo "$ADMIN_USER"
install -d -m 700 -o "$ADMIN_USER" -g "$ADMIN_USER" "/home/$ADMIN_USER/.ssh"
install -m 600 -o "$ADMIN_USER" -g "$ADMIN_USER" /root/.ssh/authorized_keys "/home/$ADMIN_USER/.ssh/authorized_keys"
# The user has no password, so sudo must not ask for one. SSH keys are the only way in.
echo "$ADMIN_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$ADMIN_USER"
chmod 440 "/etc/sudoers.d/90-$ADMIN_USER"
visudo -cf "/etc/sudoers.d/90-$ADMIN_USER"

echo "==> Allowing SSH keys only, without root"
cat > /etc/ssh/sshd_config.d/90-habita.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
MaxAuthTries 3
X11Forwarding no
EOF
sshd -t
systemctl reload ssh

echo "==> Opening only SSH, HTTP and HTTPS"
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> Banning repeated failed SSH logins"
cat > /etc/fail2ban/jail.d/sshd.local <<'EOF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable fail2ban
systemctl restart fail2ban

echo "==> Installing security updates automatically"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF

echo "==> Installing Docker"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin
usermod -aG docker "$ADMIN_USER"

echo "==> Preparing $APP_DIR"
install -d -o "$ADMIN_USER" -g "$ADMIN_USER" "$APP_DIR"
install -d -m 700 -o "$APP_UID" -g "$APP_UID" "$APP_DIR/data"
install -d -m 700 "$APP_DIR/backups"

echo "==> Scheduling the nightly backup at 03:30"
cat > /etc/cron.d/habita-backup <<EOF
30 3 * * * root $APP_DIR/backup.sh >> /var/log/habita-backup.log 2>&1
EOF

echo
echo "Done. Next, from your machine:"
echo "  scp deploy/compose.yaml deploy/Caddyfile deploy/backup.sh deploy/.env.example $ADMIN_USER@<server-ip>:$APP_DIR/"
echo "Then log in with: ssh $ADMIN_USER@<server-ip>"