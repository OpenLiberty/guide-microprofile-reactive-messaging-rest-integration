#!/bin/bash
set -euxo pipefail

##############################################################################
##
##  Travis CI test script
##
##############################################################################

./scripts/packageApps.sh

mvn -pl system verify
mvn -pl inventory verify

./scripts/buildImages.sh
./scripts/startContainers.sh

sleep 180

docker logs system

docker logs inventory

systmeCPULoad="$(curl --write-out "%{http_code}" --silent --output /dev/null "http://localhost:9085/inventory/systems")"

if [ "$systmeCPULoad" == "200" ]
then
  echo SystemInventory OK
  
  inventoryStatus="$(docker exec -it inventory curl --write-out "%{http_code}" --silent --output /dev/null "http://localhost:9085/inventory/systems")"
  systemStatus="$(docker exec -it system curl --write-out "%{http_code}" --silent --output /dev/null "http://system:9083/health/ready")"

  if [ "$inventoryStatus" == "200" ] && [ "$systemStatus" == "200" ]
  then
    echo ENDPOINT OK
  else
    echo inventory status:
    echo "$inventoryStatus"
    echo system status:
    echo "$systemStatus"
    echo ENDPOINT
    exit 1
  fi
else
  echo System Inventory status:
  echo "$systmeCPULoad"
  echo ENDPOINT
  exit 1
fi

./scripts/stopContainers.sh
