package xyz.jxmm.screenshare.ui;

import org.bytedeco.javacv.FFmpegLogCallback;
import xyz.jxmm.screenshare.server.ServerNetworkUtil;
import xyz.jxmm.screenshare.stream.H264Viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.io.InputStream;

/**
 * Viewing frame based on screego's design principles:
 * 1. Efficient video rendering
 * 2. Proper aspect ratio handling
 * 3. Clean user interface
 * 4. Proper resource management
 */
public class ViewingFrame extends JFrame {
    private final String targetUser;
    private final ScreenPanel screenPanel;
    private final JSlider volumeSlider;
    private volatile boolean running = true;
    // optional H264 viewer instance when JavaCV is used
    private xyz.jxmm.screenshare.stream.H264Viewer h264Viewer = null;

    // 静态初始化块中设置 FFmpeg 日志回调
    static {
        try {
            FFmpegLogCallback.set();
        } catch (Throwable t) {
            System.err.println("[ViewingFrame] 无法初始化FFmpeg日志回调: " + t.getMessage());
        }
    }

    public ViewingFrame(String targetUser) {
        System.out.println("[ViewingFrame] 创建ViewingFrame实例，目标用户: " + targetUser);
        this.targetUser = targetUser;

        setTitle("正在观看 " + targetUser + " 的屏幕");
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

    // 屏幕显示区域：自绘面板
    screenPanel = new ScreenPanel();
    mainPanel.add(new JScrollPane(screenPanel), BorderLayout.CENTER);

        // 控制面板
        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("音量:"));
        volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.addChangeListener(e -> {
            // 在这里处理音量变化
            float volume = volumeSlider.getValue() / 100.0f;
            // audioPlayback.setVolume(volume); // 假设有这样一个方法
            System.out.println("音量调整为: " + volume);
        });
        controlPanel.add(volumeSlider);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        add(mainPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopViewing();
            }
        });

    // 注册消息监听器来接收服务器推送的屏幕数据
    ServerNetworkUtil.addMessageListener(messageListener);
    System.out.println("[ViewingFrame] 已注册消息监听器");
    }

    private void stopViewing() {
        running = false;
        ServerNetworkUtil.leaveView(targetUser);
        ServerNetworkUtil.removeMessageListener(messageListener);
        // stop H264Viewer if present
        if (h264Viewer != null) {
            try { h264Viewer.stop(); } catch (Exception ignored) {}
            h264Viewer = null;
        }
        dispose();
    }
    private int viewFrameCount = 0;
    private long lastViewStatTime = System.currentTimeMillis();
    
    private final xyz.jxmm.screenshare.server.ServerNetworkUtil.MessageListener messageListener = new xyz.jxmm.screenshare.server.ServerNetworkUtil.MessageListener() {
        @Override
        public void onMessage(String message) {
            if (message == null || !running) return;
            
            if (message.startsWith("VIEW_ACCEPTED")) {
                System.out.println("[ViewingFrame] 收到 VIEW_ACCEPTED 消息: " + message);
                // 启动H264Viewer来处理视频流
                SwingUtilities.invokeLater(() -> {
                    try {
                        System.out.println("[ViewingFrame] 开始连接到流服务器...");
                        // 确保 viewerStream 已连接
                        boolean ok = xyz.jxmm.screenshare.server.ServerNetworkUtil.connectAsViewerToStreamServer("localhost", 10002, 5000);
                        System.out.println("[ViewingFrame] 连接流服务器结果: " + ok);
                        if (!ok) {
                            throw new RuntimeException("无法连接到流服务器");
                        }
                        
                        java.io.InputStream in = xyz.jxmm.screenshare.server.ServerNetworkUtil.getViewerStreamInput();
                        System.out.println("[ViewingFrame] 获取到输入流: " + (in != null));
                        if (in == null) {
                            throw new RuntimeException("无法获取输入流");
                        }

                        try {
                            xyz.jxmm.screenshare.stream.H264Viewer viewer = new xyz.jxmm.screenshare.stream.H264Viewer(in, img -> {
                                // System.out.println("[ViewingFrame] 接收到解码后的图像帧");
                                if (img != null) {
                                    updateScreen(img);
                                    viewFrameCount++;
                                    long now = System.currentTimeMillis();
                                    if (now - lastViewStatTime >= 1000) {
                                        System.out.println("[ViewingFrame] 当前接收帧率(H264): " + viewFrameCount + " fps");
                                        viewFrameCount = 0;
                                        lastViewStatTime = now;
                                    }
                                } else {
                                    System.out.println("[ViewingFrame] 接收到空图像帧");
                                }
                            });
                            h264Viewer = viewer;
                            System.out.println("[ViewingFrame] H264Viewer创建完成");
                        } catch (Throwable t) {
                            System.err.println("[ViewingFrame] H264Viewer创建失败: " + t.getMessage());
                            t.printStackTrace();
                        }
                    } catch (Throwable t) {
                        System.err.println("[ViewingFrame] 初始化H264Viewer失败: " + t.getMessage());
                        t.printStackTrace();
                    }
                });
            } else if (message.startsWith("SCREEN_DATA")) {
                // 如果仍在使用旧的SCREEN_DATA机制，则处理它
                // 但优先使用H264流
                if (h264Viewer == null) {
                    String imageData = message.substring(12);
                    try {
                        byte[] imageBytes = Base64.getDecoder().decode(imageData);
                        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                        BufferedImage image = ImageIO.read(bais);
                        if (image != null) {
                            updateScreen(image);
                            viewFrameCount++;
                            long now = System.currentTimeMillis();
                            if (now - lastViewStatTime >= 1000) {
                                System.out.println("[ViewingFrame] 当前接收帧率(SCREEN_DATA): " + viewFrameCount + " fps");
                                viewFrameCount = 0;
                                lastViewStatTime = now;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            } else if (message.startsWith("SHARE_STOPPED")) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(ViewingFrame.this, "对方已停止共享。", "通知", JOptionPane.INFORMATION_MESSAGE);
                    stopViewing();
                });
            }
        }
    };

    private void updateScreen(BufferedImage image) {
        SwingUtilities.invokeLater(() -> {
            if (image != null) {
                // 直接将图像交给自绘面板，面板会高效绘制并保持比例
                screenPanel.setImage(image);
            }
        });
    }

    // 自绘面板用于高效渲染收到的 BufferedImage，避免每帧创建 ImageIcon 或调用 getScaledInstance
    private static class ScreenPanel extends JPanel {
        private volatile BufferedImage current;

        public ScreenPanel() {
            setBackground(Color.BLACK);
            setDoubleBuffered(true);
        }

        public void setImage(BufferedImage img) {
            this.current = img;
            // 合并帧并触发重绘
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img = current;
            if (img == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int panelW = getWidth();
            int panelH = getHeight();
            int imgW = img.getWidth();
            int imgH = img.getHeight();
            double panelRatio = (double) panelW / panelH;
            double imgRatio = (double) imgW / imgH;
            int drawW = panelW, drawH = panelH;
            if (imgRatio > panelRatio) {
                drawW = panelW;
                drawH = (int) (panelW / imgRatio);
            } else {
                drawH = panelH;
                drawW = (int) (panelH * imgRatio);
            }
            int x = (panelW - drawW) / 2;
            int y = (panelH - drawH) / 2;
            g2.drawImage(img, x, y, drawW, drawH, null);
            g2.dispose();
        }
    }
}