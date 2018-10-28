#!/bin/bash

pushd $(dirname $0) > /dev/null
basepath=$(pwd)
popd > /dev/null

cat << EndOfMessage
HELP: 
./operationserver.sh capacity malicious_result_rate
	- capacity: (REQUIRED) Nombre d'operations pour lequel la tache est garantie.
	- malicious_result_rate: (REQUIRED) Taux de reponses erronees (0: toujours de bons resultats, 100: toujours de faux resultats)

EndOfMessage

java -cp "$basepath"/operationserver.jar:"$basepath"/shared.jar \
  -Djava.rmi.server.codebase=file:"$basepath"/shared.jar \
  -Djava.security.policy="$basepath"/policy \
  -Djava.rmi.server.hostname="127.0.0.1" \
  operationserver.OperationServer $*
