/*
 * Copyright (c) Mar 4, 2019 StarChart Labs Authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    romeara - initial API and implementation and/or initial documentation
 */
package org.starchartlabs.chronicler.webhook.handler;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.starchartlabs.chronicler.events.GitHubPullRequestEvent;
import org.starchartlabs.chronicler.github.model.webhook.InstallationEvent;
import org.starchartlabs.chronicler.github.model.webhook.InstallationRepositoriesEvent;
import org.starchartlabs.chronicler.github.model.webhook.PingEvent;
import org.starchartlabs.chronicler.github.model.webhook.PullRequestEvent;

/**
 * Handles processing GitHub webhook event payloads
 *
 * @author romeara
 */
public class WebhookEventConverter {

    /** Logger reference to output information to the application log files */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Consumer<Integer> installationRecorder;

    public WebhookEventConverter(Consumer<Integer> installationRecorder) {
        this.installationRecorder = Objects.requireNonNull(installationRecorder);
    }

    public Optional<GitHubPullRequestEvent> handleEvent(String eventType, String body) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(body);

        GitHubPullRequestEvent result = null;

        if (PingEvent.isCompatibleWithEventType(eventType)) {
            PingEvent event = PingEvent.fromJson(body);

            logger.info("GitHub Ping: {}", event.getZen());
        } else if (PullRequestEvent.isCompatibleWithEventType(eventType)) {
            PullRequestEvent event = PullRequestEvent.fromJson(body);

            logger.info("Received pull request event ({}, pr:{})", event.getLoggableRepositoryName(),
                    event.getAction());

            if (event.isCommitChangeType()) {
                result = new GitHubPullRequestEvent(
                        event.getNumber(),
                        event.getLoggableRepositoryName(),
                        event.getPullRequestUrl(),
                        event.getBaseRepositoryUrl(),
                        event.getBaseRef(),
                        event.getPullRequestStatusesUrl(),
                        event.getHeadCommitSha());
            }
        } else if (InstallationEvent.isCompatibleWithEventType(eventType)) {
            InstallationEvent event = InstallationEvent.fromJson(body);

            event.getLoggableRepositoryNames().stream()
            .forEach(repo -> logger.info("GitHub Install: {}: {}", event.getAction(), repo));

            if (event.getLoggableRepositoryNames().isEmpty()) {
                logger.info("GitHub Account Install: {}: {}", event.getAction(), event.getAccountName());
            }

            if (event.isInstallation()) {
                installationRecorder.accept(event.getLoggableRepositoryNames().size());
            }
        } else if (InstallationRepositoriesEvent.isCompatibleWithEventType(eventType)) {
            InstallationRepositoriesEvent event = InstallationRepositoriesEvent.fromJson(body);

            event.getLoggableRepositoryNames().stream()
            .forEach(repo -> logger.info("GitHub Install: {}: {}", event.getAction(), repo));

            if (event.getLoggableRepositoryNames().isEmpty()) {
                logger.info("GitHub Account Install: {}: {}", event.getAction(), event.getAccountName());
            }

            if (event.isInstallation()) {
                installationRecorder.accept(event.getLoggableRepositoryNames().size());
            }
        } else {
            logger.debug("Received unhandled event type: {}", eventType);
        }

        return Optional.ofNullable(result);
    }

}