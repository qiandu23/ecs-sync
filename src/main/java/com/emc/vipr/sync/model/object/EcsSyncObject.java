/*
 * Copyright 2015 EMC Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.vipr.sync.model.object;

import com.emc.object.s3.S3Client;
import com.emc.object.s3.S3ObjectMetadata;
import com.emc.vipr.sync.SyncPlugin;
import com.emc.vipr.sync.model.Checksum;
import com.emc.vipr.sync.model.SyncMetadata;
import com.emc.vipr.sync.util.S3Util;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EcsSyncObject extends AbstractSyncObject<String> {
    protected SyncPlugin parentPlugin;
    protected S3Client s3;
    protected String bucketName;
    protected String key;

    public EcsSyncObject(SyncPlugin parentPlugin, S3Client s3, String bucketName, String key, String relativePath, boolean isCommonPrefix) {
        super(S3Util.fullPath(bucketName, key), S3Util.fullPath(bucketName, key), relativePath, isCommonPrefix);
        this.parentPlugin = parentPlugin;
        this.s3 = s3;
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public InputStream createSourceInputStream() {
        if (isDirectory()) return null;
        return new BufferedInputStream(s3.getObject(bucketName, key).getObject(), parentPlugin.getBufferSize());
    }

    @Override
    protected void loadObject() {
        if (isDirectory()) return;

        // load metadata
        S3ObjectMetadata s3meta = s3.getObjectMetadata(bucketName, key);
        SyncMetadata meta = toSyncMeta(s3meta);

//        if (parentPlugin.isIncludeAcl()) {
//            meta.setAcl(S3Util.syncAclFromS3Acl(s3.getObjectAcl(bucketName, key)));
//        }
//
        metadata = meta;
    }

    protected SyncMetadata toSyncMeta(S3ObjectMetadata s3meta) {
        SyncMetadata meta = new SyncMetadata();

        meta.setCacheControl(s3meta.getCacheControl());
        meta.setContentDisposition(s3meta.getContentDisposition());
        meta.setContentEncoding(s3meta.getContentEncoding());
        if (s3meta.getContentMd5() != null) meta.setChecksum(new Checksum("MD5", s3meta.getContentMd5()));
        meta.setContentType(s3meta.getContentType());
        meta.setHttpExpires(s3meta.getHttpExpires());
        meta.setExpirationDate(s3meta.getExpirationDate());
        meta.setModificationTime(s3meta.getLastModified());
        meta.setSize(s3meta.getContentLength());
        meta.setUserMetadata(toMetaMap(s3meta.getUserMetadata()));

        return meta;
    }

    protected Map<String, SyncMetadata.UserMetadata> toMetaMap(Map<String, String> sourceMap) {
        Map<String, SyncMetadata.UserMetadata> metaMap = new HashMap<String, SyncMetadata.UserMetadata>();
        for (String key : sourceMap.keySet()) {
            metaMap.put(key, new SyncMetadata.UserMetadata(key, sourceMap.get(key)));
        }
        return metaMap;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }
}
