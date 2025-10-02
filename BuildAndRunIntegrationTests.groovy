pipeline {
    agent any
    environment {
        git_checkout_root = '/var/jenkins_home/workspace/integration_test_failure_analysis_git_checkout'
    }
    stages {
        stage('Checkout') {
            steps {
                sh '''
                    echo "Git checkout root:${git_checkout_root}"
                    rm -fr "${git_checkout_root}" || true
                    mkdir -p "${git_checkout_root}"
                    cd "${git_checkout_root}"
                    git clone --single-branch --branch main https://github.com/jnertl/middlewaresw.git
                    git clone --single-branch --branch main https://github.com/jnertl/mwclientwithgui.git
                    git clone --single-branch --branch main https://github.com/jnertl/testframework.git
                    echo "middlewaresw"
                    git --no-pager -C middlewaresw/ show --summary
                    echo "mwclientwithgui"
                    git --no-pager -C mwclientwithgui/ show --summary
                    echo "testframework"
                    git --no-pager -C testframework/ show --summary
                '''
            }
        }
        stage('Build binaries') {
            steps {
                sh '''
                    cd "${git_checkout_root}/middlewaresw"
                    ./build.sh
                '''
            }
        }
        stage('Cleanup workspace') {
            steps {
                sh '''
                    rm -fr "${WORKSPACE}/middlewaresw.log" || true
                    rm -fr "${WORKSPACE}/mwclientwithgui.log" || true
                    rm -fr "${WORKSPACE}/test_results.log" || true
                    rm -fr "${WORKSPACE}/results" || true
                '''
            }
        }
        stage('Install robot framework') {
            steps {
                sh '''
                    cd "${git_checkout_root}/testframework"
                    export UV_VENV_CLEAR=1
                    rm -fr robot_venv || true
                    /root/.local/bin/uv venv robot_venv
                    . robot_venv/bin/activate
                    /root/.local/bin/uv pip install -r requirements.txt --link-mode=copy
                    robot --version || true
                '''
            }
        }
        stage('Run integration tests') {
            steps {
                sh '''
                    cd "${git_checkout_root}/testframework"
                    . robot_venv/bin/activate
                    export MW_SW_BIN_PATH="${git_checkout_root}/middlewaresw/build_application"
                    export MW_CLIENT_PATH="${git_checkout_root}/mwclientwithgui"
                    scripts/run_tests.sh -i integration -o "${WORKSPACE}/results" || true
                    cp "${git_checkout_root}/testframework/middlewaresw.log" "${WORKSPACE}" || true
                    cp "${git_checkout_root}/testframework/mwclientwithgui.log" "${WORKSPACE}" || true
                '''
            }
        }
        stage('Display results') {
            steps {
                robot(outputPath: "results",
                    passThreshold: 90.0,
                    unstableThreshold: 70.0,
                    disableArchiveOutput: true,
                    outputFileName: "output.xml",
                    logFileName: 'log.html',
                    reportFileName: 'report.html',
                    countSkippedTests: true,    // Optional, defaults to false
                    otherFiles: 'screenshot-*.png'
                )
            }
        }
    }
    post {
        always {
            archiveArtifacts(
                artifacts: 'results/*',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'middlewaresw.log',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'mwclientwithgui.log',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Build succeeded!'
        }
    }
}
