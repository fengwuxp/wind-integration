package com.wind.office.excel.read;

import com.wind.office.core.OfficeDocumentTaskInfo;
import com.wind.office.core.OfficeTaskState;
import com.wind.office.excel.metadata.ExcelCellDescriptor;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * excel 导入文档任务 Info
 *
 * @author wuxp
 * @date 2025-10-15 11:04
 **/
@Getter
@Builder
public class ImportExcelTaskInfo implements OfficeDocumentTaskInfo {

    private final String id;

    private final String name;

    private final AtomicReference<OfficeTaskState> state;

    private final AtomicReference<LocalDateTime> beginTime;

    private final AtomicReference<LocalDateTime> endTime;

    private final AtomicInteger rowSize = new AtomicInteger(0);

    private final List<Object> failedRows = new ArrayList<>();

    private final List<ExcelCellDescriptor> descriptors;

    private final InputStream document;

    @Override
    public OfficeTaskState getState() {
        return state.get();
    }

    @Override
    public int getRowSize() {
        return rowSize.get();
    }

    @Override
    public int getFailedRowSize() {
        return failedRows.size();
    }

    @Override
    public LocalDateTime getBeginTime() {
        return beginTime.get();
    }

    @Override
    public LocalDateTime getEndTime() {
        return endTime.get();
    }

    @Override
    public void addRows(Collection<Object> rows) {
        rowSize.addAndGet(rows.size());
    }

    @Override
    public void addFailedRows(Collection<Object> rows) {
        failedRows.addAll(rows);
    }

    @Override
    public void updateState(OfficeTaskState newState) {
        if (Objects.equals(newState, OfficeTaskState.EXECUTING)) {
            this.beginTime.set(LocalDateTime.now());
        }
        if (OfficeTaskState.isFinished(newState)) {
            this.endTime.set(LocalDateTime.now());
        }
        this.state.set(newState);
    }

    public static ImportExcelTaskInfo of(List<ExcelCellDescriptor> descriptors, InputStream document) {
        String id = RandomStringUtils.secure().nextAlphanumeric(32);
        return of(id, id, descriptors, document);
    }

    public static ImportExcelTaskInfo of(String name, List<ExcelCellDescriptor> descriptors, InputStream document) {
        return of(RandomStringUtils.secure().nextAlphanumeric(32), name, descriptors, document);
    }

    public static ImportExcelTaskInfo of(Object id, String name, List<ExcelCellDescriptor> descriptors, InputStream document) {
        return ImportExcelTaskInfo.builder()
                .id(String.valueOf(id))
                .name(name)
                .beginTime(new AtomicReference<>())
                .endTime(new AtomicReference<>())
                .state(new AtomicReference<>(OfficeTaskState.WAIT))
                .descriptors(descriptors)
                .document(document)
                .build();
    }
}
