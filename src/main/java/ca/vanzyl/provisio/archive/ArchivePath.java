/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical, relative path inside an archive. */
final class ArchivePath {

    private final List<String> segments;
    private final String value;

    private ArchivePath(List<String> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.value = String.join("/", segments);
    }

    static ArchivePath parse(String path, String description) throws IOException {
        if (path == null) {
            throw invalid(description, "path is null");
        }
        if (path.indexOf('\0') >= 0) {
            throw invalid(description, "NUL characters are not allowed");
        }

        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw invalid(description, "absolute and UNC paths are not allowed");
        }

        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw invalid(description, "'.' and '..' path segments are not allowed");
            }
            if (isWindowsDriveSegment(segment)) {
                throw invalid(description, "Windows drive paths are not allowed");
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            throw invalid(description, "path is empty");
        }
        return new ArchivePath(segments);
    }

    ArchivePath withoutFirstSegment() {
        if (segments.size() <= 1) {
            return this;
        }
        return new ArchivePath(segments.subList(1, segments.size()));
    }

    ArchivePath fileName() {
        return new ArchivePath(Collections.singletonList(segments.get(segments.size() - 1)));
    }

    ArchivePath prepend(String prefix, String description) throws IOException {
        if (prefix == null || prefix.isEmpty()) {
            return this;
        }
        ArchivePath prefixPath = parse(prefix, description);
        List<String> joined = new ArrayList<>(prefixPath.segments);
        joined.addAll(segments);
        return new ArchivePath(joined);
    }

    String entryName(EntryType type) {
        return type == EntryType.DIRECTORY ? value + "/" : value;
    }

    String value() {
        return value;
    }

    static String validateSymbolicLinkTarget(ArchivePath link, String target) throws IOException {
        String description = "symbolic link target for " + link.value;
        if (target == null) {
            throw invalid(description, "path is null");
        }
        if (target.indexOf('\0') >= 0) {
            throw invalid(description, "NUL characters are not allowed");
        }

        String normalized = target.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw invalid(description, "absolute and UNC paths are not allowed");
        }

        int resolvedDepth = link.segments.size() - 1;
        List<String> targetSegments = new ArrayList<>();
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".")) {
                throw invalid(description, "'.' path segments are not allowed");
            }
            if (segment.equals("..")) {
                resolvedDepth--;
                if (resolvedDepth < 0) {
                    throw invalid(description, "target escapes the archive root");
                }
            } else {
                if (isWindowsDriveSegment(segment)) {
                    throw invalid(description, "Windows drive paths are not allowed");
                }
                resolvedDepth++;
            }
            targetSegments.add(segment);
        }
        if (targetSegments.isEmpty()) {
            throw invalid(description, "path is empty");
        }
        return String.join("/", targetSegments);
    }

    private static boolean isWindowsDriveSegment(String segment) {
        return segment.length() >= 2
                && ((segment.charAt(0) >= 'A' && segment.charAt(0) <= 'Z')
                        || (segment.charAt(0) >= 'a' && segment.charAt(0) <= 'z'))
                && segment.charAt(1) == ':';
    }

    private static IOException invalid(String description, String reason) {
        return new IOException("Invalid " + description + ": " + reason);
    }
}
