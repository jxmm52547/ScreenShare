package xyz.jxmm.screenshare.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server application based on screego's design principles:
 * 1. Separation of control and streaming services
 * 2. Proper resource management
 * 3. Scalable client handling
 */
public class ServerApplication {
    // 存储所有客户端的handler
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    // 存储正在共享屏幕的用户和密码
    private static final ConcurrentHashMap<String, String> sharingUsers = new ConcurrentHashMap<>();
    // 存储每个共享者的观看者
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, ClientHandler>> viewers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // start stream server for binary H264 streams
        try {
            StreamServer streamServer = StreamServer.getInstance(10002);
            Thread streamThread = new Thread(streamServer, "StreamServer-Main");
            streamThread.setDaemon(true);
            streamThread.start();
            System.out.println("StreamServer started on port 10002");
        } catch (Exception e) {
            System.err.println("Failed to start StreamServer: " + e.getMessage());
        }

        try (ServerSocket serverSocket = new ServerSocket(9999)) {
            System.out.println("服务器已启动，等待客户端连接... (控制端口9999)");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[ServerApplication] 新的客户端连接: " + clientSocket.getRemoteSocketAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients, sharingUsers, viewers);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("服务器异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
}