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
import static org.wildfly.mapreduce.MapReduceConstants.WILDCARD;

import java.util.Iterator;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * A data holder for a resource address with one or multiple wildcards.
 *
 * @author Harald Pehl
 */
final class AddressTemplate {

    final ModelNode underlying;

    AddressTemplate(final ModelNode address) {
        this.underlying = address;
    }

    @Override
    public String toString() {
        return underlying.toString();
    }

    boolean isResolved() {
        for (Property property : underlying.asPropertyList()) {
            if (WILDCARD.equals(property.getValue().asString())) {
                return false;
            }
        }
        return true;
    }

    ModelNode resolvedPart() {
        if (isResolved()) {
            return underlying;
        } else {
            ModelNode resolved = new ModelNode().setEmptyList();
            for (Property property : underlying.asPropertyList()) {
                if (WILDCARD.equals(property.getValue().asString())) {
                    break;
                }
                resolved.add(property.getName(), property.getValue());
            }
            return resolved;
        }
    }

    String firstWildcardType() {
        if (isResolved()) {
            return null;
        } else {
            for (Property property : underlying.asPropertyList()) {
                if (WILDCARD.equals(property.getValue().asString())) {
                    return property.getName();
                }
            }
            return null;
        }
    }

    /**
     * Resolves the wildcard in this address template against the specified values and returns a new address template.
     * Depending on the number of wildcards in this template and the number of values provided, the returned address
     * template might be or might not be fully resolved.
     *
     * @param value the concrete values which are replaces with the wildcards in this template
     *
     * @return a new address template
     */
    AddressTemplate resolve(final String... value) {
        if (value == null || value.length == 0 || isResolved()) {
            return this;
        } else {
            ModelNode resolved = new ModelNode();
            Iterator<String> values = asList(value).iterator();
            for (Property property : underlying.asPropertyList()) {
                if (WILDCARD.equals(property.getValue().asString())) {
                    if (values.hasNext()) {
                        resolved.add(property.getName(), values.next());
                    } else {
                        resolved.add(property.getName(), property.getValue());
                    }
                } else {
                    resolved.add(property.getName(), property.getValue());
                }
            }
            return new AddressTemplate(resolved);
        }
    }
}
