#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "local"
    command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "${env://SOURCE_ROOT_DIR}"]

model: "${env://MODEL}"
---
My question is:
${env://AI_PROMPT}

