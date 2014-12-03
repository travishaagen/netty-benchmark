## Project Description

This project is a server, implemented in Java, that opens a socket on port 4000 and processes input from at most 5 clients
concurrently. Messages from clients are strings of nine digits (zero padded), followed by a newline sequence. If a
client sends a message containing the word 'terminate', then the server shuts down immediately. The server
logs all unique numbers to 'numbers.log', which is erased on server startup. Statistics are written to StdOut every
10 seconds with the count of received and duplicate numbers, in the format:

    received 9 numbers, 0 duplicates

## Assumptions

- More than 5 clients can connect to the server, but only 5 threads will concurrently process their input
- Messages are UTF-8 encoded strings
- It is assumed that clients and servers will both use a single \n newline character for ends of messages
- 'terminate' messages must be followed by a newline character
- Printed statistics are only for a given 10 second block of time
- Unless a folder is specified for 'numbers.log' (see below), the log file will be stored in a Java temporary directory, which will be printed to StdOut on startup

## Integration Tests

Run the client/server integration tests from the root project folder via,

    mvn clean install

    mvn -Dtest=ClientServerTestIT -DfailIfNoTests=false test

The output will be something like,

    -------------------------------------------------------
     T E S T S
    -------------------------------------------------------
    Running com.github.travishaagen.server.ClientServerTestIT
    16:34:42.367 [Thread-2] INFO  com.github.travishaagen.server.DigitsServer - Starting server on port 4000 with multi threaded event-loop
    16:34:42.526 [Thread-2] INFO  com.github.travishaagen.server.DigitsServer - Created journal file at /var/folders/29/7z3c7m7d78d9lw5_yy5xlz8c0000gn/T/numbers.log

    received 3 numbers, 1 duplicates

    16:34:50.404 [Thread-3] INFO  com.github.travishaagen.server.DigitsServer - Starting server on port 4000 with multi threaded event-loop
    16:34:50.407 [Thread-3] INFO  com.github.travishaagen.server.DigitsServer - Created journal file at /var/folders/29/7z3c7m7d78d9lw5_yy5xlz8c0000gn/T/numbers.log

    Wrote 1000000 values in 4220 milliseconds
    received 1000000 numbers, 0 duplicates

    16:35:02.637 [Thread-4] INFO  com.github.travishaagen.server.DigitsServer - Starting server on port 4000 with multi threaded event-loop
    16:35:02.693 [Thread-4] INFO  com.github.travishaagen.server.DigitsServer - Created journal file at /var/folders/29/7z3c7m7d78d9lw5_yy5xlz8c0000gn/T/numbers.log
    16:35:05.028 [nioEventLoopGroup-8-1] WARN  io.netty.channel.DefaultChannelPipeline - An exception was thrown by a user handler's exceptionCaught() method while handling the following exception:
    com.github.travishaagen.server.TerminateServerException

    Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.383 sec

    Results :

    Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

## Running the Server

To install and run the server, execute the following commands in the root project folder,

    mvn clean install

    java -server -Xms1024m -Xmx1024m \
      -cp server/target/lib/*:server/target/server-1.0-SNAPSHOT.jar \
      com.github.travishaagen.server.DigitsServer

You can optionally specify an alternate port and/or directory to save the numbers.log file to, using the following JVM arguments,

    -Dserver.port=4000
    -Djournal.directory=/mydir

For load testing, the busy-wait strategy is the most efficient for consuming messages from the ring buffer,

    -Djournal.waitStrategy=Busy

A load-test client can be run on one or more separate machines with the following, where 127.0.0.1 should be replaced
with the server's hostname or IP address,

    java -Xms256m -Xms256m -cp client/target/lib/*:client-1.0-SNSHOT.jar \
      com.github.travishaagen.client.DigitsClient 127.0.0.1

To test maximum performance possible, this load test sends the same message repeatedly and has a much tighter event-loop code path,

    java -Xms256m -Xms256m -cp client/target/lib/*:client-1.0-SNSHOT.jar \
      com.github.travishaagen.client.LoadTestApp 127.0.0.1
