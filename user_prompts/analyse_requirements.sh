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
Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

Source code and test case files are available from directory:
${env://SOURCE_ROOT_DIR}

My question is:
${env://AI_PROMPT}