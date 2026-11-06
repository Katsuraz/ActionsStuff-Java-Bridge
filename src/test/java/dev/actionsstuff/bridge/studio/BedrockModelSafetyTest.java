package dev.actionsstuff.bridge.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class BedrockModelSafetyTest {
    @TempDir Path directory;
    private static byte[] json(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void facialDetailsAreBlockedWhenSourceRenderControllerGatesVisibility() throws Exception {
        Path mcpack = directory.resolve("private-test.mcpack");
        Map<String,byte[]> source = new LinkedHashMap<>();
        source.put("__brarchive/entity.brarchive",BrArchiveReaderTest.archive(Map.of(
          "player.json",json("{\"minecraft:client_entity\":{\"description\":"
          + "{\"identifier\":\"minecraft:player\","
          + "\"geometry\":{\"face\":\"geometry.source.face\",\"full\":\"geometry.test.full\"},"
          + "\"animations\":{},\"render_controllers\":[{\"controller.render.face\":\"v.show_face&&!q.is_spectator\"}]}}}"))));
        source.put("__brarchive/render_controllers.brarchive",BrArchiveReaderTest.archive(Map.of(
          "face.json",json("{\"render_controllers\":{\"controller.render.face\":{\"geometry\":\"Geometry.face\","
          + "\"textures\":[\"v.variant?Texture.transparent:Texture.default\"]}}}"))));
        String face = "{\"description\":{\"identifier\":\"geometry.source.face\","
          + "\"texture_width\":64,\"texture_height\":64},\"bones\":["
          + "{\"name\":\"root\",\"pivot\":[0,0,0]},"
          + "{\"name\":\"body\",\"parent\":\"root\",\"pivot\":[0,12,0]},"
          + "{\"name\":\"head\",\"parent\":\"body\",\"pivot\":[0,24,0]},"
          + "{\"name\":\"face_expression\",\"parent\":\"head\",\"pivot\":[0,28,-4],"
          + "\"cubes\":[{\"origin\":[0,25,-5],\"size\":[2,2,1]}]}]}";
        String full = "{\"description\":{\"identifier\":\"geometry.test.full\","
          + "\"texture_width\":64,\"texture_height\":64},\"bones\":["
          + "{\"name\":\"root\",\"pivot\":[0,0,0]},"
          + "{\"name\":\"body\",\"parent\":\"root\",\"pivot\":[0,12,0],\"cubes\":[{}]},"
          + "{\"name\":\"head\",\"parent\":\"body\",\"pivot\":[0,24,0],\"cubes\":[{}]},"
          + "{\"name\":\"left_arm\",\"parent\":\"body\",\"pivot\":[-5,22,0],\"cubes\":[{}]},"
          + "{\"name\":\"right_arm\",\"parent\":\"body\",\"pivot\":[5,22,0],\"cubes\":[{}]},"
          + "{\"name\":\"left_leg\",\"parent\":\"root\",\"pivot\":[-2,12,0],\"cubes\":[{}]},"
          + "{\"name\":\"right_leg\",\"parent\":\"root\",\"pivot\":[2,12,0],\"cubes\":[{}]}]}";
        source.put("__brarchive/models/entity.brarchive",BrArchiveReaderTest.archive(Map.of(
          "model.json",json("{\"minecraft:geometry\":["+face+","+full+"]}"))));
        source.put("__brarchive/animations.brarchive",BrArchiveReaderTest.archive(Map.of(
          "idle.json",json("{\"animations\":{\"animation.synthetic\":{\"bones\":{}}}}"))));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(mcpack))) {
            for (var item : source.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
        var catalog = BedrockPackCatalog.open(mcpack);
        assertEquals("Geometry.face",catalog.renderControllers.get("controller.render.face").geometryRef());
        var faceModel=catalog.geometry.get("geometry.source.face");
        var result=BedrockModelSafety.assess(catalog,faceModel);
        assertFalse(result.worldSafe());
        assertEquals("head / face accessory",result.kind());
        assertEquals("controller.render.face",result.controllerId());
        assertTrue(result.condition().contains("v.show_face"));
        var fullModel=catalog.geometry.get("geometry.test.full");
        assertTrue(BedrockModelSafety.assess(catalog,fullModel).worldSafe());
    }
}
