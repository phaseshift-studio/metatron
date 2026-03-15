# Metatron Web Console Setup

This guide explains how to set up the interactive web console for the Metatron website using ttyd.

## Overview

The web console allows visitors to try Metatron directly in their browser without installing anything. It runs in a sandboxed Docker container with resource limits to prevent abuse.

## Quick Start

### 1. Build the Console Image

```bash
# From the metatron root directory
docker build -f dist/docker/Dockerfile.console -t metatron-console:latest .
```

### 2. Run the Console Container

```bash
docker run -d \
  --name metatron-console \
  --restart=unless-stopped \
  --memory="512m" \
  --cpus="0.5" \
  --read-only \
  --tmpfs /tmp \
  -p 7681:7681 \
  metatron-console:latest
```

### 3. Test Locally

Open your browser to: `http://localhost:7681`

You should see the Metatron console!

### 4. Deploy to Production

Set up a reverse proxy (nginx/Cloudflare) to expose the console:

```nginx
# nginx config
location /console/ {
    proxy_pass http://localhost:7681/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

Or use Cloudflare Tunnel for secure access without opening ports.

## Security Features

### Resource Limits
- **Memory**: 512MB max
- **CPU**: 0.5 cores max
- **Read-only filesystem**: Prevents file modifications
- **Tmpfs /tmp**: Temporary files in memory only

### User Isolation
- Runs as non-root user `metatron-user`
- No sudo/privileged access
- Limited to container environment

### Network Isolation (Optional)
Add `--network none` to completely isolate from network:

```bash
docker run -d \
  --name metatron-console \
  --network none \
  --memory="512m" \
  --cpus="0.5" \
  -p 7681:7681 \
  metatron-console:latest
```

## Advanced Configuration

### Session Timeout

Add timeout to ttyd command in Dockerfile.console:

```dockerfile
ENTRYPOINT ["ttyd", \
    "-W", \
    "-t", "fontSize=14", \
    "-t", "timeout=1800", \  # 30 minute timeout
    "bin/metatron"]
```

### Authentication (Optional)

Add basic auth to prevent abuse:

```dockerfile
ENTRYPOINT ["ttyd", \
    "-W", \
    "-c", "demo:metatron123", \  # username:password
    "-t", "fontSize=14", \
    "bin/metatron"]
```

### Custom Boot Script

Create a sandboxed boot script for the console:

```bash
# boot/console.mtron
[== Metatron Web Console ==]
print("{{g}}Welcome to Metatron!{{X}}\n")
print("{{y}}Try these examples:{{X}}\n")
print("  {1,2,3}.plus(2).sum()\n")
print("  \"hello world\".-<' '\n")
print("  x -> [a=>1, b=>2]\n")
print("  *x.>>a\n")
```

Then modify the Dockerfile to use it:

```dockerfile
ENTRYPOINT ["ttyd", "-W", "bin/metatron", "-b", "boot/console.mtron"]
```

## Monitoring

### View Logs

```bash
docker logs -f metatron-console
```

### Check Resource Usage

```bash
docker stats metatron-console
```

### Restart Container

```bash
docker restart metatron-console
```

## Troubleshooting

### Console Not Loading

1. Check if container is running:
   ```bash
   docker ps | grep metatron-console
   ```

2. Check logs for errors:
   ```bash
   docker logs metatron-console
   ```

3. Verify port is accessible:
   ```bash
   curl http://localhost:7681
   ```

### High Resource Usage

1. Check current usage:
   ```bash
   docker stats metatron-console
   ```

2. Lower limits if needed:
   ```bash
   docker update --memory="256m" --cpus="0.25" metatron-console
   ```

3. Restart to apply:
   ```bash
   docker restart metatron-console
   ```

### Session Hangs

Restart the container to kill all sessions:

```bash
docker restart metatron-console
```

## Integration with Website

Add the console to your Asciidoctor files:

```asciidoc
++++
<div class="metatron-console-container">
    <iframe src="http://console.metatron.phaseshift.studio:7681"
            width="100%"
            height="600px">
    </iframe>
</div>
++++
```

See `console-embed.html` for a complete example with styling and reset button.

## Production Deployment

### Using Docker Compose

```yaml
# docker-compose.yml
version: '3.8'
services:
  metatron-console:
    build:
      context: .
      dockerfile: dist/docker/Dockerfile.console
    container_name: metatron-console
    restart: unless-stopped
    mem_limit: 512m
    cpus: 0.5
    read_only: true
    tmpfs:
      - /tmp
    ports:
      - "7681:7681"
```

Run with:
```bash
docker-compose up -d
```

### Auto-restart on Crash

The `--restart=unless-stopped` flag ensures the container restarts automatically if it crashes or the host reboots.

### Cloudflare Tunnel (Recommended)

For secure access without exposing ports:

```bash
# Install cloudflared
# Create tunnel
cloudflared tunnel create metatron-console

# Configure tunnel
# ~/.cloudflared/config.yml
tunnel: <tunnel-id>
credentials-file: /path/to/credentials.json

ingress:
  - hostname: console.metatron.phaseshift.studio
    service: http://localhost:7681
  - service: http_status:404
```

## Future Enhancements

1. **Session Management**: Track and limit concurrent sessions
2. **Example Buttons**: Pre-load code examples with one click
3. **Syntax Highlighting**: Add CodeMirror for better editing
4. **Share Links**: Generate URLs with pre-loaded code
5. **Tutorial Mode**: Step-by-step guided examples
6. **Rate Limiting**: Prevent abuse with connection limits

## Resources

- [ttyd Documentation](https://github.com/tsl0922/ttyd)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)

---

**Welcome to the Grid!** 🎮✨
