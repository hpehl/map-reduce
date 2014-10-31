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

    /**
     * Tests whether multiple wildcards are resolved.
     */
    @Test
    public void allServerConfigs() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(3, payload(response).size());
    }

    /**
     * Tests whether a single wildcard is resolved.
     */
    @Test
    public void masterServerConfigs() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(3, payload(response).size());
    }

    /**
     * Tests a single filter statement.
     */
    @Test
    public void serverConfigsInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.add("group", "main-server-group");
        op.get(FILTER).set(filter);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(2, payload(response).size());
    }

    /**
     * Tests a single filter and a reduce attributes.
     */
    @Test
    public void reducedAutoStartServerConfigs() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.add("auto-start", false);
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("name").add("group");
        op.get(REDUCE).set(attributes);

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

    /**
     * Tests a single filter and multiple wildcards.
     */
    @Test
    public void stateOfRunningServersInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server", "*");

        ModelNode filter = new ModelNode();
        filter.add("server-group", "main-server-group");
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("server-state");
        op.get(REDUCE).set(attributes);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
    }

    /**
     * Tests a map / reduce operation which results in both successful and failed outcomes.
     */
    @Test
    public void mixedProfilesResponse() {
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

    /**
     * Tests multiple wildcards, multiple filters and a reduce attribute.
     */
    @Test
    public void enabledDataSources() {
        ModelNode op = mapReduceOp("profile", "*", "subsystem", "datasources", "data-source", "*");

        ModelNode filter = new ModelNode();
        filter.add("driver-name", "h2");
        filter.add("enabled", true);
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("connection-url").add("driver-name");
        op.get(REDUCE).set(attributes);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        List<ModelNode> payload = payload(response);
        assertEquals(4, payload.size());
    }

    /**
     * Tests multiple filter using disjunction
     */
    @Test
    public void mainOrOtherServerGroup() {
        ModelNode op = mapReduceOp("host", "*", "server", "*");

        ModelNode filter = new ModelNode();
        filter.add("server-group", "main-server-group");
        filter.add("server-group", "other-server-group");
        op.get(FILTER).set(filter);
        op.get(FILTER_CONJUNCT).set(false);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertEquals(3, payload(response).size());
    }


    // ------------------------------------------------------ error / edge cases

    /**
     * Tests a map / reduce op without any wildcards. Must return a {@code read-resource(include-runtime=true)} result
     * nested inside the map / reduce structure.
     */
    @Test
    public void noWildcards() {
        ModelNode op = mapReduceOp("host", "master");

        // expect a wrapped single read-resource response
        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);

        List<ModelNode> payload = payload(response);
        assertEquals(1, payload.size());
        assertEquals(new ModelNode().add("host", "master"), payload.get(0).get(ADDRESS));
    }

    /**
     * Tests an empty address. Must return a {@code read-resource(include-runtime=true)} result nested inside the
     * map / reduce structure.
     */
    @Test
    public void emptyAddress() {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE_OP);
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

    /**
     * Tests an invalid address template.
     */
    @Test
    public void invalidAddress() {
        ModelNode op = mapReduceOp("*", "master");

        ModelNode response = mapReduceHandler.execute(op);
        assertEquals(FAILED, response.get(OUTCOME).asString());
    }

    /**
     * Tests a filter which results in no results.
     */
    @Test
    public void filterWithEmptyResult() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.add("name", "bar");
        op.get(FILTER).set(filter);

        ModelNode response = mapReduceHandler.execute(op);
        assertSuccessful(response);
        assertTrue(payload(response).isEmpty());
    }

    /**
     * Tests an invalid filter.
     */
    @Test
    public void invalidFilter() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.add("foo", "bar");
        op.get(FILTER).set(filter);

        ModelNode response = mapReduceHandler.execute(op);
        assertEquals(FAILED, response.get(OUTCOME).asString());

        List<ModelNode> payload = payload(response);
        assertEquals(3, payload.size());
        for (ModelNode modelNode : payload) {
            assertEquals(FAILED, modelNode.get(OUTCOME).asString());
        }
    }

    /**
     * Tests an invalid reduce attribute.
     */
    @Test
    public void invalidReduce() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode attributes = new ModelNode();
        attributes.add("foo");
        op.get(REDUCE).set(attributes);

        ModelNode response = mapReduceHandler.execute(op);
        assertEquals(FAILED, response.get(OUTCOME).asString());

        List<ModelNode> payload = payload(response);
        assertEquals(3, payload.size());
        for (ModelNode modelNode : payload) {
            assertEquals(FAILED, modelNode.get(OUTCOME).asString());
        }
    }


    // ------------------------------------------------------ helper methods

    private ModelNode mapReduceOp(String... address) {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE_OP);
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
