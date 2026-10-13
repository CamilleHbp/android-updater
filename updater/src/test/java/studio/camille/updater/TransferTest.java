package studio.camille.updater;
import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class TransferTest {
    @Test public void verifiesBytesAndDetectsTampering() throws Exception {
        File file = File.createTempFile("update", ".apk");
        try {
            Files.write(file.toPath(), "abc".getBytes("UTF-8"));
            Transfer.verify(file, 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            Files.write(file.toPath(), "abd".getBytes("UTF-8"));
            try { Transfer.verify(file, 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"); fail(); }
            catch (IOException expected) {}
        } finally { file.delete(); }
    }
    @Test public void rejectsOversizedAndTruncatedTransfers() throws Exception {
        for (long expected : new long[]{2, 4}) {
            try { Transfer.copy(new ByteArrayInputStream(new byte[]{1,2,3}), new ByteArrayOutputStream(), expected, expected, null); fail(); }
            catch (IOException success) {}
        }
    }
}
