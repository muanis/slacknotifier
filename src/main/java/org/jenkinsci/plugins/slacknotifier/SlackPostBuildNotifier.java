package org.jenkinsci.plugins.slacknotifier;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import hudson.tasks.*;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.slacknotifier.util.SlackPoster;
import org.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created 3/17/15 9:24 PM
 * Copyright(c) The New York Times
 */
public class SlackPostBuildNotifier extends Notifier {


    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        String endpoint = ((SlackBuildStepNotifier.DescriptorImpl)Jenkins.getInstance().getDescriptor(SlackBuildStepNotifier.class)).getWebHookEndpoint();
        String jenkinsServer = ((SlackBuildStepNotifier.DescriptorImpl)Jenkins.getInstance().getDescriptor(SlackBuildStepNotifier.class)).getJenkinsServerUrl();



        org.json.JSONObject json = new org.json.JSONObject();
        json.put("channel", channel);
        json.put("username", "Jenkins");
        String summary = build.getProject().getFullDisplayName() + " " + getVersion(build, listener) + " - " + build.getDisplayName() + " " + getStatusMessage(build) + " after " + build.getDurationString();
        json.put("text", summary); // "Config Management v.1.1.7 b 1209 success after 54 sec");
        json.put("icon_url", "https://wiki.jenkins-ci.org/download/attachments/2916393/headshot.png?version=1&modificationDate=1302753947000");
        json.put("link_names", 1);


        JSONArray mrkdown_in = new JSONArray();
        mrkdown_in.put("text");

        org.json.JSONObject jenkins = new org.json.JSONObject();
        org.json.JSONObject github = new org.json.JSONObject();
        org.json.JSONObject message = new org.json.JSONObject();
        jenkins.put("fallback", "not printable");
        github.put("fallback", "not printable");
        message.put("fallback", "not printable");
        if(build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            jenkins.put("color", "#36a64f");
            github.put("color", "#36a64f");
            message.put("color", "#36a64f");
//            jenkins.put("pretext", "Optional text");
            jenkins.put("title", "Jenkins Build Successfull");
        } else {
            jenkins.put("color", "#CD0000");
            github.put("color", "#CD0000");
            message.put("color", "#CD0000");
            jenkins.put("title", "Jenkins Build Failed");
        }
        jenkins.put("title_link", jenkinsServer + build.getUrl());
        jenkins.put("text", getCommitList(build));
        jenkins.put("mrkdwn_in", mrkdown_in);

        github.put("title","Github Diff");
        github.put("title_link", getGithubLink(build));

        message.put("text", this.escape(this.message));
        message.put("mrkdwn_in", mrkdown_in);

//        github.put("text", getGithubLink(build) + "\n" + getGithubLinkLastSuccess(build));
//        github.put("mrkdwn_in", mrkdown_in);

        JSONArray attachments = new JSONArray();
        attachments.put(jenkins);
        attachments.put(github);
        attachments.put(message);

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


    private String getVersion(AbstractBuild<?, ?> build, BuildListener listener) {
        try {
        File versionFile = new File(build.getWorkspace().absolutize() + File.separator + ".version");
            String version = readFile(versionFile.getAbsolutePath());
            return version.trim();
        } catch(Exception e) {
            listener.getLogger().append("error reading version file: " + e.getMessage());
            return "";
        }

    }

    String readFile(String fileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    private String getGithubLinkLastSuccess(AbstractBuild<?,?> build) {
        AbstractBuild<?,?> lastBuild = build.getProject().getLastSuccessfulBuild();
        BuildData data = lastBuild.getAction(BuildData.class);

        if(data == null) {

            return null;
        }

        GitRepositoryBrowser repoBrowser = (GitRepositoryBrowser)lastBuild.getProject().getScm().getBrowser();
        return repoBrowser.getRepoUrl() + "/commit/" + data.getLastBuiltRevision().getSha1String();

    }

    private String getGithubLink(AbstractBuild r) {

        BuildData data = r.getAction(BuildData.class);

        if(data == null) {

            return null;
        }

        GitRepositoryBrowser repoBrowser = (GitRepositoryBrowser)r.getProject().getScm().getBrowser();
        return repoBrowser.getRepoUrl() + "/commit/" + data.getLastBuiltRevision().getSha1String();
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

    String getCommitList(AbstractBuild r) {
        if (!r.hasChangeSetComputed()) {
            return "No Changes.";
        }
        ChangeLogSet changeSet = r.getChangeSet();
        List<ChangeLogSet.Entry> entries = new LinkedList<ChangeLogSet.Entry>();
        for (Object o : changeSet.getItems()) {
            ChangeLogSet.Entry entry = (ChangeLogSet.Entry) o;
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            return "No Changes.";
        }
        Set<String> commits = new HashSet<String>();
        for (ChangeLogSet.Entry entry : entries) {
            StringBuffer commit = new StringBuffer();
            commit.append(entry.getMsg());
            commit.append(" [").append(entry.getAuthor().getDisplayName()).append("]");
            commits.add(commit.toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Changes:\n- ");
        sb.append(StringUtils.join(commits, "\n- "));
        return sb.toString();
    }

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
    public SlackPostBuildNotifier(String channel, String message) {
        this.channel = channel;
        this.message = message;
    }


    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }


    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {


        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link SlackPostBuildNotifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/jenkinsci/plugins/stepbuildinfo/SlackBuildStepNotifier/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Slack Post Build Notifier";
        }

    }

}
