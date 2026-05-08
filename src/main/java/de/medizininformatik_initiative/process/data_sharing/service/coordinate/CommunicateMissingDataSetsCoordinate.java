package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class CommunicateMissingDataSetsCoordinate implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateMissingDataSetsCoordinate.class);

	private final boolean hrpEmailEnabled;

	public CommunicateMissingDataSetsCoordinate(boolean hrpEmailEnabled)
	{
		this.hrpEmailEnabled = hrpEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Targets targets = variables.getTargets();

		logMissingDataSets(api, targets, startTask, projectIdentifier, dmsIdentifier);
		if (hrpEmailEnabled)
			sendMail(api, startTask, targets, projectIdentifier, dmsIdentifier);

		addStartTaskOutputMissingDataSets(variables, targets);
		updateTask(api.getDsfClientProvider().getLocal(), startTask, variables);

		// needed for correlation to work when sending stop execute data sharing message
		List<Target> targetsWithoutCorrelationKey = targets.getEntries().stream().map(t -> variables
				.createTarget(t.getOrganizationIdentifierValue(), t.getEndpointIdentifierValue(), t.getEndpointUrl()))
				.toList();
		variables.setTargets(variables.createTargets(targetsWithoutCorrelationKey));
	}

	private void logMissingDataSets(ProcessPluginApi api, Targets targets, Task task, String projectIdentifier,
			String dmsIdentifier)
	{
		targets.getEntries().forEach(target -> log(api, target, task, projectIdentifier, dmsIdentifier));
	}

	private void log(ProcessPluginApi api, Target target, Task task, String projectIdentifier, String dmsIdentifier)
	{
		logger.warn("Missing data-set at DMS '{}' from organization '{}' and project-identifier '{}' in Task '{}'",
				dmsIdentifier, target.getOrganizationIdentifierValue(), projectIdentifier,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
	}

	private void sendMail(ProcessPluginApi api, Task task, Targets targets, String projectIdentifier,
			String dmsIdentifier)
	{
		String subject = "Missing data-sets in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING
				+ "'";
		StringBuilder message = new StringBuilder("Data-sets are missing in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' at DMS '" + dmsIdentifier
				+ "' regarding project-identifier '" + projectIdentifier + "' from the following organizations:\n");

		for (Target target : targets.getEntries())
			message.append("- ").append(target.getOrganizationIdentifierValue()).append("\n");

		api.getMailService().send(subject, message.toString());
	}

	private void addStartTaskOutputMissingDataSets(Variables variables, Targets targets)
	{
		Task task = variables.getStartTask();
		targets.getEntries().forEach(target -> output(task, target));
		variables.updateTask(task);
	}

	private void output(Task task, Target target)
	{
		task.addOutput()
				.setValue(new Reference()
						.setIdentifier(
								NamingSystems.OrganizationIdentifier.withValue(target.getOrganizationIdentifierValue()))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_MISSING);
	}

	private void updateTask(DsfClient client, Task task, Variables variables)
	{
		client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(task);
		variables.updateTask(task);
	}
}
