
pipeline {
    agent any

    tools {
        jdk 'JDK17'
        maven 'MAVEN3'
    }

    environment {
        SONAR_SCANNER_OPTS = "-Xmx512m"
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                bat 'mvn clean compile'
            }
        }

        stage('Unit Test') {
            steps {
                bat 'mvn test'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('Windows-Sonar') {
                    bat '''
                    mvn sonar:sonar ^
                    -Dsonar.projectKey=my-java-project ^
                    -Dsonar.projectName=my-java-project ^
                    -Dsonar.java.binaries=target
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Package') {
            steps {
                bat 'mvn package'
            }
        }

    }

    post {
        success {
            echo '✅ Build & SonarQube check passed!'
        }
        failure {
            echo '❌ Build or SonarQube failed!'
        }
    }
}
