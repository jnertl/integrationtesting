#!/usr/bin/env -S mcphost script
---
mcpServers:
  filesystem:
    type: "builtin"
    name: "fs"
    options:
      allowed_directories: [${env://SOURCE_ROOT_DIR}]
      max_results: 100000

model: "${env://MODEL:-ollama:llama3.2:3b}"
---
prompt:
${env://AI_PROMPT}