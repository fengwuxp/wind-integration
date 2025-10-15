package com.wind.office.excel;

import java.util.List;

/**
 * excel 文档数数据导入 writer, 将 {@link ExcelDocumentReader} 读取出的数据写入到系统中
 *
 * @author wuxp
 * @date 2025-10-15 09:15
 **/
public interface ExcelDocumentImportWriter<E> {

    /**
     * 写入数据
     *
     * @param rows 数据
     */
    void write(List<E> rows);
}
