/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fury;

import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Data;
import org.apache.fury.config.FuryBuilder;
import org.apache.fury.config.Language;
import org.apache.fury.logging.Logger;
import org.apache.fury.logging.LoggerFactory;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.apache.fury.serializer.ArraySerializersTest;
import org.apache.fury.serializer.BufferObject;
import org.apache.fury.serializer.EnumSerializerTest;
import org.apache.fury.serializer.ObjectSerializer;
import org.apache.fury.serializer.Serializer;
import org.apache.fury.test.TestUtils;
import org.apache.fury.type.Descriptor;
import org.apache.fury.util.DateTimeUtils;
import org.apache.fury.util.MurmurHash3;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests in this class need fury python installed. */
@Test
public class CrossLanguageTest extends FuryTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(CrossLanguageTest.class);
  private static final String PYTHON_MODULE = "pyfury.tests.test_cross_language";
  private static final String PYTHON_EXECUTABLE = "python";

  /**
   * Execute an external command.
   *
   * @return Whether the command succeeded.
   */
  private boolean executeCommand(List<String> command, int waitTimeoutSeconds) {
    return TestUtils.executeCommand(
        command, waitTimeoutSeconds, ImmutableMap.of("ENABLE_CROSS_LANGUAGE_TESTS", "true"));
  }

  @Data
  public static class A {
    public Integer f1;
    public Map<String, String> f2;

    public static A create() {
      A a = new A();
      a.f1 = 1;
      a.f2 = new HashMap<>();
      a.f2.put("pid", "12345");
      a.f2.put("ip", "0.0.0.0");
      a.f2.put("k1", "v1");
      return a;
    }
  }

  @Test
  public void testBuffer() throws IOException {
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    buffer.writeBoolean(true);
    buffer.writeByte(Byte.MAX_VALUE);
    buffer.writeInt16(Short.MAX_VALUE);
    buffer.writeInt32(Integer.MAX_VALUE);
    buffer.writeInt64(Long.MAX_VALUE);
    buffer.writeFloat32(-1.1f);
    buffer.writeFloat64(-1.1);
    buffer.writeVarUint32(100);
    byte[] bytes = {'a', 'b'};
    buffer.writeInt32(bytes.length);
    buffer.writeBytes(bytes);
    Path dataFile = Files.createTempFile("test_buffer", "data");
    Files.write(dataFile, buffer.getBytes(0, buffer.writerIndex()));
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_buffer",
            dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    buffer = MemoryUtils.wrap(Files.readAllBytes(dataFile));
    Assert.assertTrue(buffer.readBoolean());
    Assert.assertEquals(buffer.readByte(), Byte.MAX_VALUE);
    Assert.assertEquals(buffer.readInt16(), Short.MAX_VALUE);
    Assert.assertEquals(buffer.readInt32(), Integer.MAX_VALUE);
    Assert.assertEquals(buffer.readInt64(), Long.MAX_VALUE);
    Assert.assertEquals(buffer.readFloat32(), -1.1f, 0.0001);
    Assert.assertEquals(buffer.readFloat64(), -1.1, 0.0001);
    Assert.assertEquals(buffer.readVarUint32(), 100);
    Assert.assertTrue(Arrays.equals(buffer.readBytes(buffer.readInt32()), bytes));
  }

  @Test
  public void testMurmurHash3() throws IOException {
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    byte[] hash1 = Hashing.murmur3_128(47).hashBytes(new byte[] {1, 2, 8}).asBytes();
    buffer.writeBytes(hash1);
    byte[] hash2 =
        Hashing.murmur3_128(47)
            .hashBytes("01234567890123456789".getBytes(StandardCharsets.UTF_8))
            .asBytes();
    buffer.writeBytes(hash2);
    Path dataFile = Files.createTempFile("test_murmurhash3", "data");
    Files.write(dataFile, buffer.getBytes(0, buffer.writerIndex()));
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_murmurhash3",
            dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    long[] longs = MurmurHash3.murmurhash3_x64_128(new byte[] {1, 2, 8}, 0, 3, 47);
    buffer.writerIndex(0);
    buffer.writeInt64(longs[0]);
    buffer.writeInt64(longs[1]);
    Files.write(
        dataFile, buffer.getBytes(0, buffer.writerIndex()), StandardOpenOption.TRUNCATE_EXISTING);
    Assert.assertTrue(executeCommand(command, 30));
  }

  /** Keep this in sync with `foo_schema` in test_cross_language.py */
  @Data
  public static class Foo {
    public Integer f1;
    public String f2;
    public List<String> f3;
    public Map<String, Integer> f4;
    public Bar f5;

    public static Foo create() {
      Foo foo = new Foo();
      foo.f1 = 1;
      foo.f2 = "str";
      foo.f3 = Arrays.asList("str1", null, "str2");
      foo.f4 =
          new HashMap<String, Integer>() {
            {
              put("k1", 1);
              put("k2", 2);
              put("k3", 3);
              put("k4", 4);
              put("k5", 5);
              put("k6", 6);
            }
          };
      foo.f5 = Bar.create();
      return foo;
    }
  }

  /** Keep this in sync with `bar_schema` in test_cross_language.py */
  @Data
  public static class Bar {
    public Integer f1;
    public String f2;

    public static Bar create() {
      Bar bar = new Bar();
      bar.f1 = 1;
      bar.f2 = "str";
      return bar;
    }
  }

  @Test
  public void testCrossLanguageSerializer() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    fury.serialize(buffer, true);
    fury.serialize(buffer, false);
    fury.serialize(buffer, -1);
    fury.serialize(buffer, Byte.MAX_VALUE);
    fury.serialize(buffer, Byte.MIN_VALUE);
    fury.serialize(buffer, Short.MAX_VALUE);
    fury.serialize(buffer, Short.MIN_VALUE);
    fury.serialize(buffer, Integer.MAX_VALUE);
    fury.serialize(buffer, Integer.MIN_VALUE);
    fury.serialize(buffer, Long.MAX_VALUE);
    fury.serialize(buffer, Long.MIN_VALUE);
    fury.serialize(buffer, -1.f);
    fury.serialize(buffer, -1.d);
    fury.serialize(buffer, "str");
    LocalDate day = LocalDate.of(2021, 11, 23);
    fury.serialize(buffer, day);
    Instant instant = Instant.ofEpochSecond(100);
    fury.serialize(buffer, instant);
    List<Object> list = Arrays.asList("a", 1, -1.0, instant, day);
    fury.serialize(buffer, list);
    Map<Object, Object> map = new HashMap<>();
    for (int i = 0; i < list.size(); i++) {
      map.put("k" + i, list.get(i));
      map.put(list.get(i), list.get(i));
    }
    fury.serialize(buffer, map);
    Set<Object> set = new HashSet<>(list);
    fury.serialize(buffer, set);

    // test primitive arrays
    fury.serialize(buffer, new boolean[] {true, false});
    fury.serialize(buffer, new short[] {1, Short.MAX_VALUE});
    fury.serialize(buffer, new int[] {1, Integer.MAX_VALUE});
    fury.serialize(buffer, new long[] {1, Long.MAX_VALUE});
    fury.serialize(buffer, new float[] {1.f, 2.f});
    fury.serialize(buffer, new double[] {1.0, 2.0});

    BiConsumer<MemoryBuffer, Boolean> function =
        (MemoryBuffer buf, Boolean useToString) -> {
          assertStringEquals(fury.deserialize(buf), true, useToString);
          assertStringEquals(fury.deserialize(buf), false, useToString);
          assertStringEquals(fury.deserialize(buf), -1, useToString);
          assertStringEquals(fury.deserialize(buf), Byte.MAX_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Byte.MIN_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Short.MAX_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Short.MIN_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Integer.MAX_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Integer.MIN_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Long.MAX_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), Long.MIN_VALUE, useToString);
          assertStringEquals(fury.deserialize(buf), -1.f, useToString);
          assertStringEquals(fury.deserialize(buf), -1.d, useToString);
          assertStringEquals(fury.deserialize(buf), "str", useToString);
          assertStringEquals(fury.deserialize(buf), day, useToString);
          assertStringEquals(fury.deserialize(buf), instant, useToString);
          assertStringEquals(fury.deserialize(buf), list, useToString);
          assertStringEquals(fury.deserialize(buf), map, useToString);
          assertStringEquals(fury.deserialize(buf), set, useToString);
          assertStringEquals(fury.deserialize(buf), new boolean[] {true, false}, false);
          assertStringEquals(fury.deserialize(buf), new short[] {1, Short.MAX_VALUE}, false);
          assertStringEquals(fury.deserialize(buf), new int[] {1, Integer.MAX_VALUE}, false);
          assertStringEquals(fury.deserialize(buf), new long[] {1, Long.MAX_VALUE}, false);
          assertStringEquals(fury.deserialize(buf), new float[] {1.f, 2.f}, false);
          assertStringEquals(fury.deserialize(buf), new double[] {1.0, 2.0}, false);
        };
    function.accept(buffer, false);

    Path dataFile = Files.createTempFile("test_cross_language_serializer", "data");
    // Files.deleteIfExists(Paths.get("test_cross_language_serializer.data"));
    // Path dataFile = Files.createFile(Paths.get("test_cross_language_serializer.data"));
    Files.write(dataFile, buffer.getBytes(0, buffer.writerIndex()));
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_cross_language_serializer",
            dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    MemoryBuffer buffer2 = MemoryUtils.wrap(Files.readAllBytes(dataFile));
    function.accept(buffer2, true);
  }

  @Test
  public void testCrossLanguagePreserveTypes() {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    Assert.assertEquals(new String[] {"str", "str"}, serDeTyped(fury, new String[] {"str", "str"}));
    Assert.assertEquals(new Object[] {"str", 1}, serDeTyped(fury, new Object[] {"str", 1}));
    Assert.assertTrue(
        Arrays.deepEquals(
            new Integer[][] {{1, 2}, {1, 2}}, serDeTyped(fury, new Integer[][] {{1, 2}, {1, 2}})));

    Assert.assertEquals(Arrays.asList(1, 2), xserDe(fury, Arrays.asList(1, 2)));
    List<String> arrayList = Arrays.asList("str", "str");
    Assert.assertEquals(arrayList, xserDe(fury, arrayList));
    Assert.assertEquals(new LinkedList<>(arrayList), xserDe(fury, new LinkedList<>(arrayList)));
    Assert.assertEquals(new HashSet<>(arrayList), xserDe(fury, new HashSet<>(arrayList)));
    TreeSet<String> treeSet = new TreeSet<>(Comparator.naturalOrder());
    treeSet.add("str1");
    treeSet.add("str2");
    Assert.assertEquals(treeSet, xserDe(fury, treeSet));

    HashMap<String, Integer> hashMap = new HashMap<>();
    hashMap.put("k1", 1);
    hashMap.put("k2", 2);
    Assert.assertEquals(hashMap, xserDe(fury, hashMap));
    Assert.assertEquals(new LinkedHashMap<>(hashMap), xserDe(fury, new LinkedHashMap<>(hashMap)));
    Assert.assertEquals(Collections.EMPTY_LIST, xserDe(fury, Collections.EMPTY_LIST));
    Assert.assertEquals(Collections.EMPTY_SET, xserDe(fury, Collections.EMPTY_SET));
    Assert.assertEquals(Collections.EMPTY_MAP, xserDe(fury, Collections.EMPTY_MAP));
    Assert.assertEquals(
        Collections.singletonList("str"), xserDe(fury, Collections.singletonList("str")));
    Assert.assertEquals(Collections.singleton("str"), xserDe(fury, Collections.singleton("str")));
    Assert.assertEquals(
        Collections.singletonMap("k", 1), xserDe(fury, Collections.singletonMap("k", 1)));
  }

  @SuppressWarnings("unchecked")
  private void assertStringEquals(Object actual, Object expected, boolean useToString) {
    if (useToString) {
      if (expected instanceof Map) {
        Map actualMap =
            (Map)
                ((Map) actual)
                    .entrySet().stream()
                        .collect(
                            Collectors.toMap(
                                (Map.Entry e) -> e.getKey().toString(),
                                (Map.Entry e) -> e.getValue().toString()));
        Map expectedMap =
            (Map)
                ((Map) expected)
                    .entrySet().stream()
                        .collect(
                            Collectors.toMap(
                                (Map.Entry e) -> e.getKey().toString(),
                                (Map.Entry e) -> e.getValue().toString()));
        Assert.assertEquals(actualMap, expectedMap);
      } else if (expected instanceof Set) {
        Object actualSet =
            ((Set) actual).stream().map(Object::toString).collect(Collectors.toSet());
        Object expectedSet =
            ((Set) expected).stream().map(Object::toString).collect(Collectors.toSet());
        Assert.assertEquals(actualSet, expectedSet);
      } else {
        Assert.assertEquals(actual.toString(), expected.toString());
      }
    } else {
      Assert.assertEquals(actual, expected);
    }
  }

  private Object xserDe(Fury fury, Object obj) {
    byte[] bytes = fury.serialize(obj);
    return fury.deserialize(bytes);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testCrossLanguageReference() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    List<Object> list = new ArrayList<>();
    Map<Object, Object> map = new HashMap<>();
    list.add(list);
    list.add(map);
    map.put("k1", map);
    map.put("k2", list);
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    fury.serialize(buffer, list);

    Consumer<MemoryBuffer> function =
        (MemoryBuffer buf) -> {
          List<Object> newList = (List<Object>) fury.deserialize(buf);
          Assert.assertNotNull(newList);
          Assert.assertSame(newList, newList.get(0));
          Map<Object, Object> newMap = (Map<Object, Object>) newList.get(1);
          Assert.assertSame(newMap.get("k1"), newMap);
          Assert.assertSame(newMap.get("k2"), newList);
        };

    Path dataFile = Files.createTempFile("test_cross_language_reference", "data");
    // Files.deleteIfExists(Paths.get("test_cross_language_reference.data"));
    // Path dataFile = Files.createFile(Paths.get("test_cross_language_reference.data"));
    Files.write(dataFile, buffer.getBytes(0, buffer.writerIndex()));
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_cross_language_reference",
            dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    MemoryBuffer buffer2 = MemoryUtils.wrap(Files.readAllBytes(dataFile));
    function.accept(buffer2);
  }

  @Data
  public static class ComplexObject1 {
    Object f1;
    String f2;
    List<String> f3;
    Map<Byte, Integer> f4;
    Byte f5;
    Short f6;
    Integer f7;
    Long f8;
    Float f9;
    Double f10;
    short[] f11;
    List<Short> f12;
  }

  @Data
  public static class ComplexObject2 {
    Object f1;
    Map<Byte, Integer> f2;
  }

  @Test
  public void testStructHash() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    fury.register(ComplexObject1.class, "test.ComplexObject1");
    fury.serialize(new ComplexObject1()); // trigger serializer update
    ObjectSerializer serializer = (ObjectSerializer) fury.getSerializer(ComplexObject1.class);
    Method method =
        ObjectSerializer.class.getDeclaredMethod("computeStructHash", Fury.class, Collection.class);
    method.setAccessible(true);
    Collection<Descriptor> descriptors =
        fury.getClassResolver().getFieldDescriptors(ComplexObject1.class, true);
    descriptors =
        fury.getClassResolver().createDescriptorGrouper(descriptors, false).getSortedDescriptors();
    Integer hash = (Integer) method.invoke(serializer, fury, descriptors);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(4);
    buffer.writeInt32(hash);
    roundBytes("test_struct_hash", buffer.getBytes(0, 4));
  }

  @Test
  public void testSerializeSimpleStruct() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    fury.register(ComplexObject2.class, "test.ComplexObject2");
    ComplexObject2 obj2 = new ComplexObject2();
    obj2.f1 = true;
    obj2.f2 = new HashMap<>(ImmutableMap.of((byte) -1, 2));
    structRoundBack(fury, obj2, "test_serialize_simple_struct");
  }

  public void testSerializeComplexStruct() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    fury.register(ComplexObject1.class, "test.ComplexObject1");
    fury.register(ComplexObject2.class, "test.ComplexObject2");
    ComplexObject2 obj2 = new ComplexObject2();
    obj2.f1 = true;
    obj2.f2 = ImmutableMap.of((byte) -1, 2);
    ComplexObject1 obj = new ComplexObject1();
    obj.f1 = obj2;
    obj.f2 = "abc";
    obj.f3 = Arrays.asList("abc", "abc");
    obj.f4 = ImmutableMap.of((byte) 1, 2);
    obj.f5 = Byte.MAX_VALUE;
    obj.f6 = Short.MAX_VALUE;
    obj.f7 = Integer.MAX_VALUE;
    obj.f8 = Long.MAX_VALUE;
    obj.f9 = 1.0f / 2;
    obj.f10 = 1 / 3.0;
    obj.f11 = new short[] {(short) 1, (short) 2};
    obj.f12 = ImmutableList.of((short) -1, (short) 4);

    structRoundBack(fury, obj, "test_serialize_complex_struct");
  }

  private void structRoundBack(Fury fury, Object obj, String testName) throws IOException {
    byte[] serialized = fury.serialize(obj);
    Assert.assertEquals(fury.deserialize(serialized), obj);
    Path dataFile = Paths.get(testName);
    System.out.println(dataFile.toAbsolutePath());
    Files.deleteIfExists(dataFile);
    Files.write(dataFile, serialized);
    dataFile.toFile().deleteOnExit();
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE, "-m", PYTHON_MODULE, testName, dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    Assert.assertEquals(fury.deserialize(Files.readAllBytes(dataFile)), obj);
  }

  private static class ComplexObject1Serializer extends Serializer<ComplexObject1> {

    public ComplexObject1Serializer(Fury fury, Class<ComplexObject1> cls) {
      super(fury, cls);
    }

    @Override
    public void write(MemoryBuffer buffer, ComplexObject1 value) {
      xwrite(buffer, value);
    }

    @Override
    public ComplexObject1 read(MemoryBuffer buffer) {
      return xread(buffer);
    }

    @Override
    public void xwrite(MemoryBuffer buffer, ComplexObject1 value) {
      fury.xwriteRef(buffer, value.f1);
      fury.xwriteRef(buffer, value.f2);
      fury.xwriteRef(buffer, value.f3);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ComplexObject1 xread(MemoryBuffer buffer) {
      ComplexObject1 obj = new ComplexObject1();
      fury.getRefResolver().reference(obj);
      obj.f1 = fury.xreadRef(buffer);
      obj.f2 = (String) fury.xreadRef(buffer);
      obj.f3 = (List<String>) fury.xreadRef(buffer);
      return obj;
    }
  }

  @Test
  public void testRegisterSerializer() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    fury.register(ComplexObject1.class, "test.ComplexObject1");
    fury.registerSerializer(ComplexObject1.class, ComplexObject1Serializer.class);
    ComplexObject1 obj = new ComplexObject1();
    obj.f1 = true;
    obj.f2 = "abc";
    obj.f3 = Arrays.asList("abc", "abc");
    byte[] serialized = fury.serialize(obj);
    Assert.assertEquals(fury.deserialize(serialized), obj);
    Path dataFile = Files.createTempFile("test_register_serializer", "data");
    Files.write(dataFile, serialized);
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_register_serializer",
            dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    Assert.assertEquals(fury.deserialize(Files.readAllBytes(dataFile)), obj);
  }

  private byte[] roundBytes(String testName, byte[] bytes) throws IOException {
    Path dataFile = Paths.get(testName);
    System.out.println(dataFile.toAbsolutePath());
    Files.deleteIfExists(dataFile);
    Files.write(dataFile, bytes);
    dataFile.toFile().deleteOnExit();
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE, "-m", PYTHON_MODULE, testName, dataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));
    return Files.readAllBytes(dataFile);
  }

  @Test
  public void testOutOfBandBuffer() throws Exception {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(true)
            .requireClassRegistration(false)
            .build();
    AtomicInteger counter = new AtomicInteger(0);
    List<byte[]> data =
        IntStream.range(0, 10).mapToObj(i -> new byte[] {0, 1}).collect(Collectors.toList());
    List<BufferObject> bufferObjects = new ArrayList<>();
    byte[] serialized =
        fury.serialize(
            data,
            o -> {
              if (counter.incrementAndGet() % 2 == 0) {
                bufferObjects.add(o);
                return false;
              } else {
                return true;
              }
            });
    List<MemoryBuffer> buffers =
        bufferObjects.stream().map(BufferObject::toBuffer).collect(Collectors.toList());
    List<byte[]> newObj = (List<byte[]>) fury.deserialize(serialized, buffers);
    for (int i = 0; i < data.size(); i++) {
      Assert.assertEquals(data.get(i), newObj.get(i));
    }

    Path intBandDataFile = Files.createTempFile("test_oob_buffer_in_band", "data");
    Files.write(intBandDataFile, serialized);
    Path outOfBandDataFile = Files.createTempFile("test_oob_buffer_out_of_band", "data");
    Files.deleteIfExists(outOfBandDataFile);
    Files.createFile(outOfBandDataFile);
    MemoryBuffer outOfBandBuffer = MemoryBuffer.newHeapBuffer(32);
    outOfBandBuffer.writeInt32(buffers.size());
    for (int i = 0; i < buffers.size(); i++) {
      outOfBandBuffer.writeInt32(bufferObjects.get(i).totalBytes());
      bufferObjects.get(i).writeTo(outOfBandBuffer);
    }
    Files.write(outOfBandDataFile, outOfBandBuffer.getBytes(0, outOfBandBuffer.writerIndex()));
    ImmutableList<String> command =
        ImmutableList.of(
            PYTHON_EXECUTABLE,
            "-m",
            PYTHON_MODULE,
            "test_oob_buffer",
            intBandDataFile.toAbsolutePath().toString(),
            outOfBandDataFile.toAbsolutePath().toString());
    Assert.assertTrue(executeCommand(command, 30));

    MemoryBuffer inBandBuffer = MemoryUtils.wrap(Files.readAllBytes(intBandDataFile));
    outOfBandBuffer = MemoryUtils.wrap(Files.readAllBytes(outOfBandDataFile));
    int numBuffers = outOfBandBuffer.readInt32();
    buffers = new ArrayList<>();
    for (int i = 0; i < numBuffers; i++) {
      int len = outOfBandBuffer.readInt32();
      int readerIndex = outOfBandBuffer.readerIndex();
      buffers.add(outOfBandBuffer.slice(readerIndex, len));
      outOfBandBuffer.readerIndex(readerIndex + len);
    }
    newObj = (List<byte[]>) fury.deserialize(inBandBuffer, buffers);
    Assert.assertNotNull(newObj);
    for (int i = 0; i < data.size(); i++) {
      Assert.assertEquals(data.get(i), newObj.get(i));
    }
  }

  @Data
  static class ArrayStruct {
    ArrayField[] f1;
  }

  @Data
  static class ArrayField {
    public String a;
  }

  @Test
  public void testStructArrayField() {
    Fury fury = Fury.builder().withLanguage(Language.XLANG).requireClassRegistration(true).build();
    fury.register(ArrayStruct.class, "example.bar");
    fury.register(ArrayField.class, "example.foo");

    ArrayField a = new ArrayField();
    a.a = "123";
    ArrayStruct struct = new ArrayStruct();
    struct.f1 = new ArrayField[] {a};
    Assert.assertEquals(xserDe(fury, struct), struct);
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void basicTest(boolean referenceTracking) {
    FuryBuilder builder =
        Fury.builder()
            .withLanguage(Language.XLANG)
            .withRefTracking(referenceTracking)
            .requireClassRegistration(false);
    Fury fury1 = builder.build();
    Fury fury2 = builder.build();
    assertEquals("str", serDe(fury1, fury2, "str"));
    assertEquals("str", serDeObject(fury1, fury2, new StringBuilder("str")).toString());
    assertEquals("str", serDeObject(fury1, fury2, new StringBuffer("str")).toString());
    fury1.register(EnumSerializerTest.EnumFoo.class);
    fury2.register(EnumSerializerTest.EnumFoo.class);
    fury1.register(EnumSerializerTest.EnumSubClass.class);
    fury2.register(EnumSerializerTest.EnumSubClass.class);
    assertEquals(EnumSerializerTest.EnumFoo.A, serDe(fury1, fury2, EnumSerializerTest.EnumFoo.A));
    assertEquals(EnumSerializerTest.EnumFoo.B, serDe(fury1, fury2, EnumSerializerTest.EnumFoo.B));
    assertEquals(
        EnumSerializerTest.EnumSubClass.A, serDe(fury1, fury2, EnumSerializerTest.EnumSubClass.A));
    assertEquals(
        EnumSerializerTest.EnumSubClass.B, serDe(fury1, fury2, EnumSerializerTest.EnumSubClass.B));
    java.sql.Date sqlDate = new java.sql.Date(System.currentTimeMillis());
    assertEquals(sqlDate, serDeTyped(fury1, fury2, sqlDate));
    LocalDate localDate = LocalDate.now();
    assertEquals(localDate, serDeTyped(fury1, fury2, localDate));
    Date utilDate = new Date();
    assertEquals(utilDate, serDeTyped(fury1, fury2, utilDate));
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    assertEquals(timestamp, serDeTyped(fury1, fury2, timestamp));
    Instant instant = DateTimeUtils.truncateInstantToMicros(Instant.now());
    assertEquals(instant, serDeTyped(fury1, fury2, instant));

    ArraySerializersTest.testPrimitiveArray(fury1, fury2);

    assertEquals(Arrays.asList(1, 2), serDe(fury1, fury2, Arrays.asList(1, 2)));
    List<String> arrayList = Arrays.asList("str", "str");
    assertEquals(arrayList, serDe(fury1, fury2, arrayList));
    assertEquals(new LinkedList<>(arrayList), serDe(fury1, fury2, new LinkedList<>(arrayList)));
    assertEquals(new HashSet<>(arrayList), serDe(fury1, fury2, new HashSet<>(arrayList)));
    TreeSet<String> treeSet = new TreeSet<>(Comparator.naturalOrder());
    treeSet.add("str1");
    treeSet.add("str2");
    assertEquals(treeSet, serDe(fury1, fury2, treeSet));

    HashMap<String, Integer> hashMap = new HashMap<>();
    hashMap.put("k1", 1);
    hashMap.put("k2", 2);
    assertEquals(hashMap, serDe(fury1, fury2, hashMap));
    assertEquals(new LinkedHashMap<>(hashMap), serDe(fury1, fury2, new LinkedHashMap<>(hashMap)));
    TreeMap<String, Integer> treeMap = new TreeMap<>(Comparator.naturalOrder());
    treeMap.putAll(hashMap);
    assertEquals(treeMap, serDe(fury1, fury2, treeMap));
    assertEquals(Collections.EMPTY_LIST, serDe(fury1, fury2, Collections.EMPTY_LIST));
    assertEquals(Collections.EMPTY_SET, serDe(fury1, fury2, Collections.EMPTY_SET));
    assertEquals(Collections.EMPTY_MAP, serDe(fury1, fury2, Collections.EMPTY_MAP));
    assertEquals(
        Collections.singletonList("str"), serDe(fury1, fury2, Collections.singletonList("str")));
    assertEquals(Collections.singleton("str"), serDe(fury1, fury2, Collections.singleton("str")));
    assertEquals(
        Collections.singletonMap("k", 1), serDe(fury1, fury2, Collections.singletonMap("k", 1)));
  }

  enum EnumTestClass {
    FOO,
    BAR
  }

  @Data
  static class EnumFieldStruct {
    EnumTestClass f1;
    EnumTestClass f2;
    String f3;
  }

  @Test
  public void testEnumField() throws java.io.IOException {
    Fury fury = Fury.builder().withLanguage(Language.XLANG).requireClassRegistration(true).build();
    fury.register(EnumTestClass.class, "test.EnumTestClass");
    fury.register(EnumFieldStruct.class, "test.EnumFieldStruct");

    EnumFieldStruct a = new EnumFieldStruct();
    a.f1 = EnumTestClass.FOO;
    a.f2 = EnumTestClass.BAR;
    a.f3 = "abc";
    Assert.assertEquals(xserDe(fury, a), a);
    structRoundBack(fury, a, "test_enum_field");
  }
}
