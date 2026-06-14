package {{BASE_PACKAGE}}.infrastructure.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class {{AGGREGATE}}FileStorageAdapterTest {
    @TempDir
    Path tempDir;

    @Test
    void stores_and_reads_bytes() {
        var adapter = new {{AGGREGATE}}FileStorageAdapter(tempDir);
        var objectId = UUID.randomUUID();
        var content = "example content".getBytes(StandardCharsets.UTF_8);

        adapter.store(objectId, content);

        assertThat(adapter.read(objectId)).contains(content);
    }
}
