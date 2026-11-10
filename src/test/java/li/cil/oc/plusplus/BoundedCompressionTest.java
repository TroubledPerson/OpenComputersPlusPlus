package li.cil.oc.plusplus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoundedCompressionTest {
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) { out.write(data); }
        return bytes.toByteArray();
    }
    @Test public void acceptsExactBoundaryAndPreservesBinaryBytes() throws Exception {
        byte[] data = new byte[65536];
        for (int i = 0; i < data.length; i++) data[i] = (byte)i;
        assertArrayEquals(data, BoundedCompression.inflate(compress(data), data.length));
    }
    @Test public void zeroLimitAllowsEmptyOutput() throws Exception {
        assertArrayEquals(new byte[0], BoundedCompression.inflate(compress(new byte[0]), 0));
    }
    @Test(expected=IOException.class) public void rejectsOneByteOverBoundary() throws Exception {
        BoundedCompression.inflate(compress(new byte[4097]), 4096);
    }
    @Test(expected=IOException.class) public void rejectsSmallCompressedBomb() throws Exception {
        BoundedCompression.inflate(compress(new byte[2 * 1024 * 1024]), 16384);
    }
    @Test(expected=IOException.class) public void rejectsMalformedInput() throws Exception {
        BoundedCompression.inflate(new byte[] {1,2,3,4}, 1024);
    }
    @Test(expected=IOException.class) public void rejectsTruncatedStream() throws Exception {
        byte[] valid = compress(new byte[65536]);
        BoundedCompression.inflate(Arrays.copyOf(valid, valid.length - 3), 65536);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNegativeLimit() throws Exception {
        BoundedCompression.inflate(new byte[0], -1);
    }
}
