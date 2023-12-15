package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
import dev.dsf.bpe.v1.variables.Variables;

public class CommunicateMissingDataSetsCoordinate extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateMissingDataSetsCoordinate.class);

	public CommunicateMissingDataSetsCoordinate(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String taskId = variables.getStartTask().getId();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		Targets targets = variables.getTargets();

		logMissingDataSets(targets, taskId, projectIdentifier, dmsIdentifier);
		sendMail(taskId, targets, projectIdentifier, dmsIdentifier);
		outputMissingDataSets(variables, targets);
	}

	private void logMissingDataSets(Targets targets, String taskId, String projectIdentifier, String dmsIdentifier)
	{
		targets.getEntries().forEach(target -> log(target, taskId, projectIdentifier, dmsIdentifier));
	}

	private void log(Target target, String taskId, String projectIdentifier, String dmsIdentifier)
	{
		logger.warn(
				"Missing data-set at DMS '{}' from organization '{}' in data-sharing project '{}' and Task with id '{}'",
				dmsIdentifier, target.getOrganizationIdentifierValue(), projectIdentifier, taskId);
	}

	private void sendMail(String taskId, Targets targets, String projectIdentifier, String dmsIdentifier)
	{
		String subject = "Missing data-sets in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING
				+ "'";
		StringBuilder message = new StringBuilder(
				"Data-sets are missing in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING
						+ "' for Task with id '" + taskId + "' at DMS '" + dmsIdentifier + "' for project-identifier '"
						+ projectIdentifier + "' from the following organizations:\n");

		for (Target target : targets.getEntries())
			message.append("- ").append(target.getOrganizationIdentifierValue()).append("\n");

		api.getMailService().send(subject, message.toString());
	}

	private void outputMissingDataSets(Variables variables, Targets targets)
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
