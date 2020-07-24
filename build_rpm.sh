set -e

pushd "$(dirname "$0")"

TEMP_PACKAGE_DIR=`pwd -P`"/.package"    #"${TEMP_RPM_DIR}/package"
RPM_BASE_PATH="/opt/guavus/ranger-es/"
DIST_DIR_PLUGIN="./dist/rangeres"
DIST_DIR_INSTALLER="./dist/installer"
RPM_NAME="guavus-ranger-es-plugin"

VERSION=$1
REL=$2

if [ -z "$BUILD_NUMBER" ]
then
      echo "BUILD_NUMBER is not set. setting it 0"
      BUILD_NUMBER=0
fi

 echo "###### START: RPM CREATION FOR RANGER ES PLUGIN ######"
 echo -e "# # # # # # # START : Creating RPM package # # # # # # #"
    #cleanup
 rm -rf ${TEMP_PACKAGE_DIR}/*
 mkdir -p ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}
 mkdir -p ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 cp -r ./ranger-elasticsearch-service/conf ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}
 cp -r ./ranger-elasticsearch-service/target/*.jar ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 cp -r ./ranger-tagsync-elasticsearch/target/*.jar ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 cp -r ./ranger-privileges-evaluator/target/*.jar ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 fpm -f -s dir -t rpm --rpm-os linux -v ${VERSION} --iteration ${REL} --chdir $TEMP_PACKAGE_DIR -p $DIST_DIR_PLUGIN -n $RPM_NAME .
 echo "###### END: RPM CREATION FOR RANGER ES PLUGIN ######"

 rm -rf ${TEMP_PACKAGE_DIR}

popd > /dev/null
