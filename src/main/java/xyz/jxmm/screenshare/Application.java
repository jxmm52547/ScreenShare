package xyz.jxmm.screenshare;

import xyz.jxmm.screenshare.ui.LoginFrame;

import javax.swing.*;

public class Application {
    public static void main(String[] args) {
        // 设置外观为系统默认
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 在事件调度线程中启动GUI应用程序
        SwingUtilities.invokeLater(() -> {
            new LoginFrame().setVisible(true);
        });
    }
}