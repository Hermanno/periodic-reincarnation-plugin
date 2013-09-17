package org.jenkinsci.plugins.periodicreincarnation;

import java.io.IOException;

import org.jenkinsci.plugins.periodicreincarnation.PeriodicReincarnationGlobalConfiguration.RegEx;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.listeners.RunListener;
import static hudson.model.Result.SUCCESS;

/**
 * This class triggers a restart automatically after a build has failed.
 * 
 * @author yboev
 * 
 */
@Extension
public class AfterbuildReincarnation extends RunListener<AbstractBuild<?, ?>> {

    /**
     * Maximal times a project can be automatically restarted from this class in
     * a row.
     * 
     */
    private int maxRestartDepth;

    /**
     * Tells if this type of restart is enabled.
     */
    private boolean isEnabled = false;
    private boolean isLocallyEnabled = false;

    @Override
    public void onCompleted(AbstractBuild<?, ?> build, TaskListener listener) {

        // stop if no build or project can be retrieved
        if (build == null || build.getProject() == null
                || !(build.getProject() instanceof Project<?, ?>)) {
            return;
        }

        // stop if build was a success
        if (build.getResult() == SUCCESS) {
            return;
        }

        final JobLocalConfiguration localConfig = build.getProject()
                .getProperty(JobLocalConfiguration.class);
        final PeriodicReincarnationGlobalConfiguration globalConfig = PeriodicReincarnationGlobalConfiguration
                .get();
        // stop if there is no global configuration
        if (globalConfig == null) {
            return;
        }
        setConfigVariables(localConfig, globalConfig);

        // stop if not enabled
        if (!this.isEnabled) {
            return;
        }

        if (!this.isLocallyEnabled) {
            // try to restart the project by finding a matching regEx or restart
            // it because of an unchanged configuration
            regExRestart(build, globalConfig);
            noChangeRestart(build, globalConfig);
        } else {
            // restart project for which afterbuild restart has been enabled
            // locally
            localRestart(build);
        }
    }

    private void localRestart(AbstractBuild<?, ?> build) {
        if (build.getProject() instanceof Project<?, ?>
                && checkRestartDepth(build)) {
            Utils.restart((Project<?, ?>) build.getProject(),
                    "(Afterbuild restart) Locally configured project.", null);
        }
    }

    /**
     * Checks if we can restart the project for unchanged project configuration.
     * 
     * @param build
     *            the build
     * @param config
     *            the periodic reincarnation configuration
     */
    private void noChangeRestart(AbstractBuild<?, ?> build,
            PeriodicReincarnationGlobalConfiguration config) {
        if (build.getProject() instanceof Project<?, ?>
                && config.isRestartUnchangedJobsEnabled()
                && Utils.qualifyForUnchangedRestart((Project<?, ?>) build
                        .getProject()) && checkRestartDepth(build)) {
            Utils.restart((Project<?, ?>) build.getProject(),

            "(Afterbuild restart) No difference between last two builds", null);
        }
    }

    /**
     * Checks if we can restart the project for a RegEx hit.
     * 
     * @param build
     *            the build
     * @param config
     *            the periodic reincarnation configuration
     */
    private void regExRestart(AbstractBuild<?, ?> build,
            PeriodicReincarnationGlobalConfiguration config) {
        final RegEx regEx = Utils.checkBuild(build);
        if (regEx != null && checkRestartDepth(build)
                && build.getProject() instanceof Project<?, ?>) {
            Utils.restart((Project<?, ?>) build.getProject(),
                    "(Afterbuild restart) RegEx hit in console output: "
                            + regEx.getValue(), regEx);
        }
    }

    /**
     * Retrieves values from global or local config.
     * 
     * @param localconfig
     *            localconfiguration
     * @param config
     *            globalconfiguration
     */
    private void setConfigVariables(JobLocalConfiguration localconfig,
            PeriodicReincarnationGlobalConfiguration config) {
        if (localconfig != null && localconfig.getIsLocallyConfigured()) {
            this.isEnabled = localconfig.getIsEnabled();
            this.isLocallyEnabled = localconfig.getIsEnabled();
            this.maxRestartDepth = localconfig.getMaxDepth();
        } else {
            this.isEnabled = config.isTriggerActive();
            this.maxRestartDepth = config.getMaxDepth();
        }
    }

    /**
     * Checks the restart depth for the current project.
     * 
     * @param build
     *            the current build
     * @return true if check has passed, false otherwise
     */
    private boolean checkRestartDepth(AbstractBuild<?, ?> build) {
        if (this.maxRestartDepth <= 0) {
            return true;
        }
        int count = 0;

        // count the number of restarts for the current project
        while (build != null
                && build.getCause(PeriodicReincarnationBuildCause.class) != null) {
            if (build.getCause(PeriodicReincarnationBuildCause.class)
                    .getShortDescription().contains("Afterbuild")) {
                count++;
            }
            if (count > this.maxRestartDepth) {
                return false;
            }
            build = build.getPreviousBuild();
        }
        return true;
    }
}
