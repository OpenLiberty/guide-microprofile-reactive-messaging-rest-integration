@ECHO OFF
set KAFKA_SERVER=kafka:9092
set NETWORK=reactive-app

docker network create %NETWORK%

docker run -d ^
    -e ALLOW_PLAINTEXT_LISTENER=yes ^
    -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 ^
    -e KAFKA_CFG_NODE_ID=0 ^
    -e KAFKA_CFG_PROCESS_ROLES=controller,broker ^
    -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 ^
    -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka:9093 ^
    -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER ^
    --hostname=kafka ^
    --network=%NETWORK% ^
    --name=kafka ^
    --rm ^
    bitnami/kafka:latest

start /b docker run -d ^
  -e MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS=%KAFKA_SERVER% ^
  -e WLP_LOGGING_CONSOLE_LOGLEVEL=info ^
  -p 9083:9083 ^
  --network=%NETWORK% ^
  --name=system ^
  --rm ^
  system:1.0-SNAPSHOT

start /b docker run -d ^
  -e MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS=%KAFKA_SERVER% ^
  -e WLP_LOGGING_CONSOLE_LOGLEVEL=info ^
  -p 9085:9085 ^
  --network=%NETWORK% ^
  --name=inventory ^
  --rm ^
  inventory:1.0-SNAPSHOT 
