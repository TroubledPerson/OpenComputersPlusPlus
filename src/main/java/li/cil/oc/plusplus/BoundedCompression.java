package li.cil.oc.plusplus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

/** Bounded zlib expansion. Input limits and energy accounting remain in DataCard. */
public final class BoundedCompression {
    private BoundedCompression() { }

    public static byte[] inflate(byte[] input, int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("negative decompression limit");
        try (InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(input))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(512, limit));
            byte[] chunk = new byte[4096];
            int size;
            while ((size = stream.read(chunk)) != -1) {
                if (size > limit - output.size())
                    throw new IOException("decompressed data size limit exceeded");
                output.write(chunk, 0, size);
            }
            return output.toByteArray();
        }
    }
}
