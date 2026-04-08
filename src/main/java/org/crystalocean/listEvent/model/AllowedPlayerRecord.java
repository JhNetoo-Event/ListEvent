package org.crystalocean.listEvent.model;

import java.time.Instant;
import java.util.UUID;

public class AllowedPlayerRecord {

    private UUID uuid;
    private String lastKnownName;
    private String addedBy;
    private String note;
    private Instant addedAt;
    private Instant updatedAt;
    private boolean active;

    public AllowedPlayerRecord() {
    }

    public AllowedPlayerRecord(UUID uuid, String lastKnownName, String addedBy, String note,
                               Instant addedAt, Instant updatedAt, boolean active) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.addedBy = addedBy;
        this.note = note;
        this.addedAt = addedAt;
        this.updatedAt = updatedAt;
        this.active = active;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}