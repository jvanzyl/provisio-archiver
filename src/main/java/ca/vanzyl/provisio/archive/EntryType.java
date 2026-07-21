/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

/** The semantic type of a source entry, independent of an output archive format. */
public enum EntryType {
    FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    HARD_LINK
}
