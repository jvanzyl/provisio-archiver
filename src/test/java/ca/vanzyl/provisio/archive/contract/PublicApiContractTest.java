package ca.vanzyl.provisio.archive.contract;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import ca.vanzyl.provisio.archive.Archiver;
import ca.vanzyl.provisio.archive.EntryContents;
import ca.vanzyl.provisio.archive.EntryType;
import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.SourceEntry;
import ca.vanzyl.provisio.archive.Sources;
import ca.vanzyl.provisio.archive.UnArchiver;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PublicApiContractTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void publicApiCombinesBuiltInAndCustomStreamingSources() throws Exception {
        Path directory = temporary.newFolder("public-api").toPath();
        Path input = directory.resolve("input.txt");
        Files.write(input, "file source".getBytes(UTF_8));
        AtomicBoolean customClosed = new AtomicBoolean();

        Source custom = new Source() {
            @Override
            public void forEachEntry(EntryConsumer consumer) throws java.io.IOException {
                consumer.accept(
                        SourceEntry.file("custom.txt", EntryContents.of("custom source".getBytes(UTF_8)), 0644, 0));
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public void close() {
                customClosed.set(true);
            }
        };

        Path archive = directory.resolve("combined.zip");
        Archiver.builder().build().archive(archive, Sources.file("renamed.txt", input), custom);

        assertTrue(customClosed.get());
        List<String> names = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        try (Source source = Sources.archive(archive)) {
            source.forEachEntry(entry -> {
                if (entry.getType() == EntryType.FILE) {
                    names.add(entry.getName());
                    try (InputStream stream = entry.getContent().open()) {
                        contents.add(new String(readAll(stream), UTF_8));
                    }
                }
            });
        }

        assertEquals(asList("renamed.txt", "custom.txt"), names);
        assertEquals(asList("file source", "custom source"), contents);
    }

    @Test
    public void sourceFactoriesRejectInvalidContractsImmediately() throws Exception {
        Path input = temporary.newFile("input.txt").toPath();

        assertThrows(NullPointerException.class, () -> Sources.directory(null));
        assertThrows(NullPointerException.class, () -> Sources.directories((Path[]) null));
        assertThrows(NullPointerException.class, () -> Sources.directories(input, null));
        assertThrows(NullPointerException.class, () -> Sources.file((Path) null));
        assertThrows(NullPointerException.class, () -> Sources.file(null, input));
        assertThrows(NullPointerException.class, () -> Sources.zip(null));
        assertThrows(NullPointerException.class, () -> Sources.tarGz(null));
        assertThrows(NullPointerException.class, () -> Sources.archive(null));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> Sources.archive(Paths.get("archive.unknown")));
        assertEquals("Cannot detect archive format for archive.unknown", failure.getMessage());
    }

    @Test
    public void buildersAreTheOnlyPublicConstructionPath() {
        assertTrue(Modifier.isFinal(Archiver.class.getModifiers()));
        assertTrue(Modifier.isFinal(UnArchiver.class.getModifiers()));
        assertEquals(0, Archiver.class.getConstructors().length);
        assertEquals(0, UnArchiver.class.getConstructors().length);
    }

    @Test
    public void moduleExportsOnlyTheRootApiPackage() throws Exception {
        Path descriptor = Paths.get(System.getProperty("basedir"), "target", "classes", "module-info.class");
        Class<?> descriptorType = Class.forName("java.lang.module.ModuleDescriptor");
        Method read = descriptorType.getMethod("read", InputStream.class);
        Object moduleDescriptor;
        try (InputStream input = Files.newInputStream(descriptor)) {
            moduleDescriptor = read.invoke(null, input);
        }

        Set<String> exports = new HashSet<>();
        for (Object exported : (Set<?>) descriptorType.getMethod("exports").invoke(moduleDescriptor)) {
            exports.add((String) exported.getClass().getMethod("source").invoke(exported));
        }
        assertEquals(Collections.singleton("ca.vanzyl.provisio.archive"), exports);
    }

    private static List<String> asList(String first, String second) {
        List<String> values = new ArrayList<>();
        values.add(first);
        values.add(second);
        return values;
    }

    private static byte[] readAll(InputStream input) throws java.io.IOException {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
