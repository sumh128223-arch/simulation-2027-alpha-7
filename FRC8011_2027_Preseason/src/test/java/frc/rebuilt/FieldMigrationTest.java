package frc.rebuilt;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;

class FieldMigrationTest {
  @Test
  void existingLayoutsPreserveDimensionsAndTags() throws Exception {
    for (String variant : new String[] {"andymark", "welded"}) {
      for (String subset : new String[] {"official", "none", "hub", "outpost", "tower"}) {
        var layout = org.wpilib.fields.Field.loadFromFile(
            Path.of("src/main/deploy/apriltags", variant, "2026-" + subset + ".json"));
        assertEquals(variant.equals("welded") ? 16.541 : Field.LENGTH, layout.getFieldLength(), 1e-6, variant + subset);
        assertEquals(variant.equals("welded") ? 8.069 : Field.WIDTH, layout.getFieldWidth(), 1e-6, variant + subset);
        assertNotNull(layout.getTags());
      }
    }
    var official = Field.AprilTagLayoutType.OFFICIAL.getLayout();
    assertEquals(Field.APRILTAG_COUNT, official.getTags().size());
    var tag = official.getTagPose(1).orElseThrow();
    assertEquals(11.863959, tag.getX(), 1e-6);
    assertEquals(7.4114914, tag.getY(), 1e-6);
    assertEquals(0.889, tag.getZ(), 1e-6);
    assertEquals(Math.PI, Math.abs(tag.getRotation().getZ()), 1e-6);
    assertTrue(Field.AprilTagLayoutType.NONE.getLayout().getTags().isEmpty());
  }
}
