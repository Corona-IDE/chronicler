/*
 * Copyright (c) Sep 26, 2018 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.chronicler.diff.analyzer;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.calamari.core.auth.ApplicationKey;
import org.starchartlabs.calamari.core.auth.InstallationAccessToken;
import org.starchartlabs.chronicler.calamari.core.paging.PageReader;
import org.starchartlabs.chronicler.diff.analyzer.exception.InvalidConfigurationException;
import org.starchartlabs.chronicler.events.GitHubPullRequestEvent;
import org.starchartlabs.chronicler.github.model.Requests;
import org.starchartlabs.chronicler.github.model.pullrequest.StatusHandler;

import com.google.gson.JsonElement;

import okhttp3.HttpUrl;

//https://developer.github.com/v3/pulls/#list-pull-requests-files
public class PullRequestAnalyzer {

    private static final ResourceBundle MESSAGES = ResourceBundle
            .getBundle("org.starchartlabs.chronicler.diff.analyzer.messages");

    private static final String PENDING_MESSAGE_KEY = "status.pending";

    private static final String CONFIGURATION_ERROR_MESSAGE_KEY = "status.error.configuration";

    private static final String PROCESSING_ERROR_MESSAGE_KEY = "status.error.processing";

    private static final String SUCCESS_UPDATED_MESSAGE_KEY = "status.success.updated";

    private static final String SUCCESS_UNNEEDED_MESSAGE_KEY = "status.success.unneeded";

    private static final String FAILURE_MESSAGE_KEY = "status.failure";

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApplicationKey applicationKey;

    public PullRequestAnalyzer(ApplicationKey applicationKey) {
        this.applicationKey = Objects.requireNonNull(applicationKey);
    }

    public void analyze(GitHubPullRequestEvent event) {
        logger.info("Processing pull request for {}", event.getLoggableRepositoryName());

        InstallationAccessToken accessToken = InstallationAccessToken.forRepository(event.getBaseRepositoryUrl(),
                applicationKey, Requests.USER_AGENT);

        StatusHandler statusHandler = new StatusHandler("doc/chronicler", event.getPullRequestStatusesUrl(),
                accessToken);

        // Set pending status
        statusHandler.sendPending(MESSAGES.getString(PENDING_MESSAGE_KEY));

        // TODO make path a configuration setting
        try {
            AnalysisSettings settings = AnalysisSettings.forRepository(accessToken, event.getBaseRepositoryUrl(),
                    event.getBaseBranch(), ".starchart-labs/chronicler.yml");

            logger.info("Using analysis settings: {}", settings.toString());

            HttpUrl url = HttpUrl.get(event.getPullRequestUrl()).newBuilder()
                    .addPathSegment("files")
                    .addQueryParameter("page", "1")
                    .addQueryParameter("per_page", "30")
                    .build();

            FilePathAnalysis pathAnalysis = new PageReader(accessToken, Requests.USER_AGENT).page(url.toString())
                    .map(element -> new FilePathAnalysis(settings, element))
                    .until(Collectors.reducing(FilePathAnalysis::new),
                            o -> o.map(FilePathAnalysis::isComplete).orElse(false))
                    .orElse(new FilePathAnalysis());

            AnalysisResults results = new AnalysisResults(pathAnalysis.containsProductionFiles(),
                    pathAnalysis.containsReleaseNoteFiles());

            logger.info("Analysis results: prod: {}, rel: {}", results.isModifyingProductionFiles(),
                    results.isModifyingReleaseNotes());

            processResult(results, statusHandler);
        } catch (InvalidConfigurationException e) {
            statusHandler.sendError(MESSAGES.getString(CONFIGURATION_ERROR_MESSAGE_KEY));

            throw e;
        } catch (Exception e) {
            statusHandler.sendError(MESSAGES.getString(PROCESSING_ERROR_MESSAGE_KEY));

            throw new RuntimeException("Error processing pull request files", e);
        }
    }

    private void processResult(AnalysisResults results, StatusHandler statusHandler) {
        String description = null;

        if (results.isDocumented()) {
            if (results.isModifyingProductionFiles()) {
                description = MESSAGES.getString(SUCCESS_UPDATED_MESSAGE_KEY);
            } else {
                description = MESSAGES.getString(SUCCESS_UNNEEDED_MESSAGE_KEY);
            }
        } else {
            description = MESSAGES.getString(FAILURE_MESSAGE_KEY);
        }

        if (results.isDocumented()) {
            statusHandler.sendSuccess(description);
        } else {
            statusHandler.sendFailure(description);
        }
    }

    private static final class FilePathAnalysis {

        private final boolean production;

        private final boolean releaseNote;

        public FilePathAnalysis() {
            this(false, false);
        }

        public FilePathAnalysis(AnalysisSettings settings, JsonElement element) {
            Objects.requireNonNull(settings);
            Objects.requireNonNull(element);

            String path = element.getAsJsonObject().get("filename").getAsString();
            path = path.startsWith("/") ? path : "/" + path;

            this.production = settings.isProductionFile(path);
            this.releaseNote = settings.isReleaseNoteFile(path);
        }

        public FilePathAnalysis(FilePathAnalysis a, FilePathAnalysis b) {
            this(a.containsProductionFiles() || b.containsProductionFiles(),
                    a.containsReleaseNoteFiles() || b.containsReleaseNoteFiles());
        }

        private FilePathAnalysis(boolean production, boolean releaseNote) {
            this.production = production;
            this.releaseNote = releaseNote;
        }

        public boolean containsProductionFiles() {
            return production;
        }

        public boolean containsReleaseNoteFiles() {
            return releaseNote;
        }

        // Complete because the end-state of "success" is now guaranteed
        public boolean isComplete() {
            return containsProductionFiles() && containsReleaseNoteFiles();
        }

    }

}
