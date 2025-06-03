package com.wind.office.excel.export;

import com.wind.office.core.AbstractDelegateDocumentTask;
import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.ExportExcelDataFetcher;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

/**
 * 基于 spring expression 取值 excel 导出任务，不支持列合并等操作
 *
 * @author wuxp
 * @date 2023-10-27 18:28
 **/
@Slf4j
public class SpringExpressionExportExcelTask extends AbstractDelegateDocumentTask {

    private final ExportExcelDataFetcher<?> fetcher;

    /**
     * 任务状态同步器，用于在分布式情况下通过 redis（缓存） 、或数据库 同步任务的实时状态
     *
     * @key 任务 id
     * @value 任务状态
     */
    private final Function<String, OfficeTaskState> stateSyncer;

    public SpringExpressionExportExcelTask(ExportExcelTaskInfo taskInfo, ExportExcelDataFetcher<?> fetcher) {
        this(taskInfo, fetcher, id -> null);
    }

    public SpringExpressionExportExcelTask(ExportExcelTaskInfo taskInfo, ExportExcelDataFetcher<?> fetcher,
                                           Function<String, OfficeTaskState> stateSyncer) {
        super(taskInfo);
        this.fetcher = fetcher;
        this.stateSyncer = stateSyncer;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void doTask() {
        int queryPage = 1;
        ExportExcelTaskInfo taskInfo = (ExportExcelTaskInfo) getDelegate();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                OfficeTaskState state = stateSyncer.apply(getId());
                if (state != null) {
                    updateState(state);
                }
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
        } finally {
            if (Thread.currentThread().isInterrupted()) {
                log.info("excel task is interrupted，id = {}, state = {}", getId(), getState());
                taskInfo.updateState(OfficeTaskState.INTERRUPT);
            }
        }
    }

}
