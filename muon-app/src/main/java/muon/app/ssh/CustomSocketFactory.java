/**
 *
 */
package muon.app.ssh;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author subhro
 *
 */
public class CustomSocketFactory extends SocketFactory {

    private final String proxyHost;
    private final String proxyUser;
    private final String proxyPass;
    private final int proxyPort;
    private final Proxy.Type proxyType;

    /**
     * @param proxyHost
     * @param proxyPort
     * @param proxyUser
     * @param proxyPass
     */
    public CustomSocketFactory(String proxyHost, int proxyPort,
                               String proxyUser, String proxyPass, Proxy.Type proxyType) {
        super();
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUser = proxyUser;
        this.proxyPass = proxyPass;
        this.proxyType = proxyType;
    }

    public static final int getResponseCode(String statusLine) {
        String[] arr = statusLine.split(" ");
        if (arr.length < 2)
            return 400;
        return Integer.parseInt(arr[1]);
    }

    @Override
    public Socket createSocket(String host, int port)
            throws IOException {
        return this.createSocket(InetAddress.getByName(host), port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost,
                               int localPort) throws IOException {
        return this.createSocket(InetAddress.getByName(host), port, localHost,
                localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return this.createSocket(host, port, null, 0);
    }

    @Override
    public Socket createSocket(InetAddress address, int port,
                               InetAddress localAddress, int localPort) throws IOException {
        Socket socket = this.createSocket();
        if (localAddress != null) {
            socket.bind(new InetSocketAddress(localAddress, localPort));
        }
        if (address != null) {
            socket.connect(new InetSocketAddress(address, port));
        }

        if (this.proxyType == Proxy.Type.HTTP && proxyUser != null) {
            connectToProxy(socket);
        }

        return socket;
    }

    @Override
    public Socket createSocket() throws IOException {
        Proxy proxy = Proxy.NO_PROXY;
        if (this.proxyType == Proxy.Type.SOCKS) {
            proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(proxyHost, proxyPort));
        } else if (this.proxyType == Proxy.Type.HTTP) {
            if (proxyUser == null || proxyUser.length() < 1) {
                proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxyHost, proxyPort));
            }
        }

        Socket socket = new Socket(proxy);
        return socket;
    }

    private void connectToProxy(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        StringBuilder requestHeaders = new StringBuilder();
        requestHeaders
                .append("HTTP " + proxyHost + ":" + proxyPort + " HTTP/1.1\r\n")
                .append("Host: " + proxyHost + ":" + proxyPort + "\r\n");
        String proxyAuth = getBasicAuthStr();
        if (proxyAuth != null) {
            requestHeaders
                    .append("Proxy-Authorization: basic " + proxyAuth + "\r\n");
        }
        requestHeaders.append("\r\n");
        out.write(requestHeaders.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();

        String statusLine = readLine(in);
        if (statusLine == null) {
            socket.close();
            throw new IOException("Proxy sent blank response");
        }

        int responseCode = getResponseCode(statusLine);
        if (responseCode < 200 && responseCode >= 300) {
            socket.close();
            throw new IOException("Invalid response code: " + responseCode);
        }

        while (true) {
            String line = readLine(in);
            if (line.length() < 1)
                break;
        }
    }

    private String getBasicAuthStr() {
        if (proxyUser != null && proxyUser.length() > 0) {
            return (Base64.getEncoder().encodeToString(
                    (proxyUser + ":" + (proxyPass == null ? "" : proxyPass))
                            .getBytes(StandardCharsets.UTF_8)));
        }
        return null;
    }

    public final String readLine(InputStream in) throws IOException {
        StringBuilder buf = new StringBuilder();
        while (true) {
            int x = in.read();
            if (x == -1)
                throw new IOException(
                        "Unexpected EOF while reading header line");
            if (x == '\n')
                return buf.toString();
            if (x != '\r')
                buf.append((char) x);
        }
    }

}
