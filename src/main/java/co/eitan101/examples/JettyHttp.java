/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package co.eitan101.examples;

import javax.websocket.server.ServerContainer;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.jetty.JettyHttpContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 *
 * @author handasa
 */
@Path("/")
public class JettyHttp {

    public static void main(String[] args) throws Exception {
        System.out.println("hello");
        Server server = JettyHttpContainerFactory.createServer(UriBuilder.fromUri("http://localhost/").port(9998).build(), 
                new ResourceConfig(JacksonFeature.class, JettyHttp.class));
        ServletContextHandler wsContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        wsContext.setContextPath("/data");
        server.setHandler(wsContext);
        ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(wsContext);
        wscontainer.addEndpoint(WsTest.class);

        server.start();
        System.in.read();
        server.stop();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public MyClass getIt() {
        return new MyClass(3, "testString");
    }

    public static class MyClass {

        int i;
        String j;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public String getJ() {
            return j;
        }

        public void setJ(String j) {
            this.j = j;
        }

        public MyClass() {
        }

        public MyClass(int i, String j) {
            this.i = i;
            this.j = j;
        }

    }

}
