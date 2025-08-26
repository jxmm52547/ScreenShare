package xyz.jxmm.screenshare.server;

import xyz.jxmm.screenshare.model.User;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server network utility based on screego's design principles:
 * 1. Separation of control and streaming channels
 * 2. Efficient message routing
 * 3. Proper resource management
 * 4. Clean API
 */
public class ServerNetworkUtil {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 9999;
    private static final int STREAM_PORT = 10002;

    private static Socket controlSocket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static final CopyOnWriteArrayList<MessageListener> listeners = new CopyOnWriteArrayList<>();

    // 用于存储当前共享列表
    private static final AtomicReference<List<String>> currentShares = new AtomicReference<>();
    
    // 用于存储登录状态
    private static final AtomicBoolean loggedIn = new AtomicBoolean(false);

    // 流相关
    private static Socket viewerStreamSocket;
    private static Socket publisherStreamSocket;
    private static OutputStream publisherStreamOutput;

    public interface MessageListener {
        void onMessage(String message);
    }

    public static void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }

    public static void removeMessageListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners(String message) {
        for (MessageListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean login(String username) {
        try {
            controlSocket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(controlSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

            // 用于等待登录响应的同步机制
            CountDownLatch loginLatch = new CountDownLatch(1);
            AtomicBoolean loginSuccess = new AtomicBoolean(false);

            // 启动接收消息的线程
            Thread receiverThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        // 减少不必要的日志输出，避免SHARES消息刷屏
                        if (!message.startsWith("SHARES ")) {
                            System.out.println("[ServerNetworkUtil] 收到服务器消息: " + message);
                        }
                        
                        // 处理登录响应
                        if (message.startsWith("LOGIN_OK") || message.startsWith("LOGIN_FAILED")) {
                            if (message.startsWith("LOGIN_OK")) {
                                loginSuccess.set(true);
                                loggedIn.set(true);
                            }
                            loginLatch.countDown(); // 通知登录方法可以继续执行
                            // 登录响应消息也通知给监听器
                            notifyListeners(message);
                            continue; // 不再传递给其他监听器
                        }
                        
                        // 处理观看响应
                        if (message.startsWith("VIEW_ACCEPTED") || message.startsWith("VIEW_DENIED")) {
                            if (message.startsWith("VIEW_ACCEPTED")) {
                                viewResult.set("VIEW_ACCEPTED");
                            } else {
                                viewResult.set("VIEW_DENIED");
                            }
                            // 观看响应消息也通知给监听器
                            notifyListeners(message);
                            continue; // 不再传递给其他监听器
                        }
                        
                        // 处理共享列表更新消息
                        if (message.startsWith("SHARES ")) {
                            String sharesStr = message.substring(7);
                            if (sharesStr.isEmpty()) {
                                currentShares.set(List.of());
                            } else {
                                currentShares.set(List.of(sharesStr.split(",")));
                            }
                        }
                        
                        notifyListeners(message);
                    }
                } catch (IOException e) {
                    System.err.println("[ServerNetworkUtil] 接收消息线程异常: " + e.getMessage());
                    // 如果发生异常，也释放登录等待
                    loginLatch.countDown();
                }
            }, "ServerNetworkUtil-Receiver");
            receiverThread.setDaemon(true);
            receiverThread.start();

            // 发送登录命令
            out.println("LOGIN " + username);
            
            // 等待登录响应，最多等待5秒
            boolean loginResponseReceived = loginLatch.await(5, TimeUnit.SECONDS);
            
            if (!loginResponseReceived) {
                System.err.println("[ServerNetworkUtil] 登录超时");
                return false;
            }
            
            System.out.println("[ServerNetworkUtil] 登录结果: " + (loginSuccess.get() ? "成功" : "失败"));
            return loginSuccess.get();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isLoggedIn() {
        return loggedIn.get();
    }

    public static List<String> getSharedScreens() {
        // 发送GET_SHARES命令请求最新的共享列表
        out.println("GET_SHARES");
        
        // 直接返回当前存储的共享列表，无需等待
        // 因为共享列表更新应该通过服务器广播消息实时处理
        return currentShares.get();
    }

    public static boolean startShare(String username, String password, Rectangle captureRect, boolean shareAudio, int fps, Dimension targetResolution) throws IOException {
        out.println("START_SHARE " + password);
        return true;
    }

    public static boolean stopShare(String username) throws IOException {
        out.println("STOP_SHARE");
        return true;
    }

    // 用于存储观看请求的结果
    private static final AtomicReference<String> viewResult = new AtomicReference<>();

    public static boolean canViewShare(String targetUser, String password) throws IOException {
        // 清空之前的结果
        viewResult.set(null);
        
        // 发送VIEW_SHARE命令
        out.println("VIEW_SHARE " + targetUser + " " + password);
        
        // 等待服务器响应，最多等待5秒
        long startTime = System.currentTimeMillis();
        while (viewResult.get() == null && (System.currentTimeMillis() - startTime) < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        String result = viewResult.get();
        if (result == null) {
            System.err.println("[ServerNetworkUtil] 观看请求超时");
            return false;
        }
        
        System.out.println("[ServerNetworkUtil] 观看请求结果: " + result);
        return "VIEW_ACCEPTED".equals(result) || result.startsWith("VIEW_ACCEPTED ");
    }

    public static void leaveView(String targetUser) {
        out.println("LEAVE_VIEW " + targetUser);
    }

    // 流相关方法
    public static boolean connectAsViewerToStreamServer(String host, int port, int timeoutMs) {
        try {
            viewerStreamSocket = new Socket();
            viewerStreamSocket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            System.out.println("[ServerNetworkUtil] 成功连接到流服务器: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("[ServerNetworkUtil] 无法连接到流服务器: " + e.getMessage());
            return false;
        }
    }

    public static InputStream getViewerStreamInput() {
        try {
            return viewerStreamSocket != null ? viewerStreamSocket.getInputStream() : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void disconnectViewerStream() {
        if (viewerStreamSocket != null) {
            try {
                viewerStreamSocket.close();
            } catch (IOException ignored) {
            }
            viewerStreamSocket = null;
        }
    }

    public static boolean connectAsPublisherToStreamServer(String host, int port, int timeoutMs) {
        try {
            publisherStreamSocket = new Socket();
            publisherStreamSocket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            publisherStreamOutput = publisherStreamSocket.getOutputStream();
            System.out.println("[ServerNetworkUtil] 成功连接到流服务器作为发布者: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("[ServerNetworkUtil] 无法作为发布者连接到流服务器: " + e.getMessage());
            return false;
        }
    }

    public static OutputStream getPublisherStreamOutput() {
        return publisherStreamOutput;
    }

    public static void disconnectPublisherStream() {
        if (publisherStreamSocket != null) {
            try {
                publisherStreamSocket.close();
            } catch (IOException ignored) {
            }
            publisherStreamSocket = null;
            publisherStreamOutput = null;
        }
    }

    public static void disconnect() {
        try {
            loggedIn.set(false);
            if (controlSocket != null) {
                controlSocket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}