package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;
import io.github.devkruidenier.landward.model.LandMember;

public interface LandStorage extends AutoCloseable {

    void initialize() throws StorageException;

    StorageSnapshot load() throws StorageException;

    void insertLand(Land land) throws StorageException;

    void insertLandWithClaim(Land land, ClaimedChunk claim) throws StorageException;

    /** Saves a server-owned administrative land and its first protected chunk atomically. */
    void insertAdminLandWithClaim(Land land, ClaimedChunk claim) throws StorageException;

    void updateLandName(java.util.UUID landId, String name) throws StorageException;

    /** Changes the UUID that owns a player land. Names are deliberately not persisted. */
    void updateLandOwner(java.util.UUID landId, java.util.UUID ownerId) throws StorageException;

    void insertClaim(ClaimedChunk claim) throws StorageException;

    void deleteClaim(ClaimedChunk claim) throws StorageException;

    /** Deletes a land and, through the schema foreign keys, all its claims and members. */
    void deleteLand(java.util.UUID landId) throws StorageException;

    void insertMember(LandMember member) throws StorageException;

    void updateMember(LandMember member) throws StorageException;

    void deleteMember(java.util.UUID landId, java.util.UUID playerId) throws StorageException;

    String type();

    @Override
    default void close() throws StorageException {
    }
}
