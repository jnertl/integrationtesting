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
        stage('Make integration test fail') {
            steps {
                sh '''
                    echo "Making middlewaresw crash"
                    sed -i 's/^static const bool crashEnabled *= *false.*/static const bool crashEnabled = true;/' "${git_checkout_root}/middlewaresw/src/Engine.cpp"

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

                    # Clear dmesg to capture only relevant crash info later
                    dmesg -C

                    # Allow core dumps
                    echo "core" > /proc/sys/kernel/core_pattern
                    ulimit -c unlimited
                    rm -f "${git_checkout_root}/middlewaresw/core.*" || true

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

                    echo "Checking dmesg for segfaults..."
                    dmesg | grep -i segfault | tee -a "${WORKSPACE}/test_results.log"
                    if [ $? -eq 0 ]; then
                        echo "TEST FAILED: Segfault detected in dmesg." | tee -a "${WORKSPACE}/test_results.log"
                        echo "Generating backtrace..." | tee -a "${WORKSPACE}/test_results.log"
                        cd "${git_checkout_root}/middlewaresw/"
                        gdb -batch -ex "bt" -ex "quit" build_application/middlewaresw core* | tee -a "${WORKSPACE}/test_results.log"
                    fi

                    # Terminate both processes
                    kill -9 $MIDDLEWARESW_PID || true
                    kill -9 $MWCLIENTWITHGUI_PID || true

                    # Final test result
                    if [ $MW_SW_FAILED -ne 0 ] || [ $MW_CLIENT_FAILED -ne 0 ]; then
                        echo "TEST FAILED: One of the expected processes is not running." | tee -a "${WORKSPACE}/test_results.log"
                    fi
                '''
            }
        }
        stage('Analyse results') {
            steps {
                sh '''
                    # All tests passed, but middlewaresw was made to crash, so this is unexpected
                    echo "all tests passed" | tee -a "${WORKSPACE}/test_results.log"
                '''
            }
        }
    }
    post {
        always {
            sh '''
                echo 'Analysing crash...'
                rm -fr mcpdemo || true
                git clone --single-branch --branch main https://github.com/jnertl/mcpdemo.git
                cd mcpdemo
                git --no-pager show --summary

                export SOURCE_ROOT_DIR="$git_checkout_root"
                export MW_LOG=$(cat "${WORKSPACE}/middlewaresw.log")
                export GUI_LOG=$(cat "${WORKSPACE}/mwclientwithgui.log")
                export TEST_RESULTS_LOG=$(cat "${WORKSPACE}/test_results.log" || echo "No test_results.log found.")
                
                # Set up middleware context for analysis
                export MW_CONTEXT_FILE=mw_src_context.txt
                export SOURCE_DIR="$SOURCE_ROOT_DIR/middlewaresw"
                export CONTEXT_FILE="$SOURCE_ROOT_DIR/$MW_CONTEXT_FILE"
                ./create_context.sh
                if [ ! -f "$CONTEXT_FILE" ]; then
                    echo "ERROR: CONTEXT_FILE $CONTEXT_FILE does not exist after create_context.sh" >&2
                    exit 1
                fi

                # Set up gui client context for analysis
                export GUI_CONTEXT_FILE=gui_src_context.txt
                export SOURCE_DIR="$SOURCE_ROOT_DIR/mwclientwithgui"
                export CONTEXT_FILE="$SOURCE_ROOT_DIR/$GUI_CONTEXT_FILE"
                ./create_context.sh
                if [ ! -f "$CONTEXT_FILE" ]; then
                    echo "ERROR: CONTEXT_FILE $CONTEXT_FILE does not exist after create_context.sh" >&2
                    exit 1
                fi

                export TEST_REQUIREMENTS=$(cat "${WORKSPACE}/integration_testing_requirements.md" || echo "No test_requirements.md found.")

                ./ongoing_printer.sh \
                /usr/local/bin/mcphost \
                --temperature 0.1 --top-p 0.8 --top-k 50 --max-tokens 4096 \
                --quiet --stream=false \
                --system-prompt ./system_prompts/jenkins_results_assistant.txt \
                script user_prompts/analyse_crash.sh \
                >&1 | tee $WORKSPACE/failure_analysis.txt

                echo 'Analysing completed.'
            '''

            archiveArtifacts(
                artifacts: 'mcpdemo/user_prompts/analyse_crash.sh',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'mcpdemo/system_prompts/jenkins_results_assistant.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'integration_testing_requirements.md',
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
            archiveArtifacts(
                artifacts: 'failure_analysis.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Build succeeded!'
        }
    }
}
