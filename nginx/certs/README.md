默认镜像会在构建时生成自签名证书，确保 `docker compose up -d` 可直接启动。

若要切换为 Let's Encrypt 证书，请将以下文件放在该目录并以挂载方式覆盖 `/etc/nginx/certs`：
- fullchain.pem
- privkey.pem
