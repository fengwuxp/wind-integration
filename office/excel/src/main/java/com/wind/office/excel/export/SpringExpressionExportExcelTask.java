package com.wind.office.excel.export;

import com.wind.office.core.AbstractDelegateDocumentTask;
import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExportExcelDataFetcher;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 基于 spring expression 取值 excel 导出任务，不支持列合并等操作
 *
 * @author wuxp
 * @date 2023-10-27 18:28
 **/
@Slf4j
public class SpringExpressionExportExcelTask extends AbstractDelegateDocumentTask {

    private final ExportExcelDataFetcher<?> fetcher;

    public SpringExpressionExportExcelTask(ExportExcelTaskInfo taskInfo, ExportExcelDataFetcher<?> fetcher) {
        super(taskInfo);
        this.fetcher = fetcher;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void doTask() {
        int queryPage = 1;
        ExportExcelTaskInfo taskInfo = (ExportExcelTaskInfo) getDelegate();
        while (!Thread.currentThread().isInterrupted()) {
            List rows = fetcher.fetch(queryPage, taskInfo.getFetchSize());
            this.addRows(rows);
            if (OfficeTaskState.isFinished(getState())) {
                log.info("excel task is finished，id = {}, state = {}", getId(), getState());
                return;
            }
            if (rows.size() < taskInfo.getFetchSize()) {
                // 处理完成
                break;
            }
            queryPage++;
        }
        if (Thread.currentThread().isInterrupted()){
            log.info("excel task is interrupted，id = {}, state = {}", getId(), getState());
            taskInfo.updateState(OfficeTaskState.INTERRUPT);
        }
    }

}
