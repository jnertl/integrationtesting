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
        stage('Analyse results') {
            steps {
                sh '''
                    echo "Check for 'Socket server started on port 5555' in middlewaresw.log" | tee -a "${WORKSPACE}/test_results.log"
                    if grep -q "Socket server started on port 5555" "${WORKSPACE}/middlewaresw.log"; then
                        SOCKET_SERVER_STARTED=1
                    else
                        echo "TEST FAILED: 'Socket server started on port 5555' was not found in middlewaresw.log" | tee -a "${WORKSPACE}/test_results.log"
                        SOCKET_SERVER_STARTED=0
                    fi

                    echo "Check for 'Received RPM: <number>, TEMP: <number>, OIL PRESSURE: <number>' in mwclientwithgui.log" | tee -a "${WORKSPACE}/test_results.log"
                    if grep -Eq "Received RPM: [0-9]+, TEMP: [0-9]+, OIL PRESSURE: [0-9]+" "${WORKSPACE}/mwclientwithgui.log"; then
                        CLIENT_RECEIVED_DATA=1
                    else
                        echo "TEST FAILED: 'Received RPM: <number>, TEMP: <number>, OIL PRESSURE: <number>' was not found in mwclientwithgui.log" | tee -a "${WORKSPACE}/test_results.log"
                        CLIENT_RECEIVED_DATA=0
                    fi

                    # Fail if either check did not pass
                    if [ $SOCKET_SERVER_STARTED -ne 1 ] || [ $CLIENT_RECEIVED_DATA -ne 1 ]; then
                        echo "TEST FAILED: One of the log checks failed." | tee -a "${WORKSPACE}/test_results.log"
                    fi
                '''
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
                artifacts: 'test_results.log',
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
