/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.configuration.tree;

import java.util.function.Consumer;

/** */
public interface NamedListInit<T> {
    /**
     * Update the value in named list configuration.
     *
     * @param key Key for the value to be created.
     * @param valConsumer Closure to modify value associated with the key. Object of type {@code T},
     *      passed to the closure, must not be reused anywhere else.
     */
    NamedListInit<T> create(String key, Consumer<T> valConsumer);
}
