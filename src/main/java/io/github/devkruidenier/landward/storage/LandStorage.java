package io.github.devkruidenier.landward.storage;

import io.github.devkruidenier.landward.model.ClaimedChunk;
import io.github.devkruidenier.landward.model.Land;

public interface LandStorage extends AutoCloseable {

    void initialize() throws StorageException;

    StorageSnapshot load() throws StorageException;

    void insertLand(Land land) throws StorageException;

    void insertLandWithClaim(Land land, ClaimedChunk claim) throws StorageException;

    void insertClaim(ClaimedChunk claim) throws StorageException;

    String type();

    @Override
    default void close() throws StorageException {
    }
}
