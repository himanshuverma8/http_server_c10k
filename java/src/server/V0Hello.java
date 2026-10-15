package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class V0Hello {

    private static final byte[] RESPONSE = (
        "HTTP/1.1 200 OK\r\n" +
        "Content-Length: 5\r\n" +
        "Connection: close\r\n" +
        "\r\n" +
        "hello\n"
    ).getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) throws IOException {
        // socket, bind, listen: open the shop on 8080
        try (ServerSocket server = new ServerSocket(8080)) {
            System.out.println("Listening on port 8080");

            while (true) {
                // accept: take the next customer off the bench (blocks if empty)
                try (Socket client = server.accept()) {
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();

                    // read: pick up the order slip (blocks until bytes arrive)
                    byte[] buf = new byte[4096];
                    int n = in.read(buf);
                    if (n == -1) {
                        continue; // client closed without sending anything
                    }
                    System.out.println("--- read " + n + " bytes ---");
                    System.out.print(new String(buf, 0, n, StandardCharsets.US_ASCII));

                    // write: put the response on the tray
                    out.write(RESPONSE);
                    out.flush();
                } catch (IOException e) {
                    // one bad client must not kill the whole server
                    System.out.println("client error: " + e.getMessage());
                }
                // close happens here automatically: customer leaves, table cleared
            }
        }
    }
}
