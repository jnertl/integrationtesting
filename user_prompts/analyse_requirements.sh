#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "local"
    command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "${env://SOURCE_ROOT_DIR:-/tmp}"]

model: "${env://MODEL:-ollama:llama3.2:3b}"
---
Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

Test case source code is in directory: [${env://TEST_SOURCE_CODE}]

My question is:
${env://AI_PROMPT}
