/*
 * Copyright 2012 the original author or authors.
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

package net.dempsy.distconfig;

import java.io.IOException;
import java.util.Properties;

public abstract class PropertiesLoader {
    /**
     * This will basically clear the existing properties and replace them with the new set. The return value is a version identifier for the new version. The format is specific to the underlying implementation.
     */
    public abstract int push(Properties props) throws IOException;

    /**
     * This will merge the new properties with the old properties overwriting where there are clashes.
     */
    public abstract int merge(Properties props) throws IOException;

}
