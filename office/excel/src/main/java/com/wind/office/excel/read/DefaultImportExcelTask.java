package com.wind.office.excel.read;

import com.alibaba.excel.EasyExcel;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.office.core.AbstractDelegateDocumentTask;
import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExcelDocumentReadListener;
import com.wind.office.excel.ExcelDocumentReader;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * 默认的 excel 导入任务
 *
 * @author wuxp
 * @date 2025-10-15 11:01
 **/
@Slf4j
public class DefaultImportExcelTask extends AbstractDelegateDocumentTask {

    private final ExcelDocumentReader reader;

    /**
     * 任务状态同步器，用于在分布式情况下通过 redis（缓存） 、或数据库 同步任务的实时状态
     *
     * @key 任务 id
     * @value 任务状态
     */
    private final Function<String, OfficeTaskState> stateSyncer;

    public DefaultImportExcelTask(ImportExcelTaskInfo delegate, ExcelDocumentReader reader) {
        this(delegate, reader, id -> null);
    }

    public DefaultImportExcelTask(ImportExcelTaskInfo delegate, ExcelDocumentReader reader, Function<String, OfficeTaskState> stateSyncer) {
        super(delegate);
        this.reader = reader;
        this.stateSyncer = stateSyncer;
    }

    @Override
    protected void doTask() {
        try {
            ImportExcelTaskInfo taskInfo = (ImportExcelTaskInfo) getDelegate();
            reader.read(taskInfo.getDocument(), new ExcelDocumentReadListener() {
                @Override
                public void onWrite(Collection<Object> rows) {
                    taskInfo.addRows(rows);
                    OfficeTaskState state = stateSyncer.apply(getId());
                    if (state != null) {
                        updateState(state);
                    }
                }

                @Override
                public void onException(List<String> data, Throwable throwable) {
                    List<String> rowCellValues = new ArrayList<>(data);
                    // 增加失败信息
                    rowCellValues.add(throwable.getMessage() == null ? WindConstants.EMPTY : throwable.getMessage());
                    taskInfo.addFailedRows(Collections.singleton(rowCellValues));
                }

                @Override
                public void onFinish() {
                    if (taskInfo.getRowSize() > 0 && taskInfo.getFailedRowSize() > 0) {
                        updateState(OfficeTaskState.PART_COMPLETED);
                    } else if (taskInfo.getRowSize() > 0 && taskInfo.getFailedRowSize() == 0) {
                        updateState(OfficeTaskState.COMPLETED);
                    } else {
                        updateState(OfficeTaskState.FAILED);
                    }
                }
            });
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                log.info("import excel task is interrupted, id = {}, state = {}", getId(), getState());
                updateState(OfficeTaskState.INTERRUPT);
            }
        }
    }

    public void writeFailedRows(OutputStream output) {
        writeFailedRows(output, Locale.getDefault());
    }

    /**
     * 导出失败的行
     *
     * @param output 输出流
     * @param locale 语言
     */
    public void writeFailedRows(OutputStream output, Locale locale) {
        ImportExcelTaskInfo taskInfo = (ImportExcelTaskInfo) getDelegate();
        AssertUtils.notEmpty(taskInfo.getFailedRows(), "no failed rows");
        try {
            List<String> titles = new ArrayList<>(taskInfo.getDescriptors().stream().map(ExcelCellDescriptor::getTitle).toList());
            if (Objects.equals(locale, Locale.CHINESE)) {
                titles.add("失败原因");
            } else {
                titles.add("Failure Reason");
            }
            EasyExcel.write(output)
                    .head(titles.stream().map(Collections::singletonList).toList())
                    .needHead(true)
                    .charset(StandardCharsets.UTF_8)
                    .autoCloseStream(false)
                    .sheet(0)
                    .doWrite(taskInfo.getFailedRows());
        } catch (Exception e) {
            throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, "export failure rows exception", e);
        }
    }
}
