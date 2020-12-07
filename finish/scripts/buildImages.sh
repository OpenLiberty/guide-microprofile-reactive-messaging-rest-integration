#!/bin/bash

echo Pull kafka images
docker pull bitnami/zookeeper:3
docker pull bitnami/kafka:2

echo Building images
docker build -t system:1.0-SNAPSHOT system/. &
docker build -t inventory:1.0-SNAPSHOT inventory/. &

wait
echo Images building completed
