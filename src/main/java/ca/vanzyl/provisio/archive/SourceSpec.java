/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable mapping and selection policy for one archive source. */
public final class SourceSpec {

    private final Source source;
    private final Selector selector;
    private final boolean useRoot;
    private final boolean flatten;
    private final String destinationPrefix;

    private SourceSpec(Builder builder) {
        source = builder.source;
        selector = new Selector(
                Collections.unmodifiableList(new ArrayList<>(builder.includes)),
                Collections.unmodifiableList(new ArrayList<>(builder.excludes)));
        useRoot = builder.useRoot;
        flatten = builder.flatten;
        destinationPrefix = builder.destinationPrefix;
    }

    public static SourceSpec of(Source source) {
        return builder(source).build();
    }

    public static Builder builder(Source source) {
        return new Builder(source);
    }

    Source source() {
        return source;
    }

    boolean includes(String name) {
        return selector.include(name);
    }

    boolean useRoot() {
        return useRoot;
    }

    boolean flatten() {
        return flatten;
    }

    String destinationPrefix() {
        return destinationPrefix;
    }

    public static final class Builder {

        private final Source source;
        private final List<String> includes = new ArrayList<>();
        private final List<String> excludes = new ArrayList<>();
        private boolean useRoot = true;
        private boolean flatten;
        private String destinationPrefix;

        private Builder(Source source) {
            this.source = requireNonNull(source);
        }

        public Builder includes(String... patterns) {
            addPatterns(includes, patterns);
            return this;
        }

        public Builder includes(Iterable<String> patterns) {
            requireNonNull(patterns).forEach(pattern -> includes.add(requireNonNull(pattern)));
            return this;
        }

        public Builder excludes(String... patterns) {
            addPatterns(excludes, patterns);
            return this;
        }

        public Builder excludes(Iterable<String> patterns) {
            requireNonNull(patterns).forEach(pattern -> excludes.add(requireNonNull(pattern)));
            return this;
        }

        public Builder useRoot(boolean useRoot) {
            this.useRoot = useRoot;
            return this;
        }

        public Builder flatten(boolean flatten) {
            this.flatten = flatten;
            return this;
        }

        public Builder destinationPrefix(String destinationPrefix) {
            this.destinationPrefix = destinationPrefix;
            return this;
        }

        public SourceSpec build() {
            return new SourceSpec(this);
        }

        private void addPatterns(List<String> destination, String[] patterns) {
            for (String pattern : requireNonNull(patterns)) {
                destination.add(requireNonNull(pattern));
            }
        }
    }
}
