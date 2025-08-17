package xyz.jxmm.screenshare.ui;

import xyz.jxmm.screenshare.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {
    private User loggedInUser;

    public MainFrame(User user) {
        this.loggedInUser = user;
        
        setTitle("主界面 - ScreenShare");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        
        initComponents();
        
        // 添加窗口关闭事件监听器
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });
    }
    
    private void initComponents() {
        // 创建主面板
        JPanel panel = new JPanel(new BorderLayout());
        
        // 创建顶部面板显示欢迎信息
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel welcomeLabel = new JLabel("欢迎, " + loggedInUser.getNickname() + "!");
        welcomeLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        topPanel.add(welcomeLabel);
        panel.add(topPanel, BorderLayout.NORTH);
        
        // 创建中央面板显示提示信息
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        JLabel successLabel = new JLabel("登录成功！");
        successLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(successLabel, gbc);
        
        JLabel infoLabel = new JLabel("您已成功登录到 ScreenShare 系统");
        infoLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        gbc.gridy = 1;
        centerPanel.add(infoLabel, gbc);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        // 创建底部面板显示退出按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton logoutButton = new JButton("退出登录");
        logoutButton.addActionListener(e -> {
            confirmLogout();
        });
        bottomPanel.add(logoutButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(panel);
    }
    
    private void confirmExit() {
        int option = JOptionPane.showConfirmDialog(
            this,
            "确定要退出程序吗？",
            "确认退出",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }
    
    private void confirmLogout() {
        int option = JOptionPane.showConfirmDialog(
            this,
            "确定要退出登录吗？",
            "确认退出",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (option == JOptionPane.YES_OPTION) {
            dispose();
            // 退出登录返回到登录界面
            SwingUtilities.invokeLater(() -> {
                new LoginFrame().setVisible(true);
            });
        }
    }
}