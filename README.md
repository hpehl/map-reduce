# Map / Filter / Reduce

For management clients like the console it is tedious and inperformant to read resources / attributes in big domains (e.g. return the state of all running servers across all hosts which are part of server group "foo")

Today this requires to setup multiple composite operations **on the client side** which need to be executed in an specific order. In an asynchronous environment like the Admin Console things are even more complicated and error prone.

This proposal suggests a new operation which collects all relevant information **on the server side** and returns only the relevant data in one go to the client. This operation consists of three properties

- address template
- optional filter
- optional list of reducing attributes

The address template is a resource address with one or several wildcards like `host=master/server-config=*`. The address template is resolved to a list of full qualified addresses and for each resolved address a `read-resource(include-runtime=true)`} operation is executed. If a filter is specified, the results are matched against the filter value. Finally the results are reduced according the list of reducing attributes.

# Result Format

[Pending]

# Error Handling

[Pending]

# Examples

To get the state of all running servers across all hosts which are part of server group "foo", we'd use the following code:

```java
ModelNode address = new ModelNode();
address.get(ADDRESS).add("host", "*").add("server", "*");

ModelNode filter = new ModelNode();
filter.get(NAME).set("server-group");
filter.get(VALUE).set("main-server-group");

ModelNode attributes = new ModelNode();
attributes.add("server-state");

ModelNode op = new ModelNode();
op.get(OP).set(MAP_REDUCE);
op.get(ADDRESS).set(address);
op.get(FILTER).set(filter);
op.get(ATTRIBUTES).set(attributes);

ModelNode response = modelControllerClient.execute(op);
```

More examples can be found in this integration test: [ClientIT](src/test/java/org/wildfly/mapreduce/ClientIT.java)

# Prototype

This repository implements as a prototype for the proposed map / reduce operation. The [ClientIT](src/test/java/org/wildfly/mapreduce/ClientIT.java) tests some basic use cases and acts as a playground for the new operation. You can execute the integration test using maven::

    maven -Dintegration verify

By default the test expects a domain with default configuration running at localhost:9990. You can use the system properties `management.host` and `management.port` to change the defaults:

    maven -Dintegration -Dmanagement.port=somewhere -Dmanagement.port=12345 verify


