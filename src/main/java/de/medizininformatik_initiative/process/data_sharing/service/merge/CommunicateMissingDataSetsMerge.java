package de.medizininformatik_initiative.process.data_sharing.service.merge;

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

public class CommunicateMissingDataSetsMerge implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateMissingDataSetsMerge.class);

	private final boolean dmsEmailEnabled;

	public CommunicateMissingDataSetsMerge(boolean dmsEmailEnabled)
	{
		this.dmsEmailEnabled = dmsEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latesTask = variables.getLatestTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Targets targets = variables.getTargets();

		logMissingDataSets(api, targets, startTask, projectIdentifier);
		if (dmsEmailEnabled)
			sendMail(api, targets, startTask, projectIdentifier);

		addStartTaskOutputMissingDataSets(api, targets, variables);
		updateStartTask(api.getDsfClientProvider().getLocal(), startTask, variables);

		// latestTask not updated automatically in consolidate case
		updateLatestTaskIfNotStartTask(api.getDsfClientProvider().getLocal(), startTask, latesTask);
	}

	private void logMissingDataSets(ProcessPluginApi api, Targets targets, Task task, String projectIdentifier)
	{
		targets.getEntries().forEach(target -> log(api, target, task, projectIdentifier));
	}

	private void log(ProcessPluginApi api, Target target, Task task, String projectIdentifier)
	{
		logger.warn("Missing data-set from organization '{}' and project-identifier '{}' in Task '{}'",
				target.getOrganizationIdentifierValue(), projectIdentifier,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
	}

	private void sendMail(ProcessPluginApi api, Targets targets, Task task, String projectIdentifier)
	{
		List<Target> missing = targets.getEntries();
		if (!missing.isEmpty())
		{
			String subject = "Missing data-sets in process '"
					+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
			StringBuilder message = new StringBuilder("Data-sets are missing in process '"
					+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task '"
					+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' regarding project-identifier '"
					+ projectIdentifier + "' from the following organizations:\n");

			for (Target target : missing)
				message.append("- ").append(target.getOrganizationIdentifierValue()).append("\n");

			api.getMailService().send(subject, message.toString());
		}
	}

	private void addStartTaskOutputMissingDataSets(ProcessPluginApi api, Targets targets, Variables variables)
	{
		Task task = variables.getStartTask();
		targets.getEntries().forEach(target -> output(api, task, target));
		variables.updateTask(task);
	}

	private void output(ProcessPluginApi api, Task task, Target target)
	{
		task.addOutput()
				.setValue(new Reference()
						.setIdentifier(
								NamingSystems.OrganizationIdentifier.withValue(target.getOrganizationIdentifierValue()))
						.setType(ResourceType.Organization.name()))
				.getType().addCoding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING)
				.setVersion(api.getProcessPluginDefinition().getResourceVersion())
				.setCode(ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_MISSING);
	}

	private void updateStartTask(DsfClient client, Task task, Variables variables)
	{
		Task response = client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
				DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(task);
		variables.updateTask(response);
	}

	private void updateLatestTaskIfNotStartTask(DsfClient client, Task startTask, Task latestTask)
	{
		if (latestTask != null && startTask != latestTask)
		{
			client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(latestTask);
		}
	}
}
