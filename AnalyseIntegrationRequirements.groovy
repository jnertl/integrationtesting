pipeline {
    agent any
    environment {
        git_checkout_root = '/var/jenkins_home/workspace/integration_test_requirements_analysis_git_checkout'
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
                    rm -fr "${WORKSPACE}/requirements_analysis.md" || true
                    rm -fr "${WORKSPACE}/prompt.txt" || true
                '''
            }
        }
        stage('Analyse requirements') {
            steps {
                sh '''
                    echo 'Analysing requirements...'

                    export SOURCE_ROOT_DIR="$git_checkout_root"
                    
                    # Set up middleware context for analysis
                    export MIDDLEWARE_SOURCE_CODE="${SOURCE_ROOT_DIR}/middlewaresw"

                    # Set up gui client context for analysis
                    export GUI_CLIENT_SOURCE_CODE="${SOURCE_ROOT_DIR}/mwclientwithgui"

                    # Set up test case context for analysis
                    export TEST_SOURCE_CODE="${SOURCE_ROOT_DIR}/testframework/tests"

                    # Copy test requirements for analysis
                    cp "${WORKSPACE}/integration_testing_requirements.md" "${SOURCE_ROOT_DIR}"
                    export TEST_REQUIREMENTS_FILE="${SOURCE_ROOT_DIR}/integration_testing_requirements.md"

                    export MODEL=${AI_MODEL}
                    echo "Model in use: [${MODEL}]" > prompt.txt
                    echo "Source root directory: [${SOURCE_ROOT_DIR}]" >> prompt.txt
                    echo "Middlewaresw source code is in directory: [${MIDDLEWARE_SOURCE_CODE}]" >> prompt.txt
                    echo "Middlewaresw source code branch: [${MW_BRANCH}]" >> prompt.txt
                    echo "GUI client source code is in directory: [${GUI_CLIENT_SOURCE_CODE}]" >> prompt.txt
                    echo "Test source code directory: [${TEST_SOURCE_CODE}]" >> prompt.txt
                    echo "${AI_PROMPT}" >> prompt.txt
                    echo "**********************************"
                    echo "Using model: [${MODEL}]"
                    echo "**********************************"

                    bash "$SOURCE_ROOT_DIR/testframework/scripts/ongoing_printer.sh" \
                    /usr/local/bin/mcphost \
                    --temperature 0.1 --top-p 0.8 --top-k 50 --max-tokens 4096 \
                    --quiet --stream=false \
                    --system-prompt ./system_prompts/requirements_assistant.txt \
                    script user_prompts/analyse_requirements.sh \
                    >&1 | tee "$WORKSPACE/requirements_analysis.md"

                    echo 'Analysing requirements completed.'
                '''
            }
        }
    }
    post {
        always {
            archiveArtifacts(
                artifacts: 'prompt.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
            archiveArtifacts(
                artifacts: 'user_prompts/analyse_requirements.sh',
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
                artifacts: 'requirements_analysis.md',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Job succeeded!'
        }
    }
}
