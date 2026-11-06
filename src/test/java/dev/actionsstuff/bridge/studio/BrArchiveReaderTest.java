package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BrArchiveReaderTest {
    static byte[] archive(Map<String, byte[]> entries) {
        int table = entries.size() * 256;
        int payloadSize = entries.values().stream().mapToInt(x -> x.length).sum();
        ByteBuffer data = ByteBuffer.allocate(16 + table + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        data.put(new byte[] {(byte)0x7d,0x27,0x25,(byte)0xb1,(byte)0xa0,0x52,0x70,0x26});
        data.putInt(entries.size());
        data.putInt(1);
        int offset = 0;
        for (var e : entries.entrySet()) {
            byte[] name = e.getKey().getBytes(StandardCharsets.UTF_8);
            int start = data.position();
            data.put((byte)name.length);
            data.put(name);
            data.position(start + 248);
            data.putInt(offset);
            data.putInt(e.getValue().length);
            offset += e.getValue().length;
        }
        for (var e : entries.values()) data.put(e);
        return data.array();
    }

    @Test
    void readsOriginalFormatArchiveEntries() {
        Map<String, byte[]> map = new LinkedHashMap<>();
        map.put("a.json", "{\"animations\":{}}".getBytes(StandardCharsets.UTF_8));
        map.put("b.json", "{\"minecraft:geometry\":[]}".getBytes(StandardCharsets.UTF_8));
        Map<String,String> result = new LinkedHashMap<>();
        BrArchiveReader.read(archive(map), (name,bytes)->result.put(name,new String(bytes, StandardCharsets.UTF_8)));
        assertEquals(map.size(), result.size());
        assertTrue(result.containsKey("a.json"));
        assertTrue(result.get("b.json").contains("geometry"));
    }

    @Test
    void pathTraversalAndTruncationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BrArchiveReader.validateName("../evil"));
        assertThrows(IllegalArgumentException.class, () -> BrArchiveReader.validateName("C:/evil"));
        assertThrows(IllegalArgumentException.class, () ->
            BrArchiveReader.read(archive(Map.of("a.json", "{}".getBytes(StandardCharsets.UTF_8))),
                (name, bytes)->{ throw new IllegalArgumentException("callback"); }));
        assertThrows(IllegalArgumentException.class, () -> BrArchiveReader.read(new byte[] {1,2}, (n,b)->{}));
    }
}
