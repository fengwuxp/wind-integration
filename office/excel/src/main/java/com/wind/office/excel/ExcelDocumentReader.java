package com.wind.office.excel;

import org.springframework.core.io.InputStreamSource;

import java.io.InputStream;

/**
 * excel 文档数据读取器
 *
 * @author wuxp
 * @date 2025-10-15 09:10
 **/
public interface ExcelDocumentReader {

    /**
     * 读取文档
     *
     * @param source   输入流来源
     * @param listener 监听器
     */
    void read(InputStreamSource source, ExcelDocumentReadListener listener);

    /**
     * 读取文档
     *
     * @param input    输入流
     * @param listener 监听器
     */
    default void read(InputStream input, ExcelDocumentReadListener listener) {
        read(() -> input, listener);
    }
}
