package dev.alshabab.colonyspeed.convert;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A standalone NBT reader/writer.
 *
 * Deliberately does not use Minecraft's CompoundTag. The mod is compiled against the SRG-named
 * production jars, so every Minecraft call has to be written as m_128451_ and friends by hand; NBT
 * is a simple enough format that reimplementing it is less work and far less brittle than spelling
 * out three dozen obfuscated getters. It also means the converter is plain Java and can be tested
 * without a game running.
 *
 * Types map to Java as: BYTE->Byte, SHORT->Short, INT->Integer, LONG->Long, FLOAT->Float,
 * DOUBLE->Double, BYTE_ARRAY->byte[], STRING->String, LIST->List, COMPOUND->Map, INT_ARRAY->int[],
 * LONG_ARRAY->long[]. On write the tag id is inferred back from the Java type, so a value read and
 * written again round-trips unchanged.
 */
public final class Nbt {
    private static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    /** Reads a gzipped NBT file. Returns the root compound. */
    public static Map<String, Object> read(final InputStream rawIn) throws IOException {
        try (DataInputStream in = new DataInputStream(new java.util.zip.GZIPInputStream(rawIn))) {
            final int type = in.readUnsignedByte();
            if (type != COMPOUND) {
                throw new IOException("NBT root is not a compound (tag " + type + ")");
            }
            in.readUTF(); // root name, always empty in practice
            return readCompound(in);
        }
    }

    /** Writes a gzipped NBT file with an unnamed root compound, which is what a .blueprint is. */
    public static void write(final OutputStream rawOut, final Map<String, Object> root) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new java.util.zip.GZIPOutputStream(rawOut))) {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            writeCompound(out, root);
        }
    }

    private static Map<String, Object> readCompound(final DataInputStream in) throws IOException {
        final Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            final int type = in.readUnsignedByte();
            if (type == END) {
                return map;
            }
            map.put(in.readUTF(), readPayload(in, type));
        }
    }

    private static Object readPayload(final DataInputStream in, final int type) throws IOException {
        switch (type) {
            case BYTE: return in.readByte();
            case SHORT: return in.readShort();
            case INT: return in.readInt();
            case LONG: return in.readLong();
            case FLOAT: return in.readFloat();
            case DOUBLE: return in.readDouble();
            case STRING: return in.readUTF();
            case COMPOUND: return readCompound(in);
            case BYTE_ARRAY: {
                final byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case INT_ARRAY: {
                final int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case LONG_ARRAY: {
                final long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            case LIST: {
                final int elemType = in.readUnsignedByte();
                final int len = in.readInt();
                final List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) {
                    list.add(readPayload(in, elemType));
                }
                return list;
            }
            default:
                throw new IOException("unknown NBT tag id " + type);
        }
    }

    private static void writeCompound(final DataOutputStream out, final Map<String, Object> map) throws IOException {
        for (final Map.Entry<String, Object> e : map.entrySet()) {
            final int type = tagIdOf(e.getValue());
            out.writeByte(type);
            out.writeUTF(e.getKey());
            writePayload(out, type, e.getValue());
        }
        out.writeByte(END);
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(final DataOutputStream out, final int type, final Object v) throws IOException {
        switch (type) {
            case BYTE: out.writeByte((Byte) v); break;
            case SHORT: out.writeShort((Short) v); break;
            case INT: out.writeInt((Integer) v); break;
            case LONG: out.writeLong((Long) v); break;
            case FLOAT: out.writeFloat((Float) v); break;
            case DOUBLE: out.writeDouble((Double) v); break;
            case STRING: out.writeUTF((String) v); break;
            case COMPOUND: writeCompound(out, (Map<String, Object>) v); break;
            case BYTE_ARRAY: {
                final byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
                break;
            }
            case INT_ARRAY: {
                final int[] a = (int[]) v;
                out.writeInt(a.length);
                for (final int i : a) {
                    out.writeInt(i);
                }
                break;
            }
            case LONG_ARRAY: {
                final long[] a = (long[]) v;
                out.writeInt(a.length);
                for (final long l : a) {
                    out.writeLong(l);
                }
                break;
            }
            case LIST: {
                final List<Object> list = (List<Object>) v;
                // An empty list is written as LIST<END>, which is what vanilla and Structurize both do
                // for an empty "entities" tag.
                final int elemType = list.isEmpty() ? END : tagIdOf(list.get(0));
                out.writeByte(elemType);
                out.writeInt(list.size());
                for (final Object o : list) {
                    writePayload(out, elemType, o);
                }
                break;
            }
            default:
                throw new IOException("cannot write NBT tag id " + type);
        }
    }

    private static int tagIdOf(final Object v) throws IOException {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof String) return STRING;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof long[]) return LONG_ARRAY;
        if (v instanceof List) return LIST;
        if (v instanceof Map) return COMPOUND;
        throw new IOException("no NBT tag for java type " + (v == null ? "null" : v.getClass().getName()));
    }

    private Nbt() {
    }
}
