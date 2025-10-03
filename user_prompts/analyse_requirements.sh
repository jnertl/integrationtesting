#!/usr/bin/env -S mcphost script
---
model: "${env://MODEL:-ollama:llama3.2:3b}"
---
Integration testing requirements are as follows:
${env://TEST_REQUIREMENTS}

Robot test cases  are as follows
${env://TEST_SOURCE_CODE}

My question is:
${env://AI_PROMPT}