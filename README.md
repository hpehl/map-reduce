# Map / Filter / Reduce

For management clients like the console it is tedious and inperformant to read resources / attributes in big domains (e.g. return the state of all running servers across all hosts which are part of server group "foo")

Today this requires to setup multiple composite operations **on the client** which need to be executed in a specific order. In an asynchronous environment like the Admin Console things are even more complicated and error prone.

This proposal suggests a new operation for the top level resource which collects all relevant information **on the server** and returns only the relevant data in one go to the client. This operation consists of three parts:

1. address template
1. optional filter
1. optional list of reducing attributes

## Address Template

The address template is a resource address with one or multiple wildcards like `host=master/server-config=*`. The address template is resolved to a list of fully qualified resource addresses. For each resolved address a `read-resource(include-runtime=true)` operation is executed.

## Filter

If a filter is specified, the results of a map / reduce operation are matched against the filter value(s). Each filter value is compared using `equals()`. If you specify multiple filters, they're evaluated using conjunction by default:

```java
ModelNode address = new ModelNode();
address.add("profile", "*")
       .add("subsystem", "datasources")
       .add("data-source", "*");

ModelNode filter = new ModelNode();
filter.add("driver-name", "h2")
      .add("enabled", true);

ModelNode op = new ModelNode();
op.get(OP).set(MAP_REDUCE);
op.get(ADDRESS_TEMPLATE).set(address);
op.get(FILTER).set(filter);
// To return datasources where (driver-name == h2 || enabled == true) use
// op.get(FILTER_CONJUNCT).set(false);

ModelNode response = modelControllerClient.execute(op);
```

## Reduce

If you want to reduce the payload to just contain certain attributes, you can specify a list of reduce attributes. The following code returns just the names of all known users:

```java
ModelNode address = new ModelNode();
address.add("core-service", "management")
       .add("access", "authorization")
       .add("role-mapping", "*")
       .add("include", "*");

ModelNode filter = new ModelNode();
filter.add("type", "USER");

ModelNode attributes = new ModelNode();
attributes.add("name");

ModelNode op = new ModelNode();
op.get(OP).set(MAP_REDUCE);
op.get(ADDRESS_TEMPLATE).set(address);
op.get(FILTER).set(filter);
op.get(REDUCE).set(attributes);

ModelNode response = modelControllerClient.execute(op);
```

## Result Format

The response of a map / reduce operation is a list of nested model nodes for each resolved address. Each model node in turn has three elements:

1. `address`: The fully qualified address
1. `outcome`: `success` or `failed`
1. `result` or `failure-description`: The actual payload or an error description

Here's an example of the map / reduce operation for the template `/host=*/server-config=*`:

```
{
    "outcome" => "success",
    "result" => [
        {
            "address" => [
                ("host" => "master"),
                ("server-config" => "server-one")
            ],
            "outcome" => "success",
            "result" => {
                "auto-start" => true,
                ...
                "system-property" => undefined
            }
        },
        {
            "address" => [
                ("host" => "master"),
                ("server-config" => "server-three")
            ],
            "outcome" => "success",
            "result" => {
                "auto-start" => false,
                ...
                "system-property" => undefined
            }
        },
        {
            "address" => [
                ("host" => "master"),
                ("server-config" => "server-two")
            ],
            "outcome" => "success",
            "result" => {
                "auto-start" => true,
                ...
                "system-property" => undefined
            }
        }
    ]
}
```

## Error Handling

If the address template can be resolved to a list of resource addresses, the result will contain a block for each resolved address. However each block can result in an error. That means that the error reporting happens at the level of the nested result blocks. 

The overall result will be successful if one of the nested blocks is successful. Only if all nested blocks report an error the outer outcome will be marked as `failed`.   

The following example shows the result of the map / reduce operation for `/profile=*/subsystem=jacorb`. As you can see the response contains both successful and failed outcomes:

```
{
    "outcome" => "success",
    "result" => [
        {
            "address" => [
                ("profile" => "default"),
                ("subsystem" => "jacorb")
            ],
            "outcome" => "failed",
            "failure-description" => "WFLYCTL0216: Management resource '[
    (\"profile\" => \"default\"),
    (\"subsystem\" => \"jacorb\")
]' not found"
        },
        {
            "address" => [
                ("profile" => "full"),
                ("subsystem" => "jacorb")
            ],
            "outcome" => "success",
            "result" => {
                "add-component-via-interceptor" => "on",
                ...
                "ior-settings" => undefined
            }
        },
        {
            "address" => [
                ("profile" => "full-ha"),
                ("subsystem" => "jacorb")
            ],
            "outcome" => "success",
            "result" => {
                "add-component-via-interceptor" => "on",
                ...
                "ior-settings" => undefined
            }
        },
        {
            "address" => [
                ("profile" => "ha"),
                ("subsystem" => "jacorb")
            ],
            "outcome" => "failed",
            "failure-description" => "WFLYCTL0216: Management resource '[
    (\"profile\" => \"ha\"),
    (\"subsystem\" => \"jacorb\")
]' not found"
        }
    ]
}
```

## Examples

The following code shows a typical example which reads the state of all running servers across all hosts which are part of server group "main-server-group":

```java
ModelNode address = new ModelNode();
address.add("host", "*")
       .add("server", "*");

ModelNode filter = new ModelNode();
filter.add("server-group", "main-server-group")
      .add("server-state", "running");

ModelNode attributes = new ModelNode();
attributes.add("server-state");

ModelNode op = new ModelNode();
op.get(OP).set(MAP_REDUCE);
op.get(ADDRESS_TEMPLATE).set(address);
op.get(FILTER).set(filter);
op.get(REDUCE).set(attributes);

ModelNode response = modelControllerClient.execute(op);
```

More examples can be found in this integration test: [ClientIT](src/test/java/org/wildfly/mapreduce/ClientIT.java)

## Prototype

This repository implements as a prototype for the proposed map / reduce operation. The [ClientIT](src/test/java/org/wildfly/mapreduce/ClientIT.java) integration tests contains some typical use cases and acts as a playground for the new operation. You can execute the integration test using maven:

    maven -Dintegration verify

By default the integration test expects a running domain with default configuration at localhost:9990. You can use the system properties `management.host` and `management.port` to change the defaults:

    maven -Dintegration -Dmanagement.port=somewhere -Dmanagement.port=12345 verify

