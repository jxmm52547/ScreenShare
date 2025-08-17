# ScreenShare 登录和注册系统

这是一个基于Java Swing的简单登录和注册系统，使用MySQL数据库存储用户信息。

## 功能特性

- 用户注册功能
- 用户登录功能
- 用户名重复检查
- 简洁的图形用户界面

## 技术栈

- Java Swing (GUI)
- MySQL (数据库)
- JDBC (数据库连接)
- Gradle (构建工具)

## 数据库配置

数据库连接信息:
- 主机: localhost
- 数据库名: ss
- 用户名: jxmm52547
- 密码: jxmm52547@jxmm.xyz

## 数据库表结构

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 如何运行

1. 确保已安装JDK和Gradle
2. 创建MySQL数据库并执行初始化脚本
3. 配置数据库连接（已在代码中配置）
4. 构建项目：
   ```
   gradle build
   ```
5. 运行应用程序：
   ```
   gradle run
   ```
   或者直接运行 [Application.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/Application.java) 类

## 类说明

- [Application.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/Application.java) - 程序入口点
- [User.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/model/User.java) - 用户实体类
- [DatabaseUtil.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/util/DatabaseUtil.java) - 数据库连接工具类
- [UserDAO.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/dao/UserDAO.java) - 用户数据访问对象
- [LoginFrame.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/ui/LoginFrame.java) - 登录界面
- [RegisterFrame.java](file:///D:/Project/ScreenShare/src/main/java/xyz/jxmm/screenshare/ui/RegisterFrame.java) - 注册界面

## 安全说明

注意：此系统仅用于演示目的，实际生产环境中需要考虑以下安全问题：
- 密码应加密存储（如使用BCrypt）
- 应该使用PreparedStatement防止SQL注入
- 应添加输入验证和过滤
- 应使用连接池管理数据库连接