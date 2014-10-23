package org.wildfly.mapreduce;

import static org.junit.Assert.*;

import org.jboss.dmr.ModelNode;
import org.junit.Test;

public class AddressTemplateTest {

    @Test
    public void emptyIsResolved() {
        ModelNode empty = new ModelNode().setEmptyList();
        assertTrue(new AddressTemplate(empty).isResolved());
    }

    @Test
    public void resolved() {
        ModelNode resolved = new ModelNode().add("host", "master");
        assertTrue(new AddressTemplate(resolved).isResolved());
    }

    @Test
    public void unresolved() {
        ModelNode template = new ModelNode().add("host", "*");
        assertFalse(new AddressTemplate(template).isResolved());
    }

    @Test
    public void resolvedPart() {
        ModelNode template = new ModelNode().add("host", "master").add("server-config", "*");
        ModelNode resolvedPart = new AddressTemplate(template).resolvedPart();
        assertEquals(new ModelNode().add("host", "master"), resolvedPart);
    }

    @Test
    public void resolvedPartOfEmpty() {
        ModelNode empty = new ModelNode().setEmptyList();
        ModelNode resolvedPart = new AddressTemplate(empty).resolvedPart();
        assertTrue(resolvedPart.asPropertyList().isEmpty());
    }

    @Test
    public void resolvedPartOfTemplate() {
        ModelNode template = new ModelNode().add("host", "*");
        ModelNode resolvedPart = new AddressTemplate(template).resolvedPart();
        assertTrue(resolvedPart.asPropertyList().isEmpty());
    }

    @Test
    public void resolvedPartOfAlreadyResolved() {
        ModelNode resolved = new ModelNode().add("host", "master");
        ModelNode resolvedPart = new AddressTemplate(resolved).resolvedPart();
        assertSame(resolved, resolvedPart);
    }

    @Test
    public void firstWildcardType() {
        ModelNode template = new ModelNode().add("host", "master").add("server-config", "*");
        String type = new AddressTemplate(template).firstWildcardType();
        assertEquals("server-config", type);
    }

    @Test
    public void firstWildcardTypeOfEmpty() {
        ModelNode empty = new ModelNode().setEmptyList();
        String type = new AddressTemplate(empty).firstWildcardType();
        assertNull(type);
    }

    @Test
    public void firstWildcardTypeOfTemplate() {
        ModelNode template = new ModelNode().add("host", "*");
        String type = new AddressTemplate(template).firstWildcardType();
        assertEquals("host", type);
    }

    @Test
    public void firstWildcardTypeOfResolved() {
        ModelNode resolved = new ModelNode().add("host", "master");
        String type = new AddressTemplate(resolved).firstWildcardType();
        assertNull(type);
    }

    @Test
    public void resolveSimple() {
        ModelNode template = new ModelNode().add("host", "*").add("server-config", "*");
        AddressTemplate resolved = new AddressTemplate(template).resolve("master", "server-one");
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server-one"), resolved.underlying);
    }

    @Test
    public void resolveNested() {
        ModelNode template = new ModelNode().add("host", "master").add("server-config", "*");
        AddressTemplate resolved = new AddressTemplate(template).resolve("server-one");
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server-one"), resolved.underlying);
    }

    @Test
    public void resolveMoreWildcards() {
        ModelNode template = new ModelNode().add("host", "*").add("server-config", "*");
        AddressTemplate resolved = new AddressTemplate(template).resolve("master");
        assertEquals(new ModelNode().add("host", "master").add("server-config", "*"), resolved.underlying);
    }

    @Test
    public void resolveMoreValues() {
        ModelNode template = new ModelNode().add("host", "*").add("server-config", "*");
        AddressTemplate resolved = new AddressTemplate(template).resolve("master", "server-one", "foo");
        assertEquals(new ModelNode().add("host", "master").add("server-config", "server-one"), resolved.underlying);
    }

    @Test
    public void resolveResolved() {
        AddressTemplate template = new AddressTemplate(new ModelNode().add("host", "master"));
        AddressTemplate resolved = template.resolve("foo");
        assertSame(template, resolved);
    }

    @Test
    public void resolveWithNull() {
        AddressTemplate template = new AddressTemplate(new ModelNode().add("host", "master"));
        AddressTemplate resolved = template.resolve();
        assertSame(template, resolved);
    }
}