/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

/** Selects how eligible file content is identified when creating tar hard links. */
public enum ContentIdentityMode {
    /** Compare SHA-256 digests, reading a possible duplicate before selecting a hard link. */
    VERIFIED,

    /**
     * Trust source-reported uncompressed size and CRC-32 metadata without reading duplicate content.
     *
     * <p>This is an explicit performance tradeoff: CRC-32 is not collision resistant. Content without valid metadata
     * falls back to {@link #VERIFIED} identity.
     */
    SIZE_AND_CRC32
}
