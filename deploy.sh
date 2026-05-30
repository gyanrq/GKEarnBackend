#!/bin/bash
# =============================================
# EarnX-3 Production Deploy Script
# Ubuntu 22.04/24.04 — No Docker
# =============================================
set -e

echo "======================================"
echo "  EarnX-3 Deploying..."
echo "======================================"

# STEP 1: Swap
if [ ! -f /swapfile ]; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
    echo "✅ Swap ready"
fi

# STEP 2: Java 17
if ! java -version 2>&1 | grep -q "17"; then
    sudo apt update -q && sudo apt install -y openjdk-17-jre-headless
fi

# STEP 3: Load env
if [ ! -f /home/ubuntu/.env ]; then
    echo "❌ /home/ubuntu/.env missing!"
    exit 1
fi
set -a; source /home/ubuntu/.env; set +a
echo "✅ Env loaded"

# STEP 4: JAR check
JAR="/home/ubuntu/earnx/app.jar"
[ ! -f "$JAR" ] && echo "❌ $JAR missing!" && exit 1

# STEP 5: Stop old
pkill -f "app.jar" || true
sleep 3

# STEP 6: Start
mkdir -p /home/ubuntu/earnx/logs
nohup java \
    -Xms64m \
    -Xmx320m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:MetaspaceSize=64m \
    -XX:MaxMetaspaceSize=128m \
    -Dspring.profiles.active=prod \
    -jar "$JAR" \
    > /home/ubuntu/earnx/logs/app.log 2>&1 &

echo "✅ Started (PID: $!)"
echo $! > /home/ubuntu/earnx/app.pid

sleep 40
if curl -s http://localhost:8080/actuator/health | grep -q "UP\|DOWN"; then
    echo "======================================"
    echo "  🎉 DEPLOYED! https://gkearn.online"
    echo "  Logs: tail -f /home/ubuntu/earnx/logs/app.log"
    echo "======================================"
else
    echo "⚠️  Check logs: tail -f /home/ubuntu/earnx/logs/app.log"
fi
