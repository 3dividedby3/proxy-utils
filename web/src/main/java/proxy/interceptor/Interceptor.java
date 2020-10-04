package proxy.interceptor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import proxy.ProxyUtils;
import proxy.ReadInputStreamData;

public class Interceptor {
    
    private enum HttpMethod {
        POST
        ,GET;
    }
    
    public static final String REQ_PROP_CONNID = "ConnId";
    public static final String REQ_PROP_END_OF_STREAM = "EndOfStream";
    public static final String REQ_PROP_DESTINATION_HOST = "MediatorHost";
    public static final String REQ_PROP_DESTINATION_PORT = "MediatorPort";
    
    private static final int INTERCEPTED_TIMEOUT = 5 * 60_000;
    private static final int MEDIATOR_CONNECT_TIMEOUT = 5_000;
    private static final int MEDIATOR_READ_TIMEOUT = 30_000;
    private static final int POLL_TIMEOUT = 30_000;

    private int connId = 0;
    private ProxyUtils proxyUtils;

    private final int interceptorServerPort;
    private final String mediatorUrl;
    private final String destinationHost;
    private final int destinationPort;
    
    public Interceptor(int interceptorServerPort, String mediatorUrl, int destinationPort, String destinationHost) {
        proxyUtils = new ProxyUtils();
        this.interceptorServerPort = interceptorServerPort;
        this.mediatorUrl = mediatorUrl;
        this.destinationPort = destinationPort;
        this.destinationHost = destinationHost;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(interceptorServerPort);
        
        proxyUtils.logWithThreadName("Interceptor listening on port: " + interceptorServerPort);
        proxyUtils.logWithThreadName("Mediator URL: " + mediatorUrl);
        proxyUtils.logWithThreadName("Destination host: " + destinationHost + ", port: " + destinationPort);
        
        while (true) {
            proxyUtils.logWithThreadName("Waiting for new connection...");
            Socket interceptedSocket = serverSocket.accept();
            ++connId;

            new Thread() {
                public void run() {
                    try {
                        fireNewIntercepted(interceptedSocket, connId);
                    } catch (Exception e) {
                        try {
                            proxyUtils.logWithThreadName("Exception on new connection setup: " + e);
                            interceptedSocket.close();
                        } catch (IOException e1) {
                            proxyUtils.logWithThreadName("Cannot close intercepted socket: " + e1);
                        }
                    }
                }
            }.start();
        }
    }
    
    private void fireNewIntercepted(Socket interceptedSocket, int connId) throws Exception {
//        interceptedSocket.setTcpNoDelay(false);
        interceptedSocket.setKeepAlive(true);
        interceptedSocket.setSoTimeout(INTERCEPTED_TIMEOUT);
        proxyUtils.logWithThreadName("[" + connId + "] *** NEW CONNECTION ");
        
        InterceptedGroup interceptedGroup = new InterceptedGroup();
        
        proxyUtils.logWithThreadName("[" + connId + "] setting up thread to read from intercepted");
        new Thread("read_from_intercepted_" + connId) {
            public void run() {
                try {
                    while (true) {
                        ReadInputStreamData currentReadData = proxyUtils.readFromStream(interceptedSocket.getInputStream(), false);
                        if (currentReadData.getData().length > 0) {
    //                            proxyUtils.logWithThreadName("data from intercepted: " + new String(currentReadData.data, "UTF-8"));
                            interceptedGroup.getData().add(currentReadData.getData());
                        }
                        if (currentReadData.isEndOfStream()) {
                            proxyUtils.logWithThreadName("received endOfStream from intercepted");
                            closeInterceptedConnection(interceptedSocket, interceptedGroup);
                            return;
                        }
                    }
                } catch (IOException e) {
                    proxyUtils.logWithThreadName("stopping because of exception: " + e);
                    e.printStackTrace();
                    try {
                        closeInterceptedConnection(interceptedSocket, interceptedGroup);
                    } catch (IOException e2) {
                        proxyUtils.logWithThreadName("IOException while closing connection: " + e2);
                        e2.printStackTrace();
                    }
                }
            }
        }.start();
        proxyUtils.logWithThreadName("[" + connId + "] thread to read from intercepted UP and running");

        proxyUtils.logWithThreadName("[" + connId + "] setting up thread to write to mediator");
        new Thread("write_to_mediator_" + connId) {
            //this thread does not send endOfStream, only the read_mediator... thread does
            public void run() {
                try {
                    while (true) {
                        proxyUtils.logWithThreadName("waiting for data from interceptor");
                        byte[] dataToSendToMediator = interceptedGroup.getData().poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                        if (dataToSendToMediator == null) {
                            if (interceptedGroup.isEndOfStream()) {
                                proxyUtils.logWithThreadName("received endOfStream without data, stopping thread");
                                return;
                            }
                            proxyUtils.logWithThreadName("no data to send to mediator, continue");
                            continue;
                        }
                        proxyUtils.logWithThreadName("data found, writing to mediator");
                        HttpURLConnection mediatorConnection = createConnection(HttpMethod.POST);
                        mediatorConnection.addRequestProperty(REQ_PROP_CONNID, Integer.toString(connId));
                        mediatorConnection.addRequestProperty(REQ_PROP_DESTINATION_HOST, destinationHost);
                        mediatorConnection.addRequestProperty(REQ_PROP_DESTINATION_PORT, Integer.toString(destinationPort));
                        mediatorConnection.getOutputStream().write(dataToSendToMediator);
                        proxyUtils.logWithThreadName("done write to mediator");

                        //input stream needs to be closed for the data to be sent
                        mediatorConnection.getInputStream().close();
                        mediatorConnection.disconnect();
                        if (interceptedGroup.isEndOfStream()) {
                            proxyUtils.logWithThreadName("received endOfStream with data, stopping thread");
                            return;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    proxyUtils.logWithThreadName("stopping because of exception: " + e);
                    e.printStackTrace();
                    try {
                        closeInterceptedConnection(interceptedSocket, interceptedGroup);
                    } catch (IOException e2) {
                        proxyUtils.logWithThreadName("IOException while closing connection: " + e2);
                        e2.printStackTrace();
                    }
                }
            }
        }.start();
        proxyUtils.logWithThreadName("[" + connId + "] thread to write to mediator UP and running");
        
        proxyUtils.logWithThreadName("[" + connId + "] setting up thread to read from mediator and write to intercepted");
        new Thread("read_mediator_write_intercepted_" + connId) {
            public void run() {
                try {
                    while (true) {
                        proxyUtils.logWithThreadName("connecting to mediator");
                        HttpURLConnection mediatorConnection = createConnection(HttpMethod.GET);
                        mediatorConnection.addRequestProperty(REQ_PROP_CONNID, Integer.toString(connId));
                        if (interceptedGroup.isEndOfStream()) {
                            mediatorConnection.addRequestProperty(REQ_PROP_END_OF_STREAM, Boolean.TRUE.toString());
                        }
                        proxyUtils.logWithThreadName("reading data from mediator");
                        ReadInputStreamData currentReadData = proxyUtils.readFromStream(mediatorConnection.getInputStream(), true);
                        if (currentReadData.getData().length > 0) {
                            proxyUtils.logWithThreadName("writing data to intercepted");
                            interceptedSocket.getOutputStream().write(currentReadData.getData());
                        } else {
                            proxyUtils.logWithThreadName("no data from mediator");
                        }

                        if (interceptedGroup.isEndOfStream()) {
                            proxyUtils.logWithThreadName("received endOfStream, stopping thread");
                            return;
                        }
                        if (mediatorConnection.getHeaderField(REQ_PROP_END_OF_STREAM) != null) {
                            proxyUtils.logWithThreadName("received endOfStream from mediator, stopping thread");
                            interceptedGroup.setEndOfStream(true);
                            return;
                        }
                    }
                } catch (IOException e) {
                    proxyUtils.logWithThreadName("stopping because of exception: " + e);
                    e.printStackTrace();
                    try {
                        closeInterceptedConnection(interceptedSocket, interceptedGroup);
                    } catch (IOException e2) {
                        proxyUtils.logWithThreadName("IOException while closing connection: " + e2);
                        e2.printStackTrace();
                    }
                }
            }
        }.start();
        proxyUtils.logWithThreadName("[" + connId + "] thread to read from mediator and write to intercepted UP and running");
    }
    
    private HttpURLConnection createConnection(HttpMethod httpMethod) throws MalformedURLException, IOException, ProtocolException {
        URL url = new URL(mediatorUrl);
        HttpURLConnection mediatorConnection = (HttpURLConnection)url.openConnection();
        mediatorConnection.setConnectTimeout(MEDIATOR_CONNECT_TIMEOUT);
        mediatorConnection.setReadTimeout(MEDIATOR_READ_TIMEOUT);
        mediatorConnection.setDoOutput(true);
//                connection.setInstanceFollowRedirects(true);
        mediatorConnection.setRequestMethod(httpMethod.name());
        mediatorConnection.setRequestProperty("Content-Type", "application/octet-stream"); 
        mediatorConnection.setRequestProperty("charset", "UTF-8");
        
        return mediatorConnection;
    }

    private void closeInterceptedConnection(Socket interceptedSocket, InterceptedGroup interceptedGroup) throws IOException {
        proxyUtils.logWithThreadName("setting endOfStream true, closing intercepted socket");
        interceptedGroup.setEndOfStream(true);
        if (interceptedSocket.isClosed()) {
            return;
        }
        interceptedSocket.close();
    }
}