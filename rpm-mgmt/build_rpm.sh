set -e

pushd "$(dirname "$0")"

source ../artifactory_mgmt.sh

#TEMP_RPM_DIR=`pwd -P`"/.rpm-dir"
TEMP_PACKAGE_DIR=`pwd -P`"/.package"    #"${TEMP_RPM_DIR}/package"
#COMPONENT_RPM_NAME="sapp-"
RPM_BASE_PATH="/opt/guavus/ranger-es/"
DIST_DIR_PLUGIN="../dist/rangeres"
DIST_DIR_INSTALLER="../dist/installer"

VERSION=$MAJOR_VER
DATE=`date +'%Y%m%d'`

if [ -z "$BUILD_NUMBER" ]
then
      echo "BUILD_NUMBER is not set. setting it 0"
      BUILD_NUMBER=0
fi

 echo "###### START: RPM CREATION FOR RANGER ES PLUGIN ######"
 echo -e "# # # # # # # START : Creating RPM package for [${COMPONENT}] # # # # # # #"
    #cleanup
 rm -rf ${TEMP_PACKAGE_DIR}/*
 mkdir -p ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}
 mkdir -p ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 RPM_NAME="guavus-ranger-es-plugin"
 cp -r ../ranger-elasticsearch-service/conf ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}
 cp -r ../ranger-elasticsearch-service/target/*.jar ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 cp -r ../ranger-tagsync-elasticsearch/target/*.jar ${TEMP_PACKAGE_DIR}/${RPM_BASE_PATH}/lib
 fpm -f -s dir -t rpm --rpm-os linux -v ${VERSION} --iteration ${DATE}_${BUILD_NUMBER} --chdir $TEMP_PACKAGE_DIR -p $DIST_DIR_PLUGIN -n $RPM_NAME .
 echo "###### END: RPM CREATION FOR RANGER ES PLUGIN ######"

 rm -rf ${TEMP_PACKAGE_DIR}

popd > /dev/null
