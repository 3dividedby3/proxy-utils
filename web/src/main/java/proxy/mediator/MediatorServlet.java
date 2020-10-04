package proxy.mediator;

import static proxy.interceptor.Interceptor.REQ_PROP_CONNID;
import static proxy.interceptor.Interceptor.REQ_PROP_DESTINATION_HOST;
import static proxy.interceptor.Interceptor.REQ_PROP_DESTINATION_PORT;
import static proxy.interceptor.Interceptor.REQ_PROP_END_OF_STREAM;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import proxy.ProxyUtils;
import proxy.ReadInputStreamData;

public class MediatorServlet extends HttpServlet {

    private static final long serialVersionUID = 4418888451462244958L;
    
    private static final int TO_DESTINATION_TIMEOUT = 5 * 60_000;
    private static final int POLL_TIMEOUT = 10_000;

    //TODO: check if it can be replaced with WeakHashMap
    private static final Map<Integer, DestinationGroup> DESTINATION_GROUPS = Collections.synchronizedMap(new HashMap<>());
    
    private ProxyUtils proxyUtils;
    
    public MediatorServlet() {
        proxyUtils = new ProxyUtils();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int connId = getConnIdFromHeader(request);
        proxyUtils.logWithThreadName("[" + connId +"] POST - new data from intercepted; connections currently on: " + DESTINATION_GROUPS.size());
        proxyUtils.logWithThreadName("[" + connId +"] POST - reading all from interceptedInputStream");
        ReadInputStreamData dataFromIntercepted = proxyUtils.readFromStream(request.getInputStream(), true);
        
        DestinationGroup destinationGroup = getOrCreateDestinationGroup(connId
                , request.getHeader(REQ_PROP_DESTINATION_HOST)
                , Integer.valueOf(request.getHeader(REQ_PROP_DESTINATION_PORT)));
        
        proxyUtils.logWithThreadName("[" + connId +"] POST - writing all from interceptedInputStream to destinationOutputStream");
        Socket destinationsocket = destinationGroup.getSocket();
        OutputStream destinationOutputStream = destinationsocket.getOutputStream();
        destinationOutputStream.write(dataFromIntercepted.getData());
        
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
        
        if (request.getHeader(REQ_PROP_END_OF_STREAM) != null) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - received endOfStream from interceptor");
            destinationGroup.setEndOfStream(true);
            return;
        }

        proxyUtils.logWithThreadName("[" + connId +"] GET - waiting for data from destination, available data size: " + destinationGroup.getData().size());
        byte[] dataToWrite = null;
        try {
            dataToWrite = destinationGroup.getData().poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - InterruptedException: " + e);
            e.printStackTrace();
        }
        if (dataToWrite == null) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - no data to write");
        } else {
//                proxyUtils.logWithThreadName("[" + connId +"] GET - writing to intercepted what we received: " + new String(dataToWrite, "UTF-8"));
            proxyUtils.logWithThreadName("[" + connId +"] GET - received data from destination, writing to intercepted");
            response.getOutputStream().write(dataToWrite);
        }
        if (destinationGroup.isEndOfStream()) {
            proxyUtils.logWithThreadName("[" + connId +"] GET - destination endOfStream, removing connection");
            DESTINATION_GROUPS.remove(connId);
            response.setHeader(REQ_PROP_END_OF_STREAM, Boolean.TRUE.toString());
        }
    }

    private Integer getConnIdFromHeader(HttpServletRequest request) {
        return Integer.valueOf(request.getHeader(REQ_PROP_CONNID));
    }

    private DestinationGroup getOrCreateDestinationGroup(int connId, String destinationHost, int destinationPort) throws IOException {
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
        
            Socket destinationSocket = new Socket(destinationHost, destinationPort);
            destinationSocket.setKeepAlive(true);
            destinationSocket.setSoTimeout(TO_DESTINATION_TIMEOUT);
            destinationGroupResponse.setSocket(destinationSocket);    
        }
        
        Thread readFromDestination = new Thread("read_from_destination_" + connId){
            public void run() {
                while(true) {
                    DestinationGroup destinationGroup = DESTINATION_GROUPS.get(connId);
                    if (destinationGroup == null) {
                        proxyUtils.logWithThreadName("connection has been closed, so closing thread too");
                        return;
                    }
                    if (destinationGroup.isEndOfStream()) {
                        proxyUtils.logWithThreadName("stopping thread because endOfStream is true");
                        return;
                    }
                    ReadInputStreamData readInputStreamData = null;
                    try {
                        proxyUtils.logWithThreadName("waiting for data from destination");
                        readInputStreamData = proxyUtils.readFromStream(destinationGroup.getSocket().getInputStream(), false);
                    } catch (IOException e) {
                        proxyUtils.logWithThreadName("Exception while reading data, setting endOfStream and stopping: " + e);
                        e.printStackTrace();
                        destinationGroup.setEndOfStream(true);
                        return;
                    }

                    byte[] currentReadData = readInputStreamData.getData();
                    if (currentReadData.length > 0) {
                        proxyUtils.logWithThreadName("adding data from destination to the queue");
                        destinationGroup.getData().add(currentReadData);
                    }
                    if (readInputStreamData.isEndOfStream()) {
                        proxyUtils.logWithThreadName("stopping thread because received endOfStream from destination");
                        destinationGroup.setEndOfStream(true);
                        return;
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