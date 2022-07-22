package com.github.xiyoo0812.gocd;

import com.github.xiyoo0812.gocd.email.EmailNotifier;
import com.github.xiyoo0812.gocd.model.Message;
import com.github.xiyoo0812.gocd.model.api.*;
import com.github.xiyoo0812.gocd.webhook.WebhookNotifier;
import com.google.gson.Gson;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.DefaultGoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.xiyoo0812.gocd.model.api.PluginSettings.*;
import static com.github.xiyoo0812.utils.HttpUtils.response;
import static com.github.xiyoo0812.utils.IOUtils.readStream;
import static com.github.xiyoo0812.utils.MapUtils.map;
import static com.github.xiyoo0812.utils.StringUtils.*;
import static java.lang.Integer.parseInt;

@Extension
public class BuildWatcherPlugin implements GoPlugin {
    private static final Logger LOGGER = Logger.getLoggerFor(BuildWatcherPlugin.class);
    private static final String GO_BASE_URL = "http://localhost:8153/go/api";
    private final Gson gson = new Gson();
    private GoApplicationAccessor accessor;

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier("notification", Collections.singletonList("1.0"));
    }

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest requestMessage) throws UnhandledRequestTypeException {
        LOGGER.debug("request: name=" + requestMessage.requestName() + ", body:" + requestMessage.requestBody());
        switch (requestMessage.requestName()) {
            case "notifications-interested-in":
                return response(map("notifications", Collections.singletonList("stage-status")));
            case "go.plugin-settings.get-view":
                return response(map("template", readStream(getClass().getResourceAsStream("/plugin-settings.template.html"))));
            case "stage-status":
                StageStatus status = gson.fromJson(requestMessage.requestBody(), StageStatus.class);
                handleStageStatus(status.pipeline);
                return response(map("status", "success"));
            case "go.plugin-settings.validate-configuration":
                List<Map<String, String>> errors = new LinkedList<>();
                ValidateConfiguration configuration = gson.fromJson(requestMessage.requestBody(), ValidateConfiguration.class);
                // Email validation
                if (!isBlank(configuration.get(SETTING_EMAIL_SMTP_SERVER)) && isBlank(configuration.get(SETTING_EMAIL_FROM))) {
                    errors.add(map("key", SETTING_EMAIL_FROM, "message", "The field From is required"));
                }
                return response(errors);
            case "go.plugin-settings.get-configuration":
                PluginSettings defaults = new PluginSettings();
                return response(map(
                    SETTING_SERVER_BASE_URL, configItem("Server Base URL", defaults.serverBaseUrl, false, false),
                    SETTING_SERVER_API_USERNAME, configItem("Server API Username", defaults.serverAPIUsername, true, false),
                    SETTING_SERVER_API_PASSWORD, configItem("Server API Password", defaults.serverAPIPassword, true, true),
                    SETTING_EMAIL_SMTP_SERVER, configItem("Email SMTP Server", defaults.emailSMTPServer, true, false),
                    SETTING_EMAIL_SMTP_PORT, configItem("Email SMTP Port", String.valueOf(defaults.emailSMTPPort), true, false),
                    SETTING_EMAIL_SMTP_SSL, configItem("Email SMTP SSL", String.valueOf(defaults.emailSMTPSSL), true, false),
                    SETTING_EMAIL_AUTH_USER, configItem("Email SMTP Authentication Username", defaults.emailAuthUser, false, false),
                    SETTING_EMAIL_AUTH_PASSWORD, configItem("Email SMTP Authentication Password", defaults.emailAuthPassword, false, true),
                    SETTING_EMAIL_FROM, configItem("Email From", defaults.emailFrom, true, false)));
        }
        return null;
    }

    private Map<?, ?> configItem(String displayName, String defaultValue, boolean required, boolean secure) {
        return map("display-name", displayName, "display-value", defaultValue, "required", required, "secure", secure);
    }

    private PluginSettings getSettings() {
        DefaultGoApiRequest request = new DefaultGoApiRequest("go.processor.plugin-settings.get", "1.0", pluginIdentifier());
        request.setRequestBody(gson.toJson(map("plugin-id", "build-watcher.notifier")));
        GoApiResponse response = accessor.submit(request);
        LOGGER.debug("getSettings: " + response.responseBody());
        return PluginSettings.fromJSON(response.responseBody());
    }

    private void handleStageStatus(StageStatus.Pipeline pipeline) {
        PluginSettings settings = getSettings();
        StageResult result = pipeline.stage.result;
        LOGGER.info("Starting execution of the handleStageStatus()");
        if (result.isFinal()) {
            PipelineInstance instance = getPipelineInstance(settings, pipeline.name, pipeline.counter);
            PipelineConfig config = getPipelineConfig(settings, pipeline.name);

            Set<String> users = new LinkedHashSet<>();
            relevantMaterialRevisions(instance).forEach(
                $->users.addAll(getMaterialUsers($.modifications))
            );

            String changesResume = getChangesResume(instance);
            users.forEach(userEmail->{
                Message message = getMessage(settings, userEmail, pipeline, result, changesResume);
                LOGGER.info("handleStageStatus: userEmail=" + userEmail + ", result=" + result + ", message=" + message);
                // sends the notification
                getNotifiers(settings, config).forEach(n->{
                    try {
                        n.sendMessage(userEmail, message);
                    } catch (Exception e) {
                        LOGGER.error("sendMessage failed: notifier=" + n + 
                                ", userEmail=" + userEmail + ", result=" + result + ", message=" + message, e);
                    }
                });
            });
        } else {
            LOGGER.info("Ignoring non final state: stage=" +
                pipeline.name + '/' + pipeline.stage.name + ", state=" + pipeline.stage.result);
        }
    }

    private PipelineInstance getPipelineInstance(PluginSettings settings, String name, int counter) {
        try {
            URLConnection connection = new URL(GO_BASE_URL + "/pipelines/" + name +"/" + counter).openConnection();
            if (!isBlank(settings.serverAPIUsername) && !isBlank(settings.serverAPIPassword)) {
                String userpass = settings.serverAPIUsername + ":" + settings.serverAPIPassword;
                String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userpass.getBytes());
                connection.setRequestProperty("Authorization", basicAuth);
                connection.setRequestProperty("Accept", "application/vnd.go.cd+json");
            }
            return gson.fromJson(new InputStreamReader(connection.getInputStream()), PipelineInstance.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private PipelineConfig getPipelineConfig(PluginSettings settings, String name) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(GO_BASE_URL + "/admin/pipelines/" + name).openConnection();
            if (!isBlank(settings.serverAPIUsername) && !isBlank(settings.serverAPIPassword)) {
                String userpass = settings.serverAPIUsername + ":" + settings.serverAPIPassword;
                String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userpass.getBytes());
                connection.setRequestProperty("Authorization", basicAuth);
                connection.setRequestProperty("Accept", "application/vnd.go.cd+json");
            }
            // String line;
            // BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            // StringBuilder result = new StringBuilder();
            // while ((line = bufferedReader.readLine()) != null) {
            //     result.append(line);
            // }
            // String response = result.toString();
            // LOGGER.info("getPipelineConfig: " + response);
            return gson.fromJson(new InputStreamReader(connection.getInputStream()), PipelineConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<PipelineInstance.MaterialRevision> relevantMaterialRevisions(PipelineInstance instance) {
        Map<Boolean, List<PipelineInstance.MaterialRevision>> materialRevisions = instance.buildCause.revisions.stream()
            .collect(Collectors.partitioningBy(r->Objects.equals(r.material.type, "Pipeline")));

        LOGGER.info("Starting execution of the relevantMaterialRevisions()");
        LOGGER.info("Printing out all relevant material revisions...");

        LOGGER.info("Printing out type: pipeline...");
        materialRevisions.get(true).forEach(m->LOGGER.info(m.material.fingerprint));
        LOGGER.info("Printing out type: !pipeline...");
        materialRevisions.get(false).forEach(m->LOGGER.info(m.material.fingerprint));

        List<PipelineInstance.MaterialRevision> res = new LinkedList<>(materialRevisions.get(false));
        if (materialRevisions.get(false).size() == 0 && materialRevisions.get(true).size() > 0) {
            LOGGER.info("Only able to find pipeline material revisions, processing parents...");
            materialRevisions.get(true).forEach(
                $->getPipelinesFromModifications($).forEach(p->res.addAll(relevantMaterialRevisions(p)))
            );
        }
        return res;
    }

    private List<PipelineInstance> getPipelinesFromModifications(PipelineInstance.MaterialRevision materialRevision) {
        PluginSettings settings = getSettings();
        return materialRevision.modifications.stream()
            .filter($->!Objects.equals($.revision, ""))
            .map($->extractPipelineAndCounter($.revision))
            .filter(Objects::nonNull)
            .map($->getPipelineInstance(settings, $.get("pipeline"), parseInt($.get("counter"))))
            .collect(Collectors.toList());
    }

    private List<String> getMaterialUsers(List<PipelineInstance.Modification> modifications) {
        return modifications.stream()
            .filter(m->m.emailAddress != null || m.userName != null)
            .map(m->m.emailAddress != null ? m.emailAddress : getEmailAddressesInString(m.userName))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private String getChangesResume(PipelineInstance instance) {
        return instance.buildCause.revisions.stream()
            .flatMap($->$.modifications.stream())
            .map($->"Material changed:\n\tRevision: " + $.revision + ($.comment != null ? "\n\tComment: " + $.comment : "") + ($.userName != null ? "\n\tUsername: " + $.userName : ""))
            .collect(Collectors.joining("\n\n"));
    }

    private Message getMessage(PluginSettings settings, String userEmail, StageStatus.Pipeline pipeline, StageResult result, String changesResume) {
        String message = null;
        String link = null;
        Message.Type type = null;

        if (result.isSucceed()) {
            message = settings.messagePipeGood;
            type = Message.Type.GOOD;
        } else {
            message = settings.messagePipeBad;
            type = Message.Type.BAD;
        }
        if (!isBlank(settings.serverBaseUrl)) {
            link = settings.serverBaseUrl.replaceFirst("/$", "") + "/pipelines/" + pipeline.name + 
                '/' + pipeline.counter + '/' + pipeline.stage.name + '/' + pipeline.stage.counter;
        }
        
        String text = "\n" + message.replaceAll(PLACEHOLDER_USER, userEmail)
            .replaceAll(PLACEHOLDER_STAGE_COUNTER, String.valueOf(pipeline.stage.counter))
            .replaceAll(PLACEHOLDER_PIPELINE_COUNTER, String.valueOf(pipeline.counter))
            .replaceAll(PLACEHOLDER_STAGE, pipeline.stage.name)
            .replaceAll(PLACEHOLDER_PIPELINE, pipeline.name)
            .replaceAll(PLACEHOLDER_LABEL, pipeline.label);

        String name = pipeline.name + "-" + pipeline.counter + "-" + pipeline.stage.name + '-' + pipeline.stage.counter;
        return new Message.Builder()
            .name(name)
            .text(text)
            .link(link)
            .type(type)
            .tag("Pipeline", pipeline.name + '/' + pipeline.counter)
            .tag("Stage", pipeline.stage.name + '/' + pipeline.stage.counter)
            .tag("Label", pipeline.label)
            .tag("Status", capitalize(result.name()))
            .longTag("Changes", changesResume)
            .build();
    }

    private List<Notifier> getNotifiers(PluginSettings settings, PipelineConfig config) {
        if (accessor != null) {
            settings.emailCC = "";
            List<Notifier> notifs = new ArrayList<>();
            config.parameters.forEach(n->{
                if (n.name.equals("wechat") || n.name.equals("dingding") || n.name.equals("lark")) {
                    notifs.add(new WebhookNotifier(n.name, n.value));
                }
                if (n.name.equals("emails")) {
                    settings.emailCC = n.value;
                }
            });
            if (!isBlank(settings.emailSMTPServer) && !isBlank(settings.emailFrom)) {
                notifs.add(new EmailNotifier(settings.emailSMTPServer, settings.emailSMTPPort, settings.emailAuthUser,
                    settings.emailAuthPassword, settings.emailSMTPSSL, settings.emailFrom, settings.emailCC));
            }
            return notifs;
        }
        return Collections.emptyList();
    }
}
