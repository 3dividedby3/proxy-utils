package proxy.interceptor;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("used for local testing")
public class InterceptorTest {

    private int interceptorServerPort = 443;
//    private String mediatorUrl = "http://localhost:7788/proxy";
//    private String mediatorUrl = "https://imaage.herokuapp.com/proxy";
    private String mediatorUrl = "http://localhost:8804/proxy";
    
//  private String destinationHost = "216.58.214.238"; //www.youtube.com
//    private String destinationHost = "93.184.216.34"; //www.example.org
    private final String destinationHost = "172.217.20.4"; //www.google.com
//    private final String destinationHost = "seemyip.com";
//    private final String destinationHost = "www.google.com"; //www.google.com
    private final int destinationPort = 443;
    
    @Test
    public void testRunning() throws Exception {
        new Interceptor(interceptorServerPort, mediatorUrl, destinationPort, destinationHost).start();
    }
    
}