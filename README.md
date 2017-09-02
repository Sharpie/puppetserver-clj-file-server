# Puppet Server: Clojure File Server

This project implements the Puppet file serving APIs in Clojure. Moving the
implementation of these APIs from Ruby to Clojure allows the JRuby workers
to be reserved for operations such as catalog compilation and report processing.

The project currently implements the [file content][content] and
[file metadata][metadata] APIs for the following mount points:

  - `/modules`
  - `/plugins`
  - `/pluginfacts`

Requests for other mount points, such as custom mount points defined in
`fileserver.conf`, are passed through to the original Ruby implementation.
Additionally, the list of available Puppet environments and associated
modulepaths is read from Ruby once during startup. A reload of the Puppet
Server process is currently required for the Clojure file server to pick
up new environments or changes in modulepaths.

**NOTE:** This project is currently an experimental prototype focused on
exploring the potential efficiency gains of moving the Puppet file serving
APIs from the Ruby layer to the Clojure layer. No focus has been placed on
security or ensuring the implementation produces correct results for all cases.
Therefore, this implementation should not be used in production settings.

[content]: https://docs.puppet.com/puppet/5.1/http_api/http_file_content.html
[content]: https://docs.puppet.com/puppet/5.1/http_api/http_file_metadata.html

## Usage

The Clojure file server is intended to be deployed as a drop-in addition to
an existing Puppet Server installation. This is accomplished by:

  - Running `lein jar` and adding the resulting JAR file to the classpath
    used by the Puppet Server.

  - Adding the `clj-file-serving-service` to the bootstrap configuration
    of Puppet Server.

A Puppet module is included that can perform the necessary configuration for
a Puppet Enterprise installation. To build the module:

  - Run: `lein with-profile :module jar`

  - Copy the resulting JAR to the `files` directory: `cp target/clj-file-server.jar files/`

  - Build the module: `puppet module build .`

The resulting module includes a `clj_file_server` class that can be added to
the "PE Masters" group of a Puppet Enterprise installation.

## Implementation Details

When added to a Puppet Server instance, the `clj-file-serving-service` defines
Jetty handlers for the following paths:

  - `puppet/v3/file_content`
  - `puppet/v3/file_metadata`
  - `puppet/v3/file_metadatas`

These handlers have a higher precedence than the handler for `puppet/` which
dispatches to the original Ruby implementation. Any request that isn't handled
by the `clj-file-serving-service` implementations falls through to the original
`puppet/` handler.

### Limitations

  - A reload of the entire Puppet Server process is required to pick up changes
    to environments or modulepaths. Ideally, this would hook into existing
    Puppet Server functionality around the environment cache.

  - The `ignore` parameter of the `file_metadatas` API is not implemented.

  - The route handlers aren't tied into Puppet Server authorization checks such
    as auth.conf.

  - Handling of file permissions errors is not implemented.

