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
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * Fake server which resolves map / reduce operations against a real server.
 *
 * @author Harald Pehl
 */
public class Server {

    private final ModelControllerClient client;

    public Server(final String host, final int port) {
        try {
            client = ModelControllerClient.Factory.create(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public ModelNode execute(ModelNode mapReduceOp) {
        validate(mapReduceOp);

        try {
            // resolve addresses
            List<ReadResourceOperation> readResourceOperations = new ArrayList<>();
            AddressTemplate addressTemplate = new AddressTemplate(mapReduceOp.get(ADDRESS));
            List<ModelNode> resolved = new AddressResolver(client).resolve(addressTemplate);
            for (ModelNode address : resolved) {
                readResourceOperations.add(new ReadResourceOperation(address));
            }

            // execute operations
            List<Response> responses = new ArrayList<>();
            for (ReadResourceOperation readResourceOperation : readResourceOperations) {
                ModelNode node = client.execute(readResourceOperation.operation);
                responses.add(new Response(readResourceOperation.address, node.get(OUTCOME), node.get(RESULT)));
            }

            // add all responses to one model node
            ModelNode combined = new ModelNode();
            for (Response response : responses) {
                combined.add(response.asModelNode());
            }
            return combined;

        } catch (IOException e) {
            ModelNode error = new ModelNode();
            error.get(OUTCOME).set(FAILED);
            error.get(FAILURE_DESCRIPTION).set(e.getMessage());
            error.get(ROLLED_BACK).set(true);
            return error;
        }
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
                throw new IllegalArgumentException("Illegal usage of wildcards in " + address + ": " + path.toString());
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

        // for now only simple filters with an implicit == are supported
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

    public void shutdown() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
