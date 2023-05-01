#!/usr/bin/env bash
# Get the name of the Kubernetes node with the provided hadoop pod IP
set -x
podIP="${1}" # this will be the IP of a datanode
outfile=$(mktemp /tmp/$(basename $0).XXXX)
trap '{ rm -f -- "$outfile"; }' EXIT
script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source "${script_dir}/apiserver_access.sh"
# Following model described here: https://chengdol.github.io/2019/11/06/k8s-api/
# http_code is the return status code
http_code=$(curl -w  "%{http_code}" -sS --cacert $CACERT -H "Content-Type: application/json" -H "Accept: application/json, */*" -H "Authorization: Bearer $TOKEN" "$APISERVER/api/v1/namespaces/hadoop/pods?fieldSelector=status.podIP%3D$podIP" -o $outfile)
if [[ $http_code -ne 200 ]]; then
    echo "{\"Result\": \"Failure\", \"httpReturnCode\":$http_code}" | jq '.'
    exit 1
fi

# using jq, only return the name of the node containing this pod; jq will return null if no node is found
cat $outfile | jq -r .items[0].spec.nodeName
