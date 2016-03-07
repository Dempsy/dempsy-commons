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

/**
 * This is a abstraction for storing/sharing distributed properties. Properties used through this system are versioned. Some implementations may allow retrieval of the Properties history.
 */
public abstract class PropertiesStore {
    /**
     * This will basically clear the existing properties and replace them with the new set. The return value is a version identifier for the new version. The format is specific to the underlying implementation.
     * Version numbers will be increasing but they don't need to be sequential.
     * 
     * @param props
     *            is the {@link Properties} to store.
     * @return the new version of the properties.
     * @throws IOException
     *             if the underlying transport or storage mechanism throws an IOException
     */
    public abstract int push(Properties props) throws IOException;

    /**
     * This will merge the new properties with the old properties overwriting where there are clashes.
     * 
     * @param props
     *            is the {@link Properties} to store.
     * @return the new version of the properties.
     * @throws IOException
     *             if the underlying transport or storage mechanism throws an IOException
     * 
     */
    public abstract int merge(Properties props) throws IOException;

}
