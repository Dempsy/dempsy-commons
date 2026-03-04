# Dempsy Commons

A collection of reusable APIs, abstractions, and utilities extracted from the [Dempsy](http://dempsy.github.com/Dempsy/#overview) distributed message processing framework. These libraries are self-contained and usable independently of Dempsy itself.

## Overview

Dempsy-commons provides three categories of functionality:

1. **Abstractions with pluggable implementations** — Cluster management (`dempsy-cluster.api`), serialization (`dempsy-serialization.api`), and virtual file system access (`dempsy-vfs.api`). Each abstraction ships with multiple backends you can swap at deploy time.
2. **Utilities** — Functional programming helpers, zero-copy I/O streams, thread scheduling, high-performance ring buffers, and Spring context loading.
3. **Test utilities** — Multi-threaded test polling, socket disruption simulation, and environment variable management for tests.

All modules follow a naming convention: abstractions end with `.api`, implementations are named `dempsy-[feature].[backend]` (e.g., `dempsy-cluster.zookeeper`). A BOM POM keeps versions consistent across all modules.

## Quick Start

Add the BOM to your `dependencyManagement` and then pull in whichever modules you need — no version tags required on individual dependencies:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>net.dempsy</groupId>
      <artifactId>dempsy-commons-bom</artifactId>
      <version>2.4.2-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Example: using the cluster API with the local (in-memory) implementation for testing:

```xml
<dependencies>
  <dependency>
    <groupId>net.dempsy</groupId>
    <artifactId>dempsy-cluster.api</artifactId>
  </dependency>
  <dependency>
    <groupId>net.dempsy</groupId>
    <artifactId>dempsy-cluster.local</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>net.dempsy</groupId>
    <artifactId>dempsy-serialization.jackson</artifactId>
  </dependency>
</dependencies>
```

```java
import net.dempsy.cluster.ClusterInfoSession;
import net.dempsy.cluster.local.LocalClusterSessionFactory;

try (final LocalClusterSessionFactory factory = new LocalClusterSessionFactory();
     final ClusterInfoSession session = factory.createSession()) {
    // Create a directory node
    session.mkdir("/app/config", null, ClusterInfoSession.DirMode.PERSISTENT);

    // Write data to the node
    session.setData("/app/config", "hello".getBytes());

    // Read it back
    byte[] data = (byte[]) session.getData("/app/config", null);
    System.out.println(new String(data)); // prints: hello
}
```

For production, swap in `dempsy-cluster.zookeeper` without changing your application code.

## Contents

- [Module Reference](#module-reference)
  - [dempsy-cluster.api](#dempsy-cluser.api) — Cluster data management
  - [dempsy-serialization.api](#dempsy-serialization.api) — Serialization abstraction
  - [dempsy-vfs.api](#dempsy-vfs.api) — Virtual file system
  - [dempsy-spring-utils](#dempsy-spring-utils) — Spring context loading
  - [dempsy-utils](#dempsy-utils) — General utilities
  - [dempsy-test-utils](#dempsy-test-utils) — Test utilities
  - [dempsy-ringbuffer](#dempsy-ringbuffer) — High performance ring buffers
- [How to Extend](#how-to-extend)
- [Naming and Versioning](#lifecycle)
- [General Requirements](#general-requirements)
- [Current Versions](#current-versions)

---

## <a name="module-reference"></a>Module Reference

### <a name="dempsy-cluser.api"></a>dempsy-cluster.api

This API is a simple generalization of [Zookeeper's](https://zookeeper.apache.org/) API. It has an implementation that doesn't require zookeeper and also one that depends on zookeeper. All error handling is managed by the implementations and so it's much easier to code against than the raw zookeeper. Plus it allows the writing of unit tests against classes that use the API by plugging in a working Local implementation.

This is an alternative to [Netflix's Curator](http://curator.apache.org/). It provides a decoupling from the underlying Zookeeper and makes code written against Zookeeper more resilient and easier to test.

#### Limitations

Not all functionality that Zookeeper provides is available in this API. The following is a list of the current limitations:

  * There's no support for security or Zookeeper ACLs.

#### User Guide

**The main abstraction**

See the [ClusterInfoSession](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-cluster.api/src/main/java/net/dempsy/cluster/ClusterInfoSession.java) source. It's a simple api wrapper that lets you interact with ZooKeeper but has more resilience than the standard ZooKeeper client and you can plug in a local implementation for testing.

**Selecting the implementation**

Dependency injection would be the best way to select which implementation your code should use. That way you can write code that works against multiple implementations. An example using Spring:

```java
    public class MyClassThatUsesClusterInfo {
        final ClusterInfoSession session;

        public MyClassThatUsesClusterInfo(ClusterInfoSessionFactory factory) {
           session = factory.createSession();
           ...
        }
    }
```

with an application context that selects the actual ZooKeeper implementation of the API:

```xml
   <bean name="serializer" class="net.dempsy.serialization.jackson.JsonSerializer" />

   <bean name="clusterInfoFactory" class="net.dempsy.cluster.zookeeper.ZookeeperSessionFactory" >
     <constructor-arg value="${zk.connectString}" />
     <constructor-arg value="${zk.sessionTimeoutMillis:5000}" />
     <constructor-arg ref="serializer" />
   </bean>

   <bean class="com.mycompany.MyClassThatUsesClusterInfo" >
     <constructor-arg ref="clusterInfoFactory" />
   </bean>
```

*Note: the ZookeeperSessionFactory requires a serializer. There are several serializers included in dempsy-commons and the selected one will need to be included in the dependencies. For the above example you'll need to include: artifactId=dempsy-serialization.jackson.*

**Build Dependencies**

These dependencies are represented as Maven `pom.xml` file dependencies but, of course, you can include them in your favorite Maven or Ivy based build system.

* API dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.api</artifactId>
</dependency>
```

* ZooKeeper implementation dependency.

This dependency includes the actual zookeeper implementation of the cluster abstraction. If you write code against the API then this should be able to be included as a "runtime" dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <scope>runtime</scope>
</dependency>
```

* Testing dependency

For testing your code you can plug in a local implementation of the cluster abstraction as follows.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.local</artifactId>
   <scope>test</scope>
</dependency>
```

It's possible to use the Zookeeper implementation in test as there's a zookeeper implementation test-jar that's built and contains a general zookeeper test server. If you want to run tests against an embedded Zookeeper server then you can include the following dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <type>test-jar</type>
   <scope>test</scope>
</dependency>
```

The following code will then working a test:

```java
try (final ZookeeperTestServer server = new ZookeeperTestServer()) {
   final ZookeeperSessionFactory factory =
         new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer());
   ....
}
```

The port selected is ephemeral. You can alternately supply the port in the [ZookeeperTestServer](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-cluster.zookeeper/src/test/java/net/dempsy/cluster/zookeeper/ZookeeperTestServer.java#L55) constructor.

### <a name="dempsy-serialization.api"></a>dempsy-serialization.api

Serialization abstractions are a dime-a-dozen. This one exists to support the above ZooKeeper wrapper. It has the following implementations:

1. Json serialization based on Jackson - artifactId=*dempsy-serialization.jackson*
1. Native Java serialization - artifactId=*dempsy-serialization.java*
1. Kryo based serialization - artifactId=*dempsy-serialization.kryo*

See the [Serializer](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-serialization.api/src/main/java/net/dempsy/serialization/Serializer.java) source for more details.

### <a name="dempsy-vfs.api"></a>dempsy-vfs.api - Virtual File System

A pluggable virtual file system abstraction that provides unified access to local files, classpath resources, and compressed/archived content through a single API. Supported formats include tar, zip, gzip, bzip2, xz, and 7-zip.

Key classes:

* [Vfs](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-vfs.api/src/main/java/net/dempsy/vfs/Vfs.java) is the main entry point. It manages a registry of `FileSystem` implementations keyed by URI scheme and provides methods to resolve URIs to paths and open input streams.
* [Path](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-vfs.api/src/main/java/net/dempsy/vfs/Path.java) represents a file or directory within the VFS. It provides methods for reading, listing, and querying file metadata.
* [FileSystem](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-vfs.api/src/main/java/net/dempsy/vfs/FileSystem.java) is the abstract base for all file system implementations. Each implementation declares which URI schemes it handles.

Usage example:

```java
try (final Vfs vfs = new Vfs()) {
    try (final InputStream is = vfs.toInputStream(new URI("file:///tmp/data.txt"))) {
        // read from the input stream
    }
}
```

The `Vfs` constructor automatically registers local file and Apache VFS file systems. Additional file systems (e.g., for archive formats) can be passed to the constructor or registered afterward.

### <a name="dempsy-spring-utils"></a>dempsy-spring-utils - Spring Context Utilities

Utilities for loading and configuring Spring application contexts with externalized properties. Supports both traditional XML-based and Spring Boot contexts.

Key classes:

* [SpringContextLoader](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-spring-utils/src/main/java/net/dempsy/util/spring/SpringContextLoader.java) loads `ClassPathXmlApplicationContext` instances with properties injected from configurable sources. It supports dependency injection of pre-existing objects into the context.
* [SpringBootContextLoader](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-spring-utils/src/main/java/net/dempsy/util/spring/SpringBootContextLoader.java) bridges XML property contexts with Spring Boot applications, setting the XML context as the parent of the Boot context.
* [PropertiesReader](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-spring-utils/src/main/java/net/dempsy/util/spring/PropertiesReader.java) is a functional interface for reading properties from arbitrary sources (files, classpath, VFS, etc.).
* [VariableSubstituterPropertiesReader](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-spring-utils/src/main/java/net/dempsy/util/spring/VariableSubstituterPropertiesReader.java) wraps another `PropertiesReader` and performs `${variable}` substitution on property values.

### <a name="dempsy-utils"></a>dempsy-utils

Several of the utilities are simple reusable components meant for internal (to dempsy-commons) use. You can use them if you want. The following is a brief description of each:

* [SafeString](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/SafeString.java) is a utility for dempsy-commons libraries to uniformly and safely represent objects in log messages and exceptions.
* [AutoDisposeSingleThreadScheduler](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/executor/AutoDisposeSingleThreadScheduler.java) is a self contained *one-shot* scheduler for a future task. It cleans itself up once the task executes. It's useful for scheduling retries without worrying about cleaning up threads afterward.
* [MessageBufferInput](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/io/MessageBufferInput.java)/[MessageBufferOutput](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/io/MessageBufferOutput.java) are java.io Input/Output Streams that can be used for *zero-copy* messaging. That is, you can serialize/deserialize directly to/from a network buffer (or other intermediary) without copying bytes around. These classes are used in the *dempsy-serialization.api*.
* [Functional programming support](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/Functional.java) is a set of utilities that make up for some of the lack of functionality in Java's standard library.
    * Chaining method calls. There is support for the ability to chain calls and create "Builder" like functionality from existing classes that were never meant to be used this way. For example:

```java
Properties properties = chain(new Properties(), p -> p.setProperty("name1", "value1"), p -> p.setProperty("name2", "value2"));
```

There's also the ability to chain calls where the lambda's may throw exceptions. See [Functional.chainThrows](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/Functional.java)

    * Exception handling in Lambda's.

Several of these utilities are meant to help with lambda's that throw checked exceptions; something Java 8 streams is notoriously bad at.

For example, since <em>Class.forName</em> throws the checked exception <em>ClassNotFoundException</em> we cannot normally use it in a lambda unless we use <em>try/catch</em> blocks inside the lambda.

What if we wanted the stream operation (map, forEach, etc.) to throw the same exception that might be thrown from the lambda? <em>uncheck</em> and <em>recheck</em> are for this purpose.

```java
Functional.<ClassNotFoundException> recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> Class.forName(cn))));
```

There's an alternate form of <em>recheck</em> that allows for the use of a static import and doesn't require the explicit generic. e.g.:
```java
recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> Class.forName(cn))), ClassNotFoundException.class);
```

If you want to simply convert an exception from one checked type to another (checked or unchecked), you can use [Functional.mapChecked](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/Functional.java). For example:

```java
  public void myFunction() throws MyException {
    mapChecked(() -> {
       ...
       outputStream.write(value);
       ...
    },(final IOException e) -> new MyException(e)));
  }
```
See the [Functional](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-utils/src/main/java/net/dempsy/util/Functional.java) class for all of the details.

### <a name="dempsy-test-utils"></a>dempsy-test-utils
* [ConditionPoll](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-test-utils/src/main/java/net/dempsy/utils/test/ConditionPoll.java) is a class that helps in writing multi-threaded tests. See the source javadoc for a full description.
* [SocketTimeout](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-test-utils/src/main/java/net/dempsy/utils/test/SocketTimeout.java) is to help when testing socket code that needs to be resilient to network disruptions. It will allow the test writer to schedule a near future socket disruption and test the resulting behavior.

### <a name="dempsy-ringbuffer"></a>dempsy-ringbuffer - High performance multi-threading

This work is substantially based on the ingenious work done by Martin Thompson and his conception of "Mechanical Sympathy." It is basically a refactor of the [LMAX-exchange Disruptor](http://lmax-exchange.github.com/disruptor/) in order to separate the control mechanism from what is being controlled and to simplify the API.

The [RingBufferControl](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) is analogous to a traditional "condition variable." Just like a condition variable is the synchronization mechanism that gates concurrent access to some 'condition', but says nothing about what the 'condition' actually is, the [RingBufferControl](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) gates concurrent access to the publishing and consuming of data in a ring buffer.

The 'consumer side' control and the 'publishing side' control are broken into two separate classes. The [RingBufferControl](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) represents control of the publish side of the ring buffer however, it inherits from the [RingBufferConsumerControl](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferConsumerControl.java) which represents the consumer side.

NOTE: These classes are incredibly temperamental and must strictly be used the way they were intended. Misuse can easily lead to lockups, missed sequences, etc.

These two base primitives can only be used with one consuming thread and one publishing thread, however, they form the building blocks for several other configurations:

  * [RingBufferControlMulticaster](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControlMulticaster.java) is a helper class for managing a set of [RingBufferControls](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) for use in a "single-publisher to multi-consumer" thread configuration where everything published is "multicast" to all consumers.
  * [RingBufferControlMultiplexor](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControlMultiplexor.java) is a helper class for managing a set of [RingBufferControls](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) for use in a "multiple-publisher to single-consumer" thread configuration.
  * [RingBufferControlWorkerPool](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControlWorkerPool.java) is a helper class for managing a set of [RingBufferControls](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-ringbuffer/src/main/java/net/dempsy/ringbuffer/RingBufferControl.java) for use in a "single-publisher to multi-consumer" thread configuration where the consumers are workers reading from the buffered data.

---

## <a name="how-to-extend"></a>How to Extend

Dempsy-commons is designed around pluggable implementations. To add a new backend for an existing abstraction:

### Writing a new Serializer

1. Add a new module named `dempsy-serialization.[yourformat]`
2. Extend the abstract [Serializer](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-serialization.api/src/main/java/net/dempsy/serialization/Serializer.java) class
3. Implement `serialize(Object, OutputStream)` and `deserialize(InputStream, Class)`

```java
public class MySerializer extends Serializer {
    @Override
    public <T> void serialize(T object, OutputStream os) throws IOException {
        // write object to output stream
    }

    @Override
    public <T> T deserialize(InputStream is, Class<T> clazz) throws IOException {
        // read object from input stream
    }
}
```

Test against the serialization API test-jar which provides standard compatibility tests.

### Writing a new Cluster backend

1. Add a new module named `dempsy-cluster.[yourbackend]`
2. Implement [ClusterInfoSessionFactory](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-cluster.api/src/main/java/net/dempsy/cluster/ClusterInfoSessionFactory.java) to create sessions
3. Implement [ClusterInfoSession](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-cluster.api/src/main/java/net/dempsy/cluster/ClusterInfoSession.java) with your backend's directory/data operations

Test against the cluster API test-jar which provides standard session conformance tests.

### Writing a new FileSystem

1. Extend the abstract [FileSystem](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-vfs.api/src/main/java/net/dempsy/vfs/FileSystem.java) class
2. Implement `supportedSchemes()` to declare which URI schemes you handle
3. Implement `toPath(URI)` to resolve URIs to [Path](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-vfs.api/src/main/java/net/dempsy/vfs/Path.java) instances
4. Pass your `FileSystem` to the `Vfs` constructor or call `vfs.register(yourFs)`

---

## <a name="lifecycle"></a>Naming and Versioning

In general libraries that contain the definition of the abstraction (a.k.a the *interface*) end with a *.api*. They take the form *dempsy-[feature].api*.

Jars with the implementations of those interfaces are named based on the feature, plus the implementation description. They have the form *dempsy-[feature].[implementation]*.

For example, the cluster management abstraction is contained in the project *dempsy-cluster.api* while the ZooKeeper implementation is in *dempsy-cluster.zookeeper*.

The versioning methodology is fairly standard. Starting with 2.0.0 the version numbers are defined as follows:

* interface libraries (those whose projects end with *.api*) version numbers will be *major*.*minor*.*build*.
  * *build* distinguish mostly bug fixes and are backwards and forwards compatible and introduce no new functionality.
  * *minor* revisions are *backward* compatible but not forwards compatible. Increasing *minor* revisions can add new API functionality but all preexisting functionality within the same *major* revision remains the same.
  * *major* revisions are refactors of the APIs and may not be backwards or forwards compatible
* implementations of specific *major.minor* interfaces will be versioned accordingly. For example, if you're using *dempsy-cluster.api* version *2.1.15* all valid implementations should be version *2.1.X*. You will likely want the latest *2.1* implementation.

## <a name="general-requirements"></a>General Requirements

  * Java 17 (Eclipse Temurin recommended)
  * Maven 3.9+

## <a name="current-versions"></a>Current Versions

All modules are at version **2.4.2-SNAPSHOT** (managed via the [BOM](https://github.com/Dempsy/dempsy-commons/blob/master/dempsy-commons-bom/pom.xml)).
