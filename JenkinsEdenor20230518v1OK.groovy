podTemplate(
  cloud: 'openshift',
  label: 'dind', 
  serviceAccount: 'jenkins-jenkins-server',
  podRetention: onFailure(),
  idleMinutes: '5',
  ///////// Usa template DIND (con oc cli y aws cli) de registry de OS 
  containers: [
    containerTemplate(
      name: 'docker',
      image: 'default-route-openshift-image-registry.apps.ocp02-noprod.pro.edenor/jenkins-dind/dind:oc412',
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

{
  node('dind') {
    withCredentials([
      // AWS credentials from secret files
      string(credentialsId: 'secret-AWS_ACCOUNT_ID', variable: 'AWS_ACCOUNT_ID'),
      string(credentialsId: 'secret-AWS_DEFAULT_REGION', variable: 'AWS_DEFAULT_REGION'),
      string(credentialsId: 'secret-AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
      string(credentialsId: 'secret-AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
      string(credentialsId: '7189b88c-c3dd-48af-bdbf-9c946d4a2884', variable: 'OCP_SECRET_SA_TOKEN'),
      string(credentialsId: 'secret-OCP_REGISTRY_URI', variable: 'OCP_REGISTRY_URI'),
      string(credentialsId: 'secret-OCP_API_URI', variable: 'OCP_API_URI')
    ])
    {
    parameters{
            string defaulValue: 'alpine', description: 'Nombre de imagen Docker', name: 'name'
            string defaultValue: 'latest', description: 'Version de imagen Docker', version: 'version'
           string defaultValue: 'jenkins-dind', description: 'Project Name', project: 'project'
        }
    
      stage('Docker pull from ECR and push to OCP registry') {
        container('docker') {
          // Repository Envs
          IMAGE_REPO_NAME = "$name"
          IMAGE_TAG = "$version"
          REPOSITORY_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"
          PROJECT_NAME = "$project"

          // Login to ECR
          sh """aws configure set aws_access_key_id ${AWS_ACCESS_KEY_ID}"""
          sh """aws configure set aws_secret_access_key ${AWS_SECRET_ACCESS_KEY}"""
          sh """aws ecr get-login-password --region ${AWS_DEFAULT_REGION} | podman login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"""
          sh """podman info"""
          // Pull docker image from ECR 
          sh """podman pull ${REPOSITORY_URI}/${IMAGE_REPO_NAME}:${IMAGE_TAG}"""
          sh """oc login --token=${OCP_SECRET_SA_TOKEN} --server=https://${OCP_API_URI}  --insecure-skip-tls-verify=true"""
          sh """oc whoami"""
          //sh """oc login --token=sha256~ksMb8Yj86-B5i4PForWWChgSHQWbOMEmzxotvL-0cYs --server=https://${OCP_API_URI} --insecure-skip-tls-verify=true"""
          sh """oc registry login --insecure=true"""
          //sh """echo 'sha256~ksMb8Yj86-B5i4PForWWChgSHQWbOMEmzxotvL-0cYs' | podman login -u pgigena --password-stdin --tls-verify=false ${OCP_REGISTRY_URI}"""
          sh """podman tag ${REPOSITORY_URI}/${IMAGE_REPO_NAME}:${IMAGE_TAG} ${OCP_REGISTRY_URI}/${PROJECT_NAME}/${IMAGE_REPO_NAME}:${IMAGE_TAG}"""
          sh """podman push --tls-verify=false ${OCP_REGISTRY_URI}/${PROJECT_NAME}/${IMAGE_REPO_NAME}:${IMAGE_TAG} """
                
        }
      }
  
      stage('Clone git repository') {
       withCredentials([
            gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')
           ]) {   
            sh 'git config --global http.sslVerify false'
            //git url: 'https://github.com/upszot/dind-dockerfile', branch: 'master', credentialsId: 'SECRET_GIT_EDENOR'
            git url: 'https://github.com/pgigena-fusion/dind-dockerfile.git', branch: 'master', credentialsId: 'SECRET_GIT_EDENOR'
            //git url: 'https://github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium', branch: 'master', credentialsId: 'SECRET_GIT_EDENOR'
            sh 'ls -lFha'     
        }
      }
        
        stage('Replace version in template and apply in OCP') {
        container('docker') {

          sh "echo Exporta y asigna la env al manifiesto"
          sh """export appName="${params.name}" && export namespaceName="${params.project}" && export appTag="${params.version}" && /usr/bin/envsubst < manifiesto.yaml | oc apply -f - """
          sh """export appName="${params.name}" && export namespaceName="${params.project}" && export appTag="${params.version}" && /usr/bin/envsubst < manifiesto.yaml | cat - > manifiesto${env.BUILD_NUMBER}.yml"""

          
          /// Debug yaml
          //sh """export appName="${params.name}" && export namespaceName="${params.project}" && export appTag="${params.version}" && /usr/bin/envsubst < manifiesto.yaml | cat - - """
        } 
        }
    }
        
        stage('Update GIT') {
            catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            //withCredentials([usernamePassword(credentialsId: 'PSECRET_GIT_EDENOR', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
            //sh('git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${GIT_USERNAME}/dind-dockerfile.git')
            withCredentials([gitUsernamePassword(credentialsId: 'SECRET_GIT_EDENOR', gitToolName: 'Default')]) {
                    //sh "git config user.email pgigena@com.ar"
                    //sh "git config user.name SVC_Jenkins_OShift"
                    //sh "git add manifiesto${env.BUILD_NUMBER}.yml"
                    //sh "git commit -m 'Build: ${env.BUILD_NUMBER}'"
                    //sh "git push https://SVC_Jenkins_OShift:9d6f4c3f1fda0fe32ac48ff5db4820e61c64eba5@github.pro.edenor/edenorsa/pro-edenor-openshift-pipelines-prometium.git"
                    sh "git config user.email pgigena@fusion.com.ar"
                    sh "git config user.name pgigena-fusion"
                    sh "git add manifiesto${env.BUILD_NUMBER}.yml"
                    sh "git commit -m 'Build: ${env.BUILD_NUMBER}'"
                    sh "git push https://pgigena-fusion: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@github.com/pgigena-fusion/dind-dockerfile.git"
                    
            }
        }
        }
        }
}