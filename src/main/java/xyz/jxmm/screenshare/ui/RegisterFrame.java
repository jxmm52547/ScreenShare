package xyz.jxmm.screenshare.ui;

import xyz.jxmm.screenshare.dao.UserDAO;
import xyz.jxmm.screenshare.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RegisterFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JTextField invitationCodeField;
    private UserDAO userDAO;

    public RegisterFrame() {
        userDAO = new UserDAO();
        
        setTitle("注册");
        setSize(400, 350);
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
        
        // 密码标签和输入框
        JLabel passwordLabel = new JLabel("密码:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(passwordLabel, gbc);
        
        passwordField = new JPasswordField(15);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(passwordField, gbc);
        
        // 确认密码标签和输入框
        JLabel confirmPasswordLabel = new JLabel("确认密码:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(confirmPasswordLabel, gbc);
        
        confirmPasswordField = new JPasswordField(15);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(confirmPasswordField, gbc);
        
        // 邀请码标签和输入框
        JLabel invitationCodeLabel = new JLabel("邀请码:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(invitationCodeLabel, gbc);
        
        invitationCodeField = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 3;
        panel.add(invitationCodeField, gbc);
        
        // 注册按钮
        JButton registerButton = new JButton("注册");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        panel.add(registerButton, gbc);
        
        // 返回登录按钮
        JButton backButton = new JButton("返回登录");
        gbc.gridx = 1;
        gbc.gridy = 4;
        panel.add(backButton, gbc);
        
        add(panel);
        
        // 添加事件监听器
        registerButton.addActionListener(new RegisterButtonListener());
        backButton.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
    }
    
    private class RegisterButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());
            String invitationCode = invitationCodeField.getText();
            
            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || invitationCode.isEmpty()) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "请填写所有字段", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "密码和确认密码不匹配", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 验证邀请码
            if (!userDAO.isValidInvitationCode(invitationCode, username)) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "邀请码无效或与用户名不匹配", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查邀请码是否已被使用
            if (userDAO.isInvitationCodeUsed(invitationCode)) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "该邀请码已被使用", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (userDAO.isUsernameExists(username)) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "用户名已存在", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            User user = new User(username, password);
            if (userDAO.registerUser(user)) {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "注册成功!", "成功", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                // 注册成功后返回登录界面
                new LoginFrame().setVisible(true);
            } else {
                JOptionPane.showMessageDialog(RegisterFrame.this, 
                    "注册失败，请重试", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}