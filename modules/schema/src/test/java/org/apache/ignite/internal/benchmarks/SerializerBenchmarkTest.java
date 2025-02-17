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

package org.apache.ignite.internal.benchmarks;

import static org.apache.ignite.internal.schema.NativeTypes.INT64;

import com.facebook.presto.bytecode.Access;
import com.facebook.presto.bytecode.BytecodeBlock;
import com.facebook.presto.bytecode.ClassDefinition;
import com.facebook.presto.bytecode.ClassGenerator;
import com.facebook.presto.bytecode.DynamicClassLoader;
import com.facebook.presto.bytecode.MethodDefinition;
import com.facebook.presto.bytecode.ParameterizedType;
import com.facebook.presto.bytecode.Variable;
import com.facebook.presto.bytecode.expression.BytecodeExpressions;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.annotation.processing.Generated;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.internal.schema.Column;
import org.apache.ignite.internal.schema.SchemaDescriptor;
import org.apache.ignite.internal.schema.marshaller.KvMarshaller;
import org.apache.ignite.internal.schema.marshaller.asm.AsmMarshallerGenerator;
import org.apache.ignite.internal.schema.marshaller.reflection.ReflectionMarshallerFactory;
import org.apache.ignite.internal.schema.row.Row;
import org.apache.ignite.internal.util.Factory;
import org.apache.ignite.internal.util.ObjectFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Serializer benchmark.
 */
@State(Scope.Benchmark)
@Warmup(time = 30, iterations = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(time = 60, iterations = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(jvmArgs = "-Djava.lang.invoke.stringConcat=BC_SB" /* Workaround for Java 9+ */, value = 1)
public class SerializerBenchmarkTest {
    /** Random. */
    private Random rnd = new Random();

    /** Key-value marshaller. */
    private KvMarshaller<Object, Object> marshaller;

    /** Test object factory. */
    private Factory<?> objectFactory;

    /** Object fields count. */
    @Param({"0", "1", "10", "100"})
    public int fieldsCount;

    /** Serializer. */
    @Param({"ASM", "Java"})
    public String serializerName;

    /** Schema. */
    private SchemaDescriptor schema;

    /**
     * Benchmark run method.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(SerializerBenchmarkTest.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }

    /**
     * Initialize.
     */
    @Setup
    public void init() {
        long seed = System.currentTimeMillis();

        rnd = new Random(seed);

        final Class<?> valClass;

        Thread.currentThread().setContextClassLoader(new DynamicClassLoader(AsmMarshallerGenerator.getClassLoader()));

        if (fieldsCount == 0) {
            valClass = Long.class;
            objectFactory = (Factory<Object>) rnd::nextLong;
        } else {
            valClass = createGeneratedObjectClass(fieldsCount, long.class);
            objectFactory = new ObjectFactory<>(valClass);
        }

        schema = new SchemaDescriptor(
                1,
                new Column[]{new Column("key", INT64, true)},
                mapFieldsToColumns(valClass)
        );

        KvMarshaller<?, ?> marshaller = ("Java".equals(serializerName))
                ? new ReflectionMarshallerFactory().create(schema, Long.class, valClass)
                : new AsmMarshallerGenerator().create(schema, Long.class, valClass);

        this.marshaller = (KvMarshaller<Object, Object>) marshaller;
    }

    /**
     * Measure serialization-deserialization operation cost.
     *
     * @param bh Black hole.
     * @throws Exception If failed.
     */
    @Benchmark
    public void measureSerializeDeserializeCost(Blackhole bh) throws Exception {
        Long key = rnd.nextLong();

        Object val = objectFactory.create();
        BinaryRow row = marshaller.marshal(key, val);

        Object restoredKey = marshaller.unmarshalKey(new Row(schema, row));
        Object restoredVal = marshaller.unmarshalValue(new Row(schema, row));

        bh.consume(restoredVal);
        bh.consume(restoredKey);
    }

    /**
     * Map fields to columns.
     *
     * @param cls Object class.
     * @return Columns for schema
     */
    private Column[] mapFieldsToColumns(Class<?> cls) {
        if (cls == Long.class) {
            return new Column[]{new Column("col0", INT64, true)};
        }

        final Field[] fields = cls.getDeclaredFields();
        final Column[] cols = new Column[fields.length];

        for (int i = 0; i < fields.length; i++) {
            assert fields[i].getType() == Long.TYPE : "Only 'long' field type is supported.";

            cols[i] = new Column("col" + i, INT64, false);
        }

        return cols;
    }

    /**
     * Generate class for test objects.
     *
     * @param maxFields Max class member fields.
     * @param fieldType Field type.
     * @return Generated test object class.
     */
    private Class<?> createGeneratedObjectClass(int maxFields, Class<?> fieldType) {
        final String packageName = "org.apache.ignite.internal.benchmarks";
        final String className = "TestObject";

        final ClassDefinition classDef = new ClassDefinition(
                EnumSet.of(Access.PUBLIC),
                packageName.replace('.', '/') + '/' + className,
                ParameterizedType.type(Object.class)
        );
        classDef.declareAnnotation(Generated.class).setValue("value", getClass().getCanonicalName());

        for (int i = 0; i < maxFields; i++) {
            classDef.declareField(EnumSet.of(Access.PRIVATE), "col" + i, ParameterizedType.type(fieldType));
        }

        // Build constructor.
        final MethodDefinition methodDef = classDef.declareConstructor(EnumSet.of(Access.PUBLIC));
        final Variable rnd = methodDef.getScope().declareVariable(Random.class, "rnd");

        final BytecodeBlock body = methodDef.getBody()
                .append(methodDef.getThis())
                .invokeConstructor(classDef.getSuperClass())
                .append(rnd.set(BytecodeExpressions.newInstance(Random.class)));

        for (int i = 0; i < maxFields; i++) {
            body.append(methodDef.getThis().setField("col" + i, rnd.invoke("nextLong", long.class).cast(fieldType)));
        }

        body.ret();

        return ClassGenerator.classGenerator(AsmMarshallerGenerator.getClassLoader()).defineClass(classDef, Object.class);
    }
}
