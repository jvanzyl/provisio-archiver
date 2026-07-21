/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

/** Controls when assembled entries are written to the output archive. */
public enum EntryOrder {
    /** Write each entry while its source callback is active, without spooling its content. */
    SOURCE,

    /** Spool callback-scoped content and write entries in canonical name order. */
    NAME
}
