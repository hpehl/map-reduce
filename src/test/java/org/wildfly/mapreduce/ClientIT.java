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

import org.jboss.dmr.ModelNode;
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
    public void masterServerConfigurations() {
        ModelNode op = mapReduceOp("host", "master", "server-config", "*");

        ModelNode response = server.execute(op);
        System.out.println(response);
    }

    @Test
    public void allServerConfigurations() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode response = server.execute(op);
        System.out.println(response);
    }

    @Test
    public void runningServersInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("server-group");
        filter.get(VALUE).set("main-server-group");
        op.get(FILTER).set(filter);

        ModelNode response = server.execute(op);
        System.out.println(response);
    }

    @Test
    public void reducedRunningServersInMainGroup() {
        ModelNode op = mapReduceOp("host", "*", "server-config", "*");

        ModelNode filter = new ModelNode();
        filter.get(NAME).set("server-group");
        filter.get(VALUE).set("main-server-group");
        op.get(FILTER).set(filter);

        ModelNode attributes = new ModelNode();
        attributes.add("host").add("name").add("profile-name").add("server-state");
        op.get(ATTRIBUTES).set(attributes);

        ModelNode response = server.execute(op);
        System.out.println(response);
    }


    // ------------------------------------------------------ edge cases

    @Test
    public void singletonResources() {
        mapReduceOp("profile", "default", "subsystem", "*");
    }

    @Test
    public void noWildcard() {
        ModelNode op = mapReduceOp("host", "master");

        ModelNode response = server.execute(op);
        System.out.println(response);
    }

    @Test
    public void emptyAddress() {
        ModelNode op = new ModelNode();
        op.get(OP).set(MAP_REDUCE);
        op.get(ADDRESS).setEmptyList();

        ModelNode response = server.execute(op);
        System.out.println(response);
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
