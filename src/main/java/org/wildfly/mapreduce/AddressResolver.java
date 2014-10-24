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

import static java.util.Arrays.asList;
import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.wildfly.mapreduce.MapReduceConstants.ADDRESS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * Resolves the wildcards in an address template to a list of full qualified resource addresses. Wildcards are resolved
 * using a {@code read-children-names} operation: The address template {@code /host=master/server-config=*} is resolved
 * using the operation: {@code /host=master:read-children-names(child-type=server-config)}.
 *
 * @author Harald Pehl
 */
class AddressResolver {

    private final ModelControllerClient client;

    AddressResolver(final ModelControllerClient client) {this.client = client;}

    List<ModelNode> resolve(AddressTemplate start) throws IOException {
        if (start.isResolved()) {
            // are you kidding?
            return asList(start.underlying);
        } else {
            List<AddressTemplate> unresolved = asList(start);
            List<ModelNode> resolved = new ArrayList<>();
            resolveInternal(unresolved, resolved);
            return resolved;
        }
    }

    private void resolveInternal(final List<AddressTemplate> unresolved, final List<ModelNode> resolved)
            throws IOException {
        if (unresolved.isEmpty()) {
            // hooray we're finished!
            return;
        }

        ArrayList<AddressTemplate> stillUnresolved = new ArrayList<>();
        for (AddressTemplate nextUnresolved : unresolved) {
            // read children
            ModelNode resolvedPart = nextUnresolved.resolvedPart();
            String wildcardType = nextUnresolved.firstWildcardType();
            List<ModelNode> children = readChildrenNames(resolvedPart, wildcardType);

            // prepare recursion
            for (ModelNode child : children) {
                AddressTemplate template = nextUnresolved.resolve(child.asString());
                if (template.isResolved()) {
                    resolved.add(template.underlying);
                } else {
                    stillUnresolved.add(template);
                }
            }
        }
        resolveInternal(stillUnresolved, resolved);
    }

    private List<ModelNode> readChildrenNames(ModelNode address, String childType) throws IOException {
        ModelNode op = new ModelNode();
        op.get(ADDRESS).set(address);
        op.get(OP).set(READ_CHILDREN_NAMES_OPERATION);
        op.get(CHILD_TYPE).set(childType);

        ModelNode response = client.execute(op);
        if (!ModelNodeUtils.wasSuccessful(response)) {
            throw new IOException(ModelNodeUtils.getFailure(response));
        }
        ModelNode result = response.get(RESULT);
        if (!result.isDefined()) {
            throw new IOException("No result found for " + ModelNodeUtils.formatAddress(
                    address) + ":" + READ_CHILDREN_NAMES_OPERATION + "(" + CHILD_TYPE + "=" + childType + ")");
        }
        return result.asList();
    }
}
