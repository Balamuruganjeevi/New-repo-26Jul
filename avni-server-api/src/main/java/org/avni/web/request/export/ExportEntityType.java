package org.avni.web.request.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportEntityType {
    private String uuid;
    private List<String> fields = new ArrayList<>();
    private ExportFilters filters;
    private long maxCount;

    public ExportFilters getFilters() {
        return filters == null ? new ExportFilters() : filters;
    }

    public void setFilters(ExportFilters filters) {
        this.filters = filters;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(long maxCount) {
        this.maxCount = maxCount;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<String> getFields() {
        return fields;
    }

    public boolean isEmptyOrContains(String conceptUUID) {
        return fields.isEmpty() || fields.contains(conceptUUID);
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
