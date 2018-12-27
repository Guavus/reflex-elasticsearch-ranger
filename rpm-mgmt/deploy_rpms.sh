#!/usr/bin/env bash
# Pushes the generated rpms to artifactory
#
# Usage:
#     sh deploy_rpms.sh
#
#	  rpm must have been generated first. Run make publish-rpms in cdap-plugins directory
set -e

pushd "$(dirname "$0")"

source ../artifactory_mgmt.sh

print_lines(){
    for i in `seq 1 4`;
    do
        echo " # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # #"
    done
}

# rpms should only be pushed from build agents where we configured PUSH_TO_ARTIFACTORY=1
if [[ ${PUSH_RPM_TO_ARTIFACTORY} == '1' ]]; then
    echo 
    echo "Pushing rpm for RANGER ES PLUGIN to artifactory"
    curl --user $ARTIFACTORY_USERNAME:$ARTIFACTORY_PASSWORD -X PUT "$RPM_ARTIFACTORY/$BUILD_NUMBER/" -T ../dist/rangeres/*.rpm
    echo "Published RPM for RANGER ES PLUGIN successfully"

else
    print_lines
    echo
    echo "WARNING:"
    echo "    Not pushing rpms to artifactory. To do it:"
    echo "        export PUSH_RPM_TO_ARTIFACTORY=1"
    echo
    print_lines
fi

popd
