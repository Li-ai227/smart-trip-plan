package com.smart.trip.server.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * .env 文件加载工具。
 *
 * <p>将 {@code .env} 文件解析为 {@link Properties}，用于注入 Spring {@code Environment}。
 * 格式兼容 Docker-compose .env：{@code KEY=VALUE}，支持行内 {@code #} 注释、
 * 空行跳过、引号包裹值（去除引号）。不做变量展开（{@code ${VAR}}），由 Spring 自行处理。</p>
 *
 * <p>加载顺序：先尝试 {@code projectRoot/.env}，不存在时降级为空 Properties
 * （不抛异常，不阻塞启动，日志 WARNING 提示）。</p>
 */
public final class DotenvLoader {

    private static final System.Logger LOG = System.getLogger(DotenvLoader.class.getName());

    /** 默认 .env 文件路径（相对 working directory 或 classpath 根）。 */
    static final String DEFAULT_DOTENV_PATH = ".env";

    private DotenvLoader() {
    }

    /**
     * 从默认路径（working directory/.env）加载。
     *
     * @return Properties，文件不存在或读取失败时为空
     */
    public static Properties load() {
        return load(Paths.get(DEFAULT_DOTENV_PATH));
    }

    /**
     * 从指定路径加载 .env 文件。
     *
     * @param path .env 文件路径
     * @return Properties，文件不存在或读取失败时为空
     */
    public static Properties load(Path path) {
        Properties props = new Properties();
        if (!Files.isRegularFile(path)) {
            LOG.log(System.Logger.Level.WARNING,
                    ".env 文件不存在，跳过加载: {0}", path.toAbsolutePath());
            return props;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                parseAndSet(line, props);
            }
            LOG.log(System.Logger.Level.INFO,
                    "已加载 .env: path={0}, keys={1}", path.toAbsolutePath(), props.size());
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING,
                    ".env 文件读取失败: path={0}, reason={1}", path.toAbsolutePath(), ex.getMessage());
        }
        return props;
    }

    /** 解析单行 KEY=VALUE 并写入属性集。 */
    private static void parseAndSet(String line, Properties props) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        int eqIdx = trimmed.indexOf('=');
        if (eqIdx < 0) {
            return;
        }
        String key = trimmed.substring(0, eqIdx).strip();
        String value = trimmed.substring(eqIdx + 1).strip();

        // 剥离行尾注释（简单策略：不在引号内的 # 视为注释）
        int commentIdx = findCommentStart(value);
        if (commentIdx >= 0) {
            value = value.substring(0, commentIdx).strip();
        }

        // 去除包裹引号（单引号或双引号）
        value = stripQuotes(value);

        if (!key.isEmpty()) {
            props.setProperty(key, value);
        }
    }

    /** 去除字符串两端的匹配引号（单引号或双引号）。 */
    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * 找到值字符串中行内注释的起始位置（不在引号内的 #）。
     */
    private static int findCommentStart(String value) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return i;
            }
        }
        return -1;
    }
}
