#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "local"
    command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "${env://SOURCE_ROOT_DIR:-''}"]

model: "${env://MODEL:-ollama:granite3.1-moe:3b}"
---
Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

Middlewaresw source code is in directory: [${env://MIDDLEWARE_SOURCE_CODE}]

GUI client source code is in directory: [${env://GUI_CLIENT_SOURCE_CODE}]

Test case source code is in directory: [${env://TEST_SOURCE_CODE}]

Test results are in directory: [${env://TEST_RESULTS_FOLDER}]

My question is:
${env://AI_PROMPT}
