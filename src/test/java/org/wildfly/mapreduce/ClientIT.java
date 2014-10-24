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
import static org.junit.Assert.*;
import static org.wildfly.mapreduce.MapReduceConstants.*;

import java.util.List;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for typical use cases and some edge cases. Requires domain mode with default setup!
 */
public class ClientIT {

    private MapReduceHandler mapReduceHandler;

    @Before
    public void setUp() {
        mapReduceHandler = new MapReduceHandler();
    }

    @After
    public void tearDown() {
        mapReduceHandler.shutdown();
    }


    // ------------------------------------------------------ some typical use cases

    @Test
    public void allServerConfigs() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(3, payload(response).size());
    }

    @Test
    public void masterServerConfigs() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(3, payload(response).size());
    }

    @Test
    public void serverConfigsInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("group");
        filter.get(VALUE).set("main-server-group");
        op.get(FILTER).set(filter);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(2, payload(response).size());
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

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);

        List<ModelNode> payload = payload(response);
        assertEquals(1, payload.size());

        ModelNode result = payload.get(0).get(RESULT);
        List<Property> properties = result.asPropertyList();
        assertEquals(2, properties.size());
        assertTrue(result.get("name").isDefined());
        assertTrue(result.get("group").isDefined());
        assertFalse(result.get("auto-start").isDefined());
    }

    @Test
    public void stateOfRunningServersInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("server-group");
        filter.get(VALUE).set("main-server-group");
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("server-state");
        op.get(ATTRIBUTES).set(attributes);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
    }

    @Test
    public void profilesWithJacorb() {
        ModelNode op = mapReduceOp("profile", "*", "subsystem", "jacorb");

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        List<ModelNode> payload = payload(response);
        assertEquals(4, payload.size());

        int successful = 0;
        int failed= 0;
        for (ModelNode modelNode : payload) {
            if (ModelNodeUtils.wasSuccessful(modelNode)) {
                successful++;
            } else {
                failed++;
            }
        }
        assertEquals(2, successful); // full, full-ha
        assertEquals(2, failed); // default, ha
    }


    // ------------------------------------------------------ error / edge cases

    @Test
    public void noWildcard() {
        ModelNode op = mapReduceOp("host", "master");

        // expect a wrapped single read-resource response
        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);

        List<ModelNode> payload = payload(response);
        assertEquals(1, payload.size());
        assertEquals(new ModelNode().add("host", "master"), payload.get(0).get(ADDRESS));
    }

    @Test
    public void emptyAddress() {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE);
        op.get(ADDRESS).setEmptyList();

        // expect the wrapped root resource
        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);

        List<ModelNode> payload = payload(response);
        assertEquals(1, payload.size());

        ModelNode rootResource = payload.get(0);
        assertTrue(rootResource.get(ADDRESS).asList().isEmpty());
        assertEquals("DOMAIN", rootResource.get(RESULT).get("launch-type").asString());
    }

    @Test
    public void invalidAddress() {

    }

    @Test
    public void invalidFilter() {

    }

    @Test
    public void invalidReducingAttributes() {

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

    private void assertSuccessful(final ModelNode response) {
        assertNotNull(response);
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertTrue(response.get(RESULT).isDefined());
        assertEquals(ModelType.LIST, response.get(RESULT).getType());
    }

    private List<ModelNode> payload(final ModelNode response) {return response.get(RESULT).asList();}
}
