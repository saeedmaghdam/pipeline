def call(Map params) {
    pipeline {
        parameters {
            string description: 'The project\'s name that will be used during the pipeline', name: 'PROJECT_NAME', trim: true, defaultValue: params.PROJECT_NAME ?: ''
            string description: 'The GitHub\'s repository address', name: 'REPOSITORY_ADDRESS', trim: true, defaultValue: params.REPOSITORY_ADDRESS ?: ''
            string description: 'The branch that will be used during pipeline', name: 'BRANCH', trim: true, defaultValue: params.BRANCH ?: 'main'
            string description: 'The Dockerfile\'s relative path', name: 'DOCKERFILE_PATH', trim: true, defaultValue: params.DOCKERFILE_PATH ?: ''
        }    
        
        agent {
            kubernetes {
                cloud 'kubernetes-cluster'
                inheritFrom 'kube-agent'
                namespace 'jenkins-agents'
                podRetention always()
            }
        }
        
        environment {
            DOTNET_SYSTEM_GLOBALIZATION_INVARIANT = "1"
            REGISTRY_PASSWORD = credentials('registry_password')
        }

        stages {
            stage('Checkout') {
                steps {
                    git branch: params.BRANCH, credentialsId: 'github', url: params.REPOSITORY_ADDRESS
                }
            }
            stage('Restore') {
                steps {
                    sh 'dotnet restore'
                } 
            }
            stage('Build') {
                steps {
                    sh 'dotnet build'
                } 
            }
            stage('Test') {
                steps {
                    sh 'dotnet test'
                } 
            }
            stage('Dockerize') {
                steps {
                    script {
                        sh "echo ${env.REGISTRY_PASSWORD} | docker login ${env.REGISTRY_URL} --username ${env.REGISTRY_USERNAME} --password-stdin"
                        def image = docker.build("${env.REGISTRY_URL}/antaeus/${params.PROJECT_NAME}:${env.BUILD_ID}", "-f ${params.DOCKERFILE_PATH} .")
                    }
                }
            }
            stage('Push Docker Image') {
                steps {
                    script {
                        def image = docker.image("${env.REGISTRY_URL}/antaeus/${params.PROJECT_NAME}:${env.BUILD_ID}")
                        image.push("${env.BUILD_ID}")
                    }
                }
            }
            stage('Deploy') {
                steps {
                    script {
                        sh "echo ${env.BUILD_ID}"
                        sh "helm upgrade --install ${params.PROJECT_NAME} ./helm/ --set image.tag=${env.BUILD_ID} -n om"
                    }
                }
            }
        }
    }
}