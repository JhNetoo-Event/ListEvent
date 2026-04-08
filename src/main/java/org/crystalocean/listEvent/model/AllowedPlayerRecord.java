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
    private Instant expiresAt;
    private String reason;
    private String removedBy;
    private Instant removedAt;
    private Instant lastJoinAt;

    @SuppressWarnings("unused")
    public AllowedPlayerRecord() {
    }

    public AllowedPlayerRecord(UUID uuid, String lastKnownName, String addedBy, String note,
                               Instant addedAt, Instant updatedAt, boolean active,
                               Instant expiresAt, String reason) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.addedBy = addedBy;
        this.note = note;
        this.addedAt = addedAt;
        this.updatedAt = updatedAt;
        this.active = active;
        this.expiresAt = expiresAt;
        this.reason = reason;
    }

    public UUID getUuid() {
        return uuid;
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

    public String getNote() {
        return note;
    }

    public Instant getAddedAt() {
        return addedAt;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(String removedBy) {
        this.removedBy = removedBy;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public Instant getLastJoinAt() {
        return lastJoinAt;
    }

    public void setLastJoinAt(Instant lastJoinAt) {
        this.lastJoinAt = lastJoinAt;
    }
}