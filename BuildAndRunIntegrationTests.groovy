pipeline {
    agent any
    environment {
        git_checkout_root = '/var/jenkins_home/workspace/integration_testing_git_checkout'
        test_results_dir = "${git_checkout_root}/test_results"
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
                    rm -fr "${test_results_dir}" || true
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
                    mkdir -p "${test_results_dir}" | true
                    export MW_SW_BIN_PATH="${git_checkout_root}/middlewaresw/build_application/middlewaresw"
                    export MW_CLIENT_PATH="${git_checkout_root}/mwclientwithgui"
                    export MW_LOG_OUTPUT_FILE="${test_results_dir}/middlewaresw.log"
                    export MW_CLIENT_LOG_OUTPUT_FILE="${test_results_dir}/mwclientwithgui.log"
                    export MW_CLIENT_PROCESS_OUTPUT_FILE="${test_results_dir}/mwclientwithgui_process.log"

                    includeTags="integration"
                    if [ -n "$INCLUDE_TAG_PARAMS" ]; then
                        includeTags="$INCLUDE_TAG_PARAMS"
                    fi

                    echo "Using includeTags: ${includeTags}"
                    ./run_tests.sh --marks ${includeTags} -o "${test_results_dir}" || true

                    if [ -f "${MW_LOG_OUTPUT_FILE}" ]; then
                        echo "Checking dmesg for segfaults..."
                        dmesg | grep -i segfault
                        if [ $? -eq 0 ]; then
                            echo "TEST FAILED: Segfault detected in dmesg." | tee -a "${MW_LOG_OUTPUT_FILE}"
                            dmesg | grep -i segfault | tee -a "${MW_LOG_OUTPUT_FILE}"
                            echo "Generating backtrace..." | tee -a "${MW_LOG_OUTPUT_FILE}"
                            cd "${git_checkout_root}/middlewaresw/"
                            gdb -batch -ex "bt" -ex "quit" "${MW_SW_BIN_PATH}/middlewaresw" core* | tee -a "${MW_LOG_OUTPUT_FILE}"
                        fi
                    fi
                    cp -r "${test_results_dir}" "${WORKSPACE}/" || true
                    zip -r -j "${WORKSPACE}/test_results.zip" "test_results" || true
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts(
                artifacts: 'test_results/*',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'test_results.zip',
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
                cp -r "${test_results_dir}/"* "${TEST_RESULT_DIR_FOR_ANALYSIS}" || true
            '''
        }
    }
}
