package humble.framework.java;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamLinguagemC {
  private InputStream stream;
  public InputStreamLinguagemC(InputStream stream) { this.stream = stream; }
  public String readString() throws IOException { return humble.framework.java.DadosLinguagemC.readString(this.stream); }
  public int readByte() throws IOException { return humble.framework.java.DadosLinguagemC.readByte(this.stream); }
  public int readU16() throws IOException { return humble.framework.java.DadosLinguagemC.readU16(this.stream); }
  public long readU32() throws IOException { return humble.framework.java.DadosLinguagemC.readU32(this.stream); }
  public int read() throws IOException { return this.stream.read(); }
  public int read(byte[] b, int off, int len) throws IOException { return this.stream.read(b, off, len); }
  public int read(byte[] b) throws IOException { return this.stream.read(b); }
  public int available() throws IOException { return this.stream.available(); }
  public void close() throws IOException { this.stream.close(); }
  public synchronized void mark(int arg0) { this.stream.mark(arg0); }
  public boolean markSupported() { return this.stream.markSupported(); }
  public synchronized void reset() throws IOException { this.stream.reset(); }
  public long skip(long arg0) throws IOException { return this.stream.skip(arg0); }
}
