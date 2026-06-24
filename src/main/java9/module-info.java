/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
module ca.vanzyl.provisio.archive {
    requires java.base;
    requires org.apache.commons.compress;
    requires org.apache.commons.io;
    requires org.apache.commons.codec;
    requires org.tukaani.xz;
    requires org.codehaus.plexus.util;

    exports ca.vanzyl.provisio.archive;
    exports ca.vanzyl.provisio.archive.generator;
    exports ca.vanzyl.provisio.archive.perms;
    exports ca.vanzyl.provisio.archive.source;
    exports ca.vanzyl.provisio.archive.tar;
    exports ca.vanzyl.provisio.archive.zip;
}