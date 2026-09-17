package communicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

public class DataReader implements Runnable{

    private static final int MAX_LINE_BYTES = 1024 * 1024;

    private final BlockingQueue<String> queue;
    private final InputStream stream;
    private static final Logger logger = LogManager.getLogger(DataReader.class.getName());
    private boolean verbose;

    public DataReader (BlockingQueue<String> queue, InputStream stream, boolean verbose) {
        this.queue = queue;
        this.stream = stream;
        this.verbose = verbose;
    }

    public void run() {
        try (BufferedInputStream input = new BufferedInputStream(stream)) {
            while (!Thread.currentThread().isInterrupted()) {
                String line = readLine(input);
                if (line == null) return;
                if (!line.isEmpty()) {
                    if (verbose) logger.info("Received message: " + line);
                    queue.put(line);
                }
            }
        } catch (IOException e) {
            logger.error("Transport input closed or invalid; reading stopped.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (!Thread.currentThread().isInterrupted()) {
            int next = input.read();
            if (next == -1) {
                if (bytes.size() != 0) throw new IOException("Unterminated JSON-line record");
                return null;
            }
            if (next == '\n') {
                byte[] data = bytes.toByteArray();
                int length = data.length;
                if (length > 0 && data[length - 1] == '\r') length--;
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, 0, length)).toString();
            }
            if (next == 0 || bytes.size() >= MAX_LINE_BYTES) throw new IOException("Invalid or oversized JSON-line record");
            bytes.write(next);
        }
        return null;
    }
}
