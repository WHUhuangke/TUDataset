package com.github.evolution;

import java.util.Objects;

/**
 * 表示时间线上的一个版本节点，包含提交 ID、展示标签和顺序。
 */
public class TimelineVersion {

    private final String commitId;
    private final String shortId;
    private final String label;
    private final int orderIndex;
    private final String message;
    private final String author;
    private final long commitTime;

    public TimelineVersion(String commitId,
                           String shortId,
                           String label,
                           int orderIndex,
                           String message,
                           String author,
                           long commitTime) {
        this.commitId = commitId;
        this.shortId = shortId;
        this.label = label;
        this.orderIndex = orderIndex;
        this.message = message;
        this.author = author;
        this.commitTime = commitTime;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getShortId() {
        return shortId;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 返回时间顺序索引，数值越小越早。
     */
    public int getOrderIndex() {
        return orderIndex;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public long getCommitTime() {
        return commitTime;
    }

    @Override
    public String toString() {
        if (message != null && !message.isBlank()) {
            return label + " (" + shortId + ") " + message;
        }
        return label + " (" + shortId + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TimelineVersion that = (TimelineVersion) obj;
        return Objects.equals(commitId, that.commitId)
            && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commitId, label);
    }
}
