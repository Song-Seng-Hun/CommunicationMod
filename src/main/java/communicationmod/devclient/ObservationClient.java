package communicationmod.devclient;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Passive recording client. stdout is exclusively protocol JSONL, stderr is diagnostic. */
public final class ObservationClient {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("New recording directory required");
        Path output=Paths.get(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        try (BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
             OutputStream transcript=Files.newOutputStream(output.resolve("observations.jsonl"),StandardOpenOption.CREATE_NEW)) {
            System.out.println("{\"type\":\"hello\",\"protocol_version\":2}");
            System.out.flush();
            long bytes=0;
            String line;
            while((line=input.readLine())!=null) {
                if(line.length()>1024*1024)throw new IOException("Oversized observation");
                JsonObject message=new JsonParser().parse(line).getAsJsonObject();
                byte[] record=(line+"\n").getBytes(StandardCharsets.UTF_8);
                bytes+=record.length;
                if(bytes>64L*1024*1024)throw new IOException("Recording limit reached (64 MiB); human play is unaffected");
                transcript.write(record);transcript.flush();
                if("state".equals(message.get("type").getAsString())) {
                    Path temp=output.resolve("latest-state.json.tmp"), latest=output.resolve("latest-state.json");
                    Files.write(temp,record);
                    try {Files.move(temp,latest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                    catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,latest,StandardCopyOption.REPLACE_EXISTING);}
                }
            }
        }
    }
}
