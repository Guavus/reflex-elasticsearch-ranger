set -e

pushd "$(dirname "$0")"

TEMP_PACKAGE_DIR=`pwd -P`"/.package"    #"${TEMP_RPM_DIR}/package"
RANGER_ES_SERVICE_RPM_BASE_PATH="/opt/guavus/ranger-es-service/"
RANGER_ES_SERVICED_DIST_DIR_PLUGIN="./dist/ranger-es-service"
RANGER_ES_SERVICE_RPM_NAME="guavus-ranger-es-plugin"

RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH="/opt/guavus/ranger-privilege-evaluator/"
RANGER_PRIVILEGE_EVALUATOR_DIST_DIR_PLUGIN="./dist/ranger-privilege-evaluator"
RANGER_PRIVILEGE_EVALUATOR_RPM_NAME="guavus-ranger-privilege-evaluator"

VERSION=$1
REL=$2

if [ -z "$BUILD_NUMBER" ]
then
      echo "BUILD_NUMBER is not set. setting it 0"
      BUILD_NUMBER=0
fi

 echo "###### START: RPM CREATION FOR RANGER ES PLUGIN ######"
 echo -e "# # # # # # # START : Creating RPM package for ranger plugin # # # # # # #"
 #cleanup
 rm -rf ${TEMP_PACKAGE_DIR}/*
 mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_ES_SERVICE_RPM_BASE_PATH}
 mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_ES_SERVICE_RPM_BASE_PATH}/lib

 mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}
 mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}/lib

 cp -r ./ranger-elasticsearch-service/conf ${TEMP_PACKAGE_DIR}/${RANGER_ES_SERVICE_RPM_BASE_PATH}
 cp -r ./ranger-elasticsearch-service/target/*.jar ${TEMP_PACKAGE_DIR}/${RANGER_ES_SERVICE_RPM_BASE_PATH}/lib
 cp -r ./ranger-tagsync-elasticsearch/target/*.jar ${TEMP_PACKAGE_DIR}/${RANGER_ES_SERVICE_RPM_BASE_PATH}/lib
 cp -r ./ranger-privileges-evaluator/target/*.jar ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}/lib

 fpm -f -s dir -t rpm --rpm-os linux -v ${VERSION} --iteration ${REL} --chdir $TEMP_PACKAGE_DIR -p $RANGER_ES_SERVICED_DIST_DIR_PLUGIN -n $RANGER_ES_SERVICE_RPM_NAME .

 echo "###### END: RPM CREATION FOR RANGER ES PLUGIN ######"

 echo -e "# # # # # # # START : Creating RPM package for ranger privilege evaluator # # # # # # #"
#  mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}
#  mkdir -p ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}/lib
#  cp -r ./ranger-privileges-evaluator/target/*.jar ${TEMP_PACKAGE_DIR}/${RANGER_PRIVILEGE_EVALUATOR_RPM_BASE_PATH}/lib

 fpm -f -s dir -t rpm --rpm-os linux -v ${VERSION} --iteration ${REL} --chdir $TEMP_PACKAGE_DIR -p $RANGER_PRIVILEGE_EVALUATOR_DIST_DIR_PLUGIN -n $RANGER_PRIVILEGE_EVALUATOR_RPM_NAME .
 echo "###### END: RPM CREATION FOR RANGER PRIVILEGE EVALUATOR ######"

 rm -rf ${TEMP_PACKAGE_DIR}

popd > /dev/null
