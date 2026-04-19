# Kestra GraphQL Plugin

## What

- Provides plugin components under `io.kestra.plugin.graphql`.
- Includes classes such as `Request`.

## Why

- What user problem does this solve? Teams need to send GraphQL queries and mutations over HTTP endpoints from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps GraphQL steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on GraphQL.

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
