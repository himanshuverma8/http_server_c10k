package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/*
 * V1: one thread per connection.
 *
 * Measured: idle connections (nc clients that connect and send nothing)
 *
 *   State                  Threads   RSS (real memory)
 *   Before any clients          18   43,092 KB  (~42 MB)
 *   ~770 idle clients          788   146,232 KB (~143 MB)
 *   Difference                +770   +103,140 KB (~101 MB)
 *
 *   Real memory per idle connection: ~134 KB (103,140 KB / 770).
 *   Each thread reserves a 1 MB stack, but only touched pages count as RSS.
 *   1,000 nc clients were started; 770 had connected when measured.
 *   Disk: none. This server does not read or write files.
 *
 * How it was measured:
 *   PID=$(pgrep -f V1ThreadPerConn)
 *   ls /proc/$PID/task | wc -l     (threads)
 *   ps -o rss= -p $PID             (real memory in KB)
 *   for i in $(seq 1000); do nc 127.0.0.1 8080 & done
 *
 * Test conditions: Docker on macOS (Linux VM, aarch64), 8 vCPU, 3.9 GB RAM,
 * OpenJDK 21.0.12, measured on 2026-10-16.
 */
public class V1ThreadPerConn {

    private static final byte[] RESPONSE = (
        "HTTP/1.1 200 OK\r\n" +
        "Content-Length: 6\r\n" +
        "Connection: close\r\n" +
        "\r\n" +
        "hello\n"
    ).getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(8080)) {
            System.out.println("V1 listening on port 8080");

            while (true) {
                Socket client = server.accept();
                new Thread(() -> handle(client)).start();
            }
        }
    }

    private static void handle(Socket client) {
        System.out.println("handling on " + Thread.currentThread().getName());

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
