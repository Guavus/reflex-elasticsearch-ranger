# Note for editing Makefile : Makefile requires Tab to identify commands


SHELL := /bin/bash
REL_VERSION := $(shell cat .VERSION)


all: \
	clean \
	gather-dist-source-jobs 
		
gather-dist-rpms:
	VER=`cat .VERSION`
	rm -rf .package;  ./build_rpm.sh $(REL_VERSION) 0;  

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
	cd ranger-elasticsearch-service; mvn clean install -DskipTests; cd ..
	cd ranger-tagsync-elasticsearch; mvn clean install -DskipTests; cd ..
	cd ranger-privileges-evaluator; mvn clean install -DskipTests; cd ..
	@echo "= = = = = = = = > END TARGET : [build-source] < = = = = = = ="

dist:
	mkdir -p dist/rangeres
	mkdir -p dist/installer


.PHONY: all gather-dist-source-jobs clean build-source dist
