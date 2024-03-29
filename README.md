# Dempsy Common Api's

This project contains a set of APIs and useful utilities that were generated as part of the [Dempsy](http://dempsy.github.com/Dempsy/#overview) project. I'm currently using them professionally and thought that it made sense to separate them and make them generally available.

## Contents

### [APIs, interfaces and implementations](#apis)

1. [Naming and versioning](#lifecycle)
1. [dempsy-cluster.api](#dempsy-cluser.api) - a tool for writing cluster data management code.
   1. [Limitations](#limitations) with respect to ZooKeeper
   1. [User Guide](#user-guide) for getting started with the cluster info api
1. [dempsy-serialization.api](#dempsy-serialization.api) - a simple serialization abastraction and a few implementations

### [Tools and utilities](#tools)
1. [dempsy-utils](#dempsy-utils) - A few simple reusalbe components
1. [dempsy-test-utils](#dempsy-test-utils) - Aids in muti-threaded and more complicated network testing
1. [dempsy-ringbuffer](#dempsy-ringbuffer) - High performance multi-threading

### [General Requirements](#general-requirements)

### [API docs](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/)

# <a name="apis"></a>APIs, interfaces and implementations

## <a name="lifecycle"></a>Naming and versioning

In general libraries that contain the definition of the abstraction (a.k.a the *interface*) end with a *.api*. They take the form *dempsy-[feature].api*.

Jars with the implementations of those interfaces are named based on the feature, plus the implementation description. They have the form *dempsy-[feature].[implementation]*.

For example, the cluster management abstraction is contained in the project *dempsy-cluster.api* while the ZooKeeper implementation is in *dempsy-cluster.zookeeper*.

The versioning methodology is fairly standard. Starting with 2.0.0 the version numbers are defined as follows:

* interface libraries (those whose projects end with *.api*) version numbers will be *major*.*minor*.*build*.
  * *build* distinguish mostly bug fixes and are backwards and forwards compatible and introduce no new functionality.
  * *minor* revisions are *backward* compatible but not forwards compatible. Increasing *minor* revisions can add new API functionality but all preexisting functionality within the same *major* revision remains the same.
  * *major* revisions are refactors of the APIs and may not be backwards or forwards compatible
* implementations of specific *major.minor* interfaces will be versioned accordingly. For example, if you're using *dempsy-cluster.api* version *2.1.15* all valid implementations should be version *2.1.X*. You will likely want the latest *2.1* implementation.

## <a name="dempsy-cluser.api"></a>dempsy-cluster.api

This API is a simple generalization of [Zookeeper's](https://zookeeper.apache.org/) API. It has an implementation that doesn't require zookeeper and also one that depends on zookeeper. All error handling is managed by the implementations and so it's much easier to code against than the raw zookeeper. Plus it allows the writing of unit tests against classes that use the API by plugging in a working Local implementation.

This is an alternative to [Netflix's Curator](http://curator.apache.org/). It provides a decoupling from the underlying Zookeeper and makes code written against Zookeeper more resilient and easier to test.

### <a name="limitations"></a>Limitations

Not all functionality that Zookeeper provides is available in this API. The following is a list of the current limitations:

  * There's no support for security or Zookeeper ACLs.

### <a name="user-guide"></a>User Guide

#### The main abstraction

See the [API docs](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/index.html) for the [ClusterInfoSession](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/cluster/ClusterInfoSession.html). It's a simple api wrapper that lets you interact with ZooKeeper but has more resilience than the standard ZooKeeper client and you can plug in a local implementation for testing.

#### Selecting the implementation

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

#### Build Dependencies

These dependencies are represented as Maven `pom.xml` file dependencies but, of course, you can include them in your favorite Maven or Ivy based build system.

`dempsy-commons` comes with a BOM pom to make dependency management of multiple components easier. To include a consistent set of `dempsy-commons` APIs and implementations just add the following to your `dependencyManagement` section of your `pom.xml` file:

``` xml
  <dependencyManagement>
    <dependencies>
      <!-- dempsy-commons bom pom import -->
      <dependency>
        <groupId>net.dempsy</groupId>
        <artifactId>dempsy-commons-bom</artifactId>
        <version>2.1.2</version> <!-- or whatever overall version you want -->
        <type>pom</type>
        <scope>import</scope>
      </dependency>
```

Then the versions of each of the libraries will be automatically selected from a self-consistent set that coresponds to the overall version of the BOM pom (in the above example, version 2.1.2).

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

## <a name="dempsy-serialization.api"></a>dempsy-serialization.api

Serialization abstractions are a dime-a-dozen. This one exists to support the above ZooKeeper wrapper. It has the following implementations:

1. Json serialization based on Jackson - artifactId=*dempsy-serialization.jackson*
1. Native Java serialization - artifactId=*dempsy-serialization.java*
1. Kryo based serialization - artifactId=*dempsy-serialization.kryo*

See the [API docs](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs) for more details.

## <a name="tools"></a>Tools and Utilities

### <a name="dempsy-utils"></a>1. dempsy-utils

Several of the utilities are simple reusalbe components meant for internal (to dempsy-commons) use. You can use them if you want. The following is a brief description of each:

* [SafeString](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/SafeString.html) is a utility for dempsy-commons librarys to uniformly and safely represent objects in log messages and exceptions.
* [AutoDisposeSingleThreadScheduler](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/executor/AutoDisposeSingleThreadScheduler.html) is a self contained *one-shot* scheduler for a future task. It cleans itself up once the task executes. It's useful for scheduling retries without worrying about cleaning up threads afterward.
* [MessageBufferInput](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/io/MessageBufferInput.html)/[MessageBufferOutput](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/io/MessageBufferOutput.html) are java.io Input/Output Streams that can be used for *zero-copy* messaging. That is, you can serialize/deserialize directly to/from a network buffer (or other intermediary) without copying bytes around. These classes are used in the *dempsy-serialization.api*.
* [Java 8 Functional programming support](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/Functional.html) is a set of utilities that make up for some of the lack of functionality in Java 8.
*    * Chaining method calls. There is support for the ability to chain calls and create "Builder" like functionality from existing classes that were never meant to be used this way. For example:

```java
Properties properties = chain(new Properties(), p -> p.setProperty("name1", "value1"), p -> p.setProperty("name2", "value2"));
```

There's also the ability to chain calls where the lambda's may throw exceptions. See [chainThrows](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/Functional.html#chainThrows-T-net.dempsy.util.Functional.ConsumerThrows...-)

*    * Exception handling in Lambda's.

Several of these utilites are meant to help with lambda's that throw checked excpetions; something Java 8 streams is notoriously bad at.

For example, since <em>Class.forName</em> throws the checked exception <em>ClassNotFoundException</em> we cannot normally use it in a lambda unless we use <em>try/catch</em> blocks inside the lambda.

What if we wanted the stream operation (map, forEach, etc.) to throw the same exception that might be thrown from the lambda? <em>uncheck</em> and <em>recheck</em> are for this purpose.

```java
Functional.<ClassNotFoundException> recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> Class.forName(cn))));
```

There's an alternate form of <em>recheck</em> that allows for the use of a static import and doesn't require the explicit generic. e.g.:
```java
recheck(() -> classnames.stream().forEach(cn -> uncheck(() -> Class.forName(cn))), ClassNotFoundException.class);
```

If you want to simple convert an exception from one checked type to another (checked or unchecked), you can use [Functional.mapChecked](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/Functional.html#mapChecked-net.dempsy.util.Functional.SupplierThrows-java.util.function.Function-). For example:

```java
  public void myFunction() throws MyException {
    mapChecked(() -> {
       ...
       outputStream.write(value);
       ...
    },(final IOException e) -> new MyException(e)));
  }
```
See the [Functional](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/util/Functional.html) class for all of the details.

### <a name="dempsy-test-utils"></a>2. dempsy-test-utils
* [ConditionPoll](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/utils/test/ConditionPoll.html) is a class that helps in writing multi-threaded tests. See the [Javadoc](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/utils/test/ConditionPoll.html) for a full description.
* [SocketTimeout](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/utils/test/SocketTimeout.html) is to help when testing socket code that needs to be resillient to network disruptions. It will allow the test writer to schedule a near future socket disruption and test the resulting behavior.

### <a name="dempsy-ringbuffer"></a>3. dempsy-ringbuffer - High performance multi-threading

This work is substantially based on the ingenious work done by Martin Thompson and his conception of "Mechanical Sympathy." It is basically a refactor of the [LMAX-exchange Disruptor](http://lmax-exchange.github.com/disruptor/) in order to separate the control mechanism from what is being controlled and to simplify the API.

The [RingBufferControl](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) is analogous to a traditional "condition variable." Just like a condition variable is the synchronization mechanism that gates concurrent access to some 'condition', but says nothing about what the 'condition' actually is, the [RingBufferControl](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) gates concurrent access to the publishing and consuming of data in a ring buffer.

The 'consumer side' control and the 'publishing side' control are broken into two separate classes. The [RingBufferControl](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) represents control of the publish side of the ring buffer however, it inherits from the [RingBufferConsumerControl](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferConsumerControl.html) which represents the consumer side.

NOTE: These classes are incredibly temperamental and must strictly be used the way they were intended. Misuse can easily lead to lockups, missed sequences, etc.

These two base primitives can only be used with one consuming thread and one publishing thread, however, they form the building blocks for several other configurations:

  * [RingBufferControlMulticaster](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControlMulticaster.html) is a helper class for managing a set of [RingBufferControls](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "single-publisher to multi-consumer" thread configuration where everything published is "multicast" to all consumers.
  * [RingBufferControlMultiplexor](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControlMultiplexor.html) is a helper class for managing a set of [RingBufferControls](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "multiple-publisher to single-consumer" thread configuration.
  * [RingBufferControlWorkerPool](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControlWorkerPool.html) is a helper class for managing a set of [RingBufferControls](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "single-publisher to multi-consumer" thread configuration where the consumers are workers reading from the buffered data.

## <a name="general-requirements"></a>General Requirements:

  * Java 8 - at version (1.1), almost everything will build with java 7 except the Kryo serializer which will need to be modified slightly. The released version 1.1 was built with Java 8. Going forward (2.0.0 and beyond) there will be an assumption that Java 8 functionality is available and builds will be done using Java8.

## [API docs](https://dempsy.github.io/dempsy-commons/site-2.1.2/apidocs/)
   * version 1.1 javadocs - <b>deprecated</b>.
   * [Javadocs for other released versions](http://dempsy.github.io/dempsy-commons/)

## Current versions

Overall `dempsy-commons` version (bom version) 2.2.0

dempsy-serialization 2.1.x
dempsy-cluster 2.1.x
dempsy-ringbuffer 2.2.x
dempsy-utils 2.2.x
dempsy-test-utils 2.2.x

