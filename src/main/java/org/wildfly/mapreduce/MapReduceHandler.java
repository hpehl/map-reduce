/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.mapreduce;

import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.wildfly.mapreduce.MapReduceConstants.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * A handler which resolves map / reduce operations against a DMR endpoint. A map / reduce operation consists of three
 * properties:
 * <ol>
 * <li>address template</li>
 * <li>optional filter</li>
 * <li>optional list of reducing attributes</li>
 * </ol>
 * The address template is a resource address with one or several wildcards like {@code host=master/server-config=*}.
 * The template is resolved to a list of real addresses and for each resolved address a {@code
 * read-resource(include-runtime=true)} operation is executed. If a filter was specified, the results are matched
 * against the filter value using {@code equals()}. Finally the results are reduced according the list of attributes.
 * <p/>
 * The DMR endpoint can be specified using the system properties {@code management.host} and {@code management.port},
 * which are "localhost" and 9990 by default.
 *
 * @author Harald Pehl
 */
public class MapReduceHandler {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 9990;

    private static final String WILDFLY_404 = "WFLYCTL0216";
    private static final String EAP_404 = "JBAS014807";

    private final ModelControllerClient client;

    public MapReduceHandler() {
        try {
            String host = System.getProperty("management.host", DEFAULT_HOST);
            int port = Integer.parseInt(System.getProperty("management.port", String.valueOf(DEFAULT_PORT)));
            client = ModelControllerClient.Factory.create(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute the specified map / reduce operation. The method follow the 'fail-fast' principle. That is the first
     * error will result in an error model node to be returned.
     *
     * @param mapReduceOp a model node describing a valid map / reduce operation.
     *
     * @return A list model node which contains all resolved
     *
     * @throws java.lang.IllegalArgumentException      for an invalid map / reduce operation
     * @throws java.lang.UnsupportedOperationException for an invalid map / reduce operation
     */
    public ModelNode execute(ModelNode mapReduceOp) {
        ModelNode mapReduceResult;
        try {
            validate(mapReduceOp);
            ModelNode filter = mapReduceOp.get(FILTER);
            ModelNode attributes = mapReduceOp.get(ATTRIBUTES);

            // resolve addresses
            AddressTemplate addressTemplate = new AddressTemplate(mapReduceOp.get(ADDRESS));
            List<Response> responses = new AddressResolver(client).resolve(addressTemplate);

            for (Iterator<Response> iterator = responses.iterator(); iterator.hasNext(); ) {
                Response response = iterator.next();
                if (!response.isFailed()) {
                    ReadResourceOperation readResourceOperation = new ReadResourceOperation(response.address);
                    try {
                        ModelNode node = client.execute(readResourceOperation.operation);

                        if (!ModelNodeUtils.wasSuccessful(node)) {
                            response.makeFailed(ModelNodeUtils.getFailure(node));

                        } else {
                            // filter
                            ModelNode result = node.get(RESULT);
                            if (filter.isDefined() && !match(response, result, filter)) {
                                if (!response.isFailed()) {
                                    // remove filtered responses
                                    iterator.remove();
                                }
                                continue;
                            }

                            // reduce
                            if (attributes.isDefined()) {
                                result = reduce(response, result, attributes);
                                if (result == null) {
                                    // some reducing attributes were not defined for that resource
                                    continue;
                                }
                            }

                            // collect
                            response.useResult(result);
                        }
                    } catch (IOException e) {
                        response.makeFailed(e.getMessage());
                    }
                }
            }

            // build result
            ModelNode composite = new ModelNode().setEmptyList();
            for (Response response : responses) {
                composite.add(response.asModelNode());
            }
            mapReduceResult = new ModelNode();
            mapReduceResult.get(OUTCOME).set(allFailed(responses) ? FAILED : SUCCESS);
            mapReduceResult.get(RESULT).set(composite);

        } catch (RuntimeException e) {
            // validation error
            mapReduceResult = new ModelNode();
            mapReduceResult.get(OUTCOME).set("failed");
            mapReduceResult.get(FAILURE_DESCRIPTION).set(e.getMessage());
        }
        return mapReduceResult;
    }

    private void validate(final ModelNode operation) {
        // address
        if (!operation.get(ADDRESS).isDefined()) {
            throw new IllegalArgumentException("No address given");
        }
        ModelNode address = operation.get(ADDRESS);
        if (address.getType() != ModelType.LIST) {
            throw new IllegalArgumentException(
                    "Address must be of type " + ModelType.LIST + ", but was " + address.getType());
        }
        for (Property path : address.asPropertyList()) {
            if (WILDCARD.equals(path.getName())) {
                throw new IllegalArgumentException("Illegal usage of wildcards in " + ModelNodeUtils
                        .formatAddress(address) + " for segment " + path.toString());
            }
        }

        // operation
        if (!operation.get(OP).isDefined()) {
            throw new IllegalArgumentException("No operation given");
        }
        String op = operation.get(OP).asString();
        if (!MAP_REDUCE.equals(op)) {
            throw new UnsupportedOperationException("Unsupported operation " + op);
        }

        // for now only one filter attribute is supported
        ModelNode filter = operation.get(FILTER);
        if (filter.isDefined()) {
            if (filter.getType() != ModelType.OBJECT) {
                throw new IllegalArgumentException(
                        "Filter must be of type " + ModelType.OBJECT + ", but was " + filter.getType());
            }
            ModelNode name = filter.get(NAME);
            if (!name.isDefined()) {
                throw new IllegalArgumentException("Filter has no name");
            }
            ModelNode value = filter.get(VALUE);
            if (!value.isDefined()) {
                throw new IllegalArgumentException("Filter has no value");
            }
        }

        ModelNode attributes = operation.get(ATTRIBUTES);
        if (attributes.isDefined()) {
            if (attributes.getType() != ModelType.LIST) {
                throw new IllegalArgumentException(
                        "Attributes must be of type " + ModelType.LIST + ", but was " + attributes.getType());
            }
            if (attributes.asList().isEmpty()) {
                throw new IllegalArgumentException("Attributes must not be empty");
            }
        }
    }

    private boolean match(final Response response, final ModelNode result, final ModelNode filter) {
        String name = filter.get(NAME).asString();
        ModelNode value = result.get(name);

        if (value.isDefined()) {
            // atm only equals() is supported
            return value.equals(filter.get(VALUE));

        } else {
            response.makeFailed("Filter attribute \"" + name + "\" not defined for this resource");
            return false;
        }
    }

    private ModelNode reduce(final Response response, final ModelNode result, final ModelNode attributes) {
        // make sure all attributes are defined
        List<String> names = new ArrayList<>();
        List<String> undefined = new ArrayList<>();
        for (ModelNode attribute : attributes.asList()) {
            String name = attribute.asString();
            ModelNode value = result.get(name);
            if (value.isDefined()) {
                names.add(name);
            } else {
                undefined.add("\"" + name + "\"");
            }
        }

        if (!undefined.isEmpty()) {
            response.makeFailed("Reducing attributes " + undefined + " not defined for this resource");
            // ugly hack; wish Java had multiple return values
            return null;

        } else {
            ModelNode reduced = new ModelNode();
            for (String name : names) {
                ModelNode value = result.get(name);
                reduced.get(name).set(value);
            }
            return reduced;
        }
    }

    private boolean allFailed(final List<Response> responses) {
        int count = 0;
        for (Response response : responses) {
            if (response.isFailed()) {
                count++;
            }
        }
        return !responses.isEmpty() && count == responses.size();
    }

    public void shutdown() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
