#!/usr/bin/env bash

export HOST=localhost
export PORT=9200
export ENDPOINT=
echo "Waiting for host to start listening on $HOST ⏳ ";while [ $(curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT) -eq 000 ];do curl -s -o /dev/null -w "%{http_code}" http://$HOST:$PORT/$ENDPOINT;date;sleep 5;done;nc -vz $HOST $PORT

echo -e "\n-> Removing kafkaconnect index (if it doesn't exist, this just throws 404)\n"

curl -XDELETE "http://localhost:9200/_template/kafkaconnect/"

echo -e "\n\n-> Loading Elastic Dynamic Template to ensure _TS fields are used for TimeStamp\n\n"

curl -XPUT "http://localhost:9200/_template/kafkaconnect/" -H 'Content-Type: application/json' -d'
{
  "template": "*",
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "mappings": {
    "_default_": {
      "dynamic_templates": [
        {
          "dates": {
            "match": "EVENT_TS",
            "mapping": {
              "type": "date"
            }
          }
        },
        {
          "non_analysed_string_template": {
            "match": "*",
            "match_mapping_type": "string",
            "mapping": {
              "type": "keyword"
            }
          }}
      ]
    }
  }
}'