package proxy.mediator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("used for local testing")
public class MediatorServletTest {

    @Test
    public void testRunning() throws Exception {
        Server server = new Server(7788);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        
        ServletHolder holderMediatorServlet = new ServletHolder(new MediatorServlet());
        handler.addServletWithMapping(holderMediatorServlet, "/proxy");
        
        server.start();
        server.join();
    }
}