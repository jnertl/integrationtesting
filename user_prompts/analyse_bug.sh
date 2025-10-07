#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "local"
    command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "${env://SOURCE_ROOT_DIR}", "${env://TEST_RESULTS_FOLDER_FOR_AI}"]

model: "${env://MODEL}"
---
My question is:
${env://AI_PROMPT}

