# Kestra GraphQL Plugin

## What

description = 'GraphQl Plugin for Kestra Exposes 1 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with GraphQL, allowing orchestration of GraphQL-based operations as part of data pipelines and automation workflows.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `graphql`

### Key Plugin Classes

- `io.kestra.plugin.graphql.Request`

### Project Structure

```
plugin-graphql/
├── src/main/java/io/kestra/plugin/graphql/
├── src/test/java/io/kestra/plugin/graphql/
├── build.gradle
└── README.md
```

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
