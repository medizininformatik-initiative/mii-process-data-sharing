package de.medizininformatik_initiative.process.data_sharing.message;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;

public class SendConsolidateDataSets extends AbstractTaskMessageSend
{
	private static final Logger logger = LoggerFactory.getLogger(SendConsolidateDataSets.class);

	public SendConsolidateDataSets(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected IdType doSend(FhirWebserviceClient client, Task task)
	{
		return client.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
				.create(task);
	}

	@Override
	protected void handleSendTaskError(DelegateExecution execution, Variables variables, Exception exception,
			String errorMessage)
	{
		String startTaskId = variables.getStartTask().getId();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String error = "Start consolidate data-sets - " + exception.getMessage();

		logger.warn(
				"Could not start consolidate data-sets for project-identifier '{}' at DMS with identifier '{}' referenced in Task with id '{}' - {}: creating new user-task 'release-consolidate-data-sets'",
				projectIdentifier, dmsIdentifier, startTaskId, exception.getMessage());

		sendMail(startTaskId, projectIdentifier, dmsIdentifier, error);

		throw new BpmnError(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_COORDINATE_ERROR, error);
	}

	private void sendMail(String startTaskId, String dmsIdentifier, String projectIdentifier, String error)
	{
		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "'";
		String message = "Could not start consolidate data-sets in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_COORDINATE_DATA_SHARING + "' for Task with id '" + startTaskId
				+ "' at DMS with identifier '" + dmsIdentifier + "' for project-identifier '" + projectIdentifier
				+ "'.\n\nError: " + (error == null ? "none" : error) + "\n\n"
				+ "Please repair the error and answer again the new user-task 'release-consolidate-data-sets'.";

		api.getMailService().send(subject, message);
	}

	@Override
	protected void addErrorMessage(Task task, String errorMessage)
	{
		// Override in order not to add error message of AbstractTaskMessageSend
	}
}
