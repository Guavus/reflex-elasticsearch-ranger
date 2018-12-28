# reflex-elasticsearch-ranger

This repostiory hosts Ranger plugin for Elasticsearch. The plugin supports index level access control policy on Ranger UI. For enabling tag based policy support for Elasticsearch, entities of TYPE 'es_index' in Atlas should be created in Atlas and Ranger should configured to sync with Atlas.

To build plugins:
* Clone github repo, ``git clone https://github.com/Guavus/reflex-elasticsearch-ranger.git``
* To build plugin jars, run ``make all``
* To build rpm, run ``make gather-dist-rpms``

To install plugin:
* Install rpm on Ranger nodes
* Copy jar using command “cp /opt/guavus/ranger-es/lib/ranger-elasticsearch-service-1.0-SNAPSHOT-jar-with-dependencies.jar /usr/hdp/2.6.4.0-91/ranger-admin/ews/webapp/WEB-INF/classes/ranger-plugins/elasticsearch/ranger-elasticsearch-service-1.0-SNAPSHOT-jar-with-dependencies.jar” on Ranger nodes.
* Using ranger REST API, create service definition for ES ``curl -u <admin user>:<admin passwd> -X POST -H "Accept: application/json" -H "Content-Type: application/json" --data @/opt/guavus/ranger-es/conf/ranger-elasticsearch-plugin http://<IP>:6080/service/plugins/definitions``
* Restart Ranger Admin process
* Copy jar for tag sync using command ``cp ranger-tagsync-elasticsearch-1.0-SNAPSHOT.jar /usr/hdp/2.6.4.0-91/ranger-tagsync/lib/`` on Ranger nodes.
* Using Ambari, edit ranger-tagsync-site.xml and add following configs:
```
    <property>
      <name>ranger.tagsync.atlas.custom.resource.mappers</name>
      <value>org.apache.ranger.tagsync.source.atlas.AtlasESResourceMapper</value>
    </property>
```
Make sure tag source 'atlas' is configured.
* Restart ranger tag sync services
* Open Ranger UI and create instance of Elasticsearch service
* Add Ranger policies for ‘kibanaserver’ user as well ‘ranger-es’ user and create policy for admin user.

**NOTE: For enabling security (Authentication and Authorization) in Elasticsearch, please check https://github.com/Guavus/search-guard.**
