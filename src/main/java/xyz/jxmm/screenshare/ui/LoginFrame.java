package xyz.jxmm.screenshare.ui;

import xyz.jxmm.screenshare.dao.UserDAO;
import xyz.jxmm.screenshare.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private UserDAO userDAO;

    public LoginFrame() {
        userDAO = new UserDAO();
        
        setTitle("登录");
        setSize(400, 250);
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
        
        // 登录按钮
        JButton loginButton = new JButton("登录");
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(loginButton, gbc);
        
        // 注册按钮
        JButton registerButton = new JButton("注册");
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(registerButton, gbc);
        
        add(panel);
        
        // 添加事件监听器
        loginButton.addActionListener(new LoginButtonListener());
        registerButton.addActionListener(e -> {
            dispose();
            new RegisterFrame().setVisible(true);
        });
        
        // 添加回车键监听器
        usernameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    attemptLogin();
                }
            }
        });
        
        passwordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    attemptLogin();
                }
            }
        });
    }
    
    private void attemptLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(LoginFrame.this, 
                "请填写所有字段", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        User user = userDAO.loginUser(username, password);
        if (user != null) {
            // 直接进入登录成功页面
            dispose();
            new MainFrame(user).setVisible(true);
        } else {
            JOptionPane.showMessageDialog(LoginFrame.this, 
                "用户名或密码错误", "登录失败", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private class LoginButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            attemptLogin();
        }
    }
}