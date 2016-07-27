package org.corfudb.runtime.object;

import lombok.Getter;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.collections.SMRMap;
import org.corfudb.runtime.view.AbstractViewTest;
import org.corfudb.util.serializer.Serializers;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by mwei on 1/21/16.
 */
public class CorfuSMRObjectProxyTest extends AbstractViewTest {
    @Getter
    final String defaultConfigurationString = getDefaultEndpoint();

    @Test
    @SuppressWarnings("unchecked")
    public void canReadWriteToSingle()
            throws Exception {
        getDefaultRuntime();

        Map<String, String> testMap = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);
        testMap.clear();
        assertThat(testMap.put("a", "a"))
                .isNull();
        assertThat(testMap.put("a", "b"))
                .isEqualTo("a");
        assertThat(testMap.get("a"))
                .isEqualTo("b");

        Map<String, String> testMap2 = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);
        assertThat(testMap2.get("a"))
                .isEqualTo("b");
    }

    @Test
    public void canOpenObjectWithTwoRuntimes()
            throws Exception {
        getDefaultRuntime();

        TestClass testClass = getRuntime().getObjectsView()
                .build()
                .setStreamName("test")
                .setType(TestClass.class)
                .open();

        testClass.set(52);
        assertThat(testClass.get())
                .isEqualTo(52);

        CorfuRuntime runtime2 = new CorfuRuntime();
        wireExistingRuntimeToTest(runtime2);
        runtime2.connect();

        TestClass testClass2 = runtime2.getObjectsView()
                .build()
                .setStreamName("test")
                .setType(TestClass.class)
                .open();

        assertThat(testClass2.get())
                .isEqualTo(52);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void multipleWritesConsistencyTest()
            throws Exception {
        getDefaultRuntime().connect();

        Map<String, String> testMap = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);
        testMap.clear();

        for (int i = 0; i < 10_000; i++) {
            assertThat(testMap.put(Integer.toString(i), Integer.toString(i)))
                    .isNull();
        }

        Map<String, String> testMap2 = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);
        for (int i = 0; i < 10_000; i++) {
            assertThat(testMap2.get(Integer.toString(i)))
                    .isEqualTo(Integer.toString(i));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void multipleWritesConsistencyTestConcurrent()
            throws Exception {
        getDefaultRuntime().connect();


        Map<String, String> testMap = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);
        testMap.clear();
        int num_threads = 5;
        int num_records = 1_000;

        scheduleConcurrently(num_threads, threadNumber -> {
            int base = threadNumber * num_records;
            for (int i = base; i < base + num_records; i++) {
                assertThat(testMap.put(Integer.toString(i), Integer.toString(i)))
                        .isEqualTo(null);
            }
        });
        executeScheduled(num_threads, 50, TimeUnit.SECONDS);

        Map<String, String> testMap2 = getRuntime().getObjectsView().open(
                CorfuRuntime.getStreamID("test"), TreeMap.class);

        scheduleConcurrently(num_threads, threadNumber -> {
            int base = threadNumber * num_records;
            for (int i = base; i < base + num_records; i++) {
                assertThat(testMap2.get(Integer.toString(i)))
                        .isEqualTo(Integer.toString(i));
            }
        });
        executeScheduled(num_threads, 50, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void canWrapObjectWithPrimitiveTypes()
            throws Exception {
        //begin tests
        CorfuRuntime r = getDefaultRuntime().connect();
        TestClassWithPrimitives test = r.getObjectsView().open("test", TestClassWithPrimitives.class);
        test.setPrimitive("hello world".getBytes());
        assertThat(test.getPrimitive())
                .isEqualTo("hello world".getBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void canUseAnnotations()
            throws Exception {
        CorfuRuntime r = getDefaultRuntime();
        TestClassUsingAnnotation test = r.getObjectsView().build()
                .setStreamName("test")
                .setType(TestClassUsingAnnotation.class)
                .open();

        assertThat(test.testFn1())
                .isTrue();

        assertThat(test.testIncrement())
                .isTrue();

        assertThat(test.getValue())
                .isNotZero();

        // clear the cache, forcing a new object to be built.
        r.getObjectsView().getObjectCache().clear();

        TestClassUsingAnnotation test2 = r.getObjectsView().build()
                .setStreamName("test")
                .setType(TestClassUsingAnnotation.class)
                .open();

        assertThat(test)
                .isNotSameAs(test2);

        assertThat(test2.getValue())
                .isNotZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void canUsePrimitiveSerializer()
            throws Exception {
        //begin tests
        CorfuRuntime r = getDefaultRuntime().connect();
        TestClassWithPrimitives test = r.getObjectsView().build()
                .setType(TestClassWithPrimitives.class)
                .setStreamName("test")
                .setSerializer(Serializers.SerializerType.PRIMITIVE)
                .open();
        test.setPrimitive("hello world".getBytes());
        assertThat(test.getPrimitive())
                .isEqualTo("hello world".getBytes());
    }

    @Test
    public void postHandlersFire() throws Exception {
        CorfuRuntime r = getDefaultRuntime();

        Map<String, String> test = r.getObjectsView().build()
                .setType(SMRMap.class)
                .setStreamName("test")
                .open();

        ICorfuSMRObject cObj = (ICorfuSMRObject) test;
        final AtomicInteger ai = new AtomicInteger(0);
        cObj.registerPostHandler((String method, Object[] args, Object state) -> {
            ai.incrementAndGet();
        });
        test.put("a", "b");
        test.get("a");
        assertThat(ai.get())
                .isEqualTo(1);
    }

}
