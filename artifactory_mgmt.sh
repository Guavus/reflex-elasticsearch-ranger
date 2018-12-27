#!/bin/bash
export ARTIFACTORY_USERNAME=dev-deployer
export ARTIFACTORY_PASSWORD=dev@guavus
export PUSH_RPM_TO_ARTIFACTORY=0
export PUSH_TAR_TO_ARTIFACTORY=0
export MAJOR_VER="1.0"
export RPM_ARTIFACTORY=artifacts.ggn.in.guavus.com:/ggn-dev-rpms/ranger-es/$MAJOR_VER/dev/
