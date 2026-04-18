# Kestra GraphQL Plugin

## What

- Provides plugin components under `io.kestra.plugin.graphql`.
- Includes classes such as `Request`.

## Why

- This plugin integrates Kestra with GraphQL.
- It provides tasks that send GraphQL queries and mutations over HTTP endpoints.

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

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
