/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.sql.engine.exec;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.ignite.internal.sql.engine.exec.rel.ModifyNode;
import org.apache.ignite.internal.sql.engine.schema.TableDescriptor;
import org.apache.ignite.internal.sql.engine.type.IgniteTypeFactory;

/**
 * The interface describe a table that could be updated by {@link ModifyNode}.
 */
public interface UpdateableTable {
    /** Returns descriptor of the table. */
    TableDescriptor descriptor();

    /**
     * Inserts rows into the table.
     *
     * <p>The rows passed should match the insert row type defined by the table's {@link #descriptor() descriptor}
     * (see {@link TableDescriptor#insertRowType(IgniteTypeFactory)}).
     *
     * @param ectx An execution context.
     * @param rows Rows to insert.
     * @param <RowT> A type of the row sql runtime working with.
     * @return A future representing the completion of the operation.
     */
    <RowT> CompletableFuture<?> insertAll(
            ExecutionContext<RowT> ectx,
            List<RowT> rows
    );

    /**
     * Updates rows if they are exists, inserts the rows otherwise.
     *
     * <p>The rows passed should match the full row type defined by the table's {@link #descriptor() descriptor}
     * (see {@link TableDescriptor#rowType(IgniteTypeFactory, ImmutableBitSet)}).
     *
     * @param ectx An execution context.
     * @param rows Rows to upsert.
     * @param <RowT> A type of the row sql runtime working with.
     * @return A future representing the completion of the operation.
     */
    <RowT> CompletableFuture<?> upsertAll(
            ExecutionContext<RowT> ectx,
            List<RowT> rows
    );

    /**
     * Removes rows from the table if they are exactly the same as any of the specified rows.
     *
     * <p>Though this method has delete-exact semantic, implementations are allowed to apply optimisations like delete by primary key.
     * The columns contained in the row for deletion are defined by the table's {@link #descriptor() descriptor}
     * (see {@link TableDescriptor#deleteRowType(IgniteTypeFactory)}).
     *
     * @param ectx An execution context.
     * @param rows Rows to delete.
     * @param <RowT> A type of the row sql runtime working with.
     * @return A future representing the completion of the operation.
     */
    <RowT> CompletableFuture<?> deleteAll(
            ExecutionContext<RowT> ectx,
            List<RowT> rows
    );
}
