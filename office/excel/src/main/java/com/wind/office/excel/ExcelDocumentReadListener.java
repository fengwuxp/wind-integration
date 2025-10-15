package com.wind.office.excel;

import java.util.Collection;
import java.util.List;

/**
 * excel 文档数据读取监听器
 *
 * @author wuxp
 * @date 2025-10-15 11:25
 **/
public interface ExcelDocumentReadListener {

    /**
     * 读取数据写入成功回调
     *
     * @param rows 写入的数据
     */
    void onWrite(Collection<Object> rows);

    /**
     * 数据处理异常回调
     *
     * @param data      数据
     * @param throwable 异常
     */
    default void onException(List<String> data, Throwable throwable) {

    }

    /**
     * 数据处理完成回调
     */
    default void onFinish() {

    }

}
