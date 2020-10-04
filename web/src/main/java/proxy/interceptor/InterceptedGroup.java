package proxy.interceptor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class InterceptedGroup {
    
    private final BlockingQueue<byte[]> data;
    private final long created;
    private volatile boolean endOfStream;
    
    public InterceptedGroup() {
        data = new ArrayBlockingQueue<>(128);
        created = System.currentTimeMillis();
    }
    
    /**
     * @return the data
     */
    public BlockingQueue<byte[]> getData() {
        return data;
    }

    /**
     * @return the endOfStream
     */
    public boolean isEndOfStream() {
        return endOfStream;
    }
    /**
     * @param endOfStream the endOfStream to set
     */
    public void setEndOfStream(boolean endOfStream) {
        this.endOfStream = endOfStream;
    }

    public long getCreated() {
        return created;
    }
}
