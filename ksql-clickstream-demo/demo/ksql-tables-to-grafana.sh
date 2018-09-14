#!/usr/bin/env bash

export HOST=localhost
export PORT=8083
export ENDPOINT=connectors
echo "Waiting for host to start listening on $HOST ⏳ ";while [ $(curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT) -eq 000 ];do curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT;date;sleep 5;done;nc -vz $HOST $PORT

export HOST=elasticsearch
export PORT=9200
export ENDPOINT=
echo "Waiting for host to start listening on $HOST ⏳ ";while [ $(curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT) -eq 000 ];do curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT;date;sleep 5;done;nc -vz $HOST $PORT

echo "Loading Clickstream-Demo TABLES to Confluent-Connect => Elastic => Grafana datasource"

declare -a tables=('click_user_sessions' 'user_ip_activity' 'enriched_error_codes_count' 'errors_per_min_alert' 'errors_per_min' 'events_per_min' 'pages_per_min');
for i in "${tables[@]}"
do



    table_name=$i
    TABLE_NAME=`echo $table_name | tr '[a-z]' '[A-Z]'`

    echo "==================================================================" 
    echo "Charting " $TABLE_NAME  

    ## Cleanup existing data

    echo 
    echo "Remove any existing Elastic search config"  
    curl -X "DELETE" "http://elasticsearch:9200/""$table_name"  2>&1

    echo 
    echo "Remove any existing Connect config"  
    curl -X "DELETE" "http://localhost:8083/connectors/es_sink_""$TABLE_NAME"  2>&1

    echo 
    echo "Remove any existing Grafana config"  
    curl -X "DELETE" "http://grafana:3000/api/datasources/name/""$table_name"   --user admin:admin  2>&1

    # Wire in the new connection path
    echo 
    echo "Connecting KSQL->Elastic->Grafana " "$table_name"  2>&1
    /scripts/ksql-connect-es-grafana.sh "$table_name"  2>&1
done

echo "Done"

# ========================
#   REST API Notes
# ========================
#
# Extract datasources from grafana
# curl -s "http://localhost:3000/api/datasources"  -u admin:admin|jq -c -M '.[]'
#
# Delete a Grafana DataSource
# curl -X "DELETE" "http://localhost:3000/api/datasources/name/

# List confluent connectors
# curl -X "GET" "http://localhost:8083/connectors"
#
# Delete a Confluent-Connector
# curl -X "DELETE" "http://localhost:8083/connectors/es_sink_PER_USER_KBYTES_TS"
#
# Delete an Elastic Index
# curl -X "DELETE" "http://localhost:9200/per_user_kbytes_ts"
#
