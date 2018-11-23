package humble.framework.java;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;

public class EngineCriptografiaXTEA {
  public final static int rounds = 32, keySize = 16, blockSize = 8;
  private final static int delta = 0x9e3779b9, decryptSum = 0xc6ef3720;
  public long[] key;
  public void init(long[] key) throws InvalidKeyException {
    if (key == null) throw new InvalidKeyException("Null key");
    if (key.length != keySize / 4) throw new InvalidKeyException("Invalid key length (req. " + keySize + " bytes got " + key.length * 4 + ")");
    this.key = key.clone();
  }
  public void init(byte[] key) throws InvalidKeyException {
    if (key == null) throw new InvalidKeyException("Null key");
    if (key.length != keySize) throw new InvalidKeyException("Invalid key length (req. " + keySize + " bytes got " + key.length * 4 + ")");
    humble.framework.java.InputStreamLinguagemC cin = new humble.framework.java.InputStreamLinguagemC(new ByteArrayInputStream(key));
    try { init(new long[] { cin.readU32(), cin.readU32(), cin.readU32(), cin.readU32() }); } catch (IOException e) { }
  }
  public byte[] encrypt(byte[] buffer) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    encrypt(out, buffer);
    return out.toByteArray();
  }
  public void encrypt(OutputStream out, byte[] buffer) throws IOException { encrypt(out, buffer, 0); }
  public void encrypt(OutputStream out, byte[] buffer, int off) throws IOException {
    InputStream in = new ByteArrayInputStream(buffer, off, buffer.length - off);
    encrypt(in, out);
  }
  public void encrypt(InputStream in, OutputStream out) throws IOException {
    int v0 = readInt(in);
    int v1 = readInt(in);
    int sum = 0;
    for (int i = 0; i < rounds; i++) {
      v0 += ((((v1 << 4) ^ (v1 >>> 5)) + v1) ^ (sum + key[(int) (sum & 3)]));
      sum += delta;
      v1 += ((((v0 << 4) ^ (v0 >>> 5)) + v0) ^ (sum + key[(int) ((sum >>> 11) & 3)]));
    }
    writeInt(out, v0);
    writeInt(out, v1);
  }
  public byte[] decrypt(byte[] buffer) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    decrypt(out, buffer);
    return out.toByteArray();
  }
  public void decrypt(OutputStream out, byte[] buffer) throws IOException {
    InputStream in = new ByteArrayInputStream(buffer);
    decrypt(in, out);
  }
  public void decrypt(InputStream in, OutputStream out) throws IOException {
    int v0 = readInt(in);
    int v1 = readInt(in);
    int sum = decryptSum;
    for (int i = 0; i < rounds; i++) {
      v1 -= ((((v0 << 4) ^ (v0 >>> 5)) + v0) ^ (sum + key[(int) ((sum >>> 11) & 3)]));
      sum -= delta;
      v0 -= ((((v1 << 4) ^ (v1 >>> 5)) + v1) ^ (sum + key[(int) (sum & 3)]));
    }
    writeInt(out, v0);
    writeInt(out, v1);
  }
  public byte[] decrypt(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    decrypt(in, out);
    return out.toByteArray();
  }
  public int readInt(InputStream in) throws IOException {
    int a = in.read();
    int b = in.read();
    int c = in.read();
    int d = in.read();
    return (((a & 0xff) << 0) | ((b & 0xff) << 8) | ((c & 0xff) << 16) | ((d & 0xff) << 24));
  }
  public void writeInt(OutputStream out, int v) throws IOException {
    out.write((byte) (0xff & (v >> 0)));
    out.write((byte) (0xff & (v >> 8)));
    out.write((byte) (0xff & (v >> 16)));
    out.write((byte) (0xff & (v >> 24)));
  }
}
