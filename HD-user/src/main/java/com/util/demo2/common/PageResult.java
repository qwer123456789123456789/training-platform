package com.util.demo2.common;

import java.util.List;

/**
 * 分页返回结构，严格按《统一接口规范》固定为 records / total / current / size 四个字段。
 * 注意：不要直接返回 MyBatis-Plus 的 Page 对象，它会多序列化出
 * pages、searchCount、optimizeCountSql 等字段，导致各模块格式不统一。
 */
public class PageResult<T> {

    private List<T> records;
    private Long total;
    private Long current;
    private Long size;

    public PageResult() {
    }

    public PageResult(List<T> records, Long total, Long current, Long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getCurrent() {
        return current;
    }

    public void setCurrent(Long current) {
        this.current = current;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }
}
