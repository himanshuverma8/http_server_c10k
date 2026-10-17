package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * V4: one virtual thread per connection.
 *
 * Measured: idle connections (nc clients that connect and send nothing)
 *
 *   State                  Threads   RSS (real memory)
 *   Before any clients          18   43,096 KB (~42 MB)
 *   1,000 idle clients          29   63,880 KB (~62 MB)
 *   Difference                 +11   +20,784 KB (~20 MB)
 *
 *   Real memory per idle connection: ~21 KB (20,784 KB / 1,000).
 *   All 1,000 connections were established when measured (ss showed 1000).
 *   Only +11 OS threads: carrier threads (up to 1 per core) plus the epoll
 *   poller. The 1,000 virtual threads are JVM objects, not kernel threads.
 *   Disk: none. This server does not read or write files.
 *
 *   Compared with V1: ~134 KB and 1 OS thread per idle connection.
 *
 * What strace showed for one silent client (nc) and one curl:
 *   accept(5) = 6                      nc arrives, fd 6
 *   read(6) = -1 EAGAIN                no data: returns at once, no blocking
 *   epoll_ctl(8, ADD, 6, EPOLLIN)      JVM asks epoll to watch fd 6; virtual thread parks
 *   accept(5) = 7, read(7) = 77        curl served by the same carrier, no new clone
 *   epoll_pwait(8) -> fd 6 ready       nc sent "hi"; the poller wakes up
 *   read(6) = 3                        virtual thread resumes on a different carrier
 *   clone calls appeared only while the JVM set up its carriers and poller.
 *
 * How it was measured:
 *   PID=$(pgrep -f V4Virtual)
 *   ls /proc/$PID/task | wc -l     (threads)
 *   ps -o rss= -p $PID             (real memory in KB)
 *   for i in $(seq 1000); do nc 127.0.0.1 8080 & done
 *   ss -Htn state established '( sport = :8080 )' | wc -l
 *   strace -f -e trace=accept,clone,read,write,epoll_create1,epoll_ctl,epoll_pwait java -cp out server.V4Virtual
 *
 * Test conditions: Docker on macOS (Linux VM, aarch64), 8 vCPU, 3.9 GB RAM,
 * OpenJDK 21.0.12, measured on 2026-10-17. Measured with the executor created
 * inside the loop (one executor per connection).
 */
public class V4Virtual {
    private static final byte[] RESPONSE = (
        "HTTP/1.1 200 OK\r\n" +
        "Content-Length: 6\r\n" +
        "Connection: close\r\n" +
        "\r\n" +
        "hello\n"
    ).getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(8080)) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            System.out.println("V4 listening on port 8080");

            while (true) {
                Socket client = server.accept();
                executor.submit(() -> handle(client));
            }
        }
    }

    private static void handle(Socket client) {
        System.out.println("handling on " + Thread.currentThread());

        try (client) {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n == -1) {
                return;
            }
            System.out.println("--- read " + n + " bytes ---");
            System.out.print(new String(buf, 0, n, StandardCharsets.US_ASCII));

            out.write(RESPONSE);
            out.flush();
        } catch (IOException e) {
            System.out.println("client error: " + e.getMessage());
        }
    }
}
