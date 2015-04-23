package uk.co.jpereira.jenkins.plugin.nexusintegration;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import javax.servlet.ServletException;
import javax.ws.rs.core.MediaType;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.Base64;

import javax.servlet.ServletException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link NexusIntegrationBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class NexusIntegrationBuilder extends Builder {
    private static Logger log = Logger.getLogger( NexusIntegrationBuilder.class.getSimpleName() );

    private final String repository;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String packaging;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public NexusIntegrationBuilder(String nexusRepository, String nexusGroupId,
                                   String nexusArtifactId, String nexusArtifactVersion, String nexusArtifactPackaging) {

        this.repository = nexusRepository;
        this.groupId = nexusGroupId;
        this.artifactId = nexusArtifactId;
        if(nexusArtifactVersion.length() == 0){
            version = "LATEST";
        }else{
            version = nexusArtifactVersion;
        }
        this.packaging = nexusArtifactPackaging;
    }

    public String getNexusRepository() {
        return repository;
    }
    public String getNexusGroupId() {
        return groupId;
    }
    public String getNexusArtifactId() {
        return artifactId;
    }
    public String getNexusArtifactVersion() {
        return version;
    }
    public String getNexusArtifactPackaging() {
        return packaging;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        final String url = getDescriptor().getNexusUrl();
        final String user = getDescriptor().getNexusUsername();
        final Secret password = Secret.fromString(getDescriptor().getNexusPassword());
        final boolean anonymous = getDescriptor().isAnonymous();

        if( ! validatePluginConfiguration(url, user, Secret.toString( password ), anonymous) ) {
            listener.getLogger().println("Please configure Nexus Plugin("+url+", "+user+", +"+Secret.toString( password )+")");
            return false;
        }

        WebResource service = getService(url, user, password, anonymous);

        listener.getLogger().println("GET Nexus Version");

        String artefactSearch = "maven?r=releases&g=uk.co.jpereira.jenkins.plugin&a=nexus-integration&v=LATEST&p=hpi";
        String version = getNexusArtifactVersion();
        if(build.getBuildVariables().containsKey(getNexusGroupId() + "." + getNexusArtifactId()+".version")){
            version = (String)build.getBuildVariables().get(getNexusGroupId() + "." + getNexusArtifactId()+".version");
            listener.getLogger().println("Using specific build version: " + version);
        }else{
            listener.getLogger().println("Using default version: " + version);
        }
        WebResource request = service.path("service").path("local").path("artifact")
                                    .path("maven")
                                    .path("content")
                .queryParam("r", getNexusRepository())
                .queryParam("g", getNexusGroupId())
                .queryParam("a", getNexusArtifactId())
                .queryParam("v", version)
                .queryParam("p", getNexusArtifactPackaging());

        ClientResponse response = request.get(ClientResponse.class);
        if(response.getStatus() != 200){

            listener.getLogger().println("Using URI:" + request.getURI());
            listener.getLogger().println("Error retrieving the artifact!");
            listener.getLogger().println(response.getEntity(String.class));
            return false;
        }
        InputStream entityInputStream = response.getEntityInputStream();
        String contentDisp = response.getHeaders().getFirst("Content-Disposition");
        String filename = null;
        if(contentDisp == null){
            listener.getLogger().println("Filename header not present using default name 'out.jar'");
            filename = "out.jar";
        }else{
            listener.getLogger().println("Header Content-Disposition='" + contentDisp + "'");
            Pattern regex = Pattern.compile("filename=\"(.*?)\"");
            Matcher regexMatcher = regex.matcher(contentDisp);
            if (regexMatcher.find()) {
                filename = regexMatcher.group(1);
                listener.getLogger().println("Found regex: " + filename);
            }else{
                listener.getLogger().println("Format not correct using default name 'out.jar'");
                filename = "out.jar";
            }

        }
        FileChannel out = null;
        try {
            listener.getLogger().println("Creating file at: " + build.getWorkspace() + "/" + filename);
            out = new FileOutputStream(build.getWorkspace() + "/" + filename).getChannel();
            ReadableByteChannel in = Channels.newChannel(entityInputStream);
            out.transferFrom(in, 0, Long.MAX_VALUE);
            out.close();
            listener.getLogger().println("File size is: " + out.size());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        listener.getLogger().println("The out put was: " + response);



        return true;
    }
    public static WebResource getService(final String url, final String user,
                                          final Secret password,
                                          final boolean anonymous) {
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);
        if(!anonymous) {
            client.addFilter(new HTTPBasicAuthFilter(user, Secret.toString(password)));
        }
        WebResource service = client.resource( url );
        return service;
    }

    private boolean validatePluginConfiguration(
            final String url,
            final String user,
            final String password,
            final boolean anonymous) {

        if( url == null || user == null || password == null ||
                url.isEmpty() || (( user.isEmpty() || password.isEmpty()) && !anonymous )) {
            return false;
        }
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link NexusIntegrationBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/NexusIntegrationBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String nexusUrl;
        private boolean anonymous;
        private String nexusUsername;
        private String nexusPassword;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'key'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckKey(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a key");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the key too short?");
            return FormValidation.ok();
        }
        public FormValidation doCheckValue(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a value");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the key too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Nexus Integration";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            //nexusUrl = formData.getString("nexusUrl");
            //nexusUsername = formData.getString("nexusUsername");
            //nexusPassword = Secret.fromString(formData.getString("nexusPassword"));
            //anonymous = formData.getBoolean("anonymous");
            req.bindJSON(this, formData.getJSONObject("nexus"));
            //nexusPassword = formData.getJSONObject("nexus").getString("nexusPassword");
            //req.bindJSON(this, formData);
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }
        /**
         *  Nexus connection test
         */
        public FormValidation doTestConnection(
                @QueryParameter("nexusUrl") final String nexusUrl,
                @QueryParameter("anonymous") final boolean anonymous,
                @QueryParameter("nexusUsername") final String nexusUsername,
                @QueryParameter("nexusPassword") final String nexusPassword) throws IOException, ServletException {

            try {
                WebResource service = getService(nexusUrl, nexusUsername, Secret.fromString( nexusPassword ), anonymous );
                ClientResponse nexusStatus = service.path("service").path("local").path("status").accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
                if( nexusStatus.getStatus() == 200 ) {
                    return FormValidation.ok("Success. Connection with Nexus Repository verified.");
                }
                return FormValidation.error("Failed. Please check the configuration. HTTP Status: " + nexusStatus);
            } catch (Exception e) {
                System.out.println("Exception " + e.getMessage() );
                return FormValidation.error("Client error : " + e.getMessage());
            }
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public String getNexusUrl() {
            return nexusUrl;
        }
        public void setNexusUrl(String nexusUrl) {
            this.nexusUrl = nexusUrl;
        }
        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public String getNexusUsername() {
            return nexusUsername;
        }
        public void setNexusUsername(String nexusUsername) {
            this.nexusUsername = nexusUsername;
        }
        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public String getNexusPassword() {
            return nexusPassword;
        }
        public void setNexusPassword(String nexusPassword) {
            this.nexusPassword = nexusPassword;
        }
        public boolean isAnonymous(){return anonymous;}
        public void setAnonymous(boolean anonymous){this.anonymous = anonymous;}
    }
}

