#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Harness 增强 ①：G1 门禁前移（PreToolUse / Write *.java）
#
# 呼应 .harness/rules/开发流程规范.md 第 133 行：
#   「默认规则：没有人类审批，不得从 analyzing 进入 coding。」
#
# 作用：在写【新建 .java 文件】前，检查是否存在一张正处于“编码/写测试”阶段的 change
#       （status ∈ {coding, testing}）。新建 .java 仅在 ②coding / ③testing 合法；
#       reviewing/ci/verifying/done 阶段本不应再新建生产 java。
#       若没有这样的 change，打印 G1 提醒，避免“没建档/没审批就开写”。
#
# 口径：当前为【软门禁】—— 仅提醒、不阻断（exit 0）。
#       若要改为【硬门禁】（阻断写入并要求先审批），把末尾的 `exit 0` 改成 `exit 2`。
# ---------------------------------------------------------------------------
set -u

input="$(cat)"

# 提取 tool_input.file_path（仅取首个匹配，避免 content 内同名字段干扰）
file_path="$(printf '%s' "$input" \
  | grep -oE '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | head -1)"

# 非 .java 文件直接放行
case "$file_path" in
  *.java\") : ;;
  *) exit 0 ;;
esac

root="${CLAUDE_PROJECT_DIR:-.}"

# 存在任一处于“编码/写测试”阶段的 change → 放行
if grep -qE '^status:[[:space:]]*(coding|testing)\b' \
     "$root"/.harness/changes/C-*/change.md 2>/dev/null; then
  exit 0
fi

echo "[Harness/G1] ⚠ 未检测到处于 coding/testing 阶段的 change。" >&2
echo "  按开发流程规范状态机，写 .java 前需有人类审批：analyzing → coding（规范 §「变更状态机」）。" >&2
echo "  请确认当前 change.md 的 status 已置为 coding（或 testing），再继续编码。" >&2

# 软门禁：仅提醒，不阻断。改为硬门禁请将下一行改为 `exit 2`。
exit 0
