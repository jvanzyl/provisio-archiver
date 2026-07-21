/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package ca.vanzyl.provisio.archive;

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.SelectorUtils;

/** Mutable state and temporary resources for exactly one archive operation. */
final class ArchiveSession implements Closeable {

    private final ArchiveFormat format;
    private final ArchiveOptions options;
    private final ArchiveWriter writer;
    private final ContentSpool contentSpool;
    private final Selector hardLinkSelector;
    private final Map<Long, List<ContentTarget>> hardLinkTargets = new HashMap<>();
    private final Map<MetadataIdentity, String> metadataHardLinkTargets = new HashMap<>();
    private final Set<Long> metadataHardLinkSizes = new HashSet<>();
    private final Map<MetadataIdentity, MetadataGroup> metadataGroups = new HashMap<>();
    private final Map<String, Boolean> paths = new HashMap<>();
    private final Map<String, PendingEntry> entries = new TreeMap<>();
    private boolean finished;
    private boolean closed;

    ArchiveSession(Path output, ArchiveFormat format, ArchiveOptions options) throws IOException {
        this.format = format;
        this.options = options;
        contentSpool = new ContentSpool(output.getParent());
        writer = format.openWriter(output, options.posixLongFileMode(), options.gzipCompression());
        if (!options.hardLinkIncludes().isEmpty() || !options.hardLinkExcludes().isEmpty()) {
            hardLinkSelector = new Selector(options.hardLinkIncludes(), options.hardLinkExcludes());
        } else {
            hardLinkSelector = new Selector(null, Collections.singletonList("**/**"));
        }
    }

    void add(SourceSpec sourceSpec) throws IOException {
        requireActive();
        ArchivePath destinationPrefix = destinationPrefix(sourceSpec);
        try (Source source = sourceSpec.source()) {
            source.forEachEntry(entry -> addSourceEntry(sourceSpec, source, destinationPrefix, entry));
        }
    }

    void finish() throws IOException {
        requireActive();
        for (PendingEntry entry : entries.values()) {
            writeEntry(entry);
        }
        finished = true;
    }

    private void addSourceEntry(SourceSpec sourceSpec, Source source, ArchivePath destinationPrefix, SourceEntry entry)
            throws IOException {
        ArchivePath sourcePath = ArchivePath.parse(entry.getName(), "source entry path");
        if (!sourceSpec.includes(sourcePath.entryName(entry.getType()))) {
            return;
        }
        if (source.isDirectory() && !sourceSpec.useRoot() && entry.isDirectory() && sourcePath.hasSingleSegment()) {
            return;
        }
        if (source.isDirectory() && sourceSpec.flatten() && entry.isDirectory()) {
            return;
        }
        ArchivePath outputPath = mapPath(sourceSpec, source, destinationPrefix, sourcePath);
        String entryName = outputPath.entryName(entry.getType());
        String linkTarget = mapLinkTarget(sourceSpec, source, destinationPrefix, outputPath, entry);
        boolean executable = isExecutable(entry.getName()) || isExecutable(entryName);

        addMissingParentDirectories(outputPath.value());

        String path = outputPath.value();
        if (!paths.containsKey(path)) {
            paths.put(path, Boolean.TRUE);
            OutputEntry archiveEntry = createOutputEntry(entryName, entry, executable, linkTarget);
            addEntry(entryName, archiveEntry, hardLinkSelector.include(entryName));
        } else if (Boolean.TRUE.equals(paths.get(path)) || !entry.isDirectory()) {
            throw new IllegalArgumentException("Duplicate archive entry " + entryName);
        } else {
            paths.put(path, Boolean.TRUE);
        }
    }

    private boolean isExecutable(String name) {
        for (String executable : options.executables()) {
            if (SelectorUtils.match(executable, name)) {
                return true;
            }
        }
        return false;
    }

    private ArchivePath mapPath(
            SourceSpec sourceSpec, Source source, ArchivePath destinationPrefix, ArchivePath sourcePath) {
        ArchivePath mapped = sourcePath;
        if (source.isDirectory()) {
            if (!sourceSpec.useRoot()) {
                mapped = mapped.withoutFirstSegment();
            }
            if (sourceSpec.flatten()) {
                mapped = mapped.fileName();
            }
        }
        return mapped.prepend(destinationPrefix);
    }

    private String mapLinkTarget(
            SourceSpec sourceSpec,
            Source source,
            ArchivePath destinationPrefix,
            ArchivePath outputPath,
            SourceEntry entry)
            throws IOException {
        if (entry.isSymbolicLink()) {
            return ArchivePath.validateSymbolicLinkTarget(outputPath, entry.getLinkTarget());
        }
        if (entry.isHardLink()) {
            ArchivePath sourceTarget = ArchivePath.parse(entry.getLinkTarget(), "hard link target");
            return mapPath(sourceSpec, source, destinationPrefix, sourceTarget).value();
        }
        return null;
    }

    private ArchivePath destinationPrefix(SourceSpec sourceSpec) throws IOException {
        String destinationPrefix = sourceSpec.destinationPrefix();
        if (destinationPrefix == null || destinationPrefix.isEmpty()) {
            return null;
        }
        return ArchivePath.parse(destinationPrefix, "source destination prefix");
    }

    private void addMissingParentDirectories(String entryPath) throws IOException {
        List<String> missingParents = new ArrayList<>();
        int separator = entryPath.lastIndexOf('/');
        while (separator >= 0) {
            String parent = entryPath.substring(0, separator);
            if (paths.putIfAbsent(parent, Boolean.FALSE) != null) {
                break;
            }
            missingParents.add(parent);
            separator = parent.lastIndexOf('/');
        }
        for (int index = missingParents.size() - 1; index >= 0; index--) {
            String directoryName = missingParents.get(index) + "/";
            OutputEntry directoryEntry =
                    createOutputEntry(directoryName, SourceEntry.directory(directoryName, -1, 0), false, null);
            addEntry(directoryName, directoryEntry, false);
        }
    }

    private OutputEntry createOutputEntry(String name, SourceEntry source, boolean executable, String linkTarget) {
        ReproducibilityPolicy policy = options.reproducibilityPolicy();
        return OutputEntry.from(
                name,
                source,
                policy.fileMode(source, executable),
                policy.timestamp(name, source),
                linkTarget,
                policy.userId(),
                policy.groupId(),
                policy.userName(),
                policy.groupName());
    }

    private void addEntry(String entryName, OutputEntry entry, boolean hardLinkEligible) throws IOException {
        boolean eligible = format == ArchiveFormat.TAR_GZ && entry.getType() == EntryType.FILE && hardLinkEligible;
        if (options.entryOrder() == EntryOrder.NAME) {
            MetadataGroup metadataGroup = null;
            if (eligible && options.contentIdentityMode() == ContentIdentityMode.SIZE_AND_CRC32) {
                MetadataIdentity identity = MetadataIdentity.from(entry.getContent());
                if (identity != null) {
                    metadataGroup = metadataGroups.get(identity);
                    if (metadataGroup == null) {
                        metadataGroup = new MetadataGroup(contentSpool.stabilize(entry.getContent()), identity);
                        metadataGroups.put(identity, metadataGroup);
                    }
                }
            }
            if (entry.getType() == EntryType.FILE && metadataGroup == null) {
                entry = entry.withContent(contentSpool.stabilize(entry.getContent()));
            }
            PendingEntry pendingEntry = new PendingEntry(entry, eligible, metadataGroup);
            entries.put(entryName, pendingEntry);
        } else {
            writeEntry(new PendingEntry(entry, eligible, null));
        }
    }

    private void writeEntry(PendingEntry pendingEntry) throws IOException {
        OutputEntry entry = pendingEntry.entry;
        if (!pendingEntry.hardLinkEligible) {
            writer.write(entry);
            return;
        }

        if (pendingEntry.metadataGroup != null) {
            writeNameOrderedMetadataEntry(entry, pendingEntry.metadataGroup);
            return;
        }

        if (options.contentIdentityMode() == ContentIdentityMode.SIZE_AND_CRC32) {
            MetadataIdentity identity = MetadataIdentity.from(entry.getContent());
            if (identity != null) {
                writeSourceOrderedMetadataEntry(entry, identity);
                return;
            }
        }

        writeVerifiedEntry(entry);
    }

    private void writeNameOrderedMetadataEntry(OutputEntry entry, MetadataGroup group) throws IOException {
        if (group.targetEntryName == null) {
            writeSourceOrderedMetadataEntry(entry.withContent(group.content), group.identity);
            group.targetEntryName = metadataHardLinkTargets.get(group.identity);
        } else {
            writer.write(OutputEntry.hardLink(entry.getName(), group.targetEntryName, entry));
        }
    }

    private void writeSourceOrderedMetadataEntry(OutputEntry entry, MetadataIdentity identity) throws IOException {
        String target = metadataHardLinkTargets.get(identity);
        if (target == null) {
            writer.write(entry);
            addMetadataHardLinkTarget(identity, entry.getName());
        } else {
            writer.write(OutputEntry.hardLink(entry.getName(), target, entry));
        }
    }

    private void writeVerifiedEntry(OutputEntry entry) throws IOException {
        long declaredSize = entry.getContent().size();
        List<ContentTarget> candidates = hardLinkTargets.get(declaredSize);
        if (candidates == null && !metadataHardLinkSizes.contains(declaredSize)) {
            ContentFingerprint fingerprint = writeAndFingerprint(entry);
            addHardLinkTarget(declaredSize, fingerprint, entry.getName());
            addMetadataHardLinkTarget(fingerprint.metadataIdentity(), entry.getName());
            return;
        }

        FingerprintedContent fingerprintedContent = fingerprintContent(entry.getName(), entry.getContent());
        String metadataTarget = metadataHardLinkTargets.get(fingerprintedContent.fingerprint.metadataIdentity());
        if (metadataTarget != null) {
            writer.write(OutputEntry.hardLink(entry.getName(), metadataTarget, entry));
            return;
        }
        if (candidates != null) {
            for (ContentTarget candidate : candidates) {
                if (candidate.fingerprint.equals(fingerprintedContent.fingerprint)) {
                    writer.write(OutputEntry.hardLink(entry.getName(), candidate.entryName, entry));
                    return;
                }
            }
        }

        writer.write(entry.withContent(fingerprintedContent.content));
        addHardLinkTarget(declaredSize, fingerprintedContent.fingerprint, entry.getName());
        addMetadataHardLinkTarget(fingerprintedContent.fingerprint.metadataIdentity(), entry.getName());
    }

    private ContentFingerprint writeAndFingerprint(OutputEntry entry) throws IOException {
        FingerprintingContent content = new FingerprintingContent(entry.getName(), entry.getContent());
        writer.write(entry.withContent(content));
        return content.fingerprint();
    }

    private FingerprintedContent fingerprintContent(String entryName, EntryContent content) throws IOException {
        EntryContent stableContent = content.isRepeatable() ? content : contentSpool.stabilize(content);
        return new FingerprintedContent(stableContent, fingerprint(entryName, stableContent));
    }

    private ContentFingerprint fingerprint(String entryName, EntryContent content) throws IOException {
        MessageDigest digest = sha256();
        CRC32 crc32 = new CRC32();
        long size = 0;
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = content.open()) {
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                crc32.update(buffer, 0, count);
                size += count;
            }
        }
        validateSize(entryName, content.size(), size);
        return new ContentFingerprint(size, crc32.getValue(), digest.digest());
    }

    private void addHardLinkTarget(long declaredSize, ContentFingerprint fingerprint, String entryName) {
        hardLinkTargets
                .computeIfAbsent(declaredSize, ignored -> new ArrayList<>())
                .add(new ContentTarget(fingerprint, entryName));
    }

    private void addMetadataHardLinkTarget(MetadataIdentity identity, String entryName) {
        metadataHardLinkTargets.putIfAbsent(identity, entryName);
        metadataHardLinkSizes.add(identity.size);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by the Java platform", e);
        }
    }

    private static void validateSize(String entryName, long expected, long actual) throws IOException {
        if (expected != actual) {
            throw new IOException("Content size mismatch for archive entry " + entryName + ": expected " + expected
                    + ", read " + actual);
        }
    }

    private void requireActive() {
        if (closed) {
            throw new IllegalStateException("Archive session is closed");
        }
        if (finished) {
            throw new IllegalStateException("Archive session is finished");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            writer.close();
        } catch (IOException e) {
            failure = e;
        }
        try {
            contentSpool.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static final class ContentSpool implements Closeable {

        private final Path temporaryDirectory;
        private final List<Path> contentFiles = new ArrayList<>();

        private ContentSpool(Path temporaryDirectory) {
            this.temporaryDirectory = temporaryDirectory;
        }

        private EntryContent stabilize(EntryContent entryContent) throws IOException {
            Path content = Files.createTempFile(temporaryDirectory, ".provisio-entry-", ".tmp");
            boolean completed = false;
            try {
                try (InputStream inputStream = entryContent.open();
                        OutputStream outputStream = Files.newOutputStream(content)) {
                    IOUtils.copyLarge(inputStream, outputStream);
                }
                EntryContent stableContent = EntryContents.of(content);
                contentFiles.add(content);
                completed = true;
                return stableContent;
            } finally {
                if (!completed) {
                    Files.deleteIfExists(content);
                }
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (Path content : contentFiles) {
                try {
                    Files.deleteIfExists(content);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class PendingEntry {

        private final OutputEntry entry;
        private final boolean hardLinkEligible;
        private final MetadataGroup metadataGroup;

        private PendingEntry(OutputEntry entry, boolean hardLinkEligible, MetadataGroup metadataGroup) {
            this.entry = entry;
            this.hardLinkEligible = hardLinkEligible;
            this.metadataGroup = metadataGroup;
        }
    }

    private static final class MetadataGroup {

        private final EntryContent content;
        private final MetadataIdentity identity;
        private String targetEntryName;

        private MetadataGroup(EntryContent content, MetadataIdentity identity) {
            this.content = content;
            this.identity = identity;
        }
    }

    private static final class MetadataIdentity {

        private static final long MAXIMUM_CRC32 = 0xffff_ffffL;

        private final long size;
        private final long crc32;

        private MetadataIdentity(long size, long crc32) {
            this.size = size;
            this.crc32 = crc32;
        }

        private static MetadataIdentity from(EntryContent content) {
            long size = content.size();
            long crc32 = content.crc32();
            if (size < 0 || crc32 < 0 || crc32 > MAXIMUM_CRC32) {
                return null;
            }
            return new MetadataIdentity(size, crc32);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MetadataIdentity)) {
                return false;
            }
            MetadataIdentity that = (MetadataIdentity) other;
            return size == that.size && crc32 == that.crc32;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(size) + Long.hashCode(crc32);
        }
    }

    private static final class ContentTarget {

        private final ContentFingerprint fingerprint;
        private final String entryName;

        private ContentTarget(ContentFingerprint fingerprint, String entryName) {
            this.fingerprint = fingerprint;
            this.entryName = entryName;
        }
    }

    private static final class FingerprintedContent {

        private final EntryContent content;
        private final ContentFingerprint fingerprint;

        private FingerprintedContent(EntryContent content, ContentFingerprint fingerprint) {
            this.content = content;
            this.fingerprint = fingerprint;
        }
    }

    private static final class ContentFingerprint {

        private final long size;
        private final long crc32;
        private final byte[] sha256;

        private ContentFingerprint(long size, long crc32, byte[] sha256) {
            this.size = size;
            this.crc32 = crc32;
            this.sha256 = sha256;
        }

        private MetadataIdentity metadataIdentity() {
            return new MetadataIdentity(size, crc32);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ContentFingerprint)) {
                return false;
            }
            ContentFingerprint that = (ContentFingerprint) other;
            return size == that.size && Arrays.equals(sha256, that.sha256);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(size) + Arrays.hashCode(sha256);
        }
    }

    private static final class FingerprintingContent implements EntryContent {

        private final String entryName;
        private final EntryContent delegate;
        private final MessageDigest digest = sha256();
        private final CRC32 crc32 = new CRC32();
        private long size;
        private boolean opened;

        private FingerprintingContent(String entryName, EntryContent delegate) {
            this.entryName = entryName;
            this.delegate = delegate;
        }

        @Override
        public InputStream open() throws IOException {
            if (opened) {
                throw new IOException("Fingerprinting content opened more than once for " + entryName);
            }
            opened = true;
            return new FilterInputStream(delegate.open()) {
                @Override
                public int read() throws IOException {
                    int value = super.read();
                    if (value != -1) {
                        digest.update((byte) value);
                        crc32.update(value);
                        size++;
                    }
                    return value;
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = super.read(bytes, offset, length);
                    if (count != -1) {
                        digest.update(bytes, offset, count);
                        crc32.update(bytes, offset, count);
                        size += count;
                    }
                    return count;
                }
            };
        }

        @Override
        public long size() {
            return delegate.size();
        }

        private ContentFingerprint fingerprint() throws IOException {
            if (!opened) {
                throw new IOException("Archive writer did not consume content for " + entryName);
            }
            validateSize(entryName, delegate.size(), size);
            return new ContentFingerprint(size, crc32.getValue(), digest.digest());
        }
    }
}
