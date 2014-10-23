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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.wildfly.mapreduce.MapReduceConstants.*;

import java.util.List;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Requires domain mode with default setup!
 */
public class ClientIT {

    private Server server;

    @Before
    public void setUp() {
        server = new Server();
    }

    @After
    public void tearDown() {
        server.shutdown();
    }


    // ------------------------------------------------------ normal tests

    @Test
    public void allServerConfigs() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode response = server.execute(op);
        List<ModelNode> nodes = response.asList();
        assertEquals(3, nodes.size());
    }

    @Test
    public void masterServerConfigs() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode response = server.execute(op);
        List<ModelNode> nodes = response.asList();
        assertEquals(3, nodes.size());
    }

    @Test
    public void serverConfigsInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("group");
        filter.get(VALUE).set("main-server-group");
        op.get(FILTER).set(filter);

        ModelNode response = server.execute(op);
        List<ModelNode> nodes = response.asList();
        assertEquals(2, nodes.size());
    }

    @Test
    public void reducedAutoStartServerConfigs() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("auto-start");
        filter.get(VALUE).set(false);
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("name").add("group");
        op.get(ATTRIBUTES).set(attributes);

        ModelNode response = server.execute(op);
        List<ModelNode> nodes = response.asList();
        assertEquals(1, nodes.size());

        ModelNode result = nodes.get(0).get(RESULT);
        List<Property> properties = result.asPropertyList();
        assertEquals(2, properties.size());
        assertTrue(result.get("name").isDefined());
        assertTrue(result.get("group").isDefined());
        assertFalse(result.get("auto-start").isDefined());
    }


    // ------------------------------------------------------ edge cases

    @Test
    public void singletonResources() {
        ModelNode op = mapReduceOp("profile", "default", "subsystem", "*");

        ModelNode response = server.execute(op);
        assertFalse(response.asList().isEmpty()); // there should be some profiles
    }

    @Test
    public void noWildcard() {
        ModelNode op = mapReduceOp("host", "master");

        ModelNode response = server.execute(op);
        assertEquals(1, response.asList().size());
        assertEquals(new ModelNode().add("host", "master"), response.asList().get(0).get(ADDRESS));
    }

    @Test
    public void emptyAddress() {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE);
        op.get(ADDRESS).setEmptyList();

        ModelNode response = server.execute(op);
        assertEquals(1, response.asList().size());

        ModelNode firstResult = response.asList().get(0);
        assertTrue(firstResult.get(ADDRESS).asList().isEmpty());
        assertEquals("DOMAIN", firstResult.get(RESULT).get("launch-type").asString());
    }


    // ------------------------------------------------------ helper methods

    private ModelNode mapReduceOp(String... address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE);
        for (int i = 0; i < address.length; i += 2) {
            op.get(ADDRESS).add(address[i], address[i + 1]);
        }
        return op;
    }
}
