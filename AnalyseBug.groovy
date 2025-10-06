pipeline {
    agent any
    environment {
        git_checkout_root = '/var/jenkins_home/workspace/integration_test_bug_analysis_git_checkout'
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
        stage('Cleanup workspace') {
            steps {
                sh '''
                    rm -fr "${WORKSPACE}/bug_analysis.txt" || true
                    rm -fr "${WORKSPACE}/test_results.zip" || true
                '''
            }
        }
        stage('Analyse bug') {
            steps {
                sh '''
                    echo 'Analysing bug...'

                    export SOURCE_ROOT_DIR="$git_checkout_root"
                    
                    # Set up middleware context for analysis
                    export MIDDLEWARE_SOURCE_CODE="$git_checkout_root/middlewaresw"

                    # Set up gui client context for analysis
                    export GUI_CLIENT_SOURCE_CODE="$git_checkout_root/mwclientwithgui"

                    # Set up test case context for analysis
                    export TEST_SOURCE_CODE="$git_checkout_root/testframework/tests"
                    
                    export TEST_REQUIREMENTS=$(cat "${WORKSPACE}/integration_testing_requirements.md" 2>/dev/null || echo "No test_requirements.md found.") > /dev/null

                    TEST_RESULTS_FOLDER_FOR_AI=""
                    if [ -n "$TEST_RESULTS_FOLDER" ] && [ "$TEST_RESULTS_FOLDER" != "" ]; then
                        TEST_RESULTS_FOLDER_FOR_AI="${SOURCE_ROOT_DIR}/test_results"
                        mkdir -p ${TEST_RESULTS_FOLDER_FOR_AI} || true
                        cp -r ${TEST_RESULTS_FOLDER}/* ${TEST_RESULTS_FOLDER_FOR_AI}/ || true
                        zip -r -j "${WORKSPACE}/test_results.zip" "${TEST_RESULTS_FOLDER_FOR_AI}" || true
                    fi

                    MODEL=${AI_MODEL}
                    echo "Model in use: ${MODEL}" > prompt.txt
                    echo "Source root directory: ${SOURCE_ROOT_DIR}" >> prompt.txt
                    echo "Middlewaresw source code is in directory: [${MIDDLEWARE_SOURCE_CODE}]" >> prompt.txt
                    echo "Middlewaresw source code branch: [${MW_BRANCH}]" >> prompt.txt
                    echo "GUI client source code is in directory: [${GUI_CLIENT_SOURCE_CODE}]" >> prompt.txt
                    echo "Test source code directory: ${TEST_SOURCE_CODE}" >> prompt.txt
                    echo "Test results folder for AI: ${TEST_RESULTS_FOLDER_FOR_AI}\n\n" >> prompt.txt
                    echo "${AI_PROMPT}" >> prompt.txt
                    echo "**********************************"
                    echo "Using model: ${AI_MODEL}"
                    echo "**********************************"

                    bash "$SOURCE_ROOT_DIR/testframework/scripts/ongoing_printer.sh" \
                    /usr/local/bin/mcphost \
                    --temperature 0.1 --top-p 0.8 --top-k 50 --max-tokens 4096 \
                    --quiet --stream=false \
                    --system-prompt ./system_prompts/quality_manager.txt \
                    script user_prompts/analyse_bug.sh \
                    >&1 | tee "$WORKSPACE/bug_analysis.txt"

                    echo 'Analysing bug completed.'
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts(
                artifacts: 'test_results.zip',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'prompt.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'user_prompts/analyse_bug.sh',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'system_prompts/requirements_assistant.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'integration_testing_requirements.md',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'bug_analysis.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Build succeeded!'
        }
    }
}
