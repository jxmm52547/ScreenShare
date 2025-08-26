package xyz.jxmm.screenshare.server;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client handler based on screego's design principles:
 * 1. Handle individual client connections
 * 2. Process client commands
 * 3. Manage screen sharing state
 * 4. Forward screen data to viewers
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ConcurrentHashMap<String, ClientHandler> clients;
    private final ConcurrentHashMap<String, String> sharingUsers;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ClientHandler>> viewers;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, ConcurrentHashMap<String, ClientHandler> clients,
                         ConcurrentHashMap<String, String> sharingUsers,
                         ConcurrentHashMap<String, ConcurrentHashMap<String, ClientHandler>> viewers) {
        this.clientSocket = socket;
        this.clients = clients;
        this.sharingUsers = sharingUsers;
        this.viewers = viewers;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                handleCommand(inputLine);
            }
        } catch (IOException e) {
            System.err.println("客户端处理异常: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split(" ", 3);
        String cmd = parts[0];

        switch (cmd) {
            case "LOGIN":
                System.out.println("[ClientHandler] 收到 LOGIN 命令: " + command);
                if (parts.length < 2) {
                    System.out.println("[ClientHandler] LOGIN 命令格式错误: " + command);
                    out.println("LOGIN_FAILED 格式错误");
                    break;
                }
                this.username = parts[1];
                clients.put(username, this);
                System.out.println("[ClientHandler] 用户 " + username + " 已登录。");
                out.println("LOGIN_OK " + username);
                
                // 用户登录时广播当前共享列表
                broadcastSharesList();
                break;
            case "GET_SHARES":
                out.println("SHARES " + String.join(",", sharingUsers.keySet()));
                break;
            case "START_SHARE":
                // 检查参数数量
                if (parts.length < 2) {
                    System.out.println("[ClientHandler] START_SHARE 命令格式错误: " + command);
                    out.println("SHARE_FAILED 格式错误");
                    break;
                }
                String password = parts[1];
                sharingUsers.put(username, password);
                viewers.put(username, new ConcurrentHashMap<>());
                System.out.println("用户 " + username + " 开始共享屏幕。");
                out.println("SHARE_STARTED");
                    // 广播新的共享列表给所有已连接客户端
                    broadcastSharesList();
                // notify StreamServer that this user will publish a binary stream soon
                try {
                    StreamServer ss = StreamServer.getInstance();
                    if (ss != null) ss.addPendingPublisher(username);
                } catch (Exception e) {
                    System.err.println("[ClientHandler] Failed to register pending publisher: " + e.getMessage());
                }
                break;
            case "STOP_SHARE":
                sharingUsers.remove(username);
                // 通知所有观看者停止
                if (viewers.containsKey(username)) {
                    for (ClientHandler viewer : viewers.get(username).values()) {
                        viewer.out.println("SHARE_STOPPED " + username);
                    }
                    viewers.remove(username);
                }
                System.out.println("用户 " + username + " 停止共享屏幕。");
                out.println("SHARE_STOPPED");
                    // 广播新的共享列表
                    broadcastSharesList();
                break;
            case "VIEW_SHARE":
                System.out.println("[ClientHandler] 收到 VIEW_SHARE 命令: " + command);
                // 检查参数数量
                if (parts.length < 3) {
                    System.out.println("[ClientHandler] VIEW_SHARE 命令格式错误: " + command);
                    out.println("VIEW_DENIED 格式错误");
                    break;
                }
                String targetUser = parts[1];
                String providedPassword = parts[2];
                System.out.println("[ClientHandler] 目标用户: " + targetUser + ", 提供密码: " + providedPassword);
                System.out.println("[ClientHandler] 当前共享用户列表: " + sharingUsers.keySet());
                System.out.println("[ClientHandler] 目标用户是否在共享列表中: " + sharingUsers.containsKey(targetUser));
                if (sharingUsers.containsKey(targetUser)) {
                    System.out.println("[ClientHandler] 目标用户密码: " + sharingUsers.get(targetUser));
                    System.out.println("[ClientHandler] 密码是否匹配: " + sharingUsers.get(targetUser).equals(providedPassword));
                }
                if (sharingUsers.containsKey(targetUser) && sharingUsers.get(targetUser).equals(providedPassword)) {
                    System.out.println("[ClientHandler] 密码验证成功");
                    viewers.get(targetUser).put(username, this);
                    out.println("VIEW_ACCEPTED " + targetUser);
                    System.out.println("用户 " + username + " 开始观看 " + targetUser + " 的屏幕。");
                    // notify StreamServer that this user will connect as a viewer for targetUser
                    try {
                        StreamServer ss = StreamServer.getInstance();
                        if (ss != null) ss.addPendingViewerTarget(targetUser);
                    } catch (Exception e) {
                        System.err.println("[ClientHandler] Failed to register pending viewer: " + e.getMessage());
                    }
                } else {
                    System.out.println("[ClientHandler] 密码验证失败，发送VIEW_DENIED");
                    out.println("VIEW_DENIED");
                }
                break;
            case "SCREEN_DATA":
                // 接收到屏幕数据，转发给所有观看者
                String data = parts.length > 1 ? parts[1] : null;
                if (viewers.containsKey(username)) {
                    int viewerCount = viewers.get(username).size();
                    System.out.println("[Server] 转发 SCREEN_DATA 来自 " + username + ", size(base64)=" + (data != null ? data.length() : 0) + ", viewers=" + viewerCount);
                    for (ClientHandler viewer : viewers.get(username).values()) {
                        viewer.out.println("SCREEN_DATA " + data);
                    }
                } else {
                    System.out.println("[Server] 收到 SCREEN_DATA 来自 " + username + " 但无观看者，忽略。");
                }
                break;
            case "LEAVE_VIEW":
                // 用户离开观看
                String target = parts.length > 1 ? parts[1] : null;
                if (target != null && viewers.containsKey(target)) {
                    viewers.get(target).remove(username);
                    System.out.println("用户 " + username + " 停止观看 " + target + " 的屏幕。");
                }
                break;
            default:
                System.out.println("未知命令: " + command);
                break;
        }
    }

    private void cleanup() {
        boolean wasSharing = sharingUsers.containsKey(username);
        
        try {
            if (username != null) {
                clients.remove(username);
                if (sharingUsers.containsKey(username)) {
                    sharingUsers.remove(username);
                    if (viewers.containsKey(username)) {
                        for (ClientHandler viewer : viewers.get(username).values()) {
                            viewer.out.println("SHARE_STOPPED " + username);
                        }
                        viewers.remove(username);
                    }
                }
                // 如果是观看者，则从观看列表中移除
                for (ConcurrentHashMap<String, ClientHandler> v : viewers.values()) {
                    v.remove(username);
                }
                System.out.println("用户 " + username + " 已清理资源。");
            }
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 如果用户之前在共享，则在清理后广播更新的共享列表
            if (wasSharing) {
                broadcastSharesList();
            }
        }
    }
    
    /**
     * 广播当前共享列表给所有已连接的客户端
     */
    private void broadcastSharesList() {
        String sharesMsg = "SHARES " + String.join(",", sharingUsers.keySet());
        System.out.println("[ClientHandler] 广播共享列表: " + sharesMsg);
        for (ClientHandler ch : clients.values()) {
            try {
                ch.out.println(sharesMsg);
            } catch (Exception e) {
                System.err.println("[ClientHandler] 向客户端 " + ch.username + " 发送共享列表失败: " + e.getMessage());
            }
        }
    }
}