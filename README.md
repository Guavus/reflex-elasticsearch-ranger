# reflex-elasticsearch-ranger

This repostiory hosts Ranger plugin for Elasticsearch. The plugin supports index level access control policy on Ranger UI. For enabling tag based policy support for Elasticsearch, entities of TYPE 'es_index' in Atlas should be created in Atlas and Ranger should configured to sync with Atlas.

To build plugin:
1. 'cd ranger-elasticsearch-service/'
2. 'mvn clean install'
3. 'cd ../ranger-tagsync-elasticsearch/'
4. 'mvn clean install'

To install plugin:
1. Copy jar “cp ranger-elasticsearch-service-1.0-SNAPSHOT-jar-with-dependencies.jar /usr/hdp/2.6.4.0-91/ranger-admin/ews/webapp/WEB-INF/classes/ranger-plugins/elasticsearch/ranger-elasticsearch-service-1.0-SNAPSHOT-jar-with-dependencies.jar”
2. Using ranger REST API, create service definition for ES “curl -u admin:admin -X PUT -H "Accept: application/json" -H "Content-Type: application/json" --data @/root/ranger-elasticsearch-plugin http://192.168.154.194:6080/service/public/v2/api/servicedef/name/elasticsearch”
3. Restart Ranger Admin process
4. Copy jar for tag sync “cp ranger-tagsync-elasticsearch-1.0-SNAPSHOT.jar /usr/hdp/2.6.4.0-91/ranger-tagsync/lib/“
5. Edit ranger-tagsync-site.xml and add appropriate configs
6. Restart ranger tag sync services
7. Open Ranger UI and create instance of Elasticsearch service
8. Add Ranger policies for ‘kibanaserver’ user as well ‘ranger-es’ user and create policy for admin user.

NOTE: For enabling security (Authentication and Authorization) in Elasticsearch, please check https://github.com/Guavus/search-guard.
