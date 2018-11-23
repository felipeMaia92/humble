package humble.framework.java;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamLinguagemC extends OutputStream {
  private OutputStream stream;
  public OutputStreamLinguagemC(OutputStream stream) { this.stream = stream; }
  public void writeByte(int value) throws IOException { humble.framework.java.DadosLinguagemC.writeByte(this.stream, value); }
  public void writeU16(int value) throws IOException { humble.framework.java.DadosLinguagemC.writeU16(this.stream, value); }
  public void writeU32(long value) throws IOException { humble.framework.java.DadosLinguagemC.writeU32(this.stream, value); }
  public void writeString(String string) throws IOException { humble.framework.java.DadosLinguagemC.writeString(this.stream, string); }
  public void write(int val) throws IOException { this.stream.write(val); }
  public void write(byte[] b, int off, int len) throws IOException { this.stream.write(b, off, len); }
  public void write(byte[] b) throws IOException { this.stream.write(b); }
  public void close() throws IOException { this.stream.close(); }
  public void flush() throws IOException { this.stream.flush(); }
}
