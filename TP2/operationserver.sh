#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

cat << EndOfMessage
HELP: 
./operationserver.sh ip_address capacity malicious_result_rate
	- ip_address: (REQUIRED) Addresse ip du serveur.
	- port: (REQUIRED) Port du serveur.
	- capacity: (REQUIRED) Nombre d'operations pour lequel la tache est garantie.
	- malicious_result_rate: (REQUIRED) Taux de reponses erronees (0: toujours de bons resultats, 100: toujours de faux resultats)

IPADDR=$1

EndOfMessage

java -cp "$basepath"/operationserver.jar:"$basepath"/shared.jar \
  -Djava.rmi.server.codebase=file:"$basepath"/shared.jar \
  -Djava.security.policy="$basepath"/policy \
  -Djava.rmi.server.hostname="$IPADDR" \
  operationserver.OperationServer $*
