#!/bin/bash
set -euo pipefail

VPS_HOST="vps"
REMOTE_DIR="/opt/call-transcriber"

echo "=== Deploy Call Transcriber ==="

echo "1/4 Syncing code..."
rsync -avz --exclude='.git' --exclude='node_modules' --exclude='__pycache__' \
  --exclude='.next' --exclude='android' --exclude='.env' \
  /mnt/volume/Projects/call-transcriber/ ${VPS_HOST}:${REMOTE_DIR}/

echo "2/4 Building containers..."
ssh ${VPS_HOST} "cd ${REMOTE_DIR} && docker compose build"

echo "3/4 Starting containers..."
ssh ${VPS_HOST} "cd ${REMOTE_DIR} && docker compose up -d"

echo "4/4 Checking health..."
sleep 5
ssh ${VPS_HOST} "docker compose -f ${REMOTE_DIR}/docker-compose.yml ps"

echo "=== Deploy complete ==="
echo "API: https://transcriber-api.aicraftors.com/health"
echo "Web: https://transcriber.aicraftors.com"
