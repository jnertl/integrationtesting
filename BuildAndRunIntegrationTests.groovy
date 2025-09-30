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
                    echo "middlewaresw"
                    git --no-pager -C middlewaresw/ show --summary
                    echo "mwclientwithgui"
                    git --no-pager -C mwclientwithgui/ show --summary
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
                '''
            }
        }
        stage('Run integration tests') {
            steps {
                sh '''
                    # Make sure no old instances are running
                    pkill middlewaresw || true

                    # Start middlewaresw in the background
                    cd "${git_checkout_root}/middlewaresw"
                    build_application/middlewaresw 1000 2>&1 | tee "${WORKSPACE}/middlewaresw.log" &
                    MIDDLEWARESW_PID=$!
                    echo "Started middlewaresw with PID $MIDDLEWARESW_PID"

                    # Start mwclientwithgui in the background
                    cd "${git_checkout_root}/mwclientwithgui"
                    export UV_VENV_CLEAR=1
                    rm -fr gui_venv || true
                    /root/.local/bin/uv venv gui_venv
                    . gui_venv/bin/activate
                    /root/.local/bin/uv pip install -r requirements.txt
                    QT_QPA_PLATFORM=offscreen python mw_gui_client.py "${WORKSPACE}/mwclientwithgui.log" &
                    MWCLIENTWITHGUI_PID=$!
                    echo "Started mwclientwithgui with PID $MWCLIENTWITHGUI_PID"

                    echo "Waiting 5 seconds before next check..."
                    sleep 5

                    # Check if middlewaresw is running
                    MW_SW_FAILED=0
                    if kill -0 $MIDDLEWARESW_PID 2>/dev/null; then
                        echo "Process MIDDLEWARESW_PID ($MIDDLEWARESW_PID) is still running."
                    else
                        echo "TEST FAILED: Process MIDDLEWARESW_PID ($MIDDLEWARESW_PID) is not running." | tee -a "${WORKSPACE}/test_results.log"
                        MW_SW_FAILED=1
                    fi

                    # Check if mwclientwithgui is running
                    MW_CLIENT_FAILED=0
                    if kill -0 $MWCLIENTWITHGUI_PID 2>/dev/null; then
                        echo "Process MWCLIENTWITHGUI_PID ($MWCLIENTWITHGUI_PID) is still running."
                    else
                        echo "TEST FAILED: Process MWCLIENTWITHGUI_PID ($MWCLIENTWITHGUI_PID) is not running." | tee -a "${WORKSPACE}/test_results.log"
                        MW_CLIENT_FAILED=1
                    fi

                    # Terminate both processes
                    kill -9 $MIDDLEWARESW_PID || true
                    kill -9 $MWCLIENTWITHGUI_PID || true

                    # Only exit after both checks
                    if [ $MW_SW_FAILED -ne 0 ] || [ $MW_CLIENT_FAILED -ne 0 ]; then
                        echo "TEST FAILED: One of the expected processes is not running." | tee -a "${WORKSPACE}/test_results.log"
                    fi
                '''
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
