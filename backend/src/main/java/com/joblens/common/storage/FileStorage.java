package com.joblens.common.storage;

/** Abstraction over where uploaded files live (local disk now, S3 when deployed). */
public interface FileStorage {

    void store(String key, byte[] content);

    byte[] load(String key);

    void delete(String key);
}
