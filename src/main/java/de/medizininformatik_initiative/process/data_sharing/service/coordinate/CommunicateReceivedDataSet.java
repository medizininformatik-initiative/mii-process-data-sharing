package de.medizininformatik_initiative.process.data_sharing.service.coordinate;

import java.util.List;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Targets;
import dev.dsf.bpe.v1.variables.Variables;

public class CommunicateReceivedDataSet extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CommunicateReceivedDataSet.class);

	public CommunicateReceivedDataSet(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String organizationIdentifier = getOrganizationIdentifier(latestTask);

		log(dmsIdentifier, organizationIdentifier, projectIdentifier, startTask.getId());
		sendMail(latestTask, dmsIdentifier, organizationIdentifier, projectIdentifier);

		List<Target> targets = variables.getTargets().getEntries();
		List<Target> targetsWithoutReceivedIdentifier = targets.stream()
				.filter(t -> !organizationIdentifier.equals(t.getOrganizationIdentifierValue())).toList();
		Targets newTargets = variables.createTargets(targetsWithoutReceivedIdentifier);
		variables.setTargets(newTargets);

		if (targetsWithoutReceivedIdentifier.isEmpty())
			variables.setBoolean(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_ALL_DATA_SETS_RECEIVED, true);

		latestTask.setStatus(Task.TaskStatus.COMPLETED);
		api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.update(latestTask);
		variables.updateTask(latestTask);
	}

	private String getOrganizationIdentifier(Task task)
	{
		return api.getTaskHelper()
				.getFirstInputParameterValue(task,
						new Coding().setSystem(ConstantsDataSharing.CODESYSTEM_DATA_SHARING).setCode(
								ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DIC_IDENTIFIER),
						Reference.class)
				.map(Reference::getIdentifier).map(Identifier::getValue).orElse("unknown");
	}

	private void log(String dmsIdentifier, String organizationIdentifier, String projectIdentifier, String taskId)
	{
		logger.info(
				"DMS '{}' received data-set from organization '{}' in data-sharing project '{}' for Task with id '{}'",
				dmsIdentifier, organizationIdentifier, projectIdentifier, taskId);
	}

	private void sendMail(Task task, String dmsIdentifier, String organizationIdentifier, String projectIdentifier)
	{
		String subject = "New data received in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING
				+ "'";
		String message = "New data has been stored in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + task.getId()
				+ "' at DMS with identifier '" + dmsIdentifier + "' for data-sharing project '" + projectIdentifier
				+ "' received from organization '" + organizationIdentifier + "':\n";

		api.getMailService().send(subject, message);
	}
}
