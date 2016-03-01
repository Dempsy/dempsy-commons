# Dempsy Common Api's

This project contains a set of APIs that were generated as port of the [Dempsy](http://dempsy.github.com/Dempsy/#overview) project. I'm currently using them professionally and thought that it made sense to separate them and make them generally available.

## dempsy-cluster.api

This API is a simple generalization of [Zookeeper's](https://zookeeper.apache.org/) API. It has an implementation that doesn't require zookeeper and also one that depends on zookeeper. All error handling is managed by the implementations and so it's much easier to code against than the raw zookeeper. Plus it allows the writing of unit tests against classes that use the API by plugging in a working Local implementation.

This is an alternative to [Netflix's Curator](http://curator.apache.org/). It provides a decoupling from the underlying Zookeeper and makes code written against Zookeeper more resillient and easier to test.

### Limitations

Not all functionality that Zookeeper provides is available in this API. The following is a list of the current limitations:

  * There's no support for security or Zookeeper ACLs.

### Getting Started

#### The main abstraction

See the [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/1.1/) for the [ClusterInfoSession](http://dempsy.github.io/Dempsy/dempsy-commons/1.1/net/dempsy/cluster/ClusterInfoSession.html). It's a simple api wrapper that lets you interact with ZooKeeper but has more resillience than the standard ZooKeeper client and you can plug in a local implementation for testing.

#### Selecting the implementation in code

Dependency injection would be the best way to inject the selected implementation into your code. An example using Spring:

```java
    public class MyClassThatUsesClusterInfo {
        final ClusterInfoSession session;
        
        public MyClassThatUsesClusterInfo(ClusterInfoSessionFactory factory) {
           session = factory.createSession();
           ...
        }
    }
```

with an appliction context that selects the actual ZooKeeper implementation of the API:

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

#### Add dependencies.

These dependencies are represented as Maven pom.xml file dependencies but, of course, you can include them in your favorite Maven or Ivy based build system.

1. API dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.api</artifactId>
   <version>${dempsy.commons.version}</version>
</dependency>
```

  2. ZooKeeper implementation dependency.

This dependency includes the actual zookeeper implementation of the cluster abstraction. If you write code against the API then this should be able to be included as a "runtime" dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <version>${dempsy.commons.version}</version>
   <scope>runtime</scope>
</dependency>
```

  3. Testing dependency

For testing your code you can plug in a local implementation of the cluster abstraction as follows

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.local</artifactId>
   <version>${dempsy.commons.version}</version>
   <scope>test</scope>
</dependency>
```

It's possible to use the Zookeeper implementation in test as there's a zookeeper implementation test-jar that's built. If you want to run tests against an embeded Zookeeper server then you can include the following dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <type>test-jar</type>
   <version>${dempsy.commons.version}</version>
   <scope>test</scope>
</dependency>
```

The following code will then workin a test:

```java
try (final ZookeeperTestServer server = new ZookeeperTestServer()) {
   final ZookeeperSessionFactory factory = 
         new ZookeeperSessionFactory(server.connectString(), 5000, new JsonSerializer());
   ....
}
```

## dempsy-serialization.api

Serialization abstractions are a dime-a-dozen. This one exists to support the above ZooKeeper wrapper. It has the following implementations:

  1. Json serialization based on Jackson - artifactId=dempsy-serialization.jackson
  2. Native Java serialization - artifactId=dempsy-serialization.java
  3. Kryo based serialization - artifactId=dempsy-serialization.kryo

See the [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/1.1/) for more details.

## General Requirements:

  * Java 8 - at version (1.1), almost everything will build with java 7 except the Kryo serializer which will need to be modified slightly. The released version 1.1 was built with Java 8. Going forward (2.0.0 and beyon) there will be an assumption that Java 8 functionality is available and builds will be done using Java8.

## Development lifecycle methodology and versioning

In general libraries that contain the definition of the abstraction end eith a *.api*. Implementations of that abstractions are named based on the abstraction name plus the implementation description. For example, the cluster management abstraction is contained in the project *dempsy-cluster.api* while the ZooKeeper implementation is in *dempsy-cluster.zookeeper*.

The versioning methodology is fairly standard. Starting with 2.0.0 the version numbers are defined as follows:

  * api (abstraction) libraries (those whose projects end with *.api*) version numbers will be *major*.*minor*.*build*.
  * *build* distinquish mostly bug fixes and are backwards and forwards compatible and introduce no new functionality.
  * *minor* reviosions are *backward* compatible but not forwards compatible. Increasing *minor* revisions can add new API functionality but all preexisting functionality within the same *major* revision remains the same.
  * *major* revisions are refactors of the APIs and may not be backwards or forwards compatible
  * implementations of specific *major.minor* abstractions will be versioned accordingly. For example, if you're using *dempsy-cluster.api* version *2.1.15* all valid implementations should be version *2.1.X*. You will likely want the latest *2.1* implementation.



