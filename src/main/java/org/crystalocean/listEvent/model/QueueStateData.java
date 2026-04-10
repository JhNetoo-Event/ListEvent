package org.crystalocean.listEvent.model;

import java.util.ArrayList;
import java.util.List;

public class QueueStateData {

    private List<QueueEntrySnapshot> queueEntries = new ArrayList<>();
    private List<GraceEntrySnapshot> graceEntries = new ArrayList<>();

    public List<QueueEntrySnapshot> getQueueEntries() {
        return queueEntries;
    }

    public void setQueueEntries(List<QueueEntrySnapshot> queueEntries) {
        this.queueEntries = queueEntries;
    }

    public List<GraceEntrySnapshot> getGraceEntries() {
        return graceEntries;
    }

    public void setGraceEntries(List<GraceEntrySnapshot> graceEntries) {
        this.graceEntries = graceEntries;
    }

    public static class QueueEntrySnapshot {
        private String uuid;
        private long enqueueTimestamp;
        private int priorityWeight;
        private long sequence;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public long getEnqueueTimestamp() {
            return enqueueTimestamp;
        }

        public void setEnqueueTimestamp(long enqueueTimestamp) {
            this.enqueueTimestamp = enqueueTimestamp;
        }

        public int getPriorityWeight() {
            return priorityWeight;
        }

        public void setPriorityWeight(int priorityWeight) {
            this.priorityWeight = priorityWeight;
        }

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }
    }

    public static class GraceEntrySnapshot {
        private String uuid;
        private long enqueueTimestamp;
        private int priorityWeight;
        private long sequence;
        private long expiresAtMillis;

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public long getEnqueueTimestamp() {
            return enqueueTimestamp;
        }

        public void setEnqueueTimestamp(long enqueueTimestamp) {
            this.enqueueTimestamp = enqueueTimestamp;
        }

        public int getPriorityWeight() {
            return priorityWeight;
        }

        public void setPriorityWeight(int priorityWeight) {
            this.priorityWeight = priorityWeight;
        }

        public long getSequence() {
            return sequence;
        }

        public void setSequence(long sequence) {
            this.sequence = sequence;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public void setExpiresAtMillis(long expiresAtMillis) {
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
