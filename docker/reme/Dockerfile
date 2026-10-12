# ReMe Server — AgentScope 长期记忆服务（C-015 T-1）
# Python 3.11 + reme-ai[core]，HTTP 后端默认端口 8002。
# LLM/Embedding API Key 通过环境变量注入（不入镜像）。
FROM python:3.11-slim

# 已实测核实（下载真实 wheel 逐字节比对，非猜测）：PyPI 上最新已发布版本
# reme-ai==0.4.0.6 本身仍有 bug——as_embedding 组件构建 OpenAIEmbeddingModel 时
# 未将 dimensions 单独透传，触发 "TypeError: OpenAIEmbeddingModel.__init__()
# missing 1 required positional argument: 'dimensions'"，导致 ReMe 启动失败
# （Application startup failed）。该 bug 已在 GitHub main 分支修复
# （as_embedding/__init__.py 新增 dimensions 显式抽取逻辑），但截至本次构建
# 尚未发布新版本到 PyPI，因此改为源码安装并锁定 commit（保证可复现构建，
# 避免 main 分支后续提交引入未知回归）。待上游发布新版本后可改回
# `pip install "reme-ai[core]==<新版本>"`。
ARG REME_GIT_REF=6bf2db8ff4d56039ae51f1776c7ba0b73f425d7f

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && git clone https://github.com/agentscope-ai/ReMe.git /tmp/reme-src \
    && cd /tmp/reme-src \
    && git checkout "${REME_GIT_REF}" \
    && pip install --no-cache-dir ".[core]" \
    && cd / \
    && rm -rf /tmp/reme-src \
    && apt-get purge -y --auto-remove git \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8002

CMD ["reme", "start", "service.port=8002"]
