package xyz.jxmm.screenshare.util;

import xyz.jxmm.screenshare.util.DatabaseUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class InvitationCodeManager extends JFrame {
    private JTextField usernameField;
    private JTextField codeField;
    private JTextField seedField;
    private JTextField encryptedCodeField;
    private JButton copyButton;

    public InvitationCodeManager() {
        setTitle("邀请码管理工具");
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
    }

    private void initComponents() {
        // 创建主面板
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // 用户名标签和输入框
        JLabel usernameLabel = new JLabel("用户名:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(usernameLabel, gbc);

        usernameField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(usernameField, gbc);

        // 邀请码标签和输入框
        JLabel codeLabel = new JLabel("邀请码:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(codeLabel, gbc);

        codeField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(codeField, gbc);

        // 种子标签和输入框
        JLabel seedLabel = new JLabel("加密种子:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(seedLabel, gbc);

        seedField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(seedField, gbc);

        // 加密后邀请码标签和输入框
        JLabel encryptedCodeLabel = new JLabel("加密后邀请码:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(encryptedCodeLabel, gbc);

        encryptedCodeField = new JTextField(15);
        encryptedCodeField.setEditable(false); // 设置为只读
        gbc.gridx = 1;
        gbc.gridy = 3;
        panel.add(encryptedCodeField, gbc);

        // 复制按钮
        copyButton = new JButton("复制");
        copyButton.setEnabled(false); // 初始状态禁用
        gbc.gridx = 2;
        gbc.gridy = 3;
        panel.add(copyButton, gbc);

        // 生成随机邀请码按钮
        JButton generateButton = new JButton("生成随机邀请码");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(generateButton, gbc);

        // 添加邀请码按钮
        JButton addButton = new JButton("添加邀请码");
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        panel.add(addButton, gbc);

        add(panel);

        // 添加事件监听器
        generateButton.addActionListener(new GenerateButtonListener());
        addButton.addActionListener(new AddButtonListener());
        copyButton.addActionListener(new CopyButtonListener());
    }

    private class GenerateButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            // 生成随机邀请码
            String randomCode = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            codeField.setText(randomCode);
        }
    }

    private class AddButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText();
            String code = codeField.getText();
            String seed = seedField.getText();

            if (username.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(InvitationCodeManager.this,
                        "请填写用户名和邀请码字段", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 如果提供了种子，则对邀请码进行加密
            String finalCode = code;
            if (!seed.isEmpty()) {
                finalCode = encryptWithSeed(code, seed);
                // 显示加密后的邀请码
                encryptedCodeField.setText(finalCode);
                copyButton.setEnabled(true);
            } else {
                // 清空加密后邀请码字段
                encryptedCodeField.setText("");
                copyButton.setEnabled(false);
            }

            // 检查邀请码是否已存在
            if (isInvitationCodeExists(finalCode)) {
                JOptionPane.showMessageDialog(InvitationCodeManager.this,
                        "邀请码已存在，请使用其他邀请码", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (addInvitationCode(username, finalCode)) {
                JOptionPane.showMessageDialog(InvitationCodeManager.this,
                        "邀请码添加成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
                // 清空输入框
                usernameField.setText("");
                codeField.setText("");
                seedField.setText("");
            } else {
                JOptionPane.showMessageDialog(InvitationCodeManager.this,
                        "邀请码添加失败，请重试", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class CopyButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String encryptedCode = encryptedCodeField.getText();
            if (!encryptedCode.isEmpty()) {
                // 将加密后的邀请码复制到剪贴板
                StringSelection stringSelection = new StringSelection(encryptedCode);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                JOptionPane.showMessageDialog(InvitationCodeManager.this,
                        "加密后的邀请码已复制到剪贴板", "复制成功", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private String encryptWithSeed(String originalCode, String seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest((originalCode + seed).getBytes());
            
            // 将字节数组转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // 取前16位作为加密后的邀请码
            return "ENC-" + hexString.substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return originalCode; // 如果加密失败，返回原始值
        }
    }

    private boolean isInvitationCodeExists(String code) {
        String query = "SELECT COUNT(*) FROM invitation_codes WHERE code = ?";
        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, code);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "数据库错误: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    private boolean addInvitationCode(String username, String code) {
        String query = "INSERT INTO invitation_codes (code, username) VALUES (?, ?)";
        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, code);
            statement.setString(2, username);

            int rowsInserted = statement.executeUpdate();
            return rowsInserted > 0;

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "数据库错误: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public static void main(String[] args) {
        // 设置外观为系统默认
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 在事件调度线程中启动GUI应用程序
        SwingUtilities.invokeLater(() -> {
            new InvitationCodeManager().setVisible(true);
        });
    }
}