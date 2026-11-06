package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class BedrockPackCatalogTest {
    @TempDir Path temp;

    @Test
    void readsSourceGeometryEntityAliasesAndAnimationFrames() throws Exception {
        Path source = temp.resolve("synthetic.mcpack");
        Map<String,byte[]> members = new LinkedHashMap<>();
        members.put("__brarchive/entity.brarchive", BrArchiveReaderTest.archive(Map.of(
            "player.json", text("{\"minecraft:client_entity\":{\"description\":{"
              + "\"identifier\":\"minecraft:player\","
              + "\"geometry\":{\"test\":\"geometry.test\",\"default\":\"geometry.humanoid.custom\"},"
              + "\"animations\":{\"walk\":\"animation.test.walk\"}}}}")
        )));
        members.put("__brarchive/models/entity.brarchive", BrArchiveReaderTest.archive(Map.of(
            "player.json", text("{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.test\"},"
              + "\"bones\":[{\"name\":\"root\",\"pivot\":[0,0,0]},"
              + "{\"name\":\"body\",\"parent\":\"root\",\"pivot\":[0,12,0],"
              + "\"cubes\":[{\"origin\":[-1,0,0],\"size\":[2,3,4]}]}]}]}")
        )));
        members.put("__brarchive/animations.brarchive", BrArchiveReaderTest.archive(Map.of(
            "walk.json", text("{\"animations\":{\"animation.test.walk\":{\"animation_length\":1,"
              + "\"loop\":true,\"bones\":{\"body\":{\"rotation\":{"
              + "\"0.0\":[0,0,0],\"1.0\":[20,0,0]}}}}}}")
        )));
        members.put("__brarchive/animation_controllers.brarchive", BrArchiveReaderTest.archive(Map.of(
            "controller.json", text("{\"animation_controllers\":{\"controller.test\":"
              + "{\"states\":{\"default\":{},\"moving\":{}}}}}")
        )));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source))) {
            for (var item : members.entrySet()) {
                out.putNextEntry(new ZipEntry(item.getKey()));
                out.write(item.getValue());
                out.closeEntry();
            }
        }
        BedrockPackCatalog catalog = BedrockPackCatalog.open(source);
        assertEquals(1, catalog.entities.size());
        assertEquals(1, catalog.geometry.size());
        assertEquals(1, catalog.animations.size());
        assertEquals(1, catalog.controllers.size());
        assertEquals(2, catalog.controllers.get("controller.test").states().size());
        var bones = catalog.geometriesFor("minecraft:player").get(0).bones();
        assertEquals("root", bones.get(0).name());
        assertEquals(2, bones.size());
        var clip = catalog.animationsFor("minecraft:player").get(0).animation();
        assertArrayEquals(new float[] {10f,0f,0f},clip.rotation("body",0.5f),0.001f);
    }

    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
