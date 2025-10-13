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

                    if [ -z "$MW_BRANCH" ]; then
                        MW_BRANCH="main"
                    fi

                    git clone --single-branch --branch $MW_BRANCH https://github.com/jnertl/middlewaresw.git
                    git clone --single-branch --branch main https://github.com/jnertl/mwclientwithgui.git
                    git clone --single-branch --branch main https://github.com/jnertl/testing.git
                    echo "middlewaresw"
                    git --no-pager -C middlewaresw/ show --summary
                    echo "mwclientwithgui"
                    git --no-pager -C mwclientwithgui/ show --summary
                    echo "testing"
                    git --no-pager -C testing/ show --summary
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
                    rm -fr "${WORKSPACE}/mwclientwithgui_process.log" || true
                    rm -fr "${WORKSPACE}/results" || true
                    rm -fr "${WORKSPACE}/test_results.zip" || true
                '''
            }
        }
        stage('Run integration tests') {
            steps {
                sh '''
                    cd "${git_checkout_root}/testing"
                    export UV_VENV_CLEAR=1
                    rm -fr pytests_venv || true
                    ~/.local/bin/uv venv pytests_venv
                    . pytests_venv/bin/activate
                    ~/.local/bin/uv pip install -r requirements.txt --link-mode=copy
                    pytest --version
                    export MW_SW_BIN_PATH="${git_checkout_root}/middlewaresw/build_application"
                    export MW_CLIENT_PATH="${git_checkout_root}/mwclientwithgui"
                    export MW_LOG_OUTPUT_FILE="${WORKSPACE}/middlewaresw.log"
                    export MW_CLIENT_LOG_OUTPUT_FILE="${WORKSPACE}/mwclientwithgui.log"
                    export MW_CLIENT_PROCESS_OUTPUT_FILE="${WORKSPACE}/mwclientwithgui_process.log"

                    includeTags="integration"
                    if [ -n "$INCLUDE_TAG_PARAMS" ]; then
                        includeTags="$INCLUDE_TAG_PARAMS"
                    fi

                    echo "Using includeTags: ${includeTags}"
                    ./run_tests.sh --marks ${includeTags} -o "${WORKSPACE}/results" || true

                    if [ -f "${WORKSPACE}/middlewaresw.log" ]; then
                        echo "Checking dmesg for segfaults..."
                        dmesg | grep -i segfault | tee -a "${WORKSPACE}/middlewaresw.log"
                        if [ $? -eq 0 ]; then
                            echo "TEST FAILED: Segfault detected in dmesg." | tee -a "${WORKSPACE}/middlewaresw.log"
                            echo "Generating backtrace..." | tee -a "${WORKSPACE}/middlewaresw.log"
                            cd "${git_checkout_root}/middlewaresw/"
                            gdb -batch -ex "bt" -ex "quit" "${MW_SW_BIN_PATH}/middlewaresw" core* | tee -a "${MW_LOG_OUTPUT_FILE}"
                        fi
                    fi
                    zip -r -j "${WORKSPACE}/test_results.zip" "${WORKSPACE}/results" || true
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
                artifacts: 'test_results.zip',
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
            archiveArtifacts(
                artifacts: 'mwclientwithgui_process.log',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Build succeeded!'
        }
        failure {
            sh '''
                echo "TEST FAILED: One or more tests failed."
                TEST_RESULT_DIR_FOR_ANALYSIS="/var/jenkins_home/workspace/latest_failed_tests/"
                rm -fr "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
                mkdir -p "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
                cp -r "${WORKSPACE}/results/"* "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
                cp -r "${WORKSPACE}/middlewaresw.log" "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
                cp -r "${WORKSPACE}/mwclientwithgui.log" "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
                cp -r "${WORKSPACE}/mwclientwithgui_process.log" "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
            '''
        }
    }
}
