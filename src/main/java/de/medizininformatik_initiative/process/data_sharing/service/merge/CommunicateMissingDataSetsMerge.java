package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.constants.NamingSystems;
import dev.dsf.bpe.v2.service.MailService;
import dev.dsf.bpe.v2.variables.Target;
import dev.dsf.bpe.v2.variables.Targets;
import dev.dsf.bpe.v2.variables.Variables;

public class CommunicateMissingDataSetsMerge implements ServiceTask
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateMissingDataSetsMerge.class);

	public CommunicateMissingDataSetsMerge()
	{
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		String taskId = variables.getStartTask().getId();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		Targets targets = variables.getTargets();

		logMissingDataSets(targets, taskId, projectIdentifier);
		sendMail(api.getMailService(), targets, projectIdentifier);
		outputMissingDataSets(targets, variables);
	}

	private void logMissingDataSets(Targets targets, String taskId, String projectIdentifier)
	{
		targets.getEntries().forEach(target -> log(target, taskId, projectIdentifier));
	}

	private void log(Target target, String taskId, String projectIdentifier)
	{
		logger.warn("Missing data-set from organization '{}' in data-sharing project '{}' and Task with id '{}'",
				target.getOrganizationIdentifierValue(), projectIdentifier, taskId);
	}

	private void sendMail(MailService mailService, Targets targets, String projectIdentifier)
	{
		String subject = "Missing data-sets in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING
				+ "'";
		StringBuilder message = new StringBuilder("Data-sets are missing for data-sharing project '")
				.append(projectIdentifier).append("' in process '")
				.append(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING)
				.append("' from the following organizations:\n");

		for (Target target : targets.getEntries())
			message.append("- ").append(target.getOrganizationIdentifierValue()).append("\n");

		mailService.send(subject, message.toString());
	}

	private void outputMissingDataSets(Targets targets, Variables variables)
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
}
