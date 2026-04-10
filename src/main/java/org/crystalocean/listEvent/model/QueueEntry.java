package org.crystalocean.listEvent.model;

import java.util.UUID;

public record QueueEntry(UUID uuid, long enqueueTimestamp, int priorityWeight, long sequence) implements Comparable<QueueEntry> {

    @Override
    public int compareTo(QueueEntry other) {
        int priorityCompare = Integer.compare(other.priorityWeight, this.priorityWeight);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        int timeCompare = Long.compare(this.enqueueTimestamp, other.enqueueTimestamp);
        if (timeCompare != 0) {
            return timeCompare;
        }

        return Long.compare(this.sequence, other.sequence);
    }
}
