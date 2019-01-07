#!/usr/bin/env groovy
@Library('jenkins_lib')_
pipeline {
 agent any
 stages {
 stage("Define Release version"){
     steps {
     script {
       versionDefine()
       }
     }
   }
    stage("Build") {
     steps {
       echo "Building..."
       sh "make all"
     }
   }
   stage("Build rpm") {
     steps {
       echo "Building..."
       sh "./build_rpm.sh ${VERSION} ${RELEASE}"
     }
   }

   stage("RPM PUSH"){
   steps{
   script{
         rpm_push( env.buildType, 'dist', 'ggn-dev-rpms/ranger-es' )
   }}}
}
 post {
    always {
      reports_alerts('target/checkstyle-result.xml', 'target/surefire-reports/*.xml', 'target/site/cobertura/coverage.xml', 'allure-report/', 'index.html')
      slackalert('jenkins-misc-alerts')
}
  }
}
