package com.smart.trip.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DotenvLoader} 单测（RED→GREEN）。
 */
class DotenvLoaderTest {

    @Test
    @DisplayName("正常 .env 文件解析：key=value、注释、空行")
    void shouldParseSimpleDotenv(@TempDir Path tempDir) throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                # 注释行
                DEEPSEEK_API_KEY=sk-test-deepseek-key
                DASHSCOPE_API_KEY=sk-test-dashscope-key
                # 行内注释
                REDIS_HOST=127.0.0.1
                XHS_API_KEY=xhs-key-123   # 尾部注释
                """);

        Properties props = DotenvLoader.load(dotenv);

        assertThat(props.getProperty("DEEPSEEK_API_KEY")).isEqualTo("sk-test-deepseek-key");
        assertThat(props.getProperty("DASHSCOPE_API_KEY")).isEqualTo("sk-test-dashscope-key");
        assertThat(props.getProperty("REDIS_HOST")).isEqualTo("127.0.0.1");
        assertThat(props.getProperty("XHS_API_KEY")).isEqualTo("xhs-key-123");
        assertThat(props).doesNotContainKey("#");
    }

    @Test
    @DisplayName("空行和纯注释行被跳过")
    void shouldSkipEmptyLinesAndComments(@TempDir Path tempDir) throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """

                # 纯注释
                FOO=bar

                # 另一个注释
                """);

        Properties props = DotenvLoader.load(dotenv);

        assertThat(props).hasSize(1);
        assertThat(props.getProperty("FOO")).isEqualTo("bar");
    }

    @Test
    @DisplayName("文件不存在时返回空 Properties（不抛异常）")
    void shouldReturnEmptyWhenFileNotFound() {
        Properties props = DotenvLoader.load(Path.of("/nonexistent/.env-test"));
        assertThat(props).isEmpty();
    }

    @Test
    @DisplayName("引号包裹的值应去除引号")
    void shouldStripQuotes(@TempDir Path tempDir) throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                KEY1="double-quoted"
                KEY2='single-quoted'
                KEY3=no-quotes
                """);

        Properties props = DotenvLoader.load(dotenv);

        assertThat(props.getProperty("KEY1")).isEqualTo("double-quoted");
        assertThat(props.getProperty("KEY2")).isEqualTo("single-quoted");
        assertThat(props.getProperty("KEY3")).isEqualTo("no-quotes");
    }

    @Test
    @DisplayName("不带 = 的行被跳过")
    void shouldSkipLinesWithoutEquals(@TempDir Path tempDir) throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, """
                VALID_KEY=value
                invalid line without equals
                ANOTHER=ok
                """);

        Properties props = DotenvLoader.load(dotenv);

        assertThat(props).hasSize(2);
        assertThat(props.getProperty("VALID_KEY")).isEqualTo("value");
        assertThat(props.getProperty("ANOTHER")).isEqualTo("ok");
    }
}
