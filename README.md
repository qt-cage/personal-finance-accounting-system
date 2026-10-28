# 个人记账系统

基于 Spring Boot、MyBatis-Plus、MySQL 和 Vue 3 的个人财务管理项目，提供账单、账户、预算、资产与统计等功能。

## 主要功能

- 用户注册、登录、个人资料与密码管理，支持头像上传。
- 收支账单的新增、编辑、批量删除和组合查询；支持两级分类与小票附件。
- 管理资金账户和期初余额，记录账户间转账。
- 设置月度预算，查看分类支出和站内预算提醒。
- 管理周期账单，查看资产、负债、净资产和收支统计图表。
- 使用 CSV 模板预览并确认导入账单，导出 XLSX 文件。
- 基于角色的系统管理，支持默认分类、字典和设置管理，以及操作日志查看。
- 支持中文、英文、日文界面与主题切换；聚合查询可选用 Redis 缓存。

## 技术栈

| 部分 | 技术 |
| --- | --- |
| 后端 | Java、Spring Boot 2.7.18、MyBatis-Plus 3.5.3.1、MySQL、Spring Security、JWT、Redis（可选） |
| 前端 | Vue 3、Vite、Element Plus、ECharts、Vue Router |
| 测试 | JUnit 5、Node.js 内置测试运行器 |

## 项目结构

```text
backend/
  src/main/java/com/finance/   后端接口与业务实现
  src/main/resources/          应用配置和多语言消息
  src/test/                    后端测试
  sql/init.sql                 新数据库初始化脚本
  sql/migrations/              已有数据库的迁移脚本
frontend/
  src/                         前端页面、组件和接口封装
  tests/                       前端测试
```

## 本地运行

准备 JDK 11、Maven、MySQL，以及 Node.js 20.19+（20.x）或 22.12+ 和 npm。

1. 在 `backend` 目录执行 `mysql -u root -p -e "source sql/init.sql"`，创建 `finance_db` 并初始化默认数据。该步骤用于新数据库；已有账本请先备份并审查 `backend/sql/migrations/`，不要直接重复初始化。
2. 在启动后端的终端设置 `DB_PASSWORD` 和 `JWT_SECRET`。密钥去除首尾空白后需至少 32 字节，建议使用随机生成的值。需要时可配置 `DB_URL` 和 `DB_USERNAME`。
3. 在 `backend` 目录运行 `mvn spring-boot:run`，后端默认地址为 `http://localhost:8088`。
4. 在另一个终端进入 `frontend`，运行 `npm ci` 和 `npm run dev`，前端默认地址为 `http://localhost:5173`。

Windows PowerShell 环境变量示例，请将占位符替换为自己的值：

```powershell
$env:DB_PASSWORD = '<MySQL 密码>'
$env:JWT_SECRET = '<至少 32 字节的随机密钥>'
```

`JWT_SECRET` 请通过环境变量配置。数据库密码也可放在当前用户目录的 `.personal-finance/application.properties`；不要将真实凭据提交到仓库。前端开发模式默认连接本机 8088 端口，可通过 `VITE_API_ORIGIN` 调整。

Redis 聚合缓存默认关闭，可通过 `FINANCE_CACHE_ENABLED`、`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD` 配置。周期账单自动调度默认关闭，可通过 `APP_RECURRING_ENABLED` 启用。头像和附件保存在本地上传目录，需要自行备份。

## 测试与项目状态

后端普通测试在 `backend` 目录运行 `mvn test`；前端测试在 `frontend` 目录运行 `npm test`。数据库集成测试需要显式配置独立的 `finance_quality_test_*` 测试库。

项目提供源码、数据库脚本和自动化测试，运行需要自行配置数据库。仓库未提供公开在线演示地址。
