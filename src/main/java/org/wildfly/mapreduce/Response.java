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
import static org.wildfly.mapreduce.MapReduceConstants.ADDRESS;
import static org.wildfly.mapreduce.MapReduceConstants.FAILED;

import org.jboss.dmr.ModelNode;

/**
 * Result of one operation in the context of a map / reduce operation
 *
 * @author Harald Pehl
 */
final class Response {

    private ModelNode outcome;
    private ModelNode failure;
    final ModelNode address;
    ModelNode result;

    static Response prepare(final ModelNode address) {
        Response response = new Response(address);
        response.outcome = new ModelNode(); // undefined
        response.result = new ModelNode(); // undefined
        response.failure = new ModelNode(); // undefined
        return response;
    }

    static Response failed(final ModelNode address, final String failure) {
        Response response = new Response(address);
        response.makeFailed(failure);
        return response;
    }

    private Response(final ModelNode address) {
        this.address = address;
    }

    boolean isFailed() {
        return failure.isDefined();
    }

    void useResult(final ModelNode result) {
        this.outcome = new ModelNode().set(SUCCESS);
        this.result = result;
    }

    void makeFailed(String failure) {
        this.outcome = new ModelNode().set(FAILED);
        this.result = new ModelNode(); // undefined
        this.failure = new ModelNode().set(failure);
    }

    ModelNode asModelNode() {
        ModelNode node = new ModelNode();
        node.get(ADDRESS).set(address);
        node.get(OUTCOME).set(outcome);
        if (isFailed()) {
            node.get(FAILURE_DESCRIPTION).set(failure);
        } else {
            node.get(RESULT).set(result);
        }
        return node;
    }
}
