podTemplate(
  cloud: 'openshift',
  label: 'dind', 
  serviceAccount: 'jenkins',
  podRetention: onFailure(),
  idleMinutes: '5',
  ///////// Usa template DIND (con oc cli y aws cli) de registry de OS 
  containers: [
    containerTemplate(
      name: 'docker',
      image: 'default-route-openshift-image-registry.apps.ocp02-noprod.pro.edenor/jenkins-dind-test/dind:oc412',
      privileged: true,
      ttyEnabled: true
    ),
  ],
  volumes: [
    emptyDirVolume(
      memory: false,
      mountPath: '/var/lib/containers'
    )
  ]
) // fin podTemplate

{ if("$entorno" == "PROD"){
  node('dind') {
    withCredentials([
      // AWS credentials from secret files
      string(credentialsId: 'secret-AWS_ACCOUNT_ID', variable: 'AWS_ACCOUNT_ID'),
      string(credentialsId: 'secret-AWS_DEFAULT_REGION', variable: 'AWS_DEFAULT_REGION'),
      string(credentialsId: 'secret-AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
      string(credentialsId: 'secret-AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
      string(credentialsId: 'secret-jenkins-sa-noprod', variable: 'OCP_SECRET_SA_TOKEN'),
      //string(credentialsId: 'secret-token-sa-prod', variable: 'OCP_PROD_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-token-sa-prod-new', variable:'OCP_PROD_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-jenkins-sa-prod', variable:'OCP_PROD_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-OCP_REGISTRY_URI', variable: 'OCP_NOPROD_REGISTRY_URI'),
      string(credentialsId: 'secret-OCP_PROD_REGISTRY_URI', variable: 'OCP_PROD_REGISTRY_URI'),
      // agregar como parametro API y Registry
      string(credentialsId: 'secret-OCP_API_URI', variable: 'OCP_API_URI'),
      string(credentialsId: 'secret-OCP_PROD_API_URI', variable: 'OCP_PROD_API_URI'),
      string(credentialsId: 'secret-JIRA_TOKEN', variable: 'JIRA_TOKEN'),
      string(credentialsId: 'SECRET-JIRA_URL', variable: 'JIRA_URL')
    ])
    {
    parameters{
           string defaulValue: 'alpine', description: 'Nombre de imagen Docker', name: 'appName'
           string defaultValue: 'latest', description: 'Version de imagen Docker', version: 'appTag'
           string defaultValue: 'jenkins-dind', description: 'Project Name', project: 'namespaceName'
           string defaulValue: '', description: 'Id de Ticket de Jira', name: 'idTicket'
        }
        
    stage('Check TicketID in Jira') {
        container('docker') {
        def url = "$JIRA_URL"+"$idTicket" 
        def status = sh(script: "curl -sLI -w '%{http_code}' $url -H 'Authorization: Bearer $JIRA_TOKEN' -o /dev/null", returnStdout: true)
        
        if (status != "200" && status != "201") {
            error("El ticket de Jira no existe, verificar. Status code: $status en la URL: $url")
        }
        else
        {
            echo 'El ticket de Jira existe en los registros, continuando despliegue'
        }
        }
      }    
      
      stage('Pull image from OCP4 NO PROD'){
        container('docker') {
        sh """echo 'Verifico permisos y me logeo al registro de OCP4 No-Prod'"""
        sh """oc login --token=${OCP_SECRET_SA_TOKEN} --server=${OCP_API_URI} --insecure-skip-tls-verify=true"""
        sh """oc registry login --skip-check"""
        sh """podman login --tls-verify=false -u unused -p ${OCP_SECRET_SA_TOKEN} ${OCP_NOPROD_REGISTRY_URI}"""
        sh """oc whoami"""

        sh """echo 'Pull de imagen existente en NoProd:'"""
        sh """podman pull --tls-verify=false ${OCP_NOPROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag}"""
        
        sh """echo 'tag de imagen:'"""
        sh """podman tag ${OCP_NOPROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag} ${OCP_PROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag}"""
        //sh """podman tag ${OCP_NOPROD_REGISTRY_URI}/jenkins-dind-test/${appName}:${appTag} ${OCP_PROD_REGISTRY_URI}/jenkins-dind-test/${appName}:${appTag}"""
        sh """echo 'Login en Prod:'"""
        sh """oc login --token=${OCP_PROD_SECRET_SA_TOKEN} --server=${OCP_PROD_API_URI} --insecure-skip-tls-verify=true"""
        sh """echo 'Verifico permisos y me logeo al registro de OCP4 Prod'"""
        sh """echo '-------Logueado en PROD----------WHOAMI:'"""
        sh 'oc whoami'
        
        sh """echo 'Login en Registry PROD'"""
        sh """oc registry login --registry='${OCP_PROD_REGISTRY_URI}' --skip-check"""
        sh """echo '-------Logueado en registry PROD----------'"""
        sh 'oc whoami'
        
        sh """echo 'Push de imagen a Prod:'"""
        sh """podman push --tls-verify=false ${OCP_NOPROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag} ${OCP_PROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag}"""    
        sh """oc get is"""
     
        }
      }
      
      stage('Clone git repository') {
       withCredentials([
            gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')
           ]) {   
            sh 'git config --global http.sslVerify false'
            git url: 'https://github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium', branch: 'test', credentialsId: 'SECRET_GIT_EDENOR'
            sh 'ls -lFha'     
        }
      }
        
        stage('Replace version in template and apply in OCP') {
        container('docker') {
        sh 'echo Stage: Replace version in template and apply in OCP'
        sh "echo Exporta y asigna la env al manifiesto"
        sh """export appName="${params.appName}" && export namespaceName="${params.namespaceName}" && export appTag="${params.appTag}" && /usr/bin/envsubst < manifiesto.template.yaml | oc apply -f -"""
        sh """export appName="${params.appName}" && export namespaceName="${params.namespaceName}" && export appTag="${params.appTag}" && /usr/bin/envsubst < manifiesto.template.yaml | cat - > Logs/${entorno}-manifiesto-${env.BUILD_NUMBER}.yaml"""
          
        } 
        }
    }
        
        stage('Update GIT') {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            withCredentials([gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')]) {
                    sh 'ls -lFha'
                    sh "git config user.email SVC_Jenkins_OShift@github.pro.edenor"
                    sh "git config user.name SVC_Jenkins_OShift"
                    sh "git add Logs/${entorno}-manifiesto-${env.BUILD_NUMBER}.yaml"
                    sh "git commit -m 'Build: ${env.BUILD_NUMBER}'"
                    sh "git push https://SVC_Jenkins_OShift:9d6f4c3f1fda0fe32ac48ff5db4820e61c64eba5@github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium.git"
            }
        } 
        } // End Parameters
        } // End Node

    
    node('master'){
        withCredentials([
      // AWS credentials from secret files
      string(credentialsId: 'secret-AWS_ACCOUNT_ID', variable: 'AWS_ACCOUNT_ID'),
      string(credentialsId: 'secret-AWS_DEFAULT_REGION', variable: 'AWS_DEFAULT_REGION'),
      string(credentialsId: 'secret-AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
      string(credentialsId: 'secret-AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
      string(credentialsId: 'secret-jenkins-sa-noprod', variable: 'OCP_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-OCP_REGISTRY_URI', variable: 'OCP_NOPROD_REGISTRY_URI'),
      string(credentialsId: 'secret-OCP_PROD_REGISTRY_URI', variable: 'OCP_PROD_REGISTRY_URI'),
      // agregar como parametro API y Registry
      string(credentialsId: 'secret-OCP_API_URI', variable: 'OCP_API_URI'),
      string(credentialsId: 'secret-OCP_PROD_API_URI', variable: 'OCP_PROD_API_URI'),
      string(credentialsId: 'secret-JIRA_TOKEN', variable: 'JIRA_TOKEN'),
      string(credentialsId: 'SECRET-JIRA_URL', variable: 'JIRA_URL')
    ])
    {
    parameters{
           string defaulValue: 'alpine', description: 'Nombre de imagen Docker', name: 'appName'
           string defaultValue: 'latest', description: 'Version de imagen Docker', version: 'appTag'
           string defaultValue: 'jenkins-dind', description: 'Project Name', project: 'namespaceName'
           string defaulValue: '', description: 'Id de Ticket de Jira', name: 'idTicket'
        }
    stage('Save log File')
        {

         String filter = """s/\\x1b\\[8m.*?\\x1b\\[0m//g;"""
         sh """perl -pe '$filter' <  /var/lib/jenkins/jobs/${JOB_NAME}/builds/${BUILD_NUMBER}/log  > /tmp/log-${JOB_NAME}-${BUILD_NUMBER}.txt"""
            
        } // End Stage
        stage('Attach log in Jira')
        {
        def url = "$JIRA_URL"+"$idTicket"
        sh 'echo ${JIRA_URL}'
        sh 'echo ${idTicket}'
        sh '''curl -D- -H "Authorization: Bearer $JIRA_TOKEN" -X POST -H "X-Atlassian-Token: nocheck" -F "file=@/tmp/log-${JOB_NAME}-${BUILD_NUMBER}.txt" https://jira.edenor.com/rest/api/2/issue/${idTicket}/attachments'''
        
        } // End Stage
    } // End WithCred 
    } //End Node 
    } // End IF PROD
    
    if("$entorno" == "DEV"){
    node('dind') {
    withCredentials([
      // AWS credentials from secret files
      string(credentialsId: 'secret-AWS_ACCOUNT_ID', variable: 'AWS_ACCOUNT_ID'),
      string(credentialsId: 'secret-AWS_DEFAULT_REGION', variable: 'AWS_DEFAULT_REGION'),
      string(credentialsId: 'secret-AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
      string(credentialsId: 'secret-AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
      string(credentialsId: 'secret-jenkins-sa-noprod', variable: 'OCP_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-OCP_REGISTRY_URI', variable: 'OCP_NOPROD_REGISTRY_URI'),
      string(credentialsId: 'secret-OCP_PROD_REGISTRY_URI', variable: 'OCP_PROD_REGISTRY_URI'),
      // agregar como parametro API y Registry
      string(credentialsId: 'secret-OCP_API_URI', variable: 'OCP_API_URI'),
      string(credentialsId: 'secret-OCP_PROD_API_URI', variable: 'OCP_PROD_API_URI'),
      string(credentialsId: 'secret-JIRA_TOKEN', variable: 'JIRA_TOKEN'),
      string(credentialsId: 'SECRET-JIRA_URL', variable: 'JIRA_URL')
    ])
    {
    parameters{
           string defaulValue: 'alpine', description: 'Nombre de imagen Docker', name: 'appName'
           string defaultValue: 'latest', description: 'Version de imagen Docker', version: 'appTag'
           string defaultValue: 'jenkins-dind', description: 'Project Name', project: 'namespaceName'
           string defaulValue: '', description: 'Codigo sector', name: 'idTicket'
        }
        
   
      stage('Docker pull from ECR and push to OCP registry') {
        container('docker') {
          // Repository Envs
          IMAGE_REPO_NAME = "$appName"
          IMAGE_TAG = "$appTag"
          REPOSITORY_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"
          PROJECT_NAME = "$namespaceName"

          // Login to ECR
          sh """aws configure set aws_access_key_id ${AWS_ACCESS_KEY_ID} """
          sh """aws configure set aws_secret_access_key ${AWS_SECRET_ACCESS_KEY}"""
          sh """aws ecr get-login-password --region ${AWS_DEFAULT_REGION} | podman login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"""
          // Pull docker image from ECR 
          sh """podman pull ${REPOSITORY_URI}/${IMAGE_REPO_NAME}:${IMAGE_TAG}"""
          sh """podman tag ${REPOSITORY_URI}/${IMAGE_REPO_NAME}:${IMAGE_TAG} ${OCP_NOPROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag}"""
          sh """oc login --token=${OCP_SECRET_SA_TOKEN} --server=https://${OCP_API_URI}  --insecure-skip-tls-verify=true"""
          sh """oc registry login --insecure=true"""
          sh """podman push --tls-verify=false ${OCP_NOPROD_REGISTRY_URI}/${namespaceName}/${appName}:${appTag}"""  
          sh """oc get is"""
            
        }
      }
  
      stage('Clone git repository') {
       withCredentials([
            gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')
           ]) {   
            sh 'git config --global http.sslVerify false'
            git url: 'https://github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium', branch: 'test', credentialsId: 'SECRET_GIT_EDENOR'
            sh 'ls -lFha'     
        }
      }
        
        stage('Replace version in template and apply in OCP') {
        container('docker') {
        sh 'echo Stage: Replace version in template and apply in OCP'
        sh "echo Exporta y asigna la env al manifiesto"
        sh """export appName="${params.appName}" && export namespaceName="${params.namespaceName}" && export appTag="${params.appTag}" && /usr/bin/envsubst < manifiesto.template.yaml | oc apply -f -"""
        sh """export appName="${params.appName}" && export namespaceName="${params.namespaceName}" && export appTag="${params.appTag}" && /usr/bin/envsubst < manifiesto.template.yaml | cat - > Logs/${entorno}-manifiesto-${env.BUILD_NUMBER}.yaml"""
          
        } 
        }
    }
        
        stage('Update GIT') {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            withCredentials([gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')]) {
                    sh 'ls -lFha'
                    sh "git config user.email SVC_Jenkins_OShift@github.pro.edenor"
                    sh "git config user.name SVC_Jenkins_OShift"
                    sh "git add Logs/${entorno}-manifiesto-${env.BUILD_NUMBER}.yaml"
                    sh "git commit -m 'Build: ${env.BUILD_NUMBER}'"
                    sh "git push https://SVC_Jenkins_OShift:9d6f4c3f1fda0fe32ac48ff5db4820e61c64eba5@github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium.git"
            }
            }
        } // End Parameters
        } // End Node   
    } //End If DEV
} // End ALL
