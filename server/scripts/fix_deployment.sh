#!/bin/bash

# This script fixes the "KeyError: 'ContainerConfig'" error by forcibly removing 
# the containers that the old docker-compose version is choking on.

echo "🔍 Finding and removing stuck containers..."

# Remove containers by likely names
docker ps -a --filter "name=server_app" -q | xargs -r docker rm -f
docker ps -a --filter "name=server_worker" -q | xargs -r docker rm -f
docker ps -a --filter "name=deutschstart" -q | xargs -r docker rm -f

# Specific IDs from your error log just in case
docker rm -f 4167093c99fd_deutschstart-server_app_1 2>/dev/null
docker rm -f d2920a537b96_deutschstart-server_worker_1 2>/dev/null

echo "✅ Cleanup complete."
echo "🚀 Now try running: docker-compose up -d --build"
echo "   OR if available: docker compose up -d --build"
