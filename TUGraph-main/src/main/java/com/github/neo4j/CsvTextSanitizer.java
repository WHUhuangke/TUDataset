package com.github.neo4j;

/**
 * CSV 文本清洗工具
 * 将换行、制表符等控制字符编码为可逆的转义序列，避免 Neo4j Bulk Import 解析失败。
 * 同时提供还原方法，方便从数据库中取出值时恢复原始格式。
 */
public final class CsvTextSanitizer {

    private CsvTextSanitizer() {
        // 工具类不应被实例化
    }

    /**
     * 将文本中的控制字符编码为可导出的形式。
     * 规则：
     * - Windows/Mac 换行统一为 '\n'
     * - '\n'  -> "\\n"
     * - '\t'  -> "\\t"
     * - '\r'  -> "\\r"（在统一换行后仅用于孤立的回车符）
     * - '\f'  -> "\\f"
     * - '\b'  -> "\\b"
     * - '\\' -> "\\\\"
     * 其他低于 0x20 的控制字符将被编码为 "\\uXXXX"
     *
     * @param original 原始文本
     * @return 编码后的文本（永不为 null）
     */
    public static String encode(String original) {
        if (original == null || original.isEmpty()) {
            return "";
        }

        String normalized = original.replace("\r\n", "\n");
        StringBuilder encoded = new StringBuilder(normalized.length());

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            switch (ch) {
                case '\\':
                    encoded.append("\\\\");
                    break;
                case '\n':
                    encoded.append("\\n");
                    break;
                case '\t':
                    encoded.append("\\t");
                    break;
                case '\r':
                    encoded.append("\\r");
                    break;
                case '\f':
                    encoded.append("\\f");
                    break;
                case '\b':
                    encoded.append("\\b");
                    break;
                default:
                    if (ch < 0x20) {
                        encoded.append(String.format("\\u%04x", (int) ch));
                    } else {
                        encoded.append(ch);
                    }
                    break;
            }
        }

        return encoded.toString();
    }

    /**
     * 将 encode 处理后的文本恢复为原始格式。
     *
     * @param sanitized 已编码文本
     * @return 还原后的文本（永不为 null）
     */
    public static String decode(String sanitized) {
        if (sanitized == null || sanitized.isEmpty()) {
            return "";
        }

        StringBuilder decoded = new StringBuilder(sanitized.length());
        for (int i = 0; i < sanitized.length(); i++) {
            char ch = sanitized.charAt(i);
            if (ch != '\\') {
                decoded.append(ch);
                continue;
            }

            if (i + 1 >= sanitized.length()) {
                decoded.append('\\');
                break;
            }

            char next = sanitized.charAt(++i);
            switch (next) {
                case 'n':
                    decoded.append('\n');
                    break;
                case 't':
                    decoded.append('\t');
                    break;
                case 'r':
                    decoded.append('\r');
                    break;
                case 'f':
                    decoded.append('\f');
                    break;
                case 'b':
                    decoded.append('\b');
                    break;
                case '\\':
                    decoded.append('\\');
                    break;
                case 'u':
                    if (i + 4 < sanitized.length()) {
                        String hex = sanitized.substring(i + 1, i + 5);
                        try {
                            int codePoint = Integer.parseInt(hex, 16);
                            decoded.append((char) codePoint);
                            i += 4;
                        } catch (NumberFormatException e) {
                            decoded.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        decoded.append("\\u");
                    }
                    break;
                default:
                    decoded.append(next);
                    break;
            }
        }

        return decoded.toString();
    }
}
