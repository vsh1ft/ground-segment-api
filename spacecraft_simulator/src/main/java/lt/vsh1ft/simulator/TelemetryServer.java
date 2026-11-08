package lt.vsh1ft.simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TelemetryServer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(TelemetryServer.class.getName());

    private final ServerSocket serverSocket;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    TelemetryServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    void start() {
        Thread.ofPlatform().daemon().name("tm-accept").start(this::acceptClients);
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    void broadcast(byte[] packet) {
        for (final var client : clients) {
            try {
                client.getOutputStream().write(packet);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.INFO, "Client {0} disconnected: {1}",
                        client.getRemoteSocketAddress(), e.getMessage());
                drop(client);
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        clients.forEach(this::drop);
    }

    private void acceptClients() {
        try {
            while (true) {
                final var client = serverSocket.accept();
                client.setTcpNoDelay(true);
                clients.add(client);
                LOG.log(System.Logger.Level.INFO, "Client {0} connected", client.getRemoteSocketAddress());
            }
        } catch (SocketException e) {
            LOG.log(System.Logger.Level.DEBUG, "Stopped accepting clients: {0}", e.getMessage());
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Accepting clients failed", e);
        }
    }

    private void drop(Socket client) {
        clients.remove(client);
        try {
            client.close();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Closing client failed: {0}", e.getMessage());
        }
    }
}
