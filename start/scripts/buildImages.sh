#!/bin/bash

echo Pull kafka images
docker pull -q bitnami/kafka:latest

echo Building images
docker build -t system:1.0-SNAPSHOT system/. &
docker build -t inventory:1.0-SNAPSHOT inventory/. &

wait
echo Images building completed
