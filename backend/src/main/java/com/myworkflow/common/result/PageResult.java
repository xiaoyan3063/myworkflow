package com.myworkflow.common.result;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {

    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(long total, List<T> records) {
        PageResult<T> page = new PageResult<>();
        page.total = total;
        page.records = records == null ? Collections.<T>emptyList() : records;
        return page;
    }
}
