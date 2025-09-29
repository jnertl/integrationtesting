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
                    sleep 2

                    # Start mwclientwithgui in the background
                    cd "${git_checkout_root}/mwclientwithgui"
                    python3 -m venv venv
                    . venv/bin/activate
                    pip install -r requirements.txt
                    python mw_gui_client.py > "${WORKSPACE}/mwclientwithgui.log" 2>&1 &
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

                    # Only exit after both checks
                    if [ $MW_SW_FAILED -ne 0 ] || [ $MW_CLIENT_FAILED -ne 0 ]; then
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
