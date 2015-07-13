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
package com.emc.vipr.sync.target;

import com.emc.object.Protocol;
import com.emc.object.s3.S3Client;
import com.emc.object.s3.S3Config;
import com.emc.object.s3.S3Exception;
import com.emc.object.s3.S3ObjectMetadata;
import com.emc.object.s3.bean.CopyObjectResult;
import com.emc.object.s3.jersey.S3JerseyClient;
import com.emc.object.s3.request.CopyObjectRequest;
import com.emc.vipr.sync.filter.SyncFilter;
import com.emc.vipr.sync.model.object.EcsSyncObject;
import com.emc.vipr.sync.model.object.S3ObjectVersion;
import com.emc.vipr.sync.model.object.S3SyncObject;
import com.emc.vipr.sync.model.object.SyncObject;
import com.emc.vipr.sync.source.S3Source;
import com.emc.vipr.sync.source.SyncSource;
import com.emc.vipr.sync.util.OptionBuilder;
import com.emc.vipr.sync.util.S3Util;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class ECSNamespaceCopyTarget extends SyncTarget {
    private static final Logger l4j = Logger.getLogger(ECSNamespaceCopyTarget.class);

    public static final String BUCKET_OPTION = "ecs-target-bucket";
    public static final String BUCKET_DESC = "Required. Specifies the target bucket to use";
    public static final String BUCKET_ARG_NAME = "bucket";

    public static final String NAMESPACE_OPTION = "ecs-source-namespace";
    public static final String NAMESPACE_DESC = "Required. Specifies the source namespace to use";
    public static final String NAMESPACE_ARG_NAME = "namespace";

    public static final String PREFIX = "ecs-ns-copy:";

    private String protocol;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private S3Source s3Source;
    private String rootKey;
    private String sourceNamespace;

    private S3Client s3;

    @Override
    public boolean canHandleTarget(String targetUri) {
        return targetUri.startsWith(PREFIX);
    }

    @Override
    public Options getCustomOptions() {
        Options opts = new Options();
        opts.addOption(new OptionBuilder().withLongOpt(BUCKET_OPTION).withDescription(BUCKET_DESC)
                .hasArg().withArgName(BUCKET_ARG_NAME).create());
        opts.addOption(new OptionBuilder().withLongOpt(NAMESPACE_OPTION).withDescription(NAMESPACE_DESC)
                .hasArg().withArgName(NAMESPACE_ARG_NAME).create());
        return opts;
    }

    @Override
    protected void parseCustomOptions(CommandLine line) {
        S3Util.S3Uri s3Uri = S3Util.parseUri(targetUri, PREFIX);
        protocol = s3Uri.protocol;
        endpoint = s3Uri.endpoint;
        accessKey = s3Uri.accessKey;
        secretKey = s3Uri.secretKey;
        rootKey = s3Uri.rootKey;

        if (line.hasOption(BUCKET_OPTION)) bucketName = line.getOptionValue(BUCKET_OPTION);
        sourceNamespace = line.getOptionValue(NAMESPACE_OPTION);
    }

    @Override
    public void configure(SyncSource source, Iterator<SyncFilter> filters, SyncTarget target) {
        Assert.hasText(accessKey, "accessKey is required");
        Assert.hasText(secretKey, "secretKey is required");
        Assert.hasText(bucketName, "bucketName is required");
        Assert.isTrue(bucketName.matches("[A-Za-z0-9._-]+"), bucketName + " is not a valid bucket name");

        URI uri = null;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        S3Config config = new S3Config(Protocol.valueOf(protocol.toUpperCase()), uri.getHost());
        config.withIdentity(accessKey).withSecretKey(secretKey);
        s3 = new S3JerseyClient(config);

        // TODO: generalize uri translation
        S3Util.S3Uri s3Uri = new S3Util.S3Uri();
        s3Uri.protocol = protocol;
        s3Uri.endpoint = endpoint;
        s3Uri.accessKey = accessKey;
        s3Uri.secretKey = secretKey;
        s3Uri.rootKey = rootKey;
        if (targetUri == null) targetUri = s3Uri.toUri();

        if (rootKey == null) rootKey = ""; // make sure rootKey isn't null
    }

    @Override
    public void filter(SyncObject obj) {
        try {
            // S3 doesn't support directories.
            if (obj.isDirectory()) {
                l4j.debug("Skipping directory object in S3Target: " + obj.getRelativePath());
                return;
            }

            // some sync objects lazy-load their metadata (i.e. AtmosSyncObject)
            // since this may be a timed operation, ensure it loads outside of other timed operations
            if (!(obj instanceof S3ObjectVersion) || !((S3ObjectVersion) obj).isDeleteMarker())
                obj.getMetadata();

            // Compute target key
            String targetKey = getTargetKey(obj);
            obj.setTargetIdentifier(S3Util.fullPath(bucketName, targetKey));

            Date sourceLastModified = obj.getMetadata().getModificationTime();
            long sourceSize = obj.getMetadata().getSize();

            // Get target metadata.
            S3ObjectMetadata destMeta = null;
            try {
                destMeta = s3.getObjectMetadata(bucketName, targetKey);
            } catch (S3Exception e) {
                if (e.getHttpCode() == 404) {
                    // OK
                } else {
                    throw new RuntimeException("Failed to check target key '" + targetKey + "' : " + e, e);
                }
            }

            if (!force && destMeta != null) {

                // Check overwrite
                Date destLastModified = destMeta.getLastModified();
                long destSize = destMeta.getContentLength();

                if (destLastModified.equals(sourceLastModified) && sourceSize == destSize) {
                    l4j.info(String.format("Source and target the same.  Skipping %s", obj.getRelativePath()));
                    return;
                }
                if (destLastModified.after(sourceLastModified)) {
                    l4j.info(String.format("Target newer than source.  Skipping %s", obj.getRelativePath()));
                    return;
                }
            }

            // at this point we know we are going to write the object
            S3SyncObject srcObj = (S3SyncObject)obj;
            CopyObjectRequest req = new CopyObjectRequest(srcObj.getBucketName(), srcObj.getKey(), bucketName, targetKey);
            req.withSourceNamespace(sourceNamespace);

            LogMF.debug(l4j, "SS Copy: %s:%s/%s -> %s/%s", new Object[] {sourceNamespace, srcObj.getBucketName(), srcObj.getKey(), bucketName, targetKey});

            CopyObjectResult resp = s3.copyObject(req);

            l4j.debug(String.format("Wrote %s etag: %s", targetKey, resp.getETag()));

        } catch (Exception e) {
            throw new RuntimeException("Failed to store object: " + e, e);
        }
    }


    @Override
    public SyncObject reverseFilter(SyncObject obj) {
        return new EcsSyncObject(this, s3, bucketName, getTargetKey(obj), obj.getRelativePath(), obj.isDirectory());
    }

    private String getTargetKey(SyncObject obj) {
        return rootKey + obj.getRelativePath();
    }

    @Override
    public String getName() {
        return "S3 Target";
    }

    @Override
    public String getDocumentation() {
        return "Target that writes content to an S3 bucket.  This " +
                "target plugin is triggered by the pattern:\n" +
                S3Util.PATTERN_DESC + "\n" +
                "Scheme, host and port are all optional. If ommitted, " +
                "https://s3.amazonaws.com:443 is assumed. " +
                "root-prefix (optional) is the prefix to prepend to key names " +
                "when writing objects e.g. dir1/. If omitted, objects " +
                "will be written to the root of the bucket. Note that this plugin also " +
                "accepts the --force option to force overwriting target objects " +
                "even if they are the same or newer than the source.";
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRootKey() {
        return rootKey;
    }

    public void setRootKey(String rootKey) {
        this.rootKey = rootKey;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

}
