package org.jenkinsci.plugins.slacknotifier;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.util.FormValidation;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.slacknotifier.util.SlackPoster;
import org.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link SlackBuildStepNotifier} is created. The created
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
public class SlackBuildStepNotifier extends Builder {

    private final String channel;
    private final String message;

    public String getChannel() {
        return channel;
    }

    public String getMessage() {
        return message;
    }

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public SlackBuildStepNotifier(String channel, String message) {
        this.channel = channel;
        this.message = message;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

        String endpoint = ((SlackBuildStepNotifier.DescriptorImpl)Jenkins.getInstance().getDescriptor(SlackBuildStepNotifier.class)).getWebHookEndpoint();
        String jenkinsServer = ((SlackBuildStepNotifier.DescriptorImpl)Jenkins.getInstance().getDescriptor(SlackBuildStepNotifier.class)).getJenkinsServerUrl();

        org.json.JSONObject json = new org.json.JSONObject();
        json.put("channel", channel);
        json.put("username", "Jenkins");
        String summary = build.getProject().getFullDisplayName() + " - " + build.getDisplayName() + " build " + getStatusMessage(build).toLowerCase();
        json.put("text", summary); // "Config Management v.1.1.7 b 1209 success after 54 sec");
        json.put("icon_url", "https://wiki.jenkins-ci.org/download/attachments/2916393/headshot.png?version=1&modificationDate=1302753947000");
        json.put("link_names", 1);


        JSONArray mrkdown_in = new JSONArray();
        mrkdown_in.put("text");

        org.json.JSONObject jenkins = new org.json.JSONObject();
        jenkins.put("fallback", "not printable");
        jenkins.put("color", "#c2c2d6");
        jenkins.put("title", "Jenkins " );
        jenkins.put("title_link", jenkinsServer + build.getUrl());
        jenkins.put("text", this.escape(message));
        jenkins.put("mrkdwn_in", mrkdown_in);

        JSONArray attachments = new JSONArray();
        attachments.put(jenkins);

        json.put("attachments",attachments);


        new SlackPoster(endpoint).publish(json);
        listener.getLogger().printf("message posted to slack");
        return true;


    }
    public String escape(String string) {
        string = string.replace("&", "&amp;");
        string = string.replace("<", "&lt;");
        string = string.replace(">", "&gt;");

        return string;
    }

    static String getStatusMessage(AbstractBuild r) {
        if (r.isBuilding()) {
            return "Starting...";
        }
        Result result = r.getResult();
        Run previousBuild = r.getProject().getLastBuild().getPreviousBuild();
        Result previousResult = (previousBuild != null) ? previousBuild.getResult() : Result.SUCCESS;
        if (result == Result.SUCCESS && previousResult == Result.FAILURE) {
            return "Back to normal";
        }
        if (result == Result.SUCCESS) {
            return "Success";
        }
        if (result == Result.FAILURE) {
            return "Failure";
        }
        if (result == Result.ABORTED) {
            return "Aborted";
        }
        if (result == Result.NOT_BUILT) {
            return "Not built";
        }
        if (result == Result.UNSTABLE) {
            return "Unstable";
        }
        return "Unknown";
    }


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link SlackBuildStepNotifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/jenkinsci/plugins/stepbuildinfo/SlackBuildStepNotifier/*.jelly</tt>
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
        private String webHookEndpoint;
        private String jenkinsServerUrl;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
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
            return "Slack Build Step Notifier";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            webHookEndpoint = formData.getString("webHookEndpoint");
            jenkinsServerUrl = formData.getString("jenkinsServerUrl");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public String getWebHookEndpoint() {
            return webHookEndpoint;
        }

        public String getJenkinsServerUrl() {
            return jenkinsServerUrl;
        }
    }
}

