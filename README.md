# Map / Filter / Reduce

For management clients like the console it is tedious and inperformant to read resources / attributes in big domains (e.g. return the state of all running servers across all hosts which are part of server group "foo")

Today this requires to setup multiple composite operations **on the client side** which need to be executed in an specific order. In an asynchronous environment like the Admin Console things are even more complicated and error prone.

This proposal suggests a new operation which collects all relevant information **on the server side** and returns only the relevant data in one go to the client. This operation consists of three properties

- address template
- optional filter
- optional list of reducing attributes

The address template is a resource address with one or several wildcards like `host=master/server-config=*`. The address template is resolved to a list of full qualified addresses and for each resolved address a `read-resource(include-runtime=true)`} operation is executed. If a filter is specified, the results are matched against the filter value using. Finally the results are reduced according the list of reducing attributes.

# Examples

More examples can be found in this integration test: [ClientIT](src/main/test/java/org/wildfly/mapreduce/ClientIT.java)

# Prototype
