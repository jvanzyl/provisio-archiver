/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive.perms;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

@SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
public final class PermissionsSet {
    private final Set<PosixFilePermission> toAdd;
    private final Set<PosixFilePermission> toRemove;

    PermissionsSet(final Set<PosixFilePermission> toAdd, final Set<PosixFilePermission> toRemove) {
        this.toAdd = toAdd;
        this.toRemove = toRemove;
    }

    public Set<PosixFilePermission> modify(final Set<PosixFilePermission> set) {
        final Set<PosixFilePermission> ret = EnumSet.copyOf(set);

        ret.removeAll(toRemove);
        ret.addAll(toAdd);
        return ret;
    }
}
