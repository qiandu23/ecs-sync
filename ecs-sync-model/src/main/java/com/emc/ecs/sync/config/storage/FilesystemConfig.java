/*
 * Copyright 2013-2016 EMC Corporation. All Rights Reserved.
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
package com.emc.ecs.sync.config.storage;

import com.emc.ecs.sync.config.AbstractConfig;
import com.emc.ecs.sync.config.annotation.*;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.emc.ecs.sync.config.storage.FilesystemConfig.URI_PREFIX;

@XmlRootElement
@StorageConfig(uriPrefix = URI_PREFIX)
@Label("Filesystem")
@Documentation("The filesystem plugin reads/writes data from/to a file or directory. " +
        "It is triggered by the URI:\n" +
        "file://<path>, e.g. file:///home/user/myfiles\n" +
        "If the URL refers to a file, only that file will be " +
        "synced. If a directory is specified, the contents of " +
        "the directory will be synced.  Unless the --non-recursive " +
        "flag is set, the subdirectories will also be recursively " +
        "synced. To preserve object metadata on the target filesystem, " +
        "or to read back preserved metadata, use --store-metadata.")
public class FilesystemConfig extends AbstractConfig {
    static final String URI_PREFIX = "file:";
    private static final Pattern URI_PATTERN = Pattern.compile("^file:(?://)?(.+)$");

    protected String path;
    private boolean useAbsolutePath = false;
    private boolean followLinks = false;
    private boolean storeMetadata = false;
    private long deleteOlderThan = 0;
    private String deleteCheckScript;
    private String modifiedSince;
    private String[] excludedPaths;

    @XmlTransient
    @UriGenerator
    public String getUri() {
        return URI_PREFIX + path;
    }

    @UriParser
    public void setUri(String uri) {
        Matcher matcher = URI_PATTERN.matcher(uri);
        if (matcher.matches()) {
            path = matcher.group(1);
        } else {
            throw new RuntimeException("invalid file URI");
        }
    }

    @Option(locations = Option.Location.Form, description = "Path to the primary file or directory.")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Option(description = "Uses the absolute path to the file when storing it instead of the relative path from the source dir")
    public boolean isUseAbsolutePath() {
        return useAbsolutePath;
    }

    public void setUseAbsolutePath(boolean useAbsolutePath) {
        this.useAbsolutePath = useAbsolutePath;
    }

    @Option(description = "instead of preserving symbolic links, follow them and sync the actual files")
    public boolean isFollowLinks() {
        return followLinks;
    }

    public void setFollowLinks(boolean followLinks) {
        this.followLinks = followLinks;
    }

    @Option(description = "when used as a target, stores source metadata in a json file, since filesystems have no concept of user metadata")
    public boolean isStoreMetadata() {
        return storeMetadata;
    }

    public void setStoreMetadata(boolean storeMetadata) {
        this.storeMetadata = storeMetadata;
    }

    @Option(valueHint = "delete-age", description = "when --delete-source is used, add this option to only delete files that have been modified more than <delete-age> milliseconds ago")
    public long getDeleteOlderThan() {
        return deleteOlderThan;
    }

    public void setDeleteOlderThan(long deleteOlderThan) {
        this.deleteOlderThan = deleteOlderThan;
    }

    @Option(description = "when --delete-source is used, add this option to execute an external script to check whether a file should be deleted.  If the process exits with return code zero, the file is safe to delete.")
    public String getDeleteCheckScript() {
        return deleteCheckScript;
    }

    public void setDeleteCheckScript(String deleteCheckScript) {
        this.deleteCheckScript = deleteCheckScript;
    }

    @Option(valueHint = "yyyy-MM-ddThh:mm:ssZ", description = "only look at files that have been modified since the specifiec date/time.  Date/time should be provided in ISO-8601 UTC format (i.e. 2015-01-01T04:30:00Z)")
    public String getModifiedSince() {
        return modifiedSince;
    }

    /**
     * Date/time should be provided in ISO-8601 UTC format (i.e. 2015-01-01T04:30:00Z)
     */
    public void setModifiedSince(String modifiedSince) {
        this.modifiedSince = modifiedSince;
    }

    @Option(valueType = Option.ValueType.MultiValue, valueHint = "pattern,pattern,...", description = "A list of regular expressions to search against the full file path.  If the path matches, the file will be skipped.  Since this is a regular expression, take care to escape special characters.  For example, to exclude all files and directories that begin with a period, the pattern would be .*/\\..*")
    public String[] getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(String[] excludedPaths) {
        this.excludedPaths = excludedPaths;
    }
}
