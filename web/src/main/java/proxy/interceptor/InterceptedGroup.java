package proxy.interceptor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class InterceptedGroup {
    
    private BlockingQueue<byte[]> data = new ArrayBlockingQueue<>(128);
    private volatile boolean endOfStream;
    
    /**
     * @return the data
     */
    public BlockingQueue<byte[]> getData() {
        return data;
    }
    /**
     * @param data the data to set
     */
    public void setData(BlockingQueue<byte[]> data) {
        this.data = data;
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
}
