# Map / Filter / Reduce

For management clients like the console it is tedious and inperformant to read resources / attributes in big domains (e.g. return the state of all running servers across all hosts which are part of server group "foo")

Today this requires to setup multiple composite operations **on the client side** which need to be executed in an ordered way. This proposal suggests a new operation which would collect all relevant information **on the server side** and return only the relevant data to the client. In order to get the the information
