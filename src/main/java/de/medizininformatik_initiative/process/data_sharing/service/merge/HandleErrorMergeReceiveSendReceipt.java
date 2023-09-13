package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class HandleErrorMergeReceiveSendReceipt extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorMergeReceiveSendReceipt.class);

	public HandleErrorMergeReceiveSendReceipt(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE);

		sendMail(startTask, latestTask, projectIdentifier, error);
		failTaskIfNotStartTask(startTask, latestTask);
	}

	private void sendMail(Task startTask, Task latestTask, String projectIdentifier, String error)
	{
		logger.warn("{} - creating new user-startTask 'release-data-set'", error);

		String statusCode = "unknown";
		if (latestTask != null)
		{
			statusCode = latestTask.getOutput().stream().filter(o -> o.getValue() instanceof Coding)
					.map(o -> (Coding) o.getValue())
					.filter(c -> ConstantsBase.CODESYSTEM_DATA_SET_STATUS.equals(c.getSystem())).map(c -> c.getCode())
					.findFirst().orElse("unknown");
		}

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not send data-set status receipt for new data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' for Task with id '" + startTask.getId()
				+ "' to organization '" + startTask.getRequester().getIdentifier().getValue()
				+ "' for project-identifier '" + projectIdentifier + "':\n" + "- status code: " + statusCode + "\n"
				+ "- error: " + (error == null ? "none" : error);

		api.getMailService().send(subject, message);
	}

	private void failTaskIfNotStartTask(Task startTask, Task latestTask)
	{
		if (latestTask != null && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.update(latestTask);
		}
	}
}
