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
        stage('Cleanup workspace') {
            steps {
                sh '''
                    rm -fr "${WORKSPACE}/requirements_analysis.log" || true
                '''
            }
        }
        stage('Analyse requirements') {
            steps {
                sh '''
                    echo 'Analysing requirements...'

                    export SOURCE_ROOT_DIR="$git_checkout_root"
                    
                    # Set up middleware context for analysis
                    #export MW_CONTEXT_FILE=mw_src_context.txt
                    #export SOURCE_DIR="$SOURCE_ROOT_DIR/middlewaresw"
                    #export CPP_CONTEXT_FILE="$SOURCE_ROOT_DIR/$MW_CONTEXT_FILE"
                    #bash "$SOURCE_ROOT_DIR/testframework/scripts/create_cpp_context.sh"

                    # Set up gui client context for analysis
                    #export GUI_CONTEXT_FILE=gui_src_context.txt
                    #export SOURCE_DIR="$SOURCE_ROOT_DIR/mwclientwithgui"
                    #export PYTHON_CONTEXT_FILE="$SOURCE_ROOT_DIR/$GUI_CONTEXT_FILE"
                    #bash "$SOURCE_ROOT_DIR/testframework/scripts/create_python_context.sh"

                    # Set up test case context for analysis
                    #export TEST_CONTEXT_FILE=test_src_context.txt
                    #export SOURCE_DIR="$SOURCE_ROOT_DIR/testframework"
                    #export ROBOT_CONTEXT_FILE="$SOURCE_ROOT_DIR/$TEST_CONTEXT_FILE"
                    #bash "$SOURCE_ROOT_DIR/testframework/scripts/create_robot_context.sh"
                    export TEST_SOURCE_CODE="$git_checkout_root/testframework/tests"
                    
                    export TEST_REQUIREMENTS=$(cat "${WORKSPACE}/integration_testing_requirements.md" 2>/dev/null || echo "No test_requirements.md found.") > /dev/null

                    MODEL=${AI_MODEL}
                    export OLLAMA_HOST=http://10.0.2.2:11434
                    echo "Model in use: ${MODEL}" > prompt.txt
                    echo "Ollama host: ${OLLAMA_HOST}" >> prompt.txt
                    echo "Source root directory: ${SOURCE_ROOT_DIR}" >> prompt.txt
                    echo "Test source code directory: ${TEST_SOURCE_CODE}\n\n" >> prompt.txt
                    echo "${AI_PROMPT}" >> prompt.txt
                    echo "**********************************"
                    echo "Using model: ${AI_MODEL}"
                    echo "**********************************"

                    bash "$SOURCE_ROOT_DIR/testframework/scripts/ongoing_printer.sh" \
                    /usr/local/bin/mcphost \
                    --temperature 0.1 --top-p 0.8 --top-k 50 --max-tokens 4096 \
                    --quiet --stream=false \
                    --system-prompt ./system_prompts/requirements_assistant.txt \
                    script user_prompts/analyse_requirements.sh \
                    >&1 | tee "$WORKSPACE/requirements_analysis.txt"

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
                artifacts: 'requirements_analysis.txt',
                fingerprint: true,
                allowEmptyArchive: true
            )
        }
        success {
            echo 'Build succeeded!'
        }
    }
}
