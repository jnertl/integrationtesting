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
Middleware (server) source code context file:
- ${env://CPP_CONTEXT_FILE}

mwclientwithgui (client)source code context file:
- ${env://PYTHON_CONTEXT_FILE}

Test case context file:
- ${env://TEST_CONTEXT_FILE}

Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

My question is:
${env://AI_PROMPT}