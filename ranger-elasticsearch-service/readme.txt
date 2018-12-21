Helpful commands:

To retrieve service details:
curl -u admin:admin -X GET http://192.168.154.194:6080/service/public/v2/api/servicedef/name/<service name>

To update service definition:
curl -u admin:admin -X PUT -H "Accept: application/json" -H "Content-Type: application/json" --data @/root/ranger-elasticsearch-plugin http://192.168.154.194:6080/service/public/v2/api/servicedef/name/elasticsearch

To delete service definition:
curl -u admin:admin -X DELETE http://192.168.154.194:6080/service/public/v2/api/servicedef/name/<service name>
