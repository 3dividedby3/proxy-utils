package proxy;

public class ReadInputStreamData {
    
    private byte[] data;
    private boolean endOfStream;
    
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
    /**
     * @return the data
     */
    public byte[] getData() {
        return data;
    }
    /**
     * @param data the data to set
     */
    public void setData(byte[] data) {
        this.data = data;
    }
}