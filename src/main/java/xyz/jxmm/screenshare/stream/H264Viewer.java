package xyz.jxmm.screenshare.stream;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * H264 Viewer based on screego's design principles:
 * 1. Efficient H264 decoding with FFmpeg
 * 2. FLV container format for streaming
 * 3. Proper resource management
 * 4. Asynchronous frame processing
 */
public class H264Viewer {
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private volatile boolean running = true;
    private final FFmpegFrameGrabber grabber;
    private Thread readerThread;

    public H264Viewer(InputStream in, Consumer<BufferedImage> frameConsumer) throws Exception {
        System.out.println("[H264Viewer] 初始化H264Viewer...");
        // 设置 FFmpeg 日志回调
        FFmpegLogCallback.set();
        System.out.println("[H264Viewer] FFmpeg日志回调已设置");
        
        // 直接使用输入流创建帧抓取器
        grabber = new FFmpegFrameGrabber(in);
        grabber.setFormat("flv"); // 与发布端保持一致，使用FLV格式
        System.out.println("[H264Viewer] FFmpegFrameGrabber已创建，格式设置为FLV");
        grabber.start();
        System.out.println("[H264Viewer] FFmpegFrameGrabber已启动");
        
        readerThread = new Thread(() -> {
            System.out.println("[H264Viewer] 开始读取视频流...");
            int decodeFrameCount = 0;
            long lastDecodeStatTime = System.currentTimeMillis();
            try {
                while (running) {
                    long t0 = System.currentTimeMillis();
                    org.bytedeco.javacv.Frame frame = grabber.grabImage();
                    long t1 = System.currentTimeMillis();
                    if (frame == null) {
                        // 流结束
                        System.out.println("[H264Viewer] 流结束，没有更多帧可读取");
                        break;
                    }
                    BufferedImage img = converter.convert(frame);
                    if (img != null) {
                        frameConsumer.accept(img);
                    } else {
                        System.out.println("[H264Viewer] 转换得到空图像");
                    }
                    decodeFrameCount++;
                    long now = System.currentTimeMillis();
                    if (now - lastDecodeStatTime >= 1000) {
                        System.out.println("[H264Viewer] 解码帧率: " + decodeFrameCount + " fps, 解码耗时: " + (t1-t0) + "ms");
                        decodeFrameCount = 0;
                        lastDecodeStatTime = now;
                    }
                }
            } catch (Exception e) {
                System.err.println("[H264Viewer] 解码过程中出现异常: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("[H264Viewer] 读取视频流线程结束");
        }, "H264Viewer-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
        System.out.println("[H264Viewer] H264Viewer初始化完成");
    }

    public void stop() {
        System.out.println("[H264Viewer] 停止H264Viewer...");
        running = false;
        try { 
            if (readerThread != null) readerThread.interrupt(); 
        } catch (Exception ignored) {}
        try {
            if (grabber != null) grabber.stop();
        } catch (Exception ignored) {}
        System.out.println("[H264Viewer] H264Viewer已停止");
    }
}