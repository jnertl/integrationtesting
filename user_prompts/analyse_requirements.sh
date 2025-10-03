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
Source code context files:
- Middleware source file: ${env://MW_CONTEXT_FILE}
- mwclientwithgui source file: ${env://GUI_CONTEXT_FILE}

Test case context file:
- ${env://TEST_CONTEXT_FILE}

Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

Prompt for analysis:
${env://AI_PROMPT}