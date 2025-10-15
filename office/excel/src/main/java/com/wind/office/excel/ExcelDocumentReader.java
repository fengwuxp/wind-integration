package com.wind.office.excel;

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
     * @param input    输入流
     * @param listener 监听器
     */
    void read(InputStream input, ExcelDocumentReadListener listener);
}
