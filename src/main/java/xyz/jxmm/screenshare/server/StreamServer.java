package xyz.jxmm.screenshare.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple TCP stream server: accepts publisher connections and viewer connections on the same port.
 * Workflow: client announces intention over control channel, then opens TCP to this port.
 * 
 * Based on screego's design principles:
 * 1. Separation of control and streaming channels
 * 2. Publisher/viewer matching through announcement mechanism
 * 3. Efficient stream forwarding
 */
public class StreamServer implements Runnable {
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = true;

    // pending publishers announced by control channel (username order)
    private final ConcurrentLinkedQueue<String> pendingPublishers = new ConcurrentLinkedQueue<>();
    // pending viewer targets announced by control channel (targetUser order)
    private final ConcurrentLinkedQueue<String> pendingViewerTargets = new ConcurrentLinkedQueue<>();

    // active publisher socket per username
    private final Map<String, Socket> publisherSockets = new ConcurrentHashMap<>();
    // viewers per publisher username
    private final Map<String, CopyOnWriteArrayList<Socket>> viewers = new ConcurrentHashMap<>();

    private static StreamServer instance;

    public static synchronized StreamServer getInstance(int port) {
        if (instance == null) {
            instance = new StreamServer(port);
        }
        return instance;
    }

    public static synchronized StreamServer getInstance() {
        return instance;
    }

    public StreamServer(int port) {
        this.port = port;
    }

    public void addPendingPublisher(String username) {
        pendingPublishers.add(username);
        System.out.println("[StreamServer] Pending publisher announced: " + username);
    }

    public void addPendingViewerTarget(String targetUser) {
        pendingViewerTargets.add(targetUser);
        System.out.println("[StreamServer] Pending viewer announced for target: " + targetUser);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[StreamServer] Listening for streams on port " + port);
            while (running) {
                Socket sock = serverSocket.accept();
                System.out.println("[StreamServer] Incoming TCP connection from " + sock.getRemoteSocketAddress());
                // decide assignment
                String publisher = pendingPublishers.poll();
                if (publisher != null) {
                    assignPublisherSocket(publisher, sock);
                    continue;
                }
                String target = pendingViewerTargets.poll();
                if (target != null) {
                    assignViewerSocket(target, sock);
                    continue;
                }
                // no pending mapping, close
                System.out.println("[StreamServer] No pending mapping for incoming stream, closing connection.");
                try { sock.close(); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        } finally {
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void assignPublisherSocket(String username, Socket sock) {
        System.out.println("[StreamServer] Assigning publisher socket for " + username);
        publisherSockets.put(username, sock);
        viewers.putIfAbsent(username, new CopyOnWriteArrayList<>());
        // start forwarding thread for this publisher
        Thread t = new Thread(() -> forwardFromPublisher(username, sock), "StreamServer-Forward-" + username);
        t.setDaemon(true);
        t.start();
    }

    private void assignViewerSocket(String targetUser, Socket sock) {
        System.out.println("[StreamServer] Adding viewer for target " + targetUser + ", from " + sock.getRemoteSocketAddress());
        viewers.putIfAbsent(targetUser, new CopyOnWriteArrayList<>());
        viewers.get(targetUser).add(sock);
    }

    private void forwardFromPublisher(String username, Socket pubSock) {
        try (InputStream in = pubSock.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long lastStatTime = System.currentTimeMillis();
            int frameCount = 0;
            while ((read = in.read(buffer)) != -1) {
                List<Socket> list = viewers.get(username);
                if (list == null || list.isEmpty()) {
                    // swallow data
                    continue;
                }
                long t0 = System.currentTimeMillis();
                for (Socket v : list) {
                    try {
                        OutputStream os = v.getOutputStream();
                        os.write(buffer, 0, read);
                        os.flush();
                    } catch (IOException e) {
                        System.out.println("[StreamServer] Error forwarding to viewer " + v.getRemoteSocketAddress() + ": " + e.getMessage());
                        try { v.close(); } catch (IOException ignored) {}
                        list.remove(v);
                    }
                }
                long t1 = System.currentTimeMillis();
                frameCount++;
                long now = System.currentTimeMillis();
                if (now - lastStatTime >= 1000) {
                    System.out.println("[StreamServer] 转发帧率: " + frameCount + " fps, viewers=" + list.size() + ", 写入耗时: " + (t1-t0) + "ms");
                    frameCount = 0;
                    lastStatTime = now;
                }
            }
        } catch (IOException e) {
            System.out.println("[StreamServer] Publisher " + username + " stream ended: " + e.getMessage());
        } finally {
            // cleanup
            try { pubSock.close(); } catch (IOException ignored) {}
            publisherSockets.remove(username);
            // notify viewers by closing their sockets
            List<Socket> list = viewers.get(username);
            if (list != null) {
                for (Socket v : list) {
                    try { v.close(); } catch (IOException ignored) {}
                }
                list.clear();
            }
            viewers.remove(username);
            System.out.println("[StreamServer] Publisher " + username + " cleanup completed.");
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }
}