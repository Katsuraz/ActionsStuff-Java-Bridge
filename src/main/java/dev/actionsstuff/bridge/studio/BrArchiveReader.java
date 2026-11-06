package dev.actionsstuff.bridge.studio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Reader for the observed Bedrock BRARCHIVE v1 container.
 * Reads in memory only; does not extract files to the filesystem.
 */
public final class BrArchiveReader {
    private static final byte[] MAGIC = {(byte)0x7d,0x27,0x25,(byte)0xb1,(byte)0xa0,0x52,0x70,0x26};
    private static final int ENTRY_BYTES = 256;
    private static final int MAX_MEMBERS = 50_000;
    private static final int MAX_FILE = 24 * 1024 * 1024;
    private BrArchiveReader() {}

    public static void read(byte[] content, BiConsumer<String, byte[]> each) {
        if (content.length < 16) throw new IllegalArgumentException("Archive truncated");
        for (int i = 0; i < MAGIC.length; i++) {
            if (content[i] != MAGIC[i]) throw new IllegalArgumentException("Not a BRARCHIVE v1");
        }
        ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        long count = Integer.toUnsignedLong(buf.getInt(8));
        long version = Integer.toUnsignedLong(buf.getInt(12));
        if (version != 1 || count > MAX_MEMBERS) {
            throw new IllegalArgumentException("Unsupported archive version/member count");
        }
        long payloadAt = 16 + count * ENTRY_BYTES;
        if (payloadAt > content.length) throw new IllegalArgumentException("Archive index truncated");
        for (int i = 0; i < count; i++) {
            int at = 16 + i * ENTRY_BYTES;
            int n = Byte.toUnsignedInt(content[at]);
            if (n == 0 || n > 240) throw new IllegalArgumentException("Invalid member name length");
            String name = new String(content, at + 1, n, StandardCharsets.UTF_8);
            validateName(name);
            long offset = Integer.toUnsignedLong(buf.getInt(at + 248));
            long length = Integer.toUnsignedLong(buf.getInt(at + 252));
            if (length > MAX_FILE || offset + length > content.length - payloadAt) {
                throw new IllegalArgumentException("Archive member out of bounds: " + name);
            }
            byte[] data = new byte[(int) length];
            System.arraycopy(content, (int) (payloadAt + offset), data, 0, data.length);
            each.accept(name, data);
        }
    }

    public static void validateName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\")
                || name.indexOf('\0') >= 0 || name.contains(":")) {
            throw new IllegalArgumentException("Unsafe entry name");
        }
        for (String part : name.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Unsafe path component");
            }
        }
    }
}
