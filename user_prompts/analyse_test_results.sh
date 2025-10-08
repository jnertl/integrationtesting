#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "local"
    command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "${env://SOURCE_ROOT_DIR}"]

model: "${env://MODEL}"
---
Test requirements are in the file: ${env://TEST_REQUIREMENTS_FILE}
Test results are in the directory: ${env://TEST_RESULTS_FOLDER_FOR_AI}
middleware source code is in the directory: ${env://MIDDLEWARE_SOURCE_CODE}
gui client source code is in the directory: ${env://GUI_CLIENT_SOURCE_CODE}
test case source code is in the directory: ${env://TEST_SOURCE_CODE}

My question is:
${env://AI_PROMPT}
