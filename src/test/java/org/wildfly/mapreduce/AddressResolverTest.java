package org.wildfly.mapreduce;

import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

public class AddressResolverTest {

    ModelControllerClient client;
    AddressResolver resolver;

    @Before
    public void setUp() {
        client = mock(ModelControllerClient.class);
        resolver = new AddressResolver(client);
    }


    // ------------------------------------------------------ normal tests

    @Test
    public void resolveSimple() throws IOException {
        when(client.execute(any(ModelNode.class))).thenReturn(listResponse("server0", "server1", "server2"));

        List<ModelNode> resolved = resolver.resolve(templateFor("host", "master", "server-config", "*"));
        assertEquals(3, resolved.size());
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server0"), resolved.get(0));
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server1"), resolved.get(1));
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server2"), resolved.get(2));
    }

    @Test
    public void resolveNested() throws IOException {
        when(client.execute(argThat(new ChildTypeMatcher("host")))).thenReturn(listResponse("master", "slave"));
        when(client.execute(argThat(new ChildTypeMatcher("server-config"))))
                .thenReturn(listResponse("server0", "server1", "server2"));

        List<ModelNode> resolved = resolver.resolve(templateFor("host", "*", "server-config", "*"));
        assertEquals(6, resolved.size());
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server0"), resolved.get(0));
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server1"), resolved.get(1));
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server2"), resolved.get(2));
        assertEquals(new ModelNode().add("host", "slave").add("server-config", "server0"), resolved.get(3));
        assertEquals(new ModelNode().add("host", "slave").add("server-config", "server1"), resolved.get(4));
        assertEquals(new ModelNode().add("host", "slave").add("server-config", "server2"), resolved.get(5));
    }


    // ------------------------------------------------------ edge cases

    @Test
    public void resolveWithoutWildcard() throws IOException {
        ModelNode serverConfigs = new ModelNode();
        serverConfigs.add("server0").add("server1").add("server2");
        when(client.execute(any(ModelNode.class))).thenReturn(serverConfigs);

        List<ModelNode> resolved = resolver.resolve(templateFor("host", "master", "server-config", "server0"));
        assertEquals(1, resolved.size());
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server0"), resolved.get(0));
    }

    @Test
    public void resolveEmptyAddress() throws IOException {
        List<ModelNode> resolved = resolver.resolve(new AddressTemplate(new ModelNode().setEmptyList()));
        assertEquals(1, resolved.size());
        assertTrue(resolved.get(0).asPropertyList().isEmpty());
    }


    // ------------------------------------------------------ helper method

    private AddressTemplate templateFor(String... address) {
        ModelNode node = new ModelNode();
        for (int i = 0; i < address.length; i += 2) {
            node.add(address[i], address[i + 1]);
        }
        return new AddressTemplate(node);
    }

    private ModelNode listResponse(String... values) {
        ModelNode result = new ModelNode();
        for (String value : values) {
            result.add(value);
        }
        ModelNode response = new ModelNode();
        response.get(OUTCOME).set(SUCCESS);
        response.get(RESULT).set(result);
        return response;
    }


    static class ChildTypeMatcher extends ArgumentMatcher<ModelNode> {

        private final String childType;

        ChildTypeMatcher(final String childType) {this.childType = childType;}

        @Override
        public boolean matches(final Object o) {
            return o instanceof ModelNode && childType.equals(((ModelNode) o).get(CHILD_TYPE).asString());
        }
    }
}