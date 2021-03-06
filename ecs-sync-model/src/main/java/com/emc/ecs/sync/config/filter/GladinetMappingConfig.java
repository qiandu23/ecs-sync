/*
 * Copyright 2013-2017 EMC Corporation. All Rights Reserved.
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
package com.emc.ecs.sync.config.filter;

import com.emc.ecs.sync.config.AbstractConfig;
import com.emc.ecs.sync.config.annotation.Documentation;
import com.emc.ecs.sync.config.annotation.FilterConfig;
import com.emc.ecs.sync.config.annotation.Label;
import com.emc.ecs.sync.config.annotation.Option;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@FilterConfig(cliName = "gladinet-mapping")
@Label("Gladinet Mapper")
@Documentation("This plugin creates the appropriate metadata in Atmos to " +
        "upload data in a fashion compatible with Gladinet's Cloud " +
        "Desktop software when it's hosted by EMC Atmos")
public class GladinetMappingConfig extends AbstractConfig {
    private String gladinetDir;

    @Option(orderIndex = 10, required = true, valueHint = "base-directory",
            description = "Sets the base directory in Gladinet to load content into. This directory must already exist")
    public String getGladinetDir() {
        return gladinetDir;
    }

    public void setGladinetDir(String gladinetDir) {
        this.gladinetDir = gladinetDir;
    }
}
