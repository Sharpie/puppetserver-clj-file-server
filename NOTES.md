# Clojure File Serving for Puppet Server

The Puppet Server currently delegates all file serving requests to a Ruby implementation. This implementation has been around for a while, but has several negative aspects:

  - JRuby instances are expensive and consume a lot of RAM to maintain. Therefore they should be reserved for operations which cannot be re-produced outside of Ruby. Serving static file conent is not an operation that absolutely requires a Ruby implementation.

  - The Ruby implementation has several quirks, notably it reads entire file contents into memory before sending them down to the clients. This can cause large files to blow out the JVM heap size and is just plain inefficient given the copied memory and kernel context switches that could be avoided using an interface like sendfile().

  - Bursts of file activity, such as pluginsync can deplete the JRuby pool which bottlenecks other requests.

  - The Ruby file server spends a lot of effort to cache checksums and other file metadata, but does not cache these results for later re-use.

On the other hand, the Ruby implementation has allowed custom mount points and file serving logic to be developed as Ruby plugins.


## Proposal

Start the process of moving Puppet file serving mounts out of Ruby and into Clojure. Start with a handful of defined mount points such as plugins and module files. Maintain a pathway to the Ruby file serving implementation as a fallback for custom mount points. This work can leverage recent additions to Puppet Server for enumerating Puppet environments and modules from the Clojure layer. New work will have to be done to enumerate custom file mounts. Integration with PE File Sync is also possible to allow caching data based on code deployments.

Future opportunity to add additional enhancements such as using rsync or zsync protocols for file transfer.


## Alternatives

### Jetty Handlers

Use components from the Jetty libraries to handle file serving requests. Notably, the ResourceCollection handler can be used to represent content from multiple directories as a single server path --- as required by the plugins endpoints.

Pros:

  - Fairly likely that efficient static file methods, such as sendfile(), will be used.

  - Logic to unify multiple directories into a single mount is already built.

Cons:

  - Not clear how file_metadata functionality would work ResourceCollection might not expose file paths. Thus, we may end up needing the to re-implement file search logic in Clojure anyway.

  - The trapperkeeper-jetty9 library doesn't expose the ability to add arbitrary handler in a nice way. Can be hacked in, but it is definitely a hack.

  - Not clear that symlink traversal can be handled on a per-request basis.

  - Not clear that ResourceCollection could handle returning multiple files for the Puppet agent's "search" requests.

  - Not clear how caching would work.

  - Not clear what the overhead of creating 100s of ResourceCollection or Resource handlers would be. A handful of capable Ring handlers might have a much lower footprint.


### Ring Handlers

Pros:

  - Can be implemented in Clojure, which is likely to be cleaner. Better opportunities for introspection and instrumentation.

Cons:

  - Not clear that a Ring response which returns file content will end up being optimized to use sendfile() vs. copying data in and out of the kernel.
