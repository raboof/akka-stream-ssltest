Akka stream SSL connections not completing
==========================================

Run server with `sbt "run server placebo"` or `sbt "run server ssl"`.

Run client with `sbt "run client placebo"` or `sbt "run client ssl"`.

Client will connect to the server and `System.exit(0)` in one second.

If `SslTlsPlacebo` is used, the server will notice the client disconnecting:
```
$ sbt "run server placebo"
[info] Server started, listening on: /127.0.0.1:6000
[info] Client connected from: /127.0.0.1:60312
[info] Client disconnected
```

```
$ sbt "run client placebo"
[info] Simulating client crashing
```

However, if a proper SSL connection is established, the graph handling the incoming connection will never terminate.

```
$ sbt "run server ssl"
[info] Server started, listening on: /127.0.0.1:6000
[info] Client connected from: /127.0.0.1:60289
```

```
$ sbt "run client placebo"
[info] Simulating client crashing
```
