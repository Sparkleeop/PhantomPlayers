package me.sparklee.phantomplayers.names;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamePoolTest {

    @Test
    void parsesCommentsBlanksAndPreservesOrder() {
        NamePool pool = NamePool.parse("""
                # comment
                PixelRanger

                CloudyFox
                  BlockVoyager
                """);
        assertEquals(3, pool.size());
        assertEquals("PixelRanger", pool.names().get(0));
        assertEquals("CloudyFox", pool.names().get(1));
        assertEquals("BlockVoyager", pool.names().get(2));
        assertTrue(pool.warnings().isEmpty());
    }

    @Test
    void rejectsInvalidNamesAndDuplicates() {
        NamePool pool = NamePool.parse("""
                ab
                Valid_Name
                valid_name
                bad-name!
                Toolongusername123
                Winner_77
                """);
        assertEquals(2, pool.size());
        assertEquals("Valid_Name", pool.names().get(0));
        assertEquals("Winner_77", pool.names().get(1));
        assertEquals(4, pool.warnings().size());
    }

    @Test
    void builtInPoolIsLargeAndValid() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/names.txt")) {
            assertTrue(stream != null, "names.txt resource is missing");
            NamePool pool = NamePool.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(pool.size() >= 500, "expected at least 500 bundled names, got " + pool.size());
            assertTrue(pool.warnings().isEmpty(), String.join(", ", pool.warnings()));
            for (String name : pool.names()) {
                assertTrue(NameRules.isValid(name), name);
                assertFalse(name.contains(" "), name);
            }
        }
    }
}
