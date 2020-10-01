package proxy.mediator;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import proxy.ProxyUtils;
import proxy.ReadInputStreamData;

public class MediatorServlet extends HttpServlet {

    private static final long serialVersionUID = 4318888451462244958L;
    
    public static final String HEADER_CONNID = "connId";
    public static final String HEADER_END_OF_STREAM = "EndOfStream";
    public static final String HEADER_DESTINATION_HOST = "MediatorHost";
    public static final String HEADER_DESTINATION_PORT = "MediatorPort";
    
    private static final int TO_DESTINATION_TIMEOUT = 5 * 60_000;
    private static final int POLL_TIMEOUT = 10_000;

    //TODO: check if it can be replaced with WeakHashMap
    private static final Map<Integer, DestinationGroup> DESTINATION_GROUPS = Collections.synchronizedMap(new HashMap<>());
    
    private ProxyUtils proxyUtils = new ProxyUtils();
    
    public static void main(String[] args) throws Exception {
        Server server = new Server(7788);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        
        ServletHolder holderMediatorServlet = new ServletHolder(new MediatorServlet());
        handler.addServletWithMapping(holderMediatorServlet, "/proxy");
        
        server.start();
        server.join();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int connId = getConnIdFromHeader(request);
        proxyUtils.logWithThreadName("[" + connId +"] POST - NEW CONNECTION received; already existing connections: " + DESTINATION_GROUPS.size());
        proxyUtils.logWithThreadName("[" + connId +"] POST - reading all from interceptedInputStream");
        ReadInputStreamData dataFromIntercepted = proxyUtils.readFromStream(request.getInputStream());
        
        DestinationGroup destinationGroup = getDestinationGroup(connId
                , request.getHeader(HEADER_DESTINATION_HOST)
                , Integer.valueOf(request.getHeader(HEADER_DESTINATION_PORT)));
        
        proxyUtils.logWithThreadName("[" + connId +"] POST - writing all from interceptedInputStream to destinationOutputStream");
        destinationGroup.getSocket().getOutputStream().write(dataFromIntercepted.getData());
        
        proxyUtils.logWithThreadName("[" + connId +"] POST - done");
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int connId = getConnIdFromHeader(request);
        proxyUtils.logWithThreadName("[" + connId +"] GET - connId extracted");
        DestinationGroup destinationGroup = DESTINATION_GROUPS.get(connId);
        if (destinationGroup == null) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - no such connection, returning");
            return;
        }
        
        if (request.getHeader(HEADER_END_OF_STREAM) != null) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - received endOfStream from interceptor");
            destinationGroup.setEndOfStream(true);
            return;
        }

        proxyUtils.logWithThreadName("[" + connId +"] GET - waiting for data from destination, curently there are: " + destinationGroup.getData().size());
        byte[] dataToWrite = null;
        try {
            dataToWrite = destinationGroup.getData().poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - InterruptedException: " + e);
            e.printStackTrace();
        }
        if (destinationGroup.isEndOfStream()) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - destination endOfStream, removing connection");
            DESTINATION_GROUPS.remove(connId);
            response.setHeader(HEADER_END_OF_STREAM, Boolean.TRUE.toString());
        }
        if (dataToWrite == null) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - no data to write");
        } else {
//                proxyUtils.logWithThreadName("[" + connId +"] GET - writing to intercepted what we received: " + new String(dataToWrite, "UTF-8"));
            proxyUtils.logWithThreadName("[" + connId +"] GET - received data from destination, writing to intercepted");
            response.getOutputStream().write(dataToWrite);
        }
    }

    private Integer getConnIdFromHeader(HttpServletRequest request) {
        return Integer.valueOf(request.getHeader(HEADER_CONNID));
    }

    private DestinationGroup getDestinationGroup(int connId, String destinationHost, int destinationPort) throws IOException {
        DestinationGroup destinationGroupResponse;
        synchronized(DESTINATION_GROUPS) {
            destinationGroupResponse = DESTINATION_GROUPS.get(connId);
            if (destinationGroupResponse != null) {
                proxyUtils.logWithThreadName("[" + connId +"] DestinationGroup - found existing conn");
                return destinationGroupResponse;
            }
            proxyUtils.logWithThreadName("[" + connId +"] DestinationGroup - create new conn to host: " + destinationHost + ", port: " + destinationPort);
            destinationGroupResponse = new DestinationGroup();
            DESTINATION_GROUPS.put(connId, destinationGroupResponse);
        }
        
        Socket destinationSocket = new Socket(destinationHost, destinationPort);
        destinationSocket.setKeepAlive(true);
        destinationSocket.setSoTimeout(TO_DESTINATION_TIMEOUT);
        destinationGroupResponse.setSocket(destinationSocket);
        
        BlockingQueue<byte[]> destinationDataQueue = destinationGroupResponse.getData();
        DestinationGroup destinationGroupAux = destinationGroupResponse;
        
        Thread readFromDestination = new Thread("read_from_destination_" + connId){
            public void run() {
                while(true) {
                    try {
                        proxyUtils.logWithThreadName("waiting for data from destination");
                        ReadInputStreamData readInputStreamData = proxyUtils.readFromStream(destinationSocket.getInputStream());
                        if (destinationGroupAux.isEndOfStream()) {
                            proxyUtils.logWithThreadName("stopping thread because there is already endOfStream set to true");
                            return;
                        }
                        byte[] currentReadData = readInputStreamData.getData();
                        if (currentReadData.length > 0) {
                            proxyUtils.logWithThreadName("adding data from destination to the queue");
                            destinationDataQueue.add(currentReadData);
                        }
                        if (readInputStreamData.isEndOfStream()) {
                            proxyUtils.logWithThreadName("stopping thread because received endOfStream from destination");
                            destinationGroupAux.setEndOfStream(true);
                            return;
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        };
//          readFromDestination.setDaemon(true);
        readFromDestination.start();
        destinationGroupResponse.setReadFrom(readFromDestination);

        proxyUtils.logWithThreadName("[" + connId +"] conn created");

        return destinationGroupResponse;
    }
    
}