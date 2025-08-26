package xyz.jxmm.screenshare.stream;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * H264 Publisher based on screego's design principles:
 * 1. Efficient H264 encoding with FFmpeg
 * 2. FLV container format for streaming
 * 3. Low latency configuration
 * 4. Proper resource management
 */
public class H264Publisher {
    private final FFmpegFrameRecorder recorder;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private final Socket socket;
    private final OutputStream out;

    public H264Publisher(String host, int port, int width, int height, int fps) throws Exception {
        System.out.println("[H264Publisher] 初始化H264Publisher...");
        // 设置 FFmpeg 日志回调，避免 avformat_write_header 错误
        FFmpegLogCallback.set();
        System.out.println("[H264Publisher] FFmpeg日志回调已设置");
        
        // 先创建套接字连接
        this.socket = new Socket(host, port);
        System.out.println("[H264Publisher] 套接字连接已创建: " + host + ":" + port);
        this.out = socket.getOutputStream();
        System.out.println("[H264Publisher] 获取输出流成功");
        
        // 创建录制器并设置为使用OutputStream
        recorder = new FFmpegFrameRecorder(out, width, height);
        System.out.println("[H264Publisher] FFmpegFrameRecorder已创建");
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("flv"); // FLV格式支持流输出
        recorder.setFrameRate(fps);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoOption("tune", "zerolatency");
        recorder.setVideoBitrate(2_000_000); // 2Mbps
        recorder.setVideoOption("threads", "2");
        recorder.setGopSize(fps * 2);
        recorder.setVideoQuality(0); // 最高质量
        System.out.println("[H264Publisher] FFmpegFrameRecorder参数已设置");
        recorder.start();
        System.out.println("[H264Publisher] FFmpegFrameRecorder已启动");
        System.out.println("[H264Publisher] H264Publisher初始化完成");
    }

    public synchronized void publishFrame(BufferedImage img) throws Exception {
        if (img == null) {
            System.out.println("[H264Publisher] 尝试发布空图像帧");
            return;
        }
        
        // System.out.println("[H264Publisher] 开始编码帧...");
        long t0 = System.currentTimeMillis();
        Frame frame = converter.convert(img);
        if (frame == null) {
            System.out.println("[H264Publisher] 图像转换结果为空");
            return;
        }
        // System.out.println("[H264Publisher] 图像转换完成");
        recorder.record(frame);
        // System.out.println("[H264Publisher] 帧记录完成");
        long t1 = System.currentTimeMillis();
        // System.out.println("[H264Publisher] encode+send time=" + (t1-t0) + "ms");
    }

    public synchronized void stop() {
        System.out.println("[H264Publisher] 停止H264Publisher...");
        try {
            if (recorder != null) recorder.stop();
        } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        System.out.println("[H264Publisher] H264Publisher已停止");
    }
}