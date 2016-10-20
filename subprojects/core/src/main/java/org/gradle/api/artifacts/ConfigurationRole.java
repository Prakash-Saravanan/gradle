/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

/**
 * A configuration role defines the role of a configuration during dependency resolution.
 */
@Incubating
public enum ConfigurationRole {
    FOR_SELECTION("For selection in dependency resolution", true),
    FOR_RESOLUTION("For resolution", false);

    private final String description;
    private final boolean canBeUsedInSelection;

    ConfigurationRole(String desc, boolean canBeUsedInSelection) {
        this.description = desc;
        this.canBeUsedInSelection = canBeUsedInSelection;
    }

    public String getDescription() {
        return description;
    }

    public boolean canBeUsedInSelection() {
        return canBeUsedInSelection;
    }
}