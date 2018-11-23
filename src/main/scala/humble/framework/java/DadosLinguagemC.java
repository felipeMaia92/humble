package humble.framework.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DadosLinguagemC {

  private static final String CHARACTER_ENCODING = "ISO-8859-1";

  public static int readByte(InputStream in) throws IOException {
    return in.read() & 0xFF;
  }

  public static int readU16(InputStream in) throws IOException {
    int first = readByte(in);
    int second = readByte(in);
    int ret = (first) | (second << 8);
    return ret;
  }

  public static long readU32(InputStream in) throws IOException {
    int a = readByte(in);
    int b = readByte(in);
    int c = readByte(in);
    int d = readByte(in);
    long ret = ((long) (a | b << 8 | c << 16 | d << 24)) & 0xFFFFFFFFL;
    return ret;
  }

  public static String readString(InputStream in) throws IOException {
    int length = readU16(in);
    byte[] b = new byte[length];
    int l = in.read(b, 0, length);
    String str = new String(b, 0, l, CHARACTER_ENCODING);
    return str;
  }

  public static void writeByte(OutputStream out, int value) throws IOException {
    out.write(value & 0xFF);
  }

  public static void writeU16(OutputStream out, int value) throws IOException {
    out.write(value & 0x00FF);
    out.write((value & 0xFF00) >> 8);
  }

  public static void writeU32(OutputStream out, long value) throws IOException {
    out.write((int) (value & 0x000000FFL));
    out.write((int) ((value & 0x0000FF00L) >> 8));
    out.write((int) ((value & 0x00FF0000L) >> 16));
    out.write((int) ((value & 0xFF000000L) >> 24));
  }

  public static void writeString(OutputStream out, String string) throws IOException {
    byte[] b = string.getBytes(CHARACTER_ENCODING);
    writeU16(out, b.length);
    out.write(b);
  }

}
