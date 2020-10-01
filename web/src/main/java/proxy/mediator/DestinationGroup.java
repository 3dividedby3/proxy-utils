package proxy.mediator;

import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DestinationGroup {

    private Socket socket;
    private BlockingQueue<byte[]> data = new ArrayBlockingQueue<>(32);
    private Thread readFrom;
    private volatile boolean endOfStream;

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
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }
    /**
     * @param socket the socket to set
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
    }
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
     * @return the readFrom
     */
    public Thread getReadFrom() {
        return readFrom;
    }
    /**
     * @param readFrom the readFrom to set
     */
    public void setReadFrom(Thread readFrom) {
        this.readFrom = readFrom;
    }
}
