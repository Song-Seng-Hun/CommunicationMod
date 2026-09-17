import communicationmod.DataReader;
import communicationmod.DataWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

/** Exercises the actual transport threads, not Steam/game classes. */
public final class TransportTest {
    public static void main(String[] args) throws Exception {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(4);
        Thread eof = new Thread(new DataReader(queue, new ByteArrayInputStream(new byte[0]), false));
        eof.setDaemon(true);
        eof.start();
        eof.join(1000);
        if (eof.isAlive()) { eof.interrupt(); throw new AssertionError("Reader spins after EOF"); }
        byte[] utf8 = "{\"text\":\"한글 보석\"}\r\n\n".getBytes(StandardCharsets.UTF_8);
        new DataReader(queue, new ByteArrayInputStream(utf8), false).run();
        check(queue.size() == 1 && queue.take().equals("{\"text\":\"한글 보석\"}"), "UTF8/CRLF read");
        new DataReader(queue, new ByteArrayInputStream("incomplete".getBytes(StandardCharsets.UTF_8)), false).run();
        check(queue.isEmpty(), "unterminated record accepted");
        byte[] oversized = new byte[1024 * 1024 + 2];
        java.util.Arrays.fill(oversized, (byte)'a');
        oversized[oversized.length - 1] = '\n';
        new DataReader(queue, new ByteArrayInputStream(oversized), false).run();
        check(queue.isEmpty(), "oversized input accepted");
        new DataReader(queue, new ByteArrayInputStream(new byte[]{(byte)0xc3, 0x28, '\n'}), false).run();
        check(queue.isEmpty(), "malformed UTF8 accepted");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        queue.put("{\"text\":\"한글\"}");
        Thread writer = new Thread(new DataWriter(queue, bytes, false));
        writer.setDaemon(true);
        writer.start();
        long deadline = System.nanoTime() + 2000000000L;
        while (bytes.size() == 0 && System.nanoTime() < deadline) Thread.yield();
        writer.interrupt();
        writer.join(1000);
        check(!writer.isAlive(), "writer did not stop");
        check(new String(bytes.toByteArray(), StandardCharsets.UTF_8).equals("{\"text\":\"한글\"}\n"), "UTF8 write");
        queue.put("first");
        queue.put("second");
        Thread failedWriter = new Thread(new DataWriter(queue, new java.io.OutputStream() {
            public void write(int value) throws java.io.IOException { throw new java.io.IOException("expected test output failure"); }
        }, false));
        failedWriter.setDaemon(true);
        failedWriter.start();
        failedWriter.join(1000);
        if (failedWriter.isAlive()) { failedWriter.interrupt(); throw new AssertionError("failed writer kept retrying"); }
        check(queue.size() == 1, "writer continued after failed output");
        System.out.println("PASS: EOF exit, UTF8/CRLF, incomplete/oversized/malformed input, writer shutdown");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
