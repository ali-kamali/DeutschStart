# Deploying DeutschStart Server to Linux

## 🌐 Server Deployment

Your server is fully Dockerized, making deployment to any Linux server (VPS, dedicated, or cloud instance) extremely simple.

### Prerequisites

1.  **Linux Server** (Ubuntu/Debian recommended)
2.  **Docker & Docker Compose** installed:
    ```bash
    # Install Docker
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    
    # Install Docker Compose (if not included)
    sudo apt-get install docker-compose-plugin
    ```

### Deployment Steps

1.  **Transfer Files**: Copy the entire `server/` directory from your PC to the Linux server.
    ```bash
    # Example using SCP (run from your local machine)
    scp -r e:\Health\Germany\server username@your_server_ip:/opt/deutschstart-server
    ```

2.  **Start Services**:
    ```bash
    cd /opt/deutschstart-server
    
    # Build and start in detached mode
    docker-compose up -d --build
    ```

3.  **Verify Running**:
    ```bash
    docker-compose ps
    # Check logs if needed
    docker-compose logs -f app
    ```

4.  **Regenerate Content Pack**:
    Since you're on a new machine, you need to generate the content pack (and audio) again. This will take ~10-15 minutes.
    ```bash
    # Trigger generation
    docker exec -d server-app-1 python /app/scripts/merge_import_generate.py
    ```
    *Note: The `-d` flag runs it in the background.*

---

## 📱 Android Client Configuration

Once your server is running, you need to update the Android app to point to your new server instead of `localhost`.

1.  **Open Project**: `client/android`
2.  **Edit File**: `app/src/main/java/com/deutschstart/app/di/NetworkModule.kt`
3.  **Update `BASE_URL`**:

    ```kotlin
    object NetworkModule {
    
        // Update this to your server's IP or Domain
        // private const val BASE_URL = "http://10.0.2.2:8000/" // Old Localhost
        private const val BASE_URL = "http://YOUR_SERVER_IP:8000/" 
        
        // ...
    }
    ```

4.  **Rebuild App**: Build and install the APK on your device.

---

## 🔒 Security Best Practices (Production)

For a public server, you should secure the API:

1.  **Process Manager**: Use Docker restart policy (already in `docker-compose.yml` as `restart: always`).
2.  **Firewall**: Ensure port `8000` is open (or use a reverse proxy).
3.  **Reverse Proxy (Recommended)**:
    Set up **Nginx** or **Caddy** to handle SSL (HTTPS) and forward requests to port 8000.
    
    **Example Caddyfile:**
    ```
    your-domain.com {
        reverse_proxy localhost:8000
    }
    ```

4.  **Environment Variables**: Create a `.env` file in the server directory to store secrets instead of hardcoding them.

---

## 🛠 Troubleshooting

### "KeyError: 'ContainerConfig'" Error
If you see an error like `KeyError: 'ContainerConfig'` when running `docker-compose up`, it means the existing containers are in a state that your version of Docker Compose cannot understand.

**Fix 1: Use the Fix Script**
We have included a script to fix this automatically. Run this on your server:
```bash
# Make it executable
chmod +x scripts/fix_deployment.sh

# Run it
./scripts/fix_deployment.sh
```

**Fix 2: Try Docker Compose V2**
The error comes from the old `docker-compose` (v1). Check if your server has the new version (v2) installed which uses a space instead of a dash:
```bash
# Try using 'docker compose' (with a space)
docker compose up -d --build
```

**Fix 3: Manual Cleanup**
If the script doesn't work, manually remove all containers:
```bash
docker rm -f $(docker ps -a -q)
```
Then restart.
