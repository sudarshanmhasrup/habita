# Deploying the Habita server

The server runs as a Docker image behind [Caddy](https://caddyserver.com), which gets and renews
HTTPS certificates on its own. GitHub Actions builds the image on every merge to `main` and
publishes it to the GitHub container registry. The server only pulls it.

You need a small Linux server (Ubuntu 24.04, 1 vCPU and 2 GB RAM is plenty), a domain or subdomain
you control, and an SSH key on your machine.

## 1. Create the server

Create an Ubuntu 24.04 server with your SSH key added, for example the smallest Hetzner Cloud
plan. x86 and ARM both work. Note its public IP address.

## 2. Point your domain at it

Add an `A` record for your subdomain, for example `habita.example.com`, with the server's IP. Add an
`AAAA` record too if the server has IPv6. Caddy cannot get a certificate until this resolves.

## 3. Make the image public

After the first successful run of the Server workflow on `main`, open the `habita-server` package
on GitHub, go to its settings and set the visibility to public. The image holds no secrets, and a
public image lets the server pull it without a GitHub token.

## 4. Harden the server

From the repository root on your machine:

```sh
ssh root@<server-ip> 'bash -s' < deploy/setup-server.sh
```

This turns off root and password login, allows only SSH, HTTP and HTTPS through the firewall, bans
repeated failed SSH logins, turns on automatic security updates, installs Docker and schedules a
nightly database backup. From now on, log in as `habita`:

```sh
ssh habita@<server-ip>
```

## 5. Copy the deployment files

```sh
scp deploy/compose.yaml deploy/Caddyfile deploy/backup.sh deploy/.env.example habita@<server-ip>:/opt/habita/
```

## 6. Configure

Create the password hash on your machine, so the password never reaches the server:

```sh
./gradlew :server:buildFatJar
java -jar .build/server/libs/server-all.jar hash-password
```

Then on the server:

```sh
cd /opt/habita
cp .env.example .env
chmod 600 .env
chmod +x backup.sh
nano .env
```

Set `HABITA_DOMAIN`, `HABITA_EMAIL` and `HABITA_PASSWORD_HASH`. Keep the single quotes around the
hash.

## 7. Start

```sh
docker compose up -d
docker compose logs -f
```

Wait for Caddy to report the certificate, then check from your machine:

```sh
curl https://habita.example.com/health
```

It should print `OK`.

## Updating

Merging to `main` publishes a new image. To run it:

```sh
cd /opt/habita
docker compose pull
docker compose up -d
```

Editing `PROGRAM.md` works the same way: merge the change, then pull and restart.

## Backups

Every night at 03:30 the server copies the database to `/opt/habita/backups`, checks the copy and
keeps 30 days. The log is at `/var/log/habita-backup.log`. Run a backup by hand with
`sudo /opt/habita/backup.sh`.

These copies live on the same server, so they do not survive losing it. To keep copies elsewhere,
install [rclone](https://rclone.org), configure a remote for root with `sudo rclone config`, and
set `HABITA_BACKUP_REMOTE` in `.env`, for example `b2:habita-backups`.

To restore a backup:

```sh
cd /opt/habita
docker compose stop server
gunzip -c backups/<backup-file>.db.gz | sudo tee data/habita.db > /dev/null
sudo chown 10001:10001 data/habita.db
docker compose start server
```

## What is exposed

Only ports 22, 80 and 443 are open. The Habita server itself publishes no port, so it is reachable
only through Caddy over HTTPS. Every route except `/health` and `/auth/login` needs a login token,
and login allows 5 attempts a minute.