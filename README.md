# Dempsy Common Api's

This project contains a set of APIs and useful utilities that were generated as part of the [Dempsy](http://dempsy.github.com/Dempsy/#overview) project. I'm currently using them professionally and thought that it made sense to separate them and make them generally available.

## Contents

### [APIs, interfaces and implementations](#apis)

1. [Naming and versioning](#lifecycle)
1. [dempsy-cluster.api](#dempsy-cluser.api) - a tool for writing cluster data management code.
   1. [Limitations](#limitations) with respect to ZooKeeper
   1. [User Guide](#user-guide) for getting started with the cluster info api
1. [dempsy-serialization.api](#dempsy-serialization.api) - a simple serialization abastraction and a few implementations
1. [dempsy-distconfig.api](#dempsy-distconfig.api) - a means of supplying environment configuration for an elastic distributed system
   1. [Example using Spring](#dempsy-distconfig-springexample)
   1. [Example in a dropwizard app](#dempsy-distconfig-dropwizard)

### [Tools and utilities](#tools)
1. [dempsy-utils](#dempsy-utils) - A few simple reusalbe components
1. [dempsy-test-utils](#dempsy-test-utils) - Aids in muti-threaded and more complicated network testing
1. [dempsy-ringbuffer](#dempsy-ringbuffer) - High performance multi-threading

### [General Requirements](#general-requirements)

### [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/)

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

See the [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/) for the [ClusterInfoSession](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/cluster/ClusterInfoSession.html). It's a simple api wrapper that lets you interact with ZooKeeper but has more resilience than the standard ZooKeeper client and you can plug in a local implementation for testing.

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

These dependencies are represented as Maven pom.xml file dependencies but, of course, you can include them in your favorite Maven or Ivy based build system.

* API dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.api</artifactId>
   <version>${dempsy-cluster.api.version}</version>
</dependency>
```

* ZooKeeper implementation dependency.

This dependency includes the actual zookeeper implementation of the cluster abstraction. If you write code against the API then this should be able to be included as a "runtime" dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <version>${dempsy-cluster.zookeeper.version}</version>
   <scope>runtime</scope>
</dependency>
```

* Testing dependency

For testing your code you can plug in a local implementation of the cluster abstraction as follows.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.local</artifactId>
   <version>${dempsy-commons.local.version}</version>
   <scope>test</scope>
</dependency>
```

It's possible to use the Zookeeper implementation in test as there's a zookeeper implementation test-jar that's built and contains a general zookeeper test server. If you want to run tests against an embedded Zookeeper server then you can include the following dependency.

```xml
<dependency>
   <groupId>net.dempsy</groupId>
   <artifactId>dempsy-cluster.zookeeper</artifactId>
   <type>test-jar</type>
   <version>${dempsy-cluster.zookeeper.version}</version>
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

## <a name="dempsy-distconfig.api"></a>dempsy-distconfig.api

A common problem when writing elastic distributed systems is providing configuration information for newly started dynamically allocated systems. This abstraction and its accompanying implementations provide for a means to do just that.

The right way to approach this problem is to separate environment specific settings and use them as variables in application specific configuration. For example, the location of a mail server or the uri for a database is something that will vary depending on the deployment environment. The *dev* environment can have a different database/mail server than the *test* or *production* environment. Separating these settings into a centrally managed configuration store and bootstrapping that store's location is what this library is about.

### <a name="dempsy-distconfig-springexample"></a>Example using Spring

#### Main Application context

Suppose you have an application context that looks like the following <b>application.xml</b>:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans.xsd 
    http://www.springframework.org/schema/context
    http://www.springframework.org/schema/context/spring-context.xsd ">
   
   <context:property-placeholder />

   <bean class="com.mycompany.MyAppOrService">
      <constructor-arg name="mailServer" value="${com.mycompany.mailserver}" />
   </bean>
</beans>
```

This application context expects the propery source placeholder configurer to be able to pick up a value for the variable *com.mycompany.mailserver*. This can be done with a PropertiesReader and a small bit of code. For example.

#### PropertiesReader application context.

Another bootstrap application context for production would look like the following *property-source.xml*. Note, what we want from this is a [PropertiesReader](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/distconfig/PropertiesReader.html).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans.xsd 
    http://www.springframework.org/schema/context
    http://www.springframework.org/schema/context/spring-context.xsd ">

    <!-- Allow the system to supply the bootstrap variable through a system -->
    <!--  environment variable or in a -D property on the command line. -->
    <context:property-placeholder location="/etc/mycompany/thisenvironment.properties" system-properties-mode="OVERRIDE"/>
    
    <!-- ClusterInfoSessionFactory using zookeeper with jackson serialization -->
    <!--  This object needs to be bootrapped with the zookeeper connect string and nothing else. -->
    <bean id="zk-session-info-factory" class="net.dempsy.cluster.zookeeper.ZookeeperSessionFactory" >
       <constructor-arg value="${ZK_CONNECT}" />
       <constructor-arg value="5000" />
       <constructor-arg>
         <bean class="net.dempsy.serialization.jackson.JsonSerializer" />
       </constructor-arg>
    </bean>
    
    <!-- Get a session from the ClusterInfoSessionFactory -->
    <bean id="zk-session-info" factory-bean="zk-session-info-factory" factory-method="createSession" destroy-method="close" />

    <!-- Use that session in the PropertiesReader -->
    <bean id="prop-reader" class="net.dempsy.distconfig.clusterinfo.ClusterInfoPropertiesReader">
     <constructor-arg ref="zk-session-info" />
     <constructor-arg value="/ptc/envconf" />
    </bean>
</beans>
```

Walking through this what we want is a PropertiesReader.
1. The PropertiesReader implementation is a ClusterInfoPropertiesReader. This uses the ClusterInfo functionality from *dempsy-cluster.api*.
1. The ClusterInfoPropertiesReader needs to be passed a ClusterInfoSession in its constructor.
1. The ClusterInfoSession is a ZookeeperSession from *dempsy-cluster.zookeeper*. The actual session is gotten from a factory bean of the type ZookeeperSessionFactory that's configured with the only bootstrapping parameter necessary. That is ZK_CONFIG.
1. ZK_CONFIG can be provided in the file already on the system at */etc/mycompany/thisenvironment.properties* or it can be in the system environment, e.g. ```export ZK_CONFIG=host1:2181,host2:2181,host3:2181```. Or it can be provided on the command line as a -D option to the jvm start. e.g. ```-DZK_CONFIG=host1:2181,host2:2181,host3:2181```

#### Glue code.

Now, when we load the application configuration we need to do it in 2 stages. First we need to load the *properties-source.xml* context. Then we need to supply that as a Spring PropertySource to the main application context. This can be done as follows:

```java
ClassPathXmlApplicationContext ctx;

// Load the properties-source context.
try (final ClassPathXmlApplicationContext propsCtx = new ClassPathXmlApplicationContext("properies-source.xml");) {

    // Retrieve the reader.
    final PropertiesReader reader = propsCtx.getBean(PropertiesReader.class);

    // load the main application context without refreshing it.
    ctx = new ClassPathXmlApplicationContext(sc.appCtx, false);

    // Add a Spring PropertySource to the main application context's environment
    ctx.getEnvironment().getPropertySources().addFirst(
       new PropertySource<Properties>(reader.getClass().getName()) {
            Properties props = reader.read(null);

            @Override
                    public Object getProperty(final String name) {
                LOGGER.debug("Property requested from " + getName() + " using key: " + name);
                        return props.getProperty(name);
            }

        });

     // refresh the context
     ctx.refresh();
}
```

#### Unit testing your application

Now, to unit test your code you can supply a different *properties-source.xml*.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
    xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans.xsd 
    http://www.springframework.org/schema/context
    http://www.springframework.org/schema/context/spring-context.xsd ">

    <!-- Simple classpath PropertiesReader -->
    <bean id="prop-reader" class="net.dempsy.distconfig.classpath.ClasspathPropertiesReader">
     <constructor-arg ref="test-application.properties" />
    </bean>
</beans>
```

In this case, *test-application.properties* would contain a unit testing setting for the *com.mycompany.mailserver*.

### <a name="dempsy-distconfig-dropwizard"></a>Example supplying .yml substitutions in dropwizard.

<b>TBD</b>

See the [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/) for more details.

## <a name="tools"></a>Tools and Utilities

### <a name="dempsy-utils"></a>1. dempsy-utils

Several of the utilities are simple reusalbe components meant for internal (to dempsy-commons) use. You can use them if you want. The following is a brief description of each:

* [SafeString](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/util/SafeString.html) is a utility for dempsy-commons librarys to uniformly and safely represent objects in log messages and exceptions.
* [AutoDisposeSingleThreadScheduler](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/util/executor/AutoDisposeSingleThreadScheduler.html) is a self contained *one-shot* scheduler for a future task. It cleans itself up once the task executes. It's useful for scheduling retries without worrying about cleaning up threads afterward.
* [MessageBufferInput](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/util/io/MessageBufferInput.html)/[MessageBufferOutput](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/util/io/MessageBufferOutput.html) are java.io Input/Output Streams that can be used for *zero-copy* messaging. That is, you can serialize/deserialize directly to/from a network buffer (or other intermediary) without copying bytes around. These classes are used in the *dempsy-serialization.api*.

### <a name="dempsy-test-utils"></a>2. dempsy-test-utils
* [ConditionPoll](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/utils/test/ConditionPoll.html) is a class that helps in writing multi-threaded tests. See the [Javadoc](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/utils/test/ConditionPoll.html) for a full description.
* [SocketTimeout](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/utils/test/SocketTimeout.html) is to help when testing socket code that needs to be resillient to network disruptions. It will allow the test writer to schedule a near future socket disruption and test the resulting behavior.

### <a name="dempsy-ringbuffer"></a>3. dempsy-ringbuffer - High performance multi-threading

This work is substantially based on the ingenious work done by Martin Thompson and his conception of "Mechanical Sympathy." It is basically a refactor of the [LMAX-exchange Disruptor](http://lmax-exchange.github.com/disruptor/) in order to separate the control mechanism from what is being controlled and to simplify the API.

The [RingBufferControl](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) is analogous to a traditional "condition variable." Just like a condition variable is the synchronization mechanism that gates concurrent access to some 'condition', but says nothing about what the 'condition' actually is, the [RingBufferControl](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) gates concurrent access to the publishing and consuming of data in a ring buffer.

The 'consumer side' control and the 'publishing side' control are broken into two separate classes. The [RingBufferControl](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) represents control of the publish side of the ring buffer however, it inherits from the [RingBufferConsumerControl](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferConsumerControl.html) which represents the consumer side.

NOTE: These classes are incredibly temperamental and must strictly be used the way they were intended. Misuse can easily lead to lockups, missed sequences, etc.

These two base primitives can only be used with one consuming thread and one publishing thread, however, they form the building blocks for several other configurations:

  * [RingBufferControlMulticaster](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControlMulticaster.html) is a helper class for managing a set of [RingBufferControls](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "single-publisher to multi-consumer" thread configuration where everything published is "multicast" to all consumers.
  * [RingBufferControlMultiplexor](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControlMultiplexor.html) is a helper class for managing a set of [RingBufferControls](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "multiple-publisher to single-consumer" thread configuration.
  * [RingBufferControlWorkerPool](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControlWorkerPool.html) is a helper class for managing a set of [RingBufferControls](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/net/dempsy/ringbuffer/RingBufferControl.html) for use in a "single-publisher to multi-consumer" thread configuration where the consumers are workers reading from the buffered data.

## <a name="general-requirements"></a>General Requirements:

  * Java 8 - at version (1.1), almost everything will build with java 7 except the Kryo serializer which will need to be modified slightly. The released version 1.1 was built with Java 8. Going forward (2.0.0 and beyond) there will be an assumption that Java 8 functionality is available and builds will be done using Java8.

## [API docs](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/)
   * [version 1.1 javadocs](http://dempsy.github.io/Dempsy/dempsy-commons/1.1/) - <b>deprecated</b>.
   * [version 2.0.0 javadocs](http://dempsy.github.io/Dempsy/dempsy-commons/2.0.0-SNAPSHOT/) - current development SNAPSHOT build

