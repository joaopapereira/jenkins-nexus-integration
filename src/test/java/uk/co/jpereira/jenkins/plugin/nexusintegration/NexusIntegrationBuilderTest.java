package uk.co.jpereira.jenkins.plugin.nexusintegration;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.header.InBoundHeaders;
import hudson.model.FreeStyleProject;
import java.util.*;

import hudson.util.Secret;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by blue on 13/04/2015.
 */
public class NexusIntegrationBuilderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void conditionalBuildersInMavenProjectMustBeResolvable() throws Exception {

        final FreeStyleProject p = j.createFreeStyleProject("f1");

        NexusIntegrationBuilder before = new NexusIntegrationBuilder("namespace", "com.group1",
                                            "artifactname", "v1", "zipfile");
        p.getBuildersList().add(before);


        //j.submit(j.createWebClient()
        //        .getPage(p, "configure")
        //        .getFormByName("config"));
        //NexusIntegrationBuilder after = p.getBuildersList().get(NexusIntegrationBuilder.class);
        //j.assertEqualBeans(before, after, "nexusNamespace,nexusGroupId,nexusArtifactId,nexusArtifactVersion,nexusArtifactPackaging,...");
        HtmlForm form = j.createWebClient()
                .getPage(p, "configure")
                .getFormByName("config");
        form.getInputByName("_.nexusRepository").setValueAttribute("bamm");
        form.getInputByName("_.nexusGroupId").setValueAttribute("g1");
        form.getInputByName("_.nexusArtifactId").setValueAttribute("artifact");
        form.getInputByName("_.nexusArtifactVersion").setValueAttribute("1.0.0");
        form.getInputByName("_.nexusArtifactPackaging").setValueAttribute("jar");
        j.submit(form);
        NexusIntegrationBuilder after = p.getBuildersList().get(NexusIntegrationBuilder.class);
        Assert.assertEquals("bamm", after.getNexusRepository());
        Assert.assertEquals("g1", after.getNexusGroupId());
        Assert.assertEquals("artifact", after.getNexusArtifactId());
        Assert.assertEquals("1.0.0", after.getNexusArtifactVersion());
        Assert.assertEquals("jar", after.getNexusArtifactPackaging());
    }
    @Test
    public void testMainConfiguration() throws Exception {

        final FreeStyleProject p = j.createFreeStyleProject("f1");

        NexusIntegrationBuilder nexusBuilder = new NexusIntegrationBuilder("namespace", "com.group1",
                "artifactname", "v1", "zipfile");
        p.getBuildersList().add(nexusBuilder);


        HtmlForm f = j.createWebClient()
                .goTo("configure")
                .getFormByName("config");
        f.getInputsByName("_.nexusUrl").get(0).setValueAttribute("http://bamm.home:20/nexus");
        f.getInputsByName("_.nexusUsername").get(0).setValueAttribute("username1");
        f.getInputsByName("_.nexusPassword").get(0).setValueAttribute("mypassword");
        j.submit(f);
        NexusIntegrationBuilder after = p.getBuildersList().get(NexusIntegrationBuilder.class);
        j.assertEqualBeans(nexusBuilder.getDescriptor(), after.getDescriptor(), "nexusUsername,nexusPassword,nexusUrl,anonymous");
        Assert.assertEquals("http://bamm.home:20/nexus", after.getDescriptor().getNexusUrl());
        Assert.assertEquals("username1", after.getDescriptor().getNexusUsername());
        Assert.assertEquals("mypassword", after.getDescriptor().getNexusPassword());

    }
    @Test
    public void testWeb() throws Exception {

        //final FreeStyleProject p = j.createFreeStyleProject("f1");

        //NexusIntegrationBuilder nexusBuilder = new NexusIntegrationBuilder("namespace", "com.group1",
        //        "artifactname", "v1", "zipfile");

        NexusIntegrationBuilder nexusBuilder = mock(NexusIntegrationBuilder.class);
        InBoundHeaders h = new InBoundHeaders();
        List<String> l = new ArrayList<String>();
        l.add("pico");
        h.put("Content-Disposition", l);
        ClientResponse response = new ClientResponse(200, h, null, null);
        response.setStatus(200);
        WebResource webResource = mock(WebResource.class);
        when(webResource.get(ClientResponse.class)).thenReturn(response);

        //when(nexusBuilder.getService("http://bamm:80", "username", Secret.fromString("password"), false)).thenReturn(webResource);


        //Assert.assertEquals("username1", after.getDescriptor().getNexusUsername());
        //Assert.assertEquals("mypassword", after.getDescriptor().getNexusPassword());

    }
}
