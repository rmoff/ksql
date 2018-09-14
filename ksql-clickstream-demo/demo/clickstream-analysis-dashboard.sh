#!/usr/bin/env bash
#curl -X "GET" "http://localhost:3000/api/dashboards/db/click-stream-analysis" \
#        -H "Content-Type: application/json" \
#	     --user admin:admin

export HOST=localhost
export PORT=3000
export ENDPOINT=
echo "Waiting for host to start listening on $HOST ⏳ ";while [ $(curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT) -eq 000 ];do curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT;date;sleep 5;done

echo "Loading Grafana ClickStream Dashboard"

RESP="$(curl -s -X "POST" "http://localhost:3000/api/dashboards/db" \
	    -H "Content-Type: application/json" \
	     --user admin:admin \
	     --data-binary @/scripts/clickstream-analysis-dashboard.json)"

echo $RESP
echo ""
echo ""

if [[ $RESP =~ .*\"url\":\"([^\"]*)\".* ]]
then
    url="${BASH_REMATCH[1]}"
else
    url="/dashboard/db/click-stream-analysis"
fi

echo -e "Navigate to:\n\thttp://localhost:3000${url}\n(Default user: admin / password: admin)"