package ca.vanzyl.provisio.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StringListSource implements Source {

    private final List<String> entries;

    public StringListSource(List<String> entries) {
        this.entries = entries;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        for (String entry : entries) {
            consumer.accept(entry(entry));
        }
    }

    static SourceEntry entry(String name) {
        return SourceEntry.file(name, EntryContents.of(name.getBytes(StandardCharsets.UTF_8)), 0, 0);
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public void close() {}
}
