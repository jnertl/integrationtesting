pipeline {
    agent any
    environment {
        git_checkout_root = '/var/jenkins_home/workspace/build_and_ut_git_checkout'
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
        stage('Run integration tests') {
            steps {
                sh '''
                    # Start middlewaresw in the background
                    cd "${git_checkout_root}/middlewaresw"
                    build_application/middlewaresw 100 > "${WORKSPACE}/middlewaresw.log" 2>&1 &
                    MIDDLEWARESW_PID=$!

                    # Start mwclientwithgui in the background
                    cd "${git_checkout_root}/mwclientwithgui"
                    export UV_VENV_CLEAR=1
                    /root/.local/bin/uv venv mcpdemo_venv
                    . mcpdemo_venv/bin/activate
                    /root/.local/bin/uv pip install -r requirements.txt
                    xvfb-run python mw_gui_client.py > "${WORKSPACE}/mwclientwithgui.log" 2>&1 &
                    MWCLIENTWITHGUI_PID=$!

                    sleep 2

                    # Check if middlewaresw is running
                    MW_SW_FAILED=0
                    if kill -0 $MIDDLEWARESW_PID 2>/dev/null; then
                        echo "Process MIDDLEWARESW_PID ($MIDDLEWARESW_PID) is still running."
                    else
                        echo "Process MIDDLEWARESW_PID ($MIDDLEWARESW_PID) is not running."
                        MW_SW_FAILED=1
                    fi

                    # Check if mwclientwithgui is running
                    MW_CLIENT_FAILED=0
                    if kill -0 $MWCLIENTWITHGUI_PID 2>/dev/null; then
                        echo "Process MWCLIENTWITHGUI_PID ($MWCLIENTWITHGUI_PID) is still running."
                    else
                        echo "Process MWCLIENTWITHGUI_PID ($MWCLIENTWITHGUI_PID) is not running."
                        MW_CLIENT_FAILED=1
                    fi

                    # Terminate both processes
                    kill -9 $MIDDLEWARESW_PID
                    kill -9 $MWCLIENTWITHGUI_PID

                    # Only exit after both checks
                    if [ $MW_SW_FAILED -ne 0 ] || [ $MW_CLIENT_FAILED -ne 0 ]; then
                        exit 1 
                    fi
                '''
            }
        }
        stage('Analyse results') {
            steps {
                sh '''
                    # Check for "Socket server started on port 5555" in middlewaresw.log
                    if grep -q "Socket server started on port 5555" "${WORKSPACE}/middlewaresw.log"; then
                        SOCKET_SERVER_STARTED=1
                    else
                        SOCKET_SERVER_STARTED=0
                    fi

                    # Check for "Received RPM: <number>, TEMP: <number>" in mwclientwithgui.log
                    if grep -Eq "Received RPM: [0-9]+, TEMP: [0-9]+" "${WORKSPACE}/mwclientwithgui.log"; then
                        RECEIVED_RPM_TEMP=1
                    else
                        RECEIVED_RPM_TEMP=0
                    fi

                    # Fail if either check did not pass
                    if [ $SOCKET_SERVER_STARTED -ne 1 ] || [ $RECEIVED_RPM_TEMP -ne 1 ]; then
                        echo "Log checks failed."
                        exit 1
                    fi
                '''
            }
        }
    }
    post {
        always {
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
