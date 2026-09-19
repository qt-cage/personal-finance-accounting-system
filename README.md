# 个人记账系统

基于 Spring Boot、MyBatis-Plus、MySQL 和 Vue 3 的个人收支管理项目。后端提供账户、账单、分类、预算和统计接口，前端提供对应的交互页面。

## 主要功能

- 账户注册与登录、个人资料和密码管理、头像上传。
- 收支账单的新增、编辑、删除和查询；自定义收入与支出分类。
- 按月设置总预算或分类预算，并展示预算使用情况。
- 收支汇总、分类占比和趋势图表。
- 中文、英文、日文界面，以及主题偏好设置。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 后端 | Java、Spring Boot 2.7.18、MyBatis-Plus 3.5.3.1、MySQL、JWT |
| 前端 | Vue 3、Vite、Element Plus、ECharts、Vue Router |
| 测试 | JUnit 5、Node.js 内置测试运行器 |

## 项目结构

```text
backend/
  src/main/java/com/finance/   后端接口与业务实现
  src/main/resources/           应用配置及多语言消息
  src/test/                     后端测试
  sql/init.sql                  新库初始化脚本
  sql/migrations/               已有库迁移脚本
frontend/
  src/                         前端页面、组件和接口封装
  tests/                       前端测试
```

## 本地运行

准备 JDK 11、Maven、MySQL、Node.js 和 npm。前端依赖使用 `npm ci` 安装。

1. 在 `backend` 目录执行 `mysql -u root -p -e "source sql/init.sql"`，创建 `finance_db` 并初始化默认分类。已有数据库请先备份，再按需查看 `backend/sql/migrations/`；不要对现有数据重复执行新库初始化。
2. 在启动后端的终端配置 `DB_PASSWORD` 和 `JWT_SECRET`。如需修改默认数据库地址或账号，可配置 `DB_URL`、`DB_USERNAME`。配置方式见下文。
3. 在 `backend` 目录执行 `mvn spring-boot:run`。后端默认使用 `http://localhost:8088`。
4. 在另一个终端进入 `frontend`，执行 `npm ci`、`npm run dev`。前端默认使用 `http://localhost:5173`。

Windows PowerShell 配置示例（将占位符换成自己的值）：

```powershell
$env:DB_PASSWORD = '<your MySQL password>'
$env:JWT_SECRET = '<your random secret>'
```

后端也支持从当前用户目录的 `.personal-finance/application.properties` 读取本机配置；该文件不属于仓库。不要将真实凭据写入 `application.yml` 或提交到 Git。前端开发模式默认连接本机 `8088` 端口；需要调整时使用 `VITE_API_ORIGIN`。

后端普通测试在 `backend` 目录运行 `mvn test`；前端测试在 `frontend` 目录运行 `npm test`。数据库集成测试需要显式配置独立的 `finance_quality_test_*` 测试库。

## 当前状态

仓库提供源码、初始化脚本和自动化测试；运行需要自行配置 MySQL。仓库中未配置公开在线演示地址。
