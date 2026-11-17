# 粉系衣橱 / Girl Shop

二手服装交易平台，包含 Java 后端、静态前端、数据库脚本和测试代码。

## 目录

- `hm-dianping/hm-dianping`：Spring Boot 后端、数据库脚本和测试。
- `nginx-1.18.0/nginx-1.18.0/html/girl-shop`：前端页面和静态资源。
- `nginx-1.18.0/nginx-1.18.0/conf/nginx.conf`：Nginx 配置。

## 本地运行

1. 准备 Java、Maven、MySQL、Redis、RabbitMQ 和 Nginx。仓库不包含这些软件的可执行文件。
2. 将后端 `src/main/resources/application.example.yaml` 复制为同目录的 `application.yaml`，填写自己的数据库、Redis、RabbitMQ 和 AI 服务配置。所有 `REPLACE_WITH_YOUR_LOCAL_VALUE` 均需按本地环境设置；没有密码的服务填写空字符串。
3. 按后端 README 初始化数据库，启动相关依赖服务，再启动 `GirlShopApplication`。
4. 使用仓库提供的 Nginx 配置和前端文件启动 Nginx，访问 `http://localhost:8090`。

生产配置示例为 `application-prod.example.yaml`，使用前同样需要复制并填写实际配置。

详细运行和测试说明见后端目录的 `README.md`、`OPERATIONS.md` 和 `TESTING.md`。

真实配置、日志、编译产物、用户上传图片和个人面试材料不纳入版本管理。示例商品图片保留在前端静态资源中。
