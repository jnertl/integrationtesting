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

My question is:
${env://AI_PROMPT}
