package proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class ProxyUtils {

    private static final int DEFAULT_READ_BYTES_LENGTH = 5_000;
    
    public ReadInputStreamData readFromStream(final InputStream inputStream) {
        ReadInputStreamData response = new ReadInputStreamData();
        response.setEndOfStream(false);
        int readInt = 0;
        ArrayList<Byte> allBytes = new ArrayList<>(DEFAULT_READ_BYTES_LENGTH);

        try {
            while (true) {
                readInt = inputStream.read();
                // -1 comes from the JVM to notify the end of stream
                if (readInt == -1) {
                    logWithThreadName("inpustream - has received end of stream: " + readInt);
                    response.setEndOfStream(true);
                    break;
                }
                allBytes.add((byte) readInt);
                if (inputStream.available() == 0) {
                    logWithThreadName("inputstream - no more data available");
                    break;
                }
            }
        } catch (IOException e) {
            response.setEndOfStream(true);
            logWithThreadName("inpustream - exception(treating as end of stream): " + e);
            e.printStackTrace();
        }
        logWithThreadName("inpustream - reading data has finished");
        
        byte[] finalBytes = new byte[allBytes.size()];
        for (int i = 0; i < finalBytes.length; ++i) {
            finalBytes[i] = allBytes.get(i);
        }
//        logWithThreadName("Read data: " + new String(finalBytes, "UTF-8"));
        logWithThreadName("inpustream - Read data length: " + finalBytes.length);
        response.setData(finalBytes);
        
        return response;
    }
    
    public void logWithThreadName(String toLog) {
        System.out.println(Thread.currentThread().getName() + " *** " + toLog);
    }
}
