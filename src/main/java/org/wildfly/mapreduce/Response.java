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

import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESULT;
import static org.wildfly.mapreduce.MapReduceConstants.ADDRESS;

import org.jboss.dmr.ModelNode;

/**
* @author Harald Pehl
*/
final class Response {

    final ModelNode address;
    final ModelNode outcome;
    final ModelNode result;

    Response(final ModelNode address, final ModelNode outcome, final ModelNode result) {
        this.address = address;
        this.outcome = outcome;
        this.result = result;
    }

    ModelNode asModelNode() {
        ModelNode node = new ModelNode();
        node.get(ADDRESS).set(address);
        node.get(OUTCOME).set(outcome);
        node.get(RESULT).set(result);
        return node;
    }
}
