# Note for editing Makefile : Makefile requires Tab to identify commands


SHELL := /bin/bash
RANGER_ES_TAG_SYNC_RPM_VERSION := $(shell cat image-config/RANGER_ES_TAG_SYNC_RPM_VERSION)
RANGER_PRIVILEGE_EVALUATOR_RPM_VERSION := $(shell cat image-config/RANGER_PRIVILEGE_EVALUATOR_RPM_VERSION)

all: \
	clean \
	gather-dist-source-jobs 
		
gather-dist-rpms:
	rm -rf .package; ./build_rpm.sh $(RANGER_ES_TAG_SYNC_RPM_VERSION) ${RANGER_PRIVILEGE_EVALUATOR_RPM_VERSION} ${RELEASE};  

gather-dist-source-jobs: \
	build-source \
	dist


clean:
	@echo "= = = = = = = > START TARGET : [clean] < = = = = = = ="
	rm -rf dist
	rm -rf ranger-elasticsearch-service/target
	rm -rf ranger-tagsync-elasticsearch/target
	rm -rf ranger-privileges-evaluator/target
	@echo "= = = = = = = = > END TARGET : [clean] < = = = = = = ="


build-source:
	@echo "= = = = = = = > START TARGET : [build-source] < = = = = = = ="
	# installs commons locally, required to build subprojects
	cd ranger-elasticsearch-service; mvn clean install -U -DskipTests; cd ..
	cd ranger-tagsync-elasticsearch; mvn clean install -U -DskipTests; cd ..
	cd ranger-privileges-evaluator; mvn clean install -U -DskipTests; cd ..
	@echo "= = = = = = = = > END TARGET : [build-source] < = = = = = = ="

dist:
	mkdir -p dist/ranger-es-service
	mkdir -p dist/ranger-privilege-evaluator


.PHONY: all gather-dist-source-jobs clean build-source dist
